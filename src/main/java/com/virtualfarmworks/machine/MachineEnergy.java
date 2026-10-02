/*
 * MachineEnergy — the FE buffer of Farm Matrix tiers that use energy (Entropic): accepts FE from cables on every face,
 * pays the machine's consumption per tick, resizes itself when the config changes the capacity, and tells the GUI what
 * the machine uses right now.
 */
package com.virtualfarmworks.machine;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.EnergyStorage;

/**
 * NeoForge 1.21.1's {@link EnergyStorage} (an {@code IEnergyStorage}: cables insert with simulate/execute) with what the
 * machine needs on top:
 * <ul>
 *   <li>capacity set from the config: plot capacity x FE per plot x 3 (owner formula: the buffer always holds three
 *       ticks of the highest possible consumption), recomputed when the config changes. FE is an {@code int} on this
 *       API (as on 26.1's): a capacity above about 2.1 billion FE is clamped;</li>
 *   <li>{@link #consume}: the machine's own drain, in its tick (server thread);</li>
 *   <li>extraction by other blocks is refused: the buffer only feeds the machine.</li>
 * </ul>
 * Changes are reported through {@code onChange} so the machine saves them (throttled by the machine).
 *
 * <h2>What the GUI shows ({@link #shownAmount})</h2>
 * A source refills the buffer every tick and the machine pays every tick, in an order the game decides (block
 * entities tick in placement order). Read right after the machine paid, a buffer kept full by its source looks one
 * tick of consumption short (owner report: 1,025,280 / 1,036,800 with 128 plots). The GUI therefore shows the level
 * before the machine's payment of its last tick: a buffer a source keeps up with reads full, and one that drains still
 * falls tick by tick.
 */
public final class MachineEnergy extends EnergyStorage {
    private final Runnable onChange;
    /** FE the machine paid in its last tick; 0 when it did not pay (stopped, waiting, harvesting). */
    private long lastUse;

    /** @param onChange called after insertions that changed the amount (not simulated) and after {@link #consume} */
    public MachineEnergy(Runnable onChange) {
        super(0, Integer.MAX_VALUE, 0);
        this.onChange = onChange;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int received = super.receiveEnergy(toReceive, simulate);
        if (received > 0 && !simulate) {
            onChange.run();
        }
        return received;
    }

    /** Sets the capacity (config); energy above a lowered capacity is lost, like any full buffer. */
    public void setCapacity(long capacity) {
        this.capacity = (int) Math.clamp(capacity, 0, Integer.MAX_VALUE);
        if (energy > this.capacity) {
            energy = this.capacity;
        }
    }

    /** Sets the stored FE, kept within 0..capacity (game tests and tools; 26.1's handler has the same method). */
    public void set(int amount) {
        energy = Math.clamp(amount, 0, capacity);
        onChange.run();
    }

    /** FE stored now (26.1 name, kept so the machine code reads the same on both lines). */
    public long getAmountAsLong() {
        return energy;
    }

    /** The buffer's size. */
    public long getCapacityAsLong() {
        return capacity;
    }

    /** Whether the buffer can pay {@code amount} right now. */
    public boolean canPay(long amount) {
        return energy >= amount;
    }

    /** Takes {@code amount} FE for the machine's own use this tick; callers check {@link #canPay} first. */
    public void consume(long amount) {
        if (amount <= 0) {
            idle();
            return;
        }
        energy = (int) Math.max(0, energy - amount);
        lastUse = amount;
        onChange.run();
    }

    /** The machine did not pay this tick (called once per tick when it does not {@link #consume}). */
    public void idle() {
        lastUse = 0;
    }

    /** FE the machine uses per tick right now: what it paid in its last tick (0 while it does not grow). */
    public long lastUse() {
        return lastUse;
    }

    /** The level before the machine's own payment of its last tick, for the GUI (see the class doc). */
    public long shownAmount() {
        return Math.min(capacity, (long) energy + lastUse);
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    /** Saved form: {@code {amount}}. The capacity is not saved (it comes from the config). */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("amount", energy);
        return tag;
    }

    /**
     * Restores the amount. The capacity is set by the machine's next revalidation, which also trims an amount above a
     * lowered capacity; until then the saved amount is kept as it is.
     */
    public void load(CompoundTag tag) {
        energy = Math.max(0, tag.getInt("amount"));
    }
}
