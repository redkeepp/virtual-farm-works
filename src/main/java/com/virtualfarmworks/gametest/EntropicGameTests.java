/*
 * EntropicGameTests — game tests of the Entropic Farm Matrix (owner spec 2026-09-29): 60 plot groups with their own
 * problems, waiting plots, FE consumption and MISSING FE, face modes with pipe input into the grids, and groups with
 * the same plant harvested together.
 */
package com.virtualfarmworks.gametest;

import com.virtualfarmworks.machine.FaceMode;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineEnergy;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.AbstractFarmMatrixMenu;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

final class EntropicGameTests {
    private static final BlockPos MACHINE = BlockPos.ZERO;
    private static final MachineLayout LAYOUT = MachineLayout.ENTROPIC;

    private EntropicGameTests() {
    }

    /**
     * Each group has its own problem while the others grow: wheat on farmland grows, wheat on dirt waits for a hoe (and
     * holds no plots meanwhile), a sapling grows on dirt, a soil alone is waiting. Plots, waiting plots and FE per tick
     * follow; the missing hoe fixed, that group joins as PENDING.
     */
    static void groupsHaveTheirOwnProblems(GameTestHelper helper) {
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
        check(helper, machine.groupStatus(1) == MachineStatus.MISSING_HOE, "group 1 needs a hoe: " + machine.groupStatus(1));
        check(helper, machine.groupStatus(2) == MachineStatus.RUNNING, "group 2 must grow: " + machine.groupStatus(2));
        check(helper, machine.groupStatus(3) == MachineStatus.MISSING_SEED, "group 3 is only soil");
        check(helper, machine.totalPlots() == 13, "10 wheat + 3 saplings, the blocked group holds none: "
                + machine.totalPlots());
        check(helper, machine.waitingPlots() == 29, "5 + 17 + 7 soils wait: " + machine.waitingPlots());
        check(helper, machine.status() == MachineStatus.MISSING_FE, "no energy yet: " + machine.status());
        check(helper, machine.energyPerTick() == 13L * 90, "90 FE per plot per tick: " + machine.energyPerTick());

        put(inputs, LAYOUT.hoeSlot(), Items.IRON_HOE, 1);
        machine.revalidate();
        check(helper, machine.groupStatus(1) == MachineStatus.RUNNING, "the hoe fixes group 1");
        check(helper, machine.totalPlots() == 18 && machine.pendingPlots() == 5,
                "group 1 joins for the next cycle: " + machine.totalPlots() + " plots, " + machine.pendingPlots()
                        + " pending");
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

        EnergyHandler cable = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(MACHINE),
                Direction.UP);
        check(helper, cable != null, "energy capability on every face");
        try (Transaction transaction = Transaction.openRoot()) {
            cable.insert(100_000, transaction);
            transaction.commit();
        }
        machine.serverTick(helper.getLevel());
        check(helper, machine.status() == MachineStatus.RUNNING, "with energy it runs: " + machine.status());
        check(helper, energy.getAmountAsLong() == 100_000 - 20 * 90, "one tick costs 20 x 90 FE, left "
                + energy.getAmountAsLong());
        check(helper, machine.progress() > 0.0, "the bar moves");
        helper.succeed();
    }

    /**
     * Face modes: OUTPUT ALL by default; INPUT takes plantables into the seed grid and soils into the soil grid, pairing
     * them; NONE shows nothing; OUTPUT shows an extract-only output.
     */
    static void facesAndPipeInput(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        check(helper, machine.faceMode(RelativeSide.TOP) == FaceMode.OUTPUT_ALL, "default mode is OUTPUT ALL");
        machine.cycleFaceMode(RelativeSide.TOP, true); // OUTPUT ALL -> INPUT
        check(helper, machine.faceMode(RelativeSide.TOP) == FaceMode.INPUT, "next mode is INPUT");

        ResourceHandler<ItemResource> input = itemHandler(helper, Direction.UP);
        check(helper, input != null, "INPUT face has a capability");
        try (Transaction transaction = Transaction.openRoot()) {
            check(helper, input.insert(ItemResource.of(Items.WHEAT_SEEDS), 70, transaction) == 70, "70 seeds go in");
            check(helper, input.insert(ItemResource.of(Items.FARMLAND), 70, transaction) == 70, "70 soils go in");
            check(helper, input.insert(ItemResource.of(Items.DIAMOND), 1, transaction) == 0, "a diamond is refused");
            transaction.commit();
        }
        MachineInventory inputs = machine.inputs();
        check(helper, inputs.getAmountAsInt(LAYOUT.seedSlot(0)) == 64 && inputs.getAmountAsInt(LAYOUT.seedSlot(1)) == 6,
                "seeds fill slot 0 then slot 1");
        check(helper, inputs.getAmountAsInt(LAYOUT.soilSlot(0)) == 64 && inputs.getAmountAsInt(LAYOUT.soilSlot(1)) == 6,
                "soils go under their seeds");

        // NONE: nothing on that face. Top: INPUT -> NONE.
        machine.cycleFaceMode(RelativeSide.TOP, true);
        check(helper, machine.faceMode(RelativeSide.TOP) == FaceMode.NONE, "INPUT -> NONE");
        check(helper, itemHandler(helper, Direction.UP) == null, "a NONE face shows nothing");

        // OUTPUT (NONE -> OUTPUT): extract-only.
        machine.cycleFaceMode(RelativeSide.TOP, true);
        ResourceHandler<ItemResource> out = itemHandler(helper, Direction.UP);
        check(helper, out != null, "OUTPUT face has a capability");
        try (Transaction transaction = Transaction.openRoot()) {
            check(helper, out.insert(ItemResource.of(Items.WHEAT_SEEDS), 1, transaction) == 0, "outputs refuse input");
        }
        helper.succeed();
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
        check(helper, menu.slots.size() == 270, "127 inputs + 24 outputs + 36 player + 9 filter + 74 crafter = 270, got "
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
        check(helper, menu.groupStatus(0) == MachineStatus.MISSING_HOE, "wheat on dirt needs a hoe: "
                + menu.groupStatus(0));
        check(helper, menu.waitingPlots() == 20, "20 dirt wait for the hoe: " + menu.waitingPlots());
        check(helper, menu.energyCapacity() == 60L * 64 * 90 * 3, "energy capacity synced: " + menu.energyCapacity());
        check(helper, menu.faceMode(RelativeSide.LEFT) == FaceMode.OUTPUT_ALL, "face mode synced");
        helper.succeed();
    }

    /**
     * Replant (owner's spec): the extra seeds of a harvest are planted into the free soil of the groups that hold that
     * seed, even when the filter blacklists them, and they grow from the next cycle; a group without free soil takes
     * none. With replant off in the config the seeds go to the output.
     */
    static void replantsExtraSeeds(GameTestHelper helper) {
        var level = helper.getLevel();
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 10);
        put(inputs, LAYOUT.soilSlot(0), Items.FARMLAND, 64);
        put(inputs, LAYOUT.seedSlot(1), Items.WHEAT_SEEDS, 5);
        put(inputs, LAYOUT.soilSlot(1), Items.FARMLAND, 5);
        noFaces(machine);
        machine.filter().set(0, Items.WHEAT_SEEDS); // blacklist: replanting still comes first (owner)
        machine.revalidate();
        fillEnergy(machine);
        machine.setProgressForTesting(1.0);
        harvestNow(machine, level); // several batches: the first ones measure the yield

        int group0 = inputs.getAmountAsInt(LAYOUT.seedSlot(0));
        check(helper, count(machine, Items.WHEAT) == 15, "15 wheat plots give 15 wheat: " + count(machine, Items.WHEAT));
        check(helper, group0 > 10, "extra seeds are replanted into group 0's free soil: " + group0);
        check(helper, inputs.getAmountAsInt(LAYOUT.seedSlot(1)) == 5, "group 1 has no free soil");
        check(helper, count(machine, Items.WHEAT_SEEDS) == 0, "no seed reaches the output (all found free soil)");
        check(helper, machine.activePlots() == group0 + 5 && machine.pendingPlots() == 0,
                "replanted seeds grow from the next cycle: " + machine.activePlots() + " active, "
                        + machine.pendingPlots() + " pending");

        // Replant off (config restored in the same tick: tests of a batch run together).
        FarmMatrixBlockEntity other = placeMachineAt(helper, new BlockPos(2, 0, 0));
        MachineInventory otherInputs = other.inputs();
        put(otherInputs, LAYOUT.seedSlot(0), Items.WHEAT_SEEDS, 10);
        put(otherInputs, LAYOUT.soilSlot(0), Items.FARMLAND, 64);
        noFaces(other);
        var replant = com.virtualfarmworks.config.VfwServerConfig.machine(other.tier()).replant;
        boolean configured = replant.get();
        try {
            replant.set(false);
            other.revalidate();
            fillEnergy(other);
            other.setProgressForTesting(1.0);
            harvestNow(other, level);
        } finally {
            replant.set(configured);
        }
        check(helper, otherInputs.getAmountAsInt(LAYOUT.seedSlot(0)) == 10, "replant off: nothing is planted");
        check(helper, count(other, Items.WHEAT_SEEDS) > 0, "replant off: extra seeds go to the output");
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static FarmMatrixBlockEntity placeMachineAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ENTROPIC_FARM_MATRIX.get().defaultBlockState());
        return helper.getBlockEntity(pos, FarmMatrixBlockEntity.class);
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
        return helper.getBlockEntity(MACHINE, FarmMatrixBlockEntity.class);
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

    private static ResourceHandler<ItemResource> itemHandler(GameTestHelper helper, Direction side) {
        return helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE), side);
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
        helper.assertTrue(condition, Component.literal(message));
    }
}
