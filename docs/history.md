# Development history

Chronological log of how the Starter Farm Matrix was built (milestone 1), moved out of `CLAUDE.md` on 2026-09-28 to
keep that file short. `CLAUDE.md` describes the CURRENT state; this file explains how it got there and why. Entries
are kept as written at the time, with notes where something changed later.

## Milestone 1 — Starter Farm Matrix

Steps agreed with the owner: (1) registration (2) config + soil data map (3) plant/soil resolver (4) simulation core +
unit tests (5) aggregated transactional harvest (6) BlockEntity, persistence, I/O, auto-export (7) GUI (8) finishing
and in-game tests. Scope: Starter tier only, to validate the architecture before the other tiers.

- Project scaffolded from the official NeoForge 26.1.2 MDK, renamed to `virtualfarmworks`. The owner's Starter spec
  was received (`docs/specs/starter-farm-matrix.md`), open questions answered, go-ahead given.

- **Step 1 — registration.** Starter Farm Matrix block (directional), all 5 tiers of Water Provider and Growth Speed
  upgrades, Crux Provider Upgrade, creative tab, lang, blockstate/item JSON, loot table, pickaxe tag. Growth upgrades
  are registered as `<tier>_growth_upgrade` to match the owner's texture names. (Later: the owner removed `pt_br`;
  only `en_us` exists. Recipes came in step 8.)

- **Step 2 — config and soil data map.** `config/VfwServerConfig`, `VfwConfig` (compiled blacklists + generation
  counter), `ItemFilter`; soil data map (`data/SoilProperties`, `ModDataMaps`) with MA/Agradditions farmland bonuses
  (Awakened Supremium = +40%, same as Insanium, owner decision). The MA extra-seed option is a MULTIPLIER over MA's
  own chance; its config comment has worked examples (owner asked for pack-maker-friendly examples).

- **Step 3 — plant/soil resolver** (`plant/`): `PlantRules` (plantable items; `canGrowOn` = the soil's NeoForge
  `canSustainPlant` hook first, then the plant's own `mayPlaceOn` via a cached reflective MethodHandle, or vanilla
  `#supports_*` tags for non-VegetationBlock plants), `SoilRules` (hoe detection, tillable soils, cached soil-slot
  acceptance), `PlantAnalysis` (seed + soil -> MISSING_SEED / MISSING_SOIL / INVALID_SOIL / VALID + needsHoe,
  needsCrux, soil multiplier), `SoilView` (2-block virtual BlockGetter). MA crux via `compat/mysticalagriculture`.
  Why no access transformer for `mayPlaceOn`: making it public breaks the MC recompile (19 vanilla subclasses
  override it as protected).

- **Step 4 — simulation core** `sim/` (pure Java): `GrowthCycle` (global progress 0..1, `PlotGroup`s with
  active/pending, exploit rules, carry-over capped so at most one harvest per tick, 1e-9 "due" tolerance so
  600 x 1/600 is exactly one cycle), `GrowthSpeed` (hydration x upgrades x soil), `MachineStatus` (priority =
  declaration order) + `MachineConditions`. Status priority decided by Claude (owner may revisit): SHUTDOWN > the
  owner's missing hierarchy > OUTPUT FULL > RUNNING. `noWaterSpeedMultiplier` min is 0.01 (0 would show RUNNING
  without ever advancing; no "missing water" state exists).

- **Step 5 — aggregated transactional harvest** (`harvest/`): `HarvestPlans` (drop source per seed/soil, built on
  revalidation), `LootDropSource` (plant loot table, no entities, TOOL = EMPTY so the hoe never changes yields, sampled
  to `performance.maxLootRollsPerHarvest` and scaled), `MysticalDropSource` (MA formula), `Harvester` (`roll` with
  config multipliers, `tryStore` = root NeoForge Transaction + `insertStacking`, all-or-nothing). Pure math in
  `sim/HarvestMath` + `sim/DropTally`. Tag `#virtualfarmworks:harvest_byproducts`.

- **Step 6 — block entity** `machine/FarmMatrixBlockEntity`: server ticker; revalidates only on slot/config/tag
  change; one addition per normal tick; harvest rolled once and retried only when buffer or inputs change; auto-export
  via `BlockCapabilityCache` every `output.autoExportIntervalTicks`; persistence; progress saved at most every 20 ticks
  via `level.blockEntityChanged`; contents dropped in `preRemoveSideEffects`. `MachineInventory`, `OutputBuffer`,
  `MachineSlots`, `RelativeSide`; one BE type "farm_matrix" for all tiers + item capability. Owner decisions (step 7):
  automation can only EXTRACT from the output buffer (Starter inputs not exposed); auto-export keeps running while
  SHUTDOWN; faces are relative to the player looking at the front; the hoe only wears while it is actually needed.

- **Step 7 — GUI.** `menu/FarmMatrixMenu` (slots at the owner's coordinates, shift-click, `ContainerData` synced every
  5 ticks + on button press, button intents via vanilla `clickMenuButton`), `menu/FarmMatrixLayout`,
  `client/FarmMatrixScreen` (texture, texts, green bar, 40% ghost placeholders, side panel), `client/
  VirtualFarmWorksClient`, `registry/ModMenus`. Owner decisions: no input automation on the Starter (future tiers may
  add it); hoe wear is time-based (`hoe.wearIntervalTicks`, default 1200). Claude's GUI choices (owner may revisit):
  "Seeds" shows planted plots = min(seeds, soils); "Growth" multiplier includes the soil bonus; face labels
  T/L/F/R/Bk/Bt + tooltips; no "Inventory" label. The owner tested in game and revised the side column: glued to the
  texture (no theme border on its right side), the 5 upgrade slots merged in one block with single blue separators
  (17 px pitch), 3 px between boxes. The owner then made the texture 3 px taller and re-spaced the info lines.

- **Step 8 — finishing** (many owner requests, in order):
  - Recipes (Starter Farm Matrix, Starter Water Provider, Starter Growth Speed, Crux Provider) + recipe-book unlocks.
  - Fertilized Essence switch per machine (button id 7, part of the harvest key). Right-click with an upgrade pulls
    it in (falls through to the GUI when nothing fits). Title and info-line nudges.
  - JEI exclusion areas (`client/compat/VfwJeiPlugin`), Jade tooltip (`compat/jade/`). EMI has no 26.1.2 release.
  - MA "requiresEffectiveFarmland": VFW's own switch, default off (owner). The two ON/OFF boxes 3 px apart (owner
    first said 5). Tooltip "Drops Fertilized Essence: ON/OFF".
  - Owner verified config reload with the game open and the dedicated server by hand (no automated reload test).
  - Config comment: the machine does not imitate natural growth times; no "physical farm" promise (owner).
  - **Output deadlock fix** (owner's overclock report): a whole harvest stored all-or-nothing never fit when bigger
    than the 9-slot buffer, even empty. The owner rejected a big hidden overflow inventory, then chose the middle
    ground: visible 9 slots -> hidden `InternalBuffer` (Starter 27, deleted on break, owner rule) -> ripe plots wait
    on the plant, harvested in batches (`HarvestBatching`, `YieldSample`); extreme case holds one batch's excess
    (`heldDrops`, owner-approved). No GUI/Jade indicator (owner). Info lines moved up 1 px.
  - **Load benchmark** (`gametest/LoadBenchmark`, `gradlew runBenchmark`), then game tests and benchmark moved to
    `run-gametest/` (the owner's manual config in `run/config` made two tests fail and test runs rewrote it).
  - **Smooth progress bar** (`client/SmoothProgress`), then fixed: no race from 0 when the GUI opens
    (`FarmMatrixMenu#isDataSynced`), and a harvest counter (data slot 8) so a cycle wrap runs the bar to 100% first.
    The owner compared both versions in game (git checkout of the previous commit) and kept the smooth one.
  - **Harvest filter**: whitelist/blacklist per machine, 3x3 ghost slots per page (the owner's "9x9" meant 9 slots,
    clarified by a mockup), up to 16 pages, empty list = no filtering in both modes, JEI drag-and-drop (custom
    payload), never produced rather than produced-then-deleted. Then revised: the box opens next to its own button
    (bottom aligned, both boxes can be open), the visible output accepts items by hand, and the machine deletes what
    the filter rejects from the output.
  - **Performance fix**: the filter cleanup first ran after every output change (+35% per tick with a pipe on a
    filtered machine); now only on filter change, hand placement or load. Measured before/after with a new benchmark
    scenario.
  - CLAUDE.md reorganized by topic (this log moved here), 2026-09-28.
  - **Every plantable** (2026-09-28). The owner asked which plantables were refused (66 vanilla items), then decided:
    accept them all; the 59 that stand on soil grow on dirt/grass/any farmland (the vanilla vegetation soils) plus
    their natural soils, conditions (water, coral, nylium) dropped; the 7 that stand on nothing need no soil (slot
    ignored); trees yield a real tree's drops by hand, one sapling per tree (dark/pale oak included), no replanting;
    other plants yield 10 of themselves; Torchflower Seeds / Pitcher Pod yield 10 flowers; crops keep their 1-seed
    cost. The owner also previewed the future "replanting" (Voltaic: seeds planted into free soils automatically).
    Built: generic plant detection by class, soil needs read from each plant's `canSurvive` in a new in-memory
    `VirtualLevel`, trees grown there with their own feature (`TreeGrowth`, `TreeDropSource`), `FixedDropSource`,
    the `fixed_yield` data map, `#virtualfarmworks:universal_soils`. Claude's safety choices: no block-entity plants,
    no block-entity blocks from trees, "any surface" rules do not create soils. 6 new game tests
    (`PlantablesGameTests`), benchmark rows for poppies, oaks and fungi.
  - **Starter declared done by the owner** (2026-09-29), after three clean benchmark runs. Trees kept as they are (a
    busy oak farm costs ~4x a wheat farm); the tree-pool optimization is recorded in CLAUDE.md for later.

## Milestone 2 — Entropic Farm Matrix (2026-09-29)

The owner's spec (two 4x15 grids of plot groups, 3,840 plots, FE, face modes, automated input, replant, autocrafter
with JEI) and every decision are in `docs/specs/entropic-farm-matrix.md`. Built in stages, each committed after the
game tests passed:

- **Stage 1 — multi-group machine.** `machine/MachineLayout` describes a tier (plot groups, slot indices, features);
  `FarmMatrixBlockEntity` generalized from one seed/soil pair to N plot groups on ONE growth bar (groups with a
  problem hold no plots; groups with the same seed and soil are harvested together; soil bonus = plot-weighted
  average). The Starter keeps its exact behavior and save format (layout with 1 group). New: `MachineEnergy` (90 FE
  per plot per tick, buffer = capacity x 90 x 3), `FaceMode` (5 modes per face), `GridInput` (pipes fill slots
  already holding the item, then pairs, then empty slots).
- **Stage 2 — GUI.** `EntropicFarmMatrixMenu`, `EntropicLayout` (owner's texture coordinates, checked on the
  pixels), `EntropicFarmMatrixScreen` (striped FE bar that falls gradually, red tint and tooltip on groups with a
  problem, side column with face box, filter box and the crafting-table button); shared menu logic moved to
  `AbstractFarmMatrixMenu`.
- **Stage 3 — replant.** Produced plantables are planted into free soil of groups already holding them, before the
  harvest filter; they grow from the next cycle.
- **Stage 4 — autocrafter.** `MachineCrafter` (recipes, CRAFT switch, hidden buffer, chain order by strongly
  connected components so circles cannot loop, substitute items like a crafting table, transactional plan/commit);
  the owner's crafter panel as a modal over the dimmed grids; JEI "+" and drag-and-drop (`SetCrafterGridPayload`);
  crafted items are what OUTPUT CRAFTED exports and what the harvest filter never removes. 3 game tests.
- **Stage 5 — benchmark and docs.** Six Entropic rows in `LoadBenchmark` (see the log below).
- **Owner's answers and first in-game test (same day).** The owner answered the seven stage questions late: the
  machine runs only when EVERY group is valid (Claude had recommended "runs while one group grows"; now a blocked
  group freezes the whole bar, the Starter's rule generalized: `updateStatus` aggregates the groups), no hoe at all in
  this tier (tools slot reused for the catalyst, saves compatible), replant option (b) (also empty seed slots above a
  suitable soil) with a button per machine (the config only allows it). After playing: up to 100 recipes with a
  dynamic list (a server-to-client payload instead of 64 fixed display slots), a catalyst slot for the Master
  Infusion Crystal (MA essence tiers), smaller button text, the FE tooltip reduced to "current/total" and shown full
  while a source keeps up (it read one tick of use short), a lightning box with "Uses X FE/t per active plot" /
  "Using X FE/t", INPUT faces pulling from glued inventories, info lines one pixel lower. 3 new game tests.
- **Second round of owner changes (same day).** The autocrafter uses whatever the output holds, even its own earlier
  results (the output is a stock the plan may take from, removed in the same transaction that stores the results;
  circles never take back their own results); Jade shows the Entropic's active plots / capacity, waiting plots and
  FE instead of the Starter's "Seeds: x/64"; the lightning icon redrawn from the owner's pixel drawing. The first
  version of the stock hashed every output slot and every candidate per cell and made a busy crafting Entropic ~20%
  slower (an item's hash covers all its components); grouping slots by comparing items and building the candidates
  once per round brought it back below the plain machine. Game test `crafter_uses_the_output`.
- **FE drains to 0 (2026-09-30, owner).** With the source cut, the buffer used to stop at the remainder smaller than
  one tick (e.g. 1,800 FE with 100 plots). Now a tick the buffer cannot pay in full spends what is left and moves the
  bar that fraction (status MISSING FE, later RUNNING WITH LOW FE), so the buffer ends at exactly 0 and no FE is
  stranded or wasted; a weak source grows the farm proportionally slower instead of stop-and-go. The lightning icon
  became red (the owner's drawing was only an example color). Game test `entropic_energy_drains_to_zero`. A crash
  reported while exporting into a Trash Cans "Item Trash Can" was traced to Trash Cans 1.1.1 itself (its
  deleted-items codec returns a null component patch; reproduced with a plain handler insertion, no VFW machine), not
  to VFW.
- **Config file layout (2026-09-30, owner).** A blank line before every setting of `virtualfarmworks-server.toml`
  (`config/ConfigFileLayout`, applied after FML loads the file); the owner then tested the config values in game.
- **Recipes (2026-09-30, owner's costs).** Entropic Farm Matrix (netherite ingots, diamonds and redstone around a
  Starter Farm Matrix), Entropic Water Provider Upgrade (the Starter recipe around a Starter Water Provider Upgrade)
  and Entropic Growth Speed Upgrade (redstone blocks and diamonds around a Starter Growth Speed Upgrade), each with
  its recipe-book advancement. `recipes_are_loaded` now crafts every owner grid, Starter ones included.
- **Recipes stay on a broken machine (2026-09-30, owner OK).** The autocrafter's recipes (up to 100) used to be
  lost with the machine; now its item keeps them in an item data component (`machine/CrafterRecipes`, copied by
  the loot table, tooltip "Autocrafter recipes: N") and a machine placed from it gets them back. Waiting
  ingredients are still deleted. Game test `crafter_recipes_stay_on_the_item`.
- **RUNNING WITH LOW FE and the GUI scale (2026-09-30, owner).** A buffer holding less than a tick now shows
  RUNNING WITH LOW FE (yellow; the bar moves the part the FE pays); MISSING FE means an empty buffer. The Entropic
  GUI (296x320) never fit at the automatic GUI scale (240-270 px high): while its screen is open the GUI scale now
  drops to the largest that fits (`client/GuiScaleFit`) and the player's own comes back on close. Growth Speed
  Upgrades keep one bonus for every tier (owner).
- **Entropic declared done by the owner** (2026-09-30), after testing the last changes in game and on a dedicated
  server (the custom packets included) and a final clean benchmark (no regression). The energy buffer's x 3 stays
  in code (owner). Git tag `entropic-complete`.

## Preparing the first release (2026-09-30)

The owner asked what the repository lacks compared with large mods (Applied Energistics 2, Mekanism, Create, Farmer's
Delight, Mystical Agriculture, JEI; checked through the GitHub API) and approved the code and metadata part first:

- **Unbuilt tiers hidden.** The Voltaic, Ionic and Resonant upgrades were registered, in the creative tab and in JEI,
  without recipes or machines. `MachineTier#isBuilt` (Starter, Entropic) now decides which tiers register items; the
  "Fits:" tooltip names only those. Their art and names stay in the assets. Game test `only_built_tiers_have_items`.
- **Clean release jar.** The game tests and the benchmark moved to their own source set (`src/gametest`, a dev-only
  `@Mod` entry `GameTestsEntry`; `gradlew build` still compiles them); the owner's old textures (`antigos_nao_usar`)
  stay in the repository but out of the jar; the jar carries the MIT license. 273 files and 522 KB before, 258 files
  and 413 KB after.
- **Metadata.** `neoforge.mods.toml` without the MDK examples: description, authors, home page and issue tracker
  (`gradle.properties`), JEI, Jade and Mystical Agriculture as optional dependencies with the built-against versions as
  minimums. The logo line waits for the owner's logo.
- **Version 1.0.0**, file `virtualfarmworks-26.1.2-1.0.0.jar` (Minecraft version in the name, like large mods).
- **Presented as Redkeep's** (owner): author Redkeep in the metadata and the LICENSE. The owner then moved the
  repository to Redkeep's GitHub account (`redkeepp/virtual-farm-works`; `redkeep` was taken): home page and
  issue links point there, and new commits are made as Redkeep.
- **History rewritten as Redkeep** (owner OK): every commit's author and committer and both tags
  (`starter-complete`, `entropic-complete`) now name Redkeep, and old versions of LICENSE, CLAUDE.md and
  gradle.properties name Redkeep and the new repository instead of the previous account. Everything else (files,
  messages, dates) is unchanged, checked commit by commit. Every commit hash before this point changed.
- Left for the owner: logo, CurseForge/Modrinth projects, GitHub description and topics, the art license decision.
- **Public face (same day, owner OK).** A README for players and pack makers (machines, plants, upgrades, recipes,
  mod support, measured performance, config, requirements), a CHANGELOG with the 1.0.0 section, and GitHub issue
  forms (bug report with versions and log link, feature request, no blank issues). Next, when the platforms
  exist: an automatic release workflow.
- **1.0.0 released on GitHub** (owner, 2026-09-30), without a logo. Then the owner's logo (in the mods list,
  pixel art kept sharp) and three screenshots (README, platform galleries), the CurseForge/Modrinth page texts in
  `docs/publishing.md`, and `mod_version` 1.0.1 for the next release. License: the owner wants it as free as
  possible; MIT stays (code and assets), modpacks welcome without asking.

## Port to 1.21.1 (branch 1.21.1, from 2026-10-02)

The owner keeps a 1.21.1 line for All the Mods 10 (CLAUDE.md, "Two Minecraft versions"). The plan, with the areas,
their weight and the order, is `docs/port-1.21.1.md`; this section records how each step went and why. Every API was
checked against the 1.21.1 sources (NeoForge 21.1.251's `neoforge-21.1.251-sources.jar`, which holds Minecraft and
NeoForge together; FML 4.0.44 read with `javap`), never against what holds for 26.1.

### Step 1 — build and base
- Versions from ATM10 (`gradle.properties`), Java 21 toolchain (a JDK 21 is installed; JAVA_HOME may stay on 25, it
  only runs Gradle), the 1.21.1 MDK's `data()` run type (`clientData()` is 1.21.4+), JDK 21 in CI.
- FML 4 refuses a mod file without `modLoader` and `loaderVersion` ("Missing ModLoader in file", read in
  `ModFileInfo`); later FML versions dropped them, so the 26.1 template had neither. Added, with the range in
  `gradle.properties` (`loader_version_range=[4,)`, as in the 1.21.1 MDK).
- NeoForge runs Minecraft with its official names in production since 1.20.5 (checked: Mystical Agriculture 8.0.28's
  jar calls `CropBlock.randomTick` by name), so VFW's reflection by official names (stem fruit, tree growers,
  `mayPlaceOn`) keeps working in 1.21.1 jars.
- Renames: `Identifier` -> `ResourceLocation`, `ContainerInput` -> `ClickType`, `NetherFungusBlock` -> `FungusBlock`,
  `VegetationBlock` -> `BushBlock`, `FMLEnvironment.isProduction()` -> `FMLEnvironment.production`,
  `ClientPacketDistributor` -> `PacketDistributor`, `Registry#getValue` -> `get`, `ItemStack#typeHolder` ->
  `getItemHolder`. jspecify is not on the 1.21.1 classpath: `org.jetbrains.annotations.Nullable` (also a TYPE_USE
  annotation, what NeoForge 21.1 itself uses) replaces it, so `ModConfigSpec.@Nullable IntValue` still compiles.

### Step 2 — inventories and energy
- 1.21.1 has `IItemHandler` (one call at a time, simulate or execute) instead of the 26.1 transfer API. A harvest
  batch holds several item types and must be stored all-or-nothing, and simulating it item by item would let two
  items be promised the same empty slot. Since the harvest only ever stores into the machine's OWN buffers, VFW plans
  the whole batch on `transfer/SlotTransaction` (its own view of each slot it changed) and writes the slots only on
  commit: one `setStackInSlot`, hence one change callback, per changed slot; a batch that does not fit changes nothing
  and fires no callback (otherwise the failed attempt would look like an output change and the machine would retry
  every tick). Same for the autocrafter taking from the output, the refill of the visible output and upgrades taken
  from the hand. Neighbours (other mods) get plain simulate-then-execute calls: auto-export inserts a copy with
  `insertItemStacked` and keeps the rest; INPUT faces simulate the extraction and the grid insertion first and only
  extract what the grids accept (what a broken neighbour hands over beyond that goes back to it, or drops: never
  voided).
- `transfer/ItemResource` replaces NeoForge 26.1's `ItemResource` with the same method names (`of`, `EMPTY`, `getItem`,
  `toStack`, `getMaxStackSize`, `CODEC`), so the shared logic (harvest, autocrafter, held drops) reads the same on both
  lines and a fix can be carried over. Its hash is cached per object, but every `of(stack)` is a new object, so hot
  loops compare stacks in place with `matches` (the output stock scan, the replant plan, the grid routing) instead of
  building a resource per slot.
- `transfer/ItemSlots` (base of the machine's inventories) extends NeoForge's `ItemStackHandler` and keeps the 26.1
  accessor names. Two 1.21.1 traps it handles: vanilla's item codec refuses counts above 99 (grid slots may hold more:
  each slot is saved as resource + count), and vanilla's shift-click merge grows a slot's stack IN PLACE and only
  calls `Slot#setChanged`, which a plain `SlotItemHandler` sends to a dummy container — the machine would not notice.
  `menu/HandlerSlot` forwards it (`ItemSlots#slotChanged`) and uses the inventory's own limit, so grid slots configured
  above a stack fill above a stack by hand too.
- Pipe input: 1.21.1 handlers have no index-less insertion, which `GridInput` used to route seeds and soils. Pipes,
  hoppers and `insertItemStacked` insert slot by slot, so every insertion into `GridInput` is routed whatever slot it
  names; the caller only looks at what is returned. Simulated and real insertions plan the same way.
- Energy: `MachineEnergy` extends NeoForge's `EnergyStorage` (int, like 26.1's `SimpleEnergyHandler`); insertion by
  cables reports the change; the capacity is clamped to `Integer.MAX_VALUE`.

### Step 3 — saving
- 26.1's `ValueOutput` / `ValueInput` become a `CompoundTag` plus the registries (`saveAdditional` /
  `loadAdditional(CompoundTag, HolderLookup.Provider)` in 1.21.1). The keys are main's, so the two lines stay easy to
  compare, though saves never move between Minecraft versions.
- `machine/Saves` holds what 26.1's value classes did for free: a codec value written under a key with registry ops
  (held drops and the crafter's buffer carry item components, the crafter's recipes recipe ids), a failed value logged
  and left out instead of breaking the save, and booleans that default to true when missing (NBT's own default is
  false: a machine saved without the key would come back switched off).
- The filter decodes its entries one by one, as 26.1's `listOrEmpty` did, so one unreadable entry costs only itself.
- The energy amount is restored as saved: the capacity is set by the first revalidation, which also trims an amount
  above a lowered capacity (clamping at load, before the capacity is known, would empty every buffer).

### Step 4 — recipes and autocrafter
- 1.21.1 identifies recipes by `ResourceLocation` (`RecipeHolder#id`), not by `ResourceKey<Recipe<?>>`: the
  autocrafter's recipe hint and its saved form (`ResourceLocation.CODEC`, key "recipe") follow. The lookup is
  `level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, hint)`, which prefers the hinted recipe
  while it still matches, as 26.1's `recipeAccess()` did. `assemble` takes the registries.
- 26.1 refused recipes whose `placementInfo()` is impossible to place. 1.21.1 has no placement info; its recipe book
  shows a recipe when `!isSpecial() && !isIncomplete()` (`ClientRecipeBook`), i.e. it has ingredients and every one of
  them has items. The autocrafter uses that same test.
- `CraftingInput.ofPositioned` and `getRemainingItems` exist in 1.21.1 as in 26.1: the catalyst bookkeeping (remainders
  indexed by the TRIMMED input) is unchanged.
- Recipe JSONs: 1.21.1 reads ingredient objects (`{"item": ...}`), checked against vanilla 1.21.1's own recipes; the
  results already used `{"id": ...}`. Advancements are unchanged (same format in vanilla 1.21.1).

### Step 5 — plants and tags
- 1.21.1 has no `#minecraft:supports_*` block tags: each plant's soil rule is code. Read from the 1.21.1 sources:
  `BushBlock#mayPlaceOn` = `#minecraft:dirt` or any `FarmBlock`; crops and stems = any `FarmBlock`; nether wart =
  soul sand; sugar cane = `#minecraft:dirt` or `#minecraft:sand` (+ water, ignored); cactus = `#minecraft:sand`;
  bamboo = `#minecraft:bamboo_plantable_on`; cocoa = `#minecraft:jungle_logs`; chorus flower = end stone. `PlantRules`
  uses exactly these for the plants that are not BushBlocks and as the fallback when `mayPlaceOn` cannot be called.
- NeoForge 21.1's `canSustainPlant` returns its own `net.neoforged.neoforge.common.util.TriState` (26.1 moved it to
  vanilla). The farmland class is `FarmBlock` (renamed `FarmlandBlock` later); Mystical Agriculture 8's farmlands
  extend it, so "every farmland counts" still covers them.
- Tag defaults: `universal_soils` = `#minecraft:dirt` + farmland (the same blocks as 26.1's `supports_vegetation`, minus
  pale moss, which does not exist); `supports_mushrooms` = `#minecraft:mushroom_grow_block` (the later
  `overrides_mushroom_light_requirement`); `supports_glow_berries` names the moss block (no `#minecraft:moss_blocks`).
- `VirtualLevel` against 1.21.1's `WorldGenLevel` hierarchy (listed with `javap`): `getMinBuildHeight` (MUST be
  overridden: the default asks `dimensionType()`, which a rule-check level without a server cannot answer),
  `getShade`, `playSound` / `levelEvent` with a `Player`, no environment attributes.
- The private fields and methods read by reflection have the same names in 1.21.1 (`StemBlock#fruit`,
  `SaplingBlock#treeGrower`, `FungusBlock#feature` / `requiredBlock`, `TreeGrower#getConfiguredFeature` /
  `getConfiguredMegaFeature`).

### Step 6 — GUI, blocks and items
- 26.1 draws through an "extract" API that layers elements by overlap in drawing order; 1.21.1 draws at once through
  `GuiGraphics`, with depth. `AbstractContainerScreen#render` draws the background (`renderBg`), then the active slots,
  then `renderLabels` translated to the GUI origin; screens call `renderTooltip` themselves at the end of `render`. The
  code keeps 26.1's structure: `extractBackground` -> `renderBg`, `extractLabels` -> `renderLabels`,
  `extractTooltip` -> `renderTooltip`; the private helpers are `draw*`.
- Items are drawn in front of the flat GUI (z 150), so a fill drawn after an item does not cover it. The 40% ghost
  items (and the greyed-out replant icon) get their cover twice: a plain fill for the cell around the item, and the
  same color through `RenderType.guiGhostRecipeOverlay()`, which only draws where something is in front (vanilla's
  recipe-book ghost technique). Together they equal 26.1's single cover over the whole cell.
- The Entropic's crafter panel still works as a modal: vanilla does not draw inactive slots, so the grid items under
  the panel are not drawn and the panel's own slots, drawn after the background, are on top.
- Input: `mouseClicked(double, double, int)`; 1.21.1 passes no double-click flag, so the Entropic screen measures it
  (two left clicks within vanilla's 250 ms) and still requires both clicks on the same recipe.
  `hasClickedOutside` takes the button too.
- GUI scale fit (owner, 2026-09-30): same flow as 26.1 (`Minecraft#setScreen` calls the old screen's `removed()`, then
  the new one's `added()`, then sizes it), with 1.21.1's `double` scale and `resize(Minecraft, int, int)`.
- Blocks and items: `useItemOn` returns an `ItemInteractionResult` (`PASS_TO_DEFAULT_BLOCK_INTERACTION` opens the GUI as
  26.1's `TRY_WITH_EMPTY_HAND` did); the contents drop in `FarmMatrixBlock#onRemove` (26.1:
  `BlockEntity#preRemoveSideEffects`), before the block entity goes, only when the block itself changes; the loot table
  still reads the captured block entity, so the recipes stay on the item. 1.21.1 shows no tooltip for modded item
  components and has no `RegisterTooltipAppendersEvent`: a block item asks its block, so
  `FarmMatrixBlock#appendHoverText` adds "Autocrafter recipes: N" (no client event needed).
  `applyImplicitComponents(DataComponentInput)`,
  `BlockEntityType.Builder.of(...).build(null)`, `registerBlock(name, factory, Properties)`.
- Item models: 1.21.1 reads `models/item/<id>.json` only (26.1's `items/` definitions are ignored), so the two machines
  got `models/item/*_farm_matrix.json` with the block model as parent.
- Found while checking assets: the owner's block models use 26.1's multi-axis element rotations (90 / 180 degrees),
  which 1.21.1's model reader refuses ("Missing axis"). Left for the owner (rule 7), see the plan; solved after step 8
  ("Block models", below).

### Step 7 — integrations
- JEI 19: the recipe type is `mezz.jei.api.recipe.RecipeType` (26.1's JEI calls it `IRecipeType`); the crafting type
  is the same `RecipeHolder<CraftingRecipe>`. JEI 19 marks the 6-argument `transferRecipe` for removal but still
  declares it abstract (its context form calls it): implemented, with the warning suppressed and explained. The "+"
  refuses what the autocrafter refuses (`isSpecial() || isIncomplete()`).
- Jade 15: same provider API; the tooltip reads the server data with 1.21.1's `CompoundTag` getters (0 when missing)
  and its own default for the two multipliers.
- Mystical Agriculture 8.0.28 (ATM10's), checked in its bytecode with `javap -c` (no sources jar on its maven):
  `MysticalCropBlock#getDrops` and `InferiumCropBlock#getDrops` are exactly the formulas `MysticalDropSource`
  reproduces; `Crop#getSecondaryChance` adds the tier's base (0.1 by default) on essence farmland and 0.1 more on the
  effective one, capped at 1; `canGrow` checks the crux two blocks down and, with `requiresEffectiveFarmland`, the
  tier's farmland: in MA 8 `CropTier#isEffectiveFarmland` is an exact match only (MA 9's always-effective tag does not
  exist yet), and VFW calls MA's own method, so it follows the installed version. The Master Infusion Crystal extends
  Cucumber's `BaseReusableItem` (unbreakable: its remainder is itself), so the catalyst rule holds. Farmland classes
  extend `FarmBlock`. MA 8 has no Awakened Supremium farmland: its `soil_properties` entry would make NeoForge log an
  error on every reload (`DataMapLoader` resolves values as required), so the 1.21.1 data map leaves it out.
- Area 11 (config): NeoForge 21.1 loads SERVER configs from `config/` with an optional per-world override in
  `<world>/serverconfig/` (`ServerLifecycleHooks`), so the file is where main has it. The pack-maker comments now name
  MA 8.0.x and drop the always-effective tag lines.

### Step 8 — tests and benchmark
- JUnit (the Minecraft-free core, `GuiScaleFit`, `SmoothProgress`, `ConfigFileLayout`) passed unchanged on Java 21:
  86 tests.
- Game tests: 1.21.1 has no test-function registry. NeoForge's `RegisterGameTestsEvent` registers `VfwGameTests`,
  whose static `@GameTestGenerator` method turns main's list into `TestFunction`s, so the list and the test bodies
  keep main's shape. Every 1.21.1 test needs a structure template and the game ships no empty one: VFW's
  `virtualfarmworks:empty` (8 x 4 x 8 of air, written by a script with 1.21.1's DataVersion, 3955) lives in the
  gametest source set, so it never reaches the release jar; its namespace also keeps the generated tests inside the
  runs' `neoforge.enabledGameTestNamespaces` filter.
- Positions: in 1.21.1, relative (0, 0, 0) is the test's structure block, one block BELOW the template, so the tests
  place machines, chests and farmland from y 1.
- The bodies follow the other steps: `IItemHandler` / `IEnergyStorage` calls (simulated where a test only needs the
  answer, as main's never-committed transactions did), `ItemInteractionResult`, recipe ids as `ResourceLocation`s,
  `assertTrue(boolean, String)`. The plants newer than 1.21.1 left `PlantablesGameTests`; the MA tests use MA 8's
  `CropTier#getFarmland()` and lost the always-effective check (MA 8 has no such tag). Two small accessors were added
  to `src/main` for the tests (`ItemResource#is(Item)`, `MachineEnergy#set(int)`).
- Result: the 45 game tests (every VFW test of main; main's 46th is a vanilla one) pass with Mystical Agriculture
  8.0.28 loaded, with no VFW warning and no data map error in the log. Load benchmark: 2026-10-02 in the log below.
- Not run by Claude: anything on the client (screens, JEI, Jade, tooltips, the GUI scale fit, models) and a dedicated
  server. That is the owner's in-game test.

### Block models (owner OK, 2026-10-02)
- The owner chose converted copies on the 1.21.1 branch ("same look, textures untouched") over exporting new models.
  26.1 reads `"rotation": {"x", "y", "z", "origin"}` as `Matrix4f.rotationZYX(z, y, x)` (X turns first; read in its
  `CuboidModelElement$Deserializer` / `EulerXYZRotation` with `javap`, the order checked by running JOML on the unit
  vectors) around the origin; 1.21.1 only reads one axis at 0, +-22.5 or +-45 degrees. The five models share their
  geometry (only the tier color, texture #1, differs), and every Euler rotation in them is made of quarter turns (per
  model: 16 x -90 on X, 9 x -90 on Z, 4 x 90 on Y, 4 x -90 on X and Z, 2 x -180 on X; 32 more elements have 0-degree
  one-axis rotations, valid in 1.21.1, and one element none). A quarter-turned box is another axis-aligned box:
  `tools/convert_block_models.py` writes each one that way, with new corners, every face moved to the side it faces
  after the turn with its texture and UV rectangle, and the face rotation that puts the same texture point on each
  corner (both versions walk a face's corners in the same order and look up UVs the same way, read in their
  `FaceInfo` and face UV code). It keeps every other byte of the owner's file (groups, display, textures, order).
- Proof, with the games' own code: a small program on the 1.21.1 dev classpath parses the models with
  `BlockModel.fromString` (the originals fail with "Missing axis", as in game; the converted ones load) and bakes every
  face with 1.21.1's `FaceBakery`; another, on 26.1's client jar and libraries, bakes the ORIGINAL models with 26.1's
  `FaceBakery`. All 2,040 quads (5 models x 408 faces) match: same direction, texture, corners and texture point at
  each corner. In 770 the corners are listed from another start (1.21.1 puts an unrotated face's corners in its
  standard order), which only changes how per-corner light blends across the face. Sanity check: with the turns as
  read, every element of the model lies inside the block (0-16 on each axis); turned the other way, 27 would stick
  out.
- The script verifies the same way (exact decimal arithmetic) before writing and refuses what it cannot convert
  exactly (angles that are not quarter turns, faces without UVs). When the owner re-exports a model on main, bring it
  here and run the script again.

### Owner's in-game test (2026-10-02)
- Dev client with the ATM10 mods: the Starter works in full, and both machines' converted block models look right.
- The Entropic crashed when its GUI opened ("Rendering screen", `this.font` is null in `drawFittedCentered`). The
  log showed the cause just before: NeoForge's "Failed to handle advanced open screen from server", a
  NullPointerException on `this.minecraft` in `EntropicFarmMatrixScreen#added` -> `fitGuiScale`. In 1.21.1,
  `Minecraft#setScreen` makes the new screen current, calls its `added()`, and only then `init(Minecraft, w, h)`, which
  is what sets the screen's `minecraft` and `font` (26.1 screens have both from their constructor, which is why main
  never hit it). The exception skipped `init`, NeoForge's payload handler only logged it, and the uninitialized screen
  stayed current: the next frame crashed. Fix: `added()` (and `removed()`, for symmetry) use `Minecraft.getInstance()`;
  `resize` already receives it. The rest of the screen only runs after `init`. Reviewed the whole Entropic screen for
  other uses before `init`: none (the constructor only reads the menu, items and tags).
- OPEN, owner report after the fix: on 1.21.1 the Starter's progress bar moves in steps ("10 straight to 15") while
  the Entropic's moves smoothly; on main both are smooth. Postponed by the owner (I/O modes first). Measured so far:
  with the owner's own Starter (Mystical Agriculture diamond seeds on Supremium farmland, 4 growth upgrades, crux
  provider, no Water Provider: 1.01x) the server sends the progress every 5 ticks in regular steps of 0.84%, exactly
  like the Entropic; the client code (`SmoothProgress`, called once per frame in `renderBg`) is the same for both
  screens and both lines. Next step: measure the values the client receives and draws, frame by frame.
- Also in the owner's crash reports, not VFW: on the first start of the new `run/` folder, closing the accessibility
  onboarding screen opened the title screen, and Jade 15.10.6's screen-init handler (`JadeClient.onGui`) threw
  `AssertionError: Missing config translation: config.jade.plugin_pipez.pipe` (Pipez's Jade plugin lacks a
  translation). Nothing of VFW in that trace.

## Entropic face modes: new names and a sixth mode (2026-10-02, both lines)

- Owner's request, for main and 1.21.1: the Entropic's face modes get names that say what leaves (OUTPUT ONLY SEED
  PRODUCTION, OUTPUT ONLY CRAFTED, OUTPUT ALL PRODUCED, INPUT SEEDS AND SOILS; NONE stays), a sixth mode OUTPUT ONLY
  SEEDS AND SOILS (purple) that gives back the plantables and soils of the two grids, and a one-line explanation of
  each mode in the face box's tooltip. The Starter's ON/OFF faces are untouched. Decisions:
  `docs/specs/entropic-farm-matrix.md` ("Face modes").
- Code: `FaceMode` gains `OUTPUT_SEEDS_AND_SOILS` at the end (the ordinal is saved, so existing machines keep their
  modes; it is also the last in the click cycle), `exports()` became `exportsOutput()` (the output buffer) next to
  `exportsGrids()`, and `descriptionKey()` points at the explanation. `machine/GridOutput` is the extract-only view
  of the grid slots `[0, 2 x groups)`, like `GridInput` the other way round: it is the face's capability and the
  source `autoTransfer` empties into a glued inventory. Taking seeds or soils out goes through the inventory's change
  callback, so the next tick re-validates the plots exactly as when a player takes them by hand: no special case, no
  dupe. The lang keeps the keys (only the shown names change) and adds `<key>.desc`.
- The original Entropic spec said pipes never take seeds or soils out (`GridInput` refuses extraction); the new mode
  is the owner's explicit way out, and INPUT still refuses extraction.
- Tests: `entropic_faces_and_pipe_input` follows the new cycle (INPUT -> OUTPUT ONLY SEEDS AND SOILS -> NONE) and
  checks that the new face shows the grids and takes nothing in; new `entropic_seeds_and_soils_face`: a pipe takes
  planted carrots out, a chest glued to the face receives every seed and soil and nothing else (not the harvest in
  the output, not the upgrade), the grids end empty with 0 plots, and OUTPUT ALL PRODUCED never shows the grids.

## Benchmark results log

- 2026-09-26, owner's PC, game closed (16 threads, Java 25), average of the owner's last 3 runs (a run with the game
  open was ~20% slower): growing 0.009-0.015 us per machine-tick (1 and 64 plots alike), OUTPUT FULL 0.013 us, harvest
  tick 78 us (64 wheat plots, 64 loot rolls) / 5.3 us (1 wheat) / 5.2 us (64 MA Inferium, formula), busy farm (64
  wheat, 3x, harvests included) 0.30 us -> ~3,400 busy machines per ms, 1,000 = 0.6% of a 50 ms tick; revalidation
  1.5 us; auto-export of 9 full stacks into a chest 118 us per export (~13 us per stack).
- 2026-09-28, filter cleanup before/after (Claude's runs, one run each, compare rows within a run): busy farm + pipe
  0.405 us vs + filter 0.544 us before; 0.329 vs 0.328 after.
- 2026-09-28, after the hidden output, smooth bar and harvest filter (owner's PC, game closed, average of 4 runs):
  growing 0.009-0.015 us, OUTPUT FULL 0.012 us, harvest tick 64 / 4.5 / 4.5 us, busy farm 0.28 us (~3,500 busy
  machines per ms), busy farm + pipe 0.30 us with and without a filter, revalidation 1.3 us, export 82 us. No
  regression.
- 2026-09-29, after "every plantable" (owner's PC, every game closed, average of 3 runs by Claude): growing
  0.010-0.016 us, OUTPUT FULL 0.012 us, harvest tick 71 us (64 wheat) / 4.6 us (1 wheat) / 4.8 us (64 MA), busy
  wheat farm 0.28 us, pipe 0.31 us with and without a filter, revalidation 1.4 us, export 82 us: no regression. New:
  harvest of 64 poppies 7.2 us, of 32 oak saplings 245 us (4 trees grown), of 8 crimson fungi 89 us; busy oak farm
  (64 saplings, 3x) 1.2 us per machine per tick (~4x wheat; 1,000 = 1.2 ms, 2.4% of a tick). Split of an oak harvest
  (one extra diagnostic run): ~44 us per tree grown, ~0.7 us per loot roll, so growth is ~80% of it. (A run on
  2026-09-28 with three Minecraft instances open, ATM10 included, was ~2.5x slower on every row.)
- 2026-09-29, Entropic stage 5 (Claude's run, two Minecraft instances of the owner still OPEN, so provisional; the
  Starter rows of the same run were within ~0-20% of the clean reference): Entropic growing with 3,840 wheat plots
  0.029 us per machine per tick (Starter 0.011; the difference is the FE check and payment), busy Entropic (3,840
  wheat plots, 3x, harvests included, output emptied every 20 ticks) 5.1 us, same with the autocrafter (hay bales)
  4.8 us, busy mixed Entropic (60 different plant/soil pairs, 8 plots each) 2.3 us, revalidation of 60 groups 13 us,
  growing with an input change every tick (a pipe feeding the grids) 12.4 us. Per plot, a busy Entropic costs about
  a quarter of the equivalent 60 Starters (60 x 0.33 us). Starter revalidation now 3.9 us (1.4 before stage 1: the
  multi-group generalization; it runs only on changes). A first run measured the growing Entropic with 100 machines
  and 100 warm-up ticks and got 0.144 us: not enough JIT warm-up (the energy branch was new to the compiled code), so
  the row now uses the Starter's 1,000 machines.
- 2026-09-29, after the owner's changes to the Entropic (owner's PC, every game closed, average of 3 runs by Claude):
  Starter growing 0.011 us (64 plots), OUTPUT FULL 0.015 us, harvest tick 67 us (64 wheat) / 4.4 us (1 wheat) /
  4.5 us (64 MA) / 7.5 us (64 poppies) / 250 us (32 oak saplings) / 89 us (8 crimson fungi), busy wheat farm 0.29 us,
  busy oak farm 1.27 us, pipe 0.32 us with and without a filter, export 78 us: no regression. Revalidation 2.8 us
  (1.4 before the multi-group generalization; only on changes). The first row (growing, 1 plot) read 0.021-0.029 us:
  it is measured first, while the JIT still warms up the tick (a little longer now); the same path at 64 plots,
  measured right after, reads 0.011. Entropic: growing with 3,840 plots 0.036 us, busy 3,840 wheat plots 4.7 us
  (4.5 with the autocrafter), busy mixed farm of 60 plants 2.2 us, revalidation of 60 groups 10.8 / 29.0 / 12.1 us
  (one noisy run), an input change every tick 11.0 us.
- 2026-09-30, final run before closing the Entropic (owner's PC, every game closed, average of 3 runs by Claude;
  after the FE drain to 0, RUNNING WITH LOW FE, the autocrafter using the output and the recipes kept on the item):
  Starter growing 0.011 us (64 plots), OUTPUT FULL 0.014 us, harvest tick 73 us (64 wheat; median 71) / 4.7 us
  (1 wheat) / 4.5 us (64 MA) / 8.2 us (64 poppies) / 249 us (32 oak saplings) / 92 us (8 crimson fungi), busy wheat
  farm 0.30 us, busy oak farm 1.23 us, pipe 0.33 / 0.35 us without / with a filter, revalidation 3.3 us, export
  94 us (median 86; one run 111). Entropic: growing with 3,840 plots 0.031 us, busy 3,840 wheat plots 4.8 us (4.4
  with the autocrafter), busy mixed farm of 60 plants 2.2 us, revalidation of 60 groups 12.9 us (median 12.2), an
  input change every tick 11.3 us. No regression: every row is within the ~10-20% run-to-run noise of the
  2026-09-29 reference (Starter revalidation read 3.3 us against 2.8, and 3.9 in another session of 2026-09-29;
  it runs only on changes).
- 2026-10-02, the 1.21.1 line after the port (owner's PC, every game closed, Java 21, average of 3 runs by Claude):
  Starter growing 0.025 us (64 plots; the runs read 0.012 / 0.041 / 0.022), OUTPUT FULL 0.030 us, harvest tick
  76 us (64 wheat; median 71) / 3.3 us (1 wheat) / 3.8 us (64 MA) / 9.1 us (64 poppies) / 284 us (32 oak saplings;
  median 277) / 83 us (8 crimson fungi), busy wheat farm 0.24 us (~4,200 busy machines per ms), busy oak farm
  1.3 us, pipe 0.25 us with and without a filter, revalidation 2.8 us, export 52 us. Entropic: growing with 3,840
  plots 0.027 us, busy 3,840 wheat plots 2.4 us (3.9 with the autocrafter), busy mixed farm of 60 plants 0.89 us,
  revalidation of 60 groups 15.3 us (median 14.9), an input change every tick 11.4 us.
  Against main's 2026-09-30 reference (Java 25): the harvest ticks, the busy oak farm, both revalidations, the
  Entropic's growing and input rows are within noise (oak saplings +11% by the median: the tree features are 1.21.1's
  own). The rows that store many items are cheaper: busy wheat farm 0.24 vs 0.30 us, pipe 0.25 vs 0.33, export 52 vs
  94, busy Entropic 2.4 vs 4.8, mixed Entropic 0.89 vs 2.2. Probably (not profiled) because VFW's `SlotTransaction`
  plans in plain arrays and writes each changed slot once, where 26.1's transfer API journals changes in its
  transactions, and export is a plain `insertItemStacked`. For the same reason the autocrafter now adds 1.5 us
  instead of saving 0.4: the crafting itself costs about the same, but the storing it saves is cheap here
  (inference). The two nanosecond rows read higher (growing 0.025 vs 0.011, OUTPUT FULL 0.030 vs 0.014) and swing
  2-3x between runs; their per-tick path is main's code (a few flag checks and one addition) and one run matched
  main exactly, so Java 21's JIT or noise; ~15 ns per machine per tick (0.015 ms for 1,000 machines), not
  investigated further.
