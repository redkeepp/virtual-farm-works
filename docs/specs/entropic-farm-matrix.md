# Entropic Farm Matrix — specification

Source: owner's spec of 2026-09-29 (originally in Portuguese), translated faithfully, plus the owner's answers and the
decisions recorded while building it. Items marked **OPEN** wait for an owner decision; **PROPOSED** are Claude's
proposals not yet confirmed. Anything not listed here works like the Starter Farm Matrix
(`docs/specs/starter-farm-matrix.md`): "the rest you assume equal to the Starter Farm Matrix" (owner).

Why the Entropic comes before Voltaic/Ionic/Resonant (owner): with the weakest and the strongest machine done, the
midgame is easier to balance.

## Owner's rules

- The GUI has two 4x15 grids: the top one for plantables, the bottom one for soils.
- The slots of the two grids pair up: a **plot group** is a seed slot and the soil slot at the same position (a Diamond
  Seed at row 3, column 7 of the top grid uses the soil at row 3, column 7 of the bottom grid). Each plot group holds
  one plantable type and one soil type.
- Each slot takes up to 64 plantables or 64 soils (both editable in the config): 60 plot groups (at most 60 item
  types) and 3,840 plots of capacity. The capacity follows the per-slot amounts automatically: to change the maximum
  capacity, a pack maker changes how many plantables/soils a slot takes.
- Concepts:
  - **Plot**: one plantable + one soil (3,840 capacity).
  - **Plot group**: a plantable slot + its soil slot (60).
  - **Active plots**: plots in use. 35 seeds spread over the grid, each with a valid soil under it = 35 active plots.
  - **Replant** (can be turned off in the config): the machine does not export the plantables it produces; they stay
    inside, looking for a valid soil to plant themselves. Replanting stops by itself when no free soil is left.
  - **Waiting plots**: soil inside the machine that no plot uses (120 seeds on 500 soils, all 120 active = 380
    waiting plots).
- Each active plot consumes 90 FE/t (config).
- FE buffer = maximum capacity (3,840 by default) x FE per active plot (90) x 3, so the buffer always holds at least
  three times the highest possible consumption.
- Hovering the FE bar shows current FE / total FE, e.g. 1000000/1000000.
- Both grids accept automated INPUT (e.g. 3,000 Diamond Seeds piped in from a big chest instead of by hand).
- The output box ("O") has 5 modes per face: NONE (gray, gives and takes nothing), OUTPUT (green, as now: only what
  the seeds produce), OUTPUT CRAFTED (yellow: only what the autocrafter makes), OUTPUT ALL (pink: crafted and
  produced), INPUT (blue).
- Autocrafter (inspiration: RFTools Crafter tier 3):
  - An internal crafting table where the player arranges items and sees the result: purely visual, the machine does
    not use it.
  - Right of it, a dynamic list of every item chosen to be crafted: item icon on the left, name on the right.
  - Crafting is fast; results go to the output buffer.
  - JEI integration: with the crafter open, the player picks an item in JEI, clicks the recipe's "+" and the recipe
    appears in the crafting table with its result; then SET CRAFT.
  - One click on an item name in the list brings its recipe back to the crafting table to be changed; a double click
    deletes the recipe.
  - CRAFT: ON/OFF button: when on, before storing the harvest the machine checks whether each item is used by a
    crafter recipe; if so it goes to the crafter instead of the output buffer.
  - Hovering the buttons shows a gradient.

## GUI (owner texture `textures/gui/entropic_farm_matrix_gui.png`, 512x512 file)

Drawn area 296x320 (the owner quoted 297x320; the texture's column 296 is transparent). Inclusive pixel coordinates:

| Area | Coordinates |
|---|---|
| Plantable grid, 4x15 | (14,20)-(281,89) (item at x 14 + 18 c, y 20 + 18 r) |
| Soil grid, 4x15 | (14,95)-(281,164) |
| Info text area | (18,173)-(206,219) |
| Water Provider slot | (209,170)-(224,185) |
| Progress bar | (19,224)-(147,228) |
| FE bar | (153,224)-(220,228): pixel columns alternate red / dark red; the bar falls gradually when power is cut, never at once |
| Player inventory | (35,241)-(194,292) |
| Hotbar | (35,299)-(194,314) |
| Output buffer, 3x8 | (230,170)-(281,311) |

Crafter panel (owner texture `textures/gui/crafter_farm_matrix_gui.png`, 197x101 drawn in a 256x128 file), opened
OVER the machine GUI by a new side-column button with a crafting table icon, placed between the harvest filter
button and the ON/OFF button (owner, 2026-09-29):

| Area | Coordinates |
|---|---|
| Crafting grid 3x3 | (7,7)-(58,58) |
| Result | (64,25)-(79,40) |
| SET CRAFT button | (7,67)-(58,75) |
| CRAFT: ON/OFF button | (7,84)-(58,92) |
| Recipe list | (85,7)-(189,93) |

## Decisions (owner answers of 2026-09-29, and Claude's recommendations the owner accepted)

- Plot groups with a problem (owner): "the machine only runs when all soils are valid; if one is not valid, it does
  not run". Every group holding a plantable must be able to grow: one invalid soil, a plantable without its soil or a
  missing crux stops the whole machine with that problem as its status, and the bar freezes (every group keeps its
  plots, as on the Starter). A soil alone is not a problem (waiting plots). Each group's own problem still shows on
  its seed slot (red tint, tooltip).
- Soil speed bonus with many soils and one bar (Claude; owner: "it can be"): the plot-weighted average of the groups'
  soil bonuses (all Supremium Farmland = +35%, half of it = +17.5%).
- Hoe (owner): "there is no hoe here; it does not matter if the plantable only grows on tilled soil, in this machine
  the hoe is unnecessary, ignore it completely". A plant that needs farmland grows on tillable soil (dirt, grass...)
  without a hoe; no hoe slot.
- Replant (owner, option (b)): "the seed simply looks for a soil that works for it; if none works or none is free, it
  is output". A produced plantable fills the free soil of the groups already holding it, then empty seed slots above a
  soil it grows on (it never starts a group that could not grow at once). Each machine has a replant button (owner:
  wheat seeds on green ON / red OFF, like ON/OFF); with `machines.entropic.replant = false` in the config the button is
  grey, DISABLED.
- Crafter ingredients (owner: "only from the harvest"), with chains (a result that is an ingredient of another recipe
  goes on crafting, e.g. essence tiers). Exception (owner): the catalyst slot below the result holds the Master
  Infusion Crystal, which recipes use without spending it (see "Autocrafter details").
- Crafter panel (owner): a modal over the machine GUI, opened by the crafting-table button between the filter and
  ON/OFF.
- Recipes (owner): up to 100 (config `crafterRecipes`, default 100), the list growing only as recipes are added.
- GUI size: kept as drawn (296x320) for now (owner: "let's test it like this and you change it later"); at the
  automatic GUI scale on a 1080p screen the machine needs GUI scale 3.
- Owner's changes after the first in-game test (2026-09-29): SET CRAFT and CRAFT: ON/OFF in smaller letters; the FE
  bar's tooltip only "current/total" (a source keeping up shows the buffer full); a lightning box right below the "O"
  (not a button) whose tooltip says "Uses X FE/t per active plot" and "Using X FE/t" (0 while the machine does not
  grow); an INPUT face also pulls from an inventory glued to it (a chest of dirt feeds the machine, like the output
  pushes); the five info lines one pixel lower.
- Assumptions announced to the owner (not objected to): same cycle as the Starter (30 s), production x1, Water
  Provider, 4 Growth Speed Upgrades and Crux Provider in the side column, Fertilized Essence switch, harvest filter,
  on/off; theme color red (the texture's border); capacity = 60 x min(seeds per slot, soils per slot); every planted
  plot pays FE, only while the bar advances; not enough FE for a tick = MISSING FE; FE from any face; 5 info lines
  (status, hydration, active plots / capacity, waiting plots, growth); faces cycle NONE -> OUTPUT -> OUTPUT CRAFTED ->
  OUTPUT ALL -> INPUT with a click (right click goes back), default OUTPUT ALL; pipe input fills slots already holding
  the item, then empty slots whose other half matches, then any empty slot; 72 hidden output slots (3 per visible
  slot, like the Starter's 27 for 9); harvest order replant -> crafter (CRAFT ON) -> filter -> output, the filter never
  touches crafted items; replanted seeds grow from the next cycle; crafter: crafting-table recipes only (vanilla and
  modded), crafts as soon as ingredients arrive, no FE cost, recipe remainders (empty buckets) go to the output, at
  most 8 recipes (config), incomplete ingredients wait hidden (back to the output when CRAFT turns off, deleted when
  the machine breaks); one click loads a recipe into the table, SET CRAFT replaces the selected recipe or adds a new
  one, double click deletes; groups with the same seed and soil are harvested together and a big harvest spreads over
  a few ticks; no machine recipe until the owner defines one. (The "at most 8 recipes" of this list became 100, owner.)
- "Crafted" items for OUTPUT / OUTPUT CRAFTED (Claude): an output item counts as crafted when it is the result of one
  of the machine's crafter recipes.

### Autocrafter details (Claude, 2026-09-29, while building stage 4; the owner tested them in game)

- Catalyst slot (owner): below and glued to the result slot, accepting only what
  `#virtualfarmworks:crafter_catalysts` lists (Mystical Agriculture's Master Infusion Crystal), so Prudentium,
  Tertium... essences craft from harvested Inferium. A cell no harvested item fits takes the catalyst; a craft that
  would not give it back whole never happens, so it is never spent, worn or output. It is a real item of the machine
  (drops when the machine breaks); the slot sits where the Starter keeps its hoe, so saves stay compatible.

- CRAFT is ON by default: a recipe set with SET CRAFT works at once (the switch sits right under SET CRAFT).
- The grid decides WHICH recipe; each cell then accepts any item that recipe accepts there, like a real crafting table
  (sticks set with oak planks also take birch planks). So the item JEI happens to show in a cycling slot does not
  matter.
- Chains are automatic: the machine orders the recipes so that a recipe comes after the ones whose results it uses,
  whatever the list order. A result stays inside only for a recipe further down the chain; recipes that feed each
  other in a circle (ingots -> block -> ingots) never chain into each other, their results go to the output, so
  nothing can go round forever.
- The waiting limit (`crafterBufferLimit`, 1,024 per item) applies to what is left waiting after crafting; 0 = nothing
  waits. Anything beyond it goes to the output.
- Special recipes are refused (map and book cloning, fireworks, dyed armor...): their result depends on the exact
  items put in, which a stored recipe cannot follow. JEI's "+" shows an error on them.
- Recipe remainders (empty buckets, glass bottles) go to the output and count as produced, not crafted, for the face
  modes.
- The crafting grid belongs to each player viewing the machine and is not saved ("purely visual", owner). SET CRAFT
  saves the grid as a recipe (replacing the selected one), then clears the grid and the selection. A click on empty
  list space clears the selection. A double click deletes only when both clicks hit the same recipe.
- The panel is a modal: while it is open, the two grids behind it are dimmed and cannot be clicked. JEI's "+" opens it
  when it is closed; items can also be dragged from JEI onto the grid's cells.
- A recipe that disappears after a datapack reload stays in the list as "Recipe no longer exists" (crafts nothing; a
  double click deletes it). A config limit lowered below the recipes a machine holds keeps them working; SET CRAFT can
  then only replace.
- Items leaving the crafter (CRAFT OFF, a recipe edited or deleted) go to the output before anything else; harvested
  items the harvest filter rejects are deleted, as they would have been without the crafter (crafted items never
  are).
- The list shows five recipes at a time; the mouse wheel (or a click on the scrollbar) scrolls it.

## Implementation status

- Stage 1 (done): multi-group machine (`machine/MachineLayout`), energy (`machine/MachineEnergy`), face modes
  (`machine/FaceMode`), pipe input (`machine/GridInput`), registration, config section `machines.entropic`.
- Stage 2 (done): Entropic GUI (`menu/EntropicFarmMatrixMenu`, `menu/EntropicLayout`,
  `client/EntropicFarmMatrixScreen`); shared menu logic moved to `menu/AbstractFarmMatrixMenu`. The 120 grid slots
  show no 40% placeholders (a grid of faded seeds would hide what is planted; the other slots have them).
- Stage 3 (done): replant (`FarmMatrixBlockEntity#planReplant`), before the harvest filter; replanted seeds are
  added while the harvest is still due, so they grow from the next cycle.
- Stage 4 (done): autocrafter (`machine/MachineCrafter`, harvest order replant -> crafter -> filter -> output in
  `FarmMatrixBlockEntity#harvestNextBatch`), its panel in the GUI (modal over the grids, `EntropicFarmMatrixMenu` crafter
  slots and buttons), JEI "+" and drag-and-drop (`client/compat/VfwJeiPlugin`, `network/SetCrafterGridPayload`), game
  tests `CrafterGameTests`.
- Stage 5 (done): six Entropic rows in `gametest/LoadBenchmark`; results in `docs/history.md` (first run with games
  open, then 3 clean runs). Growing with 3,840 plots costs 0.036 us per tick (the plot count does not matter); a busy
  3,840-plot farm, harvests included, costs about a quarter of the 60 equivalent Starters.
- Owner's answers and first in-game test (2026-09-29, done): all groups must be valid, no hoe, replant option (b)
  with a button per machine, up to 100 recipes sent as a dynamic list (`network/CrafterRecipesPayload`), catalyst slot,
  FE shown full while a source keeps up, lightning box, INPUT faces pull from glued inventories, smaller button text,
  info lines one pixel lower. Game tests `entropic_groups_must_all_be_valid`, `entropic_input_face_pulls_from_chests`,
  `crafter_uses_the_catalyst`; the replant test covers option (b), the switch and the config.
- Waiting for the owner: in-game test of these changes and the machine's crafting recipe.
