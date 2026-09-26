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
.\gradlew.bat runGameTestServer   # headless: boots the mod, runs game tests, exits. Use it to catch registry/datapack
                                  # errors without opening the client; then check run/logs/latest.log for ERROR/WARN.
```

Dev mods for testing (Mystical Agriculture, Mystical Agradditions, ...) go as jars in `run/mods/` (26.1 is unobfuscated,
so production jars load in dev). `run/` is git-ignored.

## API gotchas (26.1.2) — verify against sources, do not trust memory of older versions

- Read the real sources before using an API. Extract them (gitignored, deleted by `gradlew clean`):
  `build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar` -> `build/mcsrc/` (Minecraft + NeoForge patches) and
  `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.109/*/neoforge-26.1.2.109-sources.jar` ->
  `build/neosrc/` (NeoForge's own classes, e.g. `DeferredRegister`). Use `tar -xf <jar>`.
- `ResourceLocation` is renamed to `net.minecraft.resources.Identifier`.
- `Item#appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`.
- Every block type needs a `MapCodec` (`codec()`); see `FarmMatrixBlock.CODEC`.
- Item models: `assets/<ns>/items/<id>.json` (item definition) -> `models/item/<id>.json`. Both are required.
- If `gradlew build` says UP-TO-DATE after code changes and the jar is stale, delete the project `.gradle/` folder
  (configuration cache pointing at an old path) and rebuild.

## Assets: hand-written JSON, no model datagen

The owner authored the block models in Blockbench. NeoForge's `ModelProvider` datagen requires generating a model for
every registered block/item, which would overwrite them. So blockstates, item definitions and simple item models are
**hand-written JSON** in `src/main/resources`. When adding a block/item, add: `blockstates/` (blocks), `items/`,
`models/item/` (flat items: parent `minecraft:item/generated`), lang keys in `en_us.json` (ONLY English for now —
owner removed `pt_br.json`; do not add other languages unless asked), loot table and `mineable` tag (blocks).

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

## Specs (read before implementing)

- `docs/specs/starter-farm-matrix.md` — Starter Farm Matrix: GUI coordinates, slots, states, behavior, open questions.
- `docs/specs/configurability.md` — everything pack makers must be able to change without Java.
- Mystical Agriculture exists for 26.1.2 (9.0.x, Blake's Mods). Dev testing uses jars in `run/mods/`
  (MA 9.0.9, Agradditions 9.0.3, Cucumber, plus the owner's other test mods). VFW must not hard-depend on it.

## Mystical Agriculture findings (from its 9.0.9 jar, via javap — needed for step 3/5)

- `MysticalCropBlock extends CropBlock implements ICropProvider` (`getCrop()` -> `api.crop.Crop`). Agradditions crops
  build on the same API.
- `MysticalCropBlock#getDrops(BlockState, LootParams.Builder)` is overridden and reads the REAL WORLD:
  `level.getBlockState(ORIGIN.below())` to get the farmland, then `crop.getSecondaryChance(farmlandBlock)` for the
  extra seed (only if MA config `secondarySeedDrops`), plus Fertilized Essence with MA config
  `fertilizedEssenceChance`. A virtual machine has no real farmland below the crop, so the generic loot path is WRONG
  for MA crops: VFW needs an MA compat path that uses `Crop#getSecondaryChance(soilBlock)` with the soil from the slot.
- `Crop#getSecondaryChance(Block soil)`: 0 if the tier has no secondary seed drop; +base (crop override if > -1,
  else tier base, default 0.1) when soil is any `IEssenceFarmland`; +0.1 more when the crop respects effective
  farmland and `tier.isEffectiveFarmland(soil)` (tier's own farmland or block tag `ALWAYS_EFFECTIVE_FARMLAND`); capped
  at 1.0. So by default: 0% plain soil, 10% any essence farmland, 20% matching tier farmland.
- Crux: `Crop#getCruxBlock()` (null when the crop needs none) -> satisfied by the Crux Provider Upgrade.
- Farmlands: `InfusedFarmlandBlock extends FarmlandBlock implements IEssenceFarmland`, `getTier()`. Ids:
  `mysticalagriculture:{inferium,prudentium,tertium,imperium,supremium,awakened_supremium}_farmland`,
  `mysticalagradditions:insanium_farmland`.
- Open decision: how VFW compiles against MA (compileOnly jar via a maven such as CurseMaven vs reflection).
- Assets: `blockstates/` and some item definitions/models (growth upgrades, crux) are not in the owner's asset drop;
  they are plumbing, not art — generate them (datagen) pointing at the owner's existing textures.
  Ignore `textures/item/antigos_nao_usar/` (old, unused).

## Status

- [x] Project scaffolded from official MDK 26.1.2, renamed to `virtualfarmworks`, builds successfully.
- [x] Starter Farm Matrix spec received (`docs/specs/`). Open questions sent to the owner.
- [x] Owner answered the open questions; go-ahead given for milestone 1.
- Milestone 1 steps: (1) registration [done] (2) config + soil data map (3) plant/soil resolver (4) simulation core +
  unit tests (5) aggregated transactional harvest (6) BlockEntity, persistence, I/O, auto-export (7) GUI (8) in-game test.
- [x] Step 1: Starter Farm Matrix block (directional, no BE yet), all 5 tiers of Water Provider and Growth Speed
  upgrades, Crux Provider Upgrade, creative tab, lang (en_us, pt_br), blockstate/item JSON, loot table, pickaxe tag.
  Growth upgrades are registered as `<tier>_growth_upgrade` to match the owner's texture names. Recipes: none yet
  (owner: ignore recipes for now; they will be datapack JSON only).
- [x] Step 2: server config (`config/VfwServerConfig`, `VfwConfig` with compiled blacklists + generation counter,
  `ItemFilter`), soil data map (`data/SoilProperties`, `ModDataMaps`) with MA/Agradditions farmland bonuses
  (Awakened Supremium = +40%, same as Insanium, owner decision). The MA extra-seed option is a MULTIPLIER over MA's
  own chance; its config comment has worked examples (owner asked for pack-maker-friendly examples).
- Milestone 1 scope (agreed): **Starter Farm Matrix only**, to validate the architecture before the other tiers.
  I/O by face, autocrafting and integrations come in later milestones.

## Layout

- `src/main/java/com/virtualfarmworks/` — mod code (main class `VirtualFarmWorks`, only wires registries).
  - `machine/MachineTier` — tier enum; order = progression; `accepts()` implements the upgrade compatibility gate.
  - `block/FarmMatrixBlock` — one block class for all tiers (tier is a constructor arg).
  - `item/` — `TieredUpgradeItem` (+ `UpgradeType`), `CruxProviderUpgradeItem`. Items carry no behavior.
  - `registry/` — `ModBlocks`, `ModItems`, `ModCreativeTabs` (DeferredRegisters).
  - `config/` — `VfwServerConfig` (spec), `VfwConfig` (runtime: compiled filters, generation), `ItemFilter`.
  - `data/` — `SoilProperties` + `ModDataMaps` (soil growth bonus data map).
- `src/main/resources/assets/virtualfarmworks/` — lang, models, textures.
- `src/main/templates/META-INF/neoforge.mods.toml` — mod metadata (Gradle expands `${...}` from `gradle.properties`).
