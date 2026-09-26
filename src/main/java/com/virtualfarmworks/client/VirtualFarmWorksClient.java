/*
 * VirtualFarmWorksClient — client-only mod entry point (loaded only on the physical client): registers screens and
 * other client-side hooks. Keep every client class (screens, renderers) under com.virtualfarmworks.client.
 */
package com.virtualfarmworks.client;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.registry.ModMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Second {@code @Mod} class for the same mod id, restricted to {@link Dist#CLIENT}: FML only loads it on the client, so
 * a dedicated server never touches client classes (which would crash it).
 */
@Mod(value = VirtualFarmWorks.MODID, dist = Dist.CLIENT)
public final class VirtualFarmWorksClient {
    public VirtualFarmWorksClient(IEventBus modEventBus) {
        modEventBus.addListener(RegisterMenuScreensEvent.class,
                event -> event.register(ModMenus.FARM_MATRIX.get(), FarmMatrixScreen::new));
    }
}
