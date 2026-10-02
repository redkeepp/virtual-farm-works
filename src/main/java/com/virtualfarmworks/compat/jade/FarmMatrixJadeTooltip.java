/*
 * FarmMatrixJadeTooltip — client half of the Jade integration: writes the Farm Matrix lines under Jade's block name,
 * with the same texts and formats as the machine GUI (shared lang keys and DisplayFormats).
 */
package com.virtualfarmworks.compat.jade;

import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.menu.DisplayFormats;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Reads the keys written by {@link FarmMatrixJadeData}; shows nothing until the server data has arrived. The lines
 * follow the tier's GUI: the Starter shows "Seeds: x/64"; grid tiers (Entropic) show active plots over the plot
 * capacity and the waiting plots; tiers with energy add the FE the GUI shows.
 */
enum FarmMatrixJadeTooltip implements IBlockComponentProvider {
    INSTANCE;

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(FarmMatrixJadeData.STATUS)) {
            return;
        }
        MachineStatus status = MachineStatus.byOrdinal(data.getIntOr(FarmMatrixJadeData.STATUS, 0));
        double progress = data.getDoubleOr(FarmMatrixJadeData.PROGRESS, 0.0);
        int plots = data.getIntOr(FarmMatrixJadeData.PLOTS, 0);

        tooltip.add(Component.translatable(status.translationKey())
                .withStyle(style -> style.withColor(TextColor.fromRgb(status.tone().rgb()))));
        tooltip.add(Component.translatable("gui.virtualfarmworks.hydration",
                DisplayFormats.multiplier(data.getDoubleOr(FarmMatrixJadeData.HYDRATION, 1.0))));
        if (data.contains(FarmMatrixJadeData.CAPACITY)) {
            tooltip.add(Component.translatable("gui.virtualfarmworks.plots_active", plots,
                    data.getIntOr(FarmMatrixJadeData.CAPACITY, 0)));
            tooltip.add(Component.translatable("gui.virtualfarmworks.plots_waiting",
                    data.getIntOr(FarmMatrixJadeData.WAITING, 0)));
        } else {
            tooltip.add(Component.translatable("gui.virtualfarmworks.seeds", plots, MachineSlots.SEED_SOIL_LIMIT));
        }
        tooltip.add(Component.translatable("gui.virtualfarmworks.growth", DisplayFormats.growthPercent(progress, plots),
                DisplayFormats.multiplier(data.getDoubleOr(FarmMatrixJadeData.GROWTH, 1.0))));
        if (data.contains(FarmMatrixJadeData.ENERGY)) {
            tooltip.add(Component.translatable("gui.virtualfarmworks.energy",
                    data.getLongOr(FarmMatrixJadeData.ENERGY, 0L),
                    data.getLongOr(FarmMatrixJadeData.ENERGY_CAPACITY, 0L)));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return FarmMatrixJadeData.UID;
    }
}
