/*
 * ModDataComponents — DeferredRegister of VFW's item data components: the autocrafter recipes a broken machine keeps on
 * its item (owner, 2026-09-30), and the tooltip line that shows them.
 */
package com.virtualfarmworks.registry;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.CrafterRecipes;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.tooltip.TooltipAppender;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Component ids are saved on items (chests, player inventories): never rename them. */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, VirtualFarmWorks.MODID);

    /** The autocrafter's recipes on a machine item; placing the item gives them to the machine. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CrafterRecipes>> CRAFTER_RECIPES =
            COMPONENTS.registerComponentType("crafter_recipes", builder -> builder
                    .persistent(CrafterRecipes.CODEC)
                    .networkSynchronized(CrafterRecipes.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
        // Modded components show no tooltip unless registered here (vanilla lists its own components one by one).
        modEventBus.addListener(RegisterTooltipAppendersEvent.class, event -> event.registerComponentAppenderAfterAll(
                CRAFTER_RECIPES, TooltipAppender.createComponentAppender(CRAFTER_RECIPES.get())));
    }
}
