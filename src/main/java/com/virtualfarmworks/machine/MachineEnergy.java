/*
 * MachineEnergy — the FE buffer of Farm Matrix tiers that use energy (Entropic): accepts FE from cables on every face,
 * pays the machine's consumption per tick, and resizes itself when the config changes the capacity.
 */
package com.virtualfarmworks.machine;

import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;

/**
 * NeoForge {@link SimpleEnergyHandler} (transaction-aware insertion, saved amount) with what the machine needs on top:
 * <ul>
 *   <li>capacity set from the config: plot capacity x FE per plot x 3 (owner formula: the buffer always holds three
 *       ticks of the highest possible consumption), recomputed when the config changes;</li>
 *   <li>{@link #consume}: the machine's own drain, outside any transaction (server thread, in its tick);</li>
 *   <li>extraction by other blocks is refused: the buffer only feeds the machine.</li>
 * </ul>
 * Changes are reported through {@code onChange} so the machine saves them (throttled by the machine).
 */
public final class MachineEnergy extends SimpleEnergyHandler {
    private final Runnable onChange;

    /** @param onChange called after committed insertions and after {@link #consume} */
    public MachineEnergy(Runnable onChange) {
        super(0, Integer.MAX_VALUE, 0);
        this.onChange = onChange;
    }

    /** Sets the capacity (config); energy above a lowered capacity is lost, like any full buffer. */
    public void setCapacity(long capacity) {
        this.capacity = (int) Math.clamp(capacity, 0, Integer.MAX_VALUE);
        if (energy > this.capacity) {
            energy = this.capacity;
        }
    }

    /** Whether the buffer can pay {@code amount} right now. */
    public boolean canPay(long amount) {
        return energy >= amount;
    }

    /** Takes {@code amount} FE for the machine's own use; callers check {@link #canPay} first. */
    public void consume(long amount) {
        if (amount <= 0) {
            return;
        }
        energy = (int) Math.max(0, energy - amount);
        onChange.run();
    }

    @Override
    protected void onEnergyChanged(int previousAmount) {
        onChange.run();
    }
}
