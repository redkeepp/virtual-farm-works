/*
 * FarmMatrixBlockEntity — the running Farm Matrix: owns the inventories (inputs, visible output, hidden output), the
 * global growth cycle, the cached analysis of its slots, the transactional batched harvest, auto-export and
 * persistence. One per machine block, server-ticked only.
 */
package com.virtualfarmworks.machine;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.virtualfarmworks.block.FarmMatrixBlock;
import com.virtualfarmworks.config.VfwConfig;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.harvest.DropSource;
import com.virtualfarmworks.harvest.HarvestPlans;
import com.virtualfarmworks.harvest.Harvester;
import com.virtualfarmworks.menu.FarmMatrixMenu;
import com.virtualfarmworks.plant.PlantAnalysis;
import com.virtualfarmworks.plant.SoilRules;
import com.virtualfarmworks.registry.ModBlockEntities;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.GrowthCycle;
import com.virtualfarmworks.sim.GrowthSpeed;
import com.virtualfarmworks.sim.HarvestBatching;
import com.virtualfarmworks.sim.MachineConditions;
import com.virtualfarmworks.sim.MachineStatus;
import com.virtualfarmworks.sim.YieldSample;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The machine. Design goal (owner): a machine with 6,000 plots must cost about the same per tick as one with 1.
 *
 * <h2>Per tick (server only, see {@link #serverTick})</h2>
 * <ol>
 *   <li>If something relevant changed since the last tick (a slot, the config generation, a tag/datapack reload),
 *       {@link #revalidate()} once. That is where all expensive work happens: pairing seed and soil, speed, drop
 *       source, status. Nothing expensive runs on a normal tick.</li>
 *   <li>If the visible output changed: refill it from the hidden buffer ({@link #refillVisibleOutput}).</li>
 *   <li>RUNNING and bar below 100%: {@code cycle.advance(progressPerTick)} — one addition.</li>
 *   <li>Bar at 100%: harvest one batch (see below). Otherwise the bar simply waits.</li>
 *   <li>Every {@code output.autoExportIntervalTicks}: push the visible output to adjacent inventories on enabled
 *       faces.</li>
 * </ol>
 *
 * <h2>Output: visible buffer, hidden buffer, plants (owner design, step 8)</h2>
 * Harvests fill the 9 visible slots ({@link OutputBuffer}) first, then the hidden ones ({@link InternalBuffer}, 27 on
 * the Starter by default); the hidden slots refill the visible ones as those empty. When both are full, the ripe
 * plots not harvested yet simply wait on the plant ({@code GrowthCycle#plotsToHarvest}): their items do not exist, so
 * nothing is stored anywhere else and nothing accumulates (the bar stays at 100% and the next cycle only starts once
 * every plot is harvested). This replaced a single all-or-nothing harvest that deadlocked when a harvest was bigger
 * than the whole output.
 *
 * <h2>Harvest batches (anti-dupe)</h2>
 * A due cycle is harvested in batches sized to the free output slots ({@code sim.HarvestBatching}): usually ONE batch
 * with every plot, several only while the output is the bottleneck, at most one batch per tick. Each batch is rolled
 * ONCE and kept in {@link #pendingBatch}; {@code Harvester#tryStore} stores it all-or-nothing in a NeoForge
 * transaction and only then are its plots counted as harvested. If it does not fit, status OUTPUT FULL and the SAME
 * drops are retried only when the output or the inputs changed ({@link #harvestRetryRequested}) — no loot re-rolls per
 * retry. The pending roll is dropped when the seed/soil/config/tags/Fertilized Essence switch it was rolled for change
 * ({@link HarvestKey}) or fewer plots are left; it is never saved (after a reload it is rolled again — nothing was
 * produced yet, so nothing can be duplicated or lost).
 *
 * <p>Extreme case (owner-approved): a batch too big even for EMPTY buffers (one plot yielding more than every output
 * slot, only possible with absurd multipliers) is stored as far as it fits and the rest is kept in {@link #heldDrops},
 * which is saved, stored before anything else, and blocks the cycle until empty.
 *
 * <h2>Persistence</h2>
 * Saved: inventories (inputs, visible and hidden output), held drops, progress, active/pending/harvested counters,
 * on/off switch, auto-output faces, Fertilized Essence switch, harvest filter. NOT saved (rebuilt by {@link #revalidate()} or
 * relearned): analysis, speed, status, drop source, pending batch, yield sample. Slot changes and harvests mark the
 * chunk for saving immediately; plain progress at most once per {@link #PROGRESS_SAVE_INTERVAL} ticks (losing < 1 s
 * of progress on a crash is harmless, marking every tick is not free).
 *
 * <h2>Breaking the machine</h2>
 * Inputs and the 9 visible output slots drop; the hidden buffer, held drops and ripe plots are deleted (owner rule).
 *
 * <h2>Chunk unload</h2>
 * No ticking while unloaded, no catch-up when reloaded, no chunk loading (owner rules).
 */
public class FarmMatrixBlockEntity extends BlockEntity implements MenuProvider {
    /** Owner spec: one global cycle; the Starter has one seed slot, so one plot group. */
    private static final int PLOT_GROUPS = 1;
    private static final int PROGRESS_SAVE_INTERVAL = 20;

    /** Saved form of {@link #heldDrops}: item and amount (amounts can exceed a stack, so not ItemStacks). */
    private static final Codec<List<DropTally.Entry<ItemResource>>> HELD_DROPS_CODEC = RecordCodecBuilder
            .<DropTally.Entry<ItemResource>>create(instance -> instance.group(
                    ItemResource.CODEC.fieldOf("item").forGetter(DropTally.Entry::key),
                    Codec.LONG.fieldOf("amount").forGetter(DropTally.Entry::amount))
                    .apply(instance, DropTally.Entry::new))
            .listOf();

    // --- persistent state -------------------------------------------------------------------------------------------
    private final MachineTier tier;
    private final MachineInventory inputs;
    private final OutputBuffer output;
    /** Hidden output slots behind the visible buffer (owner design, step 8); size from the tier config. */
    private final InternalBuffer internal;
    /** Extreme case only (see class doc): produced items that did not fit even into empty buffers. Stored first. */
    private List<DropTally.Entry<ItemResource>> heldDrops = List.of();
    private final GrowthCycle cycle = new GrowthCycle(PLOT_GROUPS);
    private boolean enabled = true;
    private int outputFaces = RelativeSide.ALL;
    /** GUI switch (owner spec): whether Mystical Agriculture crops produce Fertilized Essence. On by default. */
    private boolean fertilizedEssence = true;
    /**
     * Completed cycles since this block entity was loaded. Only its CHANGES matter (the GUI sees a cycle wrap and runs
     * the bar to the end before restarting it), so it is not saved.
     */
    private int completedHarvests;
    /** Harvest filter (owner, step 8): whitelist or blacklist of what the harvest may produce. */
    private final MachineFilter filter;

    // --- derived state (rebuilt by revalidate, never saved) ---------------------------------------------------------
    private boolean inputsDirty = true;
    private int validatedConfigGeneration = -1;
    private int validatedTagGeneration = -1;
    /** Plant type the plot counters refer to; used to detect a seed swap ("plant changed"). */
    private @Nullable Item plantedSeed;
    private PlantAnalysis analysis = PlantAnalysis.analyze(ItemStack.EMPTY, ItemStack.EMPTY, MachineTier.STARTER);
    private GrowthSpeed speed = GrowthSpeed.BASELINE;
    private double progressPerTick;
    private @Nullable DropSource dropSource;
    private @Nullable SourceKey dropSourceKey;
    private MachineStatus status = MachineStatus.MISSING_SEED;
    private boolean hasWaterProvider;
    private int growthUpgrades;
    // Config values cached at revalidation (no config reads in the per-tick path).
    private int exportInterval;
    private int maxLootRolls;
    private int internalSlots;
    private boolean hoeWears;
    private int hoeWearInterval;
    /** Ticks of RUNNING-with-a-needed-hoe since the hoe last lost durability (not saved: at most one interval lost). */
    private int hoeWearTicks;

    // --- harvest / export runtime state -----------------------------------------------------------------------------
    /** The next batch, rolled once and kept until stored (see class doc). */
    private @Nullable List<DropTally.Entry<ItemResource>> pendingBatch;
    private int pendingBatchPlots;
    private @Nullable HarvestKey pendingBatchKey;
    /** What one plot yields with the current drop source, measured from the rolled batches; sizes the next batch. */
    private final YieldSample yieldSample = new YieldSample();
    private @Nullable HarvestKey yieldSampleKey;
    private boolean harvestBlocked;
    /**
     * Set when something that may let a blocked harvest succeed has changed: the output (space may have appeared) or
     * the inputs (fewer plots, other seed...). A blocked machine retries only when this is set.
     */
    private boolean harvestRetryRequested;
    /** The visible output changed: move what fits from the hidden buffer into it on the next tick. */
    private boolean refillRequested;
    /** True while the machine itself refills the visible output (its change callback must not ask for another one). */
    private boolean refilling;
    private int exportCooldown;
    private int ticksSinceProgressSave;
    /** Capability caches of the 6 neighbours, by world direction ordinal; created lazily on the server. */
    private final @Nullable BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>[] exportTargets =
            newCacheArray();

    /** What a drop source depends on: seed and soil types, config and tags. Upgrades do not matter. */
    private record SourceKey(@Nullable Item seed, @Nullable Item soil, int configGeneration, int tagGeneration) {
    }

    /**
     * What a rolled batch (and the yield sample) depends on: its drop source inputs, the Fertilized Essence switch
     * (turning it off while a batch waits must drop the essence from that batch) and the harvest filter version (a
     * filter change must apply to a batch still waiting for space).
     */
    private record HarvestKey(@Nullable SourceKey source, boolean fertilizedEssence, int filterVersion) {
    }

    public FarmMatrixBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARM_MATRIX.get(), pos, state);
        this.tier = state.getBlock() instanceof FarmMatrixBlock block ? block.tier() : MachineTier.STARTER;
        this.inputs = new MachineInventory(tier, this::onInputsChanged);
        this.output = new OutputBuffer(this::onOutputChanged);
        this.internal = new InternalBuffer(this::markForSave);
        this.filter = new MachineFilter(this::onFilterChanged);
    }

    // =================================================================================================================
    // Tick
    // =================================================================================================================

    /** Server tick, called by the block's ticker. Cheap unless something changed (see class doc). */
    public void serverTick(ServerLevel level) {
        if (inputsDirty || validatedConfigGeneration != VfwConfig.generation()
                || validatedTagGeneration != SoilRules.cacheGeneration()) {
            revalidate();
        }
        if (refillRequested) {
            refillVisibleOutput();
        }

        if (hasOutputToStore()) {
            if (canStoreOutput()) {
                storeOutput(level);
            }
        } else if (status.isRunning()) {
            cycle.advance(progressPerTick);
            if (++ticksSinceProgressSave >= PROGRESS_SAVE_INTERVAL) {
                markForSave();
            }
        }

        if (hoeWears && status.isRunning() && analysis.needsHoe() && ++hoeWearTicks >= hoeWearInterval) {
            hoeWearTicks = 0;
            wearHoe(level);
        }

        autoExport(level);
    }

    /** Held drops, or a due harvest: the machine stores before it grows again (the bar waits at 100%). */
    private boolean hasOutputToStore() {
        return !heldDrops.isEmpty() || cycle.isHarvestDue();
    }

    /**
     * Held drops are stored whatever the machine's state (they were already produced). A due harvest is harvested
     * when every input requirement is met (RUNNING, or OUTPUT FULL which only means "the last attempt did not fit").
     * After a failed attempt, only once something changed that could make it succeed ({@link #harvestRetryRequested}),
     * so a full machine costs nothing per tick while it waits.
     */
    private boolean canStoreOutput() {
        if (heldDrops.isEmpty() && !inputsAllowHarvest()) {
            return false;
        }
        return !harvestBlocked || harvestRetryRequested;
    }

    private boolean inputsAllowHarvest() {
        return status == MachineStatus.RUNNING || status == MachineStatus.OUTPUT_FULL;
    }

    /**
     * One storing step: held drops first, then at most one harvest batch; completes the cycle once every plot is
     * harvested and stored. Sets {@link #harvestBlocked} (OUTPUT FULL) when something is left waiting for space.
     */
    private void storeOutput(ServerLevel level) {
        harvestRetryRequested = false;
        boolean blocked = false;
        if (!heldDrops.isEmpty()) {
            heldDrops = Harvester.storeWhatFits(heldDrops, fillTargets());
            markForSave();
            blocked = !heldDrops.isEmpty();
        }
        if (!blocked && cycle.plotsToHarvest() > 0 && inputsAllowHarvest()) {
            blocked = !harvestNextBatch(level) || !heldDrops.isEmpty();
        }
        if (cycle.isHarvestDue() && cycle.plotsToHarvest() == 0 && heldDrops.isEmpty()) {
            cycle.completeHarvest(); // every plot of the cycle harvested AND stored: the bar starts again
            completedHarvests++;
            markForSave();
        }
        harvestBlocked = blocked;
        updateStatus();
    }

    /**
     * Rolls the next batch (once, see class doc) and stores it all-or-nothing. Its plots count as harvested only after
     * a committed store.
     *
     * @return true when the batch was stored (or, in the extreme case, stored in part with the rest held — the caller
     *         then sees the held drops); false when it does not fit yet: the batch is kept and the ripe plots wait
     */
    private boolean harvestNextBatch(ServerLevel level) {
        int plotsLeft = cycle.plotsToHarvest(0);
        HarvestKey key = new HarvestKey(dropSourceKey, fertilizedEssence, filter.version());
        if (!key.equals(yieldSampleKey)) {
            yieldSample.reset(); // other seed/soil/config/tags/switch: the old measurements say nothing
            yieldSampleKey = key;
        }
        if (pendingBatch == null || !key.equals(pendingBatchKey) || pendingBatchPlots > plotsLeft) {
            int plots = HarvestBatching.batchSize(plotsLeft, freeOutputSlots(), yieldSample);
            pendingBatch = dropSource == null
                    ? List.of()
                    : Harvester.roll(dropSource, plots, tier,
                            new DropSource.Context(level, worldPosition, level.getRandom(), maxLootRolls,
                                    fertilizedEssence, filter.snapshot()));
            pendingBatchPlots = plots;
            pendingBatchKey = key;
            yieldSample.record(plots, Harvester.slotsFilled(pendingBatch), pendingBatch.size());
        }

        List<ResourceHandler<ItemResource>> targets = fillTargets();
        if (!Harvester.tryStore(pendingBatch, targets)) {
            if (Harvester.slotsNeeded(pendingBatch) <= totalOutputSlots()) {
                return false; // fits once space frees up: keep this roll and wait (OUTPUT FULL)
            }
            // Extreme case (owner: hold it): not even empty buffers could take this batch. Store what fits now and
            // hold the rest; it is stored before anything else and blocks the cycle until then.
            heldDrops = Harvester.storeWhatFits(pendingBatch, targets);
        }
        cycle.harvestPlots(0, pendingBatchPlots); // only after a committed store: a plot is harvested exactly once
        pendingBatch = null;
        pendingBatchKey = null;
        markForSave();
        return true;
    }

    /** Where harvests go, in order: the visible buffer, then the usable hidden slots (owner design). */
    private List<ResourceHandler<ItemResource>> fillTargets() {
        return List.of(output, internal.fillView());
    }

    /** Empty slots a harvest could use right now (partly filled stacks are not counted). */
    private int freeOutputSlots() {
        int empty = internal.emptyUsableSlots();
        for (int i = 0; i < output.size(); i++) {
            if (output.getAmountAsLong(i) == 0) {
                empty++;
            }
        }
        return empty;
    }

    /** Every slot a harvest may fill, visible and hidden. */
    private int totalOutputSlots() {
        return output.size() + internal.usableSlots();
    }

    /**
     * Moves what fits from the hidden buffer into the visible one (owner design: the hidden slots unload into the
     * visible slots as those empty). Runs on the tick after the visible buffer changed; one NeoForge transaction.
     */
    private void refillVisibleOutput() {
        refillRequested = false;
        if (internal.isEmpty()) {
            return;
        }
        int moved;
        refilling = true;
        try {
            moved = ResourceHandlerUtil.moveStacking(internal, output, resource -> true, Integer.MAX_VALUE, null);
        } finally {
            refilling = false;
        }
        if (moved > 0) {
            harvestRetryRequested = true;              // room appeared in the hidden buffer
            internal.setUsableSlots(internalSlots);    // drops slots beyond a lowered config once they are empty
        }
    }

    /**
     * Config {@code hoe.consumeDurability} (owner decision: wear over TIME, not per harvest): called every
     * {@code hoe.wearIntervalTicks} ticks of RUNNING while the soil needs the hoe. Removes 1 durability; a hoe that
     * breaks leaves the slot empty and the machine switches to MISSING HOE on its next revalidation.
     */
    private void wearHoe(ServerLevel level) {
        ItemStack hoe = inputs.stackInSlot(MachineSlots.HOE);
        if (hoe.isEmpty() || !hoe.isDamageableItem()) {
            return; // unbreakable / energy hoes never wear
        }
        hoe.hurtAndBreak(1, level, (ServerPlayer) null, brokenItem -> {
        });
        inputs.set(MachineSlots.HOE, hoe.isEmpty() ? ItemResource.EMPTY : ItemResource.of(hoe), hoe.getCount());
    }

    // =================================================================================================================
    // Revalidation (the only expensive part; runs when something changed)
    // =================================================================================================================

    /**
     * Recomputes everything derived from the slots, config and tags. Public so menu actions and game tests can force
     * it; the tick calls it automatically when needed.
     */
    public void revalidate() {
        inputsDirty = false;
        validatedConfigGeneration = VfwConfig.generation();
        validatedTagGeneration = SoilRules.cacheGeneration();

        ItemStack seed = inputs.stackInSlot(MachineSlots.SEED);
        ItemStack soil = inputs.stackInSlot(MachineSlots.SOIL);
        analysis = PlantAnalysis.analyze(seed, soil, tier);

        // Plots = min(seeds, soils) (owner rule). Plots exist whenever both slots hold usable items, even if the pair
        // is currently blocked (INVALID SOIL, missing hoe...): the bar just freezes. A missing or unusable seed/soil
        // means no plots, which resets the bar (GrowthCycle: an empty machine never keeps progress).
        boolean plotsPossible = analysis.status() != PlantAnalysis.Status.MISSING_SEED
                && analysis.status() != PlantAnalysis.Status.MISSING_SOIL;
        int plots = plotsPossible ? Math.min(seed.getCount(), soil.getCount()) : 0;
        Item seedItem = seed.isEmpty() ? null : seed.getItem();
        boolean plantChanged = plantedSeed != null && seedItem != plantedSeed;
        cycle.setPlots(0, plots, plantChanged);
        plantedSeed = cycle.totalPlots() > 0 ? seedItem : null;

        // Upgrades.
        hasWaterProvider = !inputs.getResource(MachineSlots.WATER_PROVIDER).isEmpty();
        int perSlot = VfwServerConfig.GROWTH_UPGRADES_PER_SLOT.get();
        growthUpgrades = 0;
        for (int i = 0; i < MachineSlots.GROWTH_COUNT; i++) {
            // A config lowered after insertion leaves extra upgrades in the slot; they are kept but do not count.
            growthUpgrades += Math.min(inputs.getAmountAsInt(MachineSlots.GROWTH_FIRST + i), perSlot);
        }

        VfwServerConfig.MachineSettings settings = VfwServerConfig.machine(tier);
        speed = GrowthSpeed.of(hasWaterProvider, settings.noWaterSpeedMultiplier.get(), growthUpgrades,
                VfwServerConfig.GROWTH_BONUS_PER_UPGRADE.get(), analysis.soilSpeedMultiplier());
        progressPerTick = speed.progressPerTick(settings.growthTicks.get());
        exportInterval = VfwServerConfig.AUTO_EXPORT_INTERVAL_TICKS.get();
        maxLootRolls = VfwServerConfig.MAX_LOOT_ROLLS_PER_HARVEST.get();
        hoeWears = VfwServerConfig.HOE_CONSUMES_DURABILITY.get();
        hoeWearInterval = VfwServerConfig.HOE_WEAR_INTERVAL_TICKS.get();
        internalSlots = settings.internalBufferSlots.get();
        internal.setUsableSlots(internalSlots); // a lowered value never deletes items, see InternalBuffer
        refillRequested = true;                 // a raised or lowered size may change what can move

        // Drop source: rebuilt only when what it depends on changed (not for upgrade changes).
        SourceKey sourceKey = new SourceKey(seedItem, soil.isEmpty() ? null : soil.getItem(),
                validatedConfigGeneration, validatedTagGeneration);
        if (!analysis.isValid()) {
            dropSource = null;
            dropSourceKey = null;
        } else if (!sourceKey.equals(dropSourceKey)) {
            dropSource = HarvestPlans.create(seed, soil);
            dropSourceKey = sourceKey;
        }

        // Harvest bookkeeping. No harvest due any more (e.g. every seed removed at 100%): no batch to keep, and nothing
        // blocked unless held drops still wait. Still blocked: the inputs changed, so the (possibly smaller or
        // different) batch deserves a retry.
        if (!cycle.isHarvestDue()) {
            pendingBatch = null;
            pendingBatchKey = null;
            if (heldDrops.isEmpty()) {
                harvestBlocked = false;
            }
        }
        if (harvestBlocked) {
            harvestRetryRequested = true;
        }

        updateStatus();
    }

    private void updateStatus() {
        MachineConditions conditions = new MachineConditions()
                .enabled(enabled)
                .hasSeed(analysis.status() != PlantAnalysis.Status.MISSING_SEED)
                .hasSoil(analysis.status() != PlantAnalysis.Status.MISSING_SOIL)
                .soilCompatible(analysis.status() != PlantAnalysis.Status.INVALID_SOIL)
                .hoe(analysis.needsHoe(), SoilRules.isHoe(inputs.stackInSlot(MachineSlots.HOE)))
                .crux(analysis.needsCrux(), !inputs.getResource(MachineSlots.CRUX_PROVIDER).isEmpty())
                .energy(false, true) // the Starter tier uses no FE
                .outputBlocked(harvestBlocked);
        status = MachineStatus.resolve(conditions);
    }

    // =================================================================================================================
    // Auto-export
    // =================================================================================================================

    /**
     * Pushes the output buffer into adjacent inventories on every enabled face, every {@link #exportInterval} ticks
     * (config, 0 = never). Uses NeoForge capability caches, so a lookup is a field read until a neighbour changes.
     * Exporting into another Farm Matrix does nothing: its buffer refuses insertion.
     */
    private void autoExport(ServerLevel level) {
        if (exportInterval <= 0 || outputFaces == 0) {
            return;
        }
        if (++exportCooldown < exportInterval) {
            return;
        }
        exportCooldown = 0;
        if (output.isEmpty()) {
            return;
        }
        Direction facing = getBlockState().getValue(FarmMatrixBlock.FACING);
        for (RelativeSide side : RelativeSide.all()) {
            if ((outputFaces & side.bit()) == 0) {
                continue;
            }
            ResourceHandler<ItemResource> target = exportTarget(level, side.toWorld(facing));
            if (target != null) {
                ResourceHandlerUtil.moveStacking(output, target, resource -> true, Integer.MAX_VALUE, null);
                if (output.isEmpty()) {
                    return;
                }
            }
        }
    }

    private @Nullable ResourceHandler<ItemResource> exportTarget(ServerLevel level, Direction direction) {
        BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> cache = exportTargets[direction.ordinal()];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.Item.BLOCK, level, worldPosition.relative(direction),
                    direction.getOpposite());
            exportTargets[direction.ordinal()] = cache;
        }
        return cache.getCapability();
    }

    @SuppressWarnings("unchecked")
    private static BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>[] newCacheArray() {
        return (BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction>[])
                new BlockCapabilityCache[Direction.values().length];
    }

    // =================================================================================================================
    // Change callbacks and saving
    // =================================================================================================================

    private void onInputsChanged() {
        inputsDirty = true;
        markForSave();
    }

    /**
     * A player changed the harvest filter: save it, and let a batch waiting for space be re-rolled with the new filter
     * (its {@link HarvestKey} no longer matches).
     */
    private void onFilterChanged() {
        harvestRetryRequested = true;
        markForSave();
    }

    private void onOutputChanged() {
        harvestRetryRequested = true;
        if (!refilling) {
            refillRequested = true; // room may have appeared for the hidden buffer's items
        }
        markForSave();
    }

    /**
     * Marks the chunk as needing a save. Uses {@code Level#blockEntityChanged} instead of {@code setChanged()}: the
     * latter also notifies neighbours for comparator output, which this block does not provide, and costs more.
     */
    private void markForSave() {
        ticksSinceProgressSave = 0;
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        // Persisted format: keep these keys stable.
        inputs.serialize(out.child("inputs"));
        output.serialize(out.child("output"));
        internal.serialize(out.child("internal_output"));
        if (!heldDrops.isEmpty()) {
            out.store("held_drops", HELD_DROPS_CODEC, heldDrops);
        }
        out.putDouble("progress", cycle.progress());
        out.putIntArray("active", cycle.activeCounts());
        out.putIntArray("pending", cycle.pendingCounts());
        out.putIntArray("harvested", cycle.harvestedCounts());
        out.putBoolean("enabled", enabled);
        out.putInt("output_faces", outputFaces);
        out.putBoolean("fertilized_essence", fertilizedEssence);
        filter.save(out.child("filter"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        in.child("inputs").ifPresent(inputs::deserialize);
        inputs.ensureMinimumSize();
        in.child("output").ifPresent(output::deserialize);
        output.ensureMinimumSize();
        in.child("internal_output").ifPresent(internal::deserialize);
        heldDrops = in.read("held_drops", HELD_DROPS_CODEC).map(List::copyOf).orElse(List.of());
        // "harvested" is missing in saves from before batched harvests: nothing was harvested yet, 0 is right.
        cycle.load(in.getDoubleOr("progress", 0.0), in.getIntArray("active").orElse(new int[0]),
                in.getIntArray("pending").orElse(new int[0]), in.getIntArray("harvested").orElse(new int[0]));
        enabled = in.getBooleanOr("enabled", true);
        outputFaces = in.getIntOr("output_faces", RelativeSide.ALL) & RelativeSide.ALL;
        fertilizedEssence = in.getBooleanOr("fertilized_essence", true);
        in.child("filter").ifPresent(filter::load);
        // Everything derived is rebuilt on the next tick; a batch that was blocked is simply rolled again.
        inputsDirty = true;
        plantedSeed = null;
        pendingBatch = null;
        pendingBatchKey = null;
        yieldSample.reset();
        yieldSampleKey = null;
        harvestBlocked = false;
        harvestRetryRequested = false;
    }

    /**
     * The block is being removed (broken, replaced): drop the inputs and the 9 visible output slots. The hidden
     * output slots, held drops and ripe plots are deleted (owner rule, step 8). A rolled but unstored batch was never
     * produced.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int i = 0; i < inputs.size(); i++) {
            ItemStack stack = inputs.stackInSlot(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
        for (int i = 0; i < output.size(); i++) {
            ItemStack stack = output.stackInSlot(i);
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack);
            }
        }
    }

    // =================================================================================================================
    // Menu
    // =================================================================================================================

    /** GUI title, e.g. "STARTER FARM MATRIX" (upper case per owner spec; one lang key per tier). */
    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.virtualfarmworks." + tier.getSerializedName() + "_farm_matrix");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FarmMatrixMenu(containerId, playerInventory, this);
    }

    // =================================================================================================================
    // Accessors (menu, capabilities, game tests)
    // =================================================================================================================

    public MachineTier tier() {
        return tier;
    }

    public MachineInventory inputs() {
        return inputs;
    }

    public OutputBuffer output() {
        return output;
    }

    /** Capability exposed on every face: extract-only view of the VISIBLE output buffer (the hidden one is private). */
    public ResourceHandler<ItemResource> externalOutput() {
        return output.externalView();
    }

    /** The hidden output slots. For game tests and debugging only: nothing outside the machine may use them. */
    public InternalBuffer internalOutput() {
        return internal;
    }

    /** Items held in the extreme case (see class doc); empty in normal play. Read-only. */
    public List<DropTally.Entry<ItemResource>> heldDrops() {
        return heldDrops;
    }

    /** Ripe plots of the due harvest still waiting on the plant (0 while growing). */
    public int plotsToHarvest() {
        return cycle.plotsToHarvest();
    }

    public MachineStatus status() {
        return status;
    }

    public double progress() {
        return cycle.progress();
    }

    public GrowthSpeed speed() {
        return speed;
    }

    public int activePlots() {
        return cycle.activePlots();
    }

    public int pendingPlots() {
        return cycle.pendingPlots();
    }

    public int totalPlots() {
        return cycle.totalPlots();
    }

    public int growthUpgrades() {
        return growthUpgrades;
    }

    public boolean hasWaterProvider() {
        return hasWaterProvider;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** On/off button. Server side only (the menu sends the player's intent; the server applies it). */
    public void setEnabled(boolean value) {
        if (enabled != value) {
            enabled = value;
            updateStatus();
            markForSave();
        }
    }

    public int outputFaces() {
        return outputFaces;
    }

    public boolean isOutputEnabled(RelativeSide side) {
        return (outputFaces & side.bit()) != 0;
    }

    /** Toggles auto-output on one face. Server side only. */
    public void toggleOutput(RelativeSide side) {
        outputFaces ^= side.bit();
        markForSave();
    }

    /** Completed cycles since load (see the field); the menu syncs it so the GUI can animate a cycle wrap. */
    public int completedHarvests() {
        return completedHarvests;
    }

    /** The harvest filter. Server side: the menu edits it through ghost slots and buttons. */
    public MachineFilter filter() {
        return filter;
    }

    public boolean isFertilizedEssenceEnabled() {
        return fertilizedEssence;
    }

    /**
     * Fertilized Essence switch. Server side only. A batch already rolled and waiting (OUTPUT FULL) is re-rolled with
     * the new setting because the switch is part of {@link HarvestKey}; asking for a retry makes that happen at once.
     */
    public void toggleFertilizedEssence() {
        fertilizedEssence = !fertilizedEssence;
        harvestRetryRequested = true;
        markForSave();
    }

    /**
     * Right-click with an upgrade in hand (owner spec): moves as many upgrades as fit from {@code held} into the
     * machine, each into its own slot kind (the inventory's slot rules decide, tiers included). Runs in one NeoForge
     * transaction; the held stack is shrunk by exactly what was inserted, unless {@code consume} is false (creative).
     * Server side only.
     *
     * @return how many items were inserted (0 = nothing fit, the caller opens the GUI instead)
     */
    public int insertUpgradesFrom(ItemStack held, boolean consume) {
        if (held.isEmpty()) {
            return 0;
        }
        int inserted;
        try (Transaction transaction = Transaction.openRoot()) {
            inserted = ResourceHandlerUtil.insertStacking(inputs, ItemResource.of(held), held.getCount(), transaction);
            transaction.commit();
        }
        if (inserted > 0 && consume) {
            held.shrink(inserted);
        }
        return inserted;
    }

    /**
     * TEST ONLY (game tests): sets the bar, keeping the plot counters. Not used by gameplay code; it exists so tests do
     * not have to wait for a full growth cycle.
     */
    public void setProgressForTesting(double progress) {
        cycle.load(progress, cycle.activeCounts(), cycle.pendingCounts());
    }
}
