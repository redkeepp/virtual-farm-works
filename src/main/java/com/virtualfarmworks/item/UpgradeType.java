package com.virtualfarmworks.item;

/**
 * Kinds of tiered upgrades. The Crux Provider Upgrade is NOT here on purpose: it has a single, untiered item
 * ({@link CruxProviderUpgradeItem}).
 */
public enum UpgradeType {
    /**
     * Removes the "no water" speed penalty. Without it the machine runs at a reduced speed (default 0.25x); with it at
     * the designed 1.0x baseline. It does not add speed on top of 1.0x. Max 1 per machine.
     */
    WATER_PROVIDER("water_provider_upgrade"),
    /** Adds growth speed (default +50% each, additive between upgrades). Up to 4 slots per machine. */
    GROWTH_SPEED("growth_upgrade");

    /**
     * Registry-name suffix. Items are registered as {@code <tier>_<suffix>}, matching the owner's texture file names
     * (e.g. {@code starter_growth_upgrade.png}). Registry names are persisted in saves: never change these.
     */
    private final String registrySuffix;

    UpgradeType(String registrySuffix) {
        this.registrySuffix = registrySuffix;
    }

    public String registrySuffix() {
        return registrySuffix;
    }
}
