/*
 * EntropicLayout — every position and size of the Entropic Farm Matrix GUI, shared by its menu (slot positions, both
 * sides) and its screen (drawing, client). Coordinates are relative to the GUI's top-left corner.
 */
package com.virtualfarmworks.menu;

import com.virtualfarmworks.machine.MachineLayout;

/**
 * GUI geometry of the Entropic. The central part is the owner's texture ({@code textures/gui/entropic_farm_matrix_gui.png},
 * 296x320 drawn in a 512x512 file); its coordinates are the owner's (see {@code docs/specs/entropic-farm-matrix.md}),
 * checked on the pixels: slot interiors 16 px on an 18 px pitch, a 1 px red border left, 3 px on top.
 *
 * <p>The side column is drawn by code like the Starter's ({@link FarmMatrixLayout}: same x columns, same box drawing,
 * 3 px gaps), glued to the texture's left border, with this tier's boxes: "O" (face modes), the lightning box (energy
 * use on hover, owner 2026-09-29: right below the "O"), the upgrade block (4 Growth Speed Upgrades, Crux Provider; no
 * hoe: the owner removed it from this tier), Fertilized Essence, replant (owner 2026-09-29), harvest filter,
 * autocrafter (owner: between the filter and the ON/OFF button) and ON/OFF. Its "O" interior lines up with the first
 * grid row.
 *
 * <p>Slot positions are the top-left pixel of the 16x16 item, as Minecraft expects.
 */
public final class EntropicLayout {
    // --- owner's texture ------------------------------------------------------------------------------------------
    public static final int GUI_WIDTH = 296;
    public static final int GUI_HEIGHT = 320;
    public static final int TEXTURE_SIZE = 512;
    public static final int SLOT_PITCH = 18;

    /** Plantable grid (14,20)-(281,89) and soil grid (14,95)-(281,164): 4 rows of 15. */
    public static final int GRID_X = 14;
    public static final int SEED_GRID_Y = 20;
    public static final int SOIL_GRID_Y = 95;

    /** Water Provider slot (209,170)-(224,185). */
    public static final int WATER_X = 209;
    public static final int WATER_Y = 170;

    /** Output buffer (230,170)-(281,311): 3 columns x 8 rows. */
    public static final int OUTPUT_X = 230;
    public static final int OUTPUT_Y = 170;
    public static final int OUTPUT_COLUMNS = 3;

    /** Player inventory (35,241)-(194,292), hotbar (35,299)-(194,314): vanilla's standard 58 px apart. */
    public static final int PLAYER_INVENTORY_X = 35;
    public static final int PLAYER_INVENTORY_Y = 241;

    /** Progress bar (19,224)-(147,228): 129 x 5. */
    public static final int BAR_X = 19;
    public static final int BAR_Y = 224;
    public static final int BAR_WIDTH = 129;
    public static final int BAR_HEIGHT = 5;

    /** FE bar (153,224)-(220,228): 68 x 5, pixel columns alternating red / dark red (owner spec). */
    public static final int ENERGY_BAR_X = 153;
    public static final int ENERGY_BAR_Y = 224;
    public static final int ENERGY_BAR_WIDTH = 68;
    public static final int ENERGY_BAR_HEIGHT = 5;
    public static final int COLOR_ENERGY = 0xFFFF2020;
    public static final int COLOR_ENERGY_DARK = 0xFF9E0E0E;

    /**
     * Info text area (18,173)-(206,219): five lines 9 px apart (status, hydration, plots, waiting plots, growth), 2 px
     * below the area's top (owner, 2026-09-29: one pixel lower than first drawn). {@link #INFO_WIDTH} ends before the
     * Water Provider slot; longer texts are scaled down.
     */
    public static final int INFO_X = 19;
    public static final int[] INFO_LINE_Y = {175, 184, 193, 202, 211};
    public static final int INFO_WIDTH = 187;

    /** Title, centered in the band between the 3 px top border and the first grid frame (y 19). */
    public static final int TITLE_Y = 7;

    /** A plot group's problem (owner decision): the seed slot is tinted red. */
    public static final int COLOR_GROUP_PROBLEM = 0x80D02020;

    // --- side column (drawn by code; x columns and box drawing from FarmMatrixLayout) ------------------------------
    /** Cells of the upgrade block: 4 Growth Speed Upgrades, the Crux Provider. */
    public static final int UPGRADE_CELLS = 5;
    public static final int OUTPUT_BOX_TOP = SEED_GRID_Y - 2;                                                     // 18
    /** Lightning box (owner, 2026-09-29): not a button; its tooltip tells the energy use. */
    public static final int ENERGY_BOX_TOP = nextBox(OUTPUT_BOX_TOP, 1);                                          // 41
    public static final int UPGRADE_BOX_TOP = nextBox(ENERGY_BOX_TOP, 1);                                         // 64
    public static final int FERTILIZED_BOX_TOP = nextBox(UPGRADE_BOX_TOP, UPGRADE_CELLS);                         // 155
    /** Replant switch (owner, 2026-09-29): green ON, red OFF, grey DISABLED by the config. */
    public static final int REPLANT_BUTTON_TOP = nextBox(FERTILIZED_BOX_TOP, 1);                                  // 178
    public static final int FILTER_BUTTON_TOP = nextBox(REPLANT_BUTTON_TOP, 1);                                   // 201
    /** Autocrafter button (owner, 2026-09-29): between the harvest filter button and ON/OFF. */
    public static final int CRAFTER_BUTTON_TOP = nextBox(FILTER_BUTTON_TOP, 1);                                   // 224
    public static final int POWER_BOX_TOP = nextBox(CRAFTER_BUTTON_TOP, 1);                                       // 247
    public static final int PANEL_BOTTOM = POWER_BOX_TOP + FarmMatrixLayout.boxHeight(1);                         // 267
    /**
     * Lightning bolt drawn in the energy box, copied pixel by pixel from the owner's drawing (2026-09-29): {x, y} inside
     * the 16x16 cell, a red stroke (the FE bar's red) and its dark red shade. The drawing was purple; the owner wants
     * it red.
     */
    public static final int[][] BOLT_LIGHT = {
            {8, 4}, {7, 5}, {6, 6}, {5, 7}, {6, 7}, {7, 7}, {8, 7}, {9, 7}, {8, 8}, {7, 9}, {6, 10}};
    public static final int[][] BOLT_SHADE = {
            {9, 5}, {8, 6}, {6, 8}, {7, 8}, {9, 8}, {10, 8}, {9, 9}, {8, 10}, {7, 11}};
    public static final int COLOR_BOLT_LIGHT = COLOR_ENERGY;
    public static final int COLOR_BOLT_SHADE = 0xFF3F1010;
    /** Replant button look while the config disables replanting: grey box, greyed seed. */
    public static final int COLOR_DISABLED = 0xFF5E5E5E;
    public static final int COLOR_DISABLED_COVER = 0xA0555555;

    /** Face box ("O"): left of the column like the Starter's, its top next to the "O" box. */
    public static final int FACE_BOX_X = FarmMatrixLayout.FACE_BOX_X;
    public static final int FACE_BOX_Y = OUTPUT_BOX_TOP + 1;

    /** Harvest filter box: same size and column as the Starter's, bottom aligned with its button. */
    public static final int FILTER_BOX_X = FarmMatrixLayout.FILTER_BOX_X;
    public static final int FILTER_BOX_Y = FILTER_BUTTON_TOP + FarmMatrixLayout.boxHeight(1) - FarmMatrixLayout.FILTER_BOX_HEIGHT;
    public static final int FILTER_CONTENT_X = FILTER_BOX_X + 4;
    public static final int FILTER_STRIP_Y = FILTER_BOX_Y + 3;
    public static final int FILTER_GRID_Y = FILTER_STRIP_Y + FarmMatrixLayout.FILTER_STRIP_HEIGHT + 1;
    public static final int FILTER_PAGE_ROW_Y = FILTER_GRID_Y + FarmMatrixLayout.FILTER_GRID_SIZE + 1;

    // --- autocrafter panel (owner texture crafter_farm_matrix_gui.png, 197x101 in a 256x128 file) -----------------
    public static final int CRAFTER_WIDTH = 197;
    public static final int CRAFTER_HEIGHT = 101;
    public static final int CRAFTER_TEXTURE_WIDTH = 256;
    public static final int CRAFTER_TEXTURE_HEIGHT = 128;
    /** Opened OVER the machine (owner): centered horizontally, over the two grids. */
    public static final int CRAFTER_X = (GUI_WIDTH - CRAFTER_WIDTH) / 2;                                    // 49
    public static final int CRAFTER_Y = (19 + 165) / 2 - CRAFTER_HEIGHT / 2;                                // 42
    /** Inside the panel: crafting grid (7,7)-(58,58) on an 18 px pitch, result (64,25). */
    public static final int CRAFTER_GRID_X = CRAFTER_X + 7;
    public static final int CRAFTER_GRID_Y = CRAFTER_Y + 7;
    public static final int CRAFTER_RESULT_X = CRAFTER_X + 64;
    public static final int CRAFTER_RESULT_Y = CRAFTER_Y + 25;
    /**
     * Catalyst slot (owner, 2026-09-29: "below and glued to the result slot", for the Master Infusion Crystal): not on
     * the owner's texture, so its 18x18 frame is drawn by code, one pitch below the result, like the grid's cells.
     */
    public static final int CRAFTER_CATALYST_X = CRAFTER_RESULT_X;
    public static final int CRAFTER_CATALYST_Y = CRAFTER_RESULT_Y + SLOT_PITCH;
    /** Buttons SET CRAFT (7,67)-(58,75) and CRAFT: ON/OFF (7,84)-(58,92). */
    public static final int CRAFTER_BUTTON_X = CRAFTER_X + 7;
    public static final int CRAFTER_BUTTON_WIDTH = 52;
    public static final int CRAFTER_BUTTON_HEIGHT = 9;
    public static final int CRAFTER_SET_Y = CRAFTER_Y + 67;
    public static final int CRAFTER_TOGGLE_Y = CRAFTER_Y + 84;
    /** Button text size (owner, 2026-09-29: the labels were too big at full size). */
    public static final float CRAFTER_BUTTON_TEXT_SCALE = 0.75F;
    /** Recipe list (85,7)-(189,93). */
    public static final int CRAFTER_LIST_X = CRAFTER_X + 85;
    public static final int CRAFTER_LIST_Y = CRAFTER_Y + 7;
    public static final int CRAFTER_LIST_WIDTH = 105;
    public static final int CRAFTER_LIST_HEIGHT = 87;
    /** List rows: 16 px icon + 1 px, five visible (85 of the 87 px, 1 px margin above and below); more scroll. */
    public static final int CRAFTER_ROW_HEIGHT = 17;
    public static final int CRAFTER_ROWS = 5;
    public static final int CRAFTER_ROWS_Y = CRAFTER_LIST_Y + 1;
    /** Scrollbar at the list's right edge, shown when there are more recipes than rows. */
    public static final int CRAFTER_SCROLLBAR_WIDTH = 3;
    /** While the panel is open the two grids behind it are dimmed and inactive: (13,19)-(282,165), frames included. */
    public static final int GRIDS_X0 = GRID_X - 1;
    public static final int GRIDS_Y0 = SEED_GRID_Y - 1;
    public static final int GRIDS_X1 = GRID_X + MachineLayout.GRID_COLUMNS * SLOT_PITCH - 1;
    public static final int GRIDS_Y1 = SOIL_GRID_Y + MachineLayout.GRID_ROWS * SLOT_PITCH - 1;
    // Panel colors: the texture's frame (0x34586B) and background (0x0F1A26), and shades of them.
    public static final int COLOR_CRAFTER_FRAME = 0xFF34586B;
    public static final int COLOR_CRAFTER_BACKDROP = 0xB0000000;
    public static final int COLOR_CRAFTER_HOVER_TOP = 0xFF4E829C;
    public static final int COLOR_CRAFTER_HOVER_BOTTOM = 0xFF1B3242;
    public static final int COLOR_CRAFTER_SELECTED = 0xFF34586B;
    public static final int COLOR_CRAFTER_ROW_HOVER = 0x30FFFFFF;
    public static final int COLOR_CRAFTER_TEXT = 0xFFE0E6EA;
    public static final int COLOR_CRAFTER_TEXT_DISABLED = 0xFF6F7F8A;
    public static final int COLOR_CRAFTER_ON = 0xFF55E36B;
    public static final int COLOR_CRAFTER_OFF = 0xFFFF5C5C;

    private EntropicLayout() {
    }

    /** Top of the side-column box after the one starting at {@code top} with {@code cells} cells (3 px gap). */
    private static int nextBox(int top, int cells) {
        return top + FarmMatrixLayout.boxHeight(cells) + FarmMatrixLayout.PANEL_BOX_GAP;
    }

    public static int seedSlotX(int group) {
        return GRID_X + (group % MachineLayout.GRID_COLUMNS) * SLOT_PITCH;
    }

    public static int seedSlotY(int group) {
        return SEED_GRID_Y + (group / MachineLayout.GRID_COLUMNS) * SLOT_PITCH;
    }

    public static int soilSlotY(int group) {
        return SOIL_GRID_Y + (group / MachineLayout.GRID_COLUMNS) * SLOT_PITCH;
    }

    public static int outputSlotX(int index) {
        return OUTPUT_X + (index % OUTPUT_COLUMNS) * SLOT_PITCH;
    }

    public static int outputSlotY(int index) {
        return OUTPUT_Y + (index / OUTPUT_COLUMNS) * SLOT_PITCH;
    }

    /** Item y of upgrade cell {@code i} (0..3 growth, 4 crux). */
    public static int upgradeCellY(int i) {
        return FarmMatrixLayout.cellY(UPGRADE_BOX_TOP, i);
    }

    public static int faceCellX(int col) {
        return FACE_BOX_X + FarmMatrixLayout.FACE_PADDING + col * (FarmMatrixLayout.FACE_CELL + FarmMatrixLayout.FACE_GAP);
    }

    public static int faceCellY(int row) {
        return FACE_BOX_Y + FarmMatrixLayout.FACE_PADDING + row * (FarmMatrixLayout.FACE_CELL + FarmMatrixLayout.FACE_GAP);
    }

    public static int filterSlotX(int i) {
        return FILTER_CONTENT_X + 1 + (i % FarmMatrixLayout.FILTER_GRID_CELLS) * FarmMatrixLayout.CELL_PITCH;
    }

    public static int filterSlotY(int i) {
        return FILTER_GRID_Y + 1 + (i / FarmMatrixLayout.FILTER_GRID_CELLS) * FarmMatrixLayout.CELL_PITCH;
    }

    public static int crafterGridX(int i) {
        return CRAFTER_GRID_X + (i % 3) * SLOT_PITCH;
    }

    public static int crafterGridY(int i) {
        return CRAFTER_GRID_Y + (i / 3) * SLOT_PITCH;
    }
}
