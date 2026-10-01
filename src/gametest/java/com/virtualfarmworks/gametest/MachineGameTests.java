/*
 * MachineGameTests — game tests of the placed Farm Matrix: a full growth cycle into the buffer, status reporting,
 * OUTPUT FULL holding the harvest, batched harvests bigger than the output, the hidden output slots, save/load,
 * auto-export per face, what drops on break, slot rules, the server side of the GUI menu (buttons, shift-click,
 * output slots, synced data), every crafting recipe of the mod crafted from the owner's grid, and that only tiers
 * whose machine exists register items.
 */
package com.virtualfarmworks.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.virtualfarmworks.compat.mysticalagriculture.MysticalCompat;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.FarmMatrixMenu;
import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Every test places a real Starter Farm Matrix (front facing north) at the test origin and drives it through its
 * block entity. Slots are filled with {@code set} (as the menu will do); gameplay then runs through the real ticker.
 */
final class MachineGameTests {
    private static final BlockPos MACHINE = BlockPos.ZERO;

    private MachineGameTests() {
    }

    // --- tests ------------------------------------------------------------------------------------------------------

    /**
     * Wheat x10 on farmland x10 with a Water Provider and 4 Growth Speed Upgrades (3.0x): one cycle is 200 ticks, then
     * exactly 10 wheat land in the buffer and the bar restarts.
     */
    static void runsAFullCycle(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 10);
        put(inputs, MachineSlots.SOIL, Items.FARMLAND, 10);
        put(inputs, MachineSlots.WATER_PROVIDER, ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get(), 1);
        for (int i = 0; i < MachineSlots.GROWTH_COUNT; i++) {
            put(inputs, MachineSlots.GROWTH_FIRST + i, ModItems.GROWTH_SPEED_UPGRADES.get(MachineTier.STARTER).get(), 1);
        }
        machine.revalidate();
        check(helper, machine.status() == MachineStatus.RUNNING, "expected RUNNING, got " + machine.status());
        check(helper, machine.totalPlots() == 10, "expected 10 plots, got " + machine.totalPlots());
        check(helper, Math.abs(machine.speed().total() - 3.0) < 1e-9, "expected 3.0x, got " + machine.speed().total());

        helper.succeedWhen(() -> {
            check(helper, count(machine, Items.WHEAT) == 10, "waiting for 10 wheat, have " + count(machine, Items.WHEAT));
            check(helper, machine.progress() < 0.5, "bar must restart after the harvest");
            check(helper, machine.activePlots() == 10, "plots must stay after the harvest");
        });
    }

    /** The GUI state follows the owner's priority order. */
    static void reportsStates(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        expectStatus(helper, machine, MachineStatus.MISSING_SEED);

        put(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 5);
        expectStatus(helper, machine, MachineStatus.MISSING_SOIL);

        put(inputs, MachineSlots.SOIL, Items.SOUL_SAND, 5);
        expectStatus(helper, machine, MachineStatus.INVALID_SOIL);

        put(inputs, MachineSlots.SOIL, Items.DIRT, 5);
        expectStatus(helper, machine, MachineStatus.MISSING_HOE);

        put(inputs, MachineSlots.HOE, Items.WOODEN_HOE, 1);
        expectStatus(helper, machine, MachineStatus.RUNNING);

        machine.setEnabled(false);
        expectStatus(helper, machine, MachineStatus.SHUTDOWN);
        machine.setEnabled(true);

        if (MysticalCompat.isLoaded()) {
            Item cruxSeed = firstCruxSeed();
            if (cruxSeed != null) {
                put(inputs, MachineSlots.SEED, cruxSeed, 5);
                put(inputs, MachineSlots.SOIL, Items.FARMLAND, 5);
                expectStatus(helper, machine, MachineStatus.MISSING_CRUX);
                put(inputs, MachineSlots.CRUX_PROVIDER, ModItems.CRUX_PROVIDER_UPGRADE.get(), 1);
                expectStatus(helper, machine, MachineStatus.RUNNING);
            }
        }
        helper.succeed();
    }

    /**
     * Visible AND hidden output slots full: the due harvest waits at 100% with OUTPUT FULL, all ripe plots stay on the
     * plant and nothing is stored. Once the hidden slots have room, the harvest goes there (the visible slots are still
     * full), in one or more batches, and the cycle completes with exactly one wheat per plot.
     */
    static void outputFullHoldsTheHarvest(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 10);
        put(inputs, MachineSlots.SOIL, Items.FARMLAND, 10);
        put(inputs, MachineSlots.WATER_PROVIDER, ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get(), 1);
        disableAllOutputs(machine); // keep the dirt in the buffers
        machine.revalidate();       // also sizes the hidden slots from the config (27 on the Starter)
        fill(machine.output(), Items.DIRT);
        fill(machine.internalOutput(), Items.DIRT);
        machine.setProgressForTesting(1.0);

        helper.runAfterDelay(5, () -> {
            check(helper, machine.status() == MachineStatus.OUTPUT_FULL, "expected OUTPUT_FULL, got " + machine.status());
            check(helper, machine.progress() >= 0.999, "the bar must stay at 100% while blocked");
            check(helper, machine.plotsToHarvest() == 10, "every ripe plot must wait, got " + machine.plotsToHarvest());
            check(helper, wheatIn(machine) == 0, "a blocked harvest must not store anything");
            // Empty the hidden slots. In play only the machine changes them (and asks itself for a retry when it
            // does), so the test asks for the retry through revalidate().
            clear(machine.internalOutput());
            machine.revalidate();
        });
        helper.succeedWhen(() -> {
            check(helper, countIn(machine.internalOutput(), Items.WHEAT) == 10,
                    "the harvest must go to the hidden slots, have " + countIn(machine.internalOutput(), Items.WHEAT));
            check(helper, count(machine, Items.WHEAT) == 0, "the visible slots were full of dirt");
            check(helper, machine.progress() < 0.5, "cycle must complete after storing");
            check(helper, machine.status() == MachineStatus.RUNNING, "expected RUNNING again");
        });
    }

    /**
     * The owner's bug (step 8): a harvest bigger than every output slot (64 plots x 50 wheat = 3,200 wheat = 50 slots,
     * 36 exist) used to deadlock at OUTPUT FULL with an EMPTY buffer. Now batches fill the visible then the hidden
     * slots, the other ripe plots wait on the plant, a save/load in the middle keeps that state, and draining the
     * visible slots like a pipe lets the harvest finish with exactly 3,200 wheat.
     *
     * <p>Runs synchronously ({@code serverTick} called directly), so the raised multiplier is restored before any other
     * test runs; config changes that last across ticks would leak into tests running at the same time.
     */
    static void bigHarvestDoesNotDeadlock(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 64);
        put(inputs, MachineSlots.SOIL, Items.FARMLAND, 64);
        put(inputs, MachineSlots.WATER_PROVIDER, ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get(), 1);
        disableAllOutputs(machine);
        machine.revalidate();
        machine.setProgressForTesting(1.0);
        ServerLevel level = helper.getLevel();

        var multiplier = VfwServerConfig.GLOBAL_PRODUCTION_MULTIPLIER;
        double original = multiplier.get();
        multiplier.set(50.0); // in memory only: nothing written to disk, no reload event
        try {
            for (int tick = 0; tick < 40; tick++) {
                machine.serverTick(level);
            }
            int waiting = machine.plotsToHarvest();
            long stored = wheatIn(machine);
            check(helper, machine.status() == MachineStatus.OUTPUT_FULL, "expected OUTPUT_FULL, got " + machine.status());
            check(helper, waiting > 0, "with 36 slots for 50 slots of wheat, some ripe plots must wait");
            check(helper, emptySlots(machine.output()) == 0, "the visible slots fill first");
            check(helper, stored == 50L * (64 - waiting), "exactly 50 wheat per harvested plot: " + stored
                    + " wheat for " + (64 - waiting) + " plots");
            check(helper, machine.heldDrops().isEmpty(), "no plot is bigger than the output here");

            FarmMatrixBlockEntity loaded = roundTrip(helper, machine);
            check(helper, loaded.plotsToHarvest() == waiting, "ripe plots lost or duplicated by a save: "
                    + loaded.plotsToHarvest() + " vs " + waiting);
            check(helper, wheatIn(loaded) == stored, "hidden or visible slots lost by a save");

            long drained = 0;
            for (int tick = 0; tick < 2_000 && machine.progress() >= 0.999; tick++) {
                drained += drainVisible(machine, Items.WHEAT);
                machine.serverTick(level);
            }
            check(helper, machine.progress() < 0.5, "the harvest must finish once the output drains");
            check(helper, drained + wheatIn(machine) == 3_200, "expected 3,200 wheat, got " + (drained + wheatIn(machine)));
        } finally {
            multiplier.set(original);
        }
        helper.succeed();
    }

    /**
     * Owner-approved extreme case: ONE plot yielding more than every output slot (1,000 x 3 = 3,000 wheat = 47 slots,
     * 36 exist). The machine stores what fits, holds the rest (saved with the machine), keeps the bar at 100% until the
     * held wheat is stored, and ends with exactly 3,000 wheat. Synchronous, like the test above.
     */
    static void extremeHarvestHoldsTheRest(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 1);
        put(inputs, MachineSlots.SOIL, Items.FARMLAND, 1);
        put(inputs, MachineSlots.WATER_PROVIDER, ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get(), 1);
        disableAllOutputs(machine);
        machine.revalidate();
        machine.setProgressForTesting(1.0);
        ServerLevel level = helper.getLevel();

        var global = VfwServerConfig.GLOBAL_PRODUCTION_MULTIPLIER;
        var perTier = VfwServerConfig.machine(MachineTier.STARTER).productionMultiplier;
        double originalGlobal = global.get();
        double originalTier = perTier.get();
        global.set(1000.0);
        perTier.set(3.0);
        try {
            machine.serverTick(level); // the only plot: too big even for empty buffers
            long held = heldAmount(machine, Items.WHEAT);
            check(helper, held > 0, "the part that does not fit must be held");
            check(helper, machine.plotsToHarvest() == 0, "the plot counts as harvested");
            check(helper, machine.progress() >= 0.999, "the cycle must wait for the held items");
            check(helper, machine.status() == MachineStatus.OUTPUT_FULL, "expected OUTPUT_FULL, got " + machine.status());
            check(helper, emptySlots(machine.output()) == 0 && emptySlots(machine.internalOutput()) == 0,
                    "every visible and hidden slot is filled first");
            check(helper, held + wheatIn(machine) == 3_000, "nothing lost: " + (held + wheatIn(machine)));

            FarmMatrixBlockEntity loaded = roundTrip(helper, machine);
            check(helper, heldAmount(loaded, Items.WHEAT) == held, "held items must be saved with the machine");

            long drained = 0;
            for (int tick = 0; tick < 2_000 && machine.progress() >= 0.999; tick++) {
                drained += drainVisible(machine, Items.WHEAT);
                machine.serverTick(level);
            }
            check(helper, machine.heldDrops().isEmpty(), "held items must be stored once there is room");
            check(helper, machine.progress() < 0.5, "then the cycle completes");
            check(helper, drained + wheatIn(machine) == 3_000, "expected 3,000 wheat, got " + (drained + wheatIn(machine)));
        } finally {
            global.set(originalGlobal);
            perTier.set(originalTier);
        }
        helper.succeed();
    }

    /**
     * The hidden slots unload into the visible ones as those empty (owner design), nothing is lost on the way, and the
     * outside world (capability) only ever sees the 9 visible slots.
     */
    static void hiddenSlotsRefillTheVisibleOnes(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        disableAllOutputs(machine);
        machine.revalidate();
        check(helper, machine.internalOutput().usableSlots() == 27, "Starter default: 27 hidden slots, got "
                + machine.internalOutput().usableSlots());
        fill(machine.output(), Items.DIRT);
        for (int i = 0; i < 5; i++) {
            machine.internalOutput().set(i, ItemResource.of(Items.WHEAT), 64);
        }

        // A pipe takes two stacks of dirt; on the next tick two stacks of wheat move up.
        var external = machine.externalOutput();
        try (Transaction tx = Transaction.openRoot()) {
            external.extract(0, ItemResource.of(Items.DIRT), 64, tx);
            external.extract(1, ItemResource.of(Items.DIRT), 64, tx);
            tx.commit();
        }
        machine.serverTick(helper.getLevel());

        check(helper, count(machine, Items.WHEAT) == 128, "two stacks must move up, have " + count(machine, Items.WHEAT));
        check(helper, countIn(machine.internalOutput(), Items.WHEAT) == 192, "three stacks must stay hidden");
        check(helper, external.size() == MachineSlots.OUTPUT_COUNT, "the capability must only show the 9 visible slots");
        try (Transaction tx = Transaction.openRoot()) {
            check(helper, external.insert(ItemResource.of(Items.WHEAT), 1, tx) == 0, "nothing may be inserted");
        }
        helper.succeed();
    }

    /** Progress, ACTIVE/PENDING counters, inventories, on/off and output faces survive a save/load round trip. */
    static void savesAndLoads(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 40);
        put(inputs, MachineSlots.SOIL, Items.FARMLAND, 64);
        machine.revalidate();
        machine.setProgressForTesting(0.5);
        put(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 64); // 24 new plots mid-cycle -> PENDING
        machine.revalidate();
        machine.output().set(3, ItemResource.of(Items.WHEAT), 12);
        machine.internalOutput().set(20, ItemResource.of(Items.CARROT), 33);
        machine.toggleOutput(RelativeSide.LEFT);
        machine.setEnabled(false);

        FarmMatrixBlockEntity loaded = roundTrip(helper, machine);
        loaded.revalidate();

        check(helper, Math.abs(loaded.progress() - 0.5) < 1e-9, "progress lost: " + loaded.progress());
        check(helper, loaded.activePlots() == 40, "active plots lost: " + loaded.activePlots());
        check(helper, loaded.pendingPlots() == 24, "pending plots lost: " + loaded.pendingPlots());
        check(helper, loaded.inputs().getAmountAsInt(MachineSlots.SEED) == 64, "seed slot lost");
        check(helper, loaded.output().getAmountAsInt(3) == 12, "output buffer lost");
        check(helper, loaded.internalOutput().getAmountAsInt(20) == 33
                && loaded.internalOutput().getResource(20).is(Items.CARROT), "hidden output slots lost");
        check(helper, !loaded.isEnabled(), "on/off switch lost");
        check(helper, !loaded.isOutputEnabled(RelativeSide.LEFT) && loaded.isOutputEnabled(RelativeSide.RIGHT),
                "output faces lost");
        helper.succeed();
    }

    /**
     * Auto-export pushes the buffer into a chest on the machine's LEFT (east of a north-facing machine), but only while
     * that face is enabled.
     */
    static void autoExportsToEnabledFaces(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        BlockPos chestPos = MACHINE.east(); // left side, seen from the front of a north-facing machine
        helper.setBlock(chestPos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(chestPos, ChestBlockEntity.class);

        machine.toggleOutput(RelativeSide.LEFT); // disabled first
        machine.output().set(0, ItemResource.of(Items.WHEAT), 5);

        helper.runAfterDelay(30, () -> { // > one export interval (20 ticks by default)
            check(helper, chest.isEmpty(), "a disabled face must not export");
            machine.toggleOutput(RelativeSide.LEFT);
        });
        helper.succeedWhen(() -> {
            check(helper, chest.countItem(Items.WHEAT) == 5, "chest should receive 5 wheat, has "
                    + chest.countItem(Items.WHEAT));
            check(helper, machine.output().isEmpty(), "buffer should be empty after export");
        });
    }

    /**
     * Breaking the machine drops the inputs and the 9 visible output slots; the hidden output slots are deleted (owner
     * rule, step 8).
     */
    static void dropsItsContents(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        machine.revalidate(); // sizes the hidden slots
        put(machine.inputs(), MachineSlots.SEED, Items.WHEAT_SEEDS, 7);
        put(machine.inputs(), MachineSlots.SOIL, Items.FARMLAND, 3);
        machine.output().set(0, ItemResource.of(Items.WHEAT), 5);
        machine.internalOutput().set(0, ItemResource.of(Items.DIAMOND), 5);

        helper.destroyBlock(MACHINE);

        helper.assertItemEntityPresent(Items.WHEAT_SEEDS, MACHINE, 2.0);
        helper.assertItemEntityPresent(Items.FARMLAND, MACHINE, 2.0);
        helper.assertItemEntityPresent(Items.WHEAT, MACHINE, 2.0);
        helper.assertItemEntityNotPresent(Items.DIAMOND, MACHINE, 2.0);
        helper.succeed();
    }

    /** What each slot accepts and how many; the outside world can extract outputs but never insert anything. */
    static void slotRules(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        Item starterWater = ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get();
        Item entropicGrowth = ModItems.GROWTH_SPEED_UPGRADES.get(MachineTier.ENTROPIC).get();

        try (Transaction tx = Transaction.openRoot()) { // never committed: only the answers matter
            check(helper, insert(inputs, MachineSlots.SEED, Items.STONE, 1, tx) == 0, "stone is not a seed");
            check(helper, insert(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 64, tx) == 64, "64 seeds fit");
            check(helper, insert(inputs, MachineSlots.SOIL, Items.WHEAT_SEEDS, 1, tx) == 0, "seeds are not a soil");
            check(helper, insert(inputs, MachineSlots.SOIL, Items.DIRT, 64, tx) == 64, "64 dirt fit");
            check(helper, insert(inputs, MachineSlots.WATER_PROVIDER, starterWater, 2, tx) == 1, "one water provider");
            check(helper, insert(inputs, MachineSlots.HOE, Items.STICK, 1, tx) == 0, "a stick is not a hoe");
            check(helper, insert(inputs, MachineSlots.HOE, Items.WOODEN_HOE, 1, tx) == 1, "any hoe fits");
            check(helper, insert(inputs, MachineSlots.GROWTH_FIRST, starterWater, 1, tx) == 0,
                    "a water provider is not a growth upgrade");
            check(helper, insert(inputs, MachineSlots.GROWTH_FIRST, entropicGrowth, 3, tx) == 1,
                    "one growth upgrade per slot (default config); Entropic fits the Starter");
            check(helper, insert(inputs, MachineSlots.CRUX_PROVIDER, entropicGrowth, 1, tx) == 0,
                    "only the crux provider fits the crux slot");
            check(helper, insert(inputs, MachineSlots.CRUX_PROVIDER, ModItems.CRUX_PROVIDER_UPGRADE.get(), 1, tx) == 1,
                    "the crux provider fits");

            machine.output().set(0, ItemResource.of(Items.WHEAT), 5);
            var external = machine.externalOutput();
            check(helper, external.insert(ItemResource.of(Items.WHEAT), 1, tx) == 0, "the buffer refuses insertion");
            check(helper, external.extract(ItemResource.of(Items.WHEAT), 5, tx) == 5, "the buffer allows extraction");
        }
        helper.succeed();
    }

    /**
     * Server side of the GUI: button intents change the machine, shift-click puts items in the right input slot, the
     * output slots refuse items, and the synced numbers reflect the machine.
     */
    static void menuActions(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        FarmMatrixMenu menu = new FarmMatrixMenu(1, player.getInventory(), machine);

        // Buttons (the client sends only these ids; the server applies them).
        menu.clickMenuButton(player, FarmMatrixMenu.BUTTON_POWER);
        check(helper, !machine.isEnabled() && !menu.isEnabled(), "power button must switch the machine off");
        menu.clickMenuButton(player, FarmMatrixMenu.BUTTON_POWER);
        check(helper, machine.isEnabled(), "power button must switch the machine back on");
        menu.clickMenuButton(player, FarmMatrixMenu.BUTTON_FACE_FIRST + RelativeSide.LEFT.ordinal());
        check(helper, !machine.isOutputEnabled(RelativeSide.LEFT) && !menu.isOutputEnabled(RelativeSide.LEFT),
                "face button must disable the left face");
        check(helper, !menu.clickMenuButton(player, 99), "unknown button ids must be ignored");
        check(helper, machine.isFertilizedEssenceEnabled(), "Fertilized Essence must be ON by default");
        menu.clickMenuButton(player, FarmMatrixMenu.BUTTON_FERTILIZED);
        check(helper, !machine.isFertilizedEssenceEnabled() && !menu.isFertilizedEssenceEnabled(),
                "Fertilized Essence button must switch it off");

        // Shift-click from the player's inventory (menu PLAYER_START = player inventory index 9).
        player.getInventory().setItem(9, new ItemStack(Items.WHEAT_SEEDS, 32));
        player.getInventory().setItem(10, new ItemStack(ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get()));
        // A diamond fits no machine slot (note: stone would be accepted as a soil, glow berries hang from stone).
        player.getInventory().setItem(11, new ItemStack(Items.DIAMOND, 5));
        menu.quickMoveStack(player, FarmMatrixMenu.PLAYER_START);
        menu.quickMoveStack(player, FarmMatrixMenu.PLAYER_START + 1);
        menu.quickMoveStack(player, FarmMatrixMenu.PLAYER_START + 2);
        check(helper, machine.inputs().getAmountAsInt(MachineSlots.SEED) == 32, "seeds must go to the seed slot");
        check(helper, machine.inputs().getAmountAsInt(MachineSlots.WATER_PROVIDER) == 1, "water provider must go to its slot");
        check(helper, player.getInventory().countItem(Items.DIAMOND) == 5, "a diamond must stay with the player");
        for (int i = 0; i < MachineSlots.OUTPUT_COUNT; i++) {
            check(helper, machine.output().isEmpty(), "shift-click must never fill the output buffer");
            check(helper, menu.slots.get(FarmMatrixMenu.OUTPUT_START + i).mayPlace(new ItemStack(Items.WHEAT)),
                    "players may put items in the output by hand (owner revision, step 8)");
        }

        // Taking from the output buffer works through the menu.
        machine.output().set(0, ItemResource.of(Items.WHEAT), 7);
        menu.quickMoveStack(player, FarmMatrixMenu.OUTPUT_START);
        check(helper, machine.output().isEmpty() && player.getInventory().countItem(Items.WHEAT) == 7,
                "shift-click must move output items to the player");

        // Synced numbers (server copy) follow the machine after a sync.
        machine.revalidate();
        menu.broadcastChanges();
        for (int i = 0; i < 5; i++) {
            menu.broadcastChanges(); // the copy refreshes every few ticks
        }
        check(helper, menu.status() == machine.status(), "menu status must mirror the machine");
        helper.succeed();
    }

    /**
     * Right-click with an upgrade in hand (owner spec): the machine pulls in as many as fit, the hand loses exactly
     * that many; when nothing fits, or the item is not an upgrade, the click falls through (the GUI opens instead).
     */
    static void insertsUpgradesFromHand(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = helper.getLevel().getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);

        ItemStack growth = new ItemStack(ModItems.GROWTH_SPEED_UPGRADES.get(MachineTier.STARTER).get(), 6);
        InteractionResult first = state.useItemOn(growth, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(helper, first.consumesAction(), "a growth upgrade in hand must be pulled in");
        check(helper, growth.getCount() == 2, "4 slots x 1 upgrade: 4 pulled, 2 left in hand, got " + growth.getCount());
        check(helper, machine.inputs().getAmountAsInt(MachineSlots.GROWTH_FIRST + 3) == 1, "last growth slot filled");

        InteractionResult full = state.useItemOn(growth, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(helper, full instanceof InteractionResult.TryEmptyHandInteraction && growth.getCount() == 2,
                "when nothing fits, the click must fall through to the GUI and keep the items");

        ItemStack water = new ItemStack(ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.ENTROPIC).get(), 3);
        state.useItemOn(water, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(helper, water.getCount() == 2 && machine.inputs().getAmountAsInt(MachineSlots.WATER_PROVIDER) == 1,
                "exactly one water provider must be pulled in");

        ItemStack crux = new ItemStack(ModItems.CRUX_PROVIDER_UPGRADE.get());
        state.useItemOn(crux, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(helper, crux.isEmpty() && machine.inputs().getAmountAsInt(MachineSlots.CRUX_PROVIDER) == 1,
                "the crux provider must be pulled in");

        ItemStack seeds = new ItemStack(Items.WHEAT_SEEDS, 10);
        InteractionResult other = state.useItemOn(seeds, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(helper, other instanceof InteractionResult.TryEmptyHandInteraction && seeds.getCount() == 10
                        && machine.inputs().getAmountAsInt(MachineSlots.SEED) == 0,
                "non-upgrade items must not be pulled in (the GUI opens instead)");
        helper.succeed();
    }

    /**
     * Every crafting recipe the owner specified is loaded (a JSON error would silently drop it) and crafts its item from
     * the owner's grid, written here as the owner gave it: cells 1-9 left to right, top to bottom.
     */
    static void recipesAreLoaded(GameTestHelper helper) {
        Item seeds = Items.WHEAT_SEEDS;
        expectCraft(helper, "starter_farm_matrix", ModItems.STARTER_FARM_MATRIX.get(),
                Items.DIAMOND, seeds, Items.REDSTONE, seeds, Items.IRON_BLOCK, seeds, Items.REDSTONE, seeds, Items.DIAMOND);
        expectCraft(helper, "starter_water_provider_upgrade", upgrade(ModItems.WATER_PROVIDER_UPGRADES, MachineTier.STARTER),
                ring(Items.DIAMOND, Items.IRON_INGOT, Items.WATER_BUCKET));
        expectCraft(helper, "starter_growth_upgrade", upgrade(ModItems.GROWTH_SPEED_UPGRADES, MachineTier.STARTER),
                ring(Items.REDSTONE, Items.DIAMOND, Items.REDSTONE_BLOCK));
        expectCraft(helper, "crux_provider_upgrade", ModItems.CRUX_PROVIDER_UPGRADE.get(),
                ring(Items.NETHERITE_BLOCK, Items.NETHERITE_BLOCK, Items.NETHER_STAR));

        Item starter = ModItems.STARTER_FARM_MATRIX.get();
        expectCraft(helper, "entropic_farm_matrix", ModItems.ENTROPIC_FARM_MATRIX.get(),
                Items.NETHERITE_INGOT, Items.REDSTONE, Items.DIAMOND, Items.REDSTONE, starter, Items.REDSTONE,
                Items.DIAMOND, Items.REDSTONE, Items.NETHERITE_INGOT);
        expectCraft(helper, "entropic_water_provider_upgrade", upgrade(ModItems.WATER_PROVIDER_UPGRADES, MachineTier.ENTROPIC),
                ring(Items.DIAMOND, Items.IRON_INGOT, upgrade(ModItems.WATER_PROVIDER_UPGRADES, MachineTier.STARTER)));
        expectCraft(helper, "entropic_growth_upgrade", upgrade(ModItems.GROWTH_SPEED_UPGRADES, MachineTier.ENTROPIC),
                ring(Items.REDSTONE_BLOCK, Items.DIAMOND, upgrade(ModItems.GROWTH_SPEED_UPGRADES, MachineTier.STARTER)));
        helper.succeed();
    }

    /**
     * Owner (2026-09-30, before publishing): tiers whose machine does not exist yet register no items, and an upgrade's
     * "Fits:" line names only machines that exist.
     */
    static void onlyBuiltTiersHaveItems(GameTestHelper helper) {
        for (MachineTier tier : MachineTier.values()) {
            for (String suffix : new String[] {"water_provider_upgrade", "growth_upgrade"}) {
                Identifier id = Identifier.fromNamespaceAndPath("virtualfarmworks", tier.getSerializedName() + "_" + suffix);
                check(helper, BuiltInRegistries.ITEM.containsKey(id) == tier.isBuilt(),
                        id + (tier.isBuilt() ? " must exist" : " must not exist before its machine"));
            }
        }
        ItemStack upgrade = new ItemStack(ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.ENTROPIC).get());
        List<String> fits = new ArrayList<>();
        for (Component line : upgrade.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null,
                TooltipFlag.NORMAL)) {
            if (line.getContents() instanceof TranslatableContents text
                    && text.getKey().equals("tooltip.virtualfarmworks.fits") && text.getArgs().length == 1
                    && text.getArgs()[0] instanceof Component tiers) {
                for (Component part : tiers.getSiblings()) {
                    if (part.getContents() instanceof TranslatableContents name) {
                        fits.add(name.getKey());
                    }
                }
            }
        }
        check(helper, fits.equals(List.of("tier.virtualfarmworks.starter", "tier.virtualfarmworks.entropic")),
                "an Entropic upgrade fits the machines that exist: " + fits);
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static FarmMatrixBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.STARTER_FARM_MATRIX.get().defaultBlockState());
        return helper.getBlockEntity(MACHINE, FarmMatrixBlockEntity.class);
    }

    /** Crafts {@code cells} (grid cells 1-9) in a crafting table: recipe {@code virtualfarmworks:<name>}, one {@code result}. */
    private static void expectCraft(GameTestHelper helper, String name, Item result, Item... cells) {
        ServerLevel level = helper.getLevel();
        List<ItemStack> stacks = new ArrayList<>(cells.length);
        for (Item item : cells) {
            stacks.add(new ItemStack(item));
        }
        CraftingInput input = CraftingInput.of(3, 3, stacks);
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level);
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath("virtualfarmworks", name));
        check(helper, recipe.isPresent() && recipe.get().id().equals(key), "the grid does not make recipe " + name);
        ItemStack crafted = recipe.get().value().assemble(input);
        check(helper, crafted.is(result) && crafted.getCount() == 1, name + " crafts " + crafted);
    }

    /** Cells 1-9 of a grid with the same item in the four corners, another on the four edges and one in the middle. */
    private static Item[] ring(Item corners, Item edges, Item middle) {
        return new Item[] {corners, edges, corners, edges, middle, edges, corners, edges, corners};
    }

    private static Item upgrade(Map<MachineTier, ? extends Supplier<? extends Item>> upgrades, MachineTier tier) {
        return upgrades.get(tier).get();
    }

    private static void put(MachineInventory inputs, int slot, Item item, int count) {
        inputs.set(slot, ItemResource.of(item), count);
    }

    private static int insert(MachineInventory inputs, int slot, Item item, int count, Transaction tx) {
        return inputs.insert(slot, ItemResource.of(item), count, tx);
    }

    private static void expectStatus(GameTestHelper helper, FarmMatrixBlockEntity machine, MachineStatus expected) {
        machine.revalidate();
        check(helper, machine.status() == expected, "expected " + expected + ", got " + machine.status());
    }

    private static void disableAllOutputs(FarmMatrixBlockEntity machine) {
        for (RelativeSide side : RelativeSide.all()) {
            if (machine.isOutputEnabled(side)) {
                machine.toggleOutput(side);
            }
        }
    }

    /** Amount of an item in the VISIBLE output slots. */
    private static int count(FarmMatrixBlockEntity machine, Item item) {
        return (int) countIn(machine.output(), item);
    }

    private static long countIn(ItemStacksResourceHandler handler, Item item) {
        long total = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).is(item)) {
                total += handler.getAmountAsLong(i);
            }
        }
        return total;
    }

    /** Wheat in the visible and hidden output slots. */
    private static long wheatIn(FarmMatrixBlockEntity machine) {
        return countIn(machine.output(), Items.WHEAT) + countIn(machine.internalOutput(), Items.WHEAT);
    }

    private static long heldAmount(FarmMatrixBlockEntity machine, Item item) {
        long total = 0;
        for (DropTally.Entry<ItemResource> drop : machine.heldDrops()) {
            if (drop.key().is(item)) {
                total += drop.amount();
            }
        }
        return total;
    }

    private static int emptySlots(ItemStacksResourceHandler handler) {
        int empty = 0;
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getAmountAsLong(i) == 0) {
                empty++;
            }
        }
        return empty;
    }

    private static void fill(ItemStacksResourceHandler handler, Item item) {
        for (int i = 0; i < handler.size(); i++) {
            handler.set(i, ItemResource.of(item), 64);
        }
    }

    private static void clear(ItemStacksResourceHandler handler) {
        for (int i = 0; i < handler.size(); i++) {
            handler.set(i, ItemResource.EMPTY, 0);
        }
    }

    /**
     * Empties the visible output through the capability, like a pipe, in one committed transaction.
     *
     * @return how many of {@code counted} were taken out
     */
    private static long drainVisible(FarmMatrixBlockEntity machine, Item counted) {
        ResourceHandler<ItemResource> external = machine.externalOutput();
        long taken = 0;
        try (Transaction tx = Transaction.openRoot()) {
            for (int i = 0; i < external.size(); i++) {
                ItemResource resource = external.getResource(i);
                if (!resource.isEmpty()) {
                    int amount = external.extract(i, resource, external.getAmountAsInt(i), tx);
                    if (resource.is(counted)) {
                        taken += amount;
                    }
                }
            }
            tx.commit();
        }
        return taken;
    }

    /** Saves the machine and loads the result into a new, detached block entity. */
    private static FarmMatrixBlockEntity roundTrip(GameTestHelper helper, FarmMatrixBlockEntity machine) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = machine.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(machine.getBlockPos(), machine.getBlockState(), saved, registries);
        check(helper, loaded instanceof FarmMatrixBlockEntity, "loaded block entity has the wrong type");
        return (FarmMatrixBlockEntity) loaded;
    }

    /** First plantable Mystical Agriculture seed that requires a crux, or null. */
    private static Item firstCruxSeed() {
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (PlantRules.isPlantable(stack) && MysticalCompat.requiresCrux(stack)) {
                return item;
            }
        }
        return null;
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }
}
