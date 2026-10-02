/*
 * LoadBenchmark — dev-only load test (`gradlew runBenchmark`): measures how much server time a Starter and an Entropic
 * Farm Matrix cost per tick while growing, waiting, harvesting, crafting and exporting, and writes a table to the log
 * and to run-gametest/vfw-benchmark.txt. Never fails on numbers: timings depend on the computer running it.
 */
package com.virtualfarmworks.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.compat.mysticalagriculture.MysticalCompat;
import com.virtualfarmworks.machine.FaceMode;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineEnergy;
import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * How it measures: most scenarios create hundreds of machines as detached block entities (real
 * {@link FarmMatrixBlockEntity} objects bound to the test level, not placed as blocks) and call
 * {@link FarmMatrixBlockEntity#serverTick} directly — exactly what the block's ticker calls every tick — timing whole
 * loops with {@link System#nanoTime()}. Vanilla's own per-block-entity ticking overhead is the same for every mod and
 * is not included. Each scenario warms up first so the JIT has compiled the code; tick-only scenarios keep the best of
 * three runs to filter out garbage collection and background noise. The auto-export scenario uses a real placed
 * machine and chest, because it needs a real neighbour.
 *
 * <p>Registered only in the {@code benchmark} run ({@code -Dvirtualfarmworks.benchmark=true}, see build.gradle), and
 * then alone, so nothing else runs on the server thread while it measures. Runs synchronously in one test tick
 * (a few seconds).
 */
final class LoadBenchmark {
    /** System property that registers this benchmark instead of the regular game tests. */
    static final String PROPERTY = "virtualfarmworks.benchmark";

    private static final int MACHINES = 1_000;
    private static final int HARVEST_MACHINES = 200;
    private static final int TIMED_HARVESTS = 20;
    private static final int PLAY_MACHINES = 500;
    private static final int ENTROPIC_MACHINES = 100;
    private static final MachineLayout ENTROPIC = MachineLayout.ENTROPIC;
    private static final double TICK_BUDGET_MS = 50.0;
    /** Mixed Entropic farm: 4 crops on farmland, then flowers x soils, 60 different plant/soil pairs in all. */
    private static final Item[] MIXED_CROPS = {Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS};
    private static final Item[] MIXED_FLOWERS = {Items.DANDELION, Items.POPPY, Items.BLUE_ORCHID, Items.ALLIUM,
            Items.AZURE_BLUET, Items.RED_TULIP, Items.ORANGE_TULIP, Items.WHITE_TULIP, Items.PINK_TULIP,
            Items.OXEYE_DAISY, Items.CORNFLOWER, Items.LILY_OF_THE_VALLEY};
    private static final Item[] MIXED_SOILS = {Items.DIRT, Items.GRASS_BLOCK, Items.PODZOL, Items.COARSE_DIRT,
            Items.ROOTED_DIRT};

    private LoadBenchmark() {
    }

    static void run(GameTestHelper helper) {
        List<String> report = new ArrayList<>();
        report.add("Virtual Farm Works load benchmark (Starter and Entropic Farm Matrix)");
        report.add(String.format(Locale.ROOT, "Java %s, %d CPU threads. Server ticks run on ONE thread; a tick has "
                + "%.0f ms.", Runtime.version(), Runtime.getRuntime().availableProcessors(), TICK_BUDGET_MS));
        report.add("");

        double growingOne = growingNanos(helper, 1);
        double growingFull = growingNanos(helper, 64);
        double waiting = outputFullNanos(helper);
        report.add(row("Growing, 1 plot", micros(growingOne) + " us per machine per tick"));
        report.add(row("Growing, 64 plots", micros(growingFull) + " us per machine per tick"));
        report.add(row("OUTPUT FULL, waiting", micros(waiting) + " us per machine per tick"));

        double wheatFull = harvestMicros(helper, Items.WHEAT_SEEDS, Items.FARMLAND, 64);
        double wheatOne = harvestMicros(helper, Items.WHEAT_SEEDS, Items.FARMLAND, 1);
        report.add(row("Harvest, 64 wheat plots (loot tables)", format(wheatFull) + " us per harvest"));
        report.add(row("Harvest, 1 wheat plot", format(wheatOne) + " us per harvest"));
        Item maSeeds = item("mysticalagriculture:inferium_seeds");
        Item maFarmland = item("mysticalagriculture:inferium_farmland");
        if (MysticalCompat.isLoaded() && maSeeds != Items.AIR && maFarmland != Items.AIR) {
            double maFull = harvestMicros(helper, maSeeds, maFarmland, 64);
            report.add(row("Harvest, 64 Inferium plots (MA formula)", format(maFull) + " us per harvest"));
        } else {
            report.add(row("Harvest, 64 Inferium plots (MA formula)", "skipped: Mystical Agriculture not installed"));
        }
        // Plantables expansion (2026-09-28): fixed yields cost nothing; trees are grown in memory, a few per harvest
        // (config performance.maxTreesGrownPerHarvest, 4), so their cost barely depends on the plot count. Few plots
        // here so every timed harvest fits the output in one batch (a huge fungus is ~70 blocks).
        double poppies = harvestMicros(helper, Items.POPPY, Items.DIRT, 64);
        double oaks = harvestMicros(helper, Items.OAK_SAPLING, Items.DIRT, 32);
        double fungi = harvestMicros(helper, Items.CRIMSON_FUNGUS, Items.DIRT, 8);
        report.add(row("Harvest, 64 poppy plots (10 of itself each)", format(poppies) + " us per harvest"));
        report.add(row("Harvest, 32 oak sapling plots (4 trees grown)", format(oaks) + " us per harvest"));
        report.add(row("Harvest, 8 crimson fungus plots (4 grown)", format(fungi) + " us per harvest"));

        double playWheat = normalPlayNanos(helper, Items.WHEAT_SEEDS, Items.FARMLAND);
        report.add(row("Normal play: 64 wheat plots, 4 upgrades (3x)", micros(playWheat)
                + " us per machine per tick (average, harvests included)"));
        double playOak = normalPlayNanos(helper, Items.OAK_SAPLING, Items.DIRT);
        report.add(row("Normal play: 64 oak saplings, 4 upgrades (3x)", micros(playOak)
                + " us per machine per tick (average, harvests included)"));
        double pipe = pipeNanos(helper, false);
        double pipeFiltered = pipeNanos(helper, true);
        report.add(row("Same + a pipe pulling 1 item per tick", micros(pipe) + " us per machine per tick"));
        report.add(row("Same + pipe + a harvest filter set", micros(pipeFiltered) + " us per machine per tick"));

        double revalidate = revalidateMicros(helper);
        report.add(row("Revalidation (a player changes a slot)", format(revalidate) + " us each, only on changes"));

        double exportWindow = exportWindowMicros(helper);
        report.add(row("Auto-export of 9 stacks into a chest", format(exportWindow)
                + " us per 20-tick window (one export)"));

        report.add("");
        report.add("Entropic Farm Matrix: 60 plot groups, " + MACHINES + " machines growing, " + ENTROPIC_MACHINES
                + " in the other rows; energy refilled and "
                + "outputs emptied between ticks (not timed), faces off.");
        double entropicGrowing = entropicGrowingNanos(helper);
        report.add(row("Entropic growing, 3,840 wheat plots", micros(entropicGrowing) + " us per machine per tick"));
        double entropicWheat = entropicPlayNanos(helper, false);
        report.add(row("Entropic busy: 3,840 wheat plots (3x)", micros(entropicWheat)
                + " us per machine per tick (average, harvests included)"));
        double entropicCrafting = entropicPlayNanos(helper, true);
        report.add(row("Same + autocrafter (9 wheat -> hay bale)", micros(entropicCrafting)
                + " us per machine per tick (average)"));
        double entropicMixed = entropicMixedNanos(helper);
        report.add(row("Entropic busy: 60 different plants x 8 (3x)", micros(entropicMixed)
                + " us per machine per tick (average, harvests included)"));
        double entropicRevalidate = entropicRevalidateMicros(helper);
        report.add(row("Entropic revalidation (60 groups)", format(entropicRevalidate) + " us each, only on changes"));
        double entropicInput = entropicInputNanos(helper);
        report.add(row("Entropic growing + an input change every tick", micros(entropicInput)
                + " us per machine per tick (e.g. a pipe filling the grids)"));

        report.add("");
        double perMachine = playWheat / 1_000.0; // microseconds per machine per tick, busy machines
        report.add(String.format(Locale.ROOT, "Busy machines: %.1f per millisecond of tick time. 100 machines use "
                + "%.3f ms (%.2f%% of a tick); 1,000 use %.2f ms (%.1f%%).",
                1_000.0 / perMachine, 100 * perMachine / 1_000.0, 100 * perMachine / 1_000.0 / TICK_BUDGET_MS * 100,
                1_000 * perMachine / 1_000.0, 1_000 * perMachine / 1_000.0 / TICK_BUDGET_MS * 100));
        report.add("Growing cost does not depend on the plot count: plots are two counters, not objects.");

        String text = String.join(System.lineSeparator(), report);
        VirtualFarmWorks.LOGGER.info("{}{}{}", System.lineSeparator(), text, System.lineSeparator());
        try {
            Files.writeString(FMLPaths.GAMEDIR.get().resolve("vfw-benchmark.txt"), text + System.lineSeparator());
        } catch (IOException e) {
            VirtualFarmWorks.LOGGER.warn("Could not write vfw-benchmark.txt", e);
        }
        helper.succeed();
    }

    // --- scenarios --------------------------------------------------------------------------------------------------

    /** RUNNING machines with the bar below 100% (the state a farm is in almost all the time). */
    private static double growingNanos(GameTestHelper helper, int plots) {
        List<FarmMatrixBlockEntity> machines = machines(helper, MACHINES, Items.WHEAT_SEEDS, Items.FARMLAND, plots, 0);
        expect(helper, machines.getFirst().status() == MachineStatus.RUNNING, "growing machines must be RUNNING");
        // 100 + 3 x 150 ticks stay below the 600-tick cycle: no harvest happens in this scenario.
        tickAll(helper, machines, 100);
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            best = Math.min(best, tickAll(helper, machines, 150));
        }
        return best / (double) (MACHINES * 150L);
    }

    /** Due harvest, visible and hidden output full: the machine waits and must cost (almost) nothing. */
    private static double outputFullNanos(GameTestHelper helper) {
        List<FarmMatrixBlockEntity> machines = machines(helper, MACHINES, Items.WHEAT_SEEDS, Items.FARMLAND, 64, 0);
        for (FarmMatrixBlockEntity machine : machines) {
            fill(machine.output());
            fill(machine.internalOutput());
            machine.setProgressForTesting(1.0);
        }
        tickAll(helper, machines, 100);
        expect(helper, machines.getFirst().status() == MachineStatus.OUTPUT_FULL, "waiting machines must be OUTPUT FULL");
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            best = Math.min(best, tickAll(helper, machines, 150));
        }
        return best / (double) (MACHINES * 150L);
    }

    /**
     * The tick in which a harvest happens, with an empty output. Machines first complete a few full harvests so the
     * batch sizing has measured the yield (after that a harvest is one batch in one tick, which is checked).
     */
    private static double harvestMicros(GameTestHelper helper, Item seed, Item soil, int plots) {
        ServerLevel level = helper.getLevel();
        List<FarmMatrixBlockEntity> machines = machines(helper, HARVEST_MACHINES, seed, soil, plots, 0);
        for (int warmUp = 0; warmUp < 3; warmUp++) {
            for (FarmMatrixBlockEntity machine : machines) {
                machine.setProgressForTesting(1.0);
                for (int tick = 0; tick < 20 && machine.progress() >= 0.999; tick++) {
                    machine.serverTick(level);
                }
                clearOutputs(machine);
            }
        }
        long total = 0;
        int oneTick = 0;
        for (int rep = 0; rep < TIMED_HARVESTS; rep++) {
            for (FarmMatrixBlockEntity machine : machines) {
                machine.setProgressForTesting(1.0);
                long start = System.nanoTime();
                machine.serverTick(level);
                total += System.nanoTime() - start;
                if (machine.progress() < 0.5) {
                    oneTick++;
                }
                clearOutputs(machine);
            }
        }
        int harvests = TIMED_HARVESTS * HARVEST_MACHINES;
        expect(helper, oneTick == harvests, "every timed harvest must complete in one tick, " + oneTick + "/" + harvests);
        return total / 1_000.0 / harvests;
    }

    /**
     * A realistic busy farm: 64 plots at 3x speed (a harvest every 200 ticks), bars spread so harvests do not all
     * happen on the same tick, outputs emptied every 20 ticks like auto-export would (emptying is not timed).
     */
    private static double normalPlayNanos(GameTestHelper helper, Item seed, Item soil) {
        ServerLevel level = helper.getLevel();
        List<FarmMatrixBlockEntity> machines = machines(helper, PLAY_MACHINES, seed, soil, 64, MachineSlots.GROWTH_COUNT);
        for (int i = 0; i < machines.size(); i++) {
            machines.get(i).setProgressForTesting(i / (double) machines.size());
        }
        playTicks(level, machines, 400); // two cycles: every machine has harvested at least once
        long total = playTicks(level, machines, 1_200);
        return total / (double) (PLAY_MACHINES * 1_200L);
    }

    private static long playTicks(ServerLevel level, List<FarmMatrixBlockEntity> machines, int ticks) {
        long total = 0;
        for (int tick = 0; tick < ticks; tick++) {
            long start = System.nanoTime();
            for (FarmMatrixBlockEntity machine : machines) {
                machine.serverTick(level);
            }
            total += System.nanoTime() - start;
            if (tick % 20 == 0) {
                machines.forEach(LoadBenchmark::clearOutputs);
            }
        }
        return total;
    }

    /**
     * The busy farm again, with a pipe pulling ONE item per tick from the visible output (not timed), so the output
     * changes on most ticks: the worst case for work the machine does after an output change (hidden-slot refill,
     * harvest filter cleanup). With {@code filtered}, a blacklist holding an item the farm never produces (dirt) is
     * set: the filter is active but yields do not change, so the difference between the two rows is the filter's cost.
     */
    private static double pipeNanos(GameTestHelper helper, boolean filtered) {
        ServerLevel level = helper.getLevel();
        List<FarmMatrixBlockEntity> machines = machines(helper, PLAY_MACHINES, Items.WHEAT_SEEDS, Items.FARMLAND, 64,
                MachineSlots.GROWTH_COUNT);
        for (int i = 0; i < machines.size(); i++) {
            FarmMatrixBlockEntity machine = machines.get(i);
            if (filtered) {
                machine.filter().set(0, Items.DIRT);
            }
            machine.setProgressForTesting(i / (double) machines.size());
        }
        pipeTicks(level, machines, 400);
        return pipeTicks(level, machines, 1_200) / (double) (PLAY_MACHINES * 1_200L);
    }

    private static long pipeTicks(ServerLevel level, List<FarmMatrixBlockEntity> machines, int ticks) {
        long total = 0;
        for (int tick = 0; tick < ticks; tick++) {
            long start = System.nanoTime();
            for (FarmMatrixBlockEntity machine : machines) {
                machine.serverTick(level);
            }
            total += System.nanoTime() - start;
            machines.forEach(LoadBenchmark::pipePullOne);
        }
        return total;
    }

    /** A slow pipe: takes one item from the first filled visible slot, through the capability. */
    private static void pipePullOne(FarmMatrixBlockEntity machine) {
        var external = machine.externalOutput();
        try (Transaction transaction = Transaction.openRoot()) {
            for (int i = 0; i < external.size(); i++) {
                ItemResource resource = external.getResource(i);
                if (!resource.isEmpty()) {
                    external.extract(i, resource, 1, transaction);
                    break;
                }
            }
            transaction.commit();
        }
    }

    /** Full re-analysis of the slots (runs only when a slot, the config or tags change). */
    private static double revalidateMicros(GameTestHelper helper) {
        List<FarmMatrixBlockEntity> machines = machines(helper, MACHINES, Items.WHEAT_SEEDS, Items.FARMLAND, 64, 0);
        for (FarmMatrixBlockEntity machine : machines) {
            machine.revalidate(); // warm-up
        }
        long start = System.nanoTime();
        for (int rep = 0; rep < 20; rep++) {
            for (FarmMatrixBlockEntity machine : machines) {
                machine.revalidate();
            }
        }
        return (System.nanoTime() - start) / 1_000.0 / (MACHINES * 20L);
    }

    /** A real placed machine pushing 9 full stacks into a real chest; 20 ticks contain exactly one export. */
    private static double exportWindowMicros(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos machinePos = new BlockPos(1, 1, 1);
        BlockPos chestPos = machinePos.east(); // the machine's left side (front faces north): export enabled
        helper.setBlock(machinePos, ModBlocks.STARTER_FARM_MATRIX.get().defaultBlockState());
        helper.setBlock(chestPos, Blocks.CHEST);
        FarmMatrixBlockEntity machine = helper.getBlockEntity(machinePos, FarmMatrixBlockEntity.class);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);

        long total = 0;
        int windows = 0;
        for (int rep = 0; rep < 250; rep++) {
            for (int i = 0; i < MachineSlots.OUTPUT_COUNT; i++) {
                machine.output().set(i, ItemResource.of(Items.WHEAT), 64);
            }
            long start = System.nanoTime();
            for (int tick = 0; tick < 20; tick++) {
                machine.serverTick(level);
            }
            long spent = System.nanoTime() - start;
            expect(helper, machine.output().isEmpty(), "the export must empty the buffer within 20 ticks");
            chest.clearContent();
            if (rep >= 50) { // the first 50 windows are warm-up
                total += spent;
                windows++;
            }
        }
        return total / 1_000.0 / windows;
    }

    // --- Entropic scenarios -----------------------------------------------------------------------------------------

    /**
     * Entropic machines full of wheat (3,840 plots), growing: the cost must stay that of a 1-plot machine. As many
     * machines and ticks as the Starter's growing rows, so both are measured alike.
     */
    private static double entropicGrowingNanos(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<FarmMatrixBlockEntity> machines = entropicMachines(helper, MACHINES, group -> Items.WHEAT_SEEDS,
                group -> Items.FARMLAND, 64, 0);
        expect(helper, machines.getFirst().status() == MachineStatus.RUNNING, "growing Entropics must be RUNNING, got "
                + machines.getFirst().status());
        // 100 + 3 x 150 ticks stay below the 600-tick cycle: no harvest happens in this scenario.
        entropicTicks(level, machines, 100);
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 3; run++) {
            best = Math.min(best, entropicTicks(level, machines, 150));
        }
        return best / (double) (MACHINES * 150L);
    }

    /**
     * A busy Entropic: 3,840 wheat plots at 3x speed (a harvest every 200 ticks), bars spread, outputs emptied every
     * 20 ticks. A harvest (3,840 wheat and ~6,500 extra seeds) is bigger than the 96 output slots, so it is stored in
     * batches over several drains, like a real machine limited by its export. With {@code crafting}, a hay bale
     * recipe turns the wheat into bales first.
     */
    private static double entropicPlayNanos(GameTestHelper helper, boolean crafting) {
        ServerLevel level = helper.getLevel();
        List<FarmMatrixBlockEntity> machines = entropicMachines(helper, ENTROPIC_MACHINES, group -> Items.WHEAT_SEEDS,
                group -> Items.FARMLAND, 64, MachineSlots.GROWTH_COUNT);
        for (int i = 0; i < machines.size(); i++) {
            FarmMatrixBlockEntity machine = machines.get(i);
            if (crafting) {
                machine.addCrafterRecipe(Collections.nCopies(9, new ItemStack(Items.WHEAT)), null);
            }
            machine.setProgressForTesting(i / (double) machines.size());
        }
        expect(helper, !crafting || machines.getFirst().crafter().isActive(), "the hay bale recipe must be active");
        entropicTicks(level, machines, 400); // two cycles: every machine has harvested at least once
        return entropicTicks(level, machines, 1_200) / (double) (ENTROPIC_MACHINES * 1_200L);
    }

    /**
     * The worst case for harvest units: 60 different plant/soil pairs (4 crops, 56 flower and soil pairs), 8 plots
     * each, so a harvest is 60 rolls spread over ticks (at most 4 batches per tick).
     */
    private static double entropicMixedNanos(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<FarmMatrixBlockEntity> machines = entropicMachines(helper, ENTROPIC_MACHINES, LoadBenchmark::mixedSeed,
                LoadBenchmark::mixedSoil, 8, MachineSlots.GROWTH_COUNT);
        FarmMatrixBlockEntity first = machines.getFirst();
        for (int group = 0; group < ENTROPIC.groups(); group++) {
            expect(helper, first.groupStatus(group) == MachineStatus.RUNNING, "mixed group " + group + " ("
                    + mixedSeed(group) + " on " + mixedSoil(group) + ") must grow, got " + first.groupStatus(group));
        }
        for (int i = 0; i < machines.size(); i++) {
            machines.get(i).setProgressForTesting(i / (double) machines.size());
        }
        entropicTicks(level, machines, 400);
        return entropicTicks(level, machines, 1_200) / (double) (ENTROPIC_MACHINES * 1_200L);
    }

    private static Item mixedSeed(int group) {
        return group < MIXED_CROPS.length ? MIXED_CROPS[group]
                : MIXED_FLOWERS[(group - MIXED_CROPS.length) % MIXED_FLOWERS.length];
    }

    private static Item mixedSoil(int group) {
        return group < MIXED_CROPS.length ? Items.FARMLAND
                : MIXED_SOILS[(group - MIXED_CROPS.length) / MIXED_FLOWERS.length];
    }

    /** Full re-analysis of the 60 plot groups (runs only when a slot, the config or tags change). */
    private static double entropicRevalidateMicros(GameTestHelper helper) {
        List<FarmMatrixBlockEntity> machines = entropicMachines(helper, ENTROPIC_MACHINES, group -> Items.WHEAT_SEEDS,
                group -> Items.FARMLAND, 64, 0);
        for (FarmMatrixBlockEntity machine : machines) {
            machine.revalidate(); // warm-up
        }
        long start = System.nanoTime();
        for (int rep = 0; rep < 20; rep++) {
            for (FarmMatrixBlockEntity machine : machines) {
                machine.revalidate();
            }
        }
        return (System.nanoTime() - start) / 1_000.0 / (ENTROPIC_MACHINES * 20L);
    }

    /**
     * Growing Entropics whose inputs change every tick (one seed more or less in group 0, not timed), so every timed
     * tick revalidates: the cost of a pipe that keeps feeding the grids.
     */
    private static double entropicInputNanos(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<FarmMatrixBlockEntity> machines = entropicMachines(helper, ENTROPIC_MACHINES, group -> Items.WHEAT_SEEDS,
                group -> Items.FARMLAND, 64, 0);
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 4; run++) { // the first run is warm-up
            long total = 0;
            for (int tick = 0; tick < 130; tick++) {
                for (FarmMatrixBlockEntity machine : machines) {
                    machine.inputs().set(ENTROPIC.seedSlot(0), ItemResource.of(Items.WHEAT_SEEDS), 63 + tick % 2);
                }
                long start = System.nanoTime();
                for (FarmMatrixBlockEntity machine : machines) {
                    machine.serverTick(level);
                }
                total += System.nanoTime() - start;
                machines.forEach(LoadBenchmark::refillEnergy);
            }
            if (run > 0) {
                best = Math.min(best, total);
            }
        }
        expect(helper, machines.getFirst().progress() < 0.999, "no harvest may happen while measuring input changes");
        return best / (double) (ENTROPIC_MACHINES * 130L);
    }

    /** Ticks every Entropic {@code ticks} times; energy refilled and outputs emptied every 20 ticks, both not timed. */
    private static long entropicTicks(ServerLevel level, List<FarmMatrixBlockEntity> machines, int ticks) {
        long total = 0;
        for (int tick = 0; tick < ticks; tick++) {
            long start = System.nanoTime();
            for (FarmMatrixBlockEntity machine : machines) {
                machine.serverTick(level);
            }
            total += System.nanoTime() - start;
            machines.forEach(LoadBenchmark::refillEnergy);
            if (tick % 20 == 0) {
                machines.forEach(LoadBenchmark::clearOutputs);
            }
        }
        return total;
    }

    /**
     * Detached Entropic machines bound to the test level: every plot group holds {@code plotsPerGroup} of its seed and
     * soil; a Water Provider, {@code growthUpgrades} Growth Speed Upgrades, full energy, every face NONE.
     */
    private static List<FarmMatrixBlockEntity> entropicMachines(GameTestHelper helper, int count,
                                                                IntFunction<Item> seedOf,
                                                                IntFunction<Item> soilOf,
                                                                int plotsPerGroup, int growthUpgrades) {
        BlockState state = ModBlocks.ENTROPIC_FARM_MATRIX.get().defaultBlockState();
        Item water = ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.ENTROPIC).get();
        Item growth = ModItems.GROWTH_SPEED_UPGRADES.get(MachineTier.ENTROPIC).get();
        List<FarmMatrixBlockEntity> machines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FarmMatrixBlockEntity machine = new FarmMatrixBlockEntity(helper.absolutePos(new BlockPos(i % 32, 5,
                    i / 32)), state);
            machine.setLevel(helper.getLevel());
            for (int group = 0; group < ENTROPIC.groups(); group++) {
                machine.inputs().set(ENTROPIC.seedSlot(group), ItemResource.of(seedOf.apply(group)), plotsPerGroup);
                machine.inputs().set(ENTROPIC.soilSlot(group), ItemResource.of(soilOf.apply(group)), plotsPerGroup);
            }
            machine.inputs().set(ENTROPIC.waterSlot(), ItemResource.of(water), 1);
            for (int g = 0; g < growthUpgrades; g++) {
                machine.inputs().set(ENTROPIC.growthSlot(g), ItemResource.of(growth), 1);
            }
            for (RelativeSide side : RelativeSide.all()) {
                while (machine.faceMode(side) != FaceMode.NONE) {
                    machine.cycleFaceMode(side, true);
                }
            }
            machine.revalidate();
            refillEnergy(machine);
            machine.revalidate(); // with energy: RUNNING
            machines.add(machine);
        }
        return machines;
    }

    private static void refillEnergy(FarmMatrixBlockEntity machine) {
        MachineEnergy energy = machine.energy();
        if (energy != null) {
            energy.set((int) Math.min(Integer.MAX_VALUE, energy.getCapacityAsLong()));
        }
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    /**
     * Detached Starter machines bound to the test level: {@code plots} seeds and soils, a Water Provider,
     * {@code growthUpgrades} Growth Speed Upgrades. Auto-export is switched off (these machines have no real
     * neighbours; the export is measured separately).
     */
    private static List<FarmMatrixBlockEntity> machines(GameTestHelper helper, int count, Item seed, Item soil,
                                                        int plots, int growthUpgrades) {
        BlockState state = ModBlocks.STARTER_FARM_MATRIX.get().defaultBlockState();
        Item water = ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get();
        Item growth = ModItems.GROWTH_SPEED_UPGRADES.get(MachineTier.STARTER).get();
        List<FarmMatrixBlockEntity> machines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FarmMatrixBlockEntity machine = new FarmMatrixBlockEntity(helper.absolutePos(new BlockPos(i % 32, 3,
                    i / 32)), state);
            machine.setLevel(helper.getLevel());
            machine.inputs().set(MachineSlots.SEED, ItemResource.of(seed), plots);
            machine.inputs().set(MachineSlots.SOIL, ItemResource.of(soil), plots);
            machine.inputs().set(MachineSlots.WATER_PROVIDER, ItemResource.of(water), 1);
            for (int g = 0; g < growthUpgrades; g++) {
                machine.inputs().set(MachineSlots.GROWTH_FIRST + g, ItemResource.of(growth), 1);
            }
            for (RelativeSide side : RelativeSide.all()) {
                if (machine.isOutputEnabled(side)) {
                    machine.toggleOutput(side);
                }
            }
            machine.revalidate();
            machines.add(machine);
        }
        return machines;
    }

    /** Ticks every machine {@code ticks} times; returns the nanoseconds spent. */
    private static long tickAll(GameTestHelper helper, List<FarmMatrixBlockEntity> machines, int ticks) {
        ServerLevel level = helper.getLevel();
        long start = System.nanoTime();
        for (int tick = 0; tick < ticks; tick++) {
            for (FarmMatrixBlockEntity machine : machines) {
                machine.serverTick(level);
            }
        }
        return System.nanoTime() - start;
    }

    private static void fill(ItemStacksResourceHandler handler) {
        for (int i = 0; i < handler.size(); i++) {
            handler.set(i, ItemResource.of(Items.DIRT), 64);
        }
    }

    private static void clearOutputs(FarmMatrixBlockEntity machine) {
        clear(machine.output());
        clear(machine.internalOutput());
    }

    private static void clear(ItemStacksResourceHandler handler) {
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getAmountAsLong(i) > 0) {
                handler.set(i, ItemResource.EMPTY, 0);
            }
        }
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static void expect(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal("benchmark setup: " + message));
    }

    private static String row(String scenario, String result) {
        return String.format(Locale.ROOT, "%-46s %s", scenario, result);
    }

    private static String micros(double nanos) {
        return format(nanos / 1_000.0);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, value < 1.0 ? "%.3f" : "%.1f", value);
    }
}
