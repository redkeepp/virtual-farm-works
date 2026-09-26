/*
 * VfwJadePlugin — optional Jade integration: looking at a Farm Matrix shows its status, hydration, planted seeds and
 * growth (the same lines as the GUI) without opening it. Discovered by Jade; never loaded when Jade is absent.
 */
package com.virtualfarmworks.compat.jade;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.block.FarmMatrixBlock;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Two providers, split on purpose: {@link FarmMatrixJadeData} runs on the SERVER (reads the machine, sends a few
 * numbers when a player looks at it — Jade throttles the requests), {@link FarmMatrixJadeTooltip} runs on the CLIENT
 * (turns those numbers into text). Keeping them apart avoids loading tooltip code on a dedicated server.
 */
@WailaPlugin(VirtualFarmWorks.MODID)
public final class VfwJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FarmMatrixJadeData.INSTANCE, FarmMatrixBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(FarmMatrixJadeTooltip.INSTANCE, FarmMatrixBlock.class);
    }
}
