/*
 * ModBlockEntities — DeferredRegister of VFW block entity types (one Farm Matrix type shared by every tier) and the
 * NeoForge capabilities they expose (items per face, energy on tiers that use it).
 */
package com.virtualfarmworks.registry;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * One block entity type for all Farm Matrix tiers: the tier is read from the block ({@code FarmMatrixBlock#tier()}),
 * so adding a tier only means adding its block to the valid-blocks list below. The registry name is persisted in
 * saves: never rename it.
 */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VirtualFarmWorks.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmMatrixBlockEntity>> FARM_MATRIX =
            BLOCK_ENTITY_TYPES.register("farm_matrix",
                    () -> BlockEntityType.Builder.of(FarmMatrixBlockEntity::new, ModBlocks.STARTER_FARM_MATRIX.get(),
                            ModBlocks.ENTROPIC_FARM_MATRIX.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(RegisterCapabilitiesEvent.class, ModBlockEntities::registerCapabilities);
    }

    /**
     * Items: the machine decides per face ({@code FarmMatrixBlockEntity#itemHandler}). The Starter shows only its output
     * buffer, extract-only, on every face (owner spec: nothing goes in from outside). Tiers with face modes follow each
     * face's mode: nothing, an extract-only view of the matching items, or the seed/soil grid input.
     * Energy: every face of the tiers that use it; none on the Starter.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FARM_MATRIX.get(),
                FarmMatrixBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FARM_MATRIX.get(),
                FarmMatrixBlockEntity::energyHandler);
    }
}
