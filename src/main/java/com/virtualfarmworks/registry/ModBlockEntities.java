/*
 * ModBlockEntities — DeferredRegister of VFW block entity types (one Farm Matrix type shared by every tier) and the
 * NeoForge capabilities they expose (extract-only output buffer on every face).
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
                    () -> new BlockEntityType<>(FarmMatrixBlockEntity::new, ModBlocks.STARTER_FARM_MATRIX.get()));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(RegisterCapabilitiesEvent.class, ModBlockEntities::registerCapabilities);
    }

    /**
     * Pipes, hoppers and other mods see ONLY the output buffer, and can only extract from it (owner spec: the buffer
     * never accepts items from outside). Same view on every face and from no face (null side). Inputs (seed, soil,
     * upgrades) are not exposed to automation.
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, FARM_MATRIX.get(),
                (machine, side) -> machine.externalOutput());
    }
}
