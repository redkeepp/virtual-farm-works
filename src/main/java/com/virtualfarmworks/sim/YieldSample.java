/*
 * YieldSample — running average of what one plot yields, measured in output slots, for the drop source a machine is
 * using. Only used to size harvest batches (HarvestBatching); never saved. Minecraft-free, unit-tested.
 */
package com.virtualfarmworks.sim;

/**
 * Filled from the batches the machine really rolls (no extra loot evaluations): after each roll the machine records
 * how many plots it covered, how many output slots the drops would fill ({@code amount / max stack size}, summed over
 * the items, fractional) and how many different items came out.
 *
 * <p>The machine resets it whenever what the plots yield may change (seed, soil, config, tags, Fertilized Essence
 * switch). After a reload it starts empty and relearns within a few batches.
 */
public final class YieldSample {
    private long plots;
    private double slots;
    private int itemTypes;

    /** Adds one rolled batch: {@code plots} plots whose drops fill {@code slots} output slots, {@code itemTypes} items. */
    public void record(int plots, double slots, int itemTypes) {
        if (plots <= 0 || !(slots >= 0.0)) { // !(x >= 0) also rejects NaN
            return;
        }
        this.plots += plots;
        this.slots += slots;
        this.itemTypes = Math.max(this.itemTypes, itemTypes);
    }

    public void reset() {
        plots = 0;
        slots = 0.0;
        itemTypes = 0;
    }

    public boolean isEmpty() {
        return plots == 0;
    }

    /** Plots measured so far. */
    public long plots() {
        return plots;
    }

    /** Average output slots one plot fills (0 when nothing was measured or the plots yield nothing). */
    public double slotsPerPlot() {
        return plots == 0 ? 0.0 : slots / plots;
    }

    /** Most different items seen in one batch: each needs at least one slot of its own. */
    public int itemTypes() {
        return itemTypes;
    }

    @Override
    public String toString() {
        return "YieldSample[plots=" + plots + ", slotsPerPlot=" + slotsPerPlot() + ", itemTypes=" + itemTypes + "]";
    }
}
