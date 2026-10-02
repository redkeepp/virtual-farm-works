# CLAUDE.md — Virtual Farm Works (VFW)

Guidance for any Claude instance (and human dev) working in this repository. Living document: **update it whenever a
decision, convention, command or project status changes.** It describes the CURRENT state; how things got here (step
by step, with the reasons) is in `docs/history.md`.

## Project

NeoForge mod for Minecraft. Compact, server-friendly alternative to large physical farms, meant for big modpacks
(e.g. All the Mods 11) and compatible with as many modded plants as possible — **primarily Mystical Agriculture**.
Inspired by Hostile Neural Networks: pay the cost of a real farm, but the server only processes an aggregated,
virtual representation. It is convenience + infrastructure reduction + lag prevention, NOT a free resource generator.

- Mod id `virtualfarmworks`, base package `com.virtualfarmworks`.
- Target: **Minecraft 26.1.2 / NeoForge 26.1.2.109 / Java 25** (the original spec said 1.21.2; the owner confirmed
  26.1.2). Other versions later: keep version-specific code isolated where reasonable. 26.1 is unobfuscated (official
  names).
- Build: ModDevGradle 2.0.147 (official NeoForge MDK), Gradle wrapper 9.2.1.
- License: **MIT** for code and assets (all art is the owner's; owner, 2026-09-30: "as free as possible", modpacks
  welcome without asking). `LICENSE` is VFW's; `TEMPLATE_LICENSE.txt` is the
  NeoForged MDK notice and must be kept. Never copy code or assets from other mods without checking their license.
- Git: `https://github.com/redkeepp/virtual-farm-works` (Redkeep's account; moved there on 2026-09-30), branch
  `main`. Claude commits (this repository's git identity is Redkeep, set in `.git/config`); the OWNER pushes.

## Working rules (owner requirements)

1. **Comment everything that needs it, in English only.** Explain what and why (invariants, performance, anti-dupe)
   so a future Claude instance never has to guess and human devs can maintain it. No Portuguese in code, comments,
   commit messages or repo docs (exception: the owner's texture file names, rule 7).
   - Every Java file starts with a header block comment BEFORE `package`: `/* ClassName — what this file is and
     where it fits. */` (1–3 lines). The class Javadoc holds the details.
   - Build/config files (gradle, toml, workflow yml) start with a `#`/`//` header.
   - JSON cannot hold comments: every JSON resource is documented in `docs/resources.md`; update it with the file.
   - When behavior changes, update comments and docs in the same commit. A stale comment is worse than none.
2. Keep this `CLAUDE.md` current.
3. Small, testable milestones; build and test after each; commit per working milestone (commit message ends with the
   `Co-Authored-By` line from the system reminder). Never push.
4. **Nothing hard-coded for balance**: numbers go to config (`ModConfigSpec`), datapack data maps or tags, so pack
   makers can tune them. Tier numbers are not final unless written here or in the specs.
5. **Do not read the parent folder** (`..\`, the owner's "Virtual Farm Works" folder): it holds an older, flawed
   prototype the owner does not want to influence this code. The repository is the `virtualfarmworks` subfolder.
6. **Chat**: the owner writes in Portuguese — answer in Portuguese (repo stays English). When the owner asks a
   question or says "discuss" (DISCUTIR), answer and WAIT for an explicit go-ahead before changing code. Once a
   feature is authorized, implement it directly: no plan or file list for approval first (owner, 2026-09-29). Report
   honestly: measured vs estimated, what was not tested.
7. **Art assets** under `src/main/resources/assets/virtualfarmworks/` are the owner's and complete. Do NOT rename,
   reorganize or "fix" them; keep the Portuguese texture names (`azul.png`, `corpo.png`, ...). Ignore
   `textures/gui/starter_farm_matrix_gui2.png`, `textures/item/antigos_nao_usar/` (excluded from the jar by
   build.gradle) and missing Entropic assets. If
   something renders as the missing texture, check the code's resource paths first, then tell the owner; never
   create replacement art.
8. **Modpack mindset** (owner): the mod will run in big packs — support ANY seed, ANY sapling, ANY plant through its
   block class, tags and the game's own rules. Never hard-code vanilla item lists in Java; exceptions go in tags or
   data maps that pack makers can edit.
9. **Presented as Redkeep's** (owner, 2026-09-30): the author is Redkeep (`mod_authors`, LICENSE copyright). Write
   "Redkeep" wherever a name is shown; the GitHub account is `redkeepp` (two p: `redkeep` was taken), used only
   where the account itself is needed (URLs). The owner's previous personal account is not used any more: never
   write the owner's personal name, old username or e-mail in any file, doc, metadata or commit.

## Commands

Run from the repository root. `JAVA_HOME` must point to JDK 25 (configured on the owner's machine).

```
.\gradlew.bat build               # compile + jar + JUnit (build/test-results/test/*.xml)
.\gradlew.bat runClient           # dev client (the owner usually has it open; see Tooling gotchas)
.\gradlew.bat runServer           # dev dedicated server (nogui)
.\gradlew.bat runData             # MDK data run; VFW registers no generators (assets are hand-written, see Gotchas)
.\gradlew.bat test                # JUnit only (Minecraft-free code), seconds
.\gradlew.bat runGameTestServer   # headless game tests; exit code 0 = all passed; log in run-gametest/logs/latest.log
.\gradlew.bat runBenchmark        # headless load benchmark, registered alone; table in the log and in
                                  # run-gametest/vfw-benchmark.txt. Close the game first (CPU noise).
```

- Game tests and the benchmark run in `run-gametest/` (default config, own world and logs; the `syncTestMods` task
  copies `run/mods` into it). Never point them back to `run/`: `run/config` holds the owner's manual test values.
- Dev test mods go as jars in `run/mods/` (MA 9.0.9, Agradditions 9.0.3, Cucumber, Jade, JEI, and others; 26.1 is
  unobfuscated, so production jars load in dev). `run/` and `run-gametest/` are git-ignored. CI (GitHub Actions)
  runs `gradlew build` only (JUnit, and it compiles the game tests without running them).
- Release jar (`gradlew build` -> `build/libs/virtualfarmworks-<minecraft_version>-<mod_version>.jar`, e.g.
  `virtualfarmworks-26.1.2-1.0.0.jar`; `mod_version` alone goes into `neoforge.mods.toml`): built from `main` only.
  Optional metadata lines (home page, issue tracker) are Groovy `if` blocks in the template, left out when their
  property is empty.
  The game tests and the benchmark are the `gametest` source set (`src/gametest/java`), part of the mod in dev runs
  only (their own `@Mod` class, `GameTestsEntry`); the jar carries `LICENSE_virtualfarmworks` (MIT asks for the
  notice in every copy) and leaves out `antigos_nao_usar`. Authors, home page and issue links: `gradle.properties`
  (`mod_authors`, `mod_url`, `mod_issues_url`). JEI, Jade and Mystical Agriculture are declared optional
  dependencies with the versions VFW is built against as minimums (older ones get NeoForge's error screen).
- Logo: `src/main/resources/virtualfarmworks_logo2.png` (the owner's, 512x512; `logoFile` with `logoBlur=false`,
  so the pixel art stays sharp in the mods list). Screenshots: `docs/images/` (the owner's; used by the README and
  the platform pages). Keep both names as the owner gave them.
- Public face: `README.md` (players and pack makers; update it with the features and the benchmark numbers),
  `CHANGELOG.md` (a section for every release; release notes are taken from it), `.github/ISSUE_TEMPLATE/` (bug form
  with versions and log, feature form, no blank issues), `docs/publishing.md` (CurseForge/Modrinth page texts and
  settings). A release: set `mod_version`, date the CHANGELOG section, build, and the owner publishes the jar
  (GitHub release tag `v<mod_version>`, then CurseForge and Modrinth). Right after a release, `mod_version` moves
  to the next version, so a build never carries the number of a published jar it differs from.

## Current state (2026-09-30)

**This checkout is the 1.21.1 line** (branch `1.21.1`, worktree folder `virtualfarmworks-1.21.1`, created from main
at 544812d). The port is IN PROGRESS: it follows `docs/port-1.21.1.md` (areas, weights, order, pinned versions, dev
environment), which marks the steps already done; `docs/history.md` ("Port to 1.21.1") explains them. The code does
not compile until every area of `src/main` is ported. Until the port is done, the 26.1.2 facts below describe the
target behavior, not this branch's APIs.

**Starter Farm Matrix: DONE** (declared by the owner on 2026-09-29, after the plantables and the benchmark), tested in
game by the owner (single player; dedicated server before the JEI packet was added). Features: one global growth
cycle with ACTIVE/PENDING plots; every plantable (crops, trees, flowers, grass, aquatic and hanging plants — "More
plantables", 2026-09-28); plant/soil rules from the game's own logic; loot-table, grown-tree, fixed-yield and Mystical
Agriculture harvests; transactional batched harvest into a 9-slot visible output (players can also put items in by
hand) plus 27 hidden slots; auto-export per face; Water Provider, 4 Growth Speed upgrades, Crux Provider; hoe slot
with optional time-based wear; Fertilized Essence switch; per-machine harvest filter (whitelist/blacklist, JEI
drag-and-drop); smooth progress bar; Jade tooltip; JEI exclusion areas; recipes. Git tag `starter-complete`.

**Entropic Farm Matrix: DONE** (declared by the owner on 2026-09-30, after the in-game tests, a dedicated-server test
and the final benchmark; the strongest tier, built before the middle ones to balance the midgame). Spec and every
decision: `docs/specs/entropic-farm-matrix.md`; how it was built: `docs/history.md`. Features: 60 plot groups (two
4x15 grids, up to 3,840 plots) on one growth bar, every group must be valid; FE (90 per active plot per tick, a
buffer of 3 ticks that drains to 0; RUNNING WITH LOW FE, MISSING FE); five modes per face (NONE, OUTPUT, OUTPUT
CRAFTED, OUTPUT ALL, INPUT) with pipe input and pulling from glued inventories; replant (option (b), per-machine
button, config switch); autocrafter (up to 100 crafting-table recipes, chains in order, circles stop, uses what the
output holds, Master Infusion Crystal catalyst, JEI "+", recipes kept on the broken machine's item); Water Provider,
4 Growth Speed Upgrades, Crux Provider; Fertilized Essence switch; harvest filter; 24 visible + 72 hidden output
slots; Jade; a GUI that lowers the GUI scale while open when it would not fit; recipes. Git tag `entropic-complete`.

**Released:** 1.0.0 on GitHub Releases (2026-09-30, published by the owner, without the logo). In progress: 1.0.1
(`mod_version`; adds the logo), meant as the first CurseForge/Modrinth upload (page texts: `docs/publishing.md`).

**Two Minecraft versions, side by side (owner, 2026-10-01).**
- **26.x** (branch `main`): "the base for everything from now on, on the new, up-to-date API". Every feature is
  designed and built here first, the 26.x way: never shape 26.x code around 1.21.1. Target: ATM11 and later.
- **1.21.1** (branch `1.21.1`, not created yet): "the stubborn old one that has not died yet and still needs
  attention". ATM10 (NeoForge 21.1) is the second most played CurseForge pack, so it gets every feature after 26.x,
  ported to its old API (no transfer API, old GUI, NBT saves, old game tests...). Its own problems are solved in
  that branch only; a bug in logic both versions share is fixed in both. It pins the versions ATM10 ships, kept in
  `docs/port-1.21.1.md` (2026-10-02: NeoForge 21.1.251, Mystical Agriculture 1.21.1-8.0.28, Mystical Agradditions
  1.21.1-8.0.14, Cucumber 1.21.1-8.0.16, JEI 19.57.0.446, Jade 15.10.6+neoforge).
- Same mod version for the same features on both lines (the file name carries the Minecraft version); both jars go
  to the same CurseForge/Modrinth projects. Port analysis (what changes and its weight): this session's notes in
  `docs/history.md` once the port starts. Nothing is ported until the owner asks.

**Next:** Voltaic, Ionic and Resonant (the middle tiers; they will reuse `MachineLayout`), when the owner specifies
them.
Tests: 46 game tests (45 VFW + 1 vanilla), 86 JUnit, load benchmark.

Deferred by the owner: EMI (no 26.1.2 release), publishing metadata (README still says "scaffolding"). Outside VFW:
MA 9.0.9's creative tab crashes (it lists "Inferium Essence" twice) — test in survival or with JEI; Trash Cans 1.1.1
crashes when anything exports into its Item Trash Can (its own bug, see `docs/history.md`).

## The 1.21.1 line (this branch): what differs from main

Filled in as the port goes (`docs/port-1.21.1.md`); the sections below still describe main where they differ.
- Items: no NeoForge transfer API. `transfer/` holds VFW's stand-ins with the 26.1 names: `ItemResource` (item type,
  cached hash; compare stacks with `matches` in hot loops), `ItemSlots` (base of the machine's inventories, a NeoForge
  `ItemStackHandler`; saves resource + count so counts above 99 survive), `SlotTransaction` (all-or-nothing changes to
  VFW's OWN buffers: plan, then `commit()`; dropped = nothing changed, no callback) and `SlotRange`. Neighbours get
  plain `IItemHandler` simulate/execute calls. Menus use `menu/HandlerSlot`, which reports vanilla's in-place stack
  edits (`Slot#setChanged`) to the inventory. `GridInput` routes EVERY insertion (1.21.1 pipes insert slot by slot).
- Capabilities: `Capabilities.ItemHandler.BLOCK` / `Capabilities.EnergyStorage.BLOCK`; `MachineEnergy` extends
  `EnergyStorage`.
- Saves: NBT (`saveAdditional` / `loadAdditional(CompoundTag, HolderLookup.Provider)`), main's keys. `machine/Saves`
  writes codec values with registry ops and reads booleans with a default (NBT's default is false).
- Recipes: ids are `ResourceLocation`s; `getRecipeManager().getRecipeFor(type, input, level, hint)`; "placeable" =
  `!isSpecial() && !isIncomplete()`; recipe JSON ingredients are objects (`{"item": ...}`).
- Plants: no `#supports_*` tags in 1.21.1 (soil rules are code: `#dirt`, `#sand`, `#bamboo_plantable_on`,
  `#jungle_logs`, end stone, soul sand, any `FarmBlock`); NeoForge's `TriState`; farmland class `FarmBlock`; VFW tag
  defaults use 1.21.1 tags (`#minecraft:dirt`, `#minecraft:mushroom_grow_block`). `VirtualLevel` must override
  `getMinBuildHeight` (the default asks the dimension).
- GUI: `GuiGraphics` (`renderBg`, `renderLabels`, `renderTooltip` called from `render`), depth instead of 26.1's
  layering by overlap: cover an item with `FarmMatrixScreen#coverItem` (fill + ghost-recipe overlay), not a plain
  fill. `mouseClicked(double, double, int)` (no double-click flag), `hasClickedOutside(..., button)`.
- Blocks/items: `useItemOn` -> `ItemInteractionResult`; contents drop in `FarmMatrixBlock#onRemove`; modded component
  tooltips come from `FarmMatrixBlock#appendHoverText`; item models in `models/item` (the `items/` folder is ignored).
- OPEN (owner): the owner's block models use 26.1's multi-axis element rotations, which 1.21.1 refuses: missing model
  on 1.21.1 until the owner exports compatible ones (see `docs/port-1.21.1.md`).

## Core design (owner's spec, source of truth)

- **Central principle**: the player pays for a real farm; the server processes one aggregated, virtual farm. The cost
  of advancing a machine must not grow with the number of plots (1 plot and 6,000 plots cost the same per tick).
- **One global growth cycle per machine**: one progress value 0..1; no per-plot progress. Every plant completes the
  cycle together; natural growth times are deliberately ignored (owner: "the machine is just faster").
- **ACTIVE / PENDING**: plots added mid-cycle are PENDING and only become ACTIVE at the next harvest (anti-exploit
  without per-plot timers). The first plots of an empty machine start ACTIVE at 0%. Only counters are stored.
- **Performance rules (hard)**: never tick plots individually, never create per-plot objects, entities, fake players
  or item entities; no `randomTick`, no world/chunk scans, no chunk loading, no offline catch-up (unloaded = stopped);
  GUI sync only the needed values, throttled (5–20 ticks), never full state every tick. Heavy validation only when
  something changes; the per-tick path is a few comparisons and one addition. Mark for saving only when persistent
  state changed; do not persist what can be safely rebuilt.
- **Server authoritative**: the client only sends intents (slot clicks, button ids, the JEI ghost packet); the server
  validates and applies them to the real block entity. Harvests are transactional: no dupes, no voids. Removing
  seeds or soils returns exactly those items and updates active/pending atomically.
- From the owner's original spec for later tiers, now on the Entropic: FE energy (less than a whole tick stored =
  `RUNNING WITH LOW FE`: what is left pays part of a tick and the bar moves that part, so a buffer whose source
  stopped drains to exactly 0 (owner, 2026-09-30); an empty buffer = `MISSING FE`, no progress; buffer = capacity x
  FE per plot x 3, the x 3 hard-coded by owner decision), autocrafting (its leftovers and recipes persist) and
  automated input.
- Content: Starter, Voltaic, Ionic, Resonant, Entropic **Farm Matrix**; Water Provider Upgrade and Growth Speed
  Upgrade in those 5 tiers (an upgrade fits machines of its tier or lower: `MachineTier#accepts`); one Crux Provider
  Upgrade. Only tiers whose machine exists (`MachineTier#isBuilt`: Starter, Entropic) register their upgrades, and
  the "Fits:" tooltip names only those (owner, 2026-09-30: a release shows nothing a player cannot use). Progression: from a Botany-Pot-like Starter to thousands of plots (Entropic ≈ 6,000 as a design target).

## How the machine works — quick reference

Specs with the full owner decisions: `docs/specs/starter-farm-matrix.md`, `docs/specs/configurability.md`.

### Tick (`machine/FarmMatrixBlockEntity#serverTick`)
1. Revalidate if a slot, the config generation (`VfwConfig#generation`) or tags changed — the only expensive step.
2. Filter cleanup if requested (filter change, hand placement, load — never after ordinary output changes).
3. Refill the visible output from the hidden slots if the visible output changed.
4. If something must be stored (due harvest or held drops): held drops first, then ONE harvest batch; otherwise, if
   RUNNING, `cycle.advance(progressPerTick)` (progress saved at most every 20 ticks). A failed store = OUTPUT FULL:
   the bar stays at 100% and the store is retried only after the output or inputs change.
5. Hoe wear (config `hoe.consumeDurability`, off by default): 1 point every `hoe.wearIntervalTicks` of RUNNING while
   the soil needs the hoe, never per harvest (owner). Auto-export every `output.autoExportIntervalTicks`, also while
   SHUTDOWN, on the enabled faces (relative to the front as the player sees it).

Block interaction (`block/FarmMatrixBlock`): right-click opens the GUI (`useWithoutItem`); right-click holding an
upgrade pulls in as many as fit (`useItemOn` -> `FarmMatrixBlockEntity#insertUpgradesFrom`), else opens the GUI.
Breaking drops contents in `preRemoveSideEffects`; the autocrafter's recipes leave on the machine's own item
(`machine/CrafterRecipes` data component, copied by the loot table, read back on placement).

### Growth (`sim/` — pure Java, NO net.minecraft imports, JUnit-tested)
- `GrowthCycle`: progress 0..1, `PlotGroup`s (active, pending, harvested), carry-over capped so at most one harvest
  per tick, 1e-9 "due" tolerance (600 x 1/600 is exactly one cycle).
- `setPlots(group, min(seeds, soils), plantChanged)` on every slot change: new plots PENDING unless the machine is
  empty; removal takes PENDING first, then already-harvested ACTIVE plots, then ripe ones; an empty machine resets
  progress; a plant change uproots. Persist progress, active, pending, harvested (`load` repairs corrupted data).
- Speed = hydration (Water Provider or `noWaterSpeedMultiplier`) x (1 + bonus x upgrades) x soil bonus (data map).
- `MachineStatus` priority = declaration order: SHUTDOWN > MISSING SEED > MISSING SOIL > INVALID SOIL > MISSING HOE >
  MISSING CRUX > MISSING FE > OUTPUT FULL > RUNNING WITH LOW FE > RUNNING. `isRunning()` (the bar advances) is true
  for both RUNNING states; RUNNING WITH LOW FE (yellow, owner name, 2026-09-30) = the buffer holds FE but less than
  a tick, MISSING FE = it is empty.

### Harvest (`harvest/`)
- Drop sources, built on revalidation by `HarvestPlans` (first match wins):
  - `FixedDropSource` from the `fixed_yield` data map (Torchflower Seeds -> Torchflower, Pitcher Pod -> Pitcher
    Plant, x `drops.otherPlantYield`);
  - `MysticalDropSource` (MA's formula with the soil slot's farmland);
  - native plants: `LootDropSource` (the plant's loot table, TOOL = EMPTY so the hoe never changes yields, at most
    `performance.maxLootRollsPerHarvest` evaluations scaled to the plot count);
  - trees (sapling, azalea, nether fungus): `TreeDropSource` — `TreeGrowth` grows at most
    `performance.maxTreesGrownPerHarvest` (4) trees in a `plant/VirtualLevel` with the tree's own feature, then every
    grown block's loot is rolled by hand (shared loot budget, scaled). Blocks with a block entity are skipped (hives,
    modded magic-tree cores); saplings (`#minecraft:saplings`) are SECONDARY;
  - other generic plants: `LootDropSource` crop model if planted from `#c:seeds`, else `FixedDropSource` (10 of
    itself, `drops.otherPlantYield`).
- Replanting cost: crops, nether wart, cocoa and MA pay 1 planting item per plot (the plot keeps its seed); owner
  (2026-09-28): keep it. Stems, berries, sugar cane, cactus, bamboo, mushrooms, chorus, trees and fixed yields stay
  in place: no cost. Do not confuse it with the owner's future "replanting" (Voltaic preview in Current state).
- MAIN vs SECONDARY: extra planting items are SECONDARY when in `#c:seeds`; `#virtualfarmworks:harvest_byproducts` is
  SECONDARY; the rest MAIN. MAIN x global x tier production multiplier, SECONDARY x `secondaryDropMultiplier`;
  `DropTally.finish` rounds stochastically (expected value exact).
- The harvest filter is part of the roll (`DropTally` built with `HarvestFilter` in `Harvester#roll`): rejected items
  are never produced; MA crops even skip computing them.
- A due cycle is harvested in batches (usually one) sized to the free output slots (`sim/HarvestBatching`,
  `sim/YieldSample`). Each batch is rolled ONCE and kept (`pendingBatch` + `HarvestKey`: source, Fertilized Essence
  switch, filter version); only `Harvester#tryStore` (root transaction, all-or-nothing) is retried when the output or
  inputs change. Never re-roll or discard a rolled batch because it did not fit (bias). Plots count as harvested only
  after the commit; the cycle completes when no plot is left and `heldDrops` is empty.
- A batch holds several item types (wheat + seeds) and is all-or-nothing, so it can wait while some slots are free.
- Extreme case (owner-approved): a batch bigger than EMPTY buffers stores what fits and keeps the rest in
  `heldDrops` (saved, stored first). Never call `tryStore`/`storeWhatFits` inside another transaction (they refuse):
  a rolled-back outer transaction would void a batch whose plots were already counted.
- Entropic batch order (owner): replant -> autocrafter (CRAFT ON) -> harvest filter -> output. With either of the
  first two the roll is unfiltered and the filter runs after them (`HarvestKey#unfiltered`); the filter and its purge
  never remove crafted items (`FarmMatrixBlockEntity#isCrafted` = result of a crafter recipe). Replant and crafting
  are planned per store attempt and applied only after the commit (`MachineCrafter#plan` / `#commit`).
- Replant (Entropic, owner option (b)): groups already holding the plantable (free soil = soils - seeds), then empty
  seed slots above a soil it grows on at once (`replantTargets`, cached per revalidation); soil-less plants never
  replant. Effective only when the config allows it (`machines.<tier>.replant`) AND the machine's switch is on
  (`replantOn`, saved, GUI button).

### Plot groups and status (every tier)
- The machine runs only while EVERY group holding a plantable can grow (owner, 2026-09-29): `updateStatus` feeds the
  aggregated facts (any seed, every soil present, every soil valid, hoe, crux) to `MachineStatus.resolve`; with one
  group this is exactly the Starter's rule. A blocked group keeps its plots and the whole bar freezes. A soil alone is
  waiting plots, never a problem. The per-group status only drives the GUI (red tint + tooltip).
- Tiers without a hoe (`MachineLayout#usesHoe` false: Entropic) till for free: `hasHoe` is always true. Their tool
  slot (index 2g+1, the Starter's hoe slot) holds the autocrafter's catalyst instead.

### Autocrafter (`machine/MachineCrafter`, Entropic)
- Recipes = 3x3 grids + the recipe id they made (a hint); up to 100 (config `crafterRecipes`). Crafting-table recipes
  only; special ones (`isSpecial`, NOT_PLACEABLE) refused. A cell accepts any item the recipe accepts there (tested
  once per item by substituting it into the grid and calling `matches`, cached): sticks set with oak planks take
  birch planks.
- Per batch: ingredients join a hidden buffer, recipes craft in chain order (Tarjan SCCs over "B uses A's result");
  a result stays in the buffer only for a recipe of ANOTHER SCC, so circles (ingots <-> block) end in the output.
  Leftovers wait up to `crafterBufferLimit` per item (the rest goes out). Remainders go out. No FE, no time.
- The output is stock too (owner: "pull any item in the output buffer, even what it crafted"): `plan` gets
  `outputStock()` and returns `taken`; `Harvester#tryTakeAndStore` extracts it and stores the results in ONE root
  transaction. A recipe never takes back its own SCC's results (`circleResults`), or circles would turn forever.
  Besides harvests, `craftFromOutput` runs on events only (recipes edited or loaded, CRAFT ON, hand placement,
  room after a blocked pass), never per tick.
- Catalyst (tool slot, `#virtualfarmworks:crafter_catalysts` = MA's Master Infusion Crystal): serves the cells no
  harvested item fits, never consumed; a craft whose remainder would not give it back whole is skipped
  (`catalystComesBack`, remainders indexed by the TRIMMED `CraftingInput.Positioned`).
- CRAFT OFF (and recipe edits) release the buffer into `heldDrops` (filtered like the harvest). Recipes, switch and
  buffer are saved; the buffer is deleted when the machine breaks. Re-resolved after edits and datapack reloads
  (`SoilRules#cacheGeneration`).
- Broken machine (owner, 2026-09-30): the recipes stay on its item (`CrafterRecipes`, component
  `virtualfarmworks:crafter_recipes`, tooltip "Autocrafter recipes: N") and come back when it is placed; the switch
  is ON again. `collectImplicitComponents` adds the component only when there are recipes, so an empty machine drops
  a plain item that stacks with new ones.

### Output
- Visible `OutputBuffer` (9 slots): players take and put items by hand (menu `OutputSlot`); automation only extracts
  (`externalView`); shift-click never fills it; auto-export pushes everything out.
- Hidden `InternalBuffer` (config `machines.<tier>.internalBufferSlots`, Starter 27): filled after the visible slots,
  refills them, invisible to GUI and capabilities. When both are full, ripe plots wait on the plant.
- Breaking the machine drops the inputs and the 9 visible slots; hidden slots, held drops and ripe plots are deleted
  (owner rule).
- Harvest filter (`machine/MachineFilter`, snapshot `harvest/HarvestFilter`): WHITELIST/BLACKLIST of item types,
  pages of 9, max 16 pages, empty list = no filtering in both modes. `purgeFilteredOutput` DELETES rejected items from
  visible/hidden output and held drops — triggered only by a filter change, a hand placement
  (`OutputSlot#setByPlayer`) or load.

### GUI and sync (`menu/`, `client/`)
- `FarmMatrixMenu`: slots `[0,9)` inputs, `[9,18)` output, `[18,54)` player, `[54,63)` filter ghost slots (all clicks
  handled in `clicked`, never move real items). Buttons: 0 power, 1..6 faces, 7 Fertilized Essence, 8 filter mode,
  9/10 filter page. `ContainerData` (shorts) refreshed every 5 ticks and on button press: 0 status, 1 progress x10000,
  2 hydration, 3 growth, 4 plots, 5 enabled, 6 faces, 7 fertilized, 8 harvest counter, 9 filter mode, 10 page,
  11 pages. The LAST index is what `isDataSynced` waits for (vanilla sends every value in order when a menu opens).
- `FarmMatrixLayout` holds every coordinate and color (owner-tuned pixels; details in the spec's GUI section).
  Info lines (Claude's choices, owner may revisit): "Seeds" = planted plots = min(seeds, soils); the "Growth"
  multiplier includes the soil bonus.
- `FarmMatrixScreen`: side column (O / upgrades / Fertilized Essence / filter / power, 3 px apart), face box and
  filter box (both can be open), `SmoothProgress` animates the 5-tick syncs (bar and Growth % share one value per
  frame; a harvest-counter change runs the bar through 100%).
- JEI ghost drop -> `network/SetFilterGhostPayload` -> `AbstractFarmMatrixMenu#setFilterGhost`.
- Entropic (`EntropicFarmMatrixMenu`, `EntropicLayout`, `EntropicFarmMatrixScreen`; shared logic in
  `AbstractFarmMatrixMenu`): slots `[0,127)` inputs (121 = the catalyst, shown in the crafter panel), `[127,151)`
  output, `[151,187)` player, `[187,196)` filter, `[196,205)` crafter grid (ghosts), 205 its result. Buttons 17+:
  replant, then the crafter's (SET, toggle, deselect, select i, delete i; ids travel as VarInt). The recipe list is
  NOT slots: `network/CrafterRecipesPayload` (server -> client) when it changes and on open (`sendAllDataToRemote`).
  The crafter panel is a client-side modal (`setCrafterVisible`): while open, the 120 grid slots are inactive and
  dimmed. JEI "+" (crafting recipes) -> `network/SetCrafterGridPayload` -> `EntropicFarmMatrixMenu#setCraftGrid`; the
  grid is per viewer, not saved. Side column: O, lightning (energy use tooltip), 5 upgrade cells, Fertilized
  Essence, replant, filter, crafter, ON/OFF. The FE shown is `MachineEnergy#shownAmount` (level before the machine's
  own payment of its last tick, so a buffer a source keeps up with reads full).
- GUI scale (owner OK, 2026-09-30): the automatic scale always leaves 240-270 px of height, so while the Entropic
  screen is open it lowers the GUI scale to the largest one where `EntropicLayout.FIT_WIDTH` x `FIT_HEIGHT` (460 x
  320: the GUI plus the face/filter boxes) fits (`client/GuiScaleFit`, 1080p: 4 -> 3), in `added()` and `resize()`;
  `removed()` gives back the scale computed from the options (never changed). The renderer reads the scale each
  frame, and `setScreen` calls the old screen's `removed()` before sizing the next one, so JEI's recipe view and the
  HUD get the player's scale back.
- INPUT faces pull from glued inventories every `output.autoExportIntervalTicks` (`autoTransfer`), through
  `ResourceHandlerUtil.move` (index-less insertion, so `GridInput` routing pairs seeds and soils; `moveStacking`
  would insert slot by slot and skip the routing).

### Plants and soils (`plant/`)
- Seed slot (`PlantRules#isPlantable`): the item places a NATIVE plant (`isSupportedPlantBlock`: CropBlock, StemBlock,
  NetherWart, SweetBerryBush, Mushroom, SugarCane, Cactus, Bamboo, Cocoa, CaveVines, ChorusFlower) or a GENERIC plant
  (`isGenericPlantBlock`, owner 2026-09-28: every other VegetationBlock, GrowingPlantHeadBlock, VineBlock,
  GlowLichen, SporeBlossom, HangingRoots, HangingMoss, BigDripleaf — never a block with a block entity: crafted modded
  "plants" would be duplicated), or is in `#virtualfarmworks:extra_plantables`; never in
  `#virtualfarmworks:unplantable` (empty by default). Config blacklists apply per tier on top.
- Generic plants' soil need (`PlantRules.SoilNeed`, cached per block, from the plant's own `canSurvive` run in a
  `VirtualLevel`): stands on dirt/grass -> ANY (natural soils + universal soils: `#virtualfarmworks:universal_soils`
  = `#minecraft:supports_vegetation`, plus every FarmlandBlock); only on farmland -> FARMLAND (crop-like, hoe on
  dirt: pitcher pod); on none -> NONE (lily pad, small dripleaf, vines, hanging plants: soil slot ignored, plots =
  seeds, `PlantAnalysis#needsSoil` false).
- Soil slot: the item places a block some plantable plant grows on, or is in `#virtualfarmworks:tillable_soils`.
  Plants are never soils. Soil-less plants and "any solid surface" rules (seagrass, kelp, leaf litter...:
  `PlantRules#definesSoils`) do not count, so ice, stone... do not become soils. Cached per item; caches clear on
  `TagsUpdatedEvent`.
- `canGrowOn`: the soil's NeoForge `canSustainPlant` hook first; generic plants: `canSurvive` in a VirtualLevel or a
  universal soil (ANY); native plants: `mayPlaceOn` (reflective MethodHandle: an access transformer breaks the MC
  recompile, 19 vanilla subclasses override it as protected) or the vanilla `#supports_*` tag. Virtual plots ignore
  light, water and neighbours. Mushrooms use `#virtualfarmworks:supports_mushrooms` (mycelium, podzol, nylium) and
  glow berries `#virtualfarmworks:supports_glow_berries` (stone, moss, dirt) because their vanilla rules accept almost
  any block.
- `VirtualLevel`: an in-memory `WorldGenLevel` (flat plane of the soil below y 64, air above, full light, no water, no
  entities, placement cap). `rules(ground)` has no server (plant rules also run on the client); `growth(...)` borrows
  the server's registries and chunk generator for tree features. Never touches the real world.
- Hoe needed = the plant cannot grow on the soil but can on farmland, and the soil is tillable (`hoe.requireHoe`).
- Game-test gotcha: a block that supports no plant is MISSING_SOIL (the slot rejects it), not INVALID_SOIL.

## Integrations

### Mystical Agriculture (9.0.9; findings from its jar and sources jar)
- VFW must never hard-depend on MA. `compileOnly` from maven.blakesmods.com (see `build.gradle`); the sources jar sits
  next to the jar in `~/.gradle/caches/modules-2/.../MysticalAgriculture/`.
- Only `compat/mysticalagriculture/` touches MA classes: `MysticalCompat` is a safe facade that never references MA
  types; `MysticalCompatImpl` and `MysticalDropSource` are loaded only when MA is present (else NoClassDefFoundError).
  MA-only game tests live in `MysticalHarvestTests` / `MysticalFarmlandTests`, called only when MA is loaded.
- `MysticalCropBlock extends CropBlock implements ICropProvider` (`getCrop()` -> `api.crop.Crop`); Agradditions
  crops use the same API. Its `getDrops` reads the REAL block below the crop, which a virtual machine does not have,
  so `MysticalDropSource` reproduces it with the soil slot's block. With `c = crop.getSecondaryChance(farmland)`:
  essence 1 (2 with chance `c`), seeds 1 (2 with chance `c` if MA's `secondarySeedDrops`), Fertilized Essence with
  MA's `fertilizedEssenceChance` — independent rolls. Inferium: essence `(int)(0.5 * v + 0.5)` for farmland tier `v`
  (+1 at 50% when `v` is even and > 1), never Fertilized Essence. The game test `mystical_drops_match_ma` compares
  VFW's reproduction statistically with MA's real `getDrops`.
- `Crop#getSecondaryChance(soil)`: 0 if the tier has no secondary seed drop; + base (crop override, else tier base,
  default 0.1) on any `IEssenceFarmland`; + 0.1 more when the crop respects effective farmland and the soil is
  effective for its tier; capped at 1. By default: 0% plain soil, 10% any essence farmland, 20% own tier farmland.
- Crux: `Crop#getCruxBlock()` (null = none needed) -> satisfied by the Crux Provider Upgrade.
- Effective farmland: MA's `requiresEffectiveFarmland` (default false) lets non-Inferium crops grow only on their
  tier's own farmland (EXACT match, a higher tier does not count) or `#mysticalagriculture:always_effective_farmland`
  (Awakened Supremium). It does not check `Crop#respectsEffectiveFarmland` (that flag only affects the secondary
  chance). VFW mirrors it behind its OWN switch `mysticalagriculture.requiresEffectiveFarmland` (owner: default off):
  `PlantAnalysis` -> `MysticalCompat#isEffectiveFarmland` -> `INVALID_SOIL`; game test `mystical_effective_farmland`.
- Farmlands: `InfusedFarmlandBlock extends FarmlandBlock implements IEssenceFarmland`; ids
  `mysticalagriculture:{inferium,prudentium,tertium,imperium,supremium,awakened_supremium}_farmland`,
  `mysticalagradditions:insanium_farmland`. Soil bonuses in the `soil_properties` data map (+15% .. +40%).

### JEI, Jade, EMI
- JEI (compile-only API, maven.blamejared.com): `client/compat/VfwJeiPlugin` — exclusion areas and ghost targets
  (screens describe them through `client/FarmMatrixJeiTargets`: filter slots, the crafter's 9 cells) and the Entropic
  crafter's recipe transfer ("+" on `RecipeTypes.CRAFTING`; sends the 9 displayed stacks, opens the panel).
- Jade (compile-only, Modrinth maven): `compat/jade/` — `@WailaPlugin`, server data provider and client tooltip in
  separate classes (a dedicated server must never load the client one); same lines as the tier's GUI
  (`menu/DisplayFormats`): Starter "Seeds x/64"; grid tiers active plots / capacity and waiting plots (optional keys);
  tiers with energy the FE shown.
- EMI: no 26.1.2 release yet; add an exclusion-area plugin when it exists.

## Performance and benchmark

- `gametest/LoadBenchmark`: detached machines ticked directly (the ticker's work; vanilla's per-BE overhead excluded),
  warm-up + best of 3. Rows: growing 1 vs 64 plots, OUTPUT FULL, harvest tick (wheat x64/x1, MA x64, poppy x64,
  oak saplings x32, crimson fungus x8), busy farm (64 wheat / 64 oak saplings, 3x), busy farm + a pipe pulling 1
  item/tick with and without a filter, revalidation, auto-export into a real chest. Never fails on numbers, but every
  timed harvest must fit one batch (hence few tree plots). Run-to-run noise is ~10-20%: compare rows within one run.
  Entropic rows (energy refilled and outputs emptied between ticks, untimed): growing (1,000 machines, like the
  Starter's rows: fewer machines left the JIT cold and read 5x too high), busy 3,840 wheat plots with and without the
  autocrafter, busy mixed farm (60 different plant/soil pairs), 60-group revalidation, an input change every tick.
- Reference (2026-09-29, after the Entropic and the owner's changes; owner's PC, every game closed, average of 3
  runs): Starter busy wheat farm 0.29 us per machine per tick (~3,500 busy machines per ms; 1,000 = 0.6% of a 50 ms
  tick); growing 0.011 us; harvest tick 67 us (64 wheat) / 4.5 us (64 MA) / 7.5 us (64 poppies) / 250 us (32 oak
  saplings, 4 trees grown); busy oak farm 1.27 us (~4x wheat); revalidation 2.8 us. A tree harvest is ~80% growth
  (~44 us per tree grown). Entropic: growing 0.036 us with 3,840 plots (the FE check and payment); busy 3,840 wheat
  plots 4.7 us (4.5 with the autocrafter), ~a quarter of 60 equivalent Starters; mixed 60 plants 2.2 us; revalidation
  of 60 groups ~12 us (median; one run 29), so a pipe feeding the grids every tick costs ~11 us per tick while it
  feeds (grids full = no change = no cost). The first row (growing, 1 plot) reads ~0.026 us only because it is
  measured first, while the JIT still warms up the tick. Confirmed on 2026-09-30 before closing the Entropic (3 clean
  runs, every row within noise; Entropic growing 0.031 us). History of results in `docs/history.md`.
- Close every Minecraft instance before benchmarking (dev clients AND the owner's modpack, e.g. ATM10): with them
  open every row came out ~2.5x slower.
- Trees were accepted as they are (owner closed the Starter on 2026-09-29 without choosing an optimization). If tree
  farms ever weigh on servers, the recorded option is a pool of grown trees per machine: grow 1 new tree per harvest
  and reuse the last ~8, about 2x cheaper with the same smooth yields (a plain `maxTreesGrownPerHarvest = 1` is as
  cheap but makes single harvests swing a lot).
- The priciest routine work is auto-export into a full neighbour (NeoForge transfer + the neighbour's inventory):
  tunable with `output.autoExportIntervalTicks`.
- Lessons: never run work after EVERY output change (the filter cleanup cost +35% that way); cache what the tick
  reads; re-run the benchmark after changes to the tick or harvest path. An `ItemResource`'s hash covers ALL its
  components (hundreds of ns): never hash items per slot or per cell in hot loops. Group slots by comparing items
  (`getItem() ==` then `equals`) and look each distinct item up once; the crafter's stock scan and cell loop cost +20%
  on a busy crafting Entropic until they did that.

## Gotchas

### 26.1.2 API — verify against sources, do not trust memory of older versions
- Sources (gitignored, deleted by `gradlew clean`): extract `build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar`
  -> `build/mcsrc/` and `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.109/*/neoforge-26.1.2.109-sources.jar`
  -> `build/neosrc/` (`tar -xf <jar>`). Vanilla data (tags, loot tables): `~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar`.
- `ResourceLocation` is `net.minecraft.resources.Identifier`. `ClickType` is `ContainerInput`. Block placement rules
  use `#supports_*` block tags (e.g. `supports_vegetation` = `#substrate_overworld` + farmland).
- `Item#appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`.
- Item data components: `DeferredRegister.createDataComponents` + `registerComponentType` (`persistent` codec,
  `networkSynchronized` stream codec). Values must be immutable with content equals/hashCode (ItemStack has none:
  `ItemStack.listMatches` / `hashStackList`). A block entity hands components to its dropped item with
  `collectImplicitComponents` + the loot function `minecraft:copy_components` (`"source": "block_entity"`,
  `include`), and takes them back on placement in `applyImplicitComponents` (`BlockItem#place`). A modded
  component shows no tooltip unless registered in `RegisterTooltipAppendersEvent` (mod bus; the value implements
  `TooltipProvider`).
- `GameTestHelper#destroyBlock` breaks WITHOUT block drops (`dropBlock` false): to test a loot table, call
  `level.destroyBlock(pos, true)`.
- Every block type needs a `MapCodec` (`codec()`).
- Item models: `assets/<ns>/items/<id>.json` (item definition) -> `models/item/<id>.json`; both required.
- Persistence: `ValueOutput`/`ValueInput`. Transfer API: `ResourceHandler<ItemResource>`, `Transaction.openRoot()`,
  `ResourceHandlerUtil.insertStacking/moveStacking`. Player slot changes go through `Slot#setByPlayer`.
- Payloads: `RegisterPayloadHandlersEvent` (mod bus), `playToServer`, handlers on the main thread by default;
  client send: `ClientPacketDistributor.sendToServer`.
- `ModConfigSpec.ConfigValue#set` changes the value in memory only (no save, no event): game tests that change config
  must restore it in the SAME tick (call `serverTick` directly in a loop), since tests of a batch run together.
- 26.1 names: the lily pad block class is `LilyPadBlock`; the dry grass ITEMS are `Items.DRY_SHORT_GRASS` /
  `DRY_TALL_GRASS` (blocks: `SHORT_DRY_GRASS` / `TALL_DRY_GRASS`); nether fungi are `NetherFungusBlock`.
- `TreeGrower` (final class) picks its feature in private methods (`getConfiguredFeature(RandomSource, boolean)`,
  `getConfiguredMegaFeature`); `SaplingBlock#treeGrower` and `NetherFungusBlock#feature/requiredBlock` are
  non-public fields: `harvest/TreeGrowth` reads them by reflection (works in dev and production, like
  `StemBlock#fruit`).

### GUI (26.1)
- Rendering is the "extract" API: `GuiGraphicsExtractor`, `extractBackground`, `extractLabels` (translated to the GUI
  origin), `extractTooltip`, `text(...)`, `fakeItem`, `fill`, `blit(RenderPipelines...)`. Colors are ARGB: always
  include the alpha byte.
- An element overlapping an earlier item goes to a higher layer: draw an item, then a translucent `fill` to fade it.
  Layers follow drawing order by overlap (`GuiRenderState`), so a panel drawn in `extractBackground` is covered by
  the ACTIVE slots drawn after it: make the slots under a modal inactive (they are then not drawn at all).
- Input: `mouseClicked(MouseButtonEvent event, boolean doubleClick)`; `doubleClick` only means "same screen and
  button within 250 ms": check yourself that both clicks hit the same thing.
- `SimpleContainer` has no listeners in 26.1: override `setChanged()` to react to changes.
- Anything drawn outside the texture must be excluded in `hasClickedOutside`, or clicks there drop the carried item.
- `ContainerData` values travel as 16-bit shorts: scale and clamp.
- To check pixel geometry without the game, paint the same fills with a script and READ THE PIXELS; image viewers
  downsize previews and hide 1 px lines. The owner's mockups may be resized (one was ~1.9x): measure and convert.

### Assets
- Hand-written JSON, no model datagen: NeoForge's `ModelProvider` would regenerate (overwrite) the owner's Blockbench
  models. When adding a block/item: `blockstates/`, `items/`, `models/item/` (flat items: parent
  `minecraft:item/generated`), lang keys in `en_us.json` (English only; the owner removed `pt_br`), loot table and
  `mineable` tag, and a line in `docs/resources.md`.

### Tooling (Windows, git, Gradle, config files)
- Pushing: Git Credential Manager on the owner's PC also holds another GitHub account, so the remote URL names the
  account (`https://redkeepp@github.com/redkeepp/virtual-farm-works.git`); without it GCM pushes as the other
  account and GitHub answers 403. The owner pushes (first time: GCM's browser sign-in, as redkeepp).
- Commits: Bash tool with a heredoc — `git commit -F - <<'EOF' ... EOF`. From PowerShell 5.1, quotes in `-m` split
  into pathspecs, `-F` with the long scratchpad path fails, piping adds a BOM. Never hide git's stderr (`2>$null`).
- `gradlew` must stay executable in git (`git ls-files -s gradlew` -> `100755`); Windows loses the bit and CI fails
  with "Permission denied". Fix: `git update-index --chmod=+x gradlew`.
- `src/main/templates/META-INF/neoforge.mods.toml` is a Groovy template: a literal dollar sign followed by `{`, even in
  a comment, breaks `generateModMetadata`.
- If `gradlew build` says UP-TO-DATE after code changes, delete the project `.gradle/` folder and rebuild.
- Long paths: `git clone` into deep folders may fail with `'$GIT_DIR' too big` — download a ZIP or use a short path.
- Python 3.13 is installed (`python` in the Bash tool; it was missing before 2026-09-30). A recursive grep over `build/`
  (sources + caches) takes minutes: grep `build/mcsrc` or `build/neosrc` subfolders instead.
- The owner usually has `runClient` open on `run/`. An old build running there "corrects" the shared config TOML and
  removes new keys: ask the owner to restart the client after code changes. When the owner edits the TOML by hand: a
  number with a leading zero (`05.0`) makes NeoForge back up the file and recreate it with DEFAULTS; an edited comment
  or out-of-range value gets rewritten. VS Code then refuses to save ("the content of the file is newer"): close the
  tab without saving and reopen. Comments in the TOML come from `VfwServerConfig`; change them there (a changed
  comment makes FML "correct" the file once, with a .bak backup). After every load `config/ConfigFileLayout` adds a
  blank line before each setting (owner: readability); NightConfig cannot, FML's own rewrites drop them and the next
  load puts them back; the formatted file stays "correct", so no reload loop.

## Code map

- `VirtualFarmWorks` — entry point; only wires registries, config, data maps, payloads and game tests.
- `machine/` — `FarmMatrixBlockEntity` (the running machine, every tier), `MachineLayout` (per tier: plot groups,
  slot indices, visible output size, features; groups = 1 reproduces the Starter's persisted slot order),
  `MachineInventory`, `OutputBuffer` (visible output), `InternalBuffer` (hidden output), `MachineEnergy` (FE buffer),
  `FaceMode` (NONE/OUTPUT/OUTPUT CRAFTED/OUTPUT ALL/INPUT, ordinal persisted), `GridInput` (pipe input routing),
  `MachineCrafter` (autocrafter), `CrafterRecipes` (its recipes on a broken machine's item), `MachineFilter`
  (harvest filter), `MachineSlots` (Starter indices — persisted, never
  reorder), `MachineTier` (order = progression, `accepts()`), `RelativeSide` (faces relative to the front; order
  persisted).
- `block/FarmMatrixBlock` — one block class for all tiers (tier is a constructor arg).
- `item/` — `TieredUpgradeItem` (+ `UpgradeType`), `CruxProviderUpgradeItem`; items carry no behavior.
- `registry/` — `ModBlocks`, `ModItems`, `ModBlockEntities` (+ capabilities), `ModMenus`, `ModCreativeTabs`,
  `ModDataComponents` (item data components + their tooltip appenders).
- `config/` — `VfwServerConfig` (spec + comments pack makers read), `VfwConfig` (runtime: compiled filters,
  generation, file layout), `ConfigFileLayout` (blank line before each setting), `ItemFilter`.
- `data/` — `ModDataMaps` with `SoilProperties` (soil growth bonus) and `FixedYield` (fixed harvests) data maps.
- `plant/` — `PlantRules`, `SoilRules`, `PlantAnalysis`, `SoilView` (2-block BlockGetter for native rules),
  `VirtualLevel` (in-memory WorldGenLevel for generic rules and tree growth), `VfwTags`.
- `sim/` — Minecraft-free core: `GrowthCycle`, `PlotGroup`, `GrowthSpeed`, `MachineStatus`, `MachineConditions`,
  `HarvestMath`, `DropTally`, `HarvestBatching`, `YieldSample`.
- `harvest/` — `DropSource`, `LootDropSource`, `TreeDropSource` + `TreeGrowth`, `FixedDropSource`, `HarvestPlans`,
  `Harvester`, `HarvestFilter`.
- `menu/` — `AbstractFarmMatrixMenu` (shared: sync, ghost/display slots, common buttons), `FarmMatrixMenu` +
  `FarmMatrixLayout` (Starter), `EntropicFarmMatrixMenu` + `EntropicLayout` (Entropic), `FilterPageView`,
  `DisplayFormats`.
- `network/` — `SetFilterGhostPayload`, `SetCrafterGridPayload` (client -> server), `CrafterRecipesPayload` (server ->
  client).
- `client/` — CLIENT ONLY: `VirtualFarmWorksClient` (second `@Mod`, dist CLIENT), `FarmMatrixScreen`,
  `EntropicFarmMatrixScreen`, `FarmMatrixJeiTargets`, `SmoothProgress`, `GuiScaleFit`, `compat/VfwJeiPlugin`. Never reference
  `client/` from common code (a dedicated server crashes).
- `compat/jade/`, `compat/mysticalagriculture/` — see Integrations.
- `src/gametest/java/com/virtualfarmworks/gametest/` (dev only, never in the release jar) — `GameTestsEntry` (its
  `@Mod` entry), `VfwGameTests` (registration: add a `test(...)` line with a max tick count), `MachineGameTests`,
  `FilterGameTests`, `PlantablesGameTests`, `EntropicGameTests`, `CrafterGameTests`, `MysticalHarvestTests`,
  `MysticalFarmlandTests`, `LoadBenchmark`.
  `FarmMatrixBlockEntity#setProgressForTesting` exists only for tests.
- `src/test/java/com/virtualfarmworks/` — JUnit (`sim/`, `client/SmoothProgress`, `client/GuiScaleFit`,
  `config/ConfigFileLayout`).
- `src/main/resources/data/virtualfarmworks/` — tags, data maps, recipes, loot tables (see `docs/resources.md`).
- `src/main/templates/META-INF/neoforge.mods.toml` — mod metadata: description, links, authors, optional
  dependencies (Gradle expands it from `gradle.properties`; the logo line waits for the owner's logo).

## Docs (read the specs before implementing)

- `docs/specs/starter-farm-matrix.md` — the Starter spec and every owner decision since (GUI, output, filter,
  plantables).
- `docs/specs/entropic-farm-matrix.md` — the Entropic spec (owner's rules, GUI coordinates, decisions, stages).
- `docs/specs/configurability.md` — every pack-maker setting and where it lives.
- `docs/resources.md` — what each JSON resource does and who authored it.
- `docs/history.md` — how milestone 1 was built, step by step, and the benchmark results log.
