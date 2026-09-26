/*
 * SoilRules — soil-slot acceptance (cached per item, cleared on tag reload), hoe detection and tillable soils.
 */
package com.virtualfarmworks.plant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/**
 * Soil and hoe rules, plus the cache behind "can this item go in the soil slot?".
 *
 * <h2>Soil slot acceptance</h2>
 * An item is a soil when it places a block that at least one plantable plant can grow on, or when it is tillable.
 * That is computed generically (so modded soils work), which means testing the block against every plant type — too
 * expensive to repeat on every slot click, so the answer is cached per item. The cache and the plant "probe" list are
 * cleared whenever tags reload ({@link TagsUpdatedEvent}), because both depend on tags.
 *
 * <h2>Threading</h2>
 * Slot checks run on the client too (menus validate on both sides), so the cache is a {@link ConcurrentHashMap} and
 * the probe list is published through a volatile field. Both sides compute the same answers because tags are synced.
 */
public final class SoilRules {
    /** What a tillable soil becomes with a hoe. Modded tillables are approximated as vanilla farmland. */
    public static final BlockState TILLED_SOIL = Blocks.FARMLAND.defaultBlockState();

    private static final Map<Item, Boolean> SOIL_CACHE = new ConcurrentHashMap<>();
    /** One representative plant state per plant block CLASS (see {@link #probes()}). Null = not built yet. */
    private static volatile @Nullable List<BlockState> probes;
    /**
     * Incremented whenever tags (and with them datapack data such as the soil data map) reload. Machines compare it
     * with the value they last validated against, exactly like {@code VfwConfig#generation()} for config changes.
     */
    private static volatile int cacheGeneration;

    private SoilRules() {
    }

    public static void register(IEventBus gameEventBus) {
        gameEventBus.addListener(TagsUpdatedEvent.class, event -> clearCaches());
    }

    /** Drops every cached answer and tells machines to revalidate. Called on tag reload; safe to call any time. */
    public static void clearCaches() {
        SOIL_CACHE.clear();
        probes = null;
        cacheGeneration++;
    }

    /** Changes on every tag/datapack reload; see {@link #cacheGeneration}. */
    public static int cacheGeneration() {
        return cacheGeneration;
    }

    /**
     * Whether a stack is a hoe: in the vanilla {@code #minecraft:hoes} tag OR able to perform the hoe-till ability
     * (modded hoes usually declare the ability even without the tag). Tier, damage and energy do not matter.
     */
    public static boolean isHoe(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ItemTags.HOES) || stack.canPerformAction(ItemAbilities.HOE_TILL));
    }

    /** Whether the soil counts as farmland once a hoe is installed ({@link VfwTags#TILLABLE_SOILS}). */
    public static boolean isTillable(ItemStack soil) {
        return soil.is(VfwTags.TILLABLE_SOILS);
    }

    /** The block state a soil item represents, or null when it does not place a block. */
    public static @Nullable BlockState soilState(ItemStack soil) {
        return !soil.isEmpty() && soil.getItem() instanceof BlockItem blockItem
                ? blockItem.getBlock().defaultBlockState() : null;
    }

    /** Whether the item may go in the soil slot, IGNORING config blacklists. Cached per item. */
    public static boolean isAcceptableSoil(ItemStack soil) {
        BlockState state = soilState(soil);
        if (state == null) {
            return false;
        }
        return SOIL_CACHE.computeIfAbsent(soil.getItem(), item -> computeAcceptableSoil(soil, state));
    }

    private static boolean computeAcceptableSoil(ItemStack soil, BlockState state) {
        // A plant is never a soil, even when vanilla would allow it (sugar cane on sugar cane, cactus on cactus).
        if (PlantRules.isSupportedPlantBlock(state.getBlock())) {
            return false;
        }
        if (isTillable(soil)) {
            return true;
        }
        for (BlockState plant : probes()) {
            if (PlantRules.canGrowOn(plant, state)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Representative plants used to decide whether a block is a soil at all: the default state of every plantable
     * item's block, de-duplicated by block CLASS. Plants of the same class share the same soil logic (e.g. the ~150
     * Mystical Agriculture crops are one class), so this keeps the list to a few dozen entries even in huge packs.
     * Built lazily on first use after each tag reload.
     */
    private static List<BlockState> probes() {
        List<BlockState> current = probes;
        if (current != null) {
            return current;
        }
        Map<Class<?>, BlockState> byClass = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (PlantRules.isPlantable(stack)) {
                Block block = PlantRules.plantBlock(stack);
                if (block != null) {
                    byClass.putIfAbsent(block.getClass(), block.defaultBlockState());
                }
            }
        }
        current = List.copyOf(new ArrayList<>(byClass.values()));
        probes = current;
        return current;
    }
}
