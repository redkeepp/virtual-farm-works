/*
 * GrowthSpeedTest — JUnit tests of sim/GrowthSpeed: the speed formula checked against the owner's examples.
 * Run with `gradlew test`.
 */
package com.virtualfarmworks.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Speed formula (owner decisions: multiplicative; Water Provider removes a penalty; +50% per Growth upgrade). */
class GrowthSpeedTest {
    private static final double EPS = 1e-12;

    private static double speed(boolean water, int upgrades, double soil) {
        return GrowthSpeed.of(water, 0.25, upgrades, 0.5, soil).total();
    }

    @Test
    void pureMachineIsOnePointZero() {
        assertEquals(1.0, speed(true, 0, 1.0), EPS);
    }

    @Test
    void withoutWaterProviderTheMachineIsSlower() {
        assertEquals(0.25, speed(false, 0, 1.0), EPS);
    }

    @Test
    void growthUpgradesAddFiftyPercentEach() {
        assertEquals(1.5, speed(true, 1, 1.0), EPS);
        assertEquals(3.0, speed(true, 4, 1.0), EPS);
    }

    @Test
    void ownerExampleNoWaterWithFourUpgradesIsZeroPointSevenFive() {
        assertEquals(0.75, speed(false, 4, 1.0), EPS);
    }

    @Test
    void soilBonusMultiplies() {
        assertEquals(1.35, speed(true, 0, 1.35), EPS);   // Supremium farmland
        assertEquals(4.2, speed(true, 4, 1.40), 1e-9);   // Insanium + 4 upgrades
    }

    @Test
    void configuredBonusAndStackedUpgradesAreRespected() {
        // Pack maker: 3 upgrades per slot (12 total) at +25% each.
        assertEquals(4.0, GrowthSpeed.of(true, 0.25, 12, 0.25, 1.0).total(), EPS);
    }

    @Test
    void progressPerTickMakesOneCycleLastGrowthTicks() {
        assertEquals(1.0 / 600.0, GrowthSpeed.BASELINE.progressPerTick(600), EPS);
        assertEquals(3.0 / 600.0, new GrowthSpeed(1.0, 3.0, 1.0).progressPerTick(600), EPS);
    }

    @Test
    void invalidGrowthTicksNeverDivideByZero() {
        assertEquals(0.0, GrowthSpeed.BASELINE.progressPerTick(0), EPS);
        assertEquals(0.0, GrowthSpeed.BASELINE.progressPerTick(-5), EPS);
    }
}
