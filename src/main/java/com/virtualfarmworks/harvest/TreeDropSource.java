/*
 * TreeDropSource — harvest drops of saplings, azaleas and nether fungi: a few trees are grown in memory and every block
 * of them is "broken by hand" through its loot table, scaled to the number of plots. The tree stays planted.
 */
package com.virtualfarmworks.harvest;

import java.util.List;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.plant.VfwTags;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.DropTally.Category;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * What a tree plot yields per harvest: the drops of a whole real tree broken by hand (owner, 2026-09-28), and the tree
 * stays planted — it never takes its own saplings back (owner: the Starter has no replanting).
 *
 * <h2>How one harvest is computed</h2>
 * <ol>
 *   <li>Grow {@code min(plots, maxTrees)} trees with {@link TreeGrowth} (config
 *       {@code performance.maxTreesGrownPerHarvest}). Each grown tree stands for {@code plots / grown} trees, like a
 *       loot roll of {@link LootDropSource} stands for several plots: the expected yield is exact.</li>
 *   <li>Count their blocks by state. Blocks with a block entity are skipped: hives, and "core" blocks of modded magic
 *       trees, which would otherwise be duplicated every cycle.</li>
 *   <li>Break them by hand: each block state's loot table is rolled with an empty tool (so no shears: leaves give
 *       saplings, sticks and apples; logs give logs), at most {@code maxLootRolls} rolls in total, shared out by block
 *       count and scaled (see {@link #rollBlocks}).</li>
 *   <li>Classify: saplings ({@code #minecraft:saplings}, the tree's "seeds") and
 *       {@code #virtualfarmworks:harvest_byproducts} are SECONDARY; logs, apples, sticks and the rest are MAIN.</li>
 * </ol>
 *
 * @param growth   how the plant grows into a tree
 * @param ground   what the tree stands on (the soil, or the nylium a fungus requires)
 * @param maxTrees trees grown per harvest at most (config, read on revalidation)
 */
public record TreeDropSource(TreeGrowth growth, BlockState ground, int maxTrees) implements DropSource {

    /** Logged once per session so a broken modded loot table cannot spam the log every harvest. */
    private static volatile boolean warnedAboutLoot;

    @Override
    public void roll(int plots, Context context, DropTally<ItemResource> tally) {
        if (plots <= 0) {
            return;
        }
        int trees = Math.max(1, Math.min(plots, maxTrees));
        Object2IntMap<BlockState> blocks = new Object2IntLinkedOpenHashMap<>();
        int grown = 0;
        for (int i = 0; i < trees; i++) {
            Object2IntMap<BlockState> tree = growth.grow(context.level(), ground, context.random());
            if (!tree.isEmpty()) {
                grown++;
                for (Object2IntMap.Entry<BlockState> block : tree.object2IntEntrySet()) {
                    if (!block.getKey().hasBlockEntity()) {
                        blocks.mergeInt(block.getKey(), block.getIntValue(), Integer::sum);
                    }
                }
            }
        }
        if (grown > 0) {
            // A growth that failed (a feature's own random refusal) is left out rather than counted as a tree that
            // yields nothing: virtual plots have ideal conditions, so every plot grows a tree.
            rollBlocks(blocks, (double) plots / grown, context, tally);
        }
    }

    /**
     * Rolls the loot of every counted block. {@code blocksPerCount} = real trees per grown tree. The roll budget
     * ({@code maxLootRolls}) is shared by block count: a state with a tenth of the blocks gets about a tenth of the
     * rolls, at least one, and never more rolls than it has blocks; each roll then stands for
     * {@code blocks / rolls} blocks.
     */
    private static void rollBlocks(Object2IntMap<BlockState> blocks, double blocksPerCount, Context context,
                                   DropTally<ItemResource> tally) {
        long counted = 0;
        for (int count : blocks.values()) {
            counted += count;
        }
        if (counted == 0) {
            return;
        }
        int budget = Math.max(1, context.maxLootRolls());
        LootParams.Builder params = new LootParams.Builder(context.level())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(context.machinePos()))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);

        for (Object2IntMap.Entry<BlockState> block : blocks.object2IntEntrySet()) {
            double realBlocks = block.getIntValue() * blocksPerCount;
            int rolls = (int) Math.round((double) budget * block.getIntValue() / counted);
            rolls = Math.clamp(rolls, 1, Math.max(1, (int) Math.ceil(realBlocks)));
            double weight = realBlocks / rolls;
            for (int i = 0; i < rolls; i++) {
                List<ItemStack> drops;
                try {
                    drops = block.getKey().getDrops(params);
                } catch (RuntimeException e) {
                    if (!warnedAboutLoot) {
                        warnedAboutLoot = true;
                        VirtualFarmWorks.LOGGER.warn("Loot of {} failed in a Farm Matrix tree harvest; that block "
                                + "yields nothing. This is logged once.", block.getKey(), e);
                    }
                    break;
                }
                for (ItemStack stack : drops) {
                    if (!stack.isEmpty()) {
                        Category category = stack.is(ItemTags.SAPLINGS) || stack.is(VfwTags.HARVEST_BYPRODUCTS)
                                ? Category.SECONDARY
                                : Category.MAIN;
                        tally.add(ItemResource.of(stack), stack.getCount() * weight, category);
                    }
                }
            }
        }
    }
}
