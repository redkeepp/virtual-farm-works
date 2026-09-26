package com.virtualfarmworks.sim;

/**
 * The speed of a machine, kept as its three components so the GUI can show each one (Hydration line, Growth line)
 * and the total is always their product.
 *
 * <p>Owner decision (multiplicative, "for now"):
 * {@code speed = hydration x upgrades x soil}, e.g. no Water Provider (0.25) with 4 Growth Speed Upgrades
 * (1 + 4 x 0.5 = 3.0) on plain farmland (1.0) = 0.75x.
 *
 * <p>Computed only when the machine revalidates (slot or config change); the machine caches
 * {@link #progressPerTick(int)} and adds it every tick.
 *
 * @param hydration 1.0 with a Water Provider Upgrade, otherwise the tier's {@code noWaterSpeedMultiplier}
 * @param upgrades  {@code 1 + bonusPerUpgrade x installedGrowthUpgrades}
 * @param soil      {@code 1 + growth_bonus} of the soil (soil_properties data map), 1.0 for plain soils
 */
public record GrowthSpeed(double hydration, double upgrades, double soil) {
    /** 1.0x everything: Water Provider installed, no upgrades, plain soil. */
    public static final GrowthSpeed BASELINE = new GrowthSpeed(1.0, 1.0, 1.0);

    /**
     * Builds the speed from raw machine state and config values.
     *
     * @param hasWaterProvider  a Water Provider Upgrade is installed
     * @param noWaterMultiplier the tier's config value used without a Water Provider
     * @param growthUpgrades    number of Growth Speed Upgrades installed (negative treated as 0)
     * @param bonusPerUpgrade   config bonus per upgrade (0.5 = +50%)
     * @param soilMultiplier    soil speed multiplier ({@code 1 + growth_bonus})
     */
    public static GrowthSpeed of(boolean hasWaterProvider, double noWaterMultiplier, int growthUpgrades,
                                 double bonusPerUpgrade, double soilMultiplier) {
        double hydration = hasWaterProvider ? 1.0 : noWaterMultiplier;
        double upgrades = 1.0 + bonusPerUpgrade * Math.max(0, growthUpgrades);
        return new GrowthSpeed(hydration, upgrades, soilMultiplier);
    }

    /** Final speed multiplier. */
    public double total() {
        return hydration * upgrades * soil;
    }

    /**
     * Progress added to the {@link GrowthCycle} per tick: {@code total / growthTicks}. A full cycle at 1.0x therefore
     * takes exactly {@code growthTicks} ticks (Starter default 600 = 30 s). Non-positive {@code growthTicks} yields 0
     * (machine does not advance) instead of dividing by zero.
     */
    public double progressPerTick(int growthTicks) {
        return growthTicks > 0 ? total() / growthTicks : 0.0;
    }
}
