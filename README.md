# Virtual Farm Works

**Compact, server-friendly virtual farming for NeoForge.** A Farm Matrix grows plants inside a single block: put seeds
and soils in, take the harvest out. Every machine runs one shared growth cycle instead of thousands of individual
crops, so even huge farms cost almost nothing in server tick time.

Made by **Redkeep** for Minecraft **26.1.2** and NeoForge.

## Machines

### Starter Farm Matrix

An early-game farm for one kind of plant.

- One seed slot and one soil slot: plots = the smaller of the two counts, up to 64.
- Water Provider Upgrade slot: without water the machine grows at 25% speed.
- Four Growth Speed Upgrade slots (+50% speed each, up to 3x).
- Hoe slot: plants that need tilled soil (wheat on dirt) need a hoe; tilled soils (farmland) do not. Any hoe works.
- 9 output slots plus 27 hidden overflow slots; auto-export into adjacent inventories, per face.
- Harvest filter (whitelist or blacklist) to throw away what you do not want; no power needed.

### Entropic Farm Matrix

The endgame farm: up to 3,840 plots in one block.

- 60 plant groups in two 4x15 grids (plantables on top, soils below), each up to 64 plots. No hoe needed.
- Powered by FE: 90 FE per active plot per tick by default. With too little FE it keeps running, slower
  (RUNNING WITH LOW FE); at 0 FE it stops.
- Five modes per face: NONE, OUTPUT (what the plants produce), OUTPUT CRAFTED, OUTPUT ALL and INPUT. INPUT faces accept
  pipes and pull seeds and soils from adjacent inventories, pairing them into the grids.
- Replant: seeds and saplings from the harvest go into free soil first, before the output (a switch on each machine).
- Built-in autocrafter: up to 100 crafting-table recipes, set by hand or with JEI's "+" button. Harvests are crafted as
  they come out, recipes chain (essence -> ingots -> blocks), and the machine also crafts from what its output holds. A
  catalyst slot takes Mystical Agriculture's Master Infusion Crystal, so higher essence tiers craft automatically.
  The recipes stay on the machine item when you break it.
- 24 output slots plus 72 hidden overflow slots, harvest filter, the same upgrades as the Starter.

## Plants

Anything that can be planted: crops, saplings (the machine grows the tree and harvests it), flowers, grass, mushrooms,
nether fungi, aquatic and hanging plants, and modded plantables. Which soil a plant accepts comes from the game's own
rules, so modded plants and soils work without special support. Plants with no harvest of their own (flowers, grass)
give 10 of themselves per plot.

Plots added in the middle of a cycle wait for the next one, so you cannot top up a farm just before the harvest.

## Upgrades

| Upgrade | Effect |
|---|---|
| Water Provider Upgrade | Full speed (without it: 25%) |
| Growth Speed Upgrade | +50% growth speed, up to 4 per machine |
| Crux Provider Upgrade | Provides the crux any Mystical Agriculture seed needs |

Water Provider and Growth Speed Upgrades come in tiers. An upgrade fits machines of its own tier or below: Starter
upgrades fit the Starter, Entropic upgrades fit both machines.

## Recipes

Grid cells 1-9, left to right, top to bottom (JEI shows them all).

| Item | Recipe |
|---|---|
| Starter Farm Matrix | 1, 9 diamond; 2, 4, 6, 8 wheat seeds; 3, 7 redstone; 5 iron block |
| Starter Water Provider Upgrade | Corners diamond, sides iron ingot, center water bucket |
| Starter Growth Speed Upgrade | Corners redstone, sides diamond, center redstone block |
| Crux Provider Upgrade | Netherite blocks around a nether star |
| Entropic Farm Matrix | 1, 9 netherite ingot; 3, 7 diamond; 2, 4, 6, 8 redstone; 5 Starter Farm Matrix |
| Entropic Water Provider Upgrade | Corners diamond, sides iron ingot, center Starter Water Provider Upgrade |
| Entropic Growth Speed Upgrade | Corners redstone block, sides diamond, center Starter Growth Speed Upgrade |

## Mod support (all optional)

- **Mystical Agriculture** (9.0.9+): essence yields from Mystical Agriculture's own formula, cruxes, its farmland
  bonuses, a Fertilized Essence switch on every machine, and the Master Infusion Crystal as the autocrafter catalyst.
- **JEI** (29.34.0.90+): the "+" button fills the Entropic's autocrafter; items drag and drop onto the harvest filter
  and the crafting grid.
- **Jade** (26.1.10+): status, water, plots, growth and FE when you look at a machine.

## Performance

The whole point of the mod. Measured with the built-in load benchmark on a desktop PC (numbers vary with hardware):

| Situation | Cost per machine per server tick |
|---|---|
| Starter growing | 0.011 µs |
| Starter, 64 wheat plots at 3x, harvests included | 0.30 µs (1,000 such machines: 0.6% of a tick) |
| Entropic growing, 3,840 plots | 0.031 µs |
| Entropic, 3,840 wheat plots at 3x, harvests included | 4.8 µs |

A growing machine costs the same with 1 plot or 3,840: plots are counters, not objects. Machines never tick plants
one by one, never load chunks and do nothing while their chunk is unloaded.

## For server owners and pack makers

- Server config `virtualfarmworks-server.toml`: growth time, speed and yield multipliers, FE per plot, plot limits,
  hidden output size, autocrafter limits, replanting, hoe wear, auto-export interval, and seed/soil blacklists (global
  and per machine). Changes apply without a restart.
- Datapacks: tags for universal soils and autocrafter catalysts, and data maps for soil speed bonuses and fixed yields
  (`data/virtualfarmworks/`).

## Requirements and download

- Minecraft 26.1.2 and NeoForge 26.1.2.109 or newer, on the client and on the server.
- Download: [GitHub Releases](https://github.com/redkeepp/virtual-farm-works/releases). CurseForge and Modrinth pages
  are coming.

## Bugs and ideas

Open an [issue](https://github.com/redkeepp/virtual-farm-works/issues) with the mod, Minecraft and NeoForge versions
and your `logs/latest.log` (or the crash report). The issue form asks for everything needed.

## Building from source

Requires JDK 25.

```
./gradlew build
```

The jar lands in `build/libs/` (`virtualfarmworks-<Minecraft version>-<mod version>.jar`). `./gradlew runClient` starts a
development client; `./gradlew runGameTestServer` runs the game tests. Design notes and specs are in `docs/`.

## License

MIT, for both code and assets (textures, models, JSON). See [LICENSE](LICENSE). Build files derived from the NeoForge
MDK keep their original notice in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).
