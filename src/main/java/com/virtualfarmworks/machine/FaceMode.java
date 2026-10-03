/*
 * FaceMode — what one face of a Farm Matrix does for automation (owner's Entropic spec, names of 2026-10-02): nothing,
 * export what the plants produce, what the autocrafter made, or both, accept seeds and soils, or give the grids' seeds
 * and soils back. Colors of the face box.
 */
package com.virtualfarmworks.machine;

/**
 * The mode of one machine face, chosen in the GUI's face box ("O" button). Names as the GUI shows them (owner,
 * 2026-10-02):
 *
 * <ul>
 *   <li>{@link #NONE} (gray), "NONE": the face gives and takes nothing.</li>
 *   <li>{@link #OUTPUT} (green), "OUTPUT ONLY SEED PRODUCTION": exports what the plants produced, not what the
 *       autocrafter made.</li>
 *   <li>{@link #OUTPUT_CRAFTED} (yellow), "OUTPUT ONLY CRAFTED": exports only the autocrafter's results.</li>
 *   <li>{@link #OUTPUT_ALL} (pink), "OUTPUT ALL PRODUCED": exports both; never the grids' seeds and soils.</li>
 *   <li>{@link #INPUT} (blue), "INPUT SEEDS AND SOILS": pipes insert plantables into the seed grid and soils into the
 *       soil grid; adjacent inventories are pulled from.</li>
 *   <li>{@link #OUTPUT_SEEDS_AND_SOILS} (purple), "OUTPUT ONLY SEEDS AND SOILS": exports the seeds and soils of the
 *       two grids (the planted ones, not the harvest), e.g. to empty a machine into a chest.</li>
 * </ul>
 * The output modes take from the output buffer; {@link #OUTPUT_SEEDS_AND_SOILS} takes from the grids only. The
 * Starter only has NONE and OUTPUT (its original auto-output on/off switch, shown as ON/OFF, never by these names).
 *
 * <p>The ordinal is persisted (face modes of a machine) and synced: append new modes at the end, never reorder. The
 * click order is the declaration order.
 */
public enum FaceMode {
    NONE(0xFF6E6E6E),
    OUTPUT(0xFF2E8B57),
    OUTPUT_CRAFTED(0xFFC9A227),
    OUTPUT_ALL(0xFFD66BA0),
    INPUT(0xFF3D6FD1),
    OUTPUT_SEEDS_AND_SOILS(0xFF8E44AD);

    private static final FaceMode[] VALUES = values();

    /** ARGB color of the face box cell (owner spec: gray, green, yellow, pink, blue, purple). */
    private final int color;

    FaceMode(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    /** Whether this face exports from the output buffer (the three output modes). */
    public boolean exportsOutput() {
        return this == OUTPUT || this == OUTPUT_CRAFTED || this == OUTPUT_ALL;
    }

    /** Whether output-buffer items of this origin leave through this face ({@code crafted} = made by the autocrafter). */
    public boolean exportsOutput(boolean crafted) {
        return this == OUTPUT_ALL || (crafted ? this == OUTPUT_CRAFTED : this == OUTPUT);
    }

    /** Whether this face exports the seeds and soils of the grids. */
    public boolean exportsGrids() {
        return this == OUTPUT_SEEDS_AND_SOILS;
    }

    /** Lang key of the name, e.g. {@code face_mode.virtualfarmworks.output_crafted}. */
    public String translationKey() {
        return "face_mode.virtualfarmworks." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Lang key of the one-line explanation the face box's tooltip shows ({@code <name key>.desc}). */
    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /** The next mode when the player clicks a face ({@code forward}) or right-clicks it, among the tier's modes. */
    public FaceMode cycle(boolean forward, MachineLayout layout) {
        if (!layout.acceptsInput()) {
            return this == OUTPUT ? NONE : OUTPUT; // the Starter's on/off switch
        }
        int step = forward ? 1 : VALUES.length - 1;
        return VALUES[(ordinal() + step) % VALUES.length];
    }

    public static FaceMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
    }
}
