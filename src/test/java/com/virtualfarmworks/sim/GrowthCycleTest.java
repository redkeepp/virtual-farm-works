/*
 * GrowthCycleTest — JUnit tests of sim/GrowthCycle: ACTIVE/PENDING rules, exploit prevention, exact cycle timing,
 * one-harvest-per-tick guarantee and save/load. Run with `gradlew test`.
 */
package com.virtualfarmworks.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Behavior of the global growth cycle and the ACTIVE/PENDING rules. Scenario names follow the owner's spec
 * (CLAUDE.md, "Core design", ACTIVE / PENDING).
 */
class GrowthCycleTest {
    private static final double EPS = 1e-12;

    /** Runs the bar until a harvest is due and returns how many ticks that took. */
    private static int ticksUntilDue(GrowthCycle cycle, double delta) {
        int ticks = 0;
        while (!cycle.isHarvestDue()) {
            cycle.advance(delta);
            ticks++;
            if (ticks > 10_000_000) {
                throw new AssertionError("harvest never became due");
            }
        }
        return ticks;
    }

    @Test
    void emptyMachineReceivingFirstPlotsMakesThemActiveAtZero() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 64, false);

        assertEquals(64, cycle.activePlots());
        assertEquals(0, cycle.pendingPlots());
        assertEquals(0.0, cycle.progress(), EPS);
    }

    @Test
    void plotsAddedMidCycleArePending_ownerExample87Percent() {
        // "Matrix em 87% + 500 Diamond Seeds -> 500 plots entram como PENDING"
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 100, false);
        cycle.advance(0.87);

        cycle.setPlots(0, 600, false);

        assertEquals(100, cycle.activePlots());
        assertEquals(500, cycle.pendingPlots());
        assertEquals(0.87, cycle.progress(), EPS);
    }

    @Test
    void harvestProducesOnlyActivePlotsThenPendingBecomeActiveAndBarRestarts() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 100, false);
        cycle.advance(0.87);
        cycle.setPlots(0, 600, false);

        cycle.advance(0.13);
        assertTrue(cycle.isHarvestDue());
        // The machine harvests activePlots() BEFORE completing: pending plots do not produce.
        assertEquals(100, cycle.activePlots());

        assertTrue(cycle.completeHarvest());
        assertEquals(600, cycle.activePlots());
        assertEquals(0, cycle.pendingPlots());
        assertEquals(0.0, cycle.progress(), 1e-9);
        assertFalse(cycle.isHarvestDue());
    }

    @Test
    void oneCycleAtBaselineTakesExactlyGrowthTicks() {
        // Starter default: 600 ticks = 30 s at 1.0x. Floating-point sums of 1/600 must not turn this into 601.
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 1, false);
        double delta = GrowthSpeed.BASELINE.progressPerTick(600);

        assertEquals(600, ticksUntilDue(cycle, delta));
        cycle.completeHarvest();
        assertEquals(600, ticksUntilDue(cycle, delta));
    }

    @Test
    void carryOverKeepsTheAverageCycleLengthExact() {
        // Supremium farmland (1.35x): 600 / 1.35 = 444.44 ticks per cycle, i.e. 4444.4 ticks for 10 cycles.
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 1, false);
        double delta = new GrowthSpeed(1.0, 1.0, 1.35).progressPerTick(600);

        int ticks = 0;
        for (int harvest = 0; harvest < 10; harvest++) {
            ticks += ticksUntilDue(cycle, delta);
            cycle.completeHarvest();
        }
        // Without carry-over every cycle would round up to 445 ticks (4450 total).
        assertTrue(ticks >= 4444 && ticks <= 4445, "10 cycles took " + ticks + " ticks");
    }

    @Test
    void atMostOneHarvestPerTickEvenWithAbsurdSpeed() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 10, false);

        cycle.advance(3.0); // e.g. growthTicks = 1 and 3x speed
        assertTrue(cycle.isHarvestDue());
        cycle.completeHarvest();

        assertFalse(cycle.isHarvestDue(), "carry must be capped below 100%");
        assertTrue(cycle.progress() < 1.0);
    }

    @Test
    void dueHarvestFreezesTheBarUntilCompleted_outputFull() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 10, false);
        cycle.advance(1.0);
        double atDue = cycle.progress();

        cycle.advance(0.5);
        cycle.advance(0.5);

        assertEquals(atDue, cycle.progress(), EPS);
        assertTrue(cycle.isHarvestDue());
    }

    @Test
    void plotsAddedWhileHarvestIsBlockedArePending() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 10, false);
        cycle.advance(1.0); // due, but the machine could not store it (OUTPUT FULL)

        cycle.setPlots(0, 20, false);

        assertEquals(10, cycle.activePlots());
        assertEquals(10, cycle.pendingPlots());
    }

    @Test
    void completingWithoutDueHarvestDoesNothing() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 10, false);
        cycle.advance(0.5);
        cycle.setPlots(0, 15, false);

        assertFalse(cycle.completeHarvest());
        assertEquals(10, cycle.activePlots());
        assertEquals(5, cycle.pendingPlots());
        assertEquals(0.5, cycle.progress(), EPS);
    }

    @Test
    void removingPlotsTakesPendingFirst() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 40, false);
        cycle.advance(0.5);
        cycle.setPlots(0, 64, false); // 40 active + 24 pending

        cycle.setPlots(0, 50, false); // remove 14 -> all from pending
        assertEquals(40, cycle.activePlots());
        assertEquals(10, cycle.pendingPlots());

        cycle.setPlots(0, 30, false); // remove 20 -> 10 pending, then 10 active
        assertEquals(30, cycle.activePlots());
        assertEquals(0, cycle.pendingPlots());
        assertEquals(0.5, cycle.progress(), EPS, "removing plots never touches progress");
    }

    @Test
    void emptyingTheMachineResetsProgress_noReinsertExploit() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 64, false);
        cycle.advance(0.9);

        cycle.setPlots(0, 0, false); // player takes every seed out at 90%
        assertEquals(0.0, cycle.progress(), EPS);

        cycle.setPlots(0, 64, false); // ...and puts them back
        assertEquals(64, cycle.activePlots());
        assertEquals(0.0, cycle.progress(), EPS, "re-inserted plots must start a fresh cycle");
    }

    @Test
    void keepingOnePlotDoesNotLetNewPlotsSkipTheCycle() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 64, false);
        cycle.advance(0.9);

        cycle.setPlots(0, 1, false);
        cycle.setPlots(0, 64, false);

        assertEquals(1, cycle.activePlots());
        assertEquals(63, cycle.pendingPlots());
        assertEquals(0.9, cycle.progress(), EPS);
    }

    @Test
    void changingThePlantUprootsTheGroup() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 64, false);
        cycle.advance(0.9);

        cycle.setPlots(0, 64, true); // wheat swapped for carrots in one click

        assertEquals(64, cycle.activePlots());
        assertEquals(0, cycle.pendingPlots());
        assertEquals(0.0, cycle.progress(), EPS);
    }

    @Test
    void noProgressWithoutPlotsOrWithInvalidDelta() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.advance(0.5);
        assertEquals(0.0, cycle.progress(), EPS);

        cycle.setPlots(0, 1, false);
        cycle.advance(0.0);
        cycle.advance(-1.0);
        cycle.advance(Double.NaN);
        assertEquals(0.0, cycle.progress(), EPS);
    }

    @Test
    void largeMachineBehavesExactlyLikeASmallOne() {
        // 6,000 plots (Entropic design target) are still two counters; nothing scales with the plot count.
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 6_000, false);
        double delta = GrowthSpeed.BASELINE.progressPerTick(600);

        assertEquals(600, ticksUntilDue(cycle, delta));
        assertEquals(6_000, cycle.activePlots());
    }

    @Test
    void severalGroupsShareOneCycle() {
        GrowthCycle cycle = new GrowthCycle(2);
        cycle.setPlots(0, 10, false);
        cycle.advance(0.5);

        cycle.setPlots(1, 5, false); // another seed slot filled mid-cycle
        assertEquals(10, cycle.group(0).active());
        assertEquals(5, cycle.group(1).pending());

        cycle.advance(0.5);
        cycle.completeHarvest();
        assertEquals(15, cycle.activePlots());
    }

    @Test
    void pendingOnlyMachineStartsAFreshCycle() {
        // Only reachable with several groups: group 1's plots are pending when group 0 is emptied.
        GrowthCycle cycle = new GrowthCycle(2);
        cycle.setPlots(0, 10, false);
        cycle.advance(0.9);
        cycle.setPlots(1, 5, false);

        cycle.setPlots(0, 0, false);

        assertEquals(5, cycle.activePlots());
        assertEquals(0, cycle.pendingPlots());
        assertEquals(0.0, cycle.progress(), EPS, "waiting plots must never inherit progress");
    }

    @Test
    void loadRepairsCorruptedData() {
        GrowthCycle cycle = new GrowthCycle(2);

        cycle.load(Double.NaN, new int[] {-5}, new int[] {3});
        assertEquals(0.0, cycle.progress(), EPS);
        assertEquals(3, cycle.activePlots(), "pending-only data is promoted");
        assertEquals(0, cycle.group(1).total(), "missing array entries mean empty groups");

        cycle.load(7.5, new int[] {4, 2}, new int[] {1, 0});
        assertEquals(1.0, cycle.progress(), EPS, "progress is clamped to 100%");
        assertTrue(cycle.isHarvestDue());
        assertEquals(6, cycle.activePlots());
        assertEquals(1, cycle.pendingPlots());
    }

    @Test
    void saveAndLoadRoundTrip() {
        GrowthCycle original = new GrowthCycle(1);
        original.setPlots(0, 40, false);
        original.advance(0.37);
        original.setPlots(0, 64, false);

        GrowthCycle restored = new GrowthCycle(1);
        restored.load(original.progress(), original.activeCounts(), original.pendingCounts());

        assertEquals(original.progress(), restored.progress(), EPS);
        assertEquals(40, restored.activePlots());
        assertEquals(24, restored.pendingPlots());
    }

    // --- batched harvests (owner design, step 8) --------------------------------------------------------------------

    @Test
    void nothingToHarvestWhileGrowing() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 64, false);
        cycle.advance(0.99);

        assertEquals(0, cycle.plotsToHarvest());
        cycle.harvestPlots(0, 10); // ignored: no harvest is due
        cycle.advance(0.01);
        assertEquals(64, cycle.plotsToHarvest());
    }

    @Test
    void aDueHarvestCanBeHarvestedInBatches() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 64, false);
        cycle.advance(1.0);

        cycle.harvestPlots(0, 20);
        assertEquals(44, cycle.plotsToHarvest());
        assertTrue(cycle.isHarvestDue(), "the bar stays at 100% while ripe plots wait");
        cycle.advance(0.5);
        assertEquals(44, cycle.plotsToHarvest(), "no growth while plots wait on the plant");

        cycle.harvestPlots(0, 1_000); // never more than what is left
        assertEquals(0, cycle.plotsToHarvest());
        assertTrue(cycle.isHarvestDue(), "completion is the machine's call (held drops may still wait)");

        assertTrue(cycle.completeHarvest());
        assertFalse(cycle.isHarvestDue());
        assertEquals(64, cycle.activePlots());
        assertEquals(0, cycle.group(0).harvested(), "a new cycle starts with nothing harvested");
    }

    @Test
    void seedsAddedMidHarvestWaitForTheNextCycle() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 40, false);
        cycle.advance(1.0);
        cycle.harvestPlots(0, 10);

        cycle.setPlots(0, 64, false);

        assertEquals(30, cycle.plotsToHarvest(), "new plots never join a harvest in progress");
        assertEquals(24, cycle.pendingPlots());
    }

    @Test
    void removingSeedsMidHarvestTakesAlreadyHarvestedPlotsFirst() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 64, false);
        cycle.advance(1.0);
        cycle.harvestPlots(0, 30); // 30 harvested, 34 ripe

        cycle.setPlots(0, 44, false); // 20 seeds taken out: all from the harvested ones
        assertEquals(34, cycle.plotsToHarvest(), "the ripe plots are kept");
        assertEquals(10, cycle.group(0).harvested());

        cycle.setPlots(0, 20, false); // 24 more: the last 10 harvested, then 14 ripe
        assertEquals(20, cycle.plotsToHarvest());
        assertEquals(0, cycle.group(0).harvested());
        assertEquals(20, cycle.activePlots());
    }

    @Test
    void changingThePlantMidHarvestUprootsEverything() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.setPlots(0, 64, false);
        cycle.advance(1.0);
        cycle.harvestPlots(0, 30);

        cycle.setPlots(0, 64, true);

        assertFalse(cycle.isHarvestDue());
        assertEquals(0, cycle.group(0).harvested());
        assertEquals(0.0, cycle.progress(), EPS);
    }

    @Test
    void harvestedCountersSurviveSaveAndLoad() {
        GrowthCycle original = new GrowthCycle(1);
        original.setPlots(0, 64, false);
        original.advance(1.0);
        original.harvestPlots(0, 25);

        GrowthCycle restored = new GrowthCycle(1);
        restored.load(original.progress(), original.activeCounts(), original.pendingCounts(),
                original.harvestedCounts());

        assertEquals(39, restored.plotsToHarvest(), "a reload must never harvest the same plots twice");
    }

    @Test
    void groupsFilledTogetherOnAnEmptyMachineAllStartActive() {
        // Entropic: a pipe fills several plot groups in one go; none of them may wait a cycle.
        GrowthCycle cycle = new GrowthCycle(3);
        cycle.setAllPlots(new int[] {10, 20, 30}, new boolean[3]);

        assertEquals(60, cycle.activePlots());
        assertEquals(0, cycle.pendingPlots());
        assertEquals(0.0, cycle.progress(), EPS);
    }

    @Test
    void aGroupFilledWhileOthersGrowIsPending() {
        GrowthCycle cycle = new GrowthCycle(3);
        cycle.setAllPlots(new int[] {10, 0, 0}, new boolean[3]);
        cycle.advance(0.5);

        cycle.setAllPlots(new int[] {10, 5, 0}, new boolean[3]);

        assertEquals(10, cycle.activePlots());
        assertEquals(5, cycle.pendingPlots());
        assertEquals(0.5, cycle.progress(), EPS, "the running cycle keeps its progress");
    }

    @Test
    void setAllPlotsUprootsAChangedPlantAndKeepsTheOthers() {
        GrowthCycle cycle = new GrowthCycle(2);
        cycle.setAllPlots(new int[] {10, 10}, new boolean[2]);
        cycle.advance(0.5);

        cycle.setAllPlots(new int[] {10, 10}, new boolean[] {false, true});

        assertEquals(10, cycle.activePlots(), "group 0 keeps growing");
        assertEquals(10, cycle.pendingPlots(), "group 1 was replanted with another plant: next cycle");
    }

    @Test
    void setAllPlotsWithOneGroupBehavesLikeSetPlots() {
        GrowthCycle a = new GrowthCycle(1);
        GrowthCycle b = new GrowthCycle(1);
        a.setPlots(0, 40, false);
        b.setAllPlots(new int[] {40}, new boolean[1]);
        a.advance(0.3);
        b.advance(0.3);
        a.setPlots(0, 70, false);
        b.setAllPlots(new int[] {70}, new boolean[1]);
        a.setPlots(0, 55, false);
        b.setAllPlots(new int[] {55}, new boolean[1]);

        assertEquals(a.activePlots(), b.activePlots());
        assertEquals(a.pendingPlots(), b.pendingPlots());
        assertEquals(a.progress(), b.progress(), EPS);
    }

    @Test
    void loadRepairsImpossibleHarvestedCounters() {
        GrowthCycle cycle = new GrowthCycle(1);
        cycle.load(1.0, new int[] {10}, new int[] {0}, new int[] {99});
        assertEquals(10, cycle.group(0).harvested(), "harvested is capped at active");
        assertEquals(0, cycle.plotsToHarvest());

        cycle.load(1.0, new int[] {10}, new int[] {0}, new int[] {-3});
        assertEquals(10, cycle.plotsToHarvest(), "negative harvested becomes 0");

        cycle.load(1.0, new int[] {10}, new int[] {0});
        assertEquals(10, cycle.plotsToHarvest(), "old saves have nothing harvested");
    }
}
