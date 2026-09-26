/*
 * DropTally — accumulates the drops of one aggregated harvest per item and per category (main product / secondary),
 * then applies the pack-maker multipliers and rounds to whole items. Minecraft-free (generic key), unit-tested.
 */
package com.virtualfarmworks.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

/**
 * Sum of a harvest's drops, keyed by item ({@code K} is {@code ItemResource} in the game; any key in tests).
 *
 * <h2>Categories (owner spec: "secondary drops multiplier, separate from the main drop")</h2>
 * <ul>
 *   <li>{@link Category#MAIN} — what the farm is for (wheat, carrots, essences...). Scaled by the production
 *       multipliers ({@code drops.productionMultiplier} x {@code machines.<tier>.productionMultiplier}).</li>
 *   <li>{@link Category#SECONDARY} — extra seeds and by-products (Fertilized Essence, poisonous potato...). Scaled by
 *       {@code drops.secondaryDropMultiplier}.</li>
 * </ul>
 * Deciding which drop is which is the drop source's job (see {@code harvest.LootDropSource}); this class only adds up.
 *
 * <h2>Why doubles</h2>
 * Amounts may be fractional before {@link #finish}: a machine that samples 64 loot rolls for 100 plots adds each roll
 * with weight 100/64. Rounding happens once, at the end, with {@link HarvestMath#stochasticRound}, so the expected
 * yield is exact and no item is invented or lost on average.
 *
 * <p>Insertion order is kept (LinkedHashMap) so harvests fill the output buffer in a stable, readable order.
 */
public final class DropTally<K> {
    public enum Category {
        MAIN,
        SECONDARY
    }

    /** One finished drop: an item and a whole, positive amount. */
    public record Entry<K>(K key, long amount) {
    }

    /** key -> {main amount, secondary amount}. */
    private final Map<K, double[]> amounts = new LinkedHashMap<>();

    /** Adds {@code amount} of {@code key}. Non-positive and NaN amounts are ignored. */
    public void add(K key, double amount, Category category) {
        if (!(amount > 0.0)) {
            return;
        }
        amounts.computeIfAbsent(key, k -> new double[2])[category.ordinal()] += amount;
    }

    /** Raw (pre-multiplier, pre-rounding) amount of one key in one category. Mainly for tests. */
    public double raw(K key, Category category) {
        double[] values = amounts.get(key);
        return values == null ? 0.0 : values[category.ordinal()];
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    /**
     * Applies the category multipliers and rounds each item's total to a whole amount (expected value preserved).
     * Items that end at 0 are left out, so the result only holds things to insert.
     */
    public List<Entry<K>> finish(double mainMultiplier, double secondaryMultiplier, DoubleSupplier random) {
        List<Entry<K>> result = new ArrayList<>(amounts.size());
        for (Map.Entry<K, double[]> entry : amounts.entrySet()) {
            double[] values = entry.getValue();
            double total = values[Category.MAIN.ordinal()] * mainMultiplier
                    + values[Category.SECONDARY.ordinal()] * secondaryMultiplier;
            long amount = HarvestMath.stochasticRound(total, random);
            if (amount > 0) {
                result.add(new Entry<>(entry.getKey(), amount));
            }
        }
        return result;
    }
}
