/*
 * FarmMatrixJadeData — server half of the Jade integration: copies a few numbers from the looked-at Farm Matrix into
 * the data Jade sends to the client (status, progress, hydration, growth multiplier, planted plots; for grid tiers the
 * plot capacity and waiting plots; for tiers with energy the stored FE).
 */
package com.virtualfarmworks.compat.jade;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineEnergy;
import com.virtualfarmworks.sim.GrowthSpeed;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Called by Jade only while a player looks at a machine (and throttled by Jade), so it adds no per-tick cost. The keys
 * below are read by {@link FarmMatrixJadeTooltip}; keep both classes in sync. The optional keys decide which lines the
 * tooltip shows, the same as the tier's GUI.
 */
enum FarmMatrixJadeData implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, "farm_matrix");
    static final String STATUS = "vfw_status";
    static final String PROGRESS = "vfw_progress";
    static final String HYDRATION = "vfw_hydration";
    static final String GROWTH = "vfw_growth";
    static final String PLOTS = "vfw_plots";
    /** Grid tiers only (Entropic): plot capacity and waiting plots, the GUI's "Active plots" and "Waiting plots". */
    static final String CAPACITY = "vfw_capacity";
    static final String WAITING = "vfw_waiting";
    /** Tiers with energy only: the FE the GUI shows and the buffer size. */
    static final String ENERGY = "vfw_energy";
    static final String ENERGY_CAPACITY = "vfw_energy_capacity";

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
        if (machine.layout().groups() > 1) {
            data.putInt(CAPACITY, VfwServerConfig.machine(machine.tier()).plotCapacity(machine.layout()));
            data.putInt(WAITING, machine.waitingPlots());
        }
        MachineEnergy energy = machine.energy();
        if (energy != null) {
            data.putLong(ENERGY, energy.shownAmount());
            data.putLong(ENERGY_CAPACITY, energy.getCapacityAsLong());
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }
}
