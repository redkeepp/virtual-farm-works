/*
 * FilterPageView — server side of the harvest filter's ghost slots: presents one 3x3 page of a machine's
 * MachineFilter as a vanilla Container, so the menu's 9 filter slots sync to the client with vanilla slot sync.
 */
package com.virtualfarmworks.menu;

import java.util.Arrays;
import java.util.function.IntSupplier;

import com.virtualfarmworks.machine.MachineFilter;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Ghost semantics: the "items" are the filter's item types shown as stacks of 1. They can be set (the menu turns a
 * click with an item into a type), but never taken out as real items: removal returns nothing. The page comes from
 * the viewing menu (each player browses pages independently).
 *
 * <p>Vanilla reads every slot every tick to find changes, so the 9 stacks are cached and rebuilt only when the
 * filter or the page changed.
 */
final class FilterPageView implements Container {
    private final MachineFilter filter;
    private final IntSupplier page;
    private final ItemStack[] stacks = new ItemStack[MachineFilter.PAGE_SIZE];
    private int cachedVersion = -1;
    private int cachedPage = -1;

    FilterPageView(MachineFilter filter, IntSupplier page) {
        this.filter = filter;
        this.page = page;
        Arrays.fill(stacks, ItemStack.EMPTY);
    }

    private int position(int slot) {
        return page.getAsInt() * MachineFilter.PAGE_SIZE + slot;
    }

    private void refresh() {
        int currentPage = page.getAsInt();
        if (cachedVersion == filter.version() && cachedPage == currentPage) {
            return;
        }
        for (int slot = 0; slot < stacks.length; slot++) {
            Item item = filter.get(currentPage * MachineFilter.PAGE_SIZE + slot);
            stacks[slot] = item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        cachedVersion = filter.version();
        cachedPage = currentPage;
    }

    @Override
    public int getContainerSize() {
        return MachineFilter.PAGE_SIZE;
    }

    @Override
    public boolean isEmpty() {
        refresh();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        refresh();
        return stacks[slot];
    }

    /** Ghosts are never taken out as items. */
    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    /** Records the stack's item type at this position (an empty stack clears it); duplicates are refused. */
    @Override
    public void setItem(int slot, ItemStack stack) {
        filter.set(position(slot), stack.isEmpty() ? null : stack.getItem());
    }

    @Override
    public void setChanged() {
        // The filter saves itself through the machine (MachineFilter's change callback).
    }

    @Override
    public boolean stillValid(Player player) {
        return true; // the menu's own stillValid guards access
    }

    @Override
    public void clearContent() {
        // Never cleared as a whole: entries are removed one by one by the player.
    }
}
