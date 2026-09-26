/*
 * HarvestMathTest — JUnit tests of sim/HarvestMath: stochastic rounding keeps the expected value, success counting
 * matches the probability, edge cases never produce negative or phantom items. Run with `gradlew test`.
 */
package com.virtualfarmworks.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class HarvestMathTest {
    private static final int TRIALS = 200_000;

    @Test
    void wholeNumbersAreNeverRandomized() {
        Random random = new Random(1);
        assertEquals(7, HarvestMath.stochasticRound(7.0, random::nextDouble));
        assertEquals(0, HarvestMath.stochasticRound(0.0, random::nextDouble));
    }

    @Test
    void invalidValuesGiveNothing() {
        Random random = new Random(1);
        assertEquals(0, HarvestMath.stochasticRound(-3.5, random::nextDouble));
        assertEquals(0, HarvestMath.stochasticRound(Double.NaN, random::nextDouble));
    }

    @Test
    void stochasticRoundingKeepsTheExpectedValue() {
        Random random = new Random(42);
        long sum = 0;
        for (int i = 0; i < TRIALS; i++) {
            long rounded = HarvestMath.stochasticRound(10.5, random::nextDouble);
            assertTrue(rounded == 10 || rounded == 11, "only floor or floor + 1");
            sum += rounded;
        }
        assertEquals(10.5, (double) sum / TRIALS, 0.01);
    }

    @Test
    void successCountMatchesTheChance() {
        Random random = new Random(7);
        long successes = HarvestMath.countSuccesses(TRIALS, 0.2, random::nextDouble);
        assertEquals(0.2, (double) successes / TRIALS, 0.005);
    }

    @Test
    void successCountEdgeCases() {
        Random random = new Random(7);
        assertEquals(0, HarvestMath.countSuccesses(100, 0.0, random::nextDouble));
        assertEquals(100, HarvestMath.countSuccesses(100, 1.0, random::nextDouble));
        assertEquals(100, HarvestMath.countSuccesses(100, 3.0, random::nextDouble), "chance capped at 100%");
        assertEquals(0, HarvestMath.countSuccesses(0, 0.5, random::nextDouble));
        assertEquals(0, HarvestMath.countSuccesses(-5, 0.5, random::nextDouble));
    }

    @Test
    void hugeTrialCountsUseTheExpectedValue() {
        Random random = new Random(3);
        long successes = HarvestMath.countSuccesses(10_000_000L, 0.1, random::nextDouble);
        assertEquals(1_000_000L, successes);
    }
}
