/*
 * GridOutput — what a pipe sees on a face set to OUTPUT ONLY SEEDS AND SOILS (owner, 2026-10-02): an extract-only view
 * of the seed and soil grids, so automation can take the planted seeds and soils out, e.g. to empty a machine.
 */
package com.virtualfarmworks.machine;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Covers the grid slots {@code [0, 2 x groups)} of the machine's {@link MachineInventory}, like {@link GridInput}, the
 * other way round: extraction works, insertion always gives the stack back ({@link FaceMode#INPUT} is the way in). The
 * water provider, the catalyst and the upgrades are never shown.
 *
 * <p>Taking seeds or soils out is the same as a player taking them by hand: the inventory reports the change, the
 * machine re-validates its plot groups on its next tick and the removed plots go (waiting ones first). Nothing else is
 * needed for anti-dupe: the plots are counters derived from the slots. Like any {@code ItemStackHandler}, one
 * extraction gives at most a stack, even from a grid slot configured above a stack.
 */
public final class GridOutput implements IItemHandler {
    private final MachineInventory inputs;
    private final int size;

    public GridOutput(MachineInventory inputs) {
        this.inputs = inputs;
        this.size = 2 * inputs.layout().groups();
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < size ? inputs.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return slot >= 0 && slot < size ? inputs.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= 0 && slot < size ? inputs.getSlotLimit(slot) : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }
}
