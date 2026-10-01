# Publishing — CurseForge and Modrinth pages

Texts and settings for the project pages, ready to paste (prepared 2026-09-30 for the first upload). The description
is the README adapted for the platforms: its images use absolute GitHub URLs, so they show once `docs/images/` is
pushed. Both platforms review new projects before they go public.

## Both platforms

| Field | Value |
|---|---|
| Name | Virtual Farm Works |
| Author / owner | Redkeep |
| Summary | Compact, server-friendly virtual farming: grow thousands of plants inside one block. Works with any crop, tree or modded plant, with optional Mystical Agriculture, JEI and Jade support. |
| Icon | `src/main/resources/virtualfarmworks_logo2.png` (512x512) |
| License | MIT |
| Source code | https://github.com/redkeepp/virtual-farm-works |
| Issues | https://github.com/redkeepp/virtual-farm-works/issues |
| Environment | Required on both the client and the server |
| Loader / game version | NeoForge, Minecraft 26.1.2 |
| Optional dependencies | JEI, Jade, Mystical Agriculture (mark them "optional": the mod runs without them) |

Gallery (in this order; the first can be the featured image):

| File | Title | Caption |
|---|---|---|
| `docs/images/entropic_working.png` | Entropic Farm Matrix | 3,840 plots in one block, all active, on Supremium Farmland (4.05x speed). |
| `docs/images/entropic_crafter.png` | Built-in autocrafter | Chained Mystical Agriculture essence recipes, with the Master Infusion Crystal as catalyst. |
| `docs/images/starter_working.png` | Starter Farm Matrix | One plant type, up to 64 plots, no power needed. |

First upload: the next release jar (`virtualfarmworks-26.1.2-<version>.jar` from `build/libs/`), version number = the
mod version, name "Virtual Farm Works <version>", release channel Release, changelog = its section of `CHANGELOG.md`.

## Modrinth

- Project type: Mod. URL: `virtual-farm-works`.
- Categories: Technology (main), Utility.
- Client side: Required. Server side: Required.
- Links: Source and Issues as above. Owner: the Redkeep account (or a Redkeep organization).

## CurseForge

- Project type: Mod (Minecraft). URL: `virtual-farm-works`.
- Main category: Technology > Farming. Extra categories: Technology > Automation, Server Utility (pick the closest
  ones the form offers).
- Links: Source and Issues as above; "Allow modpacks": yes.

## Description (paste as Markdown on both)

```markdown
# Virtual Farm Works

**Compact, server-friendly virtual farming for NeoForge.** A Farm Matrix grows plants inside a single block: put seeds and soils in, take the harvest out. Every machine runs one shared growth cycle instead of thousands of individual crops, so even huge farms cost almost nothing in server tick time.

![Entropic Farm Matrix](https://raw.githubusercontent.com/redkeepp/virtual-farm-works/main/docs/images/entropic_working.png)

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
- Powered by FE: 90 FE per active plot per tick by default. With too little FE it keeps running, slower (RUNNING WITH LOW FE); at 0 FE it stops.
- Five modes per face: NONE, OUTPUT (what the plants produce), OUTPUT CRAFTED, OUTPUT ALL and INPUT. INPUT faces accept pipes and pull seeds and soils from adjacent inventories, pairing them into the grids.
- Replant: seeds and saplings from the harvest go into free soil first, before the output (a switch on each machine).
- Built-in autocrafter: up to 100 crafting-table recipes, set by hand or with JEI's "+" button. Harvests are crafted as they come out, recipes chain (essence -> ingots -> blocks), and the machine also crafts from what its output holds. A catalyst slot takes Mystical Agriculture's Master Infusion Crystal, so higher essence tiers craft automatically. The recipes stay on the machine item when you break it.
- 24 output slots plus 72 hidden overflow slots, harvest filter, the same upgrades as the Starter.

![Autocrafter](https://raw.githubusercontent.com/redkeepp/virtual-farm-works/main/docs/images/entropic_crafter.png)

## Plants

Anything that can be planted: crops, saplings (the machine grows the tree and harvests it), flowers, grass, mushrooms, nether fungi, aquatic and hanging plants, and modded plantables. Which soil a plant accepts comes from the game's own rules, so modded plants and soils work without special support.

## Upgrades

- **Water Provider Upgrade**: full speed (without it: 25%).
- **Growth Speed Upgrade**: +50% growth speed, up to 4 per machine.
- **Crux Provider Upgrade**: provides the crux any Mystical Agriculture seed needs.

Water Provider and Growth Speed Upgrades come in tiers: Starter upgrades fit the Starter, Entropic upgrades fit both machines. All recipes are in JEI.

## Mod support (all optional)

- **Mystical Agriculture**: essence yields from its own formula, cruxes, farmland bonuses, a Fertilized Essence switch, and the Master Infusion Crystal as the autocrafter catalyst.
- **JEI**: the "+" button fills the Entropic's autocrafter; items drag and drop onto the harvest filter and the crafting grid.
- **Jade**: status, water, plots, growth and FE when you look at a machine.

## Performance

Measured with the mod's built-in load benchmark on a desktop PC (per machine, per server tick):

- Starter growing: 0.011 µs. Starter with 64 wheat plots at 3x, harvests included: 0.30 µs (1,000 such machines: 0.6% of a tick).
- Entropic growing, 3,840 plots: 0.031 µs. Entropic with 3,840 wheat plots at 3x, harvests included: 4.8 µs.

A growing machine costs the same with 1 plot or 3,840: plots are counters, not objects. Machines never tick plants one by one, never load chunks and do nothing while their chunk is unloaded.

## For server owners and pack makers

Server config `virtualfarmworks-server.toml` (growth time, speed and yield multipliers, FE per plot, limits, replanting, hoe wear, auto-export, blacklists), applied without a restart. Tags and data maps for soils, catalysts, soil speed bonuses and fixed yields. Modpacks are welcome, no need to ask.

## Links

Source code and bug reports: https://github.com/redkeepp/virtual-farm-works (MIT license).
```
