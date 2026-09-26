/*
 * Harvester — turns a due harvest into items (drop source + pack-maker multipliers) and stores them in the output
 * buffer inside a NeoForge transaction: everything fits and is committed, or nothing changes (OUTPUT FULL). Anti-dupe
 * core of the machine.
 */
package com.virtualfarmworks.harvest;

import java.util.List;

import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.sim.DropTally;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The two halves of a harvest, kept separate on purpose:
 * <ol>
 *   <li>{@link #roll} decides WHAT the harvest yields (random, may evaluate loot tables). The machine keeps the result
 *       until it is stored, so an OUTPUT FULL machine retries with the same items instead of re-rolling loot every
 *       time the buffer changes (cheaper, and no "re-roll until a small harvest fits" bias).</li>
 *   <li>{@link #tryStore} inserts that result atomically.</li>
 * </ol>
 *
 * <h2>Why this cannot dupe or void items</h2>
 * {@link #tryStore} opens a ROOT transaction and inserts every item into the output handler. If anything does not fit,
 * the method returns without committing; closing the transaction rolls every insertion back (NeoForge transfer API,
 * {@code SnapshotJournal}). Only after a successful commit does the machine close the growth cycle
 * ({@code GrowthCycle#completeHarvest}), on the same server thread, in the same tick — so a harvest is stored exactly
 * once: never partially, never twice (fast clicks, automation or reconnects cannot interleave inside a tick).
 */
public final class Harvester {
    private Harvester() {
    }

    /**
     * Rolls the drops of {@code plots} ACTIVE plots and applies the config multipliers: MAIN drops x
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

    /**
     * Stores every drop in {@code output}, or nothing at all.
     *
     * @return true when everything was stored and committed (the machine may now complete the cycle); false when it
     *         did not fit (OUTPUT FULL — nothing changed) or when a transaction is already open on this thread (never
     *         expected during a block entity tick; refusing is safer than nesting, because an outer transaction could
     *         still be rolled back after the machine had already completed its cycle, which would void the harvest)
     */
    public static boolean tryStore(List<DropTally.Entry<ItemResource>> drops, ResourceHandler<ItemResource> output) {
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return false;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (DropTally.Entry<ItemResource> drop : drops) {
                long remaining = drop.amount();
                while (remaining > 0) {
                    int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                    // Stacking insert: complete existing stacks of the same item first, then use empty slots. The
                    // plain handler insert would take the first slot with room and fragment the 9-slot buffer.
                    int inserted = ResourceHandlerUtil.insertStacking(output, drop.key(), chunk, transaction);
                    if (inserted < chunk) {
                        return false; // not committed: leaving the try block rolls every insertion back
                    }
                    remaining -= inserted;
                }
            }
            transaction.commit();
            return true;
        }
    }
}
