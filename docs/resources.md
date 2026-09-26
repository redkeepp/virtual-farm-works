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
| `blockstates/starter_farm_matrix.json` | Claude | Maps the block's `facing` property (north/east/south/west) to the owner's block model, rotated 0/90/180/270 degrees around Y. The model's front is its north side (confirmed in game by the owner). Add one per new tier. |
| `models/block/*_farm_matrix.json` | owner | Blockbench models of the five Farm Matrix tiers. Reference the Portuguese-named textures in `textures/block/`. |
| `items/*_farm_matrix.json`, `items/{starter,voltaic,ionic,resonant}_water_provider_upgrade.json` | owner | 26.1 item definitions (item -> model). Farm Matrix items reuse the block model. |
| `items/entropic_water_provider_upgrade.json`, `items/*_growth_upgrade.json`, `items/crux_provider_upgrade.json` | Claude | Same, for items the owner's drop did not define. Point at `models/item/<same name>`. |
| `models/item/*_water_provider_upgrade.json` | owner | Flat item models (`minecraft:item/generated`) for the Water Provider textures. |
| `models/item/*_growth_upgrade.json`, `models/item/crux_provider_upgrade.json` | Claude | Flat item models pointing at the owner's textures `textures/item/<same name>.png`. |
| `textures/**` | owner | All art. Portuguese file names on purpose. `textures/gui/starter_farm_matrix_gui.png` is the Starter GUI (coordinates in `docs/specs/starter-farm-matrix.md`). Ignore `textures/gui/starter_farm_matrix_gui2.png` and `textures/item/antigos_nao_usar/` (unused). |
| `lang/en_us.json` | Claude | English texts. Key groups: `itemGroup.*` (creative tab), `block.*`, `item.*`, `tier.*` (tier names used in tooltips), `tooltip.*` (`%s` = argument filled by code, `%%` = a literal percent sign), `status.*` (machine states, keys built by `MachineStatus#translationKey`). Only English for now (owner decision). |

## data/ (server side: rules pack makers can change)

| File | Author | Purpose |
|---|---|---|
| `virtualfarmworks/loot_table/blocks/starter_farm_matrix.json` | Claude | Breaking the machine drops the machine itself (unless destroyed by an explosion). Machine CONTENTS are not handled here (they belong to the BlockEntity, milestone 1 step 6). |
| `minecraft/tags/block/mineable/pickaxe.json` | Claude | Adds the Starter Farm Matrix to the pickaxe-mineable tag (any pickaxe tier works because no `needs_*_tool` tag is used). Add new tiers here. |
| `virtualfarmworks/data_maps/item/soil_properties.json` | Claude | Growth bonus per soil ITEM: `"growth_bonus": 0.35` = +35% speed. Defaults: Mystical Agriculture farmlands (Inferium 15%, Prudentium 20%, Tertium 25%, Imperium 30%, Supremium 35%, Awakened Supremium 40%) and Mystical Agradditions Insanium 40% (owner values). Each entry has a `neoforge:mod_loaded` condition so it is skipped when that mod is absent. Code: `data/SoilProperties`, `data/ModDataMaps`. |
| `virtualfarmworks/tags/item/tillable_soils.json` | Claude | Soils that become farmland with a hoe in the machine (dirt, grass block, dirt path, coarse dirt, rooted dirt). Add modded dirts here. Code: `SoilRules#isTillable`. |
| `virtualfarmworks/tags/item/unplantable.json` | Claude | Seeds never accepted even though their block would qualify (torchflower seeds, pitcher pod: they grow flowers — owner decision). Code: `PlantRules#isPlantable`. |
| `virtualfarmworks/tags/item/extra_plantables.json` | Claude | Empty by default. Seeds to accept even though VFW does not recognize their block type (e.g. a modded plant that is not a `CropBlock`); their soil rule is the plant's own `mayPlaceOn`. |
| `virtualfarmworks/tags/item/harvest_byproducts.json` | Claude | Harvest drops counted as SECONDARY (scaled by `drops.secondaryDropMultiplier` instead of the production multipliers): poisonous potato and, if Mystical Agriculture is installed, Fertilized Essence (`"required": false` entry). Extra seeds are secondary automatically when the planting item is in `#c:seeds`. Code: `harvest/LootDropSource`. |
| `virtualfarmworks/tags/block/supports_mushrooms.json` | Claude | Soils for mushrooms (default `#minecraft:overrides_mushroom_light_requirement`: mycelium, podzol, nylium). Replaces vanilla's "any solid block in the dark" rule, which would make almost any block a soil. |
| `virtualfarmworks/tags/block/supports_glow_berries.json` | Claude | Blocks glow berries can hang from (overworld stone, moss, dirt). Replaces vanilla's "any sturdy bottom face" rule for the same reason. |

## Other non-Java files

| File | Purpose |
|---|---|
| `src/main/templates/META-INF/neoforge.mods.toml` | Mod metadata template; Gradle fills `${...}` from `gradle.properties`. |
| `config/virtualfarmworks-server.toml` (generated at runtime, not in the repo) | Pack-maker config, generated from `config/VfwServerConfig.java` with its comments. See `docs/specs/configurability.md`. |
