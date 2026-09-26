/*
 * HarvestPlans — decides, once per revalidation, how a seed/soil pair is harvested: which drop source to use (Mystical
 * Agriculture or loot table), which block state represents one harvested plot, and whether replanting costs a seed.
 */
package com.virtualfarmworks.harvest;

import java.lang.reflect.Field;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.compat.mysticalagriculture.MysticalCompat;
import com.virtualfarmworks.plant.PlantRules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Builds the {@link DropSource} for a seed/soil pair. Called only on revalidation (seed or soil changed, config or
 * tags reloaded); the machine caches the result and uses it at every harvest.
 *
 * <h2>Harvest models (what "one harvested plot" means)</h2>
 * Each model mirrors how a real farm of that plant is harvested, so VFW yields what an equivalent physical farm would:
 * <table>
 *   <tr><th>Plant</th><th>Drops of</th><th>Replant cost</th><th>Why</th></tr>
 *   <tr><td>Mystical Agriculture crops</td><td>MA's own formula (compat)</td><td>yes (1 seed)</td>
 *       <td>MA computes drops from the block under the crop, which a virtual plot does not have.</td></tr>
 *   <tr><td>Crops, nether wart, cocoa, unknown aged plants</td><td>the mature state</td><td>yes (1 planting item)</td>
 *       <td>The crop is broken and replanted with one of its drops.</td></tr>
 *   <tr><td>Melon / pumpkin stems</td><td>the FRUIT block (melon slices, pumpkin)</td><td>no</td>
 *       <td>The stem stays; only the fruit is harvested.</td></tr>
 *   <tr><td>Sweet berry bush, glow berries</td><td>the ripe state</td><td>no</td>
 *       <td>Berries are picked; the plant stays.</td></tr>
 *   <tr><td>Sugar cane, cactus, bamboo, mushrooms</td><td>the plant block (1 item)</td><td>no</td>
 *       <td>One grown segment/spread per cycle; the base stays.</td></tr>
 *   <tr><td>Chorus flower</td><td>a chorus plant block (0-1 chorus fruit)</td><td>no</td>
 *       <td>One grown stem segment per cycle; the flower stays.</td></tr>
 * </table>
 */
public final class HarvestPlans {
    /** {@code StemBlock#fruit} (private in 26.1): the block a stem grows. Null if it could not be accessed. */
    private static final @Nullable Field STEM_FRUIT = findStemFruitField();

    private HarvestPlans() {
    }

    /**
     * The drop source for a valid seed/soil pair (see {@code plant.PlantAnalysis}); null when the seed is not
     * plantable. The soil only matters for Mystical Agriculture (its farmland tier changes the drops).
     */
    public static @Nullable DropSource create(ItemStack seed, ItemStack soil) {
        Block plant = PlantRules.plantBlock(seed);
        if (plant == null) {
            return null;
        }
        if (MysticalCompat.isMysticalSeed(seed)) {
            DropSource mystical = MysticalCompat.createDropSource(seed, soil);
            if (mystical != null) {
                return mystical;
            }
        }

        // Plain item (no components) so it matches the seed items that loot tables drop.
        ItemResource plantingItem = ItemResource.of(seed.getItem());
        boolean isSeed = seed.is(Tags.Items.SEEDS);

        if (plant instanceof StemBlock stem) {
            return new LootDropSource(fruitOf(stem), plantingItem, false, isSeed);
        }
        if (plant instanceof SweetBerryBushBlock) {
            return new LootDropSource(matureState(plant), plantingItem, false, isSeed);
        }
        if (plant instanceof CaveVinesBlock) {
            return new LootDropSource(plant.defaultBlockState().setValue(CaveVines.BERRIES, true), plantingItem, false,
                    isSeed);
        }
        if (plant instanceof SugarCaneBlock || plant instanceof CactusBlock || plant instanceof BambooStalkBlock
                || plant instanceof MushroomBlock) {
            return new LootDropSource(plant.defaultBlockState(), plantingItem, false, isSeed);
        }
        if (plant instanceof BambooSaplingBlock) {
            return new LootDropSource(Blocks.BAMBOO.defaultBlockState(), plantingItem, false, isSeed);
        }
        if (plant instanceof ChorusFlowerBlock) {
            return new LootDropSource(Blocks.CHORUS_PLANT.defaultBlockState(), plantingItem, false, isSeed);
        }
        if (plant instanceof CropBlock crop) {
            return new LootDropSource(crop.getStateForAge(crop.getMaxAge()), plantingItem, true, isSeed);
        }
        // Nether wart, cocoa and any unknown plant accepted through #virtualfarmworks:extra_plantables.
        return new LootDropSource(matureState(plant), plantingItem, true, isSeed);
    }

    /**
     * The plant's default state with its {@code age} property (if any) at the maximum — the "fully grown" state that
     * vanilla and most mods use for harvest loot.
     */
    static BlockState matureState(Block plant) {
        BlockState state = plant.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
                return state.setValue(age, max);
            }
        }
        return state;
    }

    /**
     * The fruit a stem grows, read from the stem's private {@code fruit} field (26.1 runs with official names, so the
     * name is stable). Falls back to vanilla's known pairs, then to the stem itself (whose loot is its seeds) so a
     * broken lookup degrades instead of crashing.
     */
    @SuppressWarnings("unchecked")
    private static BlockState fruitOf(StemBlock stem) {
        if (STEM_FRUIT != null) {
            try {
                ResourceKey<Block> key = (ResourceKey<Block>) STEM_FRUIT.get(stem);
                Block fruit = BuiltInRegistries.BLOCK.getValue(key);
                if (fruit != Blocks.AIR) {
                    return fruit.defaultBlockState();
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                VirtualFarmWorks.LOGGER.warn("Could not read the fruit of stem {}", stem, e);
            }
        }
        if (stem == Blocks.MELON_STEM) {
            return Blocks.MELON.defaultBlockState();
        }
        if (stem == Blocks.PUMPKIN_STEM) {
            return Blocks.PUMPKIN.defaultBlockState();
        }
        return stem.defaultBlockState();
    }

    private static @Nullable Field findStemFruitField() {
        try {
            Field field = StemBlock.class.getDeclaredField("fruit");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            VirtualFarmWorks.LOGGER.warn("Could not access StemBlock#fruit; only vanilla melon/pumpkin stems will be "
                    + "harvested correctly.", e);
            return null;
        }
    }
}
