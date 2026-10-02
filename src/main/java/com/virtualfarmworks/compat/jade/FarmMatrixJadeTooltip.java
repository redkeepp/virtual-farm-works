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
        // 1.21.1's CompoundTag getters answer 0 for a missing key; the keys with another default use doubleOr.
        MachineStatus status = MachineStatus.byOrdinal(data.getInt(FarmMatrixJadeData.STATUS));
        double progress = data.getDouble(FarmMatrixJadeData.PROGRESS);
        int plots = data.getInt(FarmMatrixJadeData.PLOTS);

        tooltip.add(Component.translatable(status.translationKey())
                .withStyle(style -> style.withColor(TextColor.fromRgb(status.tone().rgb()))));
        tooltip.add(Component.translatable("gui.virtualfarmworks.hydration",
                DisplayFormats.multiplier(doubleOr(data, FarmMatrixJadeData.HYDRATION, 1.0))));
        if (data.contains(FarmMatrixJadeData.CAPACITY)) {
            tooltip.add(Component.translatable("gui.virtualfarmworks.plots_active", plots,
                    data.getInt(FarmMatrixJadeData.CAPACITY)));
            tooltip.add(Component.translatable("gui.virtualfarmworks.plots_waiting",
                    data.getInt(FarmMatrixJadeData.WAITING)));
        } else {
            tooltip.add(Component.translatable("gui.virtualfarmworks.seeds", plots, MachineSlots.SEED_SOIL_LIMIT));
        }
        tooltip.add(Component.translatable("gui.virtualfarmworks.growth", DisplayFormats.growthPercent(progress, plots),
                DisplayFormats.multiplier(doubleOr(data, FarmMatrixJadeData.GROWTH, 1.0))));
        if (data.contains(FarmMatrixJadeData.ENERGY)) {
            tooltip.add(Component.translatable("gui.virtualfarmworks.energy",
                    data.getLong(FarmMatrixJadeData.ENERGY),
                    data.getLong(FarmMatrixJadeData.ENERGY_CAPACITY)));
        }
    }

    /** The double under {@code key}, or {@code fallback} when the server did not send it. */
    private static double doubleOr(CompoundTag data, String key, double fallback) {
        return data.contains(key) ? data.getDouble(key) : fallback;
    }

    @Override
    public ResourceLocation getUid() {
        return FarmMatrixJadeData.UID;
    }
}
