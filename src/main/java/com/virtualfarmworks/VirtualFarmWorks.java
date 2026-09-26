package com.virtualfarmworks;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.registry.ModCreativeTabs;
import com.virtualfarmworks.registry.ModItems;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

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
        ModCreativeTabs.register(modEventBus);
    }
}
