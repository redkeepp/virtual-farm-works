/*
 * PlantRules — which items are plantable in VFW, whether a plant needs a soil at all, and whether a plant can grow on
 * a soil (canGrowOn). Mirrors the vanilla/NeoForge survival rules without a real world, so modded plants and soils work
 * without per-mod code.
 */
package com.virtualfarmworks.plant;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.virtualfarmworks.VirtualFarmWorks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.TriState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.BigDripleafBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.HangingMossBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Which items are plantable in VFW, whether a plant needs a soil, and whether it can grow on a given soil.
 *
 * <h2>Two families of plants</h2>
 * <ul>
 *   <li><b>Native plants</b> ({@link #isSupportedPlantBlock}): the types VFW handled from the start — crops (every
 *       {@link CropBlock}, Mystical Agriculture included), stems, sugar cane, cactus, bamboo, cocoa, nether wart, sweet
 *       and glow berries, mushrooms, chorus. Their soil rules are exactly the game's.</li>
 *   <li><b>Generic plants</b> ({@link #isGenericPlantBlock}, owner decision 2026-09-28: "accept every plantable, any
 *       seed, any sapling, anything"): every other vegetation block (flowers, saplings, grass, ferns, bushes, fungi,
 *       roots, lily pads...) and the plants that are not vegetation blocks (kelp, vines, glow lichen, spore blossom,
 *       hanging roots, pale hanging moss, big dripleaf, and any modded {@link GrowingPlantHeadBlock} or
 *       {@link VineBlock}). Detected by block class, so modded plants built on the vanilla classes work.</li>
 * </ul>
 * Plants with a block entity are never generic plants: in modpacks those are crafted, functional blocks (Botania's
 * special flowers...), and "yields 10 of itself" would duplicate them. Pack makers can still add any item with
 * {@link VfwTags#EXTRA_PLANTABLES} and remove any with {@link VfwTags#UNPLANTABLE} or the config blacklists.
 *
 * <h2>Soil of generic plants ({@link SoilNeed}, owner decisions)</h2>
 * Decided once per block from the plant's own survival rule ({@code canSurvive}), run in a {@link VirtualLevel}:
 * <ul>
 *   <li>stands on dirt or grass: {@link SoilNeed#ANY} — grows on its natural soils AND on every universal soil
 *       ({@link #isUniversalSoil}: the vanilla vegetation soils plus any farmland). Conditions such as water (kelp,
 *       seagrass), coral (sea pickle) or nylium for the huge fungus are dropped: a soil is enough;</li>
 *   <li>stands only on farmland: {@link SoilNeed#FARMLAND} — a crop in all but class (pitcher pod): its natural rule,
 *       and a hoe tills dirt, exactly like crops;</li>
 *   <li>stands on none of them (hangs, climbs or floats: lily pad, small dripleaf, vines, weeping vines, spore blossom,
 *       hanging roots, pale hanging moss): {@link SoilNeed#NONE} — needs no soil; the soil slot is ignored.</li>
 * </ul>
 *
 * <h2>Soil compatibility</h2>
 * Mirrors the vanilla/NeoForge survival logic without a real world (see {@link #canGrowOn}). Light, water adjacency
 * and neighbour blocks are deliberately ignored: a virtual plot is assumed to have ideal conditions, only the soil
 * matters.
 */
public final class PlantRules {
    /** Handle to the protected {@code BushBlock#mayPlaceOn}; null if it could not be resolved. */
    private static final @Nullable MethodHandle MAY_PLACE_ON = findMayPlaceOn();

    /** Soil needs of generic plants, per block. Depends on tags: cleared with {@link #clearCaches()}. */
    private static final Map<Block, GenericPlant> GENERIC_PLANTS = new ConcurrentHashMap<>();

    /** Logged once per session per failure kind so a broken mod does not spam the log. */
    private static volatile boolean warnedAboutHook;
    private static volatile boolean warnedAboutMayPlaceOn;
    private static volatile boolean warnedAboutSurvival;

    /** How a generic plant relates to soil; see the class doc. */
    public enum SoilNeed {
        /** Grows on its natural soils and on every universal soil (flowers, saplings, grass, fungi...). */
        ANY,
        /** Only farmland by nature, like a crop: keeps its natural rule, a hoe tills dirt (pitcher pod). */
        FARMLAND,
        /** Does not stand on a soil (hanging, climbing, floating plants): the soil slot is ignored. */
        NONE
    }

    /**
     * What {@link #classify} learned about a generic plant.
     *
     * @param need       its soil need
     * @param anySurface its natural rule accepts any solid surface (seagrass, kelp, leaf litter...): such a rule is not a
     *                   soil rule, so it must not turn every solid block into a soil in the soil slot
     */
    private record GenericPlant(SoilNeed need, boolean anySurface) {
    }

    private PlantRules() {
    }

    /** Clears what depends on tags. Called by {@link SoilRules#clearCaches()} on every tag reload. */
    static void clearCaches() {
        GENERIC_PLANTS.clear();
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
        return isSupportedPlantBlock(block) || isGenericPlantBlock(block) || seed.is(VfwTags.EXTRA_PLANTABLES);
    }

    /** Any block VFW grows (native or generic). Also used to reject plants as "soil" (sugar cane on sugar cane). */
    public static boolean isPlantBlock(Block block) {
        return isSupportedPlantBlock(block) || isGenericPlantBlock(block);
    }

    /** Native plant types (see the class doc), each with its own harvest model in {@code harvest.HarvestPlans}. */
    public static boolean isSupportedPlantBlock(Block block) {
        return block instanceof CropBlock          // wheat, carrot, potato, beetroot, torchflower, modded crops (MA...)
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
     * Generic plants (see the class doc): any other vegetation block, and the plant types that are not vegetation
     * blocks. Never a block with a block entity (crafted functional "plants" in modpacks).
     */
    public static boolean isGenericPlantBlock(Block block) {
        if (isSupportedPlantBlock(block) || block instanceof EntityBlock) {
            return false;
        }
        return block instanceof BushBlock               // flowers, saplings, grass, bushes, fungi, roots, lily pad...
                || block instanceof GrowingPlantHeadBlock // kelp, weeping and twisting vines
                || block instanceof VineBlock
                || block instanceof GlowLichenBlock
                || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock
                || block instanceof HangingMossBlock      // pale hanging moss
                || block instanceof BigDripleafBlock;
    }

    /**
     * Whether a plant needs something in the soil slot. False only for generic plants that do not stand on a soil
     * ({@link SoilNeed#NONE}); for those the machine ignores the soil slot and plots = seeds (owner decision).
     */
    public static boolean needsSoil(Block plant) {
        return !isGenericPlantBlock(plant) || genericPlant(plant).need() != SoilNeed.NONE;
    }

    /**
     * Whether a plant may decide which blocks count as soils in the soil slot ({@link SoilRules}). Generic plants that
     * need no soil, or whose natural rule accepts any solid surface, may not: otherwise ice (lily pad), stone, glass or
     * a diamond block (seagrass, leaf litter) would become soils.
     */
    static boolean definesSoils(Block plant) {
        if (!isGenericPlantBlock(plant)) {
            return true;
        }
        GenericPlant generic = genericPlant(plant);
        return generic.need() != SoilNeed.NONE && !generic.anySurface();
    }

    /**
     * Soils every generic plant with {@link SoilNeed#ANY} grows on (owner: "dirt, grass, any farmland"): the block tag
     * {@link VfwTags#UNIVERSAL_SOILS} (default: the vanilla vegetation soils — dirt, coarse and rooted dirt, grass,
     * podzol, mycelium, moss, mud, farmland) plus every {@link FarmlandBlock}, so modded farmlands (Mystical Agriculture,
     * Agradditions...) count without a list.
     */
    public static boolean isUniversalSoil(BlockState soil) {
        return soil.is(VfwTags.UNIVERSAL_SOILS) || soil.getBlock() instanceof FarmlandBlock;
    }

    /**
     * Whether {@code plant} can grow on {@code soil}, decided like the game does but without a real world:
     * <ol>
     *   <li>Ask the SOIL first through NeoForge's {@code canSustainPlant} hook. Mods use it to force-allow or
     *       force-deny plants on their soils; a non-default answer wins.</li>
     *   <li>Generic plants: their own survival rule ({@code canSurvive}, see {@link #survivesOn}), or any universal soil
     *       when they grow on dirt ({@link SoilNeed#ANY}).</li>
     *   <li>Native plants: {@code mayPlaceOn} for {@link BushBlock}s, called through {@link #mayPlaceOn}
     *       (vanilla crops use the 26.1 {@code #supports_*} tags there, modded crops may override it), or the matching
     *       vanilla support tag for plants that are not VegetationBlocks (sugar cane, cactus, bamboo, cocoa, chorus
     *       flower).</li>
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

        if (isGenericPlantBlock(block)) {
            Boolean natural = survivesOn(block, soil);
            if (Boolean.TRUE.equals(natural)) {
                return true;
            }
            return genericPlant(block).need() == SoilNeed.ANY && isUniversalSoil(soil);
        }

        // Order matters: MushroomBlock is a BushBlock but uses a VFW tag instead of its broad vanilla rule.
        if (block instanceof MushroomBlock) {
            return soil.is(VfwTags.SUPPORTS_MUSHROOMS);
        }
        if (block instanceof CaveVinesBlock) {
            return soil.is(VfwTags.SUPPORTS_GLOW_BERRIES);
        }
        if (block instanceof BushBlock vegetation) {
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
        // Unknown plant type (only reachable through EXTRA_PLANTABLES with a non-plant block): only the soil's
        // explicit TRUE, handled above, can accept it.
        return false;
    }

    // --- generic plants ---------------------------------------------------------------------------------------------

    private static GenericPlant genericPlant(Block block) {
        return GENERIC_PLANTS.computeIfAbsent(block, PlantRules::classify);
    }

    /**
     * Classifies a generic plant by asking its own survival rule about three soils (dirt, grass block, farmland) and
     * one block no soil rule would name (a diamond block: accepted only by "any solid surface" rules). If the rule
     * fails on our virtual world, the plant is treated as {@link SoilNeed#ANY} (it then needs a universal soil): the
     * safe side, since "needs no soil" is only for plants that clearly stand on nothing.
     */
    private static GenericPlant classify(Block block) {
        Boolean onDirt = survivesOn(block, Blocks.DIRT.defaultBlockState());
        Boolean onGrass = survivesOn(block, Blocks.GRASS_BLOCK.defaultBlockState());
        Boolean onFarmland = survivesOn(block, Blocks.FARMLAND.defaultBlockState());
        Boolean onDiamond = survivesOn(block, Blocks.DIAMOND_BLOCK.defaultBlockState());
        if (onDirt == null || onGrass == null || onFarmland == null || onDiamond == null) {
            return new GenericPlant(SoilNeed.ANY, false);
        }
        SoilNeed need = onDirt || onGrass ? SoilNeed.ANY : onFarmland ? SoilNeed.FARMLAND : SoilNeed.NONE;
        return new GenericPlant(need, onDiamond);
    }

    /**
     * The plant's own survival rule ({@code BlockState#canSurvive}) with {@code soil} below it, in a
     * {@link VirtualLevel} (full light, no water, air around). For vegetation blocks NeoForge's version already asks
     * the soil's {@code canSustainPlant} hook, then {@code mayPlaceOn}.
     *
     * @return the answer, or null when the rule threw on the virtual world (logged once)
     */
    private static @Nullable Boolean survivesOn(Block block, BlockState soil) {
        BlockState plant = standingState(block);
        VirtualLevel level = VirtualLevel.rules(soil);
        try {
            level.setBlock(VirtualLevel.ORIGIN, plant, Block.UPDATE_NONE);
            return plant.canSurvive(level, VirtualLevel.ORIGIN);
        } catch (RuntimeException e) {
            if (!warnedAboutSurvival) {
                warnedAboutSurvival = true;
                VirtualFarmWorks.LOGGER.warn("canSurvive of {} failed on a virtual soil; VFW assumes it grows on the "
                        + "universal soils. This is logged once.", block, e);
            }
            return null;
        }
    }

    /**
     * The state of a plant standing on the ground. Multiface plants (glow lichen) have no face by default, which
     * survives nowhere; the floor face is the one that stands on a soil.
     */
    private static BlockState standingState(Block block) {
        BlockState state = block.defaultBlockState();
        if (block instanceof MultifaceBlock) {
            BooleanProperty floor = MultifaceBlock.getFaceProperty(Direction.DOWN);
            if (state.hasProperty(floor)) {
                return state.setValue(floor, true);
            }
        }
        return state;
    }

    // --- native plants ----------------------------------------------------------------------------------------------

    /**
     * Calls the plant's protected {@code BushBlock#mayPlaceOn} — its own "which soil can I stand on" rule, which
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
    private static boolean mayPlaceOn(BushBlock plant, BlockState soil, SoilView view) {
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

    /** Vanilla's rules for the BushBlock types VFW accepts natively, used only if {@link #mayPlaceOn} fails. */
    private static boolean fallbackMayPlaceOn(BushBlock plant, BlockState soil) {
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
            Method method = BushBlock.class.getDeclaredMethod("mayPlaceOn",
                    BlockState.class, BlockGetter.class, BlockPos.class);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (ReflectiveOperationException | RuntimeException e) {
            VirtualFarmWorks.LOGGER.warn("Could not access BushBlock#mayPlaceOn; VFW will use vanilla tag rules "
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
