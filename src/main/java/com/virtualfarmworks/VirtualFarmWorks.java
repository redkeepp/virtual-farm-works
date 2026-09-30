/*
 * VirtualFarmWorks — mod entry point (@Mod). Wires registries, configs, data maps, game tests and game-bus listeners
 * onto the event buses. Contains no game logic; see CLAUDE.md for the project map.
 */
package com.virtualfarmworks;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.virtualfarmworks.config.VfwConfig;
import com.virtualfarmworks.data.ModDataMaps;
import com.virtualfarmworks.gametest.VfwGameTests;
import com.virtualfarmworks.network.CrafterRecipesPayload;
import com.virtualfarmworks.network.SetCrafterGridPayload;
import com.virtualfarmworks.network.SetFilterGhostPayload;
import com.virtualfarmworks.plant.SoilRules;
import com.virtualfarmworks.registry.ModBlockEntities;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.registry.ModCreativeTabs;
import com.virtualfarmworks.registry.ModDataComponents;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.registry.ModMenus;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Mod entry point. Keep this class thin: it only wires the registries and configs onto the mod event bus. Game logic
 * lives in the feature packages ({@code block}, {@code item}, {@code machine}, ...).
 */
@Mod(VirtualFarmWorks.MODID)
public class VirtualFarmWorks {
    public static final String MODID = "virtualfarmworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VirtualFarmWorks(IEventBus modEventBus, ModContainer modContainer) {
        // Blocks must be registered before items that reference them (DeferredRegister resolves lazily, but keep
        // the order readable anyway).
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModDataComponents.register(modEventBus); // the autocrafter recipes a broken machine keeps on its item
        ModBlockEntities.register(modEventBus); // also registers capabilities
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModDataMaps.register(modEventBus);
        VfwConfig.register(modEventBus, modContainer);
        SetFilterGhostPayload.register(modEventBus); // JEI drag-and-drop onto the harvest filter
        SetCrafterGridPayload.register(modEventBus); // JEI "+" and drag-and-drop onto the autocrafter grid
        CrafterRecipesPayload.register(modEventBus); // the autocrafter recipe list, to viewers
        VfwGameTests.register(modEventBus); // no-op in production

        // Game (not mod) bus listeners.
        SoilRules.register(NeoForge.EVENT_BUS);
    }
}
