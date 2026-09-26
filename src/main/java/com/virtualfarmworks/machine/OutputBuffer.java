/*
 * OutputBuffer — the 9-slot output buffer of a Farm Matrix. The machine inserts harvests into it; everything outside
 * the machine (players, pipes, hoppers, auto-export targets) can only EXTRACT through externalView().
 */
package com.virtualfarmworks.machine;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Owner spec: "these 9 slots are only a buffer for the items produced by the seeds; items only come out, never go
 * in." The buffer itself accepts insertion (the {@code Harvester} needs it); the rule is enforced by only ever exposing
 * {@link #externalView()} to the outside (capabilities) and output-only slots in the menu.
 *
 * <p>Every change calls {@code onChange}: the machine uses it to retry a harvest that was blocked by OUTPUT FULL only
 * when space may have appeared, instead of retrying every tick.
 */
public final class OutputBuffer extends ItemStacksResourceHandler {
    private final Runnable onChange;
    private final ResourceHandler<ItemResource> externalView = new ExtractOnly(this);

    public OutputBuffer(Runnable onChange) {
        super(MachineSlots.OUTPUT_COUNT);
        this.onChange = onChange;
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChange.run();
    }

    /** The only handler given to the outside world: extraction works, insertion always inserts nothing. */
    public ResourceHandler<ItemResource> externalView() {
        return externalView;
    }

    public boolean isEmpty() {
        for (int i = 0; i < size(); i++) {
            if (getAmountAsLong(i) > 0) {
                return false;
            }
        }
        return true;
    }

    /** A copy of the stack in a slot (safe to read). */
    public ItemStack stackInSlot(int index) {
        return getResource(index).toStack(getAmountAsInt(index));
    }

    /** After loading a save, guarantees at least {@link MachineSlots#OUTPUT_COUNT} slots (never shrinks). */
    void ensureMinimumSize() {
        if (size() >= MachineSlots.OUTPUT_COUNT) {
            return;
        }
        NonNullList<ItemStack> resized = NonNullList.withSize(MachineSlots.OUTPUT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < size(); i++) {
            resized.set(i, stackInSlot(i));
        }
        setStacks(resized);
    }

    /** Extract-only wrapper: reports every resource as invalid for insertion and inserts nothing. */
    private static final class ExtractOnly extends DelegatingResourceHandler<ItemResource> {
        ExtractOnly(ResourceHandler<ItemResource> delegate) {
            super(delegate);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return false;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            // Report the real capacity for existing contents (fullness checks), 0 for anything new.
            return resource.isEmpty() || resource.equals(getResource(index)) ? super.getCapacityAsLong(index, resource) : 0;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
