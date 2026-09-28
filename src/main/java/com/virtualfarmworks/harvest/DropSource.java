/*
 * DropSource — contract for "what does harvesting N plots of this plant produce": the generic loot-table source
 * (LootDropSource) and the Mystical Agriculture source (compat) both implement it.
 */
package com.virtualfarmworks.harvest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import com.virtualfarmworks.sim.DropTally;

/**
 * Produces the RAW drops of harvesting a number of plots of one plant on one soil, before VFW's multipliers
 * ({@link Harvester} applies those). Implementations are immutable and built once per machine revalidation by
 * {@link HarvestPlans}; they are only called at harvest time, never per tick.
 *
 * <p>Contract for implementations:
 * <ul>
 *   <li>Add drops for exactly {@code plots} harvested plots, with the replanting cost already paid (a physical farm
 *       replants with one seed of each harvested crop; in VFW the plot keeps its seed, so that seed must not be output).
 *   <li>Classify each drop as MAIN (the product) or SECONDARY (extra seeds, by-products).</li>
 *   <li>Never touch the world, never spawn entities, never use a player (performance and safety rules).</li>
 * </ul>
 */
public interface DropSource {
    void roll(int plots, Context context, DropTally<ItemResource> tally);

    /**
     * Where and how a harvest happens.
     *
     * @param level        the machine's level (loot tables need a ServerLevel)
     * @param machinePos   position used as the loot origin (only read by location-based loot conditions)
     * @param random       randomness source (the level's)
     * @param maxLootRolls config {@code performance.maxLootRollsPerHarvest}: upper bound of loot-table evaluations per
     *                     harvest, see {@link LootDropSource}
     * @param fertilizedEssence the machine's Fertilized Essence switch (GUI button): when false, Mystical Agriculture
     *                     crops never produce Fertilized Essence. No effect on other plants.
     * @param filter       the machine's harvest filter (whitelist/blacklist). {@link Harvester#roll} builds the tally
     *                     with it, so rejected items are never produced; sources may also skip computing them.
     */
    record Context(ServerLevel level, BlockPos machinePos, RandomSource random, int maxLootRolls,
                   boolean fertilizedEssence, HarvestFilter filter) {

        /** Without a harvest filter (tests, tools). */
        public Context(ServerLevel level, BlockPos machinePos, RandomSource random, int maxLootRolls,
                       boolean fertilizedEssence) {
            this(level, machinePos, random, maxLootRolls, fertilizedEssence, HarvestFilter.NONE);
        }
    }
}
