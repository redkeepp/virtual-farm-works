/*
 * GridOutput — what a pipe sees on a face set to OUTPUT ONLY SEEDS AND SOILS (owner, 2026-10-02): an extract-only view
 * of the seed and soil grids, so automation (and the machine's own auto-export) can take the planted seeds and soils
 * out, e.g. to empty a machine into a chest.
 */
package com.virtualfarmworks.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Covers the grid slots {@code [0, 2 x groups)} of the machine's {@link MachineInventory}, like {@link GridInput}, the
 * other way round: extraction works, insertion always inserts nothing ({@link FaceMode#INPUT} is the way in). The
 * water provider, the catalyst and the upgrades are never shown.
 *
 * <p>Taking seeds or soils out is the same as a player taking them by hand: the inventory reports the change, the
 * machine re-validates its plot groups on its next tick and the removed plots go (waiting ones first). Nothing else is
 * needed for anti-dupe: the plots are counters derived from the slots.
 */
public final class GridOutput implements ResourceHandler<ItemResource> {
    private final MachineInventory inputs;
    private final int size;

    public GridOutput(MachineInventory inputs) {
        this.inputs = inputs;
        this.size = 2 * inputs.layout().groups();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public ItemResource getResource(int index) {
        return inputs.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return inputs.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        // The real capacity for what the slot holds (fullness checks), 0 for anything new: nothing goes in here.
        return resource.isEmpty() || resource.equals(getResource(index)) ? inputs.getCapacityAsLong(index, resource) : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return false;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index >= 0 && index < size ? inputs.extract(index, resource, amount, transaction) : 0;
    }
}
