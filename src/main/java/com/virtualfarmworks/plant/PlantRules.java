/*
 * PlantRules — which items are plantable in VFW and whether a plant can grow on a soil (canGrowOn). Mirrors the
 * vanilla/NeoForge survival rules without a real world, so modded crops and soils work without per-mod code.
 */
package com.virtualfarmworks.plant;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.VirtualFarmWorks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.TriState;

/**
 * Which items are plantable in VFW, and whether a plant can grow on a soil.
 *
 * <h2>Accepted plants</h2>
 * Owner decision (see {@code docs/specs/starter-farm-matrix.md}): crops (wheat, carrot, potato, beetroot and every
 * modded {@link CropBlock}, including Mystical Agriculture), stem crops (melon, pumpkin), sugar cane, cactus, bamboo,
 * cocoa, nether wart, sweet berries, glow berries, mushrooms and chorus flower. NOT accepted: flowers, saplings/trees,
 * fungi, vines, aquatic and decorative plants. Pack makers can add plants with {@link VfwTags#EXTRA_PLANTABLES} and
 * remove them with {@link VfwTags#UNPLANTABLE} or the config blacklists.
 *
 * <h2>Soil compatibility</h2>
 * Mirrors the vanilla/NeoForge survival logic without a real world (see {@link #canGrowOn}). Light, water adjacency
 * and neighbour blocks are deliberately ignored: a virtual plot is assumed to have ideal conditions, only the soil
 * matters.
 */
public final class PlantRules {
    /** Handle to the protected {@code VegetationBlock#mayPlaceOn}; null if it could not be resolved. */
    private static final @Nullable MethodHandle MAY_PLACE_ON = findMayPlaceOn();

    /** Logged once per session per failure kind so a broken mod does not spam the log. */
    private static volatile boolean warnedAboutHook;
    private static volatile boolean warnedAboutMayPlaceOn;

    private PlantRules() {
    }

    /** The plant block a seed item places, or null when the item does not place a block. */
    public static @Nullable Block plantBlock(ItemStack seed) {
        return !seed.isEmpty() && seed.getItem() instanceof BlockItem blockItem ? blockItem.getBlock() : null;
    }

    /**
     * Whether the item can go in the seed slot, IGNORING the config blacklists (those depend on the machine tier and are
     * checked by {@link PlantAnalysis}). Cheap: an instanceof chain and two tag lookups.
     */
    public static boolean isPlantable(ItemStack seed) {
        Block block = plantBlock(seed);
        if (block == null || seed.is(VfwTags.UNPLANTABLE)) {
            return false;
        }
        return isSupportedPlantBlock(block) || seed.is(VfwTags.EXTRA_PLANTABLES);
    }

    /** Plant block types VFW understands natively. Also used to reject plants as "soil" (sugar cane on sugar cane). */
    public static boolean isSupportedPlantBlock(Block block) {
        return block instanceof CropBlock          // wheat, carrot, potato, beetroot, modded crops (MA, ...)
                || block instanceof StemBlock      // melon, pumpkin
                || block instanceof NetherWartBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof MushroomBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof BambooStalkBlock
                || block instanceof BambooSaplingBlock
                || block instanceof CocoaBlock
                || block instanceof CaveVinesBlock // glow berries
                || block instanceof ChorusFlowerBlock;
    }

    /**
     * Whether {@code plant} can grow on {@code soil}, decided like the game does but without a real world:
     * <ol>
     *   <li>Ask the SOIL first through NeoForge's {@code canSustainPlant} hook. Mods use it to force-allow or
     *       force-deny plants on their soils; a non-default answer wins.</li>
     *   <li>Otherwise apply the PLANT's own rule: {@code mayPlaceOn} for {@link VegetationBlock}s, called through
     *       {@link #mayPlaceOn} (vanilla crops use the 26.1 {@code #supports_*} tags there, modded crops may override
     *       it), or the matching vanilla support tag
     *       for plants that are not VegetationBlocks (sugar cane, cactus, bamboo, cocoa, chorus flower).</li>
     * </ol>
     * Mushrooms and glow berries use VFW tags because their vanilla rules are "any solid block" (see {@link VfwTags}).
     *
     * <p>Not called per tick: only when a machine revalidates (slot/config/tag change), and when building the soil-slot
     * cache in {@link SoilRules}.
     */
    public static boolean canGrowOn(BlockState plant, BlockState soil) {
        Block block = plant.getBlock();
        Direction plantSide = plantSide(block);
        SoilView view = new SoilView(soil, plant, plantSide);

        TriState decision = soilDecision(soil, view, plantSide, plant);
        if (!decision.isDefault()) {
            return decision.isTrue();
        }

        // Order matters: MushroomBlock is a VegetationBlock but uses a VFW tag instead of its broad vanilla rule.
        if (block instanceof MushroomBlock) {
            return soil.is(VfwTags.SUPPORTS_MUSHROOMS);
        }
        if (block instanceof CaveVinesBlock) {
            return soil.is(VfwTags.SUPPORTS_GLOW_BERRIES);
        }
        if (block instanceof VegetationBlock vegetation) {
            return mayPlaceOn(vegetation, soil, view);
        }
        if (block instanceof SugarCaneBlock) {
            return soil.is(BlockTags.SUPPORTS_SUGAR_CANE); // water adjacency ignored: virtual plot
        }
        if (block instanceof CactusBlock) {
            return soil.is(BlockTags.SUPPORTS_CACTUS);
        }
        if (block instanceof BambooStalkBlock || block instanceof BambooSaplingBlock) {
            return soil.is(BlockTags.SUPPORTS_BAMBOO);
        }
        if (block instanceof CocoaBlock) {
            return soil.is(BlockTags.SUPPORTS_COCOA);
        }
        if (block instanceof ChorusFlowerBlock) {
            return soil.is(BlockTags.SUPPORTS_CHORUS_FLOWER);
        }
        // Unknown plant type (only reachable through EXTRA_PLANTABLES with a non-vegetation block): only the soil's
        // explicit TRUE, handled above, can accept it.
        return false;
    }

    /**
     * Calls the plant's protected {@code VegetationBlock#mayPlaceOn} — its own "which soil can I stand on" rule, which
     * vanilla crops implement with the 26.1 {@code #supports_*} tags and modded crops may override.
     *
     * <p>Why reflection and not an access transformer: an AT making the base method public breaks the Minecraft
     * recompile, because 19 vanilla subclasses override it as {@code protected} ("weaker access privileges"), and that
     * list changes between Minecraft versions. A {@link MethodHandle} resolved once is version-proof (26.1 runs with
     * official names in production) and dispatches to overrides like a normal call. Only used on revalidation, never
     * per tick, so reflective cost is irrelevant.
     *
     * <p>If the handle cannot be resolved or the call throws, falls back to the vanilla tag each plant type uses.
     */
    private static boolean mayPlaceOn(VegetationBlock plant, BlockState soil, SoilView view) {
        MethodHandle handle = MAY_PLACE_ON;
        if (handle != null) {
            try {
                return (boolean) handle.invoke(plant, soil, (BlockGetter) view, SoilView.SOIL_POS);
            } catch (Throwable e) {
                if (!warnedAboutMayPlaceOn) {
                    warnedAboutMayPlaceOn = true;
                    VirtualFarmWorks.LOGGER.warn("mayPlaceOn of {} failed on a virtual soil; using tag rules. "
                            + "This is logged once.", plant, e);
                }
            }
        }
        return fallbackMayPlaceOn(plant, soil);
    }

    /** Vanilla's rules for the VegetationBlock types VFW accepts natively, used only if {@link #mayPlaceOn} fails. */
    private static boolean fallbackMayPlaceOn(VegetationBlock plant, BlockState soil) {
        if (plant instanceof CropBlock) {
            return soil.is(BlockTags.SUPPORTS_CROPS);
        }
        if (plant instanceof StemBlock) {
            return soil.is(BlockTags.SUPPORTS_STEM_CROPS);
        }
        if (plant instanceof NetherWartBlock) {
            return soil.is(BlockTags.SUPPORTS_NETHER_WART);
        }
        return soil.is(BlockTags.SUPPORTS_VEGETATION);
    }

    private static @Nullable MethodHandle findMayPlaceOn() {
        try {
            Method method = VegetationBlock.class.getDeclaredMethod("mayPlaceOn",
                    BlockState.class, BlockGetter.class, BlockPos.class);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VirtualFarmWorks.LOGGER.warn("Could not access VegetationBlock#mayPlaceOn; VFW will use vanilla tag rules "
                    + "for plant/soil compatibility (modded crops with custom soil rules may be misjudged).", e);
            return null;
        }
    }

    /** Where the plant sits relative to its soil. */
    private static Direction plantSide(Block block) {
        if (block instanceof CocoaBlock) {
            return Direction.NORTH; // cocoa hangs on the side of a jungle log
        }
        if (block instanceof CaveVinesBlock) {
            return Direction.DOWN;  // glow berries hang below their support
        }
        return Direction.UP;
    }

    /**
     * Calls the soil's {@code canSustainPlant} hook defensively. Modded hooks written for real worlds may cast the
     * {@code BlockGetter} to {@code Level} and throw on our virtual view; in that case fall back to the plant's own rule
     * instead of crashing the server.
     */
    private static TriState soilDecision(BlockState soil, SoilView view, Direction plantSide, BlockState plant) {
        try {
            return soil.canSustainPlant(view, SoilView.SOIL_POS, plantSide, plant);
        } catch (RuntimeException e) {
            if (!warnedAboutHook) {
                warnedAboutHook = true;
                VirtualFarmWorks.LOGGER.warn("canSustainPlant of {} failed on a virtual soil; using the plant's own rule. "
                        + "This is logged once.", soil, e);
            }
            return TriState.DEFAULT;
        }
    }
}
