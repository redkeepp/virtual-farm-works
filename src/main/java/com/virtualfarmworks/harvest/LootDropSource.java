/*
 * LootDropSource — generic harvest drops for vanilla and modded plants: evaluates the plant's own block loot table
 * (datapacks and NeoForge global loot modifiers included) without entities or world access, sampled and scaled for
 * large machines.
 */
package com.virtualfarmworks.harvest;

import java.util.List;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.plant.VfwTags;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.DropTally.Category;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Harvest drops taken from the loot table of {@link #lootState}, i.e. exactly what breaking that block would drop —
 * which is how VFW supports vanilla crops and most modded crops without per-mod code.
 *
 * <h2>How one harvest is computed</h2>
 * <ol>
 *   <li>Roll the loot table once per plot, but at most {@code maxLootRolls} times. With more plots than that (big
 *       tiers), each roll stands for {@code plots / rolls} plots: the expected yield is exact, only the per-harvest
 *       randomness is coarser. This caps the harvest cost of a 6,000-plot machine at a few dozen loot evaluations.</li>
 *   <li>Pay the replanting cost: when {@link #replantCost} is true, one planting item is removed from each roll (a real
 *       farm replants each harvested crop with one of its seeds). Plants that stay in place (stems, sugar cane, berry
 *       bushes...) have no replanting cost.</li>
 *   <li>Classify: the planting item is SECONDARY ("extra seeds") when it is in {@code #c:seeds}, otherwise it IS the
 *       product (carrot, potato, cocoa...) and stays MAIN; items in {@code #virtualfarmworks:harvest_byproducts} are
 *       SECONDARY; everything else is MAIN.</li>
 * </ol>
 *
 * <h2>Loot context</h2>
 * {@code ORIGIN} = the machine's position and {@code TOOL} = EMPTY. The empty tool is deliberate (owner rule): the hoe's
 * tier and enchantments must never change the yield, so Fortune does not apply. No entity, no block entity.
 *
 * @param lootState         the block state whose drops represent one harvested plot (mature crop, fruit, segment)
 * @param plantingItem      the item in the seed slot, without components (matches the dropped seed)
 * @param replantCost       remove one planting item per harvested plot
 * @param plantingItemIsSeed the planting item is an extra seed (SECONDARY) rather than the product (MAIN)
 */
public record LootDropSource(BlockState lootState, ItemResource plantingItem, boolean replantCost,
                             boolean plantingItemIsSeed) implements DropSource {

    /** Logged once per session so a broken modded loot table cannot spam the log every harvest. */
    private static volatile boolean warnedAboutLoot;

    @Override
    public void roll(int plots, Context context, DropTally<ItemResource> tally) {
        if (plots <= 0) {
            return;
        }
        int rolls = Math.max(1, Math.min(plots, context.maxLootRolls()));
        double weight = (double) plots / rolls;

        // One builder for every roll: BlockState#getDrops only adds the BLOCK_STATE parameter to it, which is the same
        // on every call.
        LootParams.Builder params = new LootParams.Builder(context.level())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(context.machinePos()))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);

        for (int i = 0; i < rolls; i++) {
            List<ItemStack> drops;
            try {
                drops = lootState.getDrops(params);
            } catch (RuntimeException e) {
                if (!warnedAboutLoot) {
                    warnedAboutLoot = true;
                    VirtualFarmWorks.LOGGER.warn("Loot of {} failed in a Farm Matrix harvest; that harvest yields nothing. "
                            + "This is logged once.", lootState, e);
                }
                return;
            }
            addRoll(drops, weight, tally);
        }
    }

    /** Adds one plot's drops (already weighted), paying the replanting cost and classifying each item. */
    private void addRoll(List<ItemStack> drops, double weight, DropTally<ItemResource> tally) {
        boolean replanted = !replantCost;
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) {
                continue;
            }
            int count = stack.getCount();
            ItemResource resource = ItemResource.of(stack);
            boolean isPlantingItem = resource.equals(plantingItem);
            if (isPlantingItem && !replanted) {
                count--;          // one seed goes back into the ground
                replanted = true;
            }
            if (count <= 0) {
                continue;
            }
            Category category = (isPlantingItem && plantingItemIsSeed) || stack.is(VfwTags.HARVEST_BYPRODUCTS)
                    ? Category.SECONDARY
                    : Category.MAIN;
            tally.add(resource, count * weight, category);
        }
    }
}
