/*
 * HandlerSlot — (1.21.1 line) a menu slot over one of VFW's own inventories (ItemSlots): NeoForge 1.21.1's
 * SlotItemHandler, made to report every change to the machine and to use the inventory's own capacity. Stands in for
 * NeoForge 26.1's ResourceHandlerSlot.
 */
package com.virtualfarmworks.menu;

import com.virtualfarmworks.transfer.ItemSlots;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Two differences from {@link SlotItemHandler}:
 * <ul>
 *   <li>Changes reach the inventory's callback exactly once each. Vanilla's shift-click merge grows the stack a slot
 *       shows IN PLACE and then only calls {@link #setChanged()}, which on a plain {@code SlotItemHandler} goes to an
 *       empty dummy container: the machine would never hear about it (no revalidation, no save). {@link #setChanged()}
 *       is forwarded to {@link ItemSlots#slotChanged}, and {@link #set} writes through {@code setStackInSlot} alone
 *       (which already reports the change).</li>
 *   <li>{@link #getMaxStackSize(ItemStack)} is the inventory's own limit for that item, so grid slots configured above a
 *       stack ({@code seedsPerSlot}) take more than a stack by hand too, as on the 26.1 line.</li>
 * </ul>
 */
public class HandlerSlot extends SlotItemHandler {
    private final ItemSlots slots;

    public HandlerSlot(ItemSlots slots, int index, int x, int y) {
        super(slots, index, x, y);
        this.slots = slots;
    }

    @Override
    public void set(ItemStack stack) {
        slots.setStackInSlot(index, stack);
    }

    @Override
    public void setChanged() {
        slots.slotChanged(index);
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return slots.getCapacity(index, stack);
    }
}
