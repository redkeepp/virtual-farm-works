/*
 * DropTallyTest — JUnit tests of sim/DropTally: categories get their own multipliers, weighted samples add up,
 * rounding preserves the expected yield, zero results are dropped, order is stable and filtered keys are never
 * counted. Run with `gradlew test`.
 */
package com.virtualfarmworks.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.virtualfarmworks.sim.DropTally.Category;

class DropTallyTest {
    @Test
    void filteredKeysAreNeverCounted() {
        // Harvest filter (owner, step 8): what the filter rejects never becomes an item, it is not produced then deleted.
        DropTally<String> tally = new DropTally<>(key -> !key.equals("seed"));
        tally.add("wheat", 10, Category.MAIN);
        tally.add("seed", 5, Category.SECONDARY);

        assertFalse(tally.allows("seed"));
        assertEquals(0.0, tally.raw("seed", Category.SECONDARY));
        List<DropTally.Entry<String>> finished = tally.finish(1.0, 1.0, () -> 0.5);
        assertEquals(1, finished.size());
        assertEquals("wheat", finished.get(0).key());
    }


    @Test
    void defaultMultipliersKeepWholeAmountsExact() {
        DropTally<String> tally = new DropTally<>();
        tally.add("wheat", 10, Category.MAIN);
        tally.add("wheat_seeds", 17, Category.SECONDARY);

        List<DropTally.Entry<String>> result = tally.finish(1.0, 1.0, new Random(1)::nextDouble);

        assertEquals(List.of(new DropTally.Entry<>("wheat", 10), new DropTally.Entry<>("wheat_seeds", 17)), result);
    }

    @Test
    void eachCategoryUsesItsOwnMultiplier() {
        DropTally<String> tally = new DropTally<>();
        tally.add("essence", 40, Category.MAIN);
        tally.add("fertilized_essence", 40, Category.SECONDARY);

        List<DropTally.Entry<String>> result = tally.finish(2.0, 0.0, new Random(1)::nextDouble);

        assertEquals(List.of(new DropTally.Entry<>("essence", 80)), result, "secondary x0 disappears entirely");
    }

    @Test
    void sameItemInBothCategoriesIsSummed() {
        DropTally<String> tally = new DropTally<>();
        tally.add("potato", 10, Category.MAIN);
        tally.add("potato", 4, Category.SECONDARY);

        assertEquals(List.of(new DropTally.Entry<>("potato", 10 * 3 + 4 * 2)),
                tally.finish(3.0, 2.0, new Random(1)::nextDouble));
    }

    @Test
    void weightedSamplesAreFairOnAverage() {
        // 100 plots represented by 64 rolls: each roll weighs 100/64. One wheat per roll -> 100 wheat expected.
        double weight = 100.0 / 64.0;
        long total = 0;
        int harvests = 20_000;
        Random random = new Random(9);
        for (int h = 0; h < harvests; h++) {
            DropTally<String> tally = new DropTally<>();
            for (int roll = 0; roll < 64; roll++) {
                tally.add("wheat", 1 * weight, Category.MAIN);
            }
            total += tally.finish(1.0, 1.0, random::nextDouble).getFirst().amount();
        }
        assertEquals(100.0, (double) total / harvests, 0.01);
    }

    @Test
    void fractionalMultipliersAreFairOnAverage() {
        Random random = new Random(5);
        long total = 0;
        int harvests = 50_000;
        for (int h = 0; h < harvests; h++) {
            DropTally<String> tally = new DropTally<>();
            tally.add("carrot", 7, Category.MAIN);
            total += tally.finish(0.75, 1.0, random::nextDouble).stream().mapToLong(DropTally.Entry::amount).sum();
        }
        assertEquals(5.25, (double) total / harvests, 0.02);
    }

    @Test
    void invalidAmountsAreIgnored() {
        DropTally<String> tally = new DropTally<>();
        tally.add("x", 0, Category.MAIN);
        tally.add("x", -5, Category.MAIN);
        tally.add("x", Double.NaN, Category.MAIN);

        assertTrue(tally.isEmpty());
        assertTrue(tally.finish(1.0, 1.0, new Random(1)::nextDouble).isEmpty());
    }

    @Test
    void rawAmountsAreReadableForTests() {
        DropTally<String> tally = new DropTally<>();
        tally.add("seed", 2.5, Category.SECONDARY);
        assertEquals(2.5, tally.raw("seed", Category.SECONDARY), 1e-12);
        assertEquals(0.0, tally.raw("seed", Category.MAIN), 1e-12);
        assertEquals(0.0, tally.raw("missing", Category.MAIN), 1e-12);
    }
}
