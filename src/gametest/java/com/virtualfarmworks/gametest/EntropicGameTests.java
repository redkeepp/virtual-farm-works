/*
 * EntropicGameTests — game tests of the Entropic Farm Matrix (owner spec 2026-09-29): 60 plot groups that must all be
 * valid, waiting plots, FE consumption and MISSING FE, face modes (pipe and chest input into the grids, the grids'
 * seeds and soils given back), replant, the menu, and groups with the same plant harvested together.
 */
package com.virtualfarmworks.gametest;

import org.jetbrains.annotations.Nullable;

import com.virtualfarmworks.machine.FaceMode;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineEnergy;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.AbstractFarmMatrixMenu;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.sim.MachineStatus;
import com.virtualfarmworks.transfer.ItemResource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

final class EntropicGameTests {
    /** Where the machine stands: y 1, the test area's first layer (relative y 0 is the test's structure block). */
    private static final BlockPos MACHINE = new BlockPos(0, 1, 0);
    private static final MachineLayout LAYOUT = MachineLayout.ENTROPIC;

    private EntropicGameTests() {
    }

    /**
     * Owner (2026-09-29): the Entropic runs only while every group holding a plantable can grow, and it needs no hoe.
     * Wheat on farmland, wheat on dirt (tilled for free) and a sapling on dirt grow; a soil alone is waiting plots.
     * One group with an invalid soil, or a plantable without its soil, stops the whole machine with that problem (the
     * bar freezes); fixed, it runs again.
     */
    static void groupsMustAllBeValid(GameTestHelper helper) {
        var level = helper.getLevel();
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 10);
        put(inputs, LAYOUT.soilSlot(0), Items.FARMLAND, 10);
        put(inputs, LAYOUT.seedSlot(1), Items.WHEAT_SEEDS, 5);
        put(inputs, LAYOUT.soilSlot(1), Items.DIRT, 5);
        put(inputs, LAYOUT.seedSlot(2), Items.OAK_SAPLING, 3);
        put(inputs, LAYOUT.soilSlot(2), Items.DIRT, 20);
        put(inputs, LAYOUT.soilSlot(3), Items.DIRT, 7);
        machine.revalidate();

        check(helper, machine.groupStatus(0) == MachineStatus.RUNNING, "group 0 must grow: " + machine.groupStatus(0));
        check(helper, machine.groupStatus(1) == MachineStatus.RUNNING, "wheat on dirt needs no hoe here: "
                + machine.groupStatus(1));
        check(helper, machine.groupStatus(2) == MachineStatus.RUNNING, "group 2 must grow: " + machine.groupStatus(2));
        check(helper, machine.groupStatus(3) == MachineStatus.MISSING_SEED, "group 3 is only soil");
        check(helper, machine.totalPlots() == 18, "10 + 5 wheat + 3 saplings: " + machine.totalPlots());
        check(helper, machine.waitingPlots() == 24, "17 + 7 soils wait: " + machine.waitingPlots());
        check(helper, machine.status() == MachineStatus.MISSING_FE, "no energy yet: " + machine.status());
        check(helper, machine.energyPerTick() == 18L * 90, "90 FE per plot per tick: " + machine.energyPerTick());
        fillEnergy(machine);
        machine.serverTick(level);
        check(helper, machine.status() == MachineStatus.RUNNING && machine.progress() > 0.0, "with energy it runs: "
                + machine.status());

        // One invalid group stops everything.
        put(inputs, LAYOUT.seedSlot(4), Items.WHEAT_SEEDS, 4);
        put(inputs, LAYOUT.soilSlot(4), Items.SOUL_SAND, 4);
        double before = machine.progress();
        fillEnergy(machine);
        machine.serverTick(level);
        machine.serverTick(level);
        check(helper, machine.groupStatus(4) == MachineStatus.INVALID_SOIL
                && machine.status() == MachineStatus.INVALID_SOIL, "one invalid group stops the machine: "
                + machine.status());
        check(helper, machine.progress() == before, "the bar freezes meanwhile");

        // Fixed: runs again. A plantable without its soil stops it as well.
        put(inputs, LAYOUT.soilSlot(4), Items.FARMLAND, 4);
        put(inputs, LAYOUT.seedSlot(5), Items.CARROT, 3);
        machine.serverTick(level);
        check(helper, machine.status() == MachineStatus.MISSING_SOIL, "a plantable without soil stops it: "
                + machine.status());
        inputs.set(LAYOUT.seedSlot(5), ItemResource.EMPTY, 0);
        fillEnergy(machine);
        machine.serverTick(level);
        check(helper, machine.status() == MachineStatus.RUNNING && machine.progress() > before,
                "all groups valid again: it runs, got " + machine.status());
        helper.succeed();
    }

    /**
     * Owner (2026-09-29): an INPUT face also pulls from an inventory glued to it, like the output pushes. A chest of
     * seeds, soils and a diamond above the machine: seeds and soils go in, paired; the diamond stays.
     */
    static void inputFacePullsFromChests(GameTestHelper helper) {
        var level = helper.getLevel();
        FarmMatrixBlockEntity machine = placeMachine(helper);
        helper.setBlock(MACHINE.above(), Blocks.CHEST);
        ChestBlockEntity chest = helper.<ChestBlockEntity>getBlockEntity(MACHINE.above());
        chest.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 30));
        chest.setItem(1, new ItemStack(Items.FARMLAND, 30));
        chest.setItem(2, new ItemStack(Items.DIAMOND, 5));
        while (machine.faceMode(RelativeSide.TOP) != FaceMode.INPUT) {
            machine.cycleFaceMode(RelativeSide.TOP, true);
        }
        for (int tick = 0; tick < 25; tick++) { // one transfer window (output.autoExportIntervalTicks, 20)
            machine.serverTick(level);
        }
        MachineInventory inputs = machine.inputs();
        check(helper, inputs.getResource(LAYOUT.seedSlot(0)).is(Items.WHEAT_SEEDS)
                && inputs.getAmountAsInt(LAYOUT.seedSlot(0)) == 30, "the seeds are pulled into the seed grid");
        check(helper, inputs.getResource(LAYOUT.soilSlot(0)).is(Items.FARMLAND)
                && inputs.getAmountAsInt(LAYOUT.soilSlot(0)) == 30, "the soils are pulled under them");
        check(helper, chest.getItem(0).isEmpty() && chest.getItem(1).isEmpty() && chest.getItem(2).is(Items.DIAMOND)
                && chest.getItem(2).getCount() == 5, "only what the grids accept leaves the chest");
        helper.succeed();
    }

    /**
     * The buffer holds three ticks of the highest possible consumption (owner formula). Without energy the bar does not
     * move; with energy from a cable the machine runs and pays 90 FE per plot per tick.
     */
    static void energyRunsTheMachine(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 20);
        put(inputs, LAYOUT.soilSlot(0), Items.FARMLAND, 20);
        machine.revalidate();
        MachineEnergy energy = machine.energy();
        check(helper, energy != null, "the Entropic has an energy buffer");
        check(helper, energy.getCapacityAsLong() == 60L * 64 * 90 * 3, "capacity = 60 x 64 x 90 x 3, got "
                + energy.getCapacityAsLong());

        for (int i = 0; i < 5; i++) {
            machine.serverTick(helper.getLevel());
        }
        check(helper, machine.status() == MachineStatus.MISSING_FE && machine.progress() == 0.0,
                "without energy the bar must not move: " + machine.status() + " " + machine.progress());

        IEnergyStorage cable = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(MACHINE), Direction.UP);
        check(helper, cable != null, "energy capability on every face");
        cable.receiveEnergy(100_000, false);
        machine.serverTick(helper.getLevel());
        check(helper, machine.status() == MachineStatus.RUNNING, "with energy it runs: " + machine.status());
        check(helper, energy.getAmountAsLong() == 100_000 - 20 * 90, "one tick costs 20 x 90 FE, left "
                + energy.getAmountAsLong());
        check(helper, machine.progress() > 0.0, "the bar moves");
        helper.succeed();
    }

    /**
     * Owner (2026-09-30): with its source gone, the buffer drains to exactly 0. 10 plots (900 FE/t) and 2,000 FE: two
     * whole ticks (RUNNING), then the last 200 FE pay part of a tick (RUNNING WITH LOW FE, the bar moves 2/9 of a
     * tick), then the bar stops at 0 FE (MISSING FE).
     */
    static void energyDrainsToZero(GameTestHelper helper) {
        var level = helper.getLevel();
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 10);
        put(inputs, LAYOUT.soilSlot(0), Items.FARMLAND, 10);
        noFaces(machine);
        machine.revalidate();
        MachineEnergy energy = machine.energy();
        energy.set(2_000);
        machine.serverTick(level);
        machine.serverTick(level);
        double twoTicks = machine.progress();
        check(helper, machine.status() == MachineStatus.RUNNING, "whole ticks: RUNNING, got " + machine.status());
        check(helper, energy.getAmountAsLong() == 200, "two whole ticks: 2,000 - 2 x 900 = 200, got "
                + energy.getAmountAsLong());

        machine.serverTick(level);
        double part = machine.progress() - twoTicks;
        check(helper, energy.getAmountAsLong() == 0, "the last 200 FE are spent, left " + energy.getAmountAsLong());
        check(helper, Math.abs(part - twoTicks / 2 * 200 / 900) < 1e-12, "the bar moves 2/9 of a tick, moved "
                + part / (twoTicks / 2));
        check(helper, machine.status() == MachineStatus.RUNNING_LOW_FE,
                "part of a tick: RUNNING WITH LOW FE, got " + machine.status());

        machine.serverTick(level);
        check(helper, machine.progress() == twoTicks + part, "at 0 FE the bar stops");
        check(helper, machine.status() == MachineStatus.MISSING_FE, "an empty buffer: MISSING FE, got "
                + machine.status());
        helper.succeed();
    }

    /**
     * Face modes: OUTPUT ALL by default; INPUT takes plantables into the seed grid and soils into the soil grid, pairing
     * them; OUTPUT ONLY SEEDS AND SOILS shows the grids, extract-only; NONE shows nothing; OUTPUT shows an extract-only
     * output.
     */
    static void facesAndPipeInput(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        check(helper, machine.faceMode(RelativeSide.TOP) == FaceMode.OUTPUT_ALL, "default mode is OUTPUT ALL");
        machine.cycleFaceMode(RelativeSide.TOP, true); // OUTPUT ALL -> INPUT
        check(helper, machine.faceMode(RelativeSide.TOP) == FaceMode.INPUT, "next mode is INPUT");

        IItemHandler input = itemHandler(helper, Direction.UP);
        check(helper, input != null, "INPUT face has a capability");
        // Pipes name a slot; the grids route every stack whatever the slot (1.21.1 has no index-less insertion).
        check(helper, input.insertItem(0, new ItemStack(Items.WHEAT_SEEDS, 70), false).isEmpty(), "70 seeds go in");
        check(helper, input.insertItem(5, new ItemStack(Items.FARMLAND, 70), false).isEmpty(), "70 soils go in");
        check(helper, input.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount() == 1, "a diamond is refused");
        MachineInventory inputs = machine.inputs();
        check(helper, inputs.getAmountAsInt(LAYOUT.seedSlot(0)) == 64 && inputs.getAmountAsInt(LAYOUT.seedSlot(1)) == 6,
                "seeds fill slot 0 then slot 1");
        check(helper, inputs.getAmountAsInt(LAYOUT.soilSlot(0)) == 64 && inputs.getAmountAsInt(LAYOUT.soilSlot(1)) == 6,
                "soils go under their seeds");

        // OUTPUT ONLY SEEDS AND SOILS (after INPUT): the grids themselves, nothing goes in.
        machine.cycleFaceMode(RelativeSide.TOP, true);
        check(helper, machine.faceMode(RelativeSide.TOP) == FaceMode.OUTPUT_SEEDS_AND_SOILS,
                "INPUT -> OUTPUT ONLY SEEDS AND SOILS");
        IItemHandler grids = itemHandler(helper, Direction.UP);
        check(helper, grids != null && grids.getSlots() == 2 * LAYOUT.groups()
                && grids.getStackInSlot(LAYOUT.seedSlot(0)).getCount() == 64, "the face shows the two grids");
        check(helper, grids.insertItem(LAYOUT.seedSlot(1), new ItemStack(Items.WHEAT_SEEDS), true).getCount() == 1,
                "a seeds-and-soils output takes nothing in");

        // NONE: nothing on that face. Top: OUTPUT ONLY SEEDS AND SOILS -> NONE.
        machine.cycleFaceMode(RelativeSide.TOP, true);
        check(helper, machine.faceMode(RelativeSide.TOP) == FaceMode.NONE, "OUTPUT ONLY SEEDS AND SOILS -> NONE");
        check(helper, itemHandler(helper, Direction.UP) == null, "a NONE face shows nothing");

        // OUTPUT (NONE -> OUTPUT): extract-only.
        machine.cycleFaceMode(RelativeSide.TOP, true);
        IItemHandler out = itemHandler(helper, Direction.UP);
        check(helper, out != null, "OUTPUT face has a capability");
        check(helper, out.insertItem(0, new ItemStack(Items.WHEAT_SEEDS), false).getCount() == 1,
                "outputs refuse input");
        helper.succeed();
    }

    /**
     * Owner (2026-10-02): an OUTPUT ONLY SEEDS AND SOILS face gives the grids' seeds and soils and nothing else. A pipe
     * on it takes planted items out but puts nothing in; a chest glued to it receives every seed and soil (their plots
     * go with them), never the harvest, the upgrades or anything else; OUTPUT ALL PRODUCED never shows the grids.
     */
    static void seedsAndSoilsFaceEmptiesTheGrids(GameTestHelper helper) {
        var level = helper.getLevel();
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        Item growth = ModItems.GROWTH_SPEED_UPGRADES.get(MachineTier.ENTROPIC).get();
        put(inputs, LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 20);
        put(inputs, LAYOUT.soilSlot(0), Items.FARMLAND, 20);
        put(inputs, LAYOUT.seedSlot(5), Items.CARROT, 10);
        put(inputs, LAYOUT.soilSlot(5), Items.FARMLAND, 10);
        put(inputs, LAYOUT.growthSlot(0), growth, 1);
        machine.output().set(0, ItemResource.of(Items.WHEAT), 7); // a harvest waiting in the output
        machine.revalidate();
        check(helper, machine.totalPlots() == 30, "30 plots planted, got " + machine.totalPlots());

        IItemHandler all = itemHandler(helper, Direction.UP); // the default mode, OUTPUT ALL
        check(helper, holds(all, Items.WHEAT) && !holds(all, Items.WHEAT_SEEDS) && !holds(all, Items.FARMLAND),
                "OUTPUT ALL PRODUCED shows the harvest, never the grids");

        while (machine.faceMode(RelativeSide.TOP) != FaceMode.OUTPUT_SEEDS_AND_SOILS) {
            machine.cycleFaceMode(RelativeSide.TOP, true);
        }
        IItemHandler grids = itemHandler(helper, Direction.UP);
        check(helper, grids != null && !holds(grids, Items.WHEAT) && !holds(grids, growth),
                "the seeds-and-soils face shows neither the harvest nor the upgrades");
        check(helper, grids.insertItem(LAYOUT.seedSlot(5), new ItemStack(Items.CARROT), false).getCount() == 1,
                "nothing goes in");
        check(helper, grids.extractItem(LAYOUT.seedSlot(5), 3, false).getCount() == 3, "a pipe takes 3 carrots");

        helper.setBlock(MACHINE.above(), Blocks.CHEST);
        ChestBlockEntity chest = helper.<ChestBlockEntity>getBlockEntity(MACHINE.above());
        for (int tick = 0; tick < 25; tick++) { // one transfer window (output.autoExportIntervalTicks, 20), then a tick
            machine.serverTick(level);
        }
        check(helper, countIn(chest, Items.WHEAT_SEEDS) == 20 && countIn(chest, Items.FARMLAND) == 30
                && countIn(chest, Items.CARROT) == 7, "the chest gets every planted seed and soil");
        check(helper, countIn(chest, Items.WHEAT) == 0 && countIn(chest, growth) == 0,
                "never the harvest or the upgrades");
        for (int group = 0; group < LAYOUT.groups(); group++) {
            check(helper, inputs.getAmountAsLong(LAYOUT.seedSlot(group)) == 0
                    && inputs.getAmountAsLong(LAYOUT.soilSlot(group)) == 0, "the grids are empty");
        }
        check(helper, inputs.getResource(LAYOUT.growthSlot(0)).is(growth), "the upgrade stays in the machine");
        check(helper, machine.output().getAmountAsInt(0) == 7, "the harvest stays in the output");
        check(helper, machine.totalPlots() == 0 && machine.status() == MachineStatus.MISSING_SEED,
                "the plots left with their seeds, got " + machine.totalPlots() + " / " + machine.status());
        helper.succeed();
    }

    /** Whether a face's handler shows (and would give) an item. */
    private static boolean holds(@Nullable IItemHandler handler, Item item) {
        if (handler == null) {
            return false;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static int countIn(ChestBlockEntity chest, Item item) {
        int count = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(item)) {
                count += chest.getItem(i).getCount();
            }
        }
        return count;
    }

    /** Several groups of the same plant and one of another: one harvest yields exactly one wheat per wheat plot. */
    static void groupsHarvestTogether(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        for (int g = 0; g < 3; g++) {
            put(inputs, LAYOUT.seedSlot(g), Items.WHEAT_SEEDS, 10);
            put(inputs, LAYOUT.soilSlot(g), Items.FARMLAND, 10);
        }
        put(inputs, LAYOUT.seedSlot(7), Items.CARROT, 10);
        put(inputs, LAYOUT.soilSlot(7), Items.FARMLAND, 10);
        for (RelativeSide side : RelativeSide.all()) {
            while (machine.faceMode(side) != FaceMode.NONE) {
                machine.cycleFaceMode(side, true); // keep the harvest in the buffers
            }
        }
        machine.revalidate();
        fillEnergy(machine);
        machine.setProgressForTesting(1.0);
        helper.succeedWhen(() -> {
            long wheat = count(machine, Items.WHEAT);
            long carrots = count(machine, Items.CARROT);
            check(helper, wheat == 30, "30 wheat plots give 30 wheat, have " + wheat);
            check(helper, carrots >= 10 && carrots <= 40, "10 carrot plots give 10..40 carrots, have " + carrots);
            check(helper, machine.progress() < 0.5, "the bar restarts");
            check(helper, machine.totalPlots() == 40, "plots stay planted");
        });
    }

    /**
     * The menu's server side: slot layout, face buttons (click = next mode, right click = previous), synced numbers
     * (plots, capacity, waiting plots, energy, face modes, group statuses) and shift-click into the grids.
     */
    static void menuWorks(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        EntropicFarmMatrixMenu menu = new EntropicFarmMatrixMenu(1, player.getInventory(), machine);
        check(helper, menu.slots.size() == 206, "127 inputs + 24 outputs + 36 player + 9 filter + 10 crafter = 206, got "
                + menu.slots.size());

        // Shift-click from the player: seeds to the seed grid, soils to the soil grid.
        player.getInventory().setItem(9, new ItemStack(Items.WHEAT_SEEDS, 20));
        player.getInventory().setItem(10, new ItemStack(Items.DIRT, 20));
        menu.quickMoveStack(player, EntropicFarmMatrixMenu.PLAYER_START);
        menu.quickMoveStack(player, EntropicFarmMatrixMenu.PLAYER_START + 1);
        MachineInventory inputs = machine.inputs();
        check(helper, inputs.getResource(LAYOUT.seedSlot(0)).is(Items.WHEAT_SEEDS)
                        && inputs.getAmountAsInt(LAYOUT.seedSlot(0)) == 20, "shift-clicked seeds go to the seed grid");
        check(helper, inputs.getResource(LAYOUT.soilSlot(0)).is(Items.DIRT)
                        && inputs.getAmountAsInt(LAYOUT.soilSlot(0)) == 20, "shift-clicked soils go to the soil grid");

        // Face buttons: click = next, right click = previous.
        menu.clickMenuButton(player, AbstractFarmMatrixMenu.BUTTON_FACE_FIRST
                + RelativeSide.LEFT.ordinal());
        check(helper, machine.faceMode(RelativeSide.LEFT) == FaceMode.INPUT, "OUTPUT ALL -> INPUT");
        menu.clickMenuButton(player, AbstractFarmMatrixMenu.BUTTON_FACE_BACK_FIRST
                + RelativeSide.LEFT.ordinal());
        check(helper, machine.faceMode(RelativeSide.LEFT) == FaceMode.OUTPUT_ALL, "right click goes back");

        // Synced numbers (server copy; the client reads the same indices).
        machine.revalidate();
        menu.broadcastChanges();
        menu.clickMenuButton(player, AbstractFarmMatrixMenu.BUTTON_FERTILIZED); // refresh
        check(helper, menu.plotCapacity() == 60L * 64, "capacity 3840, got " + menu.plotCapacity());
        check(helper, menu.groupStatus(0) == MachineStatus.RUNNING, "wheat on dirt grows here without a hoe: "
                + menu.groupStatus(0));
        check(helper, menu.waitingPlots() == 0 && menu.plantedPlots() == 20, "20 plots, no soil waiting: "
                + menu.plantedPlots() + " / " + menu.waitingPlots());
        check(helper, menu.energyPerPlot() == 90, "FE per plot synced: " + menu.energyPerPlot());
        check(helper, menu.replantState() == FarmMatrixBlockEntity.ReplantState.ON, "replant ON by default");
        menu.clickMenuButton(player, EntropicFarmMatrixMenu.BUTTON_REPLANT);
        check(helper, !machine.isReplanting() && menu.replantState() == FarmMatrixBlockEntity.ReplantState.OFF,
                "the replant button switches it off");
        check(helper, menu.energyCapacity() == 60L * 64 * 90 * 3, "energy capacity synced: " + menu.energyCapacity());
        check(helper, menu.faceMode(RelativeSide.LEFT) == FaceMode.OUTPUT_ALL, "face mode synced");
        helper.succeed();
    }

    /**
     * Replant (owner's spec; option (b), 2026-09-29): the extra seeds of a harvest go first to the free soil of the
     * groups holding that seed, then to empty seed slots above a soil they grow on (group 3's soul sand is never used),
     * even when the filter blacklists them, and they grow from the next cycle. Each machine has a switch; with it off,
     * or with replanting off in the config (button DISABLED), the seeds go to the output.
     */
    static void replantsExtraSeeds(GameTestHelper helper) {
        var level = helper.getLevel();
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 10);
        put(inputs, LAYOUT.soilSlot(0), Items.FARMLAND, 12);
        put(inputs, LAYOUT.seedSlot(1), Items.WHEAT_SEEDS, 5);
        put(inputs, LAYOUT.soilSlot(1), Items.FARMLAND, 5);
        put(inputs, LAYOUT.soilSlot(2), Items.FARMLAND, 64);
        put(inputs, LAYOUT.soilSlot(3), Items.SOUL_SAND, 64);
        noFaces(machine);
        machine.filter().set(0, Items.WHEAT_SEEDS); // blacklist: replanting still comes first (owner)
        machine.revalidate();
        fillEnergy(machine);
        machine.setProgressForTesting(1.0);
        harvestNow(machine, level); // several batches: the first ones measure the yield

        int group0 = inputs.getAmountAsInt(LAYOUT.seedSlot(0));
        int group2 = inputs.getAmountAsInt(LAYOUT.seedSlot(2));
        check(helper, count(machine, Items.WHEAT) == 15, "15 wheat plots give 15 wheat: " + count(machine, Items.WHEAT));
        check(helper, group0 == 12, "group 0's 2 free soils are filled first: " + group0);
        check(helper, inputs.getAmountAsInt(LAYOUT.seedSlot(1)) == 5, "group 1 has no free soil");
        check(helper, group2 > 0 && inputs.getResource(LAYOUT.seedSlot(2)).is(Items.WHEAT_SEEDS),
                "the rest starts a group on the empty seed slot above farmland: " + group2);
        check(helper, inputs.getResource(LAYOUT.seedSlot(3)).isEmpty(), "wheat never goes above soul sand");
        check(helper, count(machine, Items.WHEAT_SEEDS) == 0, "no seed reaches the output (all found free soil)");
        check(helper, machine.status() != MachineStatus.INVALID_SOIL, "replanting never stops the machine");
        check(helper, machine.activePlots() == 12 + 5 + group2 && machine.pendingPlots() == 0,
                "replanted seeds grow from the next cycle: " + machine.activePlots() + " active, "
                        + machine.pendingPlots() + " pending");

        // The machine's switch off: the seeds go to the output.
        FarmMatrixBlockEntity other = placeMachineAt(helper, new BlockPos(2, 1, 0));
        MachineInventory otherInputs = other.inputs();
        put(otherInputs, LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 10);
        put(otherInputs, LAYOUT.soilSlot(0), Items.FARMLAND, 64);
        noFaces(other);
        other.revalidate();
        other.toggleReplant();
        check(helper, other.replantState() == FarmMatrixBlockEntity.ReplantState.OFF, "the switch turns it off");
        fillEnergy(other);
        other.setProgressForTesting(1.0);
        harvestNow(other, level);
        check(helper, otherInputs.getAmountAsInt(LAYOUT.seedSlot(0)) == 10, "switch off: nothing is planted");
        check(helper, count(other, Items.WHEAT_SEEDS) > 0, "switch off: extra seeds go to the output");

        // Replanting off in the config: DISABLED, and the switch cannot turn it on (config restored in the same tick:
        // tests of a batch run together).
        FarmMatrixBlockEntity third = placeMachineAt(helper, new BlockPos(4, 1, 0));
        put(third.inputs(), LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 10);
        put(third.inputs(), LAYOUT.soilSlot(0), Items.FARMLAND, 64);
        noFaces(third);
        var replant = com.virtualfarmworks.config.VfwServerConfig.machine(third.tier()).replant;
        boolean configured = replant.get();
        try {
            replant.set(false);
            third.revalidate();
            check(helper, third.replantState() == FarmMatrixBlockEntity.ReplantState.DISABLED, "config off: DISABLED");
            third.toggleReplant();
            fillEnergy(third);
            third.setProgressForTesting(1.0);
            harvestNow(third, level);
        } finally {
            replant.set(configured);
        }
        check(helper, third.inputs().getAmountAsInt(LAYOUT.seedSlot(0)) == 10, "config off: nothing is planted");
        check(helper, count(third, Items.WHEAT_SEEDS) > 0, "config off: extra seeds go to the output");
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static FarmMatrixBlockEntity placeMachineAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ENTROPIC_FARM_MATRIX.get().defaultBlockState());
        return helper.<FarmMatrixBlockEntity>getBlockEntity(pos);
    }

    /** Ticks the machine directly until its due harvest is complete (at most 40 ticks, all in this game tick). */
    private static void harvestNow(FarmMatrixBlockEntity machine, net.minecraft.server.level.ServerLevel level) {
        for (int tick = 0; tick < 40 && machine.progress() >= 0.999; tick++) {
            machine.serverTick(level);
        }
    }

    /** All faces NONE: harvests stay in the buffers. */
    private static void noFaces(FarmMatrixBlockEntity machine) {
        for (RelativeSide side : RelativeSide.all()) {
            while (machine.faceMode(side) != FaceMode.NONE) {
                machine.cycleFaceMode(side, true);
            }
        }
    }

    private static FarmMatrixBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.ENTROPIC_FARM_MATRIX.get().defaultBlockState());
        return helper.<FarmMatrixBlockEntity>getBlockEntity(MACHINE);
    }

    private static void put(MachineInventory inputs, int slot, Item item, int count) {
        inputs.set(slot, ItemResource.of(item), count);
    }

    private static void fillEnergy(FarmMatrixBlockEntity machine) {
        MachineEnergy energy = machine.energy();
        if (energy != null) {
            energy.set((int) energy.getCapacityAsLong());
        }
    }

    private static IItemHandler itemHandler(GameTestHelper helper, Direction side) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MACHINE), side);
    }

    /** Amount of an item in the visible and hidden output slots. */
    private static long count(FarmMatrixBlockEntity machine, Item item) {
        long total = 0;
        for (int i = 0; i < machine.output().size(); i++) {
            if (machine.output().getResource(i).is(item)) {
                total += machine.output().getAmountAsLong(i);
            }
        }
        for (int i = 0; i < machine.internalOutput().size(); i++) {
            if (machine.internalOutput().getResource(i).is(item)) {
                total += machine.internalOutput().getAmountAsLong(i);
            }
        }
        return total;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
