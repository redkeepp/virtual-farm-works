/*
 * MysticalHarvestTests — game test comparing VFW's reproduction of Mystical Agriculture's drop formula with MA's REAL
 * getDrops (a real farmland is placed in the test world). Catches MA updates that change the formula. References MA
 * classes, so it is only called when MA is installed.
 */
package com.virtualfarmworks.gametest;

import com.blakebr0.mysticalagriculture.api.crop.Crop;
import com.blakebr0.mysticalagriculture.api.crop.CropTier;
import com.blakebr0.mysticalagriculture.api.crop.ICropProvider;
import com.blakebr0.mysticalagriculture.block.InferiumCropBlock;
import com.virtualfarmworks.harvest.DropSource;
import com.virtualfarmworks.harvest.HarvestPlans;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.DropTally.Category;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Statistical comparison, per case, over {@link #SAMPLES} harvested plots: average essence, extra seeds and Fertilized
 * Essence per plot must match between MA's real drops and VFW's {@code MysticalDropSource} (raw, before VFW
 * multipliers, which are 1.0 by default). MA's real drops include the base seed, which VFW treats as the replanting
 * cost, so one seed per plot is subtracted from MA's side before comparing.
 *
 * <p>Tolerance {@link #TOLERANCE} per plot is > 4.5 standard deviations of the difference for the chances involved
 * (<= 50%), so a false failure is practically impossible, while any real formula change (typically 0.1+ per plot) fails.
 */
final class MysticalHarvestTests {
    private static final int SAMPLES = 4000;
    private static final double TOLERANCE = 0.05;
    private static final Identifier FERTILIZED_ESSENCE =
            Identifier.fromNamespaceAndPath("mysticalagriculture", "fertilized_essence");

    private MysticalHarvestTests() {
    }

    static void run(GameTestHelper helper) {
        Item inferiumSeeds = item("mysticalagriculture:inferium_seeds");
        // Inferium crop: essence depends on the farmland tier (tier 2 -> 1.5 average, tier 5 -> exactly 3).
        compare(helper, inferiumSeeds, block("mysticalagriculture:prudentium_farmland"));
        compare(helper, inferiumSeeds, block("mysticalagriculture:supremium_farmland"));

        // A regular tier-1 resource crop: on its own farmland (20% chances), on another essence farmland (10%) and on
        // vanilla farmland (0%).
        Item resourceSeeds = firstTierOneResourceSeed(helper);
        compare(helper, resourceSeeds, CropTier.ONE.getFarmlandBlock());
        compare(helper, resourceSeeds, block("mysticalagriculture:supremium_farmland"));
        compare(helper, resourceSeeds, block("minecraft:farmland"));
        helper.succeed();
    }

    private static void compare(GameTestHelper helper, Item seedItem, Block farmland) {
        ServerLevel level = helper.getLevel();
        BlockPos farmlandPos = helper.absolutePos(BlockPos.ZERO);
        level.setBlock(farmlandPos, farmland.defaultBlockState(), Block.UPDATE_CLIENTS);

        Crop crop = cropOf(seedItem);
        CropBlock cropBlock = crop.getCropBlock();
        BlockState mature = cropBlock.getStateForAge(cropBlock.getMaxAge());
        Item essence = crop.getEssenceItem();
        Item fertilized = BuiltInRegistries.ITEM.getValue(FERTILIZED_ESSENCE);

        // --- MA's real drops: the crop "stands" on the farmland placed above (MA reads origin.below()).
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(farmlandPos.above()))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
        long maEssence = 0;
        long maSeeds = 0;
        long maFertilized = 0;
        for (int i = 0; i < SAMPLES; i++) {
            for (ItemStack stack : mature.getDrops(params)) {
                if (stack.is(essence)) {
                    maEssence += stack.getCount();
                } else if (stack.is(seedItem)) {
                    maSeeds += stack.getCount();
                } else if (stack.is(fertilized)) {
                    maFertilized += stack.getCount();
                }
            }
        }
        maSeeds -= SAMPLES; // the base seed = VFW's replanting cost

        // --- VFW's reproduction, fed with the same farmland as a soil ITEM.
        DropSource source = HarvestPlans.create(new ItemStack(seedItem), new ItemStack(farmland.asItem()));
        helper.assertTrue(source != null, Component.literal("no drop source for " + seedItem));
        DropTally<ItemResource> tally = new DropTally<>();
        source.roll(SAMPLES, new DropSource.Context(level, farmlandPos, level.getRandom(), 64), tally);
        double vfwEssence = tally.raw(ItemResource.of(essence), Category.MAIN);
        double vfwSeeds = tally.raw(ItemResource.of(seedItem), Category.SECONDARY);
        double vfwFertilized = tally.raw(ItemResource.of(fertilized), Category.SECONDARY);

        String label = BuiltInRegistries.ITEM.getKey(seedItem) + " on " + BuiltInRegistries.BLOCK.getKey(farmland);
        assertClose(helper, label + " essence/plot", maEssence, vfwEssence);
        assertClose(helper, label + " extra seeds/plot", maSeeds, vfwSeeds);
        assertClose(helper, label + " fertilized essence/plot", maFertilized, vfwFertilized);
    }

    private static void assertClose(GameTestHelper helper, String what, double maTotal, double vfwTotal) {
        double ma = maTotal / SAMPLES;
        double vfw = vfwTotal / SAMPLES;
        helper.assertTrue(Math.abs(ma - vfw) <= TOLERANCE,
                Component.literal(what + ": Mystical Agriculture " + ma + " vs VFW " + vfw));
    }

    /** A crux-free, enabled tier-1 resource crop that is not Inferium (its drop rules differ). */
    private static Item firstTierOneResourceSeed(GameTestHelper helper) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem) || !(item instanceof ICropProvider provider)) {
                continue;
            }
            Crop crop = provider.getCrop();
            if (crop.getSeedsItem() == item && crop.isEnabled() && crop.getTier() == CropTier.ONE
                    && crop.getCruxBlock() == null && !(crop.getCropBlock() instanceof InferiumCropBlock)) {
                return item;
            }
        }
        helper.fail("no enabled tier-1 Mystical Agriculture resource seed found");
        throw new IllegalStateException("unreachable");
    }

    private static Crop cropOf(Item seedItem) {
        if (seedItem instanceof BlockItem blockItem && blockItem.getBlock() instanceof ICropProvider provider) {
            return provider.getCrop();
        }
        return ((ICropProvider) seedItem).getCrop();
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
    }
}
