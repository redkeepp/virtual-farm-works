# Starter Farm Matrix — specification

Source: owner's spec (originally in Portuguese), translated faithfully. Items marked **OPEN** are waiting for an owner
decision; items marked **PROPOSED** are Claude's proposals not yet confirmed. Update this file when decisions land.

Theme color: `#FFFFFF`.

## GUI texture and coordinates

Texture: `assets/virtualfarmworks/textures/gui/starter_farm_matrix_gui.png` (256x256 image). Revision 2 (owner, step 7)
is 3 px taller than the original: real GUI area `(0,0)–(175,221)`, i.e. 176x222. Ignore `starter_farm_matrix_gui2.png`.
The PNG has **no text**; every text is drawn by code. Coordinates below are inclusive pixel ranges inside the texture
(a 16x16 slot `(25,26)–(40,41)` means the item renders at x=25, y=26). Revision-2 values were verified on the pixels.

| # | Area | Purpose |
|---|------|---------|
| 1 | `(0,0)–(175,221)` | Whole GUI background |
| 2 | `(25,26)–(40,41)` | Seed slot |
| 3 | `(61,26)–(76,41)` | Soil slot |
| 4 | `(98,26)–(113,41)` | Water Provider Upgrade slot |
| 5 | `(134,26)–(149,41)` | Hoe slot |
| 6 | `(12,97)–(163,101)` | Progress bar (green), shows 1%..100% |
| 7 | `(8,114)–(167,129)` | 9 output buffer slots (one row, hotbar-like) |
| 8 | `(8,143)–(167,194)` | Player inventory (27 slots) |
| 9 | `(8,201)–(167,216)` | Player hotbar |
| 10 | frame `(7,53)–(168,106)` | Info panel (dynamic text, see below); text at x=11, lines at y 58 / 67 / 76 / 86 |

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
- IMPLEMENTED priority (step 4, `sim/MachineStatus`): SHUTDOWN (player's explicit choice) > the missing hierarchy
  above > OUTPUT FULL (only matters once the machine could grow) > RUNNING.
- OUTPUT FULL happens when the next part of a due harvest does not fit the output (visible + hidden slots); the bar
  stays at 100% until the whole harvest is stored (see "Output" below).

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
- Hidden output slots (owner, step 8; Starter default 27, config `machines.<tier>.internalBufferSlots`): harvests fill
  the 9 visible slots first, then the hidden ones; the hidden slots refill the visible ones as those empty. Nobody
  sees or reaches them (no GUI, no capability).

## DECIDED numbers (defaults; all configurable)

- **Base cycle time (Starter): 30 seconds (600 ticks)** at 1.0x = Water Provider installed, no growth upgrades, no
  accelerating soil. The Water Provider is part of the "pure" machine.
- **Plot count** = `min(seedCount, soilCount)`. Surplus seeds or soils are simply idle.
- **Speed** = `hydrationFactor x (1 + growthBonus x upgradeCount) x soilMultiplier` (multiplicative, owner OK "for now").
- **Soil growth bonuses** (soilMultiplier = 1 + bonus): Inferium Farmland +15%, Prudentium +20%, Tertium +25%,
  Imperium +30%, Supremium +35% (Mystical Agriculture), Insanium Farmland +40% (Mystical Agradditions — must be
  supported too). Shipped as datapack-editable defaults, never hard-coded.
- **Output full**: machine stops growing; drops already in the buffers stay there; ripe plots wait on the plant; nothing
  voided (step 8 details in "Output" below).

## Harvest (implemented, milestone 1 step 5)

Per ACTIVE plot, a harvest yields what one mature plant drops when harvested by hand. Only the AMOUNT follows the
real plant: the machine does not imitate natural growth times, it is simply faster, with one shared cycle for every
crop (owner decision, step 8: the machine is just faster, no growth-time imitation). Per plot:
- **Crops** (wheat, carrot, potato, beetroot, nether wart, cocoa, modded crops): the mature crop's loot, minus one
  planting item that goes back into the ground (the "replanting cost" — the plot keeps its seed in VFW). E.g. wheat:
  1 wheat + 0..3 extra seeds; carrot: 1..4 carrots; cocoa: 2 beans.
- **Melon / pumpkin**: the fruit block's loot (melon: 3..7 slices like breaking a melon; pumpkin: 1 pumpkin). The
  stem stays, so no seed is consumed or produced.
- **Sweet berries** 2..3, **glow berries** 1, **sugar cane / cactus / bamboo / mushroom** 1, **chorus** 0..1 chorus
  fruit per cycle: the plant stays in place.
- **Mystical Agriculture**: MA's own formula with the soil slot's farmland (essence, extra essence/seed chances,
  Inferium's tier-based essence, Fertilized Essence), minus the replanting seed.
- The hoe never changes yields (no Fortune, tier irrelevant — owner rule).
- Pack-maker multipliers: production (main product) and secondary (extra seeds, by-products), see
  `configurability.md`.
- Storage is all-or-nothing per batch (NeoForge transaction), see "Output" below.

## Output: visible slots, hidden slots, plants (owner design, step 8)

Why: the first version stored a whole harvest all-or-nothing, so a harvest bigger than the whole buffer (high
multipliers, future tiers with thousands of plots) never fit, even in an empty buffer: permanent OUTPUT FULL (owner's
overclock test). The owner rejected a big hidden overflow inventory and chose this middle ground:
1. Harvests fill the 9 visible slots first, then the hidden slots (27 on the Starter); the hidden slots refill the
   visible ones as those empty.
2. When both are full, the ripe plots not harvested yet wait on the plant: their items do not exist yet, the machine
   only counts them. The bar stays at 100% and the next cycle starts only once every plot is harvested, so nothing
   accumulates.
3. A due cycle is harvested in batches sized to the free slots (usually one batch with every plot; several only while
   the output is the bottleneck; at most one per tick). Each batch is rolled once and stored all-or-nothing.
4. Extreme case (owner: "pode segurar"): a batch too big even for EMPTY buffers (one plot yielding more than every
   slot, only with absurd multipliers) is stored as far as it fits; the rest is held with the machine (saved) and
   stored first.
5. Breaking the machine (owner): the inputs and the 9 visible slots drop; the hidden slots, held items and ripe plots
   are deleted.
No GUI or Jade indicator for waiting plots or hidden items (owner: "não, esquece isso").

## Machine behavior details (implemented, milestone 1 step 6)

- **Automation**: pipes/hoppers/other mods can only EXTRACT from the 9-slot output buffer, from any face. Nothing
  can be inserted from outside (seed, soil, upgrades and hoe are placed by the player). Owner: correct for the
  Starter (64 seeds/soils do not need automation); future tiers may allow automated inputs.
- **Auto-export**: every `output.autoExportIntervalTicks` (default 20), the buffer is pushed into adjacent inventories
  on each enabled face. Faces are relative to the machine's front, as the player sees it standing in front of the
  machine: LEFT is the player's left. Keeps running while the machine is SHUTDOWN (it only empties the buffer).
- **Breaking the machine** drops the inputs and the 9 visible output slots; the hidden output slots are deleted
  (owner, step 8).
- **Hoe wear** (config, off by default): over time (owner decision) — 1 durability every `hoe.wearIntervalTicks`
  (default 1200 = 1 minute) of RUNNING, only while the soil needs the hoe.

## GUI (implemented, milestone 1 step 7)

- Right-click the machine to open it. Title, status and info lines are drawn by code (the texture has no text).
- Info lines: status (colored), `Hydration: (Nx speed)`, `Seeds: planted/64` (planted = min(seeds, soils), what
  actually grows), `Growth: X% - (Nx speed)` where the multiplier is Growth Speed Upgrades x soil bonus. The bar
  shows 1%..100% while something is planted.
- Smooth bar (owner, step 8): the server still sends the progress every 5 ticks; the client animates the bar and the
  Growth % from the previous value to the new one over the time between syncs (`client/SmoothProgress`), so 10% -> 14%
  passes through 11, 12 and 13%. It never runs ahead of the server; a restart after a harvest is shown at once.
- Side column (owner revision after the first in-game test): glued to the left edge of the texture. Three boxes,
  3 px apart: the "O" auto-output button, one block with the 4 Growth Speed Upgrade slots + the Crux Provider slot
  (a single blue line between cells, no white lines between them), and the ON/OFF button. Boxes have a theme-color
  (white) border on the left, top and bottom only: the texture's own white border is their right edge, so only one
  white line shows where they touch. Exact pixel columns in `menu/FarmMatrixLayout`.
- The "O" opens the face box (3 px left of the column) in the owner's layout (T / L F R / Bk Bt), green =
  auto-output on, red = off, full side names in tooltips. ON/OFF sits at the bottom of the column (Claude's choice).
- Empty input slots show 40% placeholders; hovering an empty slot tells what it accepts.
- Owner additions (step 8): a **Fertilized Essence** ON/OFF switch in the old ON/OFF position (light pink = Mystical
  Agriculture crops produce Fertilized Essence, dark pink = they do not; no effect without MA; tooltip
  "Drops Fertilized Essence: ON/OFF"), and the machine ON/OFF right below it, 3 px apart like every other box (the
  owner first said 5 px, then corrected it to 3). Title moved up 1 px, "Seeds" line down 1 px; later status,
  hydration and seeds up 1 px again (final y: status 57, hydration 66, seeds 76, growth 86, title 8).
- Owner addition (step 8): right-clicking the machine (GUI closed) while holding a Water Provider, Growth Speed or
  Crux Provider Upgrade pulls in as many as fit; if none fits, the GUI opens instead.
- Recipes (owner): Starter Farm Matrix, Starter Water Provider, Starter Growth Speed, Crux Provider — see
  `docs/resources.md`.

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
- **Output full** (mechanism, step 8): the due cycle is harvested in batches sized to the free slots
  (`sim/HarvestBatching`, measured yield per plot in `sim/YieldSample`); each batch is rolled once and stored in one
  transaction, and its plots count as harvested only after the commit (`GrowthCycle#harvestPlots`, saved as
  "harvested"). A batch that does not fit is kept and retried only when the output or inputs change (event-driven).
  The first version rolled the whole cycle at once and deadlocked when it was bigger than the buffer.
- **Removing seeds**: PENDING plots are removed first, then ACTIVE (mid-harvest: already harvested plots before the
  ripe ones, so the ripe crops stay).
- **Hoe wear (when enabled by config)**: time-based, see "Machine behavior details" (the first idea, 1 durability per
  harvest cycle, was replaced by the owner in step 7).
- **Mystical Agriculture effective farmland** (owner, step 8): VFW has its own switch
  `mysticalagriculture.requiresEffectiveFarmland`, default off, independent from MA's option of the same name. When
  on, an MA seed only grows on its own tier's farmland (a higher-tier farmland does not count, same as MA), except
  Inferium Seeds, and blocks in `#mysticalagriculture:always_effective_farmland` (Awakened Supremium Farmland by
  default) count for every tier. Otherwise the status is `INVALID SOIL`. Tested on the block the plant stands on:
  the soil, or vanilla farmland when a hoe tills it.
