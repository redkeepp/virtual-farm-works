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

**Compact, server-friendly virtual farming.** A Farm Matrix grows plants inside a single block: put seeds and soils in, take the harvest out. Every machine runs one shared growth cycle instead of thousands of individual crops: 3,840 plots, the maximum capacity of an Entropic Farm Matrix, cost the same as a single seed in a Starter Farm Matrix, so even huge farms cost almost nothing in server tick time.

![Entropic Farm Matrix](https://raw.githubusercontent.com/redkeepp/virtual-farm-works/main/docs/images/entropic_working.png)

## Machines (Starter and Entropic for now, new mid tiers coming)

### Starter Farm Matrix

An early-game farm for one kind of plant.

*   One seed slot and one soil slot: plots = the smaller of the two counts, up to 64.
*   Water Provider Upgrade slot: without water the machine grows at only 25% speed (by default).
*   Four Growth Speed Upgrade slots (+50% speed each, up to 3x by default).
*   Hoe slot: plants that need tilled soil (wheat on dirt) need a hoe; tilled soils (farmland) do not. Any hoe from any mod works.
*   Auto-export into adjacent inventories, per face.
*   Fertilized Essence switch (ON/OFF): with Mystical Agriculture installed, choose whether its crops also drop Fertilized Essence.
*   Harvest filter (whitelist or blacklist) to throw away the drops you do not want.
*   No power needed.

![Starter Farm Matrix](https://raw.githubusercontent.com/redkeepp/virtual-farm-works/main/docs/images/starter_working.png)

### Entropic Farm Matrix

The endgame farm: up to 3,840 plots in one block.

*   60 plant kinds in two 4x15 grids (plantables on top, soils below), each up to 64 plots. No hoe needed.
*   Powered by FE: 90 FE per active plot per tick by default. With too little FE it keeps running, only slower; at 0 FE it stops.
*   Six modes per face (click a face to cycle, hover it to read what the mode does):
    *   **NONE**: gives and takes nothing.
    *   **OUTPUT ONLY SEED PRODUCTION**: exports what the plants produced, not crafted items.
    *   **OUTPUT ONLY CRAFTED**: exports only what the autocrafter made.
    *   **OUTPUT ALL PRODUCED**: exports the harvest and the crafted items, never the seeds and soils in the grids.
    *   **INPUT SEEDS AND SOILS**: pipes and adjacent inventories fill the grids, pairing seeds with soils.
    *   **OUTPUT ONLY SEEDS AND SOILS**: gives back the seeds and soils planted in the grids, to pipes or a chest placed against that face. Handy to empty a machine or move its plants to another one.
*   Replant: when it is on and a plant drops new seeds, those seeds plant themselves in the machine's free soils (when there are more soils than seeds).
*   Built-in autocrafter: up to 100 crafting recipes, set by hand or with JEI's "+" button. Harvests are crafted as they come out, recipes chain (essence -> ingots -> blocks), and the machine also crafts from what its output holds. A catalyst slot takes Mystical Agriculture's Master Infusion Crystal, so higher essence tiers craft automatically. The recipes stay on the machine item when you break it.
*   The same upgrades as the Starter.

![Autocrafter](https://raw.githubusercontent.com/redkeepp/virtual-farm-works/main/docs/images/entropic_crafter.png)

## Plants

Anything that can be planted: crops, saplings (the machine grows the tree and harvests it), flowers, grass, mushrooms, nether fungi, aquatic and hanging plants, and other modded plantables. Which soil a plant accepts comes from the game's own rules, so modded plants and soils work without special support.

## Upgrades

*   **Water Provider Upgrade**: full speed (without it: 25%).
*   **Growth Speed Upgrade**: +50% growth speed, up to 4 per machine.
*   **Crux Provider Upgrade**: provides the crux any Mystical Agriculture seed needs.

Water Provider and Growth Speed Upgrades come in tiers: Starter upgrades fit the Starter, Entropic upgrades fit both machines.

## Mod support (all optional)

*   **Mystical Agriculture**: essence yields from its own formula, cruxes, farmland bonuses, a Fertilized Essence switch, and the Master Infusion Crystal as the autocrafter catalyst.
*   **JEI**: the "+" button fills the Entropic's autocrafter; items can be dragged onto the harvest filter and the crafting grid.
*   **Jade**: status, water, plots, growth and FE when you look at a machine.

## Performance

A growing machine costs the same with 1 plot or 3,840: plots are counters, not objects. Machines never tick plants one by one, never load chunks and do nothing while their chunk is unloaded.

## For server owners and pack makers

Everything below is in the server config `virtualfarmworks-server.toml` and applies without a restart. Modpacks are welcome, no need to ask.

**Per machine (Starter and Entropic separately)**

*   `growthTicks`: length of one growth cycle at 1.0x speed (default 600 = 30 seconds).
*   `noWaterSpeedMultiplier`: speed without a Water Provider (default 0.25).
*   `productionMultiplier`: yield multiplier for that machine (default 1.0).
*   `internalBufferSlots`: hidden output slots behind the visible ones (Starter 27, Entropic 72).
*   `seedBlacklist` / `soilBlacklist`: seeds and soils that machine refuses.

**Entropic only**

*   `seedsPerSlot` / `soilsPerSlot`: items each grid slot holds (default 64; capacity = 60 x the smaller one).
*   `energyPerPlot`: FE per active plot per tick (default 90; the FE buffer follows).
*   `replant`: allows the replant button (default on).
*   `crafterRecipes`: recipes the autocrafter holds (default and maximum 100).
*   `crafterBufferLimit`: items of one kind the autocrafter keeps while waiting for the rest of a recipe (default 1024).

**Everything else**

*   Growth Speed Upgrades: `bonusPerUpgrade` (default +50%) and `upgradesPerSlot` (default 1).
*   Hoe: `requireHoe` (default on), `consumeDurability` (default off) and `wearIntervalTicks` (default 1200 = 1 durability per minute).
*   Output: `autoExportIntervalTicks`, ticks between auto-exports (default 20; 0 = never).
*   Drops: `productionMultiplier` (global yield, default 1.0), `secondaryDropMultiplier` (extra seeds and by-products, default 1.0) and `otherPlantYield` (items per plot for flowers, grass and other plants without their own harvest, default 10).
*   Mystical Agriculture: `secondarySeedChanceMultiplier` (default 1.0) and `requiresEffectiveFarmland` (seeds only grow on their tier's farmland, default off).
*   Performance: `maxLootRollsPerHarvest` (default 64) and `maxTreesGrownPerHarvest` (default 4).
*   Global `seedBlacklist` / `soilBlacklist`. Blacklist entries can be an item (`mysticalagriculture:diamond_seeds`), a whole mod (`mysticalagriculture:*`) or an item tag (`#c:seeds`).

Datapacks can also change the tags (universal soils, mushroom and glow berry soils, autocrafter catalysts) and the data maps (soil speed bonuses, fixed yields).

## Links

Source code and bug reports: [https://github.com/redkeepp/virtual-farm-works](https://github.com/redkeepp/virtual-farm-works) (MIT license).
```
