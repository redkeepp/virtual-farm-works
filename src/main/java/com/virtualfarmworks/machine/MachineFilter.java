/*
 * MachineFilter — a Farm Matrix's harvest filter (owner design, step 8): WHITELIST (only the listed items are
 * produced) or BLACKLIST (the listed items are never produced), with entries at fixed positions on pages of 3x3 ghost
 * slots. Saved with the machine; the harvest reads an immutable snapshot (harvest.HarvestFilter).
 */
package com.virtualfarmworks.machine;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.virtualfarmworks.harvest.HarvestFilter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Entries are ITEM TYPES in ghost slots: the player shows an item (or drags one from JEI), the filter remembers its
 * type, nothing is consumed. Positions are kept exactly where the player put them (page {@code p}, slot {@code s} =
 * position {@code p * 9 + s}), so pages the player created stay as they left them. An item can be listed once.
 *
 * <p>An EMPTY filter never filters, in either mode (owner rule): a new machine, or one whose list was cleared,
 * produces everything, as before the filter existed.
 *
 * <p>Size limit (owner-approved): {@link #MAX_PAGES} pages of 9, i.e. 144 items. A crop drops a handful of items;
 * the cap only stops a machine's save data from growing without bound.
 */
public final class MachineFilter {
    public static final int PAGE_SIZE = 9;
    public static final int MAX_PAGES = 16;
    public static final int MAX_POSITIONS = PAGE_SIZE * MAX_PAGES;

    /** Saved form of one entry. The item is stored by id so a removed mod only drops its entries, not the list. */
    private record SavedEntry(int position, String item) {
        static final Codec<SavedEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("slot").forGetter(SavedEntry::position),
                Codec.STRING.fieldOf("item").forGetter(SavedEntry::item)).apply(instance, SavedEntry::new));
    }

    private final Runnable onChange;
    private final TreeMap<Integer, Item> entries = new TreeMap<>();
    private boolean whitelist;
    /** Bumped on every change; the machine re-rolls a waiting harvest batch when it differs (see HarvestKey). */
    private int version;
    private @Nullable HarvestFilter snapshot;

    /** @param onChange called after every change made by a player (the machine saves and retries its harvest) */
    public MachineFilter(Runnable onChange) {
        this.onChange = onChange;
    }

    public @Nullable Item get(int position) {
        return entries.get(position);
    }

    /**
     * Puts an item type at a position, or clears it ({@code item} null or air).
     *
     * @return false (nothing changes) when the position is out of range or the item is already listed elsewhere
     */
    public boolean set(int position, @Nullable Item item) {
        if (position < 0 || position >= MAX_POSITIONS) {
            return false;
        }
        if (item == null || item == Items.AIR) {
            if (entries.remove(position) == null) {
                return true; // already empty
            }
        } else {
            Item current = entries.get(position);
            if (current == item) {
                return true;
            }
            if (entries.containsValue(item)) {
                return false; // listed once only
            }
            entries.put(position, item);
        }
        changed();
        return true;
    }

    public boolean isWhitelist() {
        return whitelist;
    }

    public void toggleMode() {
        whitelist = !whitelist;
        changed();
    }

    /** Page index of the last page holding an entry (0 when the filter is empty). */
    public int lastUsedPage() {
        return entries.isEmpty() ? 0 : entries.lastKey() / PAGE_SIZE;
    }

    public int version() {
        return version;
    }

    /** What the harvest uses; rebuilt only after a change. */
    public HarvestFilter snapshot() {
        HarvestFilter current = snapshot;
        if (current == null) {
            current = entries.isEmpty() ? HarvestFilter.NONE // empty = no filtering, whatever the mode
                    : new HarvestFilter(whitelist, new HashSet<>(entries.values()));
            snapshot = current;
        }
        return current;
    }

    private void changed() {
        snapshot = null;
        version++;
        onChange.run();
    }

    // --- persistence ------------------------------------------------------------------------------------------------

    public void save(ValueOutput out) {
        out.putBoolean("whitelist", whitelist);
        ValueOutput.TypedOutputList<SavedEntry> list = out.list("entries", SavedEntry.CODEC);
        for (Map.Entry<Integer, Item> entry : entries.entrySet()) {
            list.add(new SavedEntry(entry.getKey(), BuiltInRegistries.ITEM.getKey(entry.getValue()).toString()));
        }
    }

    /** Restores a saved filter; entries of items that no longer exist (mod removed) or bad positions are skipped. */
    public void load(ValueInput in) {
        whitelist = in.getBooleanOr("whitelist", false);
        entries.clear();
        Set<Item> seen = new HashSet<>();
        for (SavedEntry saved : in.listOrEmpty("entries", SavedEntry.CODEC)) {
            ResourceLocation id = ResourceLocation.tryParse(saved.item());
            Item item = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR && saved.position() >= 0 && saved.position() < MAX_POSITIONS && seen.add(item)) {
                entries.put(saved.position(), item);
            }
        }
        snapshot = null;
        version++;
    }
}
