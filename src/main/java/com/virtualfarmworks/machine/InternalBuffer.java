/*
 * InternalBuffer — the hidden part of a Farm Matrix's output (owner design, step 8): harvests fill the visible 9-slot
 * OutputBuffer first, then these slots, and these refill the visible slots as they empty. Nobody outside the machine
 * sees or touches it (no GUI slots, no capability); breaking the machine deletes its contents (owner rule).
 */
package com.virtualfarmworks.machine;

import com.virtualfarmworks.transfer.ItemSlots;
import com.virtualfarmworks.transfer.SlotRange;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Size comes from the config ({@code machines.<tier>.internalBufferSlots}, Starter default 27) and may change while a
 * world runs, so the handler is resized instead of having a fixed size:
 * <ul>
 *   <li>growing adds empty slots;</li>
 *   <li>shrinking never deletes items: slots beyond the new size that still hold items are kept (harvests no longer
 *       fill them, they only drain into the visible buffer) and disappear once empty ({@link #setUsableSlots}).</li>
 * </ul>
 * Only the first {@link #usableSlots()} slots receive harvests ({@link #fillView()}); the refill drains every slot.
 */
public final class InternalBuffer extends ItemSlots {
    private final Runnable onChange;
    private int usableSlots;
    private SlotRange fillView = new SlotRange(this, 0, 0);

    /** @param onChange called after every change (the machine marks itself for saving) */
    public InternalBuffer(Runnable onChange) {
        super(0);
        this.onChange = onChange;
    }

    @Override
    protected void onContentsChanged(int index) {
        onChange.run();
    }

    /**
     * Sets how many slots harvests may fill (config). Resizes to {@code max(slots, last slot holding items + 1)}, so
     * no item is ever lost by a smaller config; call again after draining to drop the extra slots once they are empty.
     */
    public void setUsableSlots(int slots) {
        usableSlots = Math.max(0, slots);
        int size = Math.max(usableSlots, lastFilledSlot() + 1);
        if (size != size()) {
            NonNullList<ItemStack> resized = NonNullList.withSize(size, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(size, size()); i++) {
                resized.set(i, getStackInSlot(i));
            }
            setStacks(resized);
        }
        // With 0 usable slots harvests go to the visible buffer only (an empty range).
        fillView = new SlotRange(this, 0, usableSlots);
    }

    public int usableSlots() {
        return usableSlots;
    }

    /** What harvests may fill: the first {@link #usableSlots()} slots. */
    public SlotRange fillView() {
        return fillView;
    }

    /** Empty slots among the usable ones (used to size harvest batches). */
    public int emptyUsableSlots() {
        int empty = 0;
        for (int i = 0; i < Math.min(usableSlots, size()); i++) {
            if (isEmpty(i)) {
                empty++;
            }
        }
        return empty;
    }

    public boolean isEmpty() {
        return lastFilledSlot() < 0;
    }

    /** A copy of the stack in a slot (safe to read). */
    public ItemStack stackInSlot(int index) {
        return getStackInSlot(index).copy();
    }

    /** Loading replaces the slot list with the saved one; resize it for the current config again. */
    @Override
    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        super.load(registries, tag);
        setUsableSlots(usableSlots);
    }

    private int lastFilledSlot() {
        for (int i = size() - 1; i >= 0; i--) {
            if (!isEmpty(i)) {
                return i;
            }
        }
        return -1;
    }
}
