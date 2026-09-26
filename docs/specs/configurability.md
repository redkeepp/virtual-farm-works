# Pack-maker configurability — specification

Goal: large modpacks (e.g. ATM 11) must be able to rebalance VFW EASILY, without touching Java. **Nothing below may be
hard-coded.** Source: owner's spec, translated. Mechanism per option is PROPOSED unless stated otherwise.

| Requirement | Proposed mechanism |
|---|---|
| Base growth time, per machine tier (e.g. default 2 min, pack sets any value) | Server config TOML, one value per tier |
| Whether the hoe loses durability (only for items that have durability; unbreakable/FE hoes never wear) | Server config (default: no wear) |
| Disable the hoe requirement entirely (`requireHoe = true/false`) | Server config (default: true) |
| Blacklist seeds (e.g. block Diamond Seeds everywhere). Default: everything allowed; lists say what is NOT allowed | Config lists + item tags |
| Blacklist soils, same semantics | Config lists + item tags |
| Blacklist by namespace/mod (`modid:*`), for seeds and soils | Config list patterns |
| Global AND per-tier blacklists (e.g. Diamond Seed only in Resonant/Entropic) | Per-tier config lists / per-tier tags |
| Seed drop rate for crops on Mystical Agriculture farmlands (0% allowed) | Server config |
| Multiplier without Water Provider (default 0.25x; 1.0x makes it optional), per tier | Server config per tier |
| Auto-output interval (5/10/20/40 ticks) or disable auto-push completely | Server config |
| Secondary drops multiplier (extra seeds, Fertilized Essence, other byproducts); 1.0 default, 0 disables | Server config |
| Machine and upgrade recipes 100% datapack-driven | Recipes only as JSON under `data/virtualfarmworks/recipe/` |
| Disable whole tiers without unregistering blocks (save-safe) | Removing recipes via datapack; optional config flag if needed |
| Global or per-tier production (yield) multiplier, default 1.0, independent from speed | Server config |
| Growth Speed Upgrade bonus (default +50% each) | Server config |
| Growth Speed Upgrade max stack per upgrade slot (default 1 per slot x 4 slots; e.g. 3 per slot = 12) | Server config |

Notes:
- Use SERVER config (per world, synced to clients) for gameplay values so multiplayer clients show the same numbers.
- Blocks/items are always registered; disabling a tier must never delete blocks from existing saves.
