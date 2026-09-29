/*
 * CrafterGameTests — game tests of the Entropic Farm Matrix autocrafter (owner spec 2026-09-29): recipe matching,
 * chains in order, circles that stop, items a recipe accepts in place of the grid's, remainders, the waiting limit, a
 * real harvest crafted before the output, face modes and the filter with crafted items, CRAFT OFF, saving, and the
 * menu (grid, result, SET CRAFT, select, replace, delete, limit, switch).
 */
package com.virtualfarmworks.gametest;

import java.util.ArrayList;
import java.util.List;

import com.virtualfarmworks.machine.FaceMode;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineCrafter;
import com.virtualfarmworks.machine.MachineEnergy;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;
import com.virtualfarmworks.registry.ModBlocks;
import com.virtualfarmworks.sim.DropTally;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

final class CrafterGameTests {
    private static final BlockPos MACHINE = BlockPos.ZERO;
    private static final MachineLayout LAYOUT = MachineLayout.ENTROPIC;
    private static final Item NONE = Items.AIR;

    private CrafterGameTests() {
    }

    /**
     * The crafting plan on its own (no harvest): chains run in chain order whatever the list order, a circle stops, a
     * cell takes any item its recipe accepts there, remainders come out, what waits is limited, and items no recipe
     * uses pass through. A plan never changes the crafter.
     */
    static void crafterPlans(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineCrafter crafter = machine.crafter();
        check(helper, crafter != null && crafter.isEnabled(), "the Entropic has an autocrafter, ON by default");

        // Chain, listed backwards: ladders (7 sticks -> 3) first, sticks from bamboo (2 -> 1) second.
        setRecipes(helper, machine, grid(Items.STICK, NONE, Items.STICK, Items.STICK, Items.STICK, Items.STICK,
                Items.STICK, NONE, Items.STICK), grid(Items.BAMBOO, NONE, NONE, Items.BAMBOO));
        MachineCrafter.Plan plan = crafter.plan(drops(Items.BAMBOO, 90), level, 1024);
        check(helper, amount(plan.output(), Items.LADDER) == 18, "90 bamboo -> 45 sticks -> 18 ladders, got "
                + amount(plan.output(), Items.LADDER));
        check(helper, amount(plan.output(), Items.STICK) == 0 && waiting(plan, Items.STICK) == 3,
                "the 3 sticks left wait for the next ladder");
        check(helper, crafter.buffer().isEmpty(), "a plan changes nothing");
        check(helper, crafter.isResult(ItemResource.of(Items.LADDER)) && crafter.isResult(ItemResource.of(Items.STICK))
                && !crafter.isResult(ItemResource.of(Items.BAMBOO)), "results are what the recipes make");

        // A recipe set with oak planks also takes birch planks, like a crafting table.
        setRecipes(helper, machine, grid(Items.OAK_PLANKS, NONE, NONE, Items.OAK_PLANKS));
        plan = crafter.plan(drops(Items.BIRCH_PLANKS, 4), level, 1024);
        check(helper, amount(plan.output(), Items.STICK) == 8, "4 birch planks -> 8 sticks, got "
                + amount(plan.output(), Items.STICK));

        // A circle (ingots -> block -> ingots) stops: the block goes out instead of back to ingots.
        List<ItemStack> nineIngots = grid(Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT,
                Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT);
        setRecipes(helper, machine, nineIngots, grid(Items.IRON_BLOCK));
        plan = crafter.plan(drops(Items.IRON_INGOT, 20), level, 1024);
        check(helper, amount(plan.output(), Items.IRON_BLOCK) == 2 && amount(plan.output(), Items.IRON_INGOT) == 0
                && waiting(plan, Items.IRON_INGOT) == 2, "20 ingots -> 2 blocks out, 2 ingots wait; got "
                + plan.output() + " / " + plan.buffer());

        // Remainders go out with the result.
        setRecipes(helper, machine, grid(Items.HONEY_BOTTLE));
        plan = crafter.plan(drops(Items.HONEY_BOTTLE, 2), level, 1024);
        check(helper, amount(plan.output(), Items.SUGAR) == 6 && amount(plan.output(), Items.GLASS_BOTTLE) == 2,
                "2 honey bottles -> 6 sugar + 2 glass bottles, got " + plan.output());

        // An ingredient whose partner never comes waits up to the limit; items no recipe uses pass through.
        setRecipes(helper, machine, grid(Items.WHEAT, Items.COCOA_BEANS, Items.WHEAT));
        List<DropTally.Entry<ItemResource>> harvest = List.of(entry(Items.WHEAT, 20), entry(Items.WHEAT_SEEDS, 5));
        plan = crafter.plan(harvest, level, 5);
        check(helper, waiting(plan, Items.WHEAT) == 5 && amount(plan.output(), Items.WHEAT) == 15
                && amount(plan.output(), Items.WHEAT_SEEDS) == 5, "5 wheat wait (limit), 15 go out, seeds pass; got "
                + plan.output() + " / " + plan.buffer());

        // Special recipes (firework rockets depend on their items) are refused.
        check(helper, MachineCrafter.find(grid(Items.PAPER, Items.GUNPOWDER), null, level).isEmpty(),
                "special recipes are refused");
        helper.succeed();
    }

    /**
     * A real harvest: 20 wheat plots with a hay bale recipe give 2 hay bales and leave 2 wheat waiting; the extra seeds
     * (no free soil to replant) pass. Face modes split crafted and produced items; the filter never removes crafted
     * items; the crafter survives a save; CRAFT OFF sends the waiting wheat to the output.
     */
    static void crafterCraftsTheHarvest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineInventory inputs = machine.inputs();
        inputs.set(LAYOUT.seedSlot(0), ItemResource.of(Items.WHEAT_SEEDS), 20);
        inputs.set(LAYOUT.soilSlot(0), ItemResource.of(Items.FARMLAND), 20);
        noFaces(machine);
        machine.addCrafterRecipe(nine(Items.WHEAT), null);
        machine.revalidate();
        fillEnergy(machine);
        machine.setProgressForTesting(1.0);
        harvestNow(machine, level);

        MachineCrafter crafter = machine.crafter();
        check(helper, machine.progress() < 0.5, "the harvest completes");
        check(helper, count(machine, Items.HAY_BLOCK) == 2, "20 wheat -> 2 hay bales, got "
                + count(machine, Items.HAY_BLOCK));
        check(helper, count(machine, Items.WHEAT) == 0 && waitingIn(crafter, Items.WHEAT) == 2,
                "the 2 wheat left wait in the crafter");
        check(helper, count(machine, Items.WHEAT_SEEDS) > 0, "seeds no recipe uses reach the output");

        // Faces: OUTPUT shows what the plants produce, OUTPUT CRAFTED what the crafter made.
        machine.cycleFaceMode(RelativeSide.TOP, true); // NONE -> OUTPUT
        check(helper, machine.faceMode(RelativeSide.TOP) == FaceMode.OUTPUT, "NONE -> OUTPUT");
        ResourceHandler<ItemResource> produced = itemHandler(helper, Direction.UP);
        check(helper, shows(produced, Items.WHEAT_SEEDS) && !shows(produced, Items.HAY_BLOCK),
                "OUTPUT: produced items only");
        machine.cycleFaceMode(RelativeSide.TOP, true); // OUTPUT -> OUTPUT CRAFTED
        ResourceHandler<ItemResource> crafted = itemHandler(helper, Direction.UP);
        check(helper, shows(crafted, Items.HAY_BLOCK) && !shows(crafted, Items.WHEAT_SEEDS),
                "OUTPUT CRAFTED: crafted items only");

        // The harvest filter never removes crafted items.
        machine.filter().set(0, Items.HAY_BLOCK); // blacklist
        machine.serverTick(level);
        check(helper, count(machine, Items.HAY_BLOCK) == 2, "a blacklisted crafted item stays");

        // Saved with the machine: recipes, switch, waiting ingredients.
        var registries = level.registryAccess();
        CompoundTag saved = machine.saveWithFullMetadata(registries);
        BlockEntity loaded = BlockEntity.loadStatic(machine.getBlockPos(), machine.getBlockState(), saved, registries);
        check(helper, loaded instanceof FarmMatrixBlockEntity copy && copy.crafter() != null
                && copy.crafter().size() == 1 && copy.crafter().grid(0).get(8).is(Items.WHEAT)
                && copy.crafter().isEnabled() && waitingIn(copy.crafter(), Items.WHEAT) == 2,
                "the autocrafter must survive a save");

        // CRAFT OFF: what waits goes to the output (stored on the next tick, before anything else).
        machine.setCrafterEnabled(false);
        check(helper, crafter.buffer().isEmpty(), "CRAFT OFF empties the buffer");
        machine.serverTick(level);
        check(helper, count(machine, Items.WHEAT) == 2, "the 2 waiting wheat reach the output, got "
                + count(machine, Items.WHEAT));
        helper.succeed();
    }

    /**
     * The menu's server side: 270 slots; the recipe grid shows its result; ghost clicks never move items; the result
     * ignores clicks; SET CRAFT adds, then replaces the selected recipe; the list stops at the config limit; double
     * click deletes; the switch; the list reaches the client through the display slots.
     */
    static void crafterMenuWorks(GameTestHelper helper) {
        FarmMatrixBlockEntity machine = placeMachine(helper);
        MachineCrafter crafter = machine.crafter();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        EntropicFarmMatrixMenu menu = new EntropicFarmMatrixMenu(1, player.getInventory(), machine);
        check(helper, menu.slots.size() == 270, "196 + 9 grid + 1 result + 64 list = 270, got " + menu.slots.size());

        menu.setCraftGrid(nine(Items.WHEAT));
        check(helper, menu.craftPreview().is(Items.HAY_BLOCK), "the grid shows its result");

        // Ghost cells: an empty cursor clears a cell, a cursor item sets it and stays on the cursor.
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(EntropicFarmMatrixMenu.CRAFT_GRID_START + 8, 0, ContainerInput.PICKUP, player);
        menu.broadcastChanges();
        check(helper, menu.gridStacks().get(8).isEmpty() && menu.craftPreview().isEmpty(),
                "8 wheat make nothing");
        menu.setCarried(new ItemStack(Items.WHEAT, 5));
        menu.clicked(EntropicFarmMatrixMenu.CRAFT_GRID_START + 8, 0, ContainerInput.PICKUP, player);
        menu.broadcastChanges();
        check(helper, menu.getCarried().getCount() == 5 && menu.craftPreview().is(Items.HAY_BLOCK),
                "a ghost cell takes the item type only");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(EntropicFarmMatrixMenu.CRAFT_RESULT, 0, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().isEmpty() && menu.craftPreview().is(Items.HAY_BLOCK),
                "the result cannot be taken");

        // SET CRAFT adds and clears the grid.
        menu.clickMenuButton(player, EntropicFarmMatrixMenu.BUTTON_CRAFT_SET);
        check(helper, crafter.size() == 1 && crafter.result(0).is(Items.HAY_BLOCK), "SET CRAFT adds the recipe");
        check(helper, menu.gridStacks().stream().allMatch(ItemStack::isEmpty), "SET CRAFT clears the grid");
        check(helper, menu.crafterRecipeCount() == 1 && menu.crafterResult(0).is(Items.HAY_BLOCK),
                "the list reaches the menu");

        // Select loads the recipe back; SET CRAFT then replaces it.
        menu.clickMenuButton(player, EntropicFarmMatrixMenu.BUTTON_CRAFT_SELECT_FIRST);
        check(helper, menu.selectedRecipe() == 0 && menu.gridStacks().get(0).is(Items.WHEAT),
                "a click loads the recipe into the grid");
        menu.setCraftGrid(grid(Items.WHEAT, Items.WHEAT, Items.WHEAT));
        menu.clickMenuButton(player, EntropicFarmMatrixMenu.BUTTON_CRAFT_SET);
        check(helper, crafter.size() == 1 && crafter.result(0).is(Items.BREAD) && menu.selectedRecipe() == -1,
                "SET CRAFT replaces the selected recipe");

        // The config limit.
        int limit = machine.crafterRecipeLimit();
        for (int i = crafter.size(); i < limit; i++) {
            menu.setCraftGrid(nine(Items.WHEAT));
            menu.clickMenuButton(player, EntropicFarmMatrixMenu.BUTTON_CRAFT_SET);
        }
        check(helper, crafter.size() == limit, "the list fills up to the limit of " + limit + ", got " + crafter.size());
        menu.setCraftGrid(nine(Items.WHEAT));
        menu.clickMenuButton(player, EntropicFarmMatrixMenu.BUTTON_CRAFT_SET);
        check(helper, crafter.size() == limit, "no recipe beyond the limit of " + limit);

        // Double click deletes; the switch.
        menu.clickMenuButton(player, EntropicFarmMatrixMenu.BUTTON_CRAFT_DELETE_FIRST);
        check(helper, crafter.size() == limit - 1 && crafter.result(0).is(Items.HAY_BLOCK), "the first recipe is gone");
        menu.clickMenuButton(player, EntropicFarmMatrixMenu.BUTTON_CRAFT_TOGGLE);
        check(helper, !crafter.isEnabled() && !menu.isCrafterEnabled(), "the switch turns CRAFT off");
        helper.succeed();
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private static FarmMatrixBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE, ModBlocks.ENTROPIC_FARM_MATRIX.get().defaultBlockState());
        return helper.getBlockEntity(MACHINE, FarmMatrixBlockEntity.class);
    }

    /** A recipe grid, row by row ({@link #NONE} = empty cell; missing cells are empty). */
    private static List<ItemStack> grid(Item... cells) {
        List<ItemStack> grid = new ArrayList<>(MachineCrafter.GRID_SIZE);
        for (int i = 0; i < MachineCrafter.GRID_SIZE; i++) {
            grid.add(i < cells.length && cells[i] != NONE ? new ItemStack(cells[i]) : ItemStack.EMPTY);
        }
        return grid;
    }

    private static List<ItemStack> nine(Item item) {
        return grid(item, item, item, item, item, item, item, item, item);
    }

    /** Replaces the machine's recipes; each must exist in the loaded data. */
    @SafeVarargs
    private static void setRecipes(GameTestHelper helper, FarmMatrixBlockEntity machine, List<ItemStack>... grids) {
        MachineCrafter crafter = machine.crafter();
        while (crafter.size() > 0) {
            machine.removeCrafterRecipe(0);
        }
        for (List<ItemStack> grid : grids) {
            machine.addCrafterRecipe(grid, null);
        }
        for (int i = 0; i < crafter.size(); i++) {
            check(helper, !crafter.result(i).isEmpty(), "recipe " + i + " must resolve: " + grids[i]);
        }
    }

    private static DropTally.Entry<ItemResource> entry(Item item, long amount) {
        return new DropTally.Entry<>(ItemResource.of(item), amount);
    }

    private static List<DropTally.Entry<ItemResource>> drops(Item item, long amount) {
        return List.of(entry(item, amount));
    }

    private static long amount(List<DropTally.Entry<ItemResource>> entries, Item item) {
        return entries.stream().filter(e -> e.key().is(item)).mapToLong(DropTally.Entry::amount).sum();
    }

    private static long waiting(MachineCrafter.Plan plan, Item item) {
        return plan.buffer().getOrDefault(ItemResource.of(item), 0L);
    }

    private static long waitingIn(MachineCrafter crafter, Item item) {
        return crafter.buffer().getOrDefault(ItemResource.of(item), 0L);
    }

    private static void noFaces(FarmMatrixBlockEntity machine) {
        for (RelativeSide side : RelativeSide.all()) {
            while (machine.faceMode(side) != FaceMode.NONE) {
                machine.cycleFaceMode(side, true);
            }
        }
    }

    private static void fillEnergy(FarmMatrixBlockEntity machine) {
        MachineEnergy energy = machine.energy();
        if (energy != null) {
            energy.set((int) energy.getCapacityAsLong());
        }
    }

    /** Ticks the machine directly until its due harvest is complete (at most 40 ticks, all in this game tick). */
    private static void harvestNow(FarmMatrixBlockEntity machine, ServerLevel level) {
        for (int tick = 0; tick < 40 && machine.progress() >= 0.999; tick++) {
            machine.serverTick(level);
        }
    }

    private static ResourceHandler<ItemResource> itemHandler(GameTestHelper helper, Direction side) {
        return helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE), side);
    }

    /** Whether a face's handler shows (and would give) an item. */
    private static boolean shows(ResourceHandler<ItemResource> handler, Item item) {
        if (handler == null) {
            return false;
        }
        for (int i = 0; i < handler.size(); i++) {
            if (handler.getResource(i).is(item) && handler.getAmountAsLong(i) > 0) {
                return true;
            }
        }
        return false;
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
