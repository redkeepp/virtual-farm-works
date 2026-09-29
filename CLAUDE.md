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
- License: **MIT** for code and assets (all art is the owner's). `LICENSE` is VFW's; `TEMPLATE_LICENSE.txt` is the
  NeoForged MDK notice and must be kept. Never copy code or assets from other mods without checking their license.
- Git: `https://github.com/redkeepp/virtual-farm-works`, branch `main`. Claude commits; the OWNER pushes.

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
   `textures/gui/starter_farm_matrix_gui2.png`, `textures/item/antigos_nao_usar/` and missing Entropic assets. If
   something renders as the missing texture, check the code's resource paths first, then tell the owner; never
   create replacement art.
8. **Modpack mindset** (owner): the mod will run in big packs — support ANY seed, ANY sapling, ANY plant through its
   block class, tags and the game's own rules. Never hard-code vanilla item lists in Java; exceptions go in tags or
   data maps that pack makers can edit.

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
  runs `gradlew build` only (JUnit, no game tests).

## Current state (2026-09-28)

**Starter Farm Matrix: complete**, tested in game by the owner (single player; dedicated server before the JEI
packet was added). Features: one global growth cycle with ACTIVE/PENDING plots; every plantable (crops, trees,
flowers, grass, aquatic and hanging plants — "More plantables", 2026-09-28, not yet tested in game by the owner);
plant/soil rules from the game's own logic; loot-table, grown-tree, fixed-yield and Mystical Agriculture harvests;
transactional batched harvest into a 9-slot visible output (players can also put items in by hand) plus 27 hidden
slots; auto-export per face; Water Provider, 4 Growth Speed upgrades, Crux Provider; hoe slot with optional
time-based wear; Fertilized Essence switch; per-machine harvest filter (whitelist/blacklist, JEI drag-and-drop);
smooth progress bar; Jade tooltip; JEI exclusion areas; recipes.
Tests: 31 game tests (30 VFW + 1 vanilla), 73 JUnit, load benchmark.

**Next: Voltaic tier** — needs the owner's spec (the GUI texture `voltaic_farm_matrix_gui.png` exists: 296 px wide,
two 15x4 slot blocks, info panel, 3x3 grid). Owner's preview (2026-09-28): the two grids are plantables (top) and
soils (bottom); "replanting" (replantio) = seeds produced by the harvest are planted automatically into the free
soils, instead of going to the output or being filtered, until no free soil is left. Several classes are
Starter-shaped today (GUI layout, menu slot constants, one plot group) and will need per-tier variants.

Open on the owner's side: in-game test of the new plantables, multiplayer re-test (a custom packet was added since
the last one), git tag "starter complete" (owner: not yet). Deferred by the owner: EMI (no 26.1.2 release),
publishing metadata (README still says "scaffolding"). Outside VFW: MA 9.0.9's creative tab crashes (it lists
"Inferium Essence" twice) — test in survival or with JEI.

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
- Planned for later tiers (owner's original spec): FE energy (no energy = `MISSING FE`, no progress) and
  autocrafting (persist its leftovers and rules); the owner said future tiers MAY also accept automated inputs.
- Content: Starter, Voltaic, Ionic, Resonant, Entropic **Farm Matrix**; Water Provider Upgrade and Growth Speed
  Upgrade in those 5 tiers (an upgrade fits machines of its tier or lower: `MachineTier#accepts`); one Crux Provider
  Upgrade. Progression: from a Botany-Pot-like Starter to thousands of plots (Entropic ≈ 6,000 as a design target).

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
Breaking drops contents in `preRemoveSideEffects`.

### Growth (`sim/` — pure Java, NO net.minecraft imports, JUnit-tested)
- `GrowthCycle`: progress 0..1, `PlotGroup`s (active, pending, harvested), carry-over capped so at most one harvest
  per tick, 1e-9 "due" tolerance (600 x 1/600 is exactly one cycle).
- `setPlots(group, min(seeds, soils), plantChanged)` on every slot change: new plots PENDING unless the machine is
  empty; removal takes PENDING first, then already-harvested ACTIVE plots, then ripe ones; an empty machine resets
  progress; a plant change uproots. Persist progress, active, pending, harvested (`load` repairs corrupted data).
- Speed = hydration (Water Provider or `noWaterSpeedMultiplier`) x (1 + bonus x upgrades) x soil bonus (data map).
- `MachineStatus` priority = declaration order: SHUTDOWN > MISSING SEED > MISSING SOIL > INVALID SOIL > MISSING HOE >
  MISSING CRUX > MISSING FE > OUTPUT FULL > RUNNING.

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
- JEI ghost drop -> `network/SetFilterGhostPayload` (the only custom packet) -> `FarmMatrixMenu#setFilterGhost`.

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
- JEI (compile-only API, maven.blamejared.com): `client/compat/VfwJeiPlugin` — exclusion areas
  (`FarmMatrixScreen#extraAreas`) and the filter ghost-ingredient handler.
- Jade (compile-only, Modrinth maven): `compat/jade/` — `@WailaPlugin`, server data provider and client tooltip in
  separate classes (a dedicated server must never load the client one); same lines as the GUI (`menu/DisplayFormats`).
- EMI: no 26.1.2 release yet; add an exclusion-area plugin when it exists.

## Performance and benchmark

- `gametest/LoadBenchmark`: detached machines ticked directly (the ticker's work; vanilla's per-BE overhead excluded),
  warm-up + best of 3. Rows: growing 1 vs 64 plots, OUTPUT FULL, harvest tick (wheat x64/x1, MA x64, poppy x64,
  oak saplings x32, crimson fungus x8), busy farm (64 wheat / 64 oak saplings, 3x), busy farm + a pipe pulling 1
  item/tick with and without a filter, revalidation, auto-export into a real chest. Never fails on numbers, but every
  timed harvest must fit one batch (hence few tree plots). Run-to-run noise is ~10-20%: compare rows within one run.
- Reference (2026-09-29, owner's PC, every game closed, 3 runs): busy wheat farm 0.28 us per machine per tick
  (~3,500 busy machines per ms; 1,000 = 0.56% of a 50 ms tick); growing ~0.01 us; harvest tick 71 us (64 wheat) /
  4.8 us (64 MA) / 7.2 us (64 poppies) / 245 us (32 oak saplings, 4 trees grown); busy oak farm 1.2 us (~4x wheat).
  A tree harvest is ~80% growth (~44 us per tree grown). History of results in `docs/history.md`.
- Close every Minecraft instance before benchmarking (dev clients AND the owner's modpack, e.g. ATM10): with them
  open every row came out ~2.5x slower.
- The priciest routine work is auto-export into a full neighbour (NeoForge transfer + the neighbour's inventory):
  tunable with `output.autoExportIntervalTicks`.
- Lessons: never run work after EVERY output change (the filter cleanup cost +35% that way); cache what the tick
  reads; re-run the benchmark after changes to the tick or harvest path.

## Gotchas

### 26.1.2 API — verify against sources, do not trust memory of older versions
- Sources (gitignored, deleted by `gradlew clean`): extract `build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar`
  -> `build/mcsrc/` and `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.109/*/neoforge-26.1.2.109-sources.jar`
  -> `build/neosrc/` (`tar -xf <jar>`). Vanilla data (tags, loot tables): `~/.gradle/caches/neoformruntime/artifacts/minecraft_26.1.2_client.jar`.
- `ResourceLocation` is `net.minecraft.resources.Identifier`. `ClickType` is `ContainerInput`. Block placement rules
  use `#supports_*` block tags (e.g. `supports_vegetation` = `#substrate_overworld` + farmland).
- `Item#appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)`.
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
- Input: `mouseClicked(MouseButtonEvent event, boolean doubleClick)`.
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
- Commits: Bash tool with a heredoc — `git commit -F - <<'EOF' ... EOF`. From PowerShell 5.1, quotes in `-m` split
  into pathspecs, `-F` with the long scratchpad path fails, piping adds a BOM. Never hide git's stderr (`2>$null`).
- `gradlew` must stay executable in git (`git ls-files -s gradlew` -> `100755`); Windows loses the bit and CI fails
  with "Permission denied". Fix: `git update-index --chmod=+x gradlew`.
- `src/main/templates/META-INF/neoforge.mods.toml` is a Groovy template: a literal dollar sign followed by `{`, even in
  a comment, breaks `generateModMetadata`.
- If `gradlew build` says UP-TO-DATE after code changes, delete the project `.gradle/` folder and rebuild.
- Long paths: `git clone` into deep folders may fail with `'$GIT_DIR' too big` — download a ZIP or use a short path.
- No Python on the owner's machine: script with bash/sed/awk or use the Edit tool. A recursive grep over `build/`
  (sources + caches) takes minutes: grep `build/mcsrc` or `build/neosrc` subfolders instead.
- The owner usually has `runClient` open on `run/`. An old build running there "corrects" the shared config TOML and
  removes new keys: ask the owner to restart the client after code changes. When the owner edits the TOML by hand: a
  number with a leading zero (`05.0`) makes NeoForge back up the file and recreate it with DEFAULTS; an edited comment
  or out-of-range value gets rewritten. VS Code then refuses to save ("the content of the file is newer"): close the
  tab without saving and reopen. Comments in the TOML come from `VfwServerConfig`; change them there.

## Code map

- `VirtualFarmWorks` — entry point; only wires registries, config, data maps, payloads and game tests.
- `machine/` — `FarmMatrixBlockEntity` (the running machine), `MachineInventory`, `OutputBuffer` (visible output),
  `InternalBuffer` (hidden output), `MachineFilter` (harvest filter), `MachineSlots` (persisted indices — never
  reorder), `MachineTier` (order = progression, `accepts()`), `RelativeSide` (faces relative to the front; bit order
  persisted).
- `block/FarmMatrixBlock` — one block class for all tiers (tier is a constructor arg).
- `item/` — `TieredUpgradeItem` (+ `UpgradeType`), `CruxProviderUpgradeItem`; items carry no behavior.
- `registry/` — `ModBlocks`, `ModItems`, `ModBlockEntities` (+ capabilities), `ModMenus`, `ModCreativeTabs`.
- `config/` — `VfwServerConfig` (spec + comments pack makers read), `VfwConfig` (runtime: compiled filters,
  generation), `ItemFilter`.
- `data/` — `ModDataMaps` with `SoilProperties` (soil growth bonus) and `FixedYield` (fixed harvests) data maps.
- `plant/` — `PlantRules`, `SoilRules`, `PlantAnalysis`, `SoilView` (2-block BlockGetter for native rules),
  `VirtualLevel` (in-memory WorldGenLevel for generic rules and tree growth), `VfwTags`.
- `sim/` — Minecraft-free core: `GrowthCycle`, `PlotGroup`, `GrowthSpeed`, `MachineStatus`, `MachineConditions`,
  `HarvestMath`, `DropTally`, `HarvestBatching`, `YieldSample`.
- `harvest/` — `DropSource`, `LootDropSource`, `TreeDropSource` + `TreeGrowth`, `FixedDropSource`, `HarvestPlans`,
  `Harvester`, `HarvestFilter`.
- `menu/` — `FarmMatrixMenu` (both sides), `FarmMatrixLayout`, `FilterPageView`, `DisplayFormats`.
- `network/` — `SetFilterGhostPayload`.
- `client/` — CLIENT ONLY: `VirtualFarmWorksClient` (second `@Mod`, dist CLIENT), `FarmMatrixScreen`,
  `SmoothProgress`, `compat/VfwJeiPlugin`. Never reference `client/` from common code (a dedicated server crashes).
- `compat/jade/`, `compat/mysticalagriculture/` — see Integrations.
- `gametest/` (dev only) — `VfwGameTests` (registration: add a `test(...)` line with a max tick count),
  `MachineGameTests`, `FilterGameTests`, `PlantablesGameTests`, `MysticalHarvestTests`, `MysticalFarmlandTests`,
  `LoadBenchmark`.
  `FarmMatrixBlockEntity#setProgressForTesting` exists only for tests.
- `src/test/java/com/virtualfarmworks/` — JUnit (`sim/`, `client/SmoothProgress`).
- `src/main/resources/data/virtualfarmworks/` — tags, data maps, recipes, loot tables (see `docs/resources.md`).
- `src/main/templates/META-INF/neoforge.mods.toml` — mod metadata (Gradle expands it from `gradle.properties`).

## Docs (read the specs before implementing)

- `docs/specs/starter-farm-matrix.md` — the Starter spec and every owner decision since (GUI, output, filter,
  plantables).
- `docs/specs/configurability.md` — every pack-maker setting and where it lives.
- `docs/resources.md` — what each JSON resource does and who authored it.
- `docs/history.md` — how milestone 1 was built, step by step, and the benchmark results log.
