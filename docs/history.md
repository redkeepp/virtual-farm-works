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
