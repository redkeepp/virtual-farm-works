/*
 * SlotTransaction — (1.21.1 line) all-or-nothing changes to VFW's own inventories (ItemSlots): every insertion and
 * extraction is planned on copies of the slots it touches, and only commit() writes them. Stands in for NeoForge 26.1's
 * transfer transactions wherever several item types must be stored together (a harvest batch, the autocrafter).
 */
package com.virtualfarmworks.transfer;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.item.ItemStack;

/**
 * NeoForge 1.21.1's {@code IItemHandler} only has "simulate, then execute" for ONE call: two simulated insertions of
 * different items into the same buffer do not see each other, so simulating a whole harvest batch item by item could
 * promise the same empty slot twice. This class keeps its own view of every slot it changed instead:
 * <ul>
 *   <li>{@link #peek} = the slot as this transaction sees it (planned contents, else the real stack);</li>
 *   <li>insertions and extractions only update that view, with the inventory's own rules ({@code isItemValid},
 *       {@code getStackLimit});</li>
 *   <li>{@link #commit()} writes each changed slot once with {@code setStackInSlot}, so each fires its change callback
 *       once, as NeoForge 26.1 calls them at the end of a transaction;</li>
 *   <li>a transaction dropped without commit changes nothing and calls no callback: a harvest that does not fit leaves
 *       the machine exactly as it was (it must not even look like an output change, or the machine would retry every
 *       tick).</li>
 * </ul>
 * Only for VFW's own inventories, on the server thread, within one call (plan, then commit or drop): nothing else may
 * change those slots in between. Other mods' inventories are reached with plain simulate/execute calls instead.
 *
 * <p>Cost: one small array per inventory touched and a copy per changed slot, the same order of work as the 26.1
 * transaction journal.
 */
public final class SlotTransaction {
    /** Planned contents per inventory, by slot; null = unchanged. */
    private final Map<ItemSlots, ItemStack[]> planned = new IdentityHashMap<>(4);

    /** The slot as this transaction sees it. Read only: never modify the returned stack. */
    public ItemStack peek(ItemSlots slots, int index) {
        ItemStack[] changed = planned.get(slots);
        ItemStack stack = changed != null ? changed[index] : null;
        return stack != null ? stack : slots.getStackInSlot(index);
    }

    private void plan(ItemSlots slots, int index, ItemStack stack) {
        planned.computeIfAbsent(slots, inventory -> new ItemStack[inventory.size()])[index] = stack;
    }

    /**
     * Inserts into one slot as far as it accepts the item (its {@code isItemValid} and limit).
     *
     * @return how many were inserted
     */
    public int insert(ItemSlots slots, int index, ItemResource resource, int amount) {
        if (amount <= 0 || resource.isEmpty()) {
            return 0;
        }
        ItemStack current = peek(slots, index);
        if (!current.isEmpty() && !resource.matches(current)) {
            return 0;
        }
        int room = slots.getCapacity(index, current.isEmpty() ? resource.toStack() : current) - current.getCount();
        if (room <= 0) {
            return 0;
        }
        int moved = Math.min(room, amount);
        plan(slots, index, current.isEmpty() ? resource.toStack(moved)
                : current.copyWithCount(current.getCount() + moved));
        return moved;
    }

    /**
     * Inserts into the slots {@code [from, to)}: first completing stacks of the same item, then into empty slots, in
     * slot order (NeoForge's "insert stacking": a plain first-slot-with-room insert would fragment the buffer).
     *
     * @return how many were inserted
     */
    public long insertStacking(ItemSlots slots, int from, int to, ItemResource resource, long amount) {
        long inserted = 0;
        for (int i = from; i < to && inserted < amount; i++) {
            ItemStack current = peek(slots, i);
            if (!current.isEmpty() && resource.matches(current)) {
                inserted += insert(slots, i, resource, chunk(amount - inserted));
            }
        }
        for (int i = from; i < to && inserted < amount; i++) {
            if (peek(slots, i).isEmpty()) {
                inserted += insert(slots, i, resource, chunk(amount - inserted));
            }
        }
        return inserted;
    }

    /** {@link #insertStacking(ItemSlots, int, int, ItemResource, long)} over a {@link SlotRange}. */
    public long insertStacking(SlotRange range, ItemResource resource, long amount) {
        return insertStacking(range.slots(), range.from(), range.to(), resource, amount);
    }

    /**
     * Takes up to {@code amount} of an item out of one slot.
     *
     * @return how many were taken
     */
    public int extract(ItemSlots slots, int index, ItemResource resource, int amount) {
        ItemStack current = peek(slots, index);
        if (amount <= 0 || current.isEmpty() || !resource.matches(current)) {
            return 0;
        }
        int moved = Math.min(amount, current.getCount());
        plan(slots, index, moved == current.getCount() ? ItemStack.EMPTY
                : current.copyWithCount(current.getCount() - moved));
        return moved;
    }

    /**
     * Takes up to {@code amount} of an item out of the whole inventory, in slot order.
     *
     * @return how many were taken
     */
    public long extract(ItemSlots slots, ItemResource resource, long amount) {
        long taken = 0;
        for (int i = 0; i < slots.size() && taken < amount; i++) {
            taken += extract(slots, i, resource, chunk(amount - taken));
        }
        return taken;
    }

    /** Writes every planned slot: one {@code setStackInSlot}, hence one change callback, per changed slot. */
    public void commit() {
        for (Map.Entry<ItemSlots, ItemStack[]> entry : planned.entrySet()) {
            ItemSlots slots = entry.getKey();
            ItemStack[] changed = entry.getValue();
            for (int i = 0; i < changed.length; i++) {
                if (changed[i] != null) {
                    slots.setStackInSlot(i, changed[i]);
                }
            }
        }
        planned.clear();
    }

    /** Whether this transaction planned any change. */
    public boolean isEmpty() {
        return planned.isEmpty();
    }

    private static int chunk(long amount) {
        return (int) Math.min(amount, Integer.MAX_VALUE);
    }
}
