/*
 * HarvestMath — random-number helpers of the aggregated harvest: stochastic rounding (fractional yields without
 * losing or inventing items on average) and counting successes of a per-plot chance. Minecraft-free, unit-tested.
 */
package com.virtualfarmworks.sim;

import java.util.function.DoubleSupplier;

/**
 * Math used when a harvest turns "N plots" into whole items.
 *
 * <p>Randomness is injected as a {@link DoubleSupplier} returning values in {@code [0, 1)} (the machine passes the
 * level's {@code RandomSource::nextDouble}, tests pass a seeded {@code java.util.Random}), which keeps this class free
 * of Minecraft types.
 */
public final class HarvestMath {
    /**
     * Above this many trials, {@link #countSuccesses} stops rolling one random number per plot and uses the expected
     * value instead (with stochastic rounding). Far above any planned machine (Entropic ~6,000 plots): it only bounds
     * the cost if a pack configures something absurd.
     */
    static final long MAX_EXACT_TRIALS = 65_536;

    private HarvestMath() {
    }

    /**
     * Rounds {@code value} to {@code floor(value)} or {@code floor(value) + 1}, choosing the upper one with probability
     * equal to the fractional part. The EXPECTED result is exactly {@code value}, so multipliers such as 1.5x or 0.25x
     * are fair over time without ever producing fractional items. Non-positive and NaN values give 0.
     */
    public static long stochasticRound(double value, DoubleSupplier random) {
        if (!(value > 0.0)) {
            return 0L;
        }
        double floor = Math.floor(value);
        long result = (long) floor;
        double fraction = value - floor;
        if (fraction > 0.0 && random.getAsDouble() < fraction) {
            result++;
        }
        return result;
    }

    /**
     * How many of {@code trials} independent plots succeed a roll with probability {@code chance} (a binomial draw).
     * Used for per-plot chances such as Mystical Agriculture's extra essence/seed and Fertilized Essence: one cheap
     * random number per plot instead of a loot-table evaluation.
     */
    public static long countSuccesses(long trials, double chance, DoubleSupplier random) {
        if (trials <= 0 || !(chance > 0.0)) {
            return 0L;
        }
        if (chance >= 1.0) {
            return trials;
        }
        if (trials > MAX_EXACT_TRIALS) {
            return stochasticRound(trials * chance, random);
        }
        long successes = 0;
        for (long i = 0; i < trials; i++) {
            if (random.getAsDouble() < chance) {
                successes++;
            }
        }
        return successes;
    }
}
