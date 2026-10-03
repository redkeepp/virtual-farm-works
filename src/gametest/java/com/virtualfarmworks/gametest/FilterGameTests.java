/*
 * FilterGameTests — game tests of the harvest filter (owner design, step 8): filtered items are never produced,
 * empty lists filter nothing, the menu's ghost slots, pages, mode button and JEI path edit the machine's filter
 * without ever moving real items, and rejected items are deleted from the output.
 */
package com.virtualfarmworks.gametest;

import java.util.List;
import java.util.Set;

import com.virtualfarmworks.harvest.DropSource;
import com.virtualfarmworks.harvest.HarvestFilter;
import com.virtualfarmworks.harvest.HarvestPlans;
import com.virtualfarmworks.harvest.Harvester;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.FarmMatrixMenu;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.sim.DropTally;
import com.virtualfarmworks.transfer.ItemResource;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;

final class FilterGameTests {
    /** Where the machine stands: y 1, the test area's first layer (relative y 0 is the test's structure block). */
    private static final BlockPos MACHINE = new BlockPos(0, 1, 0);

    private FilterGameTests() {
    }

    /**
     * Wheat drops wheat and extra seeds. A blacklist or whitelist decides what is produced at all; an empty list, in
     * either mode, lets everything through (owner rule).
     */
    static void rollHonorsTheFilter(GameTestHelper helper) {
        DropSource source = HarvestPlans.create(new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.FARMLAND));
        check(helper, source != null, "no drop source for wheat");

        List<DropTally.Entry<ItemResource>> blacklistSeeds = roll(helper, source,
                new HarvestFilter(false, Set.of(Items.WHEAT_SEEDS)));
        check(helper, amount(blacklistSeeds, Items.WHEAT) == 20 && amount(blacklistSeeds, Items.WHEAT_SEEDS) == 0,
                "blacklisted seeds must never be produced: " + blacklistSeeds);

        List<DropTally.Entry<ItemResource>> whitelistSeeds = roll(helper, source,
                new HarvestFilter(true, Set.of(Items.WHEAT_SEEDS)));
        check(helper, amount(whitelistSeeds, Items.WHEAT) == 0 && amount(whitelistSeeds, Items.WHEAT_SEEDS) > 0,
                "a whitelist of seeds must produce seeds only: " + whitelistSeeds);

        for (boolean whitelist : new boolean[] {false, true}) {
            List<DropTally.Entry<ItemResource>> empty = roll(helper, source, new HarvestFilter(whitelist, Set.of()));
            check(helper, amount(empty, Items.WHEAT) == 20 && amount(empty, Items.WHEAT_SEEDS) > 0,
                    "an empty " + (whitelist ? "whitelist" : "blacklist") + " must produce everything: " + empty);
        }
        helper.succeed();
    }

    /**
     * The menu side: ghost clicks record item types without touching the cursor, duplicates are refused, empty hand
     * and shift-click remove, pages and mode buttons work, the JEI path writes too, the filter survives a save, and a
     * placed machine's harvest follows it.
     */
    static void menuEditsTheFilter(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.STARTER_FARM_MATRIX.get().defaultBlockState());
        FarmMatrixBlockEntity machine = helper.<FarmMatrixBlockEntity>getBlockEntity(MACHINE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        FarmMatrixMenu menu = new FarmMatrixMenu(1, player.getInventory(), machine);
        int first = FarmMatrixMenu.FILTER_START;

        menu.setCarried(new ItemStack(Items.WHEAT_SEEDS, 5));
        menu.clicked(first + 4, 0, ClickType.PICKUP, player);
        check(helper, machine.filter().get(4) == Items.WHEAT_SEEDS, "a click with an item must list its type");
        check(helper, menu.getCarried().is(Items.WHEAT_SEEDS) && menu.getCarried().getCount() == 5,
                "the carried stack must be untouched (ghost)");
        menu.clicked(first + 5, 0, ClickType.PICKUP, player);
        check(helper, machine.filter().get(5) == null, "an item can be listed once");

        menu.setCarried(new ItemStack(Items.CARROT));
        menu.clicked(first + 5, 0, ClickType.PICKUP, player);
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(first + 4, 0, ClickType.PICKUP, player);
        check(helper, machine.filter().get(4) == null && machine.filter().get(5) == Items.CARROT,
                "an empty hand must remove only the clicked entry");
        menu.clicked(first + 5, 0, ClickType.QUICK_MOVE, player);
        check(helper, machine.filter().get(5) == null, "shift-click must remove too");
        check(helper, menu.quickMoveStack(player, first).isEmpty(), "ghost slots never move items");
        check(helper, !menu.canDragTo(menu.slots.get(first)), "drags never include ghost slots");

        // Pages are created on demand; each viewer browses its own page.
        menu.clickMenuButton(player, FarmMatrixMenu.BUTTON_FILTER_NEXT);
        menu.setCarried(new ItemStack(Items.WHEAT_SEEDS));
        menu.clicked(first, 0, ClickType.PICKUP, player);
        menu.setCarried(ItemStack.EMPTY);
        check(helper, machine.filter().get(9) == Items.WHEAT_SEEDS, "slot 0 of page 2 is position 9");
        check(helper, menu.setFilterGhost(1, new ItemStack(Items.DIAMOND)) && machine.filter().get(10) == Items.DIAMOND,
                "the JEI path must list the item on the current page");
        menu.clickMenuButton(player, FarmMatrixMenu.BUTTON_FILTER_PREVIOUS);
        menu.clickMenuButton(player, FarmMatrixMenu.BUTTON_FILTER_PREVIOUS);
        check(helper, menu.filterPage() == 0 && menu.filterPageCount() == 2, "page 1 of 2 expected, got "
                + (menu.filterPage() + 1) + "/" + menu.filterPageCount());
        menu.clickMenuButton(player, FarmMatrixMenu.BUTTON_FILTER_MODE);
        check(helper, machine.filter().isWhitelist() && menu.isFilterWhitelist(), "the mode button must switch it");

        // Saved with the machine.
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = machine.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(machine.getBlockPos(), machine.getBlockState(), saved, registries);
        check(helper, loaded instanceof FarmMatrixBlockEntity copy && copy.filter().isWhitelist()
                && copy.filter().get(9) == Items.WHEAT_SEEDS && copy.filter().get(10) == Items.DIAMOND,
                "the filter must survive a save");

        // A real harvest follows it: whitelist {wheat seeds, diamond} -> seeds only, no wheat.
        machine.inputs().set(MachineSlots.SEED, ItemResource.of(Items.WHEAT_SEEDS), 10);
        machine.inputs().set(MachineSlots.SOIL, ItemResource.of(Items.FARMLAND), 10);
        machine.inputs().set(MachineSlots.WATER_PROVIDER,
                ItemResource.of(ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get()), 1);
        for (RelativeSide side : RelativeSide.all()) {
            if (machine.isOutputEnabled(side)) {
                machine.toggleOutput(side);
            }
        }
        machine.revalidate();
        machine.setProgressForTesting(1.0);
        ServerLevel level = helper.getLevel();
        for (int tick = 0; tick < 10 && machine.progress() >= 0.999; tick++) {
            machine.serverTick(level);
        }
        check(helper, machine.progress() < 0.5, "the harvest must complete");
        check(helper, count(machine, Items.WHEAT) == 0 && count(machine, Items.WHEAT_SEEDS) > 0,
                "whitelisted seeds only: wheat " + count(machine, Items.WHEAT) + ", seeds "
                        + count(machine, Items.WHEAT_SEEDS));
        helper.succeed();
    }

    /**
     * Owner rule: the output never keeps what the filter rejects. Items already there when the filter changes are
     * deleted (visible and hidden output), and so is a rejected item a player puts back by hand; with an empty
     * filter nothing is touched.
     */
    static void filterPurgesTheOutput(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.STARTER_FARM_MATRIX.get().defaultBlockState());
        FarmMatrixBlockEntity machine = helper.<FarmMatrixBlockEntity>getBlockEntity(MACHINE);
        for (RelativeSide side : RelativeSide.all()) {
            if (machine.isOutputEnabled(side)) {
                machine.toggleOutput(side);
            }
        }
        machine.revalidate(); // sizes the hidden output
        ServerLevel level = helper.getLevel();
        machine.output().set(0, ItemResource.of(Items.WHEAT), 10);
        machine.output().set(1, ItemResource.of(Items.WHEAT_SEEDS), 5);
        machine.internalOutput().set(0, ItemResource.of(Items.WHEAT_SEEDS), 7);
        machine.serverTick(level);
        check(helper, count(machine, Items.WHEAT_SEEDS) == 12 && count(machine, Items.WHEAT) == 10,
                "an empty filter must not touch the output");

        machine.filter().set(0, Items.WHEAT_SEEDS); // blacklist the seeds
        machine.serverTick(level);
        check(helper, count(machine, Items.WHEAT_SEEDS) == 0, "seeds already in the output (visible and hidden) "
                + "must be deleted when they get blacklisted");
        check(helper, count(machine, Items.WHEAT) == 10, "allowed items stay");

        // The owner's scenario: the player puts the blacklisted seed back into the output by hand, through the menu.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        FarmMatrixMenu menu = new FarmMatrixMenu(1, player.getInventory(), machine);
        menu.setCarried(new ItemStack(Items.WHEAT_SEEDS, 3));
        menu.clicked(FarmMatrixMenu.OUTPUT_START + 4, 0, ClickType.PICKUP, player);
        check(helper, count(machine, Items.WHEAT_SEEDS) == 3 && menu.getCarried().isEmpty(),
                "the output accepts items by hand");
        machine.serverTick(level);
        check(helper, count(machine, Items.WHEAT_SEEDS) == 0, "a blacklisted item put in by hand must be deleted");

        // Whitelist of the seeds: now the wheat is the rejected one.
        machine.filter().toggleMode();
        machine.serverTick(level);
        check(helper, count(machine, Items.WHEAT) == 0, "switching to a whitelist must delete what is not listed");
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static List<DropTally.Entry<ItemResource>> roll(GameTestHelper helper, DropSource source,
                                                            HarvestFilter filter) {
        ServerLevel level = helper.getLevel();
        return Harvester.roll(source, 20, MachineTier.STARTER, new DropSource.Context(level,
                helper.absolutePos(BlockPos.ZERO), level.getRandom(), 64, true, filter));
    }

    private static long amount(List<DropTally.Entry<ItemResource>> drops, Item item) {
        return drops.stream().filter(drop -> drop.key().is(item)).mapToLong(DropTally.Entry::amount).sum();
    }

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
