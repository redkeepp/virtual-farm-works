package com.virtualfarmworks;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(VirtualFarmWorks.MODID)
public class VirtualFarmWorks {
    public static final String MODID = "virtualfarmworks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VirtualFarmWorks(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Virtual Farm Works loading");
    }
}
