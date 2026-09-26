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
 * button, face box, 5 upgrade slots, power button) is drawn by code and its geometry is defined here.
 *
 * <p>Slot positions are the top-left pixel of the 16x16 item, as Minecraft expects.
 */
public final class FarmMatrixLayout {
    // --- owner's texture ------------------------------------------------------------------------------------------
    public static final int GUI_WIDTH = 176;
    public static final int GUI_HEIGHT = 219;
    public static final int TEXTURE_SIZE = 256;

    /** Seed, soil, water provider, hoe: owner coordinates (25,26), (61,26), (98,26), (134,26). */
    public static final int[] TOP_SLOT_X = {25, 61, 98, 134};
    public static final int TOP_SLOT_Y = 26;

    /** Output buffer row: (8,111)-(167,126), 9 slots, 18 px apart. */
    public static final int OUTPUT_X = 8;
    public static final int OUTPUT_Y = 111;
    public static final int SLOT_SPACING = 18;

    /** Player inventory (8,140)-(167,191) and hotbar (8,198)-(167,213): vanilla's standard 58 px apart. */
    public static final int PLAYER_INVENTORY_X = 8;
    public static final int PLAYER_INVENTORY_Y = 140;

    /** Progress bar (12,94)-(163,98): 152 x 5 px. */
    public static final int BAR_X = 12;
    public static final int BAR_Y = 94;
    public static final int BAR_WIDTH = 152;
    public static final int BAR_HEIGHT = 5;

    /** Info panel (13,57)-(164,89). Text starts with a small padding; 4 lines, 8 px apart (fits the 33 px). */
    public static final int INFO_X = 16;
    public static final int INFO_Y = 58;
    public static final int INFO_WIDTH = 146;
    public static final int INFO_LINE_HEIGHT = 8;

    public static final int TITLE_Y = 9;

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
    public static final int COLOR_BUTTON_ACTIVE = 0xFF1C3144;
    /**
     * Ghost placeholders: the item is drawn, then covered by the slot background at 60% opacity, so it shows at
     * 40% (owner spec) — the same technique vanilla uses for recipe-book ghost items.
     */
    public static final int COLOR_GHOST_COVER = 0x990F1A26;

    // --- side panel (drawn by code) ---------------------------------------------------------------------------------
    // One column left of the texture. Every element is an 18x18 frame (like the texture's slots) with a 1 px outer
    // border in the machine's theme color (owner spec), i.e. 20x20 on screen, 4 px away from the texture.
    public static final int FRAME_SIZE = 18;
    /** Left edge of the column's 18x18 frames. */
    public static final int PANEL_FRAME_X = -23;
    /** Auto-output ("O") button frame, vertically centered on the top slot row (owner spec). */
    public static final int OUTPUT_BUTTON_Y = 25;
    /** First upgrade slot frame; the 5 frames are {@link #PANEL_STEP} apart (18 frame + 2 border + 2 gap). */
    public static final int FIRST_UPGRADE_FRAME_Y = 47;
    public static final int PANEL_STEP = 22;
    public static final int UPGRADE_SLOTS = MachineSlots.GROWTH_COUNT + 1; // 4 growth + 1 crux
    /** On/off button frame, below the upgrade slots. */
    public static final int POWER_BUTTON_Y = FIRST_UPGRADE_FRAME_Y + UPGRADE_SLOTS * PANEL_STEP;

    // Face box, opened by the "O" button, left of the column. 3x3 grid of 16 px cells, 2 px apart, 3 px padding.
    public static final int FACE_CELL = 16;
    public static final int FACE_GAP = 2;
    public static final int FACE_PADDING = 3;
    public static final int FACE_BOX_SIZE = 3 * FACE_CELL + 2 * FACE_GAP + 2 * FACE_PADDING; // 58
    public static final int FACE_BOX_X = PANEL_FRAME_X - 1 - 3 - FACE_BOX_SIZE;                // -85
    public static final int FACE_BOX_Y = OUTPUT_BUTTON_Y - 1;

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

    /** Item x of the side column's slots (frame + 1). */
    public static int panelSlotX() {
        return PANEL_FRAME_X + 1;
    }

    /** Frame y of upgrade slot {@code i} (0..3 growth, 4 crux). */
    public static int upgradeFrameY(int i) {
        return FIRST_UPGRADE_FRAME_Y + i * PANEL_STEP;
    }

    /** Top-left of the face cell at grid (row, col). */
    public static int faceCellX(int col) {
        return FACE_BOX_X + FACE_PADDING + col * (FACE_CELL + FACE_GAP);
    }

    public static int faceCellY(int row) {
        return FACE_BOX_Y + FACE_PADDING + row * (FACE_CELL + FACE_GAP);
    }
}
