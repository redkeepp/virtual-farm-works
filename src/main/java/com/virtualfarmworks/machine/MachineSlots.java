/*
 * MachineSlots — the fixed slot layout of a Farm Matrix's input inventory (seed, soil, water provider, hoe, 4 growth
 * upgrades, crux) and the size of its output buffer. Indices are persisted in saves: never reorder them.
 */
package com.virtualfarmworks.machine;

/**
 * Slot indices of {@link MachineInventory} (inputs) and the size of {@link OutputBuffer}.
 *
 * <p>These indices are part of the save format (the inventory is stored as an ordered list) and of the menu layout
 * (milestone 1, step 7). Appending new slots at the end is safe; reordering or removing breaks existing worlds.
 */
public final class MachineSlots {
    public static final int SEED = 0;
    public static final int SOIL = 1;
    public static final int WATER_PROVIDER = 2;
    public static final int HOE = 3;
    public static final int GROWTH_FIRST = 4;
    public static final int GROWTH_COUNT = 4;
    public static final int CRUX_PROVIDER = GROWTH_FIRST + GROWTH_COUNT; // 8
    public static final int INPUT_COUNT = CRUX_PROVIDER + 1;            // 9

    public static final int OUTPUT_COUNT = 9;

    /** Owner spec: the seed and soil slots hold up to 64 items (all of the same kind: one stack). */
    public static final int SEED_SOIL_LIMIT = 64;

    private MachineSlots() {
    }

    public static boolean isGrowthSlot(int index) {
        return index >= GROWTH_FIRST && index < GROWTH_FIRST + GROWTH_COUNT;
    }
}
