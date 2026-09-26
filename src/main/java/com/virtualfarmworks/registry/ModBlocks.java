/*
 * ModBlocks — DeferredRegister of VFW blocks (currently only the Starter Farm Matrix) and the block properties shared
 * by every Farm Matrix tier.
 */
package com.virtualfarmworks.registry;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.block.FarmMatrixBlock;
import com.virtualfarmworks.machine.MachineTier;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Block registrations.
 *
 * <p>Registry names are persisted in world saves: never rename or remove one once released. Only the Starter tier is
 * registered in milestone 1 (owner decision); Voltaic/Ionic/Resonant/Entropic are added when their specs arrive, using
 * the same {@link #registerFarmMatrix} helper.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VirtualFarmWorks.MODID);

    public static final DeferredBlock<FarmMatrixBlock> STARTER_FARM_MATRIX = registerFarmMatrix(MachineTier.STARTER);

    private ModBlocks() {
    }

    /** Registers {@code <tier>_farm_matrix} with the properties shared by every Farm Matrix. */
    private static DeferredBlock<FarmMatrixBlock> registerFarmMatrix(MachineTier tier) {
        return BLOCKS.registerBlock(
                tier.getSerializedName() + "_farm_matrix",
                properties -> new FarmMatrixBlock(tier, properties),
                properties -> properties
                        .mapColor(MapColor.METAL)
                        .strength(3.5F, 6.0F)
                        .sound(SoundType.METAL)
                        // Any pickaxe mines it (tag data/minecraft/tags/block/mineable/pickaxe.json); no tier tag.
                        .requiresCorrectToolForDrops()
                        // The owner's Blockbench model is not a full opaque cube: without this, faces of neighbor
                        // blocks behind it would be culled and look like holes (x-ray).
                        .noOcclusion());
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
