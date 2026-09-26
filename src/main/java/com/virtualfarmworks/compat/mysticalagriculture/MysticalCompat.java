/*
 * MysticalCompat — safe entry point for the Mystical Agriculture integration. Callable whether or not MA is installed:
 * it never references MA classes itself and only calls MysticalCompatImpl after checking that MA is loaded.
 */
package com.virtualfarmworks.compat.mysticalagriculture;

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
}
