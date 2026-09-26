/*
 * MysticalCompatImpl — the only code that touches Mystical Agriculture classes (its api.crop package): crop lookup from
 * a seed and crux detection. Loaded by the JVM only through MysticalCompat, i.e. only when MA is installed.
 */
package com.virtualfarmworks.compat.mysticalagriculture;

import org.jspecify.annotations.Nullable;

import com.blakebr0.mysticalagriculture.api.crop.Crop;
import com.blakebr0.mysticalagriculture.api.crop.ICropProvider;
import com.virtualfarmworks.harvest.DropSource;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Code that touches Mystical Agriculture classes. ONLY call through {@link MysticalCompat}, which checks that MA is
 * installed first. Uses MA's public API package ({@code api.crop}) only, which is the most stable part across MA
 * versions; Mystical Agradditions crops implement the same API.
 */
final class MysticalCompatImpl {
    private MysticalCompatImpl() {
    }

    static boolean requiresCrux(ItemStack seed) {
        Crop crop = cropOf(seed);
        return crop != null && crop.getCruxBlock() != null;
    }

    static boolean isMysticalSeed(ItemStack seed) {
        return cropOf(seed) != null;
    }

    /** MA drop source for a seed on a soil item; null if the seed is not an MA seed or the soil places no block. */
    static @Nullable DropSource createDropSource(ItemStack seed, ItemStack soil) {
        Crop crop = cropOf(seed);
        if (crop == null || !(soil.getItem() instanceof BlockItem soilBlock)) {
            return null;
        }
        return new MysticalDropSource(crop, soilBlock.getBlock());
    }

    /**
     * The MA crop behind a SEED, or null for anything else.
     *
     * <p>Careful: MA essences (e.g. {@code nether_star_essence}) also implement {@link ICropProvider}, so "implements
     * ICropProvider" alone does not mean "is a seed" (a game test caught this). A seed must place a block; the crop is
     * taken from that block, falling back to the item when only the item implements the interface.
     */
    static @Nullable Crop cropOf(ItemStack seed) {
        if (!(seed.getItem() instanceof BlockItem blockItem)) {
            return null; // essences and other non-placeable items
        }
        if (blockItem.getBlock() instanceof ICropProvider provider) {
            return provider.getCrop();
        }
        if (seed.getItem() instanceof ICropProvider provider) {
            return provider.getCrop();
        }
        return null;
    }
}
