# Port to Minecraft 1.21.1 — plan

Written 2026-10-02, before the port starts, so the session that does it does not start from zero. The analysis was
made on the 26.1.2 code (main at commit 544812d): file counts below are measured, the weights are estimates.

## Why and how (owner's policy, see CLAUDE.md "Two Minecraft versions")

- 26.x (branch `main`) is the base for everything; 1.21.1 (this branch, worktree folder `virtualfarmworks-1.21.1`)
  is "the stubborn old one that still needs attention": ATM10 gets every feature after 26.x, ported to the old API.
- Never shape 26.x code around 1.21.1; solve 1.21.1's own problems here. A bug in shared logic is fixed in both.
- Starting point: this branch was created from main at 544812d, the 26.1.2 code with the 1.0.1 content (logo
  included). It does not build for 1.21.1 until the first steps below are done.
- First release here: `mod_version` 1.0.1, file `virtualfarmworks-1.21.1-1.0.1.jar`, on the same CurseForge and
  Modrinth projects as 26.x.

## Versions (ATM10, as updated by the owner on 2026-10-02; all checked on their mavens)

| Dependency | Version | Maven |
|---|---|---|
| Minecraft | 1.21.1 | |
| NeoForge | 21.1.251 | maven.neoforged.net |
| Java | 21 (Gradle toolchain; the foojay resolver downloads it) | |
| Mystical Agriculture | 1.21.1-8.0.28 (mod version 8.0.28) | maven.blakesmods.com |
| Mystical Agradditions | 1.21.1-8.0.14 | maven.blakesmods.com |
| Cucumber | 1.21.1-8.0.16 (MA's library, runtime only) | maven.blakesmods.com |
| JEI | 19.57.0.446 (`jei-1.21.1-common-api`, `jei-1.21.1-neoforge-api`) | maven.blamejared.com |
| Jade | 15.10.6+neoforge | Modrinth maven |

When ATM10 updates again, re-read the versions from the owner's instance (`minecraftinstance.json` for NeoForge, the
jars in its `mods` folder for the rest; the owner grants folder access per session) and update this table.

## Dev environment

`run/mods` (git-ignored) holds 20 jars copied from the owner's ATM10 instance on 2026-10-02: Mystical Agriculture,
Mystical Agradditions, Cucumber, JEI, Jade, Mouse Tweaks, Super Factory Manager, Applied Energistics 2, Functional
Storage, Just Dire Things, Trash Slot, Trash Cans, Powah (FE source), Pipez (pipes), and their required libraries
GuideME, Titanium, Balm, SuperMartijn642's Core Lib and Config Lib, Cloth Config. `syncTestMods` copies them into
`run-gametest/mods` as on main. Final check by the owner: in a copy of the ATM10 instance or a new world, with a
backup (removing a mod later deletes its machines and their contents).

Verify every API against the 1.21.1 sources (`build/moddev/artifacts/*-sources.jar` once the build runs on 21.1.251),
never against memory of 26.1 or of other versions.

## Areas, weight and what changes

| # | Area | What changes | Weight |
|---|---|---|---|
| 1 | Build and base | `gradle.properties` (Minecraft 1.21.1, range `[1.21.1]`, NeoForge 21.1.251, the versions above), `build.gradle` (toolchain 21, JEI artifact names), the mods.toml ranges, the CI JDK. Renames: `Identifier` -> `ResourceLocation` (20 files), `ContainerInput` -> `ClickType`, `LilyPadBlock` -> `WaterlilyBlock`, `NetherFungusBlock` -> `FungusBlock`, `VegetationBlock` -> `BushBlock`, `FMLEnvironment.isProduction()` -> `FMLEnvironment.production`, `ClientPacketDistributor` -> `PacketDistributor`. No Java 22+ syntax is used. | light, mechanical |
| 2 | Inventories and energy | The new NeoForge transfer API (`ResourceHandler`, `ItemResource`, `Transaction`, `EnergyHandler`; 26 files, 23 with `ItemResource`) does not exist: 1.21.1 has `IItemHandler` / `IEnergyStorage` (simulate, then execute; no transactions). Keep the harvest's "no dupe, no void" guarantee with simulate/execute. `ItemResource` becomes an item key (item + components); keep the lesson that hashing components per slot or cell is expensive. FE is an `int` (about 2.1 billion max): clamp the buffer when the config raises FE per plot. Touches the harvest, both outputs, face modes, pipe input, auto-export, the energy buffer and the autocrafter. | heavy (the core) |
| 3 | Saving | `ValueInput` / `ValueOutput` -> NBT (`saveAdditional` / `loadAdditional` with `CompoundTag` and `HolderLookup.Provider`, codecs through `RegistryOps`) in the machine, the inventories, the crafter and the filter (4 files). Saves never move between Minecraft versions, so no compatibility with 26.1 saves is needed. | medium |
| 4 | Recipes and autocrafter | `level.recipeAccess()` -> `getRecipeManager()`; the recipe hint is a `RecipeHolder`, ids are `ResourceLocation`; `placementInfo()` does not exist (find the 1.21.1 way to refuse unplaceable recipes); `assemble` takes the registries. The 7 recipe JSONs need ingredient objects (`{"item": "minecraft:diamond"}`). | medium |
| 5 | Plants and tags | 1.21.1 has no `#minecraft:supports_vegetation` (soil rules were code then): rework the defaults of `universal_soils`, `supports_mushrooms` (`overrides_mushroom_light_requirement`) and `supports_glow_berries` (`moss_blocks`), checking which vanilla tags exist, and re-check `PlantRules` against 1.21.1's `mayPlaceOn` / `canSurvive`. Plants added after 1.21.1 (pale garden, dry grass, firefly bush, leaf litter, cactus flower, eyeblossoms) appear only in `PlantablesGameTests`: remove them there. | medium |
| 6 | Blocks and items | `useItemOn` returns `ItemInteractionResult`; drops on break go through `Block#onRemove` instead of `BlockEntity#preRemoveSideEffects`; `appendHoverText(ItemStack, TooltipContext, List<Component>, TooltipFlag)` (no `TooltipDisplay`); the "Autocrafter recipes" tooltip through `ItemTooltipEvent` (no `RegisterTooltipAppendersEvent`); `applyImplicitComponents(DataComponentInput)`; item models come from `models/item` (the `items/` folder is ignored); `Item.Properties` without ids. | light |
| 7 | GUI | The 26.1 drawing API (`GuiGraphicsExtractor`, `extract*`, `RenderPipelines`) and the new input events -> 1.21.1's `GuiGraphics` (`renderBg`, `render`, `renderLabels`, `blit` with a `ResourceLocation`, `PoseStack`) and `mouseClicked(double, double, int)`-style methods. Both screens (`FarmMatrixScreen`, `EntropicFarmMatrixScreen`, about 2,000 lines in `client/`); the layout constants, colors and textures stay. `GuiScaleFit` is pure; its caller adapts to 1.21.1's `Window` (double GUI scale). | heavy |
| 8 | Integrations | JEI 19 (crafting recipe type over `RecipeHolder<CraftingRecipe>`, transfer and ghost handlers), Jade 15, Mystical Agriculture 8.0.28 with Cucumber 8.0.16: re-verify every MA finding (essence formula, cruxes, farmland tiers, Fertilized Essence, Master Infusion Crystal as `BaseReusableItem`) against the 8.x jars, since the integration was written from 9.0.9. | medium |
| 9 | Tests | Game tests use 26.1's test-function registry; 1.21.1 uses `@GameTest` methods registered through `RegisterGameTestsEvent` with a structure template (an empty one). Test bodies follow the other areas. JUnit (`sim/`, `GuiScaleFit`, `SmoothProgress`, `ConfigFileLayout`) is plain Java and should pass unchanged. Then the load benchmark and the owner's test in ATM10. | medium |
| 10 | Assets and data | Textures, GUI textures, lang, models, blockstates, loot tables (`copy_components` exists), advancements and data maps stay; only the recipe JSONs (area 4) and tags (area 5) change. | almost none |
| 11 | Config | `ModConfigSpec` is the same. To confirm: 1.21.1 probably keeps the server config inside each world (`serverconfig/`); if so, say it in this line's README. | light |

## Order

Progress is marked here as the port goes (DONE = committed on this branch); `docs/history.md` ("Port to 1.21.1") has
the details and the reasons. The code does not compile until every area of `src/main` is ported (javac compiles the
whole source set at once), so the steps are committed as they are finished and checked against the compiler's error
list: after each step, no error is left in the files and areas it covers.

1. Build and base, until the code compiles again together with steps 2 and 3. **DONE** (2026-10-02): versions,
   Java 21 toolchain, `data()` run, CI JDK, FML 4's `modLoader` / `loaderVersion` in the mods.toml template, the
   renames of area 1 plus `Registry#getValue` -> `get`, `ItemStack#typeHolder` -> `getItemHolder` and
   `org.jspecify` -> `org.jetbrains.annotations.Nullable` (jspecify is not on the 1.21.1 classpath).
2. Inventories and energy. **DONE** (2026-10-02): VFW's own stand-ins in a new package `transfer/` (`ItemResource`,
   `ItemSlots` over NeoForge's `ItemStackHandler`, `SlotTransaction` for all-or-nothing changes to VFW's own buffers,
   `SlotRange`); `IItemHandler` / `IEnergyStorage` capabilities; routed pipe input on every slot; simulate/execute
   with neighbours; `menu/HandlerSlot` for the menus. The inventories' own save format is part of this step (counts
   above 99 survive); the machine's save code is step 3.
3. Saving. **DONE** (2026-10-02): `saveAdditional` / `loadAdditional(CompoundTag, HolderLookup.Provider)` in the
   machine, `save` / `load` with `CompoundTag` in the crafter, the filter and the energy buffer; `machine/Saves` keeps
   26.1's conveniences (a codec value under a key with registry ops, booleans with a default). Same keys as main.
4. Recipes and autocrafter. **DONE** (2026-10-02): `getRecipeManager().getRecipeFor(..., ResourceLocation hint)`,
   recipe ids are `ResourceLocation`s, `assemble(input, registries)`; "placeable by a player" = what the recipe book
   lists in 1.21.1 (`!isSpecial() && !isIncomplete()`, `ClientRecipeBook`'s own test). The 7 recipe JSONs use
   ingredient objects. JEI's side is step 7.
5. Plants and tags. **DONE** (2026-10-02): the soil rules of 1.21.1 read from its sources (`#minecraft:dirt`,
   `#minecraft:sand`, `#minecraft:bamboo_plantable_on`, `#minecraft:jungle_logs`, end stone, soul sand, any `FarmBlock`),
   NeoForge's own `TriState`, `FarmBlock` (1.21.1's farmland class), no pale hanging moss; `VirtualLevel` / `SoilView`
   on 1.21.1's level interfaces; the three block tags' defaults (`universal_soils`, `supports_mushrooms`,
   `supports_glow_berries`). The reflected fields and methods (stem fruit, tree growers, fungi) have the same names in
   1.21.1. The plants newer than 1.21.1 in `PlantablesGameTests` are step 8.
6. GUI (with area 6, blocks and items, where it touches screens). **DONE** (2026-10-02): both screens on 1.21.1's
   `GuiGraphics` (`renderBg` / `renderLabels` / `renderTooltip` called from `render`, `blit(texture, ...)`,
   `drawString`, `PoseStack`, ghost items covered with a fill plus vanilla's ghost-recipe overlay, `mouseClicked(x, y,
   button)` with the recipe list's double click measured by the screen, `hasClickedOutside` with the button, the GUI
   scale fit on a double scale and `resize(Minecraft, w, h)`); blocks and items (`useItemOn` returns an
   `ItemInteractionResult`, contents dropped in `Block#onRemove`, the "Autocrafter recipes" line from
   `FarmMatrixBlock#appendHoverText`, `applyImplicitComponents(DataComponentInput)`, `BlockEntityType.Builder`, item
   models for the machines in `models/item`).
   **Open (owner):** the owner's Blockbench block models (`models/block/*_farm_matrix.json`) use 26.1's element
   rotation format (`"rotation": {"origin", "x", "y", "z"}`, 90 and 180 degrees, two axes at once). 1.21.1 only reads
   `{"origin", "axis", "angle"}` with one axis and an angle of 0, +-22.5 or +-45 (it throws "Missing axis"), so on
   1.21.1 the machines show the missing model until the owner exports 1.21.1-compatible models (rule 7: art is not
   changed by Claude without the owner).
7. Integrations. **DONE** (2026-10-02): JEI 19 (`mezz.jei.api.recipe.RecipeType`; the 6-argument `transferRecipe`
   is the one JEI 19 still declares abstract), Jade 15 (same API; `CompoundTag` getters with defaults), Mystical
   Agriculture 8.0.28 re-read from its bytecode: drop formulas (resource and Inferium crops), secondary chance, crux,
   effective farmland (tier's own farmland only: MA 8 has no always-effective tag), config fields, Master Infusion
   Crystal (Cucumber's `BaseReusableItem`, unbreakable, gives itself back) all as VFW reproduces them; no Awakened
   Supremium farmland in MA 8 (its `soil_properties` entry is left out on this line). Area 11: the server config is
   still `config/virtualfarmworks-server.toml`; a world may override it in `<world>/serverconfig/`. `src/main`
   compiles with no error or warning.
8. Game tests, JUnit and the load benchmark; then the owner's in-game test in ATM10. **DONE** (2026-10-02) up to the
   owner's in-game test: JUnit passes unchanged (86 tests, Java 21); the 45 game tests (every VFW test of main) pass
   with Mystical Agriculture 8.0.28 loaded; the load benchmark runs (3 runs, every game closed; numbers and the
   comparison with main in `docs/history.md`). How the tests were ported, all read in the 1.21.1 sources:
   - Registration: no test-function registry. `RegisterGameTestsEvent#register(VfwGameTests.class)` and a static
     `@GameTestGenerator` method returning `TestFunction(batch, name, structure, maxTicks, 0, true, function)` (with
     `neoforge.enabledGameTestNamespaces` set, generated tests are kept by their structure's namespace).
   - Structure: none is built in. VFW ships an empty 8x4x8 template (DataVersion 3955) as
     `src/gametest/resources/data/virtualfarmworks/structure/empty.nbt` (`virtualfarmworks:empty`, documented in
     `docs/resources.md`).
   - Positions: relative (0, 0, 0) is the test's STRUCTURE BLOCK (the test area starts at y 1), so every `MACHINE`
     is (0, 1, 0); the Entropic test's extra machines (2, 1, 0) and (4, 1, 0); the MA test's farmland too.
   - `GameTestHelper`: `assertTrue(boolean, String)`, `getBlockEntity(pos)` (generic, no class), `fail(String)`.
   - Transfer calls became `IItemHandler` / `IEnergyStorage` calls (simulated where only the answer matters);
     `ItemStacksResourceHandler` -> `ItemSlots` (`OutputBuffer` for the plain 9-slot buffer); `useItemOn` returns an
     `ItemInteractionResult` (`PASS_TO_DEFAULT_BLOCK_INTERACTION` = falls through to the GUI); recipes as in step 4.
   - The plants newer than 1.21.1 left the tests (cactus flower, pale hanging moss, pale oak, golden dandelion, open
     eyeblossom, leaf litter, dry short grass, firefly bush, pale moss block and carpet).
   - MA 8 in the MA tests: `CropTier#getFarmland()` (not `getFarmlandBlock()`); no Awakened Supremium farmland and no
     always-effective tag, so that check went.
   - Added to `src/main` for the tests: `ItemResource#is(Item)`, `MachineEnergy#set(int)`.

   **Left for the owner** (nothing on the client was run by Claude): the in-game test — both screens, JEI's "+" and
   drag and drop, Jade, tooltips, the GUI scale fit, a dedicated server, ATM10's pipes and FE sources — in a copy of
   the ATM10 instance or a new world with a backup; and the block models of step 6 (missing model until then).

Commit on this branch after every finished step; the owner pushes the branch.
