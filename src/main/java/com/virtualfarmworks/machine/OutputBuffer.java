/*
 * OutputBuffer — the visible output buffer of a Farm Matrix (Starter 9 slots, Entropic 24). The machine inserts
 * harvests into it and players may take or put items by hand (menu slots); automation (pipes, hoppers, other machines)
 * can only EXTRACT, through the extract-only views.
 */
package com.virtualfarmworks.machine;

import java.util.function.Predicate;

import com.virtualfarmworks.transfer.ItemSlots;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Owner spec, first version: "these 9 slots are only a buffer for the items produced by the seeds; items only come
 * out, never go in." Owner revision (step 8): players may now put items in by hand, like an inventory; automated input
 * stays impossible. The buffer itself accepts insertion (the {@code Harvester} and the menu slots need it); automation
 * only ever gets an extract-only view (capabilities). Items the machine's harvest filter rejects are removed by the
 * machine, including ones a player puts here.
 *
 * <p>Every change calls {@code onChange}: the machine uses it to retry a harvest that was blocked by OUTPUT FULL only
 * when space may have appeared, instead of retrying every tick.
 */
public final class OutputBuffer extends ItemSlots {
    private final int minimumSize;
    private final Runnable onChange;
    private final IItemHandler externalView = new ExtractOnly(this, stack -> true);

    public OutputBuffer(int slots, Runnable onChange) {
        super(slots);
        this.minimumSize = slots;
        this.onChange = onChange;
    }

    @Override
    protected void onContentsChanged(int index) {
        onChange.run();
    }

    /** The handler given to the outside world: extraction works, insertion always inserts nothing. */
    public IItemHandler externalView() {
        return externalView;
    }

    /**
     * An extract-only view that only shows and gives the stacks {@code shown} accepts (a face exporting only what the
     * plants produce, or only what the autocrafter made). Other slots look empty through it. The predicate runs on
     * every pipe query: keep it cheap.
     */
    public IItemHandler externalView(Predicate<ItemStack> shown) {
        return new ExtractOnly(this, shown);
    }

    public boolean isEmpty() {
        for (int i = 0; i < size(); i++) {
            if (!isEmpty(i)) {
                return false;
            }
        }
        return true;
    }

    /** A copy of the stack in a slot (safe to read). */
    public ItemStack stackInSlot(int index) {
        return getStackInSlot(index).copy();
    }

    /** After loading a save, guarantees at least the tier's visible slot count (never shrinks). */
    void ensureMinimumSize() {
        if (size() >= minimumSize) {
            return;
        }
        NonNullList<ItemStack> resized = NonNullList.withSize(minimumSize, ItemStack.EMPTY);
        for (int i = 0; i < size(); i++) {
            resized.set(i, stackInSlot(i));
        }
        setStacks(resized);
    }

    /**
     * Extract-only wrapper: inserts nothing; stacks {@code shown} rejects look like empty slots and cannot be taken.
     * Callers must not modify the stacks it shows ({@code IItemHandler} contract).
     */
    private static final class ExtractOnly implements IItemHandler {
        private final OutputBuffer buffer;
        private final Predicate<ItemStack> shown;

        ExtractOnly(OutputBuffer buffer, Predicate<ItemStack> shown) {
            this.buffer = buffer;
            this.shown = shown;
        }

        @Override
        public int getSlots() {
            return buffer.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            ItemStack stack = buffer.getStackInSlot(index);
            return stack.isEmpty() || shown.test(stack) ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int index, int amount, boolean simulate) {
            ItemStack stack = buffer.getStackInSlot(index);
            return stack.isEmpty() || !shown.test(stack) ? ItemStack.EMPTY : buffer.extractItem(index, amount, simulate);
        }

        @Override
        public int getSlotLimit(int index) {
            return buffer.getSlotLimit(index);
        }

        @Override
        public boolean isItemValid(int index, ItemStack stack) {
            return false;
        }
    }
}
