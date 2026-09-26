/*
 * HarvestBatchingTest — JUnit tests of sim/HarvestBatching and sim/YieldSample: how many ripe plots a harvest batch
 * takes from the free output slots and the yield measured so far. Run with `gradlew test`.
 */
package com.virtualfarmworks.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HarvestBatchingTest {
    private static final double EPS = 1e-12;

    /** Wheat at default config: 1 wheat + about 1.4 extra seeds per plot = 2.4 / 64 slots, two item types. */
    private static YieldSample wheat(long plots) {
        YieldSample sample = new YieldSample();
        sample.record((int) plots, plots * 2.4 / 64.0, 2);
        return sample;
    }

    @Test
    void nothingLeftMeansNoBatch() {
        assertEquals(0, HarvestBatching.batchSize(0, 36, wheat(64)));
        assertEquals(0, HarvestBatching.batchSize(-5, 36, wheat(64)));
    }

    @Test
    void theFirstBatchOfANewSeedIsOnePlot() {
        assertEquals(1, HarvestBatching.batchSize(64, 36, new YieldSample()));
    }

    @Test
    void batchesGrowWithTheEvidence() {
        // 1 plot measured -> at most 2; 3 measured -> 6; 9 -> 18: sizes 1, 2, 6, 18, 54...
        assertEquals(2, HarvestBatching.batchSize(64, 36, wheat(1)));
        assertEquals(6, HarvestBatching.batchSize(64, 36, wheat(3)));
        assertEquals(18, HarvestBatching.batchSize(64, 36, wheat(9)));
    }

    @Test
    void aKnownSmallHarvestIsTakenAtOnce() {
        // Normal Starter case after the first cycle: 64 wheat plots need ~2.4 slots, 36 are free.
        assertEquals(64, HarvestBatching.batchSize(64, 36, wheat(64)));
    }

    @Test
    void aBigHarvestIsSizedToHalfTheFreeSlots() {
        // Production multiplier 50: 50 wheat + 1.4 seeds per plot = 0.803 slots per plot.
        YieldSample sample = new YieldSample();
        sample.record(64, 64 * 51.4 / 64.0, 2);
        int plots = HarvestBatching.batchSize(64, 36, sample);
        // (36 free - 2 types) x 0.5 = 17 slots of budget -> 21 plots (~16.9 slots expected).
        assertEquals(21, plots);
        assertTrue(plots * sample.slotsPerPlot() <= (36 - 2) * HarvestBatching.SPACE_MARGIN + EPS);
    }

    @Test
    void withNoFreeSlotsOnePlotIsTried() {
        // It may still fit into partly filled stacks; if not, the machine keeps it and waits.
        assertEquals(1, HarvestBatching.batchSize(64, 0, wheat(64)));
        assertEquals(1, HarvestBatching.batchSize(64, 2, wheat(64)));
    }

    @Test
    void onePlotBiggerThanTheOutputIsStillOnePlot() {
        // Extreme case: 3000 wheat per plot = 47 slots. The machine stores what fits and holds the rest.
        YieldSample sample = new YieldSample();
        sample.record(1, 3000 / 64.0, 1);
        assertEquals(1, HarvestBatching.batchSize(10, 36, sample));
    }

    @Test
    void plotsThatYieldNothingAreLimitedOnlyByEvidence() {
        YieldSample sample = new YieldSample();
        sample.record(4, 0.0, 0); // e.g. production multiplier 0
        assertEquals(8, HarvestBatching.batchSize(64, 0, sample));
    }

    @Test
    void yieldSampleAveragesAndResets() {
        YieldSample sample = new YieldSample();
        sample.record(2, 1.0, 2);
        sample.record(6, 2.0, 3);
        sample.record(0, 5.0, 9);          // ignored: no plots
        sample.record(3, Double.NaN, 9);   // ignored: not a number

        assertEquals(8, sample.plots());
        assertEquals(3.0 / 8.0, sample.slotsPerPlot(), EPS);
        assertEquals(3, sample.itemTypes());

        sample.reset();
        assertTrue(sample.isEmpty());
        assertEquals(0.0, sample.slotsPerPlot(), EPS);
    }
}
