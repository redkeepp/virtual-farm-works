/*
 * FarmMatrixLayout — every position, size and color of the Farm Matrix GUI in one place, shared by the menu (slot
 * positions, both sides) and the screen (drawing, client). Coordinates are relative to the GUI's top-left corner.
 */
package com.virtualfarmworks.menu;

import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.RelativeSide;

/**
 * GUI geometry. The central part comes from the owner's texture
 * ({@code textures/gui/starter_farm_matrix_gui.png}, 176x219 used out of 256x256); its coordinates are the owner's
 * (see {@code docs/specs/starter-farm-matrix.md}, GUI table). The side panel on the LEFT of the texture (auto-output
 * button, face box, 5 upgrade slots, Fertilized Essence button, power button) is drawn by code and its geometry is
 * defined here.
 *
 * <p>Slot positions are the top-left pixel of the 16x16 item, as Minecraft expects.
 */
public final class FarmMatrixLayout {
    // --- owner's texture ------------------------------------------------------------------------------------------
    // Revision 2 of the texture (owner, step 7): 3 px taller than the first one, to give the info lines more room.
    // Every value below was checked against the texture's pixels (slot interiors, 1 px #34586B frames).
    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 222; // (0,0)-(175,221)
    public static final int TEXTURE_SIZE = 256;

    /** Seed, soil, water provider, hoe: owner coordinates (25,26), (61,26), (98,26), (134,26). */
    public static final int[] TOP_SLOT_X = {25, 61, 98, 134};
    public static final int TOP_SLOT_Y = 26;

    /** Output buffer row: (8,114)-(167,129), 9 slots, 18 px apart. */
    public static final int OUTPUT_X = 8;
    public static final int OUTPUT_Y = 114;
    public static final int SLOT_SPACING = 18;

    /** Player inventory (8,143)-(167,194) and hotbar (8,201)-(167,216): vanilla's standard 58 px apart. */
    public static final int PLAYER_INVENTORY_X = 8;
    public static final int PLAYER_INVENTORY_Y = 143;

    /** Progress bar interior (12,97)-(163,101): 152 x 5 px. */
    public static final int BAR_X = 12;
    public static final int BAR_Y = 97;
    public static final int BAR_WIDTH = 152;
    public static final int BAR_HEIGHT = 5;

    /**
     * Info lines inside the info panel (frame (7,53)-(168,106), interior x 8..167). Owner-tuned positions: x = 11,
     * and one y per line — status 58, hydration 67, seeds 76, growth 86 (uneven on purpose, owner's choice).
     * {@link #INFO_WIDTH} is the room up to x 166; longer texts (translations) are scaled down to fit.
     */
    public static final int INFO_X = 11;
    /** Owner-tuned: status 58, hydration 67, seeds 77, growth 86. */
    public static final int[] INFO_LINE_Y = {58, 67, 77, 86};
    public static final int INFO_WIDTH = 156;

    /** Owner-tuned (moved up 1 px after the second in-game review). */
    public static final int TITLE_Y = 8;

    // --- colors (sampled from the owner's texture) ------------------------------------------------------------------
    /** Background / slot interior of the owner's texture. */
    public static final int COLOR_BACKGROUND = 0xFF0F1A26;
    /** Slot and panel frame color of the owner's texture. */
    public static final int COLOR_FRAME = 0xFF34586B;
    /** Info text other than the status line. */
    public static final int COLOR_TEXT = 0xFFD7E3EC;
    public static final int COLOR_BAR = 0xFF3CCB5A;
    public static final int COLOR_ON = 0xFF2E8B57;
    public static final int COLOR_OFF = 0xFF8B2E2E;
    /** Fertilized Essence button (owner spec): light pink = generated, dark pink = not generated. */
    public static final int COLOR_FERTILIZED_ON = 0xFFF48FB1;
    public static final int COLOR_FERTILIZED_OFF = 0xFF880E4F;
    public static final int COLOR_BUTTON_ACTIVE = 0xFF1C3144;
    /**
     * Ghost placeholders: the item is drawn, then covered by the slot background at 60% opacity, so it shows at
     * 40% (owner spec) — the same technique vanilla uses for recipe-book ghost items.
     */
    public static final int COLOR_GHOST_COVER = 0x990F1A26;

    // --- side panel (drawn by code) ---------------------------------------------------------------------------------
    // One column glued to the LEFT edge of the texture (owner's revision, step 7). Pixel columns, x relative to the
    // GUI origin (the texture's own white border is at x = 0):
    //   x = -19        theme-color (white) border, left side only
    //   x = -18        blue frame line (COLOR_FRAME)
    //   x = -17..-2    16 px interior (item / label)
    //   x = -1         blue frame line
    //   x =  0         the texture's white border, which is also the column's right border — so the column has NO
    //                  theme border on its right side and only one white line shows where they touch.
    // Four boxes from top to bottom, 3 px apart: the "O" button (1 cell), the upgrade block (5 cells separated by a
    // single blue line, no white lines between them), the Fertilized Essence ON/OFF button (1 cell) and the machine
    // ON/OFF button (1 cell). Each box: white top line, blue line, cells, blue line, white bottom line.
    public static final int PANEL_BORDER_X = -19;
    public static final int PANEL_INTERIOR_X = -17;
    public static final int CELL = 16;
    /** Distance between two stacked cells of the upgrade block: 16 px interior + 1 shared blue line. */
    public static final int CELL_PITCH = CELL + 1;
    /** Empty rows between two boxes (owner spec: 3 px everywhere, including between the two ON/OFF buttons). */
    public static final int PANEL_BOX_GAP = 3;
    public static final int UPGRADE_SLOTS = MachineSlots.GROWTH_COUNT + 1; // 4 growth + 1 crux

    /** Top (white line) of the "O" box; its interior (y 26) lines up with the texture's top slot row (owner spec). */
    public static final int OUTPUT_BOX_TOP = 24;
    public static final int UPGRADE_BOX_TOP = OUTPUT_BOX_TOP + boxHeight(1) + PANEL_BOX_GAP;       // 47
    /** Fertilized Essence ON/OFF box: where the machine ON/OFF box used to be (owner spec). */
    public static final int FERTILIZED_BOX_TOP = UPGRADE_BOX_TOP + boxHeight(UPGRADE_SLOTS) + PANEL_BOX_GAP; // 138
    public static final int POWER_BOX_TOP = FERTILIZED_BOX_TOP + boxHeight(1) + PANEL_BOX_GAP;     // 161
    public static final int PANEL_BOTTOM = POWER_BOX_TOP + boxHeight(1);                           // 181 (exclusive)

    // Face box, opened by the "O" button, left of the column (3 px gap). 3x3 grid of 16 px cells, 2 px apart, 3 px
    // padding, inside a blue frame and a theme-color border. FACE_BOX_X/Y are the inner (blue frame) top-left corner.
    public static final int FACE_CELL = 16;
    public static final int FACE_GAP = 2;
    public static final int FACE_PADDING = 3;
    public static final int FACE_BOX_SIZE = 3 * FACE_CELL + 2 * FACE_GAP + 2 * FACE_PADDING;      // 58
    public static final int FACE_BOX_X = PANEL_BORDER_X - PANEL_BOX_GAP - 1 - FACE_BOX_SIZE;      // -81
    public static final int FACE_BOX_Y = OUTPUT_BOX_TOP + 1;                                      // 25

    /**
     * Owner's face layout ({@code null} = empty cell):
     * <pre>
     *   X     Top     X
     *   Left  Front   Right
     *   Back  Bottom  X
     * </pre>
     */
    public static final RelativeSide[][] FACE_GRID = {
            {null, RelativeSide.TOP, null},
            {RelativeSide.LEFT, RelativeSide.FRONT, RelativeSide.RIGHT},
            {RelativeSide.BACK, RelativeSide.BOTTOM, null},
    };

    private FarmMatrixLayout() {
    }

    /**
     * Height of a side-panel box with {@code cells} stacked cells: white + blue line on top, the cells with one shared
     * blue line between two cells, blue + white line at the bottom.
     */
    public static int boxHeight(int cells) {
        return 2 + cells * CELL + (cells - 1) + 2;
    }

    /** Interior (item) y of cell {@code i} of a box whose white top line is at {@code boxTop}. */
    public static int cellY(int boxTop, int i) {
        return boxTop + 2 + i * CELL_PITCH;
    }

    /** Item y of upgrade slot {@code i} (0..3 growth, 4 crux). */
    public static int upgradeSlotY(int i) {
        return cellY(UPGRADE_BOX_TOP, i);
    }

    /** Top-left of the face cell at grid (row, col). */
    public static int faceCellX(int col) {
        return FACE_BOX_X + FACE_PADDING + col * (FACE_CELL + FACE_GAP);
    }

    public static int faceCellY(int row) {
        return FACE_BOX_Y + FACE_PADDING + row * (FACE_CELL + FACE_GAP);
    }
}
