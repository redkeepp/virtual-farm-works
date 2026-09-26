/*
 * Harvester — turns a harvest batch into items (drop source + pack-maker multipliers) and stores them in the output
 * buffers inside a NeoForge transaction: everything fits and is committed, or nothing changes. Anti-dupe core of the
 * machine.
 */
package com.virtualfarmworks.harvest;

import java.util.ArrayList;
import java.util.List;

import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.sim.DropTally;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The two halves of a harvest batch (the machine harvests a cycle in one or more batches, see
 * {@code sim.HarvestBatching}), kept separate on purpose:
 * <ol>
 *   <li>{@link #roll} decides WHAT a batch of plots yields (random, may evaluate loot tables). The machine keeps the
 *       result until it is stored, so an OUTPUT FULL machine retries with the same items instead of re-rolling loot
 *       every time the buffer changes (cheaper, and no "re-roll until a small harvest fits" bias).</li>
 *   <li>{@link #tryStore} inserts that result atomically, visible buffer first, then the hidden one.</li>
 * </ol>
 *
 * <h2>Why this cannot dupe or void items</h2>
 * {@link #tryStore} opens a ROOT transaction and inserts every item into the output handlers. If anything does not
 * fit, the method returns without committing; closing the transaction rolls every insertion back (NeoForge transfer
 * API, {@code SnapshotJournal}). Only after a successful commit does the machine count the batch's plots as harvested
 * ({@code GrowthCycle#harvestPlots}), on the same server thread, in the same tick — so a batch is stored exactly once:
 * never partially, never twice (fast clicks, automation or reconnects cannot interleave inside a tick). The one
 * exception, {@link #storeWhatFits}, is only used for a batch that could never fit, and the machine keeps the rest.
 */
public final class Harvester {
    private Harvester() {
    }

    /**
     * Rolls the drops of {@code plots} ripe plots (a batch) and applies the config multipliers: MAIN drops x
     * {@code drops.productionMultiplier} x {@code machines.<tier>.productionMultiplier}, SECONDARY drops x
     * {@code drops.secondaryDropMultiplier}. Server thread only (reads the server config and evaluates loot).
     */
    public static List<DropTally.Entry<ItemResource>> roll(DropSource source, int plots, MachineTier tier,
                                                           DropSource.Context context) {
        DropTally<ItemResource> tally = new DropTally<>();
        source.roll(plots, context, tally);
        double main = VfwServerConfig.GLOBAL_PRODUCTION_MULTIPLIER.get()
                * VfwServerConfig.machine(tier).productionMultiplier.get();
        double secondary = VfwServerConfig.SECONDARY_DROP_MULTIPLIER.get();
        return tally.finish(main, secondary, context.random()::nextDouble);
    }

    /** {@link #tryStore(List, List)} into a single handler. */
    public static boolean tryStore(List<DropTally.Entry<ItemResource>> drops, ResourceHandler<ItemResource> output) {
        return tryStore(drops, List.of(output));
    }

    /**
     * Stores every drop, or nothing at all. Each drop fills {@code targets} in order: the machine passes its visible
     * buffer first, then the hidden one (owner design, step 8).
     *
     * @return true when everything was stored and committed (the machine may now count those plots as harvested);
     *         false when it did not fit (nothing changed) or when a transaction is already open on this thread (never
     *         expected during a block entity tick; refusing is safer than nesting, because an outer transaction could
     *         still be rolled back after the machine had already counted the plots, which would void the harvest)
     */
    public static boolean tryStore(List<DropTally.Entry<ItemResource>> drops,
                                   List<ResourceHandler<ItemResource>> targets) {
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (DropTally.Entry<ItemResource> drop : drops) {
                if (insertInOrder(drop.key(), drop.amount(), targets, transaction) < drop.amount()) {
                    return false; // not committed: leaving the try block rolls every insertion back
                }
            }
            transaction.commit();
            return true;
        }
    }

    /**
     * Extreme case only (a batch too big even for empty buffers, see the machine): stores as much of each drop as fits,
     * commits it, and returns what is left, in the same order. Stores nothing (returns {@code drops}) when a transaction
     * is already open on this thread, for the reason given in {@link #tryStore(List, List)}.
     */
    public static List<DropTally.Entry<ItemResource>> storeWhatFits(List<DropTally.Entry<ItemResource>> drops,
                                                                    List<ResourceHandler<ItemResource>> targets) {
        if (drops.isEmpty() || Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return drops;
        }
        List<DropTally.Entry<ItemResource>> left = new ArrayList<>();
        try (Transaction transaction = Transaction.openRoot()) {
            for (DropTally.Entry<ItemResource> drop : drops) {
                long stored = insertInOrder(drop.key(), drop.amount(), targets, transaction);
                if (stored < drop.amount()) {
                    left.add(new DropTally.Entry<>(drop.key(), drop.amount() - stored));
                }
            }
            transaction.commit();
        }
        return List.copyOf(left);
    }

    /** Output slots the drops need when every slot is empty: one per started stack of each item. */
    public static long slotsNeeded(List<DropTally.Entry<ItemResource>> drops) {
        long slots = 0;
        for (DropTally.Entry<ItemResource> drop : drops) {
            int stack = maxStack(drop.key());
            slots += (drop.amount() + stack - 1) / stack;
        }
        return slots;
    }

    /** Output slots the drops fill, fractional ({@code amount / max stack size}, summed): for {@code YieldSample}. */
    public static double slotsFilled(List<DropTally.Entry<ItemResource>> drops) {
        double slots = 0.0;
        for (DropTally.Entry<ItemResource> drop : drops) {
            slots += (double) drop.amount() / maxStack(drop.key());
        }
        return slots;
    }

    /**
     * Inserts up to {@code amount} into the targets in order, completing existing stacks of the same item before using
     * empty slots in each target (the plain handler insert would take the first slot with room and fragment the
     * buffer). Returns how much was inserted, inside {@code transaction}.
     */
    private static long insertInOrder(ItemResource resource, long amount, List<ResourceHandler<ItemResource>> targets,
                                      Transaction transaction) {
        long inserted = 0;
        for (ResourceHandler<ItemResource> target : targets) {
            while (inserted < amount) {
                int chunk = (int) Math.min(amount - inserted, Integer.MAX_VALUE);
                int moved = ResourceHandlerUtil.insertStacking(target, resource, chunk, transaction);
                inserted += moved;
                if (moved < chunk) {
                    break; // this target is full for this item: try the next one
                }
            }
            if (inserted == amount) {
                break;
            }
        }
        return inserted;
    }

    /** Items per slot, as the output handlers count it (never 0; capped like ItemStacksResourceHandler). */
    private static int maxStack(ItemResource resource) {
        return Math.clamp(resource.getMaxStackSize(), 1, Item.ABSOLUTE_MAX_STACK_SIZE);
    }
}
