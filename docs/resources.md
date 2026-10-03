# Resource files (JSON) — what each one does

JSON cannot hold comments, so every JSON file shipped in `src/main/resources/` is documented here instead.
**Keep this file in sync** when adding, removing or changing the meaning of a resource file.

"Author" matters: files marked **owner** are the owner's art and must not be edited, renamed or regenerated
(CLAUDE.md rule 7). Files marked **Claude** are plumbing and can be changed freely.

Pack makers override any `data/...` file with a datapack at the same path. Tags merge by default; add
`"replace": true` to a tag file to replace the list instead of adding to it. Data maps also merge; use `"remove"` to
drop entries (see NeoForge data map docs).

## assets/virtualfarmworks/ (client side: visuals and text)

| File(s) | Author | Purpose |
|---|---|---|
| `blockstates/starter_farm_matrix.json`, `blockstates/entropic_farm_matrix.json` | Claude | Maps the block's `facing` property (north/east/south/west) to the owner's block model, rotated 0/90/180/270 degrees around Y. The model's front is its north side (confirmed in game by the owner). Add one per new tier. |
| `items/entropic_farm_matrix.json` | Claude | Item definition of the Entropic machine (the owner's drop had none): points at the owner's block model. |
| `models/block/*_farm_matrix.json` | owner | Blockbench models of the five Farm Matrix tiers. Reference the Portuguese-named textures in `textures/block/`. On the 1.21.1 line they are main's exports converted by `tools/convert_block_models.py` (owner OK, 2026-10-02): 1.21.1 refuses 26.1's multi-axis element rotations, so the 35 rotated elements of each model are written as the same boxes already turned (same faces, textures and UVs, with face rotations); everything else is the owner's file unchanged. |
| `tools/convert_block_models.py` | Claude | 1.21.1 line only: the converter above (Python, standard library). Run it from the repository root after bringing a re-exported model from main; it verifies every face against 26.1's baking before writing and leaves converted files alone. Not part of the jar. |
| `items/*_farm_matrix.json`, `items/{starter,voltaic,ionic,resonant}_water_provider_upgrade.json` | owner | 26.1 item definitions (item -> model). Farm Matrix items reuse the block model. 1.21.1 does not read the `items/` folder (kept as the owner shipped it); it reads `models/item/` only. |
| `models/item/{starter,entropic}_farm_matrix.json` | Claude | 1.21.1 line only: the machine items' models, parent = the owner's block model (what `items/*_farm_matrix.json` say on main). |
| `items/entropic_water_provider_upgrade.json`, `items/*_growth_upgrade.json`, `items/crux_provider_upgrade.json` | Claude | Same, for items the owner's drop did not define. Point at `models/item/<same name>`. |
| `models/item/*_water_provider_upgrade.json` | owner | Flat item models (`minecraft:item/generated`) for the Water Provider textures. |
| `models/item/*_growth_upgrade.json`, `models/item/crux_provider_upgrade.json` | Claude | Flat item models pointing at the owner's textures `textures/item/<same name>.png`. |
| `textures/**` | owner | All art. Portuguese file names on purpose. `textures/gui/starter_farm_matrix_gui.png` is the Starter GUI (coordinates in `docs/specs/starter-farm-matrix.md`). Ignore `textures/gui/starter_farm_matrix_gui2.png` and `textures/item/antigos_nao_usar/` (unused). |
| `lang/en_us.json` | Claude | English texts. Key groups: `itemGroup.*` (creative tab), `block.*`, `item.*`, `tier.*` (tier names used in tooltips), `tooltip.*` (`%s` = argument filled by code, `%%` = a literal percent sign), `status.*` (machine states, keys built by `MachineStatus#translationKey`), `side.*` (machine faces, keys built by `RelativeSide#translationKey`; `side.*.short` = labels in the face box), `gui.*` (GUI title per tier, info lines, button and slot tooltips). Only English for now (owner decision). |

## data/ (server side: rules pack makers can change)

| File | Author | Purpose |
|---|---|---|
| `virtualfarmworks/loot_table/blocks/{starter,entropic}_farm_matrix.json` | Claude | Breaking the machine drops the machine itself (unless destroyed by an explosion). Machine CONTENTS are not handled here (they belong to the BlockEntity). The Entropic's also copies the `virtualfarmworks:crafter_recipes` component (`copy_components`, source `block_entity`): the autocrafter's recipes stay on the item (owner, 2026-09-30). |
| `minecraft/tags/block/mineable/pickaxe.json` | Claude | Adds the Starter and Entropic Farm Matrix to the pickaxe-mineable tag (any pickaxe tier works because no `needs_*_tool` tag is used). Add new tiers here. |
| `virtualfarmworks/data_maps/item/soil_properties.json` | Claude | Growth bonus per soil ITEM: `"growth_bonus": 0.35` = +35% speed. Defaults: Mystical Agriculture farmlands (Inferium 15%, Prudentium 20%, Tertium 25%, Imperium 30%, Supremium 35%, Awakened Supremium 40%) and Mystical Agradditions Insanium 40% (owner values). 1.21.1 line: no Awakened Supremium entry (Mystical Agriculture 8 has no such farmland, and NeoForge logs an error for every missing id in a data map). Each entry has a `neoforge:mod_loaded` condition so it is skipped when that mod is absent. Code: `data/SoilProperties`, `data/ModDataMaps`. |
| `virtualfarmworks/data_maps/item/fixed_yield.json` | Claude | Plantable ITEMS whose plots yield a fixed harvest instead of their own: `"item"` (default: the plant itself) x `"count"` (default: config `drops.otherPlantYield`, 10) per plot, the plant staying planted. Defaults (owner, 2026-09-28): Torchflower Seeds -> Torchflower, Pitcher Pod -> Pitcher Plant. Pack makers can give any plant (vanilla or modded) a fixed harvest here. Code: `data/FixedYield`, `harvest/FixedDropSource`. |
| `virtualfarmworks/tags/item/tillable_soils.json` | Claude | Soils that become farmland with a hoe in the machine (dirt, grass block, dirt path, coarse dirt, rooted dirt). Add modded dirts here. Code: `SoilRules#isTillable`. |
| `virtualfarmworks/tags/item/unplantable.json` | Claude | Plantables never accepted even though their block would qualify. Empty by default since 2026-09-28 (owner: every plantable is accepted; torchflower seeds and pitcher pod used to be here). Code: `PlantRules#isPlantable`. |
| `virtualfarmworks/tags/item/extra_plantables.json` | Claude | Empty by default. Items to accept even though VFW does not recognize their block as a plant (a modded plant built on a plain block); they are harvested like crops (their block's loot, one planting item replanted) and grow only on soils whose `canSustainPlant` hook accepts them. |
| `virtualfarmworks/tags/item/crafter_catalysts.json` | Claude (owner request 2026-09-29) | Items the Entropic autocrafter's catalyst slot accepts (below the result in the crafter panel): recipes that need one use it without spending it, as long as the recipe gives it back whole (the Master Infusion Crystal does; a breakable Infusion Crystal would not, so it is not listed). Default: Mystical Agriculture's Master Infusion Crystal (`"required": false` entry). Code: `machine/MachineInventory`, `machine/MachineCrafter`. |
| `virtualfarmworks/tags/item/harvest_byproducts.json` | Claude | Harvest drops counted as SECONDARY (scaled by `drops.secondaryDropMultiplier` instead of the production multipliers): poisonous potato and, if Mystical Agriculture is installed, Fertilized Essence (`"required": false` entry). Extra seeds are secondary automatically when the planting item is in `#c:seeds`, and so are the saplings a tree drops (`#minecraft:saplings`). Code: `harvest/LootDropSource`, `harvest/TreeDropSource`. |
| `virtualfarmworks/tags/block/universal_soils.json` | Claude | Soils where every plant that stands on dirt grows, on top of its natural soils (owner, 2026-09-28: "dirt, grass, any farmland"). 1.21.1 default: `#minecraft:dirt` (dirt, coarse and rooted dirt, grass block, podzol, mycelium, moss block, mud, muddy mangrove roots) and `minecraft:farmland`, vanilla 1.21.1's own vegetation rule (main uses `#minecraft:supports_vegetation`, which 1.21.1 does not have). Every farmland block (Mystical Agriculture, Agradditions, other mods) counts too without being listed. Code: `PlantRules#isUniversalSoil`. |
| `virtualfarmworks/tags/block/supports_mushrooms.json` | Claude | Soils for mushrooms (1.21.1 default `#minecraft:mushroom_grow_block`: mycelium, podzol, nylium; main uses its later name `overrides_mushroom_light_requirement`). Replaces vanilla's "any solid block in the dark" rule, which would make almost any block a soil. |
| `virtualfarmworks/tags/block/supports_glow_berries.json` | Claude | Blocks glow berries can hang from (`#minecraft:base_stone_overworld`, moss block, `#minecraft:dirt`; 1.21.1 has no `#minecraft:moss_blocks`). Replaces vanilla's "any sturdy bottom face" rule for the same reason. |

### Recipes (owner-defined costs; datapack-replaceable)

Crafting grid slots are numbered 1-9 left to right, top to bottom.

| File | Author | Recipe |
|---|---|---|
| `virtualfarmworks/recipe/starter_farm_matrix.json` | Claude (owner's cost) | 1, 9 diamond; 2, 4, 6, 8 wheat seeds; 3, 7 redstone; 5 iron block |
| `virtualfarmworks/recipe/starter_water_provider_upgrade.json` | Claude (owner's cost) | 1, 3, 7, 9 diamond; 2, 4, 6, 8 iron ingot; 5 water bucket (the empty bucket is returned) |
| `virtualfarmworks/recipe/starter_growth_upgrade.json` | Claude (owner's cost) | 1, 3, 7, 9 redstone; 2, 4, 6, 8 diamond; 5 redstone block |
| `virtualfarmworks/recipe/crux_provider_upgrade.json` | Claude (owner's cost) | 1-4, 6-9 netherite block; 5 nether star (expensive on purpose: only very rare seeds need a crux) |
| `virtualfarmworks/recipe/entropic_farm_matrix.json` | Claude (owner's cost, 2026-09-30) | 1, 9 netherite ingot; 3, 7 diamond; 2, 4, 6, 8 redstone; 5 Starter Farm Matrix (the owner wrote "netherite": the ingot, since the owner names blocks as blocks) |
| `virtualfarmworks/recipe/entropic_water_provider_upgrade.json` | Claude (owner's cost, 2026-09-30) | The Starter one with a Starter Water Provider Upgrade in the middle: 1, 3, 7, 9 diamond; 2, 4, 6, 8 iron ingot; 5 Starter Water Provider Upgrade |
| `virtualfarmworks/recipe/entropic_growth_upgrade.json` | Claude (owner's cost, 2026-09-30) | 1, 3, 7, 9 redstone block; 2, 4, 6, 8 diamond; 5 Starter Growth Speed Upgrade |
| `virtualfarmworks/advancement/recipes/misc/*.json` | Claude | Unlock each recipe in the recipe book when the player gets a key item (wheat seeds, a Starter Farm Matrix for its upgrades and for the Entropic Farm Matrix, an Entropic Farm Matrix for its upgrades, a nether star). Crafting works without them. |

Like every shaped recipe, each one also crafts from its mirror image (left and right swapped). Voltaic, Ionic and
Resonant have no recipes yet (their machines and upgrades are not built).

1.21.1 line: every key of a recipe is an ingredient object (`{"item": "minecraft:diamond"}`, or `{"tag": ...}`), the
format 1.21.1 reads; main writes plain ids (`"minecraft:diamond"`), which 1.21.1 refuses. The result is
`{"id": ...}` on both.

## Other non-Java files

| File | Purpose |
|---|---|
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata template; Gradle fills `${...}` from `gradle.properties`: the language loader and its version range (`modLoader`, `loaderVersion`: required by FML 4 on the 1.21.1 line), description, author (Redkeep), home page and issue links (left out while empty), the owner's logo, and JEI, Jade and Mystical Agriculture as optional dependencies (the built-against versions as minimums). |
| `src/main/resources/virtualfarmworks_logo2.png` | The owner's logo (512x512): `logoFile` in the mod metadata (mods list, no blur), the README header, the CurseForge/Modrinth icon. |
| `docs/images/*.png` | The owner's screenshots (Starter, Entropic, autocrafter), shown in the README and the platform galleries. |
| `docs/publishing.md` | CurseForge and Modrinth page texts and settings, ready to paste. |
| `README.md` | The public face for players and pack makers: machines, plants, upgrades, recipes, mod support, measured performance, config, requirements, download, bug reports, building, license. Its body can serve as the CurseForge/Modrinth description. Keep it in sync with the features and the benchmark. |
| `CHANGELOG.md` | One section per release (Keep a Changelog style); the GitHub, CurseForge and Modrinth release notes come from it. |
| `.github/ISSUE_TEMPLATE/` | `bug_report.yml` (asks for the mod, NeoForge and Minecraft versions, single player or server, other mods, steps and a log link), `feature_request.yml`, `config.yml` (no blank issues). |
| `src/gametest/resources/data/virtualfarmworks/structure/empty.nbt` | 1.21.1 line, development only: the empty 8 x 4 x 8 template every game test runs in (`virtualfarmworks:empty`, see `gametest/VfwGameTests`), since 1.21.1 ships no empty test structure. Gzip-compressed NBT written by a script (size 8 x 4 x 8, one air block in its palette, no blocks or entities; DataVersion 3955 = 1.21.1); never in the release jar (gametest source set). |
| `config/virtualfarmworks-server.toml` (generated at runtime, not in the repo) | Pack-maker config, generated from `config/VfwServerConfig.java` with its comments. See `docs/specs/configurability.md`. |
