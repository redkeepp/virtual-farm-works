/*
 * ItemFilter — a config blacklist compiled into a fast matcher. Entries: exact item ids, whole mods ("modid:*") and
 * item tags ("#namespace:tag"). Built on config load/reload by VfwConfig, never per tick.
 */
package com.virtualfarmworks.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A compiled item blacklist built from config strings. Pack makers write entries in three forms:
 * <ul>
 *   <li>{@code "mysticalagriculture:diamond_seeds"} — one exact item;</li>
 *   <li>{@code "mysticalagriculture:*"} — every item of a namespace (mod);</li>
 *   <li>{@code "#c:seeds"} — every item in an item tag (tags can themselves be edited by datapacks/KubeJS).</li>
 * </ul>
 *
 * <p>Compiled once per config load/reload (see {@link VfwConfig}), never per tick. Matching is O(1) for ids and
 * namespaces; tags are checked with {@link ItemStack#is(TagKey)}, which is a cheap set lookup. Only called when a
 * machine revalidates its slots, not every tick.
 *
 * <p>Unknown ids are NOT an error: an entry for a mod that is not installed simply never matches. That lets pack
 * makers ship one config across pack variants.
 */
public final class ItemFilter {
    /** Accepts {@code ns:path}, {@code ns:*} and {@code #ns:path}. Used as the config list element validator. */
    private static final Pattern ENTRY = Pattern.compile("^#?[a-z0-9_.-]+:([a-z0-9_./-]+|\\*)$");

    public static final ItemFilter EMPTY = new ItemFilter(Set.of(), Set.of(), List.of());

    private final Set<Identifier> ids;
    private final Set<String> namespaces;
    private final List<TagKey<Item>> tags;

    private ItemFilter(Set<Identifier> ids, Set<String> namespaces, List<TagKey<Item>> tags) {
        this.ids = ids;
        this.namespaces = namespaces;
        this.tags = tags;
    }

    /** Config validator: whether a single list element is well-formed. Invalid entries are rejected by the spec. */
    public static boolean isValidEntry(Object entry) {
        return entry instanceof String s && ENTRY.matcher(s).matches();
    }

    /** Compiles config strings. Entries are expected to be pre-validated by {@link #isValidEntry(Object)}. */
    public static ItemFilter compile(List<? extends String> entries) {
        if (entries.isEmpty()) {
            return EMPTY;
        }
        Set<Identifier> ids = new HashSet<>();
        Set<String> namespaces = new HashSet<>();
        List<TagKey<Item>> tags = new ArrayList<>();
        for (String entry : entries) {
            if (entry.startsWith("#")) {
                tags.add(TagKey.create(Registries.ITEM, Identifier.parse(entry.substring(1))));
            } else if (entry.endsWith(":*")) {
                namespaces.add(entry.substring(0, entry.length() - 2));
            } else {
                ids.add(Identifier.parse(entry));
            }
        }
        return new ItemFilter(Set.copyOf(ids), Set.copyOf(namespaces), List.copyOf(tags));
    }

    public boolean isEmpty() {
        return ids.isEmpty() && namespaces.isEmpty() && tags.isEmpty();
    }

    /** Whether the stack's item is matched (i.e. blacklisted) by any entry. Empty stacks never match. */
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (ids.contains(id) || namespaces.contains(id.getNamespace())) {
            return true;
        }
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag)) {
                return true;
            }
        }
        return false;
    }
}
