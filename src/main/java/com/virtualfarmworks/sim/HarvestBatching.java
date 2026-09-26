/*
 * HarvestBatching — decides how many ripe plots the next harvest batch takes, from the free output slots and the
 * yield measured so far (owner design, step 8: the harvest goes in parts that fit; the rest waits on the plant).
 * Minecraft-free, unit-tested.
 */
package com.virtualfarmworks.sim;

/**
 * Why batches: a harvest is stored all-or-nothing (anti-dupe), so a harvest bigger than the whole output (high
 * multipliers, thousands of plots) could never be stored — the machine would stay OUTPUT FULL forever with an empty
 * buffer. Harvesting only as many plots as fit removes that deadlock without storing anything extra: the plots left
 * out simply stay ripe, their items do not exist yet.
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>Nothing measured yet (new seed, reload): one plot, to learn what a plot yields.</li>
 *   <li>A batch never covers more than {@link #GROWTH_FACTOR} times the plots measured so far, so a lucky or unlucky
 *       small sample cannot produce a huge batch: sizes grow as 1, 2, 6, 18, 54... plots, and a machine that already
 *       harvested a full cycle takes everything at once when it fits.</li>
 *   <li>The expected drops of a batch use at most {@link #SPACE_MARGIN} of the free slots, after keeping one slot per
 *       item type, so the random result practically always fits. If it does not, the machine keeps the rolled batch
 *       and waits for space (no re-roll); only a batch too big even for EMPTY buffers is stored partly and held (the
 *       owner-approved extreme case, see the machine).</li>
 *   <li>At least one plot whenever plots are left: it may still fit in partly filled stacks.</li>
 * </ul>
 * Cost: one batch per tick at most, and more than one batch per cycle only while the output is the bottleneck.
 */
public final class HarvestBatching {
    /** Share of the free slots the expected drops of a batch may fill. */
    static final double SPACE_MARGIN = 0.5;
    /** A batch covers at most this many times the plots measured so far. */
    static final int GROWTH_FACTOR = 2;

    private HarvestBatching() {
    }

    /**
     * @param plotsLeft ripe plots not harvested yet in this cycle
     * @param freeSlots EMPTY output slots right now (visible and hidden buffers; partly filled stacks not counted)
     * @param sample    what the plots yielded so far with the current drop source
     * @return plots for the next batch: {@code 0} only when {@code plotsLeft <= 0}, otherwise {@code 1..plotsLeft}
     */
    public static int batchSize(int plotsLeft, int freeSlots, YieldSample sample) {
        if (plotsLeft <= 0) {
            return 0;
        }
        if (sample.isEmpty()) {
            return 1;
        }
        int byEvidence = (int) Math.min(plotsLeft, Math.max(1L, sample.plots() * GROWTH_FACTOR));
        double perPlot = sample.slotsPerPlot();
        if (!(perPlot > 0.0)) {
            return byEvidence; // the plots yield nothing measurable (e.g. production multiplier 0)
        }
        double budget = (freeSlots - sample.itemTypes()) * SPACE_MARGIN;
        if (budget < perPlot) {
            return 1;
        }
        return (int) Math.min(byEvidence, Math.floor(budget / perPlot));
    }
}
