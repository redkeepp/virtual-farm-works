/*
 * FaceMode — what one face of a Farm Matrix does for automation (owner's Entropic spec): nothing, export what the plants
 * produce, export what the autocrafter made, export both, or accept seeds and soils from pipes. Colors of the face box.
 */
package com.virtualfarmworks.machine;

/**
 * The mode of one machine face, chosen in the GUI's face box ("O" button).
 *
 * <ul>
 *   <li>{@link #NONE} (gray): the face gives and takes nothing.</li>
 *   <li>{@link #OUTPUT} (green): exports what the plants produce (everything except the autocrafter's results).</li>
 *   <li>{@link #OUTPUT_CRAFTED} (yellow): exports only the autocrafter's results.</li>
 *   <li>{@link #OUTPUT_ALL} (pink): exports both.</li>
 *   <li>{@link #INPUT} (blue): pipes insert plantables into the seed grid and soils into the soil grid.</li>
 * </ul>
 * The Starter only has NONE and OUTPUT (its original auto-output on/off switch).
 *
 * <p>The ordinal is persisted (face modes of a machine) and synced: append new modes at the end, never reorder.
 */
public enum FaceMode {
    NONE(0xFF6E6E6E),
    OUTPUT(0xFF2E8B57),
    OUTPUT_CRAFTED(0xFFC9A227),
    OUTPUT_ALL(0xFFD66BA0),
    INPUT(0xFF3D6FD1);

    private static final FaceMode[] VALUES = values();

    /** ARGB color of the face box cell (owner spec: gray, green, yellow, pink, blue). */
    private final int color;

    FaceMode(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    /** Whether this face exports anything. */
    public boolean exports() {
        return this == OUTPUT || this == OUTPUT_CRAFTED || this == OUTPUT_ALL;
    }

    /** Whether items of this origin leave through this face ({@code crafted} = made by the autocrafter). */
    public boolean exports(boolean crafted) {
        return this == OUTPUT_ALL || (crafted ? this == OUTPUT_CRAFTED : this == OUTPUT);
    }

    /** Lang key, e.g. {@code face_mode.virtualfarmworks.output_crafted}. */
    public String translationKey() {
        return "face_mode.virtualfarmworks." + name().toLowerCase(java.util.Locale.ROOT);
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
