# Pack-maker configurability — specification

Goal: large modpacks (e.g. ATM 11) must be able to rebalance VFW EASILY, without touching Java. **Nothing below may be
hard-coded.** Source: owner's spec, translated.

Implemented in milestone 1 step 2 (plantables settings added 2026-09-28). Server config file:
`config/virtualfarmworks-server.toml` (code: `config/VfwServerConfig.java`). Soil bonuses and fixed harvests: datapack
data maps in `data/virtualfarmworks/data_maps/item/` (code: `data/SoilProperties.java`, `data/FixedYield.java`,
`data/ModDataMaps.java`). Tags: `data/virtualfarmworks/tags/` (see `docs/resources.md`).

| Requirement | Mechanism | Key |
|---|---|---|
| Base growth time, per machine tier (default Starter 30 s) | Server config, per tier | `machines.<tier>.growthTicks` |
| Hoe loses durability (only items with durability), over time | Server config (default false; 1 point per interval) | `hoe.consumeDurability`, `hoe.wearIntervalTicks` |
| Disable the hoe requirement entirely | Server config (default true) | `hoe.requireHoe` |
| Blacklist seeds (default: all allowed) | Config list | `filters.seedBlacklist` |
| Blacklist soils | Config list | `filters.soilBlacklist` |
| Blacklist by namespace/mod | Entry form `"modid:*"` | (both lists) |
| Blacklist by tag | Entry form `"#namespace:tag"` | (both lists) |
| Global AND per-tier blacklists | Per-tier lists add to the global ones | `machines.<tier>.seedBlacklist` / `soilBlacklist` |
| Extra-seed chance on Mystical Agriculture farmlands (0% allowed) | Multiplier over MA's native chance (extra SEED only; MA's extra-essence chance is production) | `drops.mysticalagriculture.secondarySeedChanceMultiplier` |
| MA seeds only on their own tier's farmland (MA's `requiresEffectiveFarmland` rule, Inferium exempt, `#mysticalagriculture:always_effective_farmland` counts for every tier) | VFW's own switch, default false (owner): machines do not follow MA's option unless this is on too | `mysticalagriculture.requiresEffectiveFarmland` |
| Which drops are "secondary" | Item tag (planting items in `#c:seeds` are extra seeds automatically, saplings from trees too) | `#virtualfarmworks:harvest_byproducts` |
| Harvest cost on huge machines | Max loot-table evaluations per harvest (sampled and scaled, expected yield exact) | `performance.maxLootRollsPerHarvest` |
| Harvest cost of trees | Trees grown in memory per harvest (each stands for several plots, expected yield exact; default 4) | `performance.maxTreesGrownPerHarvest` |
| Yield of plants without a harvest of their own (flowers, grass, vines, aquatic plants...) | Server config, items of itself per plot and harvest (owner: default 10) | `drops.otherPlantYield` |
| A fixed harvest for any plantable (item and count per plot; defaults: Torchflower Seeds -> 10 Torchflowers, Pitcher Pod -> 10 Pitcher Plants) | Datapack data map | `fixed_yield.json` |
| Soils every plant grows on (owner: dirt, grass, any farmland; natural soils kept) | Block tag (default `#minecraft:supports_vegetation`; every farmland block counts too) | `#virtualfarmworks:universal_soils` |
| Refuse a plantable by datapack (empty by default) | Item tag, on top of the config blacklists | `#virtualfarmworks:unplantable` |
| Accept an item whose block VFW does not recognize as a plant | Item tag (harvested like a crop) | `#virtualfarmworks:extra_plantables` |
| Multiplier without Water Provider, per tier | Server config per tier (default 0.25) | `machines.<tier>.noWaterSpeedMultiplier` |
| Auto-output interval or disable | Server config (default 20, 0 = off) | `output.autoExportIntervalTicks` |
| Hidden output slots behind the visible ones, per tier (Starter 27, Entropic 72; deleted when the machine breaks) | Server config per tier (0..256, 0 = none) | `machines.<tier>.internalBufferSlots` |
| Plantables / soils per grid slot, hence the plot capacity (Entropic: 60 groups x 64 = 3,840) | Server config per grid tier (owner: default 64 each) | `machines.<tier>.seedsPerSlot`, `soilsPerSlot` |
| FE per planted plot per tick (owner: 90); the buffer is sized from it (capacity x FE x 3) | Server config per tier with energy | `machines.<tier>.energyPerPlot` |
| Replant produced plantables into free soils (Entropic) | Server config per tier (default true) | `machines.<tier>.replant` |
| Autocrafter recipes and waiting-ingredient limit (Entropic) | Server config per tier (8 recipes, 1,024 items of a kind) | `machines.<tier>.crafterRecipes`, `crafterBufferLimit` |
| Secondary drops multiplier (extra seeds, Fertilized Essence, by-products) | Server config (default 1.0) | `drops.secondaryDropMultiplier` |
| Global and per-tier production (yield) multiplier | Server config | `drops.productionMultiplier`, `machines.<tier>.productionMultiplier` |
| Growth Speed Upgrade bonus (default +50% each) | Server config | `growth.bonusPerUpgrade` |
| Growth Speed Upgrades per slot (default 1 x 4 slots) | Server config | `growth.upgradesPerSlot` |
| Soil growth accelerators (MA farmlands etc.) | Datapack data map, `growth_bonus` per soil item | `soil_properties.json` |
| Machine and upgrade recipes 100% datapack-driven | Recipes only as JSON (owner: recipes postponed) | — |
| Disable whole tiers without unregistering blocks | Remove their recipes via datapack | — |

Notes:
- Config changes bump `VfwConfig.generation()`; machines revalidate when it changes (no per-tick config reads).
- Blocks/items are always registered; disabling a tier must never delete blocks from existing saves.
- Data map entries for other mods use `neoforge:mod_loaded` conditions so VFW never hard-depends on them.
