# Changelog

All notable changes to Virtual Farm Works. Versions follow [Semantic Versioning](https://semver.org/); release files
are named `virtualfarmworks-<Minecraft version>-<mod version>.jar`.

## [1.0.0] - 2026-09-30

First public release, for Minecraft 26.1.2 and NeoForge 26.1.2.109 or newer.

### Added

- **Starter Farm Matrix**: one plant type, up to 64 plots; Water Provider, four Growth Speed Upgrades, Crux Provider
  and hoe slots; 9 output slots plus 27 hidden overflow slots; auto-export per face; harvest filter (whitelist or
  blacklist); Fertilized Essence switch. No power needed.
- **Entropic Farm Matrix**: 60 plant groups, up to 3,840 plots in one block; powered by FE (90 per active plot per
  tick; with too little FE it runs slower, RUNNING WITH LOW FE); five modes per face (NONE, OUTPUT, OUTPUT CRAFTED,
  OUTPUT ALL, INPUT) with pipe input and pulling from adjacent inventories; replanting; 24 output slots plus 72 hidden
  overflow slots; a GUI that lowers the GUI scale while open when it would not fit the screen.
- **Autocrafter** in the Entropic Farm Matrix: up to 100 crafting-table recipes, chains in order, crafts from what the
  output holds, a catalyst slot for Mystical Agriculture's Master Infusion Crystal, JEI's "+" button; the recipes
  stay on the machine item when it is broken.
- **Upgrades**: Water Provider and Growth Speed Upgrades (Starter and Entropic tiers), Crux Provider Upgrade.
- **Plants**: crops, saplings (grown trees are harvested), flowers, grass, mushrooms, nether fungi, aquatic and
  hanging plants, and modded plantables, with soil rules taken from the game itself.
- **Optional integrations**: Mystical Agriculture (essence yields, cruxes, farmland bonuses, Fertilized Essence,
  catalyst), JEI (recipe transfer, drag and drop onto the filter and the crafting grid), Jade (machine tooltip).
- **Configuration**: a server config for growth, yields, FE, limits, replanting, hoe wear, auto-export and blacklists,
  reloaded without a restart; tags and data maps for pack makers.
