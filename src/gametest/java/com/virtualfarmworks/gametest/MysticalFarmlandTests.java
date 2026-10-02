/*
 * MysticalFarmlandTests — game test for VFW's mysticalagriculture.requiresEffectiveFarmland switch, against Mystical
 * Agriculture's real crop tiers, farmlands and block tags. References MA classes, so it is only called when MA is
 * installed.
 */
package com.virtualfarmworks.gametest;

import com.blakebr0.mysticalagriculture.api.crop.Crop;
import com.blakebr0.mysticalagriculture.api.crop.CropTier;
import com.blakebr0.mysticalagriculture.api.crop.ICropProvider;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.plant.PlantAnalysis;
import com.virtualfarmworks.plant.PlantAnalysis.Status;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Flips the switch in memory only ({@code ConfigValue#set} writes nothing to disk and fires no event) and always puts
 * the original value back, even when a check fails, so the other tests keep the default config. The switch is read by
 * {@link PlantAnalysis#analyze} directly, so no machine is needed here.
 */
final class MysticalFarmlandTests {
    private MysticalFarmlandTests() {
    }

    static void run(GameTestHelper helper) {
        // A tier-2 (Prudentium) crop: lower AND higher tier farmlands exist to test that only its own one counts.
        Crop crop = firstTierTwoResourceCrop(helper);
        Item seed = crop.getSeedsItem();
        Item ownFarmland = crop.getTier().getFarmlandBlock().asItem();                    // Prudentium Farmland
        Item lowerFarmland = CropTier.ONE.getFarmlandBlock().asItem();                     // Inferium Farmland
        Item higherFarmland = CropTier.FIVE.getFarmlandBlock().asItem();                   // Supremium Farmland
        Item awakenedFarmland = item("mysticalagriculture:awakened_supremium_farmland");   // always-effective tag
        Item inferiumSeeds = item("mysticalagriculture:inferium_seeds");

        var option = VfwServerConfig.MYSTICAL_REQUIRES_EFFECTIVE_FARMLAND;
        boolean original = option.get();
        try {
            // Default (off): any farmland works, as before.
            option.set(false);
            expect(helper, seed, lowerFarmland, Status.VALID);
            expect(helper, seed, Items.FARMLAND, Status.VALID);

            // On: MA's rule.
            option.set(true);
            expect(helper, seed, ownFarmland, Status.VALID);
            expect(helper, seed, lowerFarmland, Status.INVALID_SOIL);
            expect(helper, seed, higherFarmland, Status.INVALID_SOIL);
            expect(helper, seed, Items.FARMLAND, Status.INVALID_SOIL);
            expect(helper, seed, Items.DIRT, Status.INVALID_SOIL);   // tilled dirt is vanilla farmland
            expect(helper, seed, awakenedFarmland, Status.VALID);   // #mysticalagriculture:always_effective_farmland
            expect(helper, inferiumSeeds, ownFarmland, Status.VALID);     // Inferium is exempt (MA rule)
            expect(helper, inferiumSeeds, Items.FARMLAND, Status.VALID);
            expect(helper, Items.WHEAT_SEEDS, Items.FARMLAND, Status.VALID); // non-MA seeds are not affected
            expect(helper, Items.WHEAT_SEEDS, Items.DIRT, Status.VALID);
        } finally {
            option.set(original);
        }
        helper.succeed();
    }

    private static void expect(GameTestHelper helper, Item seed, Item soil, Status status) {
        PlantAnalysis analysis = PlantAnalysis.analyze(new ItemStack(seed), new ItemStack(soil), MachineTier.STARTER);
        helper.assertTrue(analysis.status() == status, Component.literal(BuiltInRegistries.ITEM.getKey(seed) + " on "
                + BuiltInRegistries.ITEM.getKey(soil) + " (requiresEffectiveFarmland="
                + VfwServerConfig.MYSTICAL_REQUIRES_EFFECTIVE_FARMLAND.get() + "): expected " + status + ", got "
                + analysis.status()));
    }

    /** An enabled tier-2 resource crop whose seed is a placeable seed item. */
    private static Crop firstTierTwoResourceCrop(GameTestHelper helper) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem) || !(item instanceof ICropProvider provider)) {
                continue;
            }
            Crop crop = provider.getCrop();
            if (crop.getSeedsItem() == item && crop.isEnabled() && crop.getTier() == CropTier.TWO) {
                return crop;
            }
        }
        helper.fail("no enabled tier-2 Mystical Agriculture resource seed found");
        throw new IllegalStateException("unreachable");
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }
}
