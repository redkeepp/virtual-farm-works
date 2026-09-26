/*
 * MachineGameTests — game tests of the placed Farm Matrix: a full growth cycle into the buffer, status reporting,
 * OUTPUT FULL holding the harvest, save/load, auto-export per face, contents dropped on break, slot rules, and the
 * server side of the GUI menu (buttons, shift-click, output slots, synced data).
 */
package com.virtualfarmworks.gametest;

import com.virtualfarmworks.compat.mysticalagriculture.MysticalCompat;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.FarmMatrixMenu;
import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.item.ItemResource;
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
     * A due harvest that does not fit waits at 100% with OUTPUT FULL and nothing is lost or partially stored; as soon
     * as there is room for all of it, the same harvest is stored.
     */
    static void outputFullHoldsTheHarvest(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        put(inputs, MachineSlots.SEED, Items.WHEAT_SEEDS, 10);
        put(inputs, MachineSlots.SOIL, Items.FARMLAND, 10);
        put(inputs, MachineSlots.WATER_PROVIDER, ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get(), 1);
        for (int i = 0; i < MachineSlots.OUTPUT_COUNT; i++) {
            machine.output().set(i, ItemResource.of(Items.DIRT), 64);
        }
        disableAllOutputs(machine); // keep the dirt in the buffer
        machine.revalidate();
        machine.setProgressForTesting(1.0);

        helper.runAfterDelay(5, () -> {
            check(helper, machine.status() == MachineStatus.OUTPUT_FULL, "expected OUTPUT_FULL, got " + machine.status());
            check(helper, machine.progress() >= 0.999, "the bar must stay at 100% while blocked");
            check(helper, count(machine, Items.WHEAT) == 0, "a blocked harvest must not store anything");
            // Free ONE slot first: a wheat harvest is two item types (wheat + extra seeds), so it still must not fit,
            // and nothing may be stored partially.
            machine.output().set(0, ItemResource.EMPTY, 0);
        });
        helper.runAfterDelay(10, () -> {
            check(helper, machine.status() == MachineStatus.OUTPUT_FULL, "one free slot is not enough for 2 item types");
            check(helper, count(machine, Items.WHEAT) == 0, "no partial storage: wheat alone must not be stored");
            machine.output().set(1, ItemResource.EMPTY, 0); // now wheat and seeds both have room
        });
        helper.succeedWhen(() -> {
            check(helper, count(machine, Items.WHEAT) == 10, "harvest must be stored once space appears");
            check(helper, machine.progress() < 0.5, "cycle must complete after storing");
            check(helper, machine.status() == MachineStatus.RUNNING, "expected RUNNING again");
        });
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
        machine.toggleOutput(RelativeSide.LEFT);
        machine.setEnabled(false);

        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = machine.saveWithFullMetadata(registries);
        BlockState state = machine.getBlockState();
        BlockEntity loadedEntity = BlockEntity.loadStatic(machine.getBlockPos(), state, saved, registries);
        check(helper, loadedEntity instanceof FarmMatrixBlockEntity, "loaded block entity has the wrong type");
        FarmMatrixBlockEntity loaded = (FarmMatrixBlockEntity) loadedEntity;
        loaded.revalidate();

        check(helper, Math.abs(loaded.progress() - 0.5) < 1e-9, "progress lost: " + loaded.progress());
        check(helper, loaded.activePlots() == 40, "active plots lost: " + loaded.activePlots());
        check(helper, loaded.pendingPlots() == 24, "pending plots lost: " + loaded.pendingPlots());
        check(helper, loaded.inputs().getAmountAsInt(MachineSlots.SEED) == 64, "seed slot lost");
        check(helper, loaded.output().getAmountAsInt(3) == 12, "output buffer lost");
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

    /** Breaking the machine drops every stored item (inputs and output buffer). */
    static void dropsItsContents(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        put(machine.inputs(), MachineSlots.SEED, Items.WHEAT_SEEDS, 7);
        put(machine.inputs(), MachineSlots.SOIL, Items.FARMLAND, 3);
        machine.output().set(0, ItemResource.of(Items.WHEAT), 5);

        helper.destroyBlock(MACHINE);

        helper.assertItemEntityPresent(Items.WHEAT_SEEDS, MACHINE, 2.0);
        helper.assertItemEntityPresent(Items.FARMLAND, MACHINE, 2.0);
        helper.assertItemEntityPresent(Items.WHEAT, MACHINE, 2.0);
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
            check(helper, !menu.slots.get(FarmMatrixMenu.OUTPUT_START + i).mayPlace(new ItemStack(Items.WHEAT)),
                    "output slots must refuse items");
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

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static FarmMatrixBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.STARTER_FARM_MATRIX.get().defaultBlockState());
        return helper.getBlockEntity(MACHINE, FarmMatrixBlockEntity.class);
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

    private static int count(FarmMatrixBlockEntity machine, Item item) {
        int total = 0;
        for (int i = 0; i < machine.output().size(); i++) {
            if (machine.output().getResource(i).is(item)) {
                total += machine.output().getAmountAsInt(i);
            }
        }
        return total;
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
