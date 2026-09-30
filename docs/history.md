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
