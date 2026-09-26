# Pack-maker configurability — specification

Goal: large modpacks (e.g. ATM 11) must be able to rebalance VFW EASILY, without touching Java. **Nothing below may be
hard-coded.** Source: owner's spec, translated.

Implemented in milestone 1 step 2. Server config file: `config/virtualfarmworks-server.toml` (code:
`config/VfwServerConfig.java`). Soil bonuses: datapack data map `data/virtualfarmworks/data_maps/item/soil_properties.json`
(code: `data/SoilProperties.java`, `data/ModDataMaps.java`).

| Requirement | Mechanism | Key |
|---|---|---|
| Base growth time, per machine tier (default Starter 30 s) | Server config, per tier | `machines.<tier>.growthTicks` |
| Hoe loses durability (only items with durability) | Server config (default false) | `hoe.consumeDurability` |
| Disable the hoe requirement entirely | Server config (default true) | `hoe.requireHoe` |
| Blacklist seeds (default: all allowed) | Config list | `filters.seedBlacklist` |
| Blacklist soils | Config list | `filters.soilBlacklist` |
| Blacklist by namespace/mod | Entry form `"modid:*"` | (both lists) |
| Blacklist by tag | Entry form `"#namespace:tag"` | (both lists) |
| Global AND per-tier blacklists | Per-tier lists add to the global ones | `machines.<tier>.seedBlacklist` / `soilBlacklist` |
| Extra-seed chance on Mystical Agriculture farmlands (0% allowed) | Multiplier over MA's native chance | `drops.mysticalagriculture.secondarySeedChanceMultiplier` |
| Multiplier without Water Provider, per tier | Server config per tier (default 0.25) | `machines.<tier>.noWaterSpeedMultiplier` |
| Auto-output interval or disable | Server config (default 20, 0 = off) | `output.autoExportIntervalTicks` |
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
