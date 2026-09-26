package com.virtualfarmworks.sim;

/**
 * Plain facts about a machine, filled by the BlockEntity when it revalidates and turned into a {@link MachineStatus}
 * by {@link MachineStatus#resolve}. A mutable builder with fluent setters (instead of a constructor with nine
 * booleans) so call sites and tests read clearly and cannot swap two arguments by accident.
 *
 * <p>Defaults describe a healthy machine: everything present, nothing required that is missing.
 */
public final class MachineConditions {
    boolean enabled = true;
    boolean hasSeed = true;
    boolean hasSoil = true;
    boolean soilCompatible = true;
    boolean needsHoe;
    boolean hasHoe;
    boolean needsCrux;
    boolean hasCrux;
    boolean needsEnergy;
    boolean hasEnergy;
    boolean outputBlocked;

    /** The on/off button is ON. */
    public MachineConditions enabled(boolean value) {
        enabled = value;
        return this;
    }

    /** The seed slot holds a plantable, non-blacklisted seed. */
    public MachineConditions hasSeed(boolean value) {
        hasSeed = value;
        return this;
    }

    /** The soil slot holds a soil, non-blacklisted, and there is at least one plot. */
    public MachineConditions hasSoil(boolean value) {
        hasSoil = value;
        return this;
    }

    /** The seed can grow on the soil (possibly with a hoe). */
    public MachineConditions soilCompatible(boolean value) {
        soilCompatible = value;
        return this;
    }

    public MachineConditions hoe(boolean needed, boolean present) {
        needsHoe = needed;
        hasHoe = present;
        return this;
    }

    public MachineConditions crux(boolean needed, boolean present) {
        needsCrux = needed;
        hasCrux = present;
        return this;
    }

    /** Energy requirement for this tick (always "not needed" for the Starter tier). */
    public MachineConditions energy(boolean needed, boolean present) {
        needsEnergy = needed;
        hasEnergy = present;
        return this;
    }

    /** A due harvest could not be stored because the output buffer lacks space. */
    public MachineConditions outputBlocked(boolean value) {
        outputBlocked = value;
        return this;
    }
}
