/*
 * FarmMatrixBlockEntity — the running Farm Matrix of any tier: owns the inventories (inputs, visible output, hidden
 * output), the energy buffer, the autocrafter, the global growth cycle over its plot groups, the cached analysis of its
 * slots, the transactional batched harvest, face modes, auto-export and persistence. One per machine block,
 * server-ticked only.
 */
package com.virtualfarmworks.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.virtualfarmworks.block.FarmMatrixBlock;
import com.virtualfarmworks.config.VfwConfig;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.harvest.DropSource;
import com.virtualfarmworks.harvest.HarvestFilter;
import com.virtualfarmworks.harvest.HarvestPlans;
import com.virtualfarmworks.harvest.Harvester;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;
import com.virtualfarmworks.menu.FarmMatrixMenu;
import com.virtualfarmworks.plant.PlantAnalysis;
import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.plant.SoilRules;
import com.virtualfarmworks.registry.ModBlockEntities;
import com.virtualfarmworks.registry.ModDataComponents;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.GrowthCycle;
import com.virtualfarmworks.sim.GrowthSpeed;
import com.virtualfarmworks.sim.HarvestBatching;
import com.virtualfarmworks.sim.MachineConditions;
import com.virtualfarmworks.sim.MachineStatus;
import com.virtualfarmworks.sim.YieldSample;
import com.virtualfarmworks.transfer.ItemResource;
import com.virtualfarmworks.transfer.ItemSlots;
import com.virtualfarmworks.transfer.SlotRange;
import com.virtualfarmworks.transfer.SlotTransaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The machine. Design goal (owner): a machine with 6,000 plots must cost about the same per tick as one with 1.
 *
 * <h2>Tiers</h2>
 * One class for every tier; the tier's {@link MachineLayout} says how many plot groups it has (Starter 1, Entropic
 * 60) and which features it has (energy, autocrafter, automated input). A plot group is one seed slot plus the soil
 * slot at the same position; the growth cycle is still ONE bar for the whole machine (owner spec).
 *
 * <h2>Per tick (server only, see {@link #serverTick})</h2>
 * <ol>
 *   <li>If something relevant changed since the last tick (a slot, the config generation, a tag/datapack reload),
 *       {@link #revalidate()} once. That is where all expensive work happens: pairing seeds and soils, speed, drop
 *       sources, status. Nothing expensive runs on a normal tick.</li>
 *   <li>If the visible output changed: refill it from the hidden buffer ({@link #refillVisibleOutput}).</li>
 *   <li>Tiers with energy: check the buffer can pay this tick (a comparison); MISSING FE otherwise.</li>
 *   <li>RUNNING and bar below 100%: pay the energy, {@code cycle.advance(progressPerTick)} — one addition.</li>
 *   <li>Bar at 100%: harvest (see below). Otherwise the bar simply waits.</li>
 *   <li>Every {@code output.autoExportIntervalTicks}: push the visible output to adjacent inventories on the faces
 *       whose {@link FaceMode} exports, and pull from adjacent inventories on INPUT faces into the grids.</li>
 * </ol>
 *
 * <h2>Plot groups (multi-group tiers)</h2>
 * Each group is analyzed on its own (seed/soil pairing, hoe, crux) and shows its own problem in the GUI. The machine
 * runs only while EVERY group holding a plantable can grow (owner, 2026-09-29: "it only runs when all soils are
 * valid"): the status is the most important problem of any group, and the bar freezes like the Starter's, every group
 * keeping its plots meanwhile, so no group can ride a cycle it did not grow in. The soil speed bonus of a machine is
 * the plot-weighted average of its groups' soils (owner decision: one bar, many soils). Groups with the same seed and
 * soil are harvested together (one drop source, one roll): 60 identical groups cost like one. Tiers without a hoe
 * ({@link MachineLayout#usesHoe()}, the Entropic) till for free: a plant that needs farmland grows on tillable soil.
 *
 * <h2>Output: visible buffer, hidden buffer, plants (owner design, step 8)</h2>
 * Harvests fill the visible slots ({@link OutputBuffer}) first, then the hidden ones ({@link InternalBuffer}, size from
 * the tier's config); the hidden slots refill the visible ones as those empty. When both are full, the ripe plots not
 * harvested yet simply wait on the plant ({@code GrowthCycle#plotsToHarvest}): their items do not exist, so nothing is
 * stored anywhere else and nothing accumulates (the bar stays at 100% and the next cycle only starts once every plot
 * is harvested).
 *
 * <h2>Harvest batches (anti-dupe)</h2>
 * A due cycle is harvested in batches sized to the free output slots ({@code sim.HarvestBatching}). Each batch is
 * rolled ONCE for one harvest unit (groups sharing seed and soil) and kept in {@link #pendingBatch};
 * {@code Harvester#tryStore} stores it all-or-nothing (a {@code SlotTransaction}) and only then are its plots counted as
 * harvested. If it does not fit, status OUTPUT FULL and the SAME drops are retried only when the output or the inputs
 * changed ({@link #harvestRetryRequested}) — no loot re-rolls per retry. At most one batch per harvest unit per tick
 * (a one-plant machine such as the Starter: one batch per tick) and at most {@link #MAX_BATCHES_PER_TICK} in total,
 * so a big harvest spreads over a few ticks instead of spiking one. The pending roll is dropped when what it was rolled
 * for changes ({@link HarvestKey}) or fewer plots are left; it is never saved (after a reload it is rolled again —
 * nothing was produced yet, so nothing can be duplicated or lost).
 *
 * <p>Harvest filter (owner, step 8): the roll never produces what the machine's filter rejects ({@link MachineFilter}),
 * and {@link #purgeFilteredOutput()} deletes rejected items that are already in the output when the filter changes,
 * or that a player puts in by hand. The cleanup is triggered by those events only, never by ordinary output changes
 * (see {@link #requestFilterPurge}).
 *
 * <p>Extreme case (owner-approved): a batch too big even for EMPTY buffers is stored as far as it fits and the rest is
 * kept in {@link #heldDrops}, which is saved, stored before anything else, and blocks the cycle until empty.
 *
 * <h2>Replant and autocrafter (tiers that have them)</h2>
 * Owner's order for every batch: replant (produced plantables planted into free soil that suits them: groups already
 * holding them first, then empty seed slots above a suitable soil; switch per machine, allowed by the config), then the
 * autocrafter ({@link MachineCrafter}, CRAFT ON: what its recipes use is crafted, with the catalyst in the tool slot),
 * then the harvest filter (never on crafted items), then the output. Both are planned on every store attempt and
 * applied only after the store commits, like the plots. Items leaving the crafter's buffer (CRAFT turned OFF, recipes
 * edited) join the held drops.
 *
 * <h2>Energy (tiers that use it)</h2>
 * Each planted plot costs {@code machines.<tier>.energyPerPlot} FE per tick while the bar advances (owner spec: 90);
 * not while the bar waits. Without enough stored FE for a whole tick the status is MISSING FE and the machine spends
 * what is left on part of a tick (the bar moves that part), so a buffer whose source stopped drains to exactly 0
 * (owner, 2026-09-30) and a weak source grows the farm proportionally slower; at 0 FE the bar stops. The buffer is
 * sized from the config (plot capacity x FE per plot x 3) and accepts FE from cables on every face.
 *
 * <h2>Persistence</h2>
 * Saved: inventories (inputs, visible and hidden output), held drops, energy, progress, active/pending/harvested
 * counters per group, on/off switch, face modes, Fertilized Essence switch, replant switch, harvest filter, autocrafter
 * (recipes, switch, waiting ingredients). NOT saved (rebuilt by {@link #revalidate()} or relearned): analyses, speed, status, drop
 * sources, pending batch, yield samples, resolved recipes. Slot changes and harvests mark the chunk for saving
 * immediately; plain progress and energy at most once per {@link #SAVE_INTERVAL} ticks.
 *
 * <h2>Breaking the machine</h2>
 * Inputs and the visible output slots drop; the hidden buffer, held drops, the autocrafter's waiting ingredients,
 * stored energy and ripe plots are deleted (owner rule). The autocrafter's recipes stay on the dropped machine item
 * and come back when it is placed ({@link CrafterRecipes}, owner, 2026-09-30).
 *
 * <h2>Chunk unload</h2>
 * No ticking while unloaded, no catch-up when reloaded, no chunk loading (owner rules).
 */
public class FarmMatrixBlockEntity extends BlockEntity implements MenuProvider {
    private static final int SAVE_INTERVAL = 20;
    /** Harvest batches of different units stored in one tick at most (lag guard for machines full of plant types). */
    private static final int MAX_BATCHES_PER_TICK = 4;
    /** Items an INPUT face asks a neighbour's slot for at once (a stack: what most inventories hand out per call). */
    private static final int PULL_CHUNK = 64;
    /**
     * Owner formula: the energy buffer holds three ticks of the highest possible consumption. Stays in code, not in
     * the config (owner, 2026-09-30).
     */
    private static final int ENERGY_BUFFER_TICKS = 3;

    /** Saved form of {@link #heldDrops}: item and amount (amounts can exceed a stack, so not ItemStacks). */
    private static final Codec<List<DropTally.Entry<ItemResource>>> HELD_DROPS_CODEC = RecordCodecBuilder
            .<DropTally.Entry<ItemResource>>create(instance -> instance.group(
                    ItemResource.CODEC.fieldOf("item").forGetter(DropTally.Entry::key),
                    Codec.LONG.fieldOf("amount").forGetter(DropTally.Entry::amount))
                    .apply(instance, DropTally.Entry::new))
            .listOf();

    // --- persistent state -------------------------------------------------------------------------------------------
    private final MachineTier tier;
    private final MachineLayout layout;
    private final MachineInventory inputs;
    private final OutputBuffer output;
    /** Hidden output slots behind the visible buffer (owner design, step 8); size from the tier config. */
    private final InternalBuffer internal;
    /** FE buffer; null on tiers without energy. */
    private final @Nullable MachineEnergy energy;
    /** Autocrafter; null on tiers without one. */
    private final @Nullable MachineCrafter crafter;
    /** Extreme case only (see class doc): produced items that did not fit even into empty buffers. Stored first. */
    private List<DropTally.Entry<ItemResource>> heldDrops = List.of();
    private final GrowthCycle cycle;
    private boolean enabled = true;
    /** Mode of each face, by {@link RelativeSide} ordinal. */
    private final FaceMode[] faceModes = new FaceMode[RelativeSide.values().length];
    /** GUI switch (owner spec): whether Mystical Agriculture crops produce Fertilized Essence. On by default. */
    private boolean fertilizedEssence = true;
    /**
     * GUI switch (owner, 2026-09-29): whether this machine replants what it produces. On by default; only takes effect
     * while the config allows replanting ({@link #replantAllowed}).
     */
    private boolean replantOn = true;
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
    /** Per group: plant type its plot counters refer to; used to detect a seed swap ("plant changed"). */
    private final @Nullable Item[] plantedSeeds;
    /** Per group: seed/soil pairing. */
    private final PlantAnalysis[] analyses;
    /** Per group: what the GUI shows on that group (RUNNING = fine; MISSING_SEED = empty). */
    private final MachineStatus[] groupStatuses;
    /** Per group: drop source key and source (null when the group cannot grow). */
    private final @Nullable SourceKey[] sourceKeys;
    private final @Nullable DropSource[] dropSources;
    /** Groups harvested together (same seed and soil), in slot order of their first group. */
    private List<HarvestUnit> harvestUnits = List.of();
    /** A group needs the hoe the tool slot holds (tiers that use a hoe): the hoe wears while the machine runs. */
    private boolean anyGroupNeedsHoe;
    /** A hoe is in the tool slot, or the tier tills for free; a Crux Provider is installed. */
    private boolean hasHoe;
    private boolean hasCrux;
    // What the status needs from the groups holding a plantable (see updateStatus).
    private boolean anySeed;
    private boolean everySoilPresent = true;
    private boolean everySoilValid = true;
    private boolean anyGroupNeedsCrux;
    /** Per plantable: empty seed slots it could start a plot group in (see {@link #planReplant}); per revalidation. */
    private final Map<ItemResource, int[]> replantTargetCache = new HashMap<>();
    private GrowthSpeed speed = GrowthSpeed.BASELINE;
    private double progressPerTick;
    private MachineStatus status = MachineStatus.MISSING_SEED;
    private boolean hasWaterProvider;
    private int growthUpgrades;
    /** FE this machine pays per tick of growth (energy per plot x planted plots). */
    private long energyPerTick;
    /** The buffer could pay the last tick checked (tiers with energy). */
    private boolean hasEnergy = true;
    /** The buffer held some FE at the last check, perhaps less than a tick (then RUNNING WITH LOW FE). */
    private boolean hasSomeEnergy = true;
    // Config values cached at revalidation (no config reads in the per-tick path).
    private int exportInterval;
    private int maxLootRolls;
    private int internalSlots;
    private boolean hoeWears;
    private int hoeWearInterval;
    /** Config {@code machines.<tier>.replant} (tiers that have it): whether the machine's replant switch may work. */
    private boolean replantAllowed;
    /** Config {@code machines.<tier>.energyPerPlot} (tiers with energy), for the GUI. */
    private long energyPerPlot;
    /** Config {@code machines.<tier>.crafterBufferLimit} (tiers with an autocrafter). */
    private long crafterBufferLimit;
    /** Ticks of RUNNING-with-a-needed-hoe since the hoe last lost durability (not saved: at most one interval lost). */
    private int hoeWearTicks;

    // --- harvest / export runtime state -----------------------------------------------------------------------------
    /** The next batch, rolled once and kept until stored (see class doc). */
    private @Nullable List<DropTally.Entry<ItemResource>> pendingBatch;
    private int pendingBatchPlots;
    private @Nullable HarvestKey pendingBatchKey;
    /** What one plot yields, per harvest unit, measured from the rolled batches; sizes the next batch. */
    private final Map<SourceKey, KeyedSample> yieldSamples = new HashMap<>();
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
    /** The filter changed or a player put an item in: remove from the output what the filter rejects next tick. */
    private boolean purgeRequested;
    /** Something craftable may be in the output (see {@link #craftFromOutput}): run a crafting pass next tick. */
    private boolean craftFromOutputRequested;
    /** The last pass had no room for its results: try again once the output changes. */
    private boolean craftFromOutputBlocked;
    /** Progress or energy changed without an immediate save: saved at most every {@link #SAVE_INTERVAL} ticks. */
    private boolean saveDue;
    private int exportCooldown;
    private int ticksSinceSave;
    /** Capability caches of the 6 neighbours, by world direction ordinal; created lazily on the server. */
    private final @Nullable BlockCapabilityCache<IItemHandler, @Nullable Direction>[] exportTargets = newCacheArray();
    // Automation views (created once; cheap wrappers).
    private final IItemHandler producedView;
    private final IItemHandler craftedView;
    private final IItemHandler allOutputView;
    private final @Nullable GridInput gridInput;

    /** What a drop source depends on: seed and soil types, config and tags. Upgrades do not matter. */
    private record SourceKey(@Nullable Item seed, @Nullable Item soil, int configGeneration, int tagGeneration) {
    }

    /**
     * What a rolled batch (and the yield sample) depends on: its drop source inputs, the Fertilized Essence switch
     * (turning it off while a batch waits must drop the essence from that batch), the harvest filter version (a
     * filter change must apply to a batch still waiting for space) and whether the roll ignored the filter (replant or
     * autocrafter first, see {@link #harvestNextBatch}).
     */
    private record HarvestKey(@Nullable SourceKey source, boolean fertilizedEssence, int filterVersion,
                              boolean unfiltered) {
    }

    /** Groups with the same seed and soil, harvested with one drop source. */
    private record HarvestUnit(SourceKey key, @Nullable DropSource source, int[] groups) {
    }

    /** A unit's yield sample and the exact harvest key it measured (a different key resets it). */
    private record KeyedSample(HarvestKey key, YieldSample sample) {
    }

    public FarmMatrixBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FARM_MATRIX.get(), pos, state);
        this.tier = state.getBlock() instanceof FarmMatrixBlock block ? block.tier() : MachineTier.STARTER;
        this.layout = MachineLayout.of(tier);
        this.inputs = new MachineInventory(tier, this::onInputsChanged);
        this.output = new OutputBuffer(layout.visibleOutputSlots(), this::onOutputChanged);
        this.internal = new InternalBuffer(this::markForSave);
        this.filter = new MachineFilter(this::onFilterChanged);
        this.energy = layout.usesEnergy() ? new MachineEnergy(this::onEnergyChanged) : null;
        this.crafter = layout.hasCrafter() ? new MachineCrafter(this::markForSave) : null;
        this.cycle = new GrowthCycle(layout.groups());
        int groups = layout.groups();
        this.plantedSeeds = new Item[groups];
        this.analyses = new PlantAnalysis[groups];
        this.groupStatuses = new MachineStatus[groups];
        this.sourceKeys = new SourceKey[groups];
        this.dropSources = new DropSource[groups];
        PlantAnalysis empty = PlantAnalysis.analyze(ItemStack.EMPTY, ItemStack.EMPTY, MachineTier.STARTER);
        Arrays.fill(analyses, empty);
        Arrays.fill(groupStatuses, MachineStatus.MISSING_SEED);
        // Default face modes: the Starter exports on every face (owner spec); multi-mode tiers export everything.
        Arrays.fill(faceModes, layout.acceptsInput() ? FaceMode.OUTPUT_ALL : FaceMode.OUTPUT);
        this.producedView = output.externalView(stack -> !isCrafted(stack));
        this.craftedView = output.externalView(this::isCrafted);
        this.allOutputView = output.externalView();
        this.gridInput = layout.acceptsInput() ? new GridInput(inputs) : null;
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
        if (crafter != null && crafter.needsResolve()) {
            resolveCrafter(level);
        }
        if (purgeRequested) {
            purgeFilteredOutput(); // before the refill, so rejected hidden items never move up
        }
        if (refillRequested) {
            refillVisibleOutput();
        }
        if (craftFromOutputRequested && crafter != null) {
            craftFromOutput(level);
        }

        boolean paid = false;
        if (hasOutputToStore()) {
            if (canStoreOutput()) {
                storeOutput(level);
            }
        } else {
            if (energy != null) {
                checkEnergy();
            }
            if (status.isRunning()) {
                double advance = progressPerTick;
                if (energy != null) {
                    // Owner (2026-09-30): the buffer drains to 0. What cannot pay a whole tick pays part of one
                    // (RUNNING WITH LOW FE) and the bar moves that part: no FE is stranded, none is wasted, and a weak
                    // source grows the farm slower.
                    long pay = status == MachineStatus.RUNNING_LOW_FE ? energy.getAmountAsLong() : energyPerTick;
                    energy.consume(pay);
                    paid = true;
                    if (pay < energyPerTick) {
                        advance = progressPerTick * pay / energyPerTick;
                    }
                }
                cycle.advance(advance);
                saveDue = true;
            }
        }
        if (energy != null && !paid) {
            energy.idle(); // the GUI's "Using X FE/t" drops to 0 while the bar does not move
        }

        if (hoeWears && status.isRunning() && anyGroupNeedsHoe && ++hoeWearTicks >= hoeWearInterval) {
            hoeWearTicks = 0;
            wearHoe(level);
        }

        autoTransfer(level);

        if (saveDue && ++ticksSinceSave >= SAVE_INTERVAL) {
            markForSave();
        }
    }

    /**
     * Tiers with energy: whether the buffer pays this tick whole, in part or not at all; a change flips RUNNING /
     * RUNNING WITH LOW FE / MISSING FE.
     */
    private void checkEnergy() {
        boolean enough = energy == null || energy.canPay(energyPerTick);
        boolean some = enough || energy.getAmountAsLong() > 0;
        if (enough != hasEnergy || some != hasSomeEnergy) {
            hasEnergy = enough;
            hasSomeEnergy = some;
            updateStatus();
        }
    }

    /** Held drops, or a due harvest: the machine stores before it grows again (the bar waits at 100%). */
    private boolean hasOutputToStore() {
        return !heldDrops.isEmpty() || cycle.isHarvestDue();
    }

    /**
     * Held drops are stored whatever the machine's state (they were already produced). A due harvest is harvested
     * when the inputs allow it (see {@link #inputsAllowHarvest}). After a failed attempt, only once something changed
     * that could make it succeed ({@link #harvestRetryRequested}), so a full machine costs nothing per tick while it
     * waits.
     */
    private boolean canStoreOutput() {
        if (heldDrops.isEmpty() && !inputsAllowHarvest()) {
            return false;
        }
        return !harvestBlocked || harvestRetryRequested;
    }

    /**
     * RUNNING (with low FE too), or OUTPUT FULL (which only means "the last attempt did not fit"), or MISSING FE (the
     * plants are ripe; harvesting costs no energy).
     */
    private boolean inputsAllowHarvest() {
        return status.isRunning() || status == MachineStatus.OUTPUT_FULL || status == MachineStatus.MISSING_FE;
    }

    /**
     * One storing step: held drops first, then at most one batch per harvest unit (and {@link #MAX_BATCHES_PER_TICK}
     * in total); completes the cycle once every plot is harvested and stored. Sets {@link #harvestBlocked} (OUTPUT
     * FULL) when something is left waiting for space.
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
            int batches = 0;
            for (HarvestUnit unit : unitsInHarvestOrder()) {
                if (plotsToHarvest(unit) == 0) {
                    continue;
                }
                if (!harvestNextBatch(level, unit) || !heldDrops.isEmpty()) {
                    blocked = true;
                    break;
                }
                if (++batches >= MAX_BATCHES_PER_TICK) {
                    break;
                }
            }
        }
        if (cycle.isHarvestDue() && cycle.plotsToHarvest() == 0 && heldDrops.isEmpty()) {
            cycle.completeHarvest(); // every plot of the cycle harvested AND stored: the bar starts again
            completedHarvests++;
            markForSave();
        }
        harvestBlocked = blocked;
        updateStatus();
    }

    /** The unit of a batch waiting for space first (so its roll is not wasted), then the others in slot order. */
    private List<HarvestUnit> unitsInHarvestOrder() {
        if (pendingBatchKey == null || harvestUnits.size() < 2) {
            return harvestUnits;
        }
        List<HarvestUnit> ordered = new ArrayList<>(harvestUnits.size());
        for (HarvestUnit unit : harvestUnits) {
            if (unit.key().equals(pendingBatchKey.source())) {
                ordered.addFirst(unit);
            } else {
                ordered.add(unit);
            }
        }
        return ordered;
    }

    private int plotsToHarvest(HarvestUnit unit) {
        int sum = 0;
        for (int group : unit.groups()) {
            sum += cycle.plotsToHarvest(group);
        }
        return sum;
    }

    /**
     * Rolls the next batch of a unit (once, see class doc) and stores it all-or-nothing. Its plots count as harvested
     * only after a committed store.
     *
     * @return true when the batch was stored (or, in the extreme case, stored in part with the rest held — the caller
     *         then sees the held drops); false when it does not fit yet: the batch is kept and the ripe plots wait
     */
    private boolean harvestNextBatch(ServerLevel level, HarvestUnit unit) {
        int plotsLeft = plotsToHarvest(unit);
        MachineCrafter activeCrafter = crafter != null && crafter.isActive() ? crafter : null;
        // With replanting or the autocrafter the roll ignores the harvest filter: produced plantables are planted
        // first ("instead of being output or blacklisted", owner) and the crafter takes its ingredients before the
        // filter (owner order); the filter then applies to what is left (see below).
        boolean replant = isReplanting();
        boolean unfiltered = replant || activeCrafter != null;
        HarvestKey key = new HarvestKey(unit.key(), fertilizedEssence, filter.version(), unfiltered);
        YieldSample sample = sampleFor(key);
        if (pendingBatch == null || !key.equals(pendingBatchKey) || pendingBatchPlots > plotsLeft) {
            int plots = HarvestBatching.batchSize(plotsLeft, freeOutputSlots(), sample);
            HarvestFilter rollFilter = unfiltered ? HarvestFilter.NONE : filter.snapshot();
            pendingBatch = unit.source() == null
                    ? List.of()
                    : Harvester.roll(unit.source(), plots, tier,
                            new DropSource.Context(level, worldPosition, level.getRandom(), maxLootRolls,
                                    fertilizedEssence, rollFilter));
            pendingBatchPlots = plots;
            pendingBatchKey = key;
            sample.record(plots, Harvester.slotsFilled(pendingBatch), pendingBatch.size());
        }

        // Owner's harvest order: replant, then the autocrafter, then the filter, then the output. Planned on every
        // attempt (free soil and the crafter may change while the batch waits) and applied only after the store
        // commits, so nothing is planted or crafted twice.
        List<Replanting> replanting = replant ? planReplant(pendingBatch) : List.of();
        List<DropTally.Entry<ItemResource>> toStore = withoutReplanted(pendingBatch, replanting);
        // The crafter also uses what the output already holds (owner): its plan says what to take from there.
        MachineCrafter.Plan crafting = activeCrafter != null
                ? activeCrafter.plan(toStore, outputStock(), level, crafterBufferLimit,
                        inputs.getResource(layout.catalystSlot()))
                : null;
        Map<ItemResource, Long> fromOutput = Map.of();
        if (crafting != null) {
            toStore = crafting.output();
            fromOutput = crafting.taken();
        }
        if (unfiltered) {
            toStore = withoutFiltered(toStore);
        }

        List<SlotRange> targets = fillTargets();
        if (!Harvester.tryTakeAndStore(fromOutput, outputSources(), toStore, targets)) {
            if (Harvester.slotsNeeded(toStore) <= totalOutputSlots()) {
                return false; // fits once space frees up: keep this roll and wait (OUTPUT FULL)
            }
            // Extreme case (owner: hold it): not even empty buffers could take this batch. Store what fits now and
            // hold the rest; it is stored before anything else and blocks the cycle until then.
            List<DropTally.Entry<ItemResource>> held = Harvester.takeAndStoreWhatFits(fromOutput, outputSources(), toStore,
                    targets);
            if (held == null) {
                return false; // nothing changed (never expected in one tick): the batch waits
            }
            heldDrops = held;
        }
        if (crafting != null) {
            activeCrafter.commit(crafting); // same tick as the plan: its version always matches
        }
        // Only after a committed store: a plot is harvested exactly once. The batch's plots are spread over the unit's
        // groups in slot order.
        int left = pendingBatchPlots;
        for (int group : unit.groups()) {
            int taken = Math.min(left, cycle.plotsToHarvest(group));
            cycle.harvestPlots(group, taken);
            left -= taken;
        }
        pendingBatch = null;
        pendingBatchKey = null;
        if (!replanting.isEmpty()) {
            for (Replanting planted : replanting) {
                int slot = layout.seedSlot(planted.group());
                inputs.set(slot, planted.seed(), inputs.getAmountAsInt(slot) + planted.amount()); // may be an empty slot
            }
            // Right away, while the harvest is still due: the new plots enter as PENDING and become ACTIVE when this
            // cycle completes, so replanted seeds grow from the next cycle (not one later).
            revalidate();
        }
        markForSave();
        return true;
    }

    /** Seeds planted into one plot group by the replant (owner's Entropic spec). */
    private record Replanting(int group, ItemResource seed, int amount) {
    }

    /**
     * Replant plan (owner: produced plantables look for free soil inside the machine; owner's choice (b), 2026-09-29:
     * "the seed simply looks for a soil that works for it; if none works or none is free, it is output"). A plantable
     * goes first to the groups already holding it, as far as they have free soil ({@code soils - seeds}) and seed-slot
     * room, then to empty seed slots whose soil it grows on (the whole soil stack becomes its plot group). A new group is
     * only started where it would grow at once ({@link #replantTargets}), so replanting never stops the machine. Plants
     * that need no soil do not replant. What finds no room stays in the drops and goes to the output.
     */
    private List<Replanting> planReplant(List<DropTally.Entry<ItemResource>> drops) {
        List<Replanting> plan = new ArrayList<>();
        boolean[] claimed = null; // empty seed slots this plan already gives to a plantable
        for (DropTally.Entry<ItemResource> drop : drops) {
            long left = drop.amount();
            for (int g = 0; g < layout.groups() && left > 0; g++) {
                int seedSlot = layout.seedSlot(g);
                // Compared in place: building a resource per group would cost an object and a hash each.
                ItemStack seed = inputs.getStackInSlot(seedSlot);
                if (!drop.key().matches(seed) || !analyses[g].isValid() || !analyses[g].needsSoil()) {
                    continue;
                }
                long seeds = seed.getCount();
                long freeSoil = inputs.getAmountAsLong(layout.soilSlot(g)) - seeds;
                long slotRoom = inputs.getCapacityAsLong(seedSlot, drop.key()) - seeds;
                long take = Math.min(left, Math.min(freeSoil, slotRoom));
                if (take > 0) {
                    plan.add(new Replanting(g, drop.key(), (int) take));
                    left -= take;
                }
            }
            if (left <= 0) {
                continue;
            }
            for (int g : replantTargets(drop.key())) {
                if (left <= 0) {
                    break;
                }
                if (claimed == null) {
                    claimed = new boolean[layout.groups()];
                }
                if (claimed[g]) {
                    continue;
                }
                int seedSlot = layout.seedSlot(g);
                long take = Math.min(left, Math.min(inputs.getAmountAsLong(layout.soilSlot(g)),
                        inputs.getCapacityAsLong(seedSlot, drop.key())));
                if (take > 0) {
                    plan.add(new Replanting(g, drop.key(), (int) take));
                    claimed[g] = true;
                    left -= take;
                }
            }
        }
        return plan;
    }

    /**
     * Empty seed slots (in slot order) whose soil a plantable would grow on right away: a valid pairing that needs a
     * soil, and nothing else missing (hoe, crux). Cached per item until the next revalidation (the slots may change).
     */
    private int[] replantTargets(ItemResource plantable) {
        int[] cached = replantTargetCache.get(plantable);
        if (cached != null) {
            return cached;
        }
        ItemStack seed = plantable.toStack(1);
        List<Integer> targets = new ArrayList<>();
        if (PlantRules.isPlantable(seed)) {
            for (int g = 0; g < layout.groups(); g++) {
                if (!inputs.isEmpty(layout.seedSlot(g)) || inputs.isEmpty(layout.soilSlot(g))) {
                    continue;
                }
                PlantAnalysis analysis = PlantAnalysis.analyze(seed, inputs.stackInSlot(layout.soilSlot(g)), tier);
                if (analysis.isValid() && analysis.needsSoil()
                        && groupStatusOf(analysis, hasHoe, hasCrux) == MachineStatus.RUNNING) {
                    targets.add(g);
                }
            }
        }
        cached = targets.stream().mapToInt(Integer::intValue).toArray();
        replantTargetCache.put(plantable, cached);
        return cached;
    }

    /** The drops minus what the replant plants. */
    private static List<DropTally.Entry<ItemResource>> withoutReplanted(List<DropTally.Entry<ItemResource>> drops,
                                                                       List<Replanting> replanting) {
        if (replanting.isEmpty()) {
            return drops;
        }
        Map<ItemResource, Long> planted = new HashMap<>();
        for (Replanting r : replanting) {
            planted.merge(r.seed(), (long) r.amount(), Long::sum);
        }
        List<DropTally.Entry<ItemResource>> left = new ArrayList<>(drops.size());
        for (DropTally.Entry<ItemResource> drop : drops) {
            long amount = drop.amount() - planted.getOrDefault(drop.key(), 0L);
            if (amount > 0) {
                left.add(new DropTally.Entry<>(drop.key(), amount));
            }
        }
        return left;
    }

    /**
     * The drops the harvest filter lets through (used when the roll itself was not filtered). Crafted items always
     * pass: the filter is for what the plants produce (owner order: crafter before filter).
     */
    private List<DropTally.Entry<ItemResource>> withoutFiltered(List<DropTally.Entry<ItemResource>> drops) {
        HarvestFilter current = filter.snapshot();
        if (current.items().isEmpty()) {
            return drops;
        }
        return drops.stream().filter(drop -> keeps(current, drop.key())).toList();
    }

    /**
     * Items leaving the autocrafter's buffer (CRAFT turned OFF, recipes edited) go to the output like held drops:
     * stored before anything else. Harvested items the filter rejects are deleted, as they would have been without the
     * crafter.
     */
    private void holdReleased(List<DropTally.Entry<ItemResource>> released) {
        List<DropTally.Entry<ItemResource>> kept = withoutFiltered(released);
        if (kept.isEmpty()) {
            return;
        }
        List<DropTally.Entry<ItemResource>> merged = new ArrayList<>(heldDrops);
        merged.addAll(kept);
        heldDrops = List.copyOf(merged);
        harvestRetryRequested = true;
        markForSave();
    }

    /** Matches the autocrafter's recipes to the loaded data; what no recipe uses any more goes to the output. */
    private void resolveCrafter(ServerLevel level) {
        if (crafter != null) {
            holdReleased(crafter.resolve(level));
            harvestRetryRequested = true; // a batch waiting for space is planned again with the recipes as they are
            craftFromOutputRequested = true; // the recipes (maybe new ones) may use what the output holds
        }
    }

    /**
     * Owner (2026-09-29): the autocrafter uses whatever the output holds, even what it crafted itself. Harvests already
     * do (their plan sees the output); this pass covers the other moments something craftable can appear there:
     * recipes edited or loaded, CRAFT turned ON, items put in by hand. One transaction: the ingredients leave the
     * output and the results come in, or nothing changes (no room: tried again once the output changes).
     */
    private void craftFromOutput(ServerLevel level) {
        if (!heldDrops.isEmpty()) {
            return; // held drops go out first; the request waits for them
        }
        craftFromOutputRequested = false;
        craftFromOutputBlocked = false;
        if (crafter == null || !crafter.isActive()) {
            return;
        }
        MachineCrafter.Plan plan = crafter.plan(List.of(), outputStock(), level, crafterBufferLimit,
                inputs.getResource(layout.catalystSlot()));
        if (plan.taken().isEmpty() && plan.output().isEmpty()) {
            return; // nothing to craft
        }
        if (Harvester.tryTakeAndStore(plan.taken(), outputSources(), withoutFiltered(plan.output()), fillTargets())) {
            crafter.commit(plan);
        } else {
            craftFromOutputBlocked = true;
        }
    }

    /**
     * What the visible and hidden output hold, per item: the stock the autocrafter may use. Runs on every store attempt
     * of a crafting machine, so slots are grouped by comparing each stack with the items found so far (a few distinct
     * items fill many slots) instead of building and hashing a resource per slot: an item's hash covers all its
     * components, which made this scan the crafter's main cost.
     */
    private Map<ItemResource, Long> outputStock() {
        List<ItemResource> items = new ArrayList<>();
        List<Long> amounts = new ArrayList<>();
        for (ItemSlots handler : outputSources()) {
            for (int i = 0; i < handler.size(); i++) {
                ItemStack stack = handler.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }
                int known = -1;
                for (int k = 0; k < items.size() && known < 0; k++) {
                    if (items.get(k).matches(stack)) {
                        known = k;
                    }
                }
                if (known >= 0) {
                    amounts.set(known, amounts.get(known) + stack.getCount());
                } else {
                    items.add(ItemResource.of(stack));
                    amounts.add((long) stack.getCount());
                }
            }
        }
        Map<ItemResource, Long> stock = new LinkedHashMap<>();
        for (int k = 0; k < items.size(); k++) {
            stock.put(items.get(k), amounts.get(k));
        }
        return stock;
    }

    /** Where the autocrafter takes output items from: the visible slots first (what the player sees), then hidden. */
    private List<ItemSlots> outputSources() {
        return List.of(output, internal);
    }

    /** The yield sample of a unit for exactly this key; a key change (switch, filter, config) starts a fresh one. */
    private YieldSample sampleFor(HarvestKey key) {
        KeyedSample keyed = yieldSamples.get(key.source());
        if (keyed == null || !keyed.key().equals(key)) {
            keyed = new KeyedSample(key, new YieldSample());
            yieldSamples.put(key.source(), keyed);
        }
        return keyed.sample();
    }

    /** Where harvests go, in order: the visible buffer, then the usable hidden slots (owner design). */
    private List<SlotRange> fillTargets() {
        return List.of(SlotRange.all(output), internal.fillView());
    }

    /** Empty slots a harvest could use right now (partly filled stacks are not counted). */
    private int freeOutputSlots() {
        int empty = internal.emptyUsableSlots();
        for (int i = 0; i < output.size(); i++) {
            if (output.isEmpty(i)) {
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
     * visible slots as those empty), completing stacks of the same item first. Runs on the tick after the visible buffer
     * changed; one {@code SlotTransaction}.
     */
    private void refillVisibleOutput() {
        refillRequested = false;
        if (internal.isEmpty()) {
            return;
        }
        SlotTransaction transaction = new SlotTransaction();
        SlotRange visible = SlotRange.all(output);
        long moved = 0;
        for (int i = 0; i < internal.size(); i++) {
            ItemStack stack = internal.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemResource resource = ItemResource.of(stack);
            long fits = transaction.insertStacking(visible, resource, stack.getCount());
            if (fits > 0) {
                transaction.extract(internal, i, resource, (int) fits);
                moved += fits;
            }
        }
        refilling = true;
        try {
            transaction.commit();
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
     * {@code hoe.wearIntervalTicks} ticks of RUNNING while a soil needs the hoe. Removes 1 durability; a hoe that
     * breaks leaves the slot empty and the groups that needed it show MISSING HOE on the next revalidation.
     */
    private void wearHoe(ServerLevel level) {
        int slot = layout.hoeSlot();
        ItemStack hoe = inputs.stackInSlot(slot);
        if (hoe.isEmpty() || !hoe.isDamageableItem()) {
            return; // unbreakable / energy hoes never wear
        }
        hoe.hurtAndBreak(1, level, (ServerPlayer) null, brokenItem -> {
        });
        inputs.setStackInSlot(slot, hoe.isEmpty() ? ItemStack.EMPTY : hoe); // the copy, now worn (or broken: empty)
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
        VfwServerConfig.MachineSettings settings = VfwServerConfig.machine(tier);

        hasHoe = !layout.usesHoe() || SoilRules.isHoe(inputs.stackInSlot(layout.hoeSlot()));
        hasCrux = !inputs.isEmpty(layout.cruxSlot());
        int groups = layout.groups();
        int[] plots = new int[groups];
        boolean[] plantChanged = new boolean[groups];
        Item[] seedItems = new Item[groups];
        anyGroupNeedsHoe = false;
        anySeed = false;
        everySoilPresent = true;
        everySoilValid = true;
        anyGroupNeedsCrux = false;
        replantTargetCache.clear();
        for (int g = 0; g < groups; g++) {
            ItemStack seed = inputs.stackInSlot(layout.seedSlot(g));
            ItemStack soil = inputs.stackInSlot(layout.soilSlot(g));
            PlantAnalysis analysis = PlantAnalysis.analyze(seed, soil, tier);
            analyses[g] = analysis;
            groupStatuses[g] = groupStatusOf(analysis, hasHoe, hasCrux);
            switch (analysis.status()) {
                case MISSING_SEED -> {
                    // An empty group, or soil alone: waiting plots, never a problem.
                }
                case MISSING_SOIL -> {
                    anySeed = true;
                    everySoilPresent = false;
                }
                case INVALID_SOIL -> {
                    anySeed = true;
                    everySoilValid = false;
                }
                case VALID -> {
                    anySeed = true;
                    anyGroupNeedsHoe |= layout.usesHoe() && analysis.needsHoe();
                    anyGroupNeedsCrux |= analysis.needsCrux();
                }
            }

            // Plots = min(seeds, soils) (owner rule), or the seeds for plants that need no soil (owner, 2026-09-28).
            // They exist whenever both slots hold usable items, even while a group is blocked: the whole bar freezes
            // then (see the class doc), so no group can ride a cycle it did not grow in.
            boolean plotsPossible = analysis.status() != PlantAnalysis.Status.MISSING_SEED
                    && analysis.status() != PlantAnalysis.Status.MISSING_SOIL;
            plots[g] = !plotsPossible ? 0
                    : analysis.needsSoil() ? Math.min(seed.getCount(), soil.getCount())
                    : seed.getCount();
            seedItems[g] = seed.isEmpty() ? null : seed.getItem();
            plantChanged[g] = plantedSeeds[g] != null && seedItems[g] != plantedSeeds[g];
        }
        cycle.setAllPlots(plots, plantChanged);
        for (int g = 0; g < groups; g++) {
            plantedSeeds[g] = cycle.group(g).total() > 0 ? seedItems[g] : null;
        }

        // Upgrades.
        hasWaterProvider = !inputs.isEmpty(layout.waterSlot());
        int perSlot = VfwServerConfig.GROWTH_UPGRADES_PER_SLOT.get();
        growthUpgrades = 0;
        for (int i = 0; i < MachineSlots.GROWTH_COUNT; i++) {
            // A config lowered after insertion leaves extra upgrades in the slot; they are kept but do not count.
            growthUpgrades += Math.min(inputs.getAmountAsInt(layout.growthSlot(i)), perSlot);
        }

        speed = GrowthSpeed.of(hasWaterProvider, settings.noWaterSpeedMultiplier.get(), growthUpgrades,
                VfwServerConfig.GROWTH_BONUS_PER_UPGRADE.get(), soilMultiplier(plots));
        progressPerTick = speed.progressPerTick(settings.growthTicks.get());
        exportInterval = VfwServerConfig.AUTO_EXPORT_INTERVAL_TICKS.get();
        maxLootRolls = VfwServerConfig.MAX_LOOT_ROLLS_PER_HARVEST.get();
        hoeWears = VfwServerConfig.HOE_CONSUMES_DURABILITY.get();
        hoeWearInterval = VfwServerConfig.HOE_WEAR_INTERVAL_TICKS.get();
        replantAllowed = settings.replant != null && settings.replant.get();
        crafterBufferLimit = settings.crafterBufferLimit != null ? settings.crafterBufferLimit.get() : 0;
        internalSlots = settings.internalBufferSlots.get();
        internal.setUsableSlots(internalSlots); // a lowered value never deletes items, see InternalBuffer
        refillRequested = true;                 // a raised or lowered size may change what can move
        if (energy != null && settings.energyPerPlot != null) {
            energyPerPlot = settings.energyPerPlot.get();
            energy.setCapacity(settings.plotCapacity(layout) * energyPerPlot * ENERGY_BUFFER_TICKS);
            energyPerTick = energyPerPlot * cycle.totalPlots();
            hasEnergy = energy.canPay(energyPerTick);
            hasSomeEnergy = hasEnergy || energy.getAmountAsLong() > 0;
        }

        rebuildDropSources(groups);

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

    /** What a group shows: its pairing problem, a missing hoe or crux it needs, or RUNNING when it can grow. */
    private static MachineStatus groupStatusOf(PlantAnalysis analysis, boolean hasHoe, boolean hasCrux) {
        return switch (analysis.status()) {
            case MISSING_SEED -> MachineStatus.MISSING_SEED;
            case MISSING_SOIL -> MachineStatus.MISSING_SOIL;
            case INVALID_SOIL -> MachineStatus.INVALID_SOIL;
            case VALID -> analysis.needsHoe() && !hasHoe ? MachineStatus.MISSING_HOE
                    : analysis.needsCrux() && !hasCrux ? MachineStatus.MISSING_CRUX
                    : MachineStatus.RUNNING;
        };
    }

    /**
     * Soil speed bonus of the machine: the plot-weighted average of its groups' soils (owner decision for one bar over
     * many soils: all Supremium Farmland = +35%, half of it = +17.5%). A machine without plots uses its first group's
     * soil, like the Starter always did.
     */
    private double soilMultiplier(int[] plots) {
        long total = 0;
        double weighted = 0.0;
        for (int g = 0; g < plots.length; g++) {
            total += plots[g];
            weighted += plots[g] * analyses[g].soilSpeedMultiplier();
        }
        return total > 0 ? weighted / total : analyses[0].soilSpeedMultiplier();
    }

    /**
     * Drop sources per group (rebuilt only when what they depend on changed, not for upgrade changes) and the harvest
     * units: groups with the same seed and soil share one source and are rolled together.
     */
    private void rebuildDropSources(int groups) {
        Map<SourceKey, DropSource> built = new HashMap<>();
        Map<SourceKey, List<Integer>> unitGroups = new LinkedHashMap<>();
        for (int g = 0; g < groups; g++) {
            if (!analyses[g].isValid()) {
                sourceKeys[g] = null;
                dropSources[g] = null;
                continue;
            }
            ItemStack seed = inputs.stackInSlot(layout.seedSlot(g));
            ItemStack soil = inputs.stackInSlot(layout.soilSlot(g));
            SourceKey key = new SourceKey(seed.getItem(), soil.isEmpty() ? null : soil.getItem(),
                    validatedConfigGeneration, validatedTagGeneration);
            if (!key.equals(sourceKeys[g])) {
                DropSource source = built.get(key);
                if (source == null && !built.containsKey(key)) {
                    source = HarvestPlans.create(seed, soil);
                    built.put(key, source);
                }
                sourceKeys[g] = key;
                dropSources[g] = source;
            } else {
                built.putIfAbsent(key, dropSources[g]);
            }
            if (cycle.group(g).total() > 0) {
                unitGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(g);
            }
        }
        List<HarvestUnit> units = new ArrayList<>(unitGroups.size());
        unitGroups.forEach((key, list) -> units.add(new HarvestUnit(key, built.get(key),
                list.stream().mapToInt(Integer::intValue).toArray())));
        harvestUnits = List.copyOf(units);
        yieldSamples.keySet().retainAll(unitGroups.keySet()); // forget samples of plants no longer planted
    }

    /**
     * The owner's priority list over every plot group: the machine needs a plantable somewhere, and every group holding
     * one must have its soil, a valid pairing and what it needs (hoe on tiers that use one, crux). One group short of
     * that stops the whole machine with that problem (owner, 2026-09-29); with one group this is the Starter's rule.
     */
    private void updateStatus() {
        // No plots: nothing to pay, so no energy problem.
        boolean wholeTick = !layout.usesEnergy() || hasEnergy || cycle.totalPlots() == 0;
        MachineConditions conditions = new MachineConditions()
                .enabled(enabled)
                .hasSeed(anySeed)
                .hasSoil(everySoilPresent)
                .soilCompatible(everySoilValid)
                .hoe(anyGroupNeedsHoe, hasHoe)
                .crux(anyGroupNeedsCrux, hasCrux)
                .energy(layout.usesEnergy(), wholeTick, hasSomeEnergy)
                .outputBlocked(harvestBlocked);
        status = MachineStatus.resolve(conditions);
    }

    // =================================================================================================================
    // Faces: auto-export and automation
    // =================================================================================================================

    /**
     * Every {@link #exportInterval} ticks (config, 0 = never): pushes the output buffer into adjacent inventories on
     * every face whose mode exports, and pulls from adjacent inventories on INPUT faces into the grids (owner: "a chest
     * full of dirt glued to the machine must feed it too", like the output). Uses NeoForge capability caches, so a
     * lookup is a field read until a neighbour changes. Exporting into another Farm Matrix does nothing (its buffer
     * refuses insertion); pulling from one takes what its face gives (its output), never its inputs. Neighbours are other
     * mods' inventories: plain {@code IItemHandler} calls, simulate before every real extraction.
     */
    private void autoTransfer(ServerLevel level) {
        if (exportInterval <= 0) {
            return;
        }
        if (++exportCooldown < exportInterval) {
            return;
        }
        exportCooldown = 0;
        boolean exporting = !output.isEmpty();
        Direction facing = null;
        for (RelativeSide side : RelativeSide.all()) {
            FaceMode mode = faceModes[side.ordinal()];
            boolean pulls = mode == FaceMode.INPUT && gridInput != null;
            if (!pulls && !(exporting && mode.exports())) {
                continue;
            }
            if (facing == null) {
                facing = getBlockState().getValue(FarmMatrixBlock.FACING);
            }
            IItemHandler neighbour = exportTarget(level, side.toWorld(facing));
            if (neighbour == null) {
                continue;
            }
            if (pulls) {
                pullInto(gridInput, neighbour, level);
            } else {
                exportTo(neighbour, mode);
                exporting = !output.isEmpty();
            }
        }
    }

    /**
     * Pushes every output stack the face's mode exports into a neighbour, completing its stacks of the same item first
     * ({@code insertItemStacked}). What the neighbour took leaves the buffer at once (one change per slot).
     */
    private void exportTo(IItemHandler neighbour, FaceMode mode) {
        for (int i = 0; i < output.size(); i++) {
            ItemStack stack = output.getStackInSlot(i);
            if (stack.isEmpty() || !mode.exports(isCrafted(stack))) {
                continue;
            }
            // A copy: an inventory may keep the stack object it is given.
            ItemStack rest = ItemHandlerHelper.insertItemStacked(neighbour, stack.copy(), false);
            if (rest.getCount() != stack.getCount()) {
                output.setStackInSlot(i, rest);
            }
        }
    }

    /**
     * Pulls into the grids what they take out of a neighbour's slots, a stack at most per call. Every amount is
     * simulated on both sides first and only what the grids accept is extracted, so nothing is taken that cannot be
     * stored. The grids route it (see {@link GridInput}). Should the neighbour hand over something else than it offered
     * (a broken inventory), the rest goes back to it, or drops at the machine if it will not take it: never voided.
     */
    private void pullInto(GridInput grids, IItemHandler neighbour, ServerLevel level) {
        for (int slot = 0; slot < neighbour.getSlots(); slot++) {
            while (true) {
                ItemStack offered = neighbour.extractItem(slot, PULL_CHUNK, true);
                if (offered.isEmpty()) {
                    break;
                }
                int accepted = offered.getCount() - grids.insert(offered, true).getCount();
                if (accepted <= 0) {
                    break;
                }
                ItemStack taken = neighbour.extractItem(slot, accepted, false);
                if (taken.isEmpty()) {
                    break;
                }
                ItemStack left = grids.insert(taken, false);
                if (!left.isEmpty()) {
                    ItemStack back = ItemHandlerHelper.insertItemStacked(neighbour, left, false);
                    if (!back.isEmpty()) {
                        Block.popResource(level, worldPosition, back);
                    }
                    break;
                }
                if (accepted < offered.getCount()) {
                    break; // the grids are full for this item
                }
            }
        }
    }

    private @Nullable IItemHandler exportTarget(ServerLevel level, Direction direction) {
        BlockCapabilityCache<IItemHandler, @Nullable Direction> cache = exportTargets[direction.ordinal()];
        if (cache == null) {
            cache = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK, level,
                    worldPosition.relative(direction), direction.getOpposite());
            exportTargets[direction.ordinal()] = cache;
        }
        return cache.getCapability();
    }

    @SuppressWarnings("unchecked")
    private static BlockCapabilityCache<IItemHandler, @Nullable Direction>[] newCacheArray() {
        return (BlockCapabilityCache<IItemHandler, @Nullable Direction>[])
                new BlockCapabilityCache[Direction.values().length];
    }

    /**
     * Whether an output item counts as made by the autocrafter: it is the result of one of its recipes (faces set to
     * OUTPUT CRAFTED export these, OUTPUT exports the rest; the harvest filter never removes them). Tiers without an
     * autocrafter craft nothing. Called by pipes through the face views, so it is one set lookup.
     */
    public boolean isCrafted(ItemResource resource) {
        return crafter != null && crafter.isResult(resource);
    }

    /** {@link #isCrafted(ItemResource)} for a stack; builds no resource unless the autocrafter has results. */
    public boolean isCrafted(ItemStack stack) {
        return crafter != null && crafter.isResult(stack);
    }

    /**
     * The item capability of one face ({@code side} = world direction, null = no side). The Starter shows its
     * extract-only output on every face, whatever the auto-output switch (its original behavior). Multi-mode tiers
     * follow the face's {@link FaceMode}: nothing, an extract-only view of the matching items, or the grid input.
     */
    public @Nullable IItemHandler itemHandler(@Nullable Direction side) {
        if (!layout.acceptsInput()) {
            return allOutputView;
        }
        if (side == null) {
            return allOutputView;
        }
        FaceMode mode = faceModes[RelativeSide.fromWorld(side, getBlockState().getValue(FarmMatrixBlock.FACING)).ordinal()];
        return switch (mode) {
            case NONE -> null;
            case OUTPUT -> producedView;
            case OUTPUT_CRAFTED -> craftedView;
            case OUTPUT_ALL -> allOutputView;
            case INPUT -> gridInput;
        };
    }

    /** The energy capability (every face), or null on tiers without energy. */
    public @Nullable IEnergyStorage energyHandler(@Nullable Direction side) {
        return energy;
    }

    // =================================================================================================================
    // Change callbacks and saving
    // =================================================================================================================

    private void onInputsChanged() {
        inputsDirty = true;
        markForSave();
    }

    private void onEnergyChanged() {
        saveDue = true; // saved with the progress, at most once per SAVE_INTERVAL
    }

    /**
     * A player changed the harvest filter: save it, let a batch waiting for space be re-rolled with the new filter
     * (its {@link HarvestKey} no longer matches), and remove from the output what the filter now rejects.
     */
    private void onFilterChanged() {
        harvestRetryRequested = true;
        purgeRequested = true;
        markForSave();
    }

    private void onOutputChanged() {
        harvestRetryRequested = true;
        if (!refilling) {
            refillRequested = true; // room may have appeared for the hidden buffer's items
        }
        if (craftFromOutputBlocked) {
            craftFromOutputRequested = true; // room may have appeared for the autocrafter's results
        }
        markForSave();
    }

    /**
     * A player put an item in the visible output by hand (menu slot, server side): delete it on the next tick if the
     * harvest filter rejects it, and let the autocrafter use it (owner: it uses anything in the output). Only this, a
     * filter change and loading can bring a rejected item into the output (harvests are filtered when rolled; exports,
     * pipes and the refill only take out or move), so the cleanup never runs after ordinary output changes: measured,
     * running it after every change cost +35% per tick with a pipe pulling from a filtered machine.
     */
    public void requestFilterPurge() {
        purgeRequested = true;
        craftFromOutputRequested = true;
    }

    /**
     * Owner rule (step 8): the output never keeps what the harvest filter rejects. The harvest never produces such
     * items, but the output may already hold them when the filter changes, and players may put them in by hand; both
     * are DELETED here, from the visible and the hidden output and from held drops. Crafted items are never removed
     * (the filter is for what the plants produce). Runs on the tick after a filter change, a hand-placed item
     * ({@link #requestFilterPurge}) or loading; returns at once while the filter is empty (it then rejects nothing).
     */
    private void purgeFilteredOutput() {
        purgeRequested = false;
        HarvestFilter current = filter.snapshot();
        if (current.items().isEmpty()) {
            return;
        }
        removeRejected(output, current);
        removeRejected(internal, current);
        if (heldDrops.stream().anyMatch(drop -> !keeps(current, drop.key()))) {
            heldDrops = heldDrops.stream().filter(drop -> keeps(current, drop.key())).toList();
            markForSave();
        }
    }

    private void removeRejected(ItemSlots handler, HarvestFilter filter) {
        for (int i = 0; i < handler.size(); i++) {
            ItemResource resource = handler.getResource(i);
            if (!resource.isEmpty() && !keeps(filter, resource)) {
                handler.set(i, ItemResource.EMPTY, 0);
            }
        }
    }

    /** Whether the output may keep an item under a filter: the filter lets it through, or the autocrafter made it. */
    private boolean keeps(HarvestFilter filter, ItemResource resource) {
        return filter.allows(resource) || isCrafted(resource);
    }

    /**
     * Marks the chunk as needing a save. Uses {@code Level#blockEntityChanged} instead of {@code setChanged()}: the
     * latter also notifies neighbours for comparator output, which this block does not provide, and costs more.
     */
    private void markForSave() {
        ticksSinceSave = 0;
        saveDue = false;
        if (level != null) {
            level.blockEntityChanged(worldPosition);
        }
    }

    /**
     * NBT save (1.21.1: a {@code CompoundTag}; see {@link Saves}). Codec values (held drops, the crafter's recipes) are
     * written with registry ops, item components may need them.
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Persisted format: keep these keys stable.
        tag.put("inputs", inputs.save(registries));
        tag.put("output", output.save(registries));
        tag.put("internal_output", internal.save(registries));
        if (energy != null) {
            tag.put("energy", energy.save());
        }
        if (!heldDrops.isEmpty()) {
            Saves.store(tag, "held_drops", HELD_DROPS_CODEC, heldDrops, registries);
        }
        tag.putDouble("progress", cycle.progress());
        tag.putIntArray("active", cycle.activeCounts());
        tag.putIntArray("pending", cycle.pendingCounts());
        tag.putIntArray("harvested", cycle.harvestedCounts());
        tag.putBoolean("enabled", enabled);
        tag.putIntArray("face_modes", Arrays.stream(faceModes).mapToInt(FaceMode::ordinal).toArray());
        tag.putBoolean("fertilized_essence", fertilizedEssence);
        tag.putBoolean("replant", replantOn);
        tag.put("filter", filter.save());
        if (crafter != null) {
            tag.put("crafter", crafter.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Saves.child(tag, "inputs").ifPresent(saved -> inputs.load(registries, saved));
        inputs.ensureMinimumSize();
        Saves.child(tag, "output").ifPresent(saved -> output.load(registries, saved));
        output.ensureMinimumSize();
        Saves.child(tag, "internal_output").ifPresent(saved -> internal.load(registries, saved));
        if (energy != null) {
            Saves.child(tag, "energy").ifPresent(energy::load);
        }
        heldDrops = Saves.read(tag, "held_drops", HELD_DROPS_CODEC, registries).map(List::copyOf).orElse(List.of());
        // Missing arrays read as empty ones (CompoundTag's default): "harvested" is missing in saves from before
        // batched harvests, nothing was harvested yet, 0 is right.
        cycle.load(tag.getDouble("progress"), tag.getIntArray("active"), tag.getIntArray("pending"),
                tag.getIntArray("harvested"));
        enabled = Saves.booleanOr(tag, "enabled", true);
        loadFaceModes(tag);
        fertilizedEssence = Saves.booleanOr(tag, "fertilized_essence", true);
        replantOn = Saves.booleanOr(tag, "replant", true);
        Saves.child(tag, "filter").ifPresent(filter::load);
        if (crafter != null) {
            // Its recipes are matched again on the next tick.
            Saves.child(tag, "crafter").ifPresent(saved -> crafter.load(registries, saved));
        }
        // Everything derived is rebuilt on the next tick; a batch that was blocked is simply rolled again.
        inputsDirty = true;
        Arrays.fill(plantedSeeds, null);
        pendingBatch = null;
        pendingBatchKey = null;
        yieldSamples.clear();
        harvestBlocked = false;
        harvestRetryRequested = false;
        purgeRequested = true; // a save may hold output items the filter rejects (put in right before saving)
    }

    /**
     * What the machine's item keeps when it is made from the block (loot table {@code copy_components}): the
     * autocrafter's recipes, when there are any (owner, 2026-09-30). A machine without recipes drops a plain item.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (crafter != null && crafter.size() > 0) {
            components.set(ModDataComponents.CRAFTER_RECIPES.get(), new CrafterRecipes(crafter.patterns()));
        }
    }

    /** Placed from an item that kept autocrafter recipes: they are the machine's again (matched on the next tick). */
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        if (crafter != null) {
            CrafterRecipes recipes = components.get(ModDataComponents.CRAFTER_RECIPES.get());
            if (recipes != null) {
                crafter.setPatterns(recipes.patterns());
            }
        }
    }

    /**
     * Face modes: "face_modes" (one ordinal per side), or the Starter's older "output_faces" bit mask (bit set =
     * OUTPUT, clear = NONE), or the tier's defaults.
     */
    private void loadFaceModes(CompoundTag tag) {
        if (tag.contains("face_modes", Tag.TAG_INT_ARRAY)) {
            int[] modes = tag.getIntArray("face_modes");
            for (int i = 0; i < faceModes.length; i++) {
                FaceMode mode = i < modes.length ? FaceMode.byOrdinal(modes[i]) : faceModes[i];
                faceModes[i] = layout.acceptsInput() || mode == FaceMode.NONE ? mode : FaceMode.OUTPUT;
            }
            return;
        }
        int mask = tag.contains("output_faces", Tag.TAG_INT) ? tag.getInt("output_faces") : -1;
        if (mask >= 0) {
            for (RelativeSide side : RelativeSide.all()) {
                faceModes[side.ordinal()] = (mask & side.bit()) != 0 ? FaceMode.OUTPUT : FaceMode.NONE;
            }
        }
    }

    /**
     * The block is being removed (broken, replaced): drop the inputs and the visible output slots. The hidden output
     * slots, held drops, stored energy and ripe plots are deleted (owner rule, step 8). A rolled but unstored batch was
     * never produced. The autocrafter's recipes leave with the machine's own item (the loot table copies them, see
     * {@link #collectImplicitComponents}).
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (int i = 0; i < inputs.size(); i++) {
            dropStack(pos, inputs.getResource(i), inputs.getAmountAsLong(i));
        }
        for (int i = 0; i < output.size(); i++) {
            dropStack(pos, output.getResource(i), output.getAmountAsLong(i));
        }
    }

    /** Drops an amount as normal stacks (grid slots may hold more than a stack). */
    private void dropStack(BlockPos pos, ItemResource resource, long amount) {
        if (level == null || resource.isEmpty()) {
            return;
        }
        int maxStack = Math.max(1, resource.getMaxStackSize());
        while (amount > 0) {
            int count = (int) Math.min(amount, maxStack);
            Block.popResource(level, pos, resource.toStack(count));
            amount -= count;
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
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return layout == MachineLayout.STARTER ? new FarmMatrixMenu(containerId, playerInventory, this)
                : new EntropicFarmMatrixMenu(containerId, playerInventory, this);
    }

    // =================================================================================================================
    // Accessors (menu, capabilities, game tests)
    // =================================================================================================================

    public MachineTier tier() {
        return tier;
    }

    public MachineLayout layout() {
        return layout;
    }

    public MachineInventory inputs() {
        return inputs;
    }

    public OutputBuffer output() {
        return output;
    }

    /** Extract-only view of the VISIBLE output buffer (the hidden one is private). */
    public IItemHandler externalOutput() {
        return allOutputView;
    }

    /** The hidden output slots. For game tests and debugging only: nothing outside the machine may use them. */
    public InternalBuffer internalOutput() {
        return internal;
    }

    /** The energy buffer, or null on tiers without energy. */
    public @Nullable MachineEnergy energy() {
        return energy;
    }

    /** FE per tick while growing (energy per plot x planted plots); 0 on tiers without energy. */
    public long energyPerTick() {
        return energy == null ? 0 : energyPerTick;
    }

    /** FE per active plot per tick (config {@code machines.<tier>.energyPerPlot}); 0 on tiers without energy. */
    public long energyPerPlot() {
        return energy == null ? 0 : energyPerPlot;
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

    /** What the GUI shows on one plot group (RUNNING = the group grows; MISSING_SEED = empty). */
    public MachineStatus groupStatus(int group) {
        return groupStatuses[group];
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

    /** Planted plots, the owner's "active plots" (ACTIVE and PENDING counters together). */
    public int totalPlots() {
        return cycle.totalPlots();
    }

    /**
     * Soils in the machine that no plot uses (owner's "waiting plots": 120 seeds planted on 500 soils = 380 waiting).
     * A plant that needs no soil leaves its whole soil slot waiting.
     */
    public int waitingPlots() {
        long waiting = 0;
        for (int g = 0; g < layout.groups(); g++) {
            long soils = inputs.getAmountAsLong(layout.soilSlot(g));
            long used = analyses[g].needsSoil() ? cycle.group(g).total() : 0;
            waiting += Math.max(0, soils - used);
        }
        return (int) Math.min(Integer.MAX_VALUE, waiting);
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

    public FaceMode faceMode(RelativeSide side) {
        return faceModes[side.ordinal()];
    }

    /** Steps a face to its next mode ({@code forward}) or the previous one. Server side only. */
    public void cycleFaceMode(RelativeSide side, boolean forward) {
        faceModes[side.ordinal()] = faceModes[side.ordinal()].cycle(forward, layout);
        if (level != null && layout.acceptsInput()) {
            level.invalidateCapabilities(worldPosition); // the face's capability changed
        }
        markForSave();
    }

    /** Starter: auto-output switch per face as a bit mask (bit set = OUTPUT), what its menu syncs. */
    public int outputFaces() {
        int mask = 0;
        for (RelativeSide side : RelativeSide.all()) {
            if (faceModes[side.ordinal()] == FaceMode.OUTPUT) {
                mask |= side.bit();
            }
        }
        return mask;
    }

    public boolean isOutputEnabled(RelativeSide side) {
        return faceModes[side.ordinal()].exports();
    }

    /** Starter: toggles auto-output on one face. Server side only. */
    public void toggleOutput(RelativeSide side) {
        cycleFaceMode(side, true);
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

    /** Whether produced plantables are replanted now: the config allows it and this machine's switch is on. */
    public boolean isReplanting() {
        return replantAllowed && replantOn;
    }

    /** The replant switch as the GUI shows it (owner: green ON, red OFF, grey DISABLED when the config forbids it). */
    public ReplantState replantState() {
        return !replantAllowed ? ReplantState.DISABLED : replantOn ? ReplantState.ON : ReplantState.OFF;
    }

    /** Replant switch of this machine; nothing while the config forbids replanting. Server side only. */
    public void toggleReplant() {
        if (!replantAllowed) {
            return;
        }
        replantOn = !replantOn;
        harvestRetryRequested = true; // a batch waiting for space is planned again with the new switch
        markForSave();
    }

    /** The replant button's three looks. Ordinal synced to the GUI. */
    public enum ReplantState {
        OFF,
        ON,
        DISABLED;

        public static ReplantState byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : DISABLED;
        }
    }

    /** The autocrafter (read it; change it through the methods below), or null on tiers without one. */
    public @Nullable MachineCrafter crafter() {
        return crafter;
    }

    /** Recipes the autocrafter may hold (config {@code machines.<tier>.crafterRecipes}); 0 without an autocrafter. */
    public int crafterRecipeLimit() {
        VfwServerConfig.MachineSettings settings = VfwServerConfig.machine(tier);
        return crafter == null || settings.crafterRecipes == null ? 0
                : Math.min(settings.crafterRecipes.get(), MachineCrafter.MAX_RECIPES);
    }

    /** SET CRAFT without a selected recipe: adds one. Server side only. */
    public void addCrafterRecipe(List<ItemStack> grid, @Nullable ResourceKey<Recipe<?>> recipeId) {
        if (crafter != null) {
            crafter.add(grid, recipeId);
            crafterEdited();
        }
    }

    /** SET CRAFT with a selected recipe: replaces it. Server side only. */
    public void replaceCrafterRecipe(int index, List<ItemStack> grid, @Nullable ResourceKey<Recipe<?>> recipeId) {
        if (crafter != null) {
            crafter.replace(index, grid, recipeId);
            crafterEdited();
        }
    }

    /** Double click on a recipe: deletes it. Server side only. */
    public void removeCrafterRecipe(int index) {
        if (crafter != null) {
            crafter.remove(index);
            crafterEdited();
        }
    }

    /** CRAFT: ON/OFF. Turning it OFF sends the waiting ingredients to the output. Server side only. */
    public void setCrafterEnabled(boolean on) {
        if (crafter != null) {
            holdReleased(crafter.setEnabled(on));
            harvestRetryRequested = true;
            craftFromOutputRequested |= on; // turned ON: craft what already waits in the output
        }
    }

    /** Matches the recipes now (not on the next tick), so the menu shows the edit at once. */
    private void crafterEdited() {
        if (level instanceof ServerLevel serverLevel) {
            resolveCrafter(serverLevel);
        }
        harvestRetryRequested = true;
    }

    /** Menu refresh: makes sure the recipes are matched (after loading, before the first tick). Server side only. */
    public void ensureCrafterResolved() {
        if (crafter != null && crafter.needsResolve() && level instanceof ServerLevel serverLevel) {
            resolveCrafter(serverLevel);
        }
    }

    /**
     * Right-click with an upgrade in hand (owner spec): moves as many upgrades as fit from {@code held} into the
     * machine, each into its own slot kind (the inventory's slot rules decide, tiers included). One
     * {@code SlotTransaction}; the held stack is shrunk by exactly what was inserted, unless {@code consume} is false
     * (creative). Server side only.
     *
     * @return how many items were inserted (0 = nothing fit, the caller opens the GUI instead)
     */
    public int insertUpgradesFrom(ItemStack held, boolean consume) {
        if (held.isEmpty()) {
            return 0;
        }
        SlotTransaction transaction = new SlotTransaction();
        int inserted = (int) transaction.insertStacking(inputs, 0, inputs.size(), ItemResource.of(held), held.getCount());
        transaction.commit();
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
