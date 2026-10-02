/*
 * ItemSlots — (1.21.1 line) base class of VFW's own inventories (machine inputs, visible and hidden output): NeoForge
 * 1.21.1's ItemStackHandler, plus the accessors the shared machine code uses, change callbacks that also catch the
 * in-place edits menus make, and a save format that keeps counts above 99.
 */
package com.virtualfarmworks.transfer;

import com.virtualfarmworks.VirtualFarmWorks;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Stands in for NeoForge 26.1's {@code ItemStacksResourceHandler} on this line. It IS an {@code IItemHandler}, so menus
 * ({@code menu.HandlerSlot}) and capability views use it directly; VFW's multi-item, all-or-nothing changes go through a
 * {@link SlotTransaction} instead of {@code insertItem} calls.
 *
 * <h2>Change callbacks</h2>
 * {@link #onContentsChanged} runs after every change made through this class (setting a slot, inserting, extracting).
 * Vanilla's container code also edits the stack a slot shows IN PLACE ({@code moveItemStackTo} grows it) and then only
 * calls {@code Slot#setChanged}; VFW's menu slots forward that to {@link #slotChanged}, so the machine hears about
 * every change. {@link #getStackInSlot} returns the stored stack itself, as NeoForge's handler does: callers outside
 * the menus must not modify it (copy with {@code stackInSlot} in subclasses, or use {@link #set}).
 *
 * <h2>Saving</h2>
 * Vanilla's item codec refuses counts above 99, and grid slots may hold more (config {@code seedsPerSlot}). Each slot is
 * saved as its {@link ItemResource} plus a separate count ({@link #save} / {@link #load}), never with
 * {@code ItemStack#save}.
 */
public abstract class ItemSlots extends ItemStackHandler {
    protected ItemSlots(int size) {
        super(size);
    }

    /** Number of slots (26.1 name of {@code getSlots}). */
    public int size() {
        return getSlots();
    }

    /** The item type in a slot ({@link ItemResource#EMPTY} when empty). Creates an object: not for hot loops. */
    public ItemResource getResource(int index) {
        return ItemResource.of(stacks.get(index));
    }

    /** How many items a slot holds. */
    public long getAmountAsLong(int index) {
        return stacks.get(index).getCount();
    }

    public int getAmountAsInt(int index) {
        return stacks.get(index).getCount();
    }

    /** Whether a slot holds nothing. */
    public boolean isEmpty(int index) {
        return stacks.get(index).isEmpty();
    }

    /** Puts {@code amount} of {@code resource} in a slot (empty with {@link ItemResource#EMPTY} or 0). No checks. */
    public void set(int index, ItemResource resource, int amount) {
        setStackInSlot(index, resource.toStack(amount));
    }

    /**
     * How many of an item a slot may hold (0 when it refuses the item). Subclasses change it through
     * {@link #getStackLimit}, the one place NeoForge's own insertion reads it too.
     */
    public int getCapacity(int index, ItemStack stack) {
        return isItemValid(index, stack) ? getStackLimit(index, stack) : 0;
    }

    /** {@link #getCapacity(int, ItemStack)} for an item type. */
    public long getCapacityAsLong(int index, ItemResource resource) {
        return resource.isEmpty() ? getSlotLimit(index) : getCapacity(index, resource.toStack());
    }

    /**
     * A menu slot changed the stack it shows in place (vanilla's {@code moveItemStackTo}): run the change callback as if
     * the slot had been set.
     */
    public void slotChanged(int index) {
        onContentsChanged(index);
    }

    /**
     * Replaces the slot list without change callbacks (loading, resizing). The new list is used as given.
     */
    protected void setStacks(NonNullList<ItemStack> list) {
        stacks = list;
    }

    // --- saving -----------------------------------------------------------------------------------------------------

    /**
     * Saved form: {@code {size, items: [{slot, item: {id, components}, count}]}}; counts may exceed a stack. Encoded
     * with registry ops (item components may refer to registries).
     */
    public CompoundTag save(HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ListTag items = new ListTag();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            Tag item = ItemResource.CODEC.encodeStart(ops, ItemResource.of(stack)).result().orElse(null);
            if (item == null) {
                continue; // an item that cannot be encoded (never expected) is left out rather than breaking the save
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", i);
            entry.put("item", item);
            entry.putInt("count", stack.getCount());
            items.add(entry);
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("size", stacks.size());
        tag.put("items", items);
        return tag;
    }

    /**
     * Restores what {@link #save} wrote. The slot count becomes the saved size (callers then make sure it is at least
     * their layout's); entries of items that no longer exist (a mod was removed) are skipped. No change callbacks.
     */
    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        int size = Math.max(0, tag.contains("size", Tag.TAG_INT) ? tag.getInt("size") : stacks.size());
        NonNullList<ItemStack> loaded = NonNullList.withSize(size, ItemStack.EMPTY);
        ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag entry = items.getCompound(i);
            int slot = entry.getInt("slot");
            int count = entry.getInt("count");
            Tag item = entry.get("item");
            if (slot < 0 || slot >= size || count <= 0 || item == null) {
                continue;
            }
            ItemResource.CODEC.parse(ops, item)
                    .resultOrPartial(error -> VirtualFarmWorks.LOGGER.error("Skipped an invalid saved item: {}", error))
                    .ifPresent(resource -> loaded.set(slot, resource.toStack(count)));
        }
        stacks = loaded;
        onLoad();
    }
}
