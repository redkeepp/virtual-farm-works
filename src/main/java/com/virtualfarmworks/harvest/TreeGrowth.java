/*
 * TreeGrowth — how a sapling, azalea or nether fungus grows into a tree, and growing one in a VirtualLevel (memory
 * only) to count its blocks. Uses the plant's own tree feature, so vanilla and modded trees need no per-mod code.
 */
package com.virtualfarmworks.harvest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.plant.VirtualLevel;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherFungusBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * One plot of a tree plant = one whole tree (owner, 2026-09-28: "imagine the player breaking the tree in the normal
 * world; the machine drops the same as a real tree"). This class grows such a tree the way the game does — with the
 * plant's own configured feature — but in a {@link VirtualLevel}, never in the world, and reports its blocks.
 *
 * <h2>Which feature</h2>
 * <ul>
 *   <li>{@link SaplingBlock} (every vanilla sapling, the mangrove propagule and most modded saplings): its
 *       {@link TreeGrower}, choosing the normal tree with the grower's own odds (fancy oak 10%, tall mangrove 85%...).
 *       Trees that only exist in 2x2 (dark oak, pale oak) use their 2x2 feature: one sapling is enough for the whole
 *       tree (owner). The bee variants are never chosen (they need flowers around the sapling).</li>
 *   <li>{@link AzaleaBlock}: the azalea tree grower.</li>
 *   <li>{@link NetherFungusBlock}: its huge fungus feature, grown on the nylium it requires whatever the machine's
 *       soil is (owner: the nylium condition is dropped).</li>
 * </ul>
 * The private parts of those vanilla classes are read once through reflection (26.1 runs with official names, so the
 * names are stable); if that ever fails, {@link #of} returns null and the plant is harvested as a plain plant instead.
 * NeoForge's {@code BlockGrowFeatureEvent} is not fired: it expects a real level.
 *
 * <p>Cost: one growth places a few dozen to a few hundred blocks in a hash map, comparable to tens of loot-table rolls,
 * so {@link TreeDropSource} grows only a few trees per harvest (config). Server thread only.
 */
public final class TreeGrowth {
    private static final @Nullable Field SAPLING_GROWER = field(SaplingBlock.class, "treeGrower");
    private static final @Nullable Field FUNGUS_FEATURE = field(NetherFungusBlock.class, "feature");
    private static final @Nullable Field FUNGUS_GROUND = field(NetherFungusBlock.class, "requiredBlock");
    private static final @Nullable MethodHandle NORMAL_TREE = method("getConfiguredFeature", RandomSource.class,
            boolean.class);
    private static final @Nullable MethodHandle MEGA_TREE = method("getConfiguredMegaFeature", RandomSource.class);

    /** Logged once per session so a broken modded tree cannot spam the log every harvest. */
    private static volatile boolean warnedAboutGrowth;

    private final @Nullable TreeGrower grower;
    private final @Nullable ResourceKey<ConfiguredFeature<?, ?>> feature;
    private final @Nullable BlockState requiredGround;

    private TreeGrowth(@Nullable TreeGrower grower, @Nullable ResourceKey<ConfiguredFeature<?, ?>> feature,
                       @Nullable BlockState requiredGround) {
        this.grower = grower;
        this.feature = feature;
        this.requiredGround = requiredGround;
    }

    /** Whether a plant block is a tree plant (sapling, azalea or nether fungus, modded subclasses included). */
    public static boolean isTree(Block plant) {
        return plant instanceof SaplingBlock || plant instanceof AzaleaBlock || plant instanceof NetherFungusBlock;
    }

    /**
     * How {@code plant} grows into a tree, or null when it is not a tree plant or its tree cannot be read (then the
     * caller treats it as a plain plant). Called on revalidation only.
     */
    @SuppressWarnings("unchecked")
    public static @Nullable TreeGrowth of(Block plant) {
        try {
            if (plant instanceof NetherFungusBlock) {
                if (FUNGUS_FEATURE == null || FUNGUS_GROUND == null) {
                    return null;
                }
                return new TreeGrowth(null, (ResourceKey<ConfiguredFeature<?, ?>>) FUNGUS_FEATURE.get(plant),
                        ((Block) FUNGUS_GROUND.get(plant)).defaultBlockState());
            }
            if (NORMAL_TREE == null || MEGA_TREE == null) {
                return null;
            }
            if (plant instanceof AzaleaBlock) {
                return new TreeGrowth(TreeGrower.AZALEA, null, null);
            }
            if (plant instanceof SaplingBlock && SAPLING_GROWER != null) {
                return new TreeGrowth((TreeGrower) SAPLING_GROWER.get(plant), null, null);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            VirtualFarmWorks.LOGGER.warn("Could not read the tree of {}; it is harvested as a plain plant.", plant, e);
        }
        return null;
    }

    /**
     * The ground a tree grows on: the fungus's own nylium, or the machine's soil (a sapling on mud grows its mangrove
     * roots into the mud, like in the world).
     */
    public BlockState groundFor(BlockState soil) {
        return requiredGround != null ? requiredGround : soil;
    }

    /**
     * Grows one tree on {@code ground} in a fresh {@link VirtualLevel} and counts the blocks that grew above the
     * ground. If the feature places nothing there (some modded trees check their ground), it gets one more try on a
     * grass block. Empty when the tree could not grow at all (logged once).
     */
    public Object2IntMap<BlockState> grow(ServerLevel level, BlockState ground, RandomSource random) {
        Object2IntMap<BlockState> blocks = growOn(level, ground, random);
        if (blocks.isEmpty() && requiredGround == null && !ground.is(Blocks.GRASS_BLOCK)) {
            blocks = growOn(level, Blocks.GRASS_BLOCK.defaultBlockState(), random);
        }
        return blocks;
    }

    private Object2IntMap<BlockState> growOn(ServerLevel level, BlockState ground, RandomSource random) {
        try {
            ResourceKey<ConfiguredFeature<?, ?>> key = chooseFeature(random);
            if (key == null) {
                return Object2IntMaps.emptyMap();
            }
            Optional<Holder.Reference<ConfiguredFeature<?, ?>>> holder =
                    level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(key);
            if (holder.isEmpty()) {
                return Object2IntMaps.emptyMap();
            }
            VirtualLevel world = VirtualLevel.growth(ground, level, random);
            if (!holder.get().value().place(world, level.getChunkSource().getGenerator(), random, VirtualLevel.ORIGIN)) {
                return Object2IntMaps.emptyMap();
            }
            return world.countGrownBlocks();
        } catch (Throwable e) {
            if (e instanceof Error && !(e instanceof StackOverflowError)) {
                throw (Error) e; // never swallow OutOfMemoryError and friends
            }
            if (!warnedAboutGrowth) {
                warnedAboutGrowth = true;
                VirtualFarmWorks.LOGGER.warn("A tree ({}) failed to grow in a Farm Matrix; that tree yields nothing. "
                        + "This is logged once.", feature != null ? feature : grower, e);
            }
            return Object2IntMaps.emptyMap();
        }
    }

    /** The feature for this growth: the fungus's, or the grower's normal tree, else its 2x2 tree. */
    @SuppressWarnings("unchecked")
    private @Nullable ResourceKey<ConfiguredFeature<?, ?>> chooseFeature(RandomSource random) throws Throwable {
        if (feature != null) {
            return feature;
        }
        if (grower == null || NORMAL_TREE == null || MEGA_TREE == null) {
            return null;
        }
        ResourceKey<ConfiguredFeature<?, ?>> normal =
                (ResourceKey<ConfiguredFeature<?, ?>>) NORMAL_TREE.invoke(grower, random, false);
        return normal != null ? normal : (ResourceKey<ConfiguredFeature<?, ?>>) MEGA_TREE.invoke(grower, random);
    }

    private static @Nullable Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            VirtualFarmWorks.LOGGER.warn("Could not access {}#{}; those plants are harvested as plain plants.",
                    owner.getSimpleName(), name, e);
            return null;
        }
    }

    private static @Nullable MethodHandle method(String name, Class<?>... parameters) {
        try {
            Method method = TreeGrower.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VirtualFarmWorks.LOGGER.warn("Could not access TreeGrower#{}; saplings are harvested as plain plants.",
                    name, e);
            return null;
        }
    }
}
