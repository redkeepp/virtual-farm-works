/*
 * HarvestFilter — immutable snapshot of a machine's harvest filter (owner design, step 8), read by the harvest:
 * WHITELIST = only the listed items are produced, BLACKLIST = the listed items are never produced.
 */
package com.virtualfarmworks.harvest;

import java.util.Set;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Matches by item type only (components ignored): crops drop plain items, and a ghost slot shows one item per type.
 * An EMPTY list lets everything through in both modes (owner rule: an empty whitelist must not stop the machine), so
 * the filter only acts once the player lists something. The default, {@link #NONE}, is an empty blacklist.
 *
 * @param whitelist true = WHITELIST mode, false = BLACKLIST mode
 * @param items     the listed item types
 */
public record HarvestFilter(boolean whitelist, Set<Item> items) {
    /** No filtering (every machine's default). */
    public static final HarvestFilter NONE = new HarvestFilter(false, Set.of());

    public HarvestFilter {
        items = Set.copyOf(items);
    }

    /** Whether the harvest may produce this item. */
    public boolean allows(ItemResource resource) {
        return items.isEmpty() || items.contains(resource.getItem()) == whitelist;
    }
}
