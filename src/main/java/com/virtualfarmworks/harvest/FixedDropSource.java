/*
 * FixedDropSource — harvest of plants without a harvest of their own (flowers, grass, vines, aquatic plants...): a
 * fixed number of one item per plot, the plant staying in place. Also backs the fixed_yield data map.
 */
package com.virtualfarmworks.harvest;

import com.virtualfarmworks.plant.VfwTags;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.DropTally.Category;

import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Each harvested plot yields {@code perPlot} of {@code product}; nothing is random, nothing is paid back (the plant
 * stays planted). Owner decision (2026-09-28): plants whose own drops are poor or empty by hand (flowers, grass, ferns,
 * vines, seagrass...) "each yield 10 of themselves per harvest" — config {@code drops.otherPlantYield} — and
 * Torchflower Seeds / Pitcher Pod yield that many flowers (data map {@code virtualfarmworks:fixed_yield}).
 *
 * <p>The product is MAIN (it is what the farm is for) unless it is in {@code #virtualfarmworks:harvest_byproducts}.
 * Costs one tally addition per harvest, whatever the plot count.
 *
 * @param product what each plot yields
 * @param perPlot how many per plot and harvest (0 = nothing)
 */
public record FixedDropSource(ItemResource product, int perPlot) implements DropSource {
    @Override
    public void roll(int plots, Context context, DropTally<ItemResource> tally) {
        if (plots <= 0 || perPlot <= 0 || product.isEmpty()) {
            return;
        }
        Category category = product.typeHolder().is(VfwTags.HARVEST_BYPRODUCTS) ? Category.SECONDARY : Category.MAIN;
        tally.add(product, (double) plots * perPlot, category);
    }
}
