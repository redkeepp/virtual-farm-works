/*
 * MysticalCompat — safe entry point for the Mystical Agriculture integration. Callable whether or not MA is installed:
 * it never references MA classes itself and only calls MysticalCompatImpl after checking that MA is loaded.
 */
package com.virtualfarmworks.compat.mysticalagriculture;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.harvest.DropSource;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Safe entry point for Mystical Agriculture integration. Callable whether or not MA is installed.
 *
 * <p>Class-loading rule: this class must NEVER reference MA types (not in fields, signatures or lambdas). All MA code
 * lives in {@link MysticalCompatImpl}, which the JVM only loads when a method here calls into it — and every call is
 * guarded by {@link #isLoaded()}. Breaking this rule makes VFW crash with {@code NoClassDefFoundError} in packs without
 * MA.
 */
public final class MysticalCompat {
    public static final String MODID = "mysticalagriculture";

    /** Resolved lazily: ModList is only complete after mod loading. */
    private static volatile Boolean loaded;

    private MysticalCompat() {
    }

    public static boolean isLoaded() {
        Boolean value = loaded;
        if (value == null) {
            value = ModList.get().isLoaded(MODID);
            loaded = value;
        }
        return value;
    }

    /**
     * Whether a seed is a Mystical Agriculture crop that needs a crux (a specific block under its farmland in MA). In
     * VFW every crux requirement is satisfied by the generic Crux Provider Upgrade. False for non-MA seeds.
     */
    public static boolean requiresCrux(ItemStack seed) {
        return isLoaded() && MysticalCompatImpl.requiresCrux(seed);
    }

    /** Whether the stack is a Mystical Agriculture (or addon) SEED — essences are not. */
    public static boolean isMysticalSeed(ItemStack seed) {
        return isLoaded() && MysticalCompatImpl.isMysticalSeed(seed);
    }

    /**
     * The harvest drop source of an MA seed on a soil (MA's formula with the soil slot's farmland), or null when MA is
     * absent or the seed is not an MA seed. Returned as VFW's own {@link DropSource} type, so no MA type leaks out.
     */
    public static @Nullable DropSource createDropSource(ItemStack seed, ItemStack soil) {
        return isLoaded() ? MysticalCompatImpl.createDropSource(seed, soil) : null;
    }
}
