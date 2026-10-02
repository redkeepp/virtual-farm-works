/*
 * ItemResource — (1.21.1 line) an immutable item TYPE: an item and its data components, without a count. VFW's
 * stand-in for NeoForge 26.1's transfer-API class of the same name, which 1.21.1 does not have, so the logic both lines
 * share (harvest, autocrafter, held drops) reads the same on both.
 */
package com.virtualfarmworks.transfer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * An item type: the key of every per-item map and tally in VFW (drops, held drops, the autocrafter's buffer and
 * stock). Two resources are equal when their items and component patches are equal, i.e. exactly when vanilla's
 * {@link ItemStack#isSameItemSameComponents} would say so for stacks of them.
 *
 * <h2>Cost (the lesson from the 26.1 line)</h2>
 * A resource's hash covers all its components, which can cost hundreds of nanoseconds for items with many of them. It
 * is computed once per object and cached, but every {@link #of(ItemStack)} makes a new object: never build and hash
 * resources per slot or per cell in a hot loop. Group slots by comparing items first ({@code getItem() ==}, then
 * {@link #equals}) or use {@link #matches(ItemStack)}, which compares without creating anything.
 *
 * <p>Immutable: the component patch is never changed after creation (vanilla's patches are copy-on-write).
 */
public final class ItemResource {
    /** No item. Never stored in maps or saves. */
    public static final ItemResource EMPTY = new ItemResource(Items.AIR, DataComponentPatch.EMPTY, 0);

    /**
     * Saved form: {@code {id, components}}. Encoding components may need registries (enchantments...), so encode it
     * with registry ops ({@code HolderLookup.Provider#createSerializationContext}). Never encode {@link #EMPTY}.
     */
    public static final Codec<ItemResource> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemResource::holder),
                    DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                            .forGetter(ItemResource::components))
            .apply(instance, (holder, components) -> new ItemResource(holder.value(), components, 0)));

    private final Item item;
    private final DataComponentPatch components;
    /** Cached max stack size; 0 = not computed yet. */
    private int maxStackSize;
    /** Cached hash; {@link #hashed} says whether it was computed. */
    private int hash;
    private boolean hashed;

    private ItemResource(Item item, DataComponentPatch components, int maxStackSize) {
        this.item = item;
        this.components = components;
        this.maxStackSize = maxStackSize;
    }

    /** The type of a stack (its count is ignored); {@link #EMPTY} for an empty stack. */
    public static ItemResource of(ItemStack stack) {
        if (stack.isEmpty()) {
            return EMPTY;
        }
        return new ItemResource(stack.getItem(), stack.getComponentsPatch(), stack.getMaxStackSize());
    }

    /** An item without extra components (what loot tables drop); {@link #EMPTY} for air. */
    public static ItemResource of(ItemLike itemLike) {
        Item item = itemLike.asItem();
        return item == Items.AIR ? EMPTY : new ItemResource(item, DataComponentPatch.EMPTY, 0);
    }

    public boolean isEmpty() {
        return item == Items.AIR;
    }

    public Item getItem() {
        return item;
    }

    private Holder<Item> holder() {
        return item.builtInRegistryHolder();
    }

    /** The components this type adds to (or removes from) its item's defaults. */
    public DataComponentPatch components() {
        return components;
    }

    /** Whether the item is in an item tag. */
    public boolean is(TagKey<Item> tag) {
        return holder().is(tag);
    }

    /** Whether this type is of {@code other} (its components aside). */
    public boolean is(Item other) {
        return item == other;
    }

    /** A new stack of this type ({@link ItemStack#EMPTY} for {@link #EMPTY} or a count below 1). */
    public ItemStack toStack(int count) {
        return isEmpty() || count <= 0 ? ItemStack.EMPTY : new ItemStack(holder(), count, components);
    }

    /** A new stack of one. */
    public ItemStack toStack() {
        return toStack(1);
    }

    /** Items of this type per stack, as vanilla counts it (components included). */
    public int getMaxStackSize() {
        int max = maxStackSize;
        if (max == 0) {
            max = isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE : toStack(1).getMaxStackSize();
            maxStackSize = max;
        }
        return max;
    }

    /** Whether a stack is of this type, without creating anything (false for an empty stack). */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == item && stack.getComponentsPatch().equals(components);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ItemResource resource && resource.item == item
                && resource.components.equals(components);
    }

    @Override
    public int hashCode() {
        if (!hashed) {
            hash = 31 * item.hashCode() + components.hashCode();
            hashed = true;
        }
        return hash;
    }

    @Override
    public String toString() {
        return components.isEmpty() ? item.toString() : item + components.toString();
    }
}
