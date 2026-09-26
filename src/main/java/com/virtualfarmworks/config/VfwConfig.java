/*
 * VfwConfig — runtime side of the config: registers the server config, rebuilds the compiled blacklists on
 * load/reload and bumps a generation counter that machines compare to know when to revalidate.
 */
package com.virtualfarmworks.config;

import java.util.EnumMap;
import java.util.Map;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.MachineTier;

import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * Runtime view of the config: registration, compiled blacklists and a change counter.
 *
 * <h2>Why a generation counter</h2>
 * Machines cache everything derived from config (cycle length, multipliers, whether their seed is still allowed...).
 * Instead of subscribing every BlockEntity to config events, each machine remembers the {@link #generation()} it
 * validated against and revalidates when the number differs. That is a single int comparison per tick, and a
 * {@code /reload}-style config edit takes effect on every machine on its next tick.
 *
 * <h2>Threading</h2>
 * Config events can fire on a config-watcher thread. Compiled filters are rebuilt into new immutable objects and
 * published through volatile fields, so the server thread always sees a consistent snapshot.
 */
public final class VfwConfig {
    private static volatile int generation;
    private static volatile ItemFilter globalSeedBlacklist = ItemFilter.EMPTY;
    private static volatile ItemFilter globalSoilBlacklist = ItemFilter.EMPTY;
    private static volatile Map<MachineTier, ItemFilter> tierSeedBlacklists = Map.of();
    private static volatile Map<MachineTier, ItemFilter> tierSoilBlacklists = Map.of();

    private VfwConfig() {
    }

    public static void register(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.SERVER, VfwServerConfig.SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, event -> onConfigChanged(event.getConfig()));
        modEventBus.addListener(ModConfigEvent.Reloading.class, event -> onConfigChanged(event.getConfig()));
    }

    private static void onConfigChanged(ModConfig config) {
        if (config.getSpec() != VfwServerConfig.SPEC) {
            return;
        }
        globalSeedBlacklist = ItemFilter.compile(VfwServerConfig.GLOBAL_SEED_BLACKLIST.get());
        globalSoilBlacklist = ItemFilter.compile(VfwServerConfig.GLOBAL_SOIL_BLACKLIST.get());

        Map<MachineTier, ItemFilter> seeds = new EnumMap<>(MachineTier.class);
        Map<MachineTier, ItemFilter> soils = new EnumMap<>(MachineTier.class);
        VfwServerConfig.MACHINES.forEach((tier, settings) -> {
            seeds.put(tier, ItemFilter.compile(settings.seedBlacklist.get()));
            soils.put(tier, ItemFilter.compile(settings.soilBlacklist.get()));
        });
        tierSeedBlacklists = Map.copyOf(seeds);
        tierSoilBlacklists = Map.copyOf(soils);

        // Bump last, after every compiled structure is published, so a machine that sees the new generation also
        // sees the new filters.
        generation++;
        VirtualFarmWorks.LOGGER.debug("Virtual Farm Works config (re)loaded, generation {}", generation);
    }

    /** Increments on every config load/reload. Machines compare it with their cached value to know when to revalidate. */
    public static int generation() {
        return generation;
    }

    /** Whether a seed/plantable is blacklisted for this machine tier (global list OR the tier's own list). */
    public static boolean isSeedBlacklisted(ItemStack stack, MachineTier tier) {
        return globalSeedBlacklist.matches(stack)
                || tierSeedBlacklists.getOrDefault(tier, ItemFilter.EMPTY).matches(stack);
    }

    /** Whether a soil is blacklisted for this machine tier (global list OR the tier's own list). */
    public static boolean isSoilBlacklisted(ItemStack stack, MachineTier tier) {
        return globalSoilBlacklist.matches(stack)
                || tierSoilBlacklists.getOrDefault(tier, ItemFilter.EMPTY).matches(stack);
    }

    /**
     * Safe read of the growth bonus for client-side display (tooltips). Server config values are only available
     * once connected to a world; outside of one (e.g. main menu) this returns the built-in default instead of
     * throwing.
     */
    public static double growthBonusForDisplay() {
        return VfwServerConfig.SPEC.isLoaded() ? VfwServerConfig.GROWTH_BONUS_PER_UPGRADE.get()
                : VfwServerConfig.GROWTH_BONUS_PER_UPGRADE.getDefault();
    }
}
