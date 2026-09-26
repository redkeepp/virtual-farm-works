# CLAUDE.md — Virtual Farm Works (VFW)

Guidance for any Claude instance (and human dev) working in this repository.
This file is a living document: **update it whenever a decision, convention, command or project status changes.**

## Project

NeoForge mod for Minecraft. Compact, server-friendly alternative to large physical farms, meant for big modpacks
(e.g. All the Mods 11) and compatible with as many modded crops as possible — **primarily Mystical Agriculture**.
Inspired by Hostile Neural Networks: pay the cost of a real farm, but the server only processes an aggregated,
virtual representation. It is convenience + infrastructure reduction + lag prevention, NOT a free resource generator.

- Mod id: `virtualfarmworks` — base package: `com.virtualfarmworks`
- Target now: **Minecraft 26.1.2 / NeoForge 26.1.2.109 / Java 25** (other versions are planned later, keep
  version-specific code isolated where reasonable). The original spec said "1.21.2"; the owner confirmed 26.1.2.
- Build system: ModDevGradle 2.0.147 (from the official NeoForge MDK), Gradle wrapper 9.2.1.
- License: **MIT** for everything (code and assets; the owner confirmed all art is their own). `LICENSE` is the VFW
  license; `TEMPLATE_LICENSE.txt` is the NeoForged MDK notice and must be kept. Do not copy code or assets from
  other mods into this repo without checking their license.
- Mojang mappings are unobfuscated in 26.1 (official parameter names are available).

## Commands

Run from the repository root (this folder). `JAVA_HOME` must point to JDK 25 (already configured on the owner's machine).

```
.\gradlew.bat build         # compile + jar (first run downloads MC/NeoForge, ~1 min afterwards)
.\gradlew.bat runClient     # launch dev client
.\gradlew.bat runServer     # launch dev dedicated server (nogui)
.\gradlew.bat runData       # run data generators
```

## Working rules (owner requirements)

1. **Comment everything that needs it, in English only.** Every non-trivial piece of logic must explain *what* it
   does and *why* (invariants, performance reasons, anti-dupe reasoning), so that a future Claude instance never has to
   guess and human devs can maintain it. No Portuguese in code, comments, commit messages or docs inside the repo.
2. Keep this `CLAUDE.md` current (status section, decisions, gotchas).
3. Work in small, testable milestones; build after each one; commit per working milestone.
4. Numbers per tier (plot capacity, energy, speed, upgrade effects) are **not final** unless listed below.
   Put them in config (TOML via `ModConfigSpec`) instead of hard-coding, so the owner can tune them.
5. **Do not read the parent folder** (`..\`, the owner's "Virtual Farm Works" folder). It holds an older, flawed prototype
   that the owner does not want to influence this code. This repository is the `virtualfarmworks` subfolder only.
6. Windows: the working path can be long; `git clone` of external repos into deep paths may fail with
   `'$GIT_DIR' too big` — download a ZIP or clone into a short path instead.

7. **Art assets (owner decisions):** everything under `src/main/resources/assets/virtualfarmworks/` was made by the
   owner and is considered complete. Do NOT rename, reorganize or "fix" it: keep the Portuguese texture file names
   (`azul.png`, `corpo.png`, `preto.png`, ...) — this is the one exception to the English-only rule. Ignore
   `textures/gui/starter_farm_matrix_gui2.png` (unused variant). Ignore any missing Entropic-tier assets. If something
   renders as a missing-texture (purple/black) in game, first check that the code's resource paths match the
   existing files, and only then tell the owner — do not create replacement art.

## Core design (from the owner's spec — source of truth)

### Central principle
The player pays for a real farm; the server processes one aggregated, virtual farm. Cost of advancing a machine must
not grow with the number of plots.

### One global growth cycle per machine
Each Farm Matrix has exactly ONE progress bar: `globalGrowthProgress = 0.0 .. 1.0`.
There is no per-plot progress. All installed crops (wheat, Inferium Seed, Diamond Seed, any modded crop) complete the
cycle together, regardless of their vanilla/mod growth time. Growth-time differences are deliberately ignored.
A Matrix with 1 plot and one with 6,000 plots cost (conceptually) the same to advance.

### ACTIVE / PENDING eligibility
Anti-exploit for seeds inserted late in the cycle (e.g. at 90%) without per-plot timers:
each plot / PlotGroup only has an eligibility state, `ACTIVE` or `PENDING`.
- Seeds inserted while the machine is mid-cycle enter as `PENDING`.
- At 100%: `ACTIVE` plots are harvested; `PENDING` plots do NOT produce, they just become `ACTIVE`; progress resets to 0.
- If the machine was empty and receives its first plots, they may enter directly as `ACTIVE` (cycle starts at 0%).
- The only extra data is `activeCount` / `pendingCount` per slot or PlotGroup. Never create individual progress.
- Never compute real random ticks.

### Performance rules (hard requirements)
DO NOT:
- tick each plot individually; create a BlockEntity per virtual plot;
- create crop/item entities or fake players to simulate growth/harvest;
- use `randomTick` for the simulation; scan the world/chunks to compute production;
- spawn `ItemEntity` for outputs (use buffers/inventories directly);
- keep chunks loaded (chunk unload = machine stopped); simulate offline progress in v1;
- sync full plot lists (3,000 / 6,000 plots) to the client every tick.

Ideal BlockEntity tick:
```
tick():
  if cachedState invalid -> revalidate()
  if cannotRun -> return
  if tierUsesEnergy and !hasEnergy -> set status + return
  progress += speedMultiplier
  consumeEnergyIfNeeded()
  if progress >= baseGrowthTime: tryHarvestAggregated()
```
- Heavy validation happens when inventory/config changes, not every tick. Cache flags such as `needsHoe`,
  `hasValidProvider`, `hasValidPlots`, `hasOutputSpace`; invalidate on relevant events.
- GUI sync: only needed values, throttled (5–20 ticks), never full state per tick.
- Persistence: call `setChanged()` only when persistent state actually changed.

### NeoForge implementation notes
- One BlockEntity per Farm Matrix. Expose inventories/energy through NeoForge capabilities only on faces/modes the
  configuration allows.
- Server is authoritative for adding/removing plots, harvest and autocrafting. Client only sends UI intents; the server
  validates stock, capacity and permissions. Never trust client GUI state for real quantities.
- Harvest must be **transactional** (no dupes with fast clicks, concurrent automation or reconnects).
- Removing a PlotGroup / withdrawing resources returns exactly the matching planting items/substrates and updates
  active/pending atomically.
- Chunk unloaded = no tick = production stopped. No own chunk loader. No offline catch-up in v1.
- Persist: progress, active/pending, storage, buffers, autocraft leftovers/rules, I/O configuration.
  Avoid redundant derivable state when it can be safely rebuilt.

### Crop compatibility approach (planned, not yet implemented)
Resolve a seed item to its crop block, and derive drops from the block's loot table at maturity using a server-side
loot context (no entities, no fake player). Cache the result per seed item; invalidate on datapack/recipe reload.
This should support Mystical Agriculture and other mods without per-mod integration. Mystical Agriculture "Crux"
(special block requirement for some crops) is what the Crux Provider Upgrade is meant to satisfy — details pending.

### Content list
Blocks: Starter, Voltaic, Ionic, Resonant, Entropic **Farm Matrix**.
Items (5 tiers each: Starter, Voltaic, Ionic, Resonant, Entropic): **Water Provider Upgrade**, **Growth Speed Upgrade**.
Item (single, untiered): **Crux Provider Upgrade**.
Progression: starts as a simple alternative to the Hopper Botany Pot and ends with machines representing thousands of
plots (Entropic ≈ 6,000 plots as a design target). Tier effects/numbers: pending owner's spec (config-driven).

## Status

- [x] Project scaffolded from official MDK 26.1.2, renamed to `virtualfarmworks`, builds successfully.
- [ ] Waiting for the owner's Starter Farm Matrix spec (what the initial machine does).
- Milestone 1 scope (agreed): **Starter Farm Matrix only**, to validate the architecture before the other tiers.
  I/O by face, autocrafting and integrations come in later milestones.

## Layout

- `src/main/java/com/virtualfarmworks/` — mod code (main class `VirtualFarmWorks`).
- `src/main/resources/assets/virtualfarmworks/` — lang, models, textures.
- `src/main/templates/META-INF/neoforge.mods.toml` — mod metadata (Gradle expands `${...}` from `gradle.properties`).
