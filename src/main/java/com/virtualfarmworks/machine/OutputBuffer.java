/*
 * OutputBuffer — the visible output buffer of a Farm Matrix (Starter 9 slots, Entropic 24). The machine inserts
 * harvests into it and players may take or put items by hand (menu slots); automation (pipes, hoppers, other machines)
 * can only EXTRACT, through the extract-only views.
 */
package com.virtualfarmworks.machine;

import java.util.function.Predicate;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

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
public final class OutputBuffer extends ItemStacksResourceHandler {
    private final int minimumSize;
    private final Runnable onChange;
    private final ResourceHandler<ItemResource> externalView = new ExtractOnly(this, resource -> true);

    public OutputBuffer(int slots, Runnable onChange) {
        super(slots);
        this.minimumSize = slots;
        this.onChange = onChange;
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChange.run();
    }

    /** The handler given to the outside world: extraction works, insertion always inserts nothing. */
    public ResourceHandler<ItemResource> externalView() {
        return externalView;
    }

    /**
     * An extract-only view that only shows and gives the items {@code shown} accepts (a face exporting only what the
     * plants produce, or only what the autocrafter made). Other slots look empty through it.
     */
    public ResourceHandler<ItemResource> externalView(Predicate<ItemResource> shown) {
        return new ExtractOnly(this, shown);
    }

    public boolean isEmpty() {
        for (int i = 0; i < size(); i++) {
            if (getAmountAsLong(i) > 0) {
                return false;
            }
        }
        return true;
    }

    /** A copy of the stack in a slot (safe to read). */
    public ItemStack stackInSlot(int index) {
        return getResource(index).toStack(getAmountAsInt(index));
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

    /** Extract-only wrapper: inserts nothing; items {@code shown} rejects look like empty slots and cannot be taken. */
    private static final class ExtractOnly extends DelegatingResourceHandler<ItemResource> {
        private final Predicate<ItemResource> shown;

        ExtractOnly(ResourceHandler<ItemResource> delegate, Predicate<ItemResource> shown) {
            super(delegate);
            this.shown = shown;
        }

        @Override
        public ItemResource getResource(int index) {
            ItemResource resource = super.getResource(index);
            return resource.isEmpty() || shown.test(resource) ? resource : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            ItemResource resource = super.getResource(index);
            return resource.isEmpty() || shown.test(resource) ? super.getAmountAsLong(index) : 0;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return false;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            // Report the real capacity for existing contents (fullness checks), 0 for anything new.
            return resource.isEmpty() || resource.equals(getResource(index)) ? super.getCapacityAsLong(index, resource) : 0;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return shown.test(resource) ? super.extract(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return shown.test(resource) ? super.extract(resource, amount, transaction) : 0;
        }
    }
}
