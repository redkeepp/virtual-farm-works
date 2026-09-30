/*
 * MachineLayout — the shape of a Farm Matrix tier: how many plot groups (seed slot + soil slot pairs) it has, where
 * each input sits in its inventory, how many visible output slots it shows, and which features it has (energy,
 * autocrafter, automated input, hoe). One per tier; the Starter is the one-group case.
 */
package com.virtualfarmworks.machine;

/**
 * Structural description of a tier. Balance numbers (slot capacities, energy per plot, buffer sizes...) are NOT here:
 * they are config values ({@code config.VfwServerConfig}); this class only holds what the code and the GUI texture
 * fix.
 *
 * <h2>Input inventory order (persisted: never change it for an existing tier)</h2>
 * {@code [0, g)} seed slots, {@code [g, 2g)} soil slots, then the water provider, the tool slot, the 4 Growth Speed
 * Upgrades and the Crux Provider ({@code g} = plot groups). With {@code g = 1} this is exactly the Starter's historical
 * order ({@link MachineSlots}: seed 0, soil 1, water 2, hoe 3, growth 4..7, crux 8), so Starter saves load unchanged.
 * The tool slot holds the hoe on tiers that use one ({@link #usesHoe}) and the autocrafter's catalyst (the Master
 * Infusion Crystal, see {@code MachineCrafter}) on tiers with an autocrafter; a tier with both would need a new slot.
 *
 * <h2>Plot groups (owner's Entropic spec)</h2>
 * A plot group is one seed slot and the soil slot at the same position of the other grid: the seed in the top grid at
 * row 3, column 7 uses the soil in the bottom grid at row 3, column 7. Each group holds one plantable type and one soil
 * type; the machine's plots are the sum over its groups. The machine runs only while EVERY group holding a plantable
 * can grow (owner, 2026-09-29): one invalid group stops the whole bar, like the Starter's single pair.
 *
 * @param groups             plot groups (Starter 1, Entropic 60 = two 4x15 grids)
 * @param visibleOutputSlots output slots shown in the GUI (Starter 9, Entropic 24); the hidden ones are config
 * @param usesEnergy         consumes FE per planted plot and shows MISSING FE without it (Entropic)
 * @param hasCrafter         has the autocrafter (Entropic)
 * @param acceptsInput       faces can be set to INPUT: pipes fill the grids and neighbouring inventories are pulled
 *                           from (Entropic); the Starter only lets automation extract
 * @param usesHoe            plants that need tilled soil need a hoe in the tool slot (Starter). Without it the machine
 *                           tills for free (owner, Entropic: "ignore the hoe completely")
 */
public record MachineLayout(int groups, int visibleOutputSlots, boolean usesEnergy, boolean hasCrafter,
                            boolean acceptsInput, boolean usesHoe) {

    /** Owner's Starter spec: one seed slot, one soil slot, 9 visible output slots, no energy, a hoe slot. */
    public static final MachineLayout STARTER = new MachineLayout(1, 9, false, false, false, true);
    /** Owner's Entropic spec: two 4x15 grids (60 plot groups), 3x8 visible output slots, FE, autocrafter, input. */
    public static final MachineLayout ENTROPIC = new MachineLayout(60, 24, true, true, true, false);

    /** Grid shape of the Entropic GUI (owner texture): 4 rows of 15 slots per grid. */
    public static final int GRID_ROWS = 4;
    public static final int GRID_COLUMNS = 15;

    /** Layout of a tier. Tiers without a machine yet (Voltaic, Ionic, Resonant) have no layout. */
    public static MachineLayout of(MachineTier tier) {
        return switch (tier) {
            case STARTER -> STARTER;
            case ENTROPIC -> ENTROPIC;
            default -> throw new IllegalStateException("No machine layout for tier " + tier.getSerializedName());
        };
    }

    public int seedSlot(int group) {
        return group;
    }

    public int soilSlot(int group) {
        return groups + group;
    }

    public int waterSlot() {
        return 2 * groups;
    }

    /** The tool slot: the hoe on tiers that {@link #usesHoe use one}; check that before reading it as a hoe. */
    public int hoeSlot() {
        return 2 * groups + 1;
    }

    /** The tool slot on tiers with an autocrafter: its catalyst (same index as the hoe, see the class doc). */
    public int catalystSlot() {
        return 2 * groups + 1;
    }

    public int growthSlot(int index) {
        return 2 * groups + 2 + index;
    }

    public int cruxSlot() {
        return 2 * groups + 2 + MachineSlots.GROWTH_COUNT;
    }

    /** Size of the input inventory. */
    public int inputCount() {
        return cruxSlot() + 1;
    }

    public boolean isSeedSlot(int index) {
        return index >= 0 && index < groups;
    }

    public boolean isSoilSlot(int index) {
        return index >= groups && index < 2 * groups;
    }

    public boolean isGrowthSlot(int index) {
        return index >= growthSlot(0) && index < growthSlot(0) + MachineSlots.GROWTH_COUNT;
    }

    /** The plot group of a seed or soil slot. */
    public int groupOf(int index) {
        return isSoilSlot(index) ? index - groups : index;
    }
}
