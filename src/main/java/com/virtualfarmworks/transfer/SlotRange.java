/*
 * SlotRange — (1.21.1 line) a run of slots [from, to) of one of VFW's inventories: where a harvest may store (the
 * whole visible output, the usable part of the hidden one). Stands in for NeoForge 26.1's ranged resource handlers.
 */
package com.virtualfarmworks.transfer;

/**
 * @param slots the inventory
 * @param from  first slot, inclusive
 * @param to    last slot, exclusive ({@code from == to} = no slot at all)
 */
public record SlotRange(ItemSlots slots, int from, int to) {
    /** Every slot of an inventory (as large as it is when this is called). */
    public static SlotRange all(ItemSlots slots) {
        return new SlotRange(slots, 0, slots.size());
    }

    /** Number of slots in the range. */
    public int size() {
        return to - from;
    }
}
