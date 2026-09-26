package com.virtualfarmworks.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** State priority and colors (owner hierarchy: SEED > SOIL > INVALID SOIL > HOE > CRUX > FE). */
class MachineStatusTest {

    /** A machine with every possible problem at once. */
    private static MachineConditions everythingWrong() {
        return new MachineConditions()
                .enabled(false)
                .hasSeed(false)
                .hasSoil(false)
                .soilCompatible(false)
                .hoe(true, false)
                .crux(true, false)
                .energy(true, false)
                .outputBlocked(true);
    }

    @Test
    void healthyMachineIsRunning() {
        assertEquals(MachineStatus.RUNNING, MachineStatus.resolve(new MachineConditions()));
    }

    @Test
    void problemsAreReportedInPriorityOrder() {
        MachineConditions c = everythingWrong();
        assertEquals(MachineStatus.SHUTDOWN, MachineStatus.resolve(c));
        c.enabled(true);
        assertEquals(MachineStatus.MISSING_SEED, MachineStatus.resolve(c));
        c.hasSeed(true);
        assertEquals(MachineStatus.MISSING_SOIL, MachineStatus.resolve(c));
        c.hasSoil(true);
        assertEquals(MachineStatus.INVALID_SOIL, MachineStatus.resolve(c));
        c.soilCompatible(true);
        assertEquals(MachineStatus.MISSING_HOE, MachineStatus.resolve(c));
        c.hoe(true, true);
        assertEquals(MachineStatus.MISSING_CRUX, MachineStatus.resolve(c));
        c.crux(true, true);
        assertEquals(MachineStatus.MISSING_FE, MachineStatus.resolve(c));
        c.energy(true, true);
        assertEquals(MachineStatus.OUTPUT_FULL, MachineStatus.resolve(c));
        c.outputBlocked(false);
        assertEquals(MachineStatus.RUNNING, MachineStatus.resolve(c));
    }

    @Test
    void requirementsThatAreNotNeededNeverBlock() {
        // Sugar cane on sand: no hoe needed, none installed. Starter: no FE.
        MachineConditions c = new MachineConditions().hoe(false, false).crux(false, false).energy(false, false);
        assertEquals(MachineStatus.RUNNING, MachineStatus.resolve(c));
    }

    @Test
    void onlyRunningAdvancesTheCycle() {
        for (MachineStatus status : MachineStatus.values()) {
            assertEquals(status == MachineStatus.RUNNING, status.isRunning(), status.name());
        }
    }

    @Test
    void colorsFollowTheSpec() {
        assertEquals(MachineStatus.Tone.GOOD, MachineStatus.RUNNING.tone());
        assertEquals(MachineStatus.Tone.ERROR, MachineStatus.OUTPUT_FULL.tone());
        assertEquals(MachineStatus.Tone.ERROR, MachineStatus.SHUTDOWN.tone());
        for (MachineStatus missing : new MachineStatus[] {MachineStatus.MISSING_SEED, MachineStatus.MISSING_SOIL,
                MachineStatus.INVALID_SOIL, MachineStatus.MISSING_HOE, MachineStatus.MISSING_CRUX,
                MachineStatus.MISSING_FE}) {
            assertEquals(MachineStatus.Tone.WARNING, missing.tone(), missing.name());
        }
    }

    @Test
    void unknownSyncedOrdinalFallsBackSafely() {
        assertEquals(MachineStatus.SHUTDOWN, MachineStatus.byOrdinal(-1));
        assertEquals(MachineStatus.SHUTDOWN, MachineStatus.byOrdinal(999));
        assertEquals(MachineStatus.RUNNING, MachineStatus.byOrdinal(MachineStatus.RUNNING.ordinal()));
    }

    @Test
    void translationKeysAreLowercase() {
        assertTrue(MachineStatus.MISSING_SEED.translationKey().endsWith("missing_seed"));
    }
}
