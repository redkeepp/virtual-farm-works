# Starter Farm Matrix — specification

Source: owner's spec (originally in Portuguese), translated faithfully. Items marked **OPEN** are waiting for an owner
decision; items marked **PROPOSED** are Claude's proposals not yet confirmed. Update this file when decisions land.

Theme color: `#FFFFFF`.

## GUI texture and coordinates

Texture: `assets/virtualfarmworks/textures/gui/starter_farm_matrix_gui.png` (256x256 image, real GUI area is
`(0,0)–(175,218)`, i.e. 176x219). Ignore `starter_farm_matrix_gui2.png`. The PNG has **no text**; every text is drawn
by code. Coordinates below are inclusive pixel ranges inside the texture (a 16x16 slot `(25,26)–(40,41)` means the
item renders at x=25, y=26).

| # | Area | Purpose |
|---|------|---------|
| 1 | `(0,0)–(175,218)` | Whole GUI background |
| 2 | `(25,26)–(40,41)` | Seed slot |
| 3 | `(61,26)–(76,41)` | Soil slot |
| 4 | `(98,26)–(113,41)` | Water Provider Upgrade slot |
| 5 | `(134,26)–(149,41)` | Hoe slot |
| 6 | `(12,94)–(163,98)` | Progress bar (green), shows 1%..100% |
| 7 | `(8,111)–(167,126)` | 9 output buffer slots (one row, hotbar-like) |
| 8 | `(8,140)–(167,191)` | Player inventory (27 slots) |
| 9 | `(8,198)–(167,213)` | Player hotbar |
| 10 | `(13,57)–(164,89)` | Info panel (dynamic text, see below) |

Title "STARTER FARM MATRIX" at the top, centered, in the theme color.

### Slot rules

**Ghost placeholders.** Every empty machine slot renders a placeholder item at 40% opacity; it disappears as soon as a
real item is inserted. Placeholders: seed slot = Wheat Seeds; soil slot = vanilla Farmland; water slot = Starter
Water Provider Upgrade; hoe slot = Stone Hoe; growth slots = Starter Growth Speed Upgrade; crux slot = Crux Provider
Upgrade. Placeholders are client-only rendering, never real items.

**Seed slot (2).** Only plantables (any vanilla/modded seed or plantable, *except flowers*). Up to 64, all the same
item.

DECIDED — vanilla plantables:
- ALLOWED: wheat seeds, carrot, potato, beetroot seeds; melon seeds, pumpkin seeds (produce the melon/pumpkin block);
  sugar cane, cactus, bamboo; cocoa beans (jungle log); nether wart (soul sand); sweet berries, glow berries;
  brown/red mushroom; chorus flower.
- NOT ALLOWED: torchflower seeds, pitcher pod; crimson/warped fungus; kelp, sea pickle (maybe later); saplings,
  azalea, mangrove propagule (trees); vines (vine, twisting, weeping); decorative plants (grass, fern, bush, firefly
  bush, dripleaf, leaf litter, moss); all flowers.
- Modded crops (Mystical Agriculture, Mystical Agradditions, others) are accepted generically when the compatibility
  check passes; pack makers restrict them with blacklists.

**Soil slot (3).** Only soils a plant can grow on (any vanilla/modded farmland/soil that actually grows crops; a grass
block that grows nothing is not accepted). Also soul sand, jungle log (cocoa), sand (sugar cane), etc. Up to 64, all
the same item. The machine must detect whether the soil carries a growth accelerator (e.g. Mystical Agriculture
farmlands in the owner's pack give extra growth speed).

**Seed ↔ soil compatibility.** Slots 2 and 3 must agree: sugar cane only works on dirt/sand, wheat only on
dirt/farmland, etc. The machine only runs with a valid pair.

**Water Provider slot (4).** Only Water Provider Upgrades (any tier). Max 1. Not required to run: without it the
machine runs at 0.25x; with it at 1.0x. (1.0x is the designed baseline — the upgrade removes a penalty, it does not
add speed.)

DECIDED — upgrade tiers (Water Provider AND Growth Speed) are purely a COMPATIBILITY gate, never a quality level.
An upgrade fits a machine when `upgradeTier >= machineTier` (tier order: Starter < Voltaic < Ionic < Resonant <
Entropic). Examples: Ionic upgrade fits Starter/Voltaic/Ionic but not Resonant/Entropic; Entropic fits everything;
Starter fits only Starter. All tiers give exactly the same effect.

**Hoe slot (5).** Only hoes (vanilla or any mod). Level does not matter (wood hoe == endgame hoe). Accepts damaged
hoes and FE hoes with any charge. By default the hoe does NOT lose durability and does NOT consume FE (configurable,
see configurability spec). The hoe is required ONLY when the seed needs tilled soil AND the soil item is not already
tilled. Examples: wheat + Supremium Farmland -> no hoe; wheat + dirt -> hoe required; sugar cane + dirt -> no hoe.

**Side panel (drawn by code, outside the PNG, left side).**
- An "O" (Output) button at the top-left, vertically centered on the seed slot row. Clicking opens a small box with the
  6 sides of the machine to toggle auto-output per side. Layout requested by owner:
  ```
  X     Top     X
  Left  Front   Right
  Back  Bottom  X
  ```
  DECIDED: exactly the owner's layout (NOT Mekanism's). The owner's legend listed "B = back" then "B = bottom", so the
  last row is read as Back, Bottom, empty. Each button shows its side name/tooltip, so a swap is a one-line change.
- Below the "O", 5 vertical upgrade slots, 1 item each: slots 1–4 = Growth Speed Upgrade (any tier), slot 5 = Crux
  Provider Upgrade. Same placeholder behavior.
- The "O" box (open and closed) and the 5 upgrade slots get a 1-pixel OUTER border in the theme color.
- Claude designs only this side panel; the central art is the owner's PNG and must not be redesigned.

### Info panel text (area 10)

```
<STATE>
Hydration: (0.25x speed) | (1.0x speed)
Seeds: <current>/64
Growth: <X>% - (<speed>x speed)     speed = 1.0x + growth upgrades (+50% each by default)
```

### States (priority order)

- `RUNNING` — green.
- Missing-input hierarchy, first missing one wins, all orange:
  `MISSING SEED` > `MISSING SOIL` > `MISSING HOE` (seed needs tilled soil) > `MISSING CRUX` (seed needs a crux) >
  `MISSING FE` (machine needs FE; never for Starter).
- `OUTPUT FULL` — red, output buffer cannot take the harvest.
- `SHUTDOWN` — red, player turned the machine off with the on/off button.
- DECIDED: `INVALID SOIL` (orange) for an incompatible seed/soil pair, placed right after `MISSING SOIL`.
  Final order: MISSING SEED > MISSING SOIL > INVALID SOIL > MISSING HOE > MISSING CRUX > MISSING FE.

## Machine behavior

- Starter does NOT use FE.
- One global progress bar; at 100% every ACTIVE plot is harvested at once (see CLAUDE.md for ACTIVE/PENDING).
- Up to 4 Growth Speed Upgrades (any tier); each ADDS +50% growth speed (configurable amount and per-slot stack size).
- Up to 1 Crux Provider Upgrade (single item, no tiers). Generic crux: satisfies ANY crux requirement of any seed
  (Mystical Agriculture crux blocks).
- On/off button.
- Auto-export ON by default on all 6 faces; player can disable per face. Pushes output buffer items into adjacent
  inventories (e.g. a chest).
- Output buffer (9 slots): output only. Players/automation can extract, nothing can be inserted.

## DECIDED numbers (defaults; all configurable)

- **Base cycle time (Starter): 30 seconds (600 ticks)** at 1.0x = Water Provider installed, no growth upgrades, no
  accelerating soil. The Water Provider is part of the "pure" machine.
- **Plot count** = `min(seedCount, soilCount)`. Surplus seeds or soils are simply idle.
- **Speed** = `hydrationFactor x (1 + growthBonus x upgradeCount) x soilMultiplier` (multiplicative, owner OK "for now").
- **Soil growth bonuses** (soilMultiplier = 1 + bonus): Inferium Farmland +15%, Prudentium +20%, Tertium +25%,
  Imperium +30%, Supremium +35% (Mystical Agriculture), Insanium Farmland +40% (Mystical Agradditions — must be
  supported too). Shipped as datapack-editable defaults, never hard-coded.
- **Output full**: machine stops completely; drops already in the buffer stay there; no partial harvest, nothing voided.

## Implementation decisions

- **Seed/soil compatibility** (implemented, step 3): computed generically in a tiny in-memory block view (no world
  access): the soil's NeoForge `canSustainPlant` hook first, then the plant's own `mayPlaceOn` (26.1 `#supports_*`
  tags for vanilla, overrides for modded crops). Light/water/neighbours are ignored (ideal virtual conditions).
  Mushrooms and glow berries use VFW tags because their vanilla rules accept almost any block. Only computed when a
  slot/config/tags change.
- **Hoe need** (implemented): plant cannot grow on the soil, the soil is in `#virtualfarmworks:tillable_soils`, and the
  plant can grow on farmland -> hoe required (unless `hoe.requireHoe = false`).
- **Blacklisted item already in a machine** (config changed later): stays in its slot (player can take it back) but is
  treated as missing (`MISSING SEED` / `MISSING SOIL`).
- **Soil growth multipliers** (mechanism): NeoForge data map on items (`virtualfarmworks:soil_properties`, e.g.
  `growth_bonus`), shipped defaults for the MA/Agradditions farmlands above; optional entries so VFW does not
  hard-depend on those mods.
- **Output full** (mechanism): compute the whole cycle's drops, simulate insertion into the buffer; if everything fits,
  commit; otherwise hold at 100% with `OUTPUT FULL` and retry only when the buffer changes (event-driven).
- **Removing seeds**: PENDING plots are removed first, then ACTIVE.
- **Hoe wear (when enabled by config)**: 1 durability per harvest cycle, not per plot.
