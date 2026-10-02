/*
 * Harvester — turns a harvest batch into items (drop source + pack-maker multipliers) and stores them in the output
 * buffers inside a SlotTransaction: everything fits and is committed, or nothing changes. Anti-dupe core of the
 * machine.
 */
package com.virtualfarmworks.harvest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.transfer.ItemResource;
import com.virtualfarmworks.transfer.ItemSlots;
import com.virtualfarmworks.transfer.SlotRange;
import com.virtualfarmworks.transfer.SlotTransaction;

import net.minecraft.world.item.Item;

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
 * {@link #tryStore} plans every insertion on a {@link SlotTransaction} (1.21.1 has no NeoForge transactions: see that
 * class). If anything does not fit, the method returns without committing: nothing was written, no change callback
 * ran. Only after a successful commit does the machine count the batch's plots as harvested
 * ({@code GrowthCycle#harvestPlots}), on the same server thread, in the same tick — so a batch is stored exactly once:
 * never partially, never twice (fast clicks, automation or reconnects cannot interleave inside a tick). The one
 * exception, {@link #storeWhatFits}, is only used for a batch that could never fit, and the machine keeps the rest.
 *
 * <p>Targets and sources are always the machine's own buffers, so the transaction sees every slot involved. (On the
 * 26.1 line these methods also refuse to run inside an open NeoForge transaction; 1.21.1 has no such global state.)
 */
public final class Harvester {
    private Harvester() {
    }

    /**
     * Rolls the drops of {@code plots} ripe plots (a batch), leaving out what the machine's harvest filter rejects
     * ({@link DropSource.Context#filter()}), and applies the config multipliers: MAIN drops x
     * {@code drops.productionMultiplier} x {@code machines.<tier>.productionMultiplier}, SECONDARY drops x
     * {@code drops.secondaryDropMultiplier}. Server thread only (reads the server config and evaluates loot).
     */
    public static List<DropTally.Entry<ItemResource>> roll(DropSource source, int plots, MachineTier tier,
                                                           DropSource.Context context) {
        // The machine's harvest filter is part of the tally: rejected items are never counted, never become items.
        DropTally<ItemResource> tally = new DropTally<>(context.filter()::allows);
        source.roll(plots, context, tally);
        double main = VfwServerConfig.GLOBAL_PRODUCTION_MULTIPLIER.get()
                * VfwServerConfig.machine(tier).productionMultiplier.get();
        double secondary = VfwServerConfig.SECONDARY_DROP_MULTIPLIER.get();
        return tally.finish(main, secondary, context.random()::nextDouble);
    }

    /** {@link #tryStore(List, List)} into a single inventory (all its slots). */
    public static boolean tryStore(List<DropTally.Entry<ItemResource>> drops, ItemSlots output) {
        return tryStore(drops, List.of(SlotRange.all(output)));
    }

    /**
     * Stores every drop, or nothing at all. Each drop fills {@code targets} in order: the machine passes its visible
     * buffer first, then the hidden one (owner design, step 8).
     *
     * @return true when everything was stored and committed (the machine may now count those plots as harvested);
     *         false when it did not fit (nothing changed)
     */
    public static boolean tryStore(List<DropTally.Entry<ItemResource>> drops, List<SlotRange> targets) {
        return tryTakeAndStore(Map.of(), List.of(), drops, targets);
    }

    /**
     * {@link #tryStore(List, List)} that first takes {@code take} out of {@code sources}, in the SAME root transaction:
     * the autocrafter used items already in the output and its results replace them (owner, 2026-09-29). Everything
     * happens, or nothing: a take that falls short or drops that do not fit roll the whole transaction back.
     */
    public static boolean tryTakeAndStore(Map<ItemResource, Long> take, List<? extends ItemSlots> sources,
                                          List<DropTally.Entry<ItemResource>> drops, List<SlotRange> targets) {
        SlotTransaction transaction = new SlotTransaction();
        if (!takeAll(take, sources, transaction)) {
            return false; // not committed: nothing changed
        }
        for (DropTally.Entry<ItemResource> drop : drops) {
            if (insertInOrder(drop.key(), drop.amount(), targets, transaction) < drop.amount()) {
                return false;
            }
        }
        transaction.commit();
        return true;
    }

    /**
     * Extreme case only (a batch too big even for empty buffers, see the machine): stores as much of each drop as fits,
     * commits it, and returns what is left, in the same order.
     */
    public static List<DropTally.Entry<ItemResource>> storeWhatFits(List<DropTally.Entry<ItemResource>> drops,
                                                                    List<SlotRange> targets) {
        List<DropTally.Entry<ItemResource>> left = takeAndStoreWhatFits(Map.of(), List.of(), drops, targets);
        return left != null ? left : drops;
    }

    /**
     * {@link #storeWhatFits} that first takes {@code take} out of {@code sources} in the same transaction. Returns null,
     * with nothing changed, when the take falls short: the caller must then keep its whole plan (the autocrafter's
     * results may only exist once their ingredients are gone).
     */
    public static @Nullable List<DropTally.Entry<ItemResource>> takeAndStoreWhatFits(
            Map<ItemResource, Long> take, List<? extends ItemSlots> sources,
            List<DropTally.Entry<ItemResource>> drops, List<SlotRange> targets) {
        if (take.isEmpty() && drops.isEmpty()) {
            return drops;
        }
        SlotTransaction transaction = new SlotTransaction();
        if (!takeAll(take, sources, transaction)) {
            return null;
        }
        List<DropTally.Entry<ItemResource>> left = new ArrayList<>();
        for (DropTally.Entry<ItemResource> drop : drops) {
            long stored = insertInOrder(drop.key(), drop.amount(), targets, transaction);
            if (stored < drop.amount()) {
                left.add(new DropTally.Entry<>(drop.key(), drop.amount() - stored));
            }
        }
        transaction.commit();
        return List.copyOf(left);
    }

    /** Takes every amount of {@code take} out of the sources in order; false when one falls short. */
    private static boolean takeAll(Map<ItemResource, Long> take, List<? extends ItemSlots> sources,
                                   SlotTransaction transaction) {
        for (Map.Entry<ItemResource, Long> wanted : take.entrySet()) {
            long missing = wanted.getValue();
            for (ItemSlots source : sources) {
                if (missing <= 0) {
                    break;
                }
                missing -= transaction.extract(source, wanted.getKey(), missing);
            }
            if (missing > 0) {
                return false;
            }
        }
        return true;
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
     * empty slots in each target (inserting into the first slot with room would fragment the buffer). Returns how much
     * was inserted, inside {@code transaction}.
     */
    private static long insertInOrder(ItemResource resource, long amount, List<SlotRange> targets,
                                      SlotTransaction transaction) {
        long inserted = 0;
        for (SlotRange target : targets) {
            inserted += transaction.insertStacking(target, resource, amount - inserted);
            if (inserted >= amount) {
                break;
            }
        }
        return inserted;
    }

    /** Items per slot, as the output handlers count it (never 0; capped like NeoForge's ItemStackHandler). */
    private static int maxStack(ItemResource resource) {
        return Math.clamp(resource.getMaxStackSize(), 1, Item.ABSOLUTE_MAX_STACK_SIZE);
    }
}
