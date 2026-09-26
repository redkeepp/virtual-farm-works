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
.\gradlew.bat test                # JUnit tests of the Minecraft-free simulation core (seconds). Results:
                                  # build/test-results/test/*.xml
.\gradlew.bat runGameTestServer   # headless: boots the mod, runs game tests, exits (exit code 0 = all passed). Use it to
                                  # catch registry/datapack errors without opening the client; check
                                  # run-gametest/logs/latest.log.
.\gradlew.bat runBenchmark        # headless load benchmark (gametest/LoadBenchmark, runs alone): cost per machine per
                                  # tick; table in the log and in run-gametest/vfw-benchmark.txt. Close the game first
                                  # (CPU noise).
```

Game tests and the benchmark run in `run-gametest/` (own config = defaults, own world and logs; mods copied from
`run/mods` by the `syncTestMods` task). Never point them back to `run/`: `run/config` holds the owner's manual test
values (a changed `upgradesPerSlot` made two tests fail), and test runs used to rewrite the owner's config file.

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
- Commits: use the Bash tool with a heredoc — `git commit -F - <<'EOF' ... EOF`. From Windows PowerShell 5.1, double
  quotes inside a `-m` message are split into pathspecs (commit fails), `-F` with the long scratchpad path fails
  ("Filename too long"), and piping a message adds a UTF-8 BOM to the subject. Never hide git's stderr with
  `2>$null` — that is how a failed commit went unnoticed once.
- `src/main/templates/META-INF/neoforge.mods.toml` is a Groovy template: a literal dollar sign followed by `{`, even
  inside a comment, breaks `generateModMetadata`.
- `gradlew` must be executable in git (`git ls-files -s gradlew` -> `100755`); the repo is managed from Windows, which
  loses the bit and makes the GitHub Actions build fail with "Permission denied". Fix: `git update-index --chmod=+x gradlew`.

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
   Conventions (owner request):
   - Every Java file starts with a header block comment BEFORE `package`:
     `/* ClassName — what this file is and where it fits. */` (1–3 lines). The class Javadoc below the imports holds
     the details (design, rules, why).
   - Build/config files (gradle, toml, workflow yml) start with a `#`/`//` header saying what the file is.
   - JSON cannot hold comments: every JSON resource is documented in `docs/resources.md` — update it whenever you add,
     remove or change the meaning of a resource file.
   - When behavior changes, update the comments in the same commit. A stale comment is worse than none.
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

### Crop compatibility approach (implemented in steps 3 and 5)
A seed item resolves to its plant block; soil compatibility uses the game's own rules in a virtual 2-block view
(step 3). Drops come from the plant's loot table at maturity with a server-side loot context (no entities, no fake
player), so vanilla and most modded crops work without per-mod code (step 5). Mystical Agriculture is the exception:
its crops compute drops from the real block below them, so VFW reproduces MA's formula through MA's API with the soil
slot's farmland. MA crux requirements are satisfied by the generic Crux Provider Upgrade.

### Content list
Blocks: Starter, Voltaic, Ionic, Resonant, Entropic **Farm Matrix**.
Items (5 tiers each: Starter, Voltaic, Ionic, Resonant, Entropic): **Water Provider Upgrade**, **Growth Speed Upgrade**.
Item (single, untiered): **Crux Provider Upgrade**.
Progression: starts as a simple alternative to the Hopper Botany Pot and ends with machines representing thousands of
plots (Entropic ≈ 6,000 plots as a design target). Tier effects/numbers: pending owner's spec (config-driven).

## Specs (read before implementing)

- `docs/specs/starter-farm-matrix.md` — Starter Farm Matrix: GUI coordinates, slots, states, behavior, open questions.
- `docs/specs/configurability.md` — everything pack makers must be able to change without Java.
- `docs/resources.md` — what every JSON resource file does and who authored it (owner art vs Claude plumbing).
- Mystical Agriculture exists for 26.1.2 (9.0.x, Blake's Mods). Dev testing uses jars in `run/mods/`
  (MA 9.0.9, Agradditions 9.0.3, Cucumber, plus the owner's other test mods). VFW must not hard-depend on it.

## Mystical Agriculture findings (from its 9.0.9 jar, via javap — needed for step 3/5)

- `MysticalCropBlock extends CropBlock implements ICropProvider` (`getCrop()` -> `api.crop.Crop`). Agradditions crops
  build on the same API.
- `MysticalCropBlock#getDrops(BlockState, LootParams.Builder)` is overridden and reads the REAL WORLD:
  `level.getBlockState(ORIGIN.below())` to get the farmland. With `c = crop.getSecondaryChance(farmlandBlock)`, a
  mature crop drops: essence 1 (2 with chance `c`), seeds 1 (2 with chance `c`, only if MA config
  `secondarySeedDrops`), Fertilized Essence with MA config `fertilizedEssenceChance` — three INDEPENDENT rolls.
  `InferiumCropBlock` overrides it: essence = `(int)(0.5 * v + 0.5)` for farmland tier value `v` (+1 with 50% when `v`
  is even and > 1), seeds as above, never Fertilized Essence. A virtual machine has no real farmland below the crop,
  so VFW reproduces this in `compat/mysticalagriculture/MysticalDropSource` with the soil slot's block; the game test
  `mystical_drops_match_ma` compares it statistically with MA's real `getDrops` (verified to catch a broken formula).
- `Crop#getSecondaryChance(Block soil)`: 0 if the tier has no secondary seed drop; +base (crop override if > -1,
  else tier base, default 0.1) when soil is any `IEssenceFarmland`; +0.1 more when the crop respects effective
  farmland and `tier.isEffectiveFarmland(soil)` (tier's own farmland or block tag `ALWAYS_EFFECTIVE_FARMLAND`); capped
  at 1.0. So by default: 0% plain soil, 10% any essence farmland, 20% matching tier farmland.
- Crux: `Crop#getCruxBlock()` (null when the crop needs none) -> satisfied by the Crux Provider Upgrade.
- Effective farmland (MA common config `requiresEffectiveFarmland`, default false; read from MA's sources jar): in
  `MysticalCropBlock#canGrow`, every crop except `ModCrops.INFERIUM` only grows when
  `crop.getTier().isEffectiveFarmland(blockBelow)` — the tier's own farmland (EXACT match: a higher-tier farmland does
  not count) or a block in `#mysticalagriculture:always_effective_farmland` (MA ships `awakened_supremium_farmland`
  in it). It does NOT check `Crop#respectsEffectiveFarmland` (that flag only affects the secondary chance). VFW
  mirrors it behind its OWN switch `mysticalagriculture.requiresEffectiveFarmland` (owner: default off, independent
  from MA's option): `PlantAnalysis` -> `MysticalCompat#isEffectiveFarmland` -> `INVALID_SOIL`; game test
  `mystical_effective_farmland`.
- Farmlands: `InfusedFarmlandBlock extends FarmlandBlock implements IEssenceFarmland`, `getTier()`. Ids:
  `mysticalagriculture:{inferium,prudentium,tertium,imperium,supremium,awakened_supremium}_farmland`,
  `mysticalagradditions:insanium_farmland`.
- Compiling against MA: `compileOnly` jar from Blake's maven (see `build.gradle`); MA classes are referenced only in
  `compat/mysticalagriculture/*Impl`/`MysticalDropSource` and in game tests that run only when MA is loaded. The
  sources jar sits next to the jar in the Gradle cache (`~/.gradle/caches/modules-2/.../MysticalAgriculture/`).
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
- [x] Step 3: plant/soil resolver (`plant/`): `PlantRules` (plantable items, `canGrowOn` = soil's NeoForge
  `canSustainPlant` hook first, then the plant's own `mayPlaceOn` via a cached reflective MethodHandle, or vanilla
  `#supports_*` tags for non-VegetationBlock plants), `SoilRules` (hoe detection, tillable soils, cached soil-slot
  acceptance), `PlantAnalysis` (seed+soil -> MISSING_SEED / MISSING_SOIL / INVALID_SOIL / VALID + needsHoe, needsCrux,
  soil multiplier), `SoilView` (2-block virtual BlockGetter). MA crux via `compat/mysticalagriculture`.
  Game tests in `gametest/VfwGameTests` (4 pass with MA + Agradditions in run/mods).
  Why no access transformer for `mayPlaceOn`: making it public breaks the MC recompile (19 vanilla subclasses
  override it as protected) — see `PlantRules#mayPlaceOn`.

- [x] Step 4: simulation core `sim/` (pure Java, NO net.minecraft imports — keep it that way so JUnit can test it):
  `GrowthCycle` (global progress 0..1, `PlotGroup`s with active/pending, exploit rules, carry-over capped so at most
  one harvest per tick, 1e-9 "due" tolerance so 600 x 1/600 is exactly one cycle), `GrowthSpeed`
  (hydration x upgrades x soil), `MachineStatus` (priority = declaration order) + `MachineConditions`. 34 JUnit tests.
  Status priority decided by Claude (owner may revisit): SHUTDOWN > owner's missing hierarchy > OUTPUT FULL > RUNNING.
  `noWaterSpeedMultiplier` min is 0.01 (0 would show RUNNING without ever advancing; no "missing water" state exists).

- [x] Step 5: aggregated transactional harvest (`harvest/`): `HarvestPlans` (drop source per seed/soil, built on
  revalidation), `LootDropSource` (plant loot table, no entities, TOOL = EMPTY so the hoe never changes yields,
  sampled to `performance.maxLootRollsPerHarvest` and scaled), `MysticalDropSource` (MA formula), `Harvester` (`roll`
  with config multipliers, `tryStore` = root NeoForge Transaction + `insertStacking`, all-or-nothing). Pure math in
  `sim/HarvestMath` + `sim/DropTally` (JUnit). Tag `#virtualfarmworks:harvest_byproducts`. 47 JUnit + 7 game tests.

- [x] Step 6: `machine/FarmMatrixBlockEntity` (server ticker; revalidates only on slot/config/tag change; one addition
  per normal tick; harvest rolled once and retried only when buffer or inputs change; auto-export via
  `BlockCapabilityCache` every `output.autoExportIntervalTicks`; persistence of inventories, progress, counters, on/off,
  faces; progress saved at most every 20 ticks via `level.blockEntityChanged`; contents dropped in
  `preRemoveSideEffects`). `MachineInventory` (per-slot rules), `OutputBuffer` (+ extract-only `externalView()`),
  `MachineSlots`, `RelativeSide`. `registry/ModBlockEntities` (one BE type "farm_matrix" for all tiers + item
  capability). 14 game tests pass (7 machine tests), 47 JUnit.
  Decisions (confirmed by the owner in step 7): automation can only EXTRACT from the output buffer (inputs not
  exposed on the Starter); auto-export keeps running while SHUTDOWN; faces are relative to the player looking at the
  front; the hoe only wears while it is actually needed.

- [x] Step 7: GUI. `menu/FarmMatrixMenu` (slots at the owner's coordinates, shift-click, `ContainerData` synced
  every 5 ticks + on button press, button intents via vanilla `clickMenuButton`: 0 = power, 1..6 = faces),
  `menu/FarmMatrixLayout` (all GUI geometry/colors, shared), `client/FarmMatrixScreen` (texture, texts, green bar,
  40% ghost placeholders, side panel: "O" button + face box, 5 upgrade slots, power button), `client/
  VirtualFarmWorksClient` (`@Mod(dist = CLIENT)`, registers the screen), `registry/ModMenus`. Right-click opens it
  (`FarmMatrixBlock#useWithoutItem`). Game test `menu_actions` covers the server side; visuals are tested by the owner.
  Owner decisions this step: no input automation on the Starter (future tiers may add it); hoe wear is time-based
  (`hoe.wearIntervalTicks`, default 1200 = 1 per minute of RUNNING while the hoe is needed).
  Claude's GUI choices (owner may revisit): "Seeds" shows planted plots = min(seeds, soils); "Growth" multiplier
  includes the soil bonus; power button at the bottom of the side column; face labels T/L/F/R/Bk/Bt + tooltips;
  no "Inventory" label. Known gap: no JEI/EMI exclusion area for the side panel yet.
  Owner tested everything in game (all OK) and revised the side column: glued to the texture (no theme border on its
  right side), the 5 upgrade slots merged in one block with single blue separators (17 px pitch), 3 px between boxes.
  Tip: to check pixel geometry without the game, paint the same fills on the texture with a script and READ THE
  PIXELS — the image viewer downsizes previews and can hide 1 px lines.

- [x] Step 8 (finishing, in progress): recipes for Starter Farm Matrix / Water Provider / Growth Speed and Crux
  Provider (+ recipe-book unlocks); Fertilized Essence switch per machine (persisted, button id 7, part of the
  pending-harvest key, `DropSource.Context#fertilizedEssence`); right-click with an upgrade pulls it in
  (`FarmMatrixBlock#useItemOn` -> `FarmMatrixBlockEntity#insertUpgradesFrom`, falls through to the GUI when nothing
  fits); info lines y 57/66/76/86 (status, hydration, seeds, growth), title y 8.
  JEI: `client/compat/VfwJeiPlugin` (exclusion areas from `FarmMatrixScreen#extraAreas`). Jade: `compat/jade/`
  (server data provider + client tooltip, same lines as the GUI via `menu/DisplayFormats`; plugin load confirmed in
  the game-test log). Both compileOnly (maven.blamejared.com, Modrinth maven). EMI: no 26.1.2 release exists yet —
  add an exclusion-area plugin when it does.
  MA effective farmland: VFW switch `mysticalagriculture.requiresEffectiveFarmland` (default off, see the MA findings
  above). Owner corrections: 3 px between the two ON/OFF boxes (not 5; all side boxes are now 3 px apart), and the
  Fertilized Essence tooltip reads "Drops Fertilized Essence: ON/OFF". Smooth progress bar: `client/SmoothProgress`
  interpolates the 5-tick syncs on the client (bar and Growth % share one value per frame); sync rate unchanged.
  Owner verified by hand: editing the config with the game open affects machines at once, and the dedicated server
  works (no automated reload test needed). Deferred by the owner: EMI, publishing metadata.
  Output deadlock FIXED (owner's overclock report): a whole harvest was stored all-or-nothing, so one bigger than the
  9-slot buffer never fit, even when empty. Owner's design (rejected a big hidden inventory, then chose this middle
  ground): visible 9 slots -> hidden `machine/InternalBuffer` (config `machines.<tier>.internalBufferSlots`, Starter
  27, refills the visible slots, invisible to GUI and capabilities, DELETED on break) -> ripe plots wait on the plant
  (`GrowthCycle#plotsToHarvest`, harvested counter saved). Batches sized by `sim/HarvestBatching` + `sim/YieldSample`,
  each rolled once and stored all-or-nothing; extreme case (one batch bigger than empty buffers) stores what fits and
  keeps the rest in `heldDrops` (saved, stored first, owner-approved). No GUI/Jade indicator (owner). Details: spec,
  section "Output". 21 game tests (20 VFW + 1 vanilla), 63 JUnit.
  Also decided: the machine does not imitate natural growth times; config comments must not promise a "physical farm".
  Load benchmark: `gametest/LoadBenchmark` (`gradlew runBenchmark`, registered alone via -Dvirtualfarmworks.benchmark).
  Detached machines ticked directly (ticker work only, vanilla BE-ticking overhead excluded), warm-up + best of 3;
  scenarios: growing 1 vs 64 plots, OUTPUT FULL waiting, harvest tick (wheat x64/x1, MA x64), busy farm average,
  revalidation, auto-export into a real chest. Never fails on numbers (machine-dependent).
  Results 2026-09-26, owner's PC with the game closed (16 threads, Java 25), average of the owner's last 3 runs
  (owner's choice; a run of mine with the game open was ~20% slower): growing 0.009-0.015 us per machine-tick (1
  and 64 plots alike), OUTPUT FULL waiting 0.013 us, harvest tick 78 us (64 wheat plots, 64 loot rolls), 5.3 us
  (1 wheat plot), 5.2 us (64 MA Inferium plots, formula), busy farm (64 wheat, 3x, harvests included) 0.30 us
  average -> ~3,400 busy machines per 1 ms of tick, 1,000 = 0.6% of a 50 ms tick; revalidation 1.5 us; auto-export
  of 9 full stacks into a chest 118 us per export (~13 us per stack, NeoForge transfer + vanilla chest; the priciest
  part when a buffer is always full, tunable with output.autoExportIntervalTicks).
  Dev tip: the owner often has `runClient` open on `run/`. An old build running there reverts new config keys (its
  file watcher "corrects" the TOML), so ask the owner to restart the client after code changes. Game tests no longer
  share that folder (`run-gametest/`). When the owner edits the TOML by hand: a value with a leading zero (`05.0`)
  makes NeoForge back the file up and recreate it with DEFAULTS; an edited comment or out-of-range value gets
  "corrected" and rewritten. Either way VS Code then refuses to save ("the content of the file is newer").

## GUI gotchas (26.1)

- Rendering is the "extract" API: `GuiGraphicsExtractor` (not GuiGraphics), `extractBackground`, `extractLabels`
  (translated to the GUI origin), `extractTooltip`, `text(...)`, `item/fakeItem`, `fill`, `blit(RenderPipelines...)`.
  Colors are ARGB: always include the alpha byte (0xFF......) or text/fills are invisible.
- Elements that overlap an earlier item go to a higher layer: draw an item, then a translucent `fill` over it to fade
  it (ghost placeholders; vanilla recipe book does the same).
- Input: `mouseClicked(MouseButtonEvent event, boolean doubleClick)`; `event.x()/y()/button()`.
- Anything drawn outside the texture must be excluded in `hasClickedOutside`, or clicks there drop the carried item.
- `ContainerData` values travel as 16-bit shorts: scale and clamp (see `FarmMatrixMenu#toShort`).

## Harvest rules — quick reference (`harvest/`)

- Replanting cost: crops/nether wart/cocoa/MA pay 1 planting item per harvested plot (the plot keeps its seed, a real
  farm replants with a drop). Stems (fruit loot: melon slices 3-7, pumpkin), berries, sugar cane, cactus, bamboo,
  mushrooms and chorus (chorus_plant loot) stay in place: no cost.
- MAIN vs SECONDARY: planting-item surplus is SECONDARY only if the planting item is in `#c:seeds`; items in
  `#virtualfarmworks:harvest_byproducts` are SECONDARY; the rest is MAIN. MAIN x global x tier production multiplier,
  SECONDARY x `secondaryDropMultiplier`; `DropTally.finish` rounds stochastically (expected value exact).
- Implemented in `FarmMatrixBlockEntity` (keep it this way): a due cycle is harvested in batches (usually one). Each
  batch is rolled once and kept in memory (`pendingBatch` + `HarvestKey`); only `tryStore` is retried when the output
  or inputs change (no re-rolling per retry: cost + bias). `GrowthCycle#harvestPlots` only after `tryStore` returned
  true (or the extreme-case partial store), `completeHarvest()` only when no plot is left and `heldDrops` is empty.
  Never discard a rolled batch because it did not fit (that would bias yields down). Hoe durability (config
  `hoe.consumeDurability`, off by default) wears over TIME (owner decision): 1 point every `hoe.wearIntervalTicks` of
  RUNNING while the soil needs the hoe — not per harvest.
- Fill order: visible `OutputBuffer` first, then `InternalBuffer#fillView` (usable hidden slots); the hidden buffer
  refills the visible one on the tick after the visible one changed (`refillVisibleOutput`). A batch is several item
  types (wheat + seeds): it can wait with free slots if not ALL of it fits (all-or-nothing per batch).
- Never call `tryStore`/`storeWhatFits` inside another transaction (they refuse): a rolled-back outer transaction
  would void a batch whose plots were already counted.
- Game tests that change the config must restore it in the SAME tick (call `serverTick` directly in a loop, see
  `MachineGameTests#bigHarvestDoesNotDeadlock`): tests of one batch run at the same time and would see the change.

## Simulation rules — quick reference (`sim/GrowthCycle`)

- Machine tick: if status RUNNING and nothing to store -> `advance(progressPerTick)`; if due -> store held drops,
  then one batch of `plotsToHarvest()`; when none is left, `completeHarvest()`; on failure OUTPUT FULL (bar frozen at
  100%, retried only when the output or inputs change).
- `setPlots(group, min(seeds, soils), plantChanged)` on every slot change: new plots are PENDING unless the machine
  is empty (then ACTIVE at 0%); removal takes PENDING first, then already-harvested ACTIVE plots, then ripe ones; an
  empty machine resets progress; plant change uproots.
- Persist `progress`, `activeCounts()`, `pendingCounts()`, `harvestedCounts()`; restore with `load(...)` (repairs
  corrupted data; saves without "harvested" load as nothing harvested).

## Plant/soil rules — quick reference

- Seed slot: item places a supported plant block (CropBlock, StemBlock, NetherWart, SweetBerryBush, Mushroom,
  SugarCane, Cactus, Bamboo, Cocoa, CaveVines, ChorusFlower) or is in `#virtualfarmworks:extra_plantables`; never in
  `#virtualfarmworks:unplantable` (torchflower seeds, pitcher pod). Config blacklists apply per tier on top.
- Soil slot: item places a block some plantable plant can grow on, or is in `#virtualfarmworks:tillable_soils`.
  Plants are never soils. Cached per item; caches clear on `TagsUpdatedEvent`.
- Hoe needed = plant cannot grow on the soil, but can on farmland and the soil is tillable (and `hoe.requireHoe`).
- Virtual plots ignore light, water adjacency and neighbours. Mushrooms use `#virtualfarmworks:supports_mushrooms`
  (mycelium, podzol, nylium) and glow berries `#virtualfarmworks:supports_glow_berries` (stone, moss, dirt) because
  their vanilla rules accept almost any block.
- Game-test gotcha: a block that supports no plant is MISSING_SOIL (slot rejects it), not INVALID_SOIL.
- Milestone 1 scope (agreed): **Starter Farm Matrix only**, to validate the architecture before the other tiers.
  I/O by face, autocrafting and integrations come in later milestones.

## Layout

- `src/main/java/com/virtualfarmworks/` — mod code (main class `VirtualFarmWorks`, only wires registries).
  - `machine/MachineTier` — tier enum; order = progression; `accepts()` implements the upgrade compatibility gate.
  - `machine/FarmMatrixBlockEntity` — the running machine; `MachineInventory`, `OutputBuffer` (9 visible slots),
    `InternalBuffer` (hidden output slots), `MachineSlots` (slot indices, persisted — never reorder), `RelativeSide`
    (faces relative to the front; bit order persisted).
  - `block/FarmMatrixBlock` — one block class for all tiers (tier is a constructor arg).
  - `item/` — `TieredUpgradeItem` (+ `UpgradeType`), `CruxProviderUpgradeItem`. Items carry no behavior.
  - `registry/` — `ModBlocks`, `ModItems`, `ModBlockEntities` (+ capabilities), `ModMenus`, `ModCreativeTabs`.
  - `menu/` — `FarmMatrixMenu` (both sides), `FarmMatrixLayout` (GUI geometry and colors).
  - `client/` — CLIENT ONLY: `VirtualFarmWorksClient` (second `@Mod`, dist CLIENT), `FarmMatrixScreen`,
    `compat/VfwJeiPlugin`. Never reference `client/` classes from common code (a dedicated server would crash).
  - `compat/jade/` — Jade plugin (`@WailaPlugin`), server data provider and client tooltip, kept in separate classes.
  - `menu/DisplayFormats` — number formats shared by the GUI and Jade.
  - `config/` — `VfwServerConfig` (spec), `VfwConfig` (runtime: compiled filters, generation), `ItemFilter`.
  - `data/` — `SoilProperties` + `ModDataMaps` (soil growth bonus data map).
  - `plant/` — `PlantRules`, `SoilRules`, `PlantAnalysis`, `SoilView`, `VfwTags`.
  - `compat/mysticalagriculture/` — `MysticalCompat` (safe facade, never references MA types) and
    `MysticalCompatImpl` (MA API calls, only loaded when MA is present). MA is `compileOnly` from maven.blakesmods.com.
  - `gametest/` — `VfwGameTests` (dev only). Run `gradlew runGameTestServer`; exit code 0 = all passed.
  - `sim/` — Minecraft-free simulation core: `GrowthCycle`, `PlotGroup`, `GrowthSpeed`, `MachineStatus`,
    `MachineConditions`, `HarvestMath`, `DropTally`, `HarvestBatching` + `YieldSample` (batch sizes).
  - `harvest/` — `DropSource`, `LootDropSource`, `HarvestPlans`, `Harvester` (drops + transactional storage).
  - `gametest/MysticalHarvestTests` — MA-only game test (references MA classes; called only when MA is loaded).
  - `gametest/MachineGameTests` — tests on a placed machine. Register new tests in `VfwGameTests.TESTS` with a
    max tick count. `FarmMatrixBlockEntity#setProgressForTesting` exists only so tests need not wait a full cycle.
- `src/test/java/com/virtualfarmworks/sim/` — JUnit tests for `sim/` (`gradlew test`).
- `src/main/resources/data/virtualfarmworks/tags/` — VFW item/block tags (datapack-editable plant/soil rules).
- `src/main/resources/assets/virtualfarmworks/` — lang, models, textures.
- `src/main/templates/META-INF/neoforge.mods.toml` — mod metadata (Gradle expands `${...}` from `gradle.properties`).
