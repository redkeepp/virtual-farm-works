/*
 * MachineStatus — the states shown in the machine GUI (RUNNING, MISSING ..., INVALID SOIL, OUTPUT FULL, SHUTDOWN), their
 * priority, colors and lang keys. Part of the Minecraft-free simulation core (package sim), unit-tested.
 */
package com.virtualfarmworks.sim;

/**
 * The state shown at the top of the machine's info panel.
 *
 * <p>Declaration order is the display PRIORITY: when several problems exist, the first one in this list wins (see
 * {@link #resolve(MachineConditions)}). The missing-input order is the owner's hierarchy (MISSING SEED > MISSING SOIL >
 * INVALID SOIL > MISSING HOE > MISSING CRUX > MISSING FE). SHUTDOWN comes first because it is the player's explicit
 * choice; OUTPUT FULL comes after the missing inputs because it only matters once the machine could grow at all.
 *
 * <p>The ordinal is synced to the client as a number, so appending new states at the end is safe but REORDERING
 * changes priority and the network meaning — review both when touching this enum.
 */
public enum MachineStatus {
    SHUTDOWN(Tone.ERROR),
    MISSING_SEED(Tone.WARNING),
    MISSING_SOIL(Tone.WARNING),
    INVALID_SOIL(Tone.WARNING),
    MISSING_HOE(Tone.WARNING),
    MISSING_CRUX(Tone.WARNING),
    MISSING_FE(Tone.WARNING),
    OUTPUT_FULL(Tone.ERROR),
    RUNNING(Tone.GOOD);

    /** Display color family (owner spec: RUNNING green, missing inputs orange, OUTPUT FULL / SHUTDOWN red). */
    public enum Tone {
        GOOD(0x55FF55),
        WARNING(0xFFAA00),
        ERROR(0xFF5555);

        private final int rgb;

        Tone(int rgb) {
            this.rgb = rgb;
        }

        public int rgb() {
            return rgb;
        }
    }

    private static final MachineStatus[] VALUES = values();

    private final Tone tone;

    MachineStatus(Tone tone) {
        this.tone = tone;
    }

    public Tone tone() {
        return tone;
    }

    /** Only a RUNNING machine advances its growth cycle. */
    public boolean isRunning() {
        return this == RUNNING;
    }

    /** Lang key, e.g. {@code status.virtualfarmworks.missing_seed}. */
    public String translationKey() {
        return "status.virtualfarmworks." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Safe decoding of a synced ordinal; unknown values (version mismatch) fall back to SHUTDOWN. */
    public static MachineStatus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SHUTDOWN;
    }

    /** Picks the highest-priority status for the given conditions. Pure function, see {@link MachineConditions}. */
    public static MachineStatus resolve(MachineConditions c) {
        if (!c.enabled) {
            return SHUTDOWN;
        }
        if (!c.hasSeed) {
            return MISSING_SEED;
        }
        if (!c.hasSoil) {
            return MISSING_SOIL;
        }
        if (!c.soilCompatible) {
            return INVALID_SOIL;
        }
        if (c.needsHoe && !c.hasHoe) {
            return MISSING_HOE;
        }
        if (c.needsCrux && !c.hasCrux) {
            return MISSING_CRUX;
        }
        if (c.needsEnergy && !c.hasEnergy) {
            return MISSING_FE;
        }
        if (c.outputBlocked) {
            return OUTPUT_FULL;
        }
        return RUNNING;
    }
}
