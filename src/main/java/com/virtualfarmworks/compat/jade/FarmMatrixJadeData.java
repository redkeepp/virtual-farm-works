/*
 * FarmMatrixJadeData — server half of the Jade integration: copies a few numbers from the looked-at Farm Matrix into
 * the data Jade sends to the client (status, progress, hydration, growth multiplier, planted plots).
 */
package com.virtualfarmworks.compat.jade;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.sim.GrowthSpeed;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Called by Jade only while a player looks at a machine (and throttled by Jade), so it adds no per-tick cost. The keys
 * below are read by {@link FarmMatrixJadeTooltip}; keep both classes in sync.
 */
enum FarmMatrixJadeData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, "farm_matrix");
    static final String STATUS = "vfw_status";
    static final String PROGRESS = "vfw_progress";
    static final String HYDRATION = "vfw_hydration";
    static final String GROWTH = "vfw_growth";
    static final String PLOTS = "vfw_plots";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof FarmMatrixBlockEntity machine)) {
            return;
        }
        GrowthSpeed speed = machine.speed();
        data.putInt(STATUS, machine.status().ordinal());
        data.putDouble(PROGRESS, machine.progress());
        data.putDouble(HYDRATION, speed.hydration());
        data.putDouble(GROWTH, speed.upgrades() * speed.soil());
        data.putInt(PLOTS, machine.totalPlots());
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
