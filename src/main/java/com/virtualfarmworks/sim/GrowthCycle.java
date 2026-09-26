/*
 * GrowthCycle — the single global growth cycle of a Farm Matrix: progress bar, plot groups (ACTIVE/PENDING counters),
 * batched harvests and the anti-exploit rules. Part of the Minecraft-free simulation core (package sim), unit-tested in
 * src/test/java.
 */
package com.virtualfarmworks.sim;

import java.util.Arrays;

/**
 * The single global growth cycle of one Farm Matrix. Pure Java: this package must never import Minecraft classes, so
 * it can be unit-tested in milliseconds (see {@code src/test/java}).
 *
 * <h2>Model (owner spec, see CLAUDE.md)</h2>
 * <ul>
 *   <li>ONE progress value per machine, {@code 0.0 .. 1.0}. Every plant in the machine completes the cycle together;
 *       natural growth times of vanilla/modded crops are deliberately ignored.</li>
 *   <li>Plots live in {@link PlotGroup}s that only count ACTIVE and PENDING plots.</li>
 *   <li>At 100%: ACTIVE plots are harvested (by the machine, see {@link #completeHarvest()}), PENDING plots only become
 *       ACTIVE, and the bar starts again.</li>
 *   <li>Batched harvests (owner design, step 8): when the output cannot take the whole harvest, the machine harvests
 *       it in parts ({@link #harvestPlots}); the other ripe plots wait on the plant ({@link #plotsToHarvest()}) and the
 *       bar stays at 100% until the machine completes the cycle.</li>
 * </ul>
 *
 * <h2>Performance</h2>
 * {@link #advance(double)} — the only per-tick call — is O(1): one comparison and one addition, regardless of how
 * many plots the machine holds (1 or 6,000 cost the same). Loops over groups only happen when plots change or a
 * harvest completes, which is rare.
 *
 * <h2>Rules that close exploits</h2>
 * <ul>
 *   <li>Plots added while the machine already has plots enter as PENDING.</li>
 *   <li>A machine that becomes EMPTY (0 plots) resets progress to 0, so "remove everything at 90%, put it back" cannot
 *       create active plots at 90%. The first plots of an empty machine enter as ACTIVE at 0%.</li>
 *   <li>Changing the plant type of a group uproots the whole group (as if emptied first).</li>
 *   <li>If no ACTIVE plot is left but PENDING ones remain (only possible with several groups), the pending plots are
 *       promoted and progress restarts at 0: they start a fresh cycle, they never inherit progress.</li>
 * </ul>
 */
public final class GrowthCycle {
    /**
     * Tolerance for "the bar reached 100%". Progress is a sum of doubles such as 1/600, and 600 x (1/600) is not
     * exactly 1.0 in binary floating point (it can land at 0.9999999999999). Without this, a 30 s cycle would take 601
     * ticks. 1e-9 is far smaller than any realistic per-tick delta, so it only absorbs rounding error.
     */
    static final double DUE_EPSILON = 1e-9;

    /**
     * Largest progress carried into the next cycle. After a harvest the overshoot beyond 100% is kept (so the average
     * cycle length is exactly {@code growthTicks / speed}, not rounded up to whole ticks), but it is capped BELOW the
     * "due" threshold ({@code 1 - DUE_EPSILON}) so that a harvest can never be due right after completing one. Result:
     * at most ONE harvest per tick, even with absurd configs (e.g. growthTicks = 1 and high speed), which keeps the
     * harvest cost per tick bounded — a lag-prevention guarantee.
     */
    static final double MAX_CARRY = 1.0 - 2 * DUE_EPSILON;

    private final PlotGroup[] groups;
    private double progress;
    /** Sum of every group's total; cached because {@link #advance(double)} reads it every tick. */
    private int totalPlots;

    public GrowthCycle(int groupCount) {
        if (groupCount < 1) {
            throw new IllegalArgumentException("A machine needs at least one plot group");
        }
        this.groups = new PlotGroup[groupCount];
        for (int i = 0; i < groupCount; i++) {
            groups[i] = new PlotGroup();
        }
    }

    // --- per-tick ---------------------------------------------------------------------------------------------------

    /**
     * Advances the cycle by {@code delta} (= speed / growthTicks, precomputed by the machine when it revalidates).
     * Does nothing when the machine has no plots, when the delta is not positive, or while a harvest is due: a due
     * harvest that cannot be stored (OUTPUT FULL) keeps the bar at 100% until {@link #completeHarvest()} succeeds.
     */
    public void advance(double delta) {
        if (totalPlots == 0 || !(delta > 0.0) || isHarvestDue()) { // !(x > 0) also rejects NaN
            return;
        }
        progress += delta;
    }

    /** Whether the bar reached 100% and the machine should harvest now. */
    public boolean isHarvestDue() {
        return progress >= 1.0 - DUE_EPSILON;
    }

    /**
     * ACTIVE plots of the due harvest that are not harvested yet (the ripe plots waiting for output space); 0 while no
     * harvest is due.
     */
    public int plotsToHarvest() {
        if (!isHarvestDue()) {
            return 0;
        }
        int sum = 0;
        for (PlotGroup group : groups) {
            sum += group.unharvested();
        }
        return sum;
    }

    /** Same as {@link #plotsToHarvest()}, for one group. */
    public int plotsToHarvest(int groupIndex) {
        return isHarvestDue() ? groups[groupIndex].unharvested() : 0;
    }

    /**
     * Records that the machine stored the harvest of {@code count} more plots of a group (one batch, see the class
     * doc). Ignored while no harvest is due; never counts more plots than are left. The cycle does NOT complete here:
     * the machine calls {@link #completeHarvest()} once nothing is left to harvest or to store.
     */
    public void harvestPlots(int groupIndex, int count) {
        if (isHarvestDue()) {
            groups[groupIndex].harvest(count);
        }
    }

    /**
     * Closes the cycle AFTER the machine has successfully stored the harvest of every ACTIVE plot, in one go or in
     * batches (the harvest itself is transactional and happens outside this class). PENDING plots become ACTIVE, the
     * harvested counters reset and the bar restarts, keeping the small overshoot (see {@link #MAX_CARRY}).
     *
     * @return false (and changes nothing) when no harvest was due — protects against double completion.
     */
    public boolean completeHarvest() {
        if (!isHarvestDue()) {
            return false;
        }
        for (PlotGroup group : groups) {
            group.promotePending();
        }
        // Clamp at 0 too: a harvest due "by epsilon" (progress 0.9999999999) must not leave a negative remainder.
        progress = Math.clamp(progress - 1.0, 0.0, MAX_CARRY);
        return true;
    }

    // --- plot changes (rare: slot changes, revalidation) ------------------------------------------------------------

    /**
     * Sets how many plots a group has, applying the ACTIVE/PENDING rules. The machine calls this whenever the seed or
     * soil slot of the group changes; {@code plots} is the effective plot count ({@code min(seeds, soils)}).
     *
     * @param plantChanged the group's plant type differs from the one its counters refer to (e.g. wheat swapped for
     *                     carrots): the old plots are uprooted before the new count is applied.
     */
    public void setPlots(int groupIndex, int plots, boolean plantChanged) {
        PlotGroup group = groups[groupIndex];
        int target = Math.max(0, plots);
        if (plantChanged) {
            group.clear();
        }
        int current = group.total();
        if (target < current) {
            group.remove(current - target); // pending first
        } else if (target > current) {
            int added = target - current;
            if (sumTotals() == 0) {
                // First plots of an empty machine: eligible right away, fresh cycle.
                progress = 0.0;
                group.addActive(added);
            } else {
                // A cycle is (or may be) running: these plots must wait for the next one.
                group.addPending(added);
            }
        }
        normalize();
    }

    /** {@link #load(double, int[], int[], int[])} with nothing harvested yet (saves from before batched harvests). */
    public void load(double savedProgress, int[] active, int[] pending) {
        load(savedProgress, active, pending, new int[0]);
    }

    /**
     * Restores saved state (NBT). Corrupted values are repaired instead of crashing: negative counts become 0, a
     * non-finite progress becomes 0, progress is clamped to {@code 0..1}, harvested never exceeds active. Arrays
     * shorter than the group count leave the missing groups empty (e.g. a machine saved by an older version with fewer
     * groups).
     */
    public void load(double savedProgress, int[] active, int[] pending, int[] harvested) {
        for (int i = 0; i < groups.length; i++) {
            groups[i].set(i < active.length ? active[i] : 0, i < pending.length ? pending[i] : 0,
                    i < harvested.length ? harvested[i] : 0);
        }
        progress = Double.isFinite(savedProgress) ? Math.clamp(savedProgress, 0.0, 1.0) : 0.0;
        normalize();
    }

    // --- read-only view ---------------------------------------------------------------------------------------------

    /** Progress of the current cycle, {@code 0.0 .. 1.0} (may be slightly above 1.0 while a harvest is due). */
    public double progress() {
        return progress;
    }

    public int groupCount() {
        return groups.length;
    }

    public PlotGroup group(int index) {
        return groups[index];
    }

    public int totalPlots() {
        return totalPlots;
    }

    /** Plots that produce at the next harvest. */
    public int activePlots() {
        int sum = 0;
        for (PlotGroup group : groups) {
            sum += group.active();
        }
        return sum;
    }

    /** Plots waiting for the next cycle. */
    public int pendingPlots() {
        int sum = 0;
        for (PlotGroup group : groups) {
            sum += group.pending();
        }
        return sum;
    }

    /** Saved counters, group by group (for NBT). */
    public int[] activeCounts() {
        return Arrays.stream(groups).mapToInt(PlotGroup::active).toArray();
    }

    public int[] pendingCounts() {
        return Arrays.stream(groups).mapToInt(PlotGroup::pending).toArray();
    }

    public int[] harvestedCounts() {
        return Arrays.stream(groups).mapToInt(PlotGroup::harvested).toArray();
    }

    // --- internals --------------------------------------------------------------------------------------------------

    private int sumTotals() {
        int sum = 0;
        for (PlotGroup group : groups) {
            sum += group.total();
        }
        return sum;
    }

    /** Re-establishes the machine-wide invariants after any change and refreshes the cached total. */
    private void normalize() {
        totalPlots = sumTotals();
        if (totalPlots == 0) {
            progress = 0.0; // an empty machine never keeps progress
            return;
        }
        if (activePlots() == 0) {
            // Nothing is growing, only waiting plots: they start a fresh cycle instead of inheriting progress.
            for (PlotGroup group : groups) {
                group.promotePending();
            }
            progress = 0.0;
        }
    }

    @Override
    public String toString() {
        return "GrowthCycle[progress=" + progress + ", groups=" + Arrays.toString(groups) + "]";
    }
}
