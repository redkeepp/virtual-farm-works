/*
 * EntropicFarmMatrixMenu — the Entropic Farm Matrix container menu (both sides): the two 4x15 grids, water provider,
 * side-column upgrades and hoe, 24 output slots, harvest filter ghost slots, the autocrafter (recipe grid, result,
 * recipe list, SET CRAFT, CRAFT ON/OFF) and the numbers only this tier shows (energy, plot capacity, waiting plots, face
 * modes, each plot group's status).
 */
package com.virtualfarmworks.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.machine.FaceMode;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineCrafter;
import com.virtualfarmworks.machine.MachineEnergy;
import com.virtualfarmworks.machine.MachineFilter;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.OutputBuffer;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.registry.ModMenus;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * The Entropic's menu.
 *
 * <h2>Slot order (menu indices)</h2>
 * {@code [0, 127)} machine inputs in inventory order ({@link MachineLayout}: 60 seed slots, 60 soil slots, water
 * provider, hoe, 4 growth, crux), {@code [127, 151)} output buffer, {@code [151, 178)} player inventory,
 * {@code [178, 187)} hotbar, {@code [187, 196)} harvest filter ghost slots, {@code [196, 205)} autocrafter recipe grid
 * (ghost slots), {@code 205} its result, {@code [206, 270)} the recipe list's results (data only: never drawn or
 * clicked; vanilla slot sync carries them to the client).
 *
 * <h2>Autocrafter</h2>
 * The recipe grid belongs to this menu, not the machine (owner: "purely visual, the machine does not use it"): each
 * viewer arranges their own; the server shows the recipe's result in the result slot. Buttons: SET CRAFT (saves the
 * grid as a recipe, replacing the selected one), CRAFT ON/OFF, select a recipe (loads it into the grid), delete a
 * recipe, clear the selection. The panel is a client-side modal ({@link #setCrafterVisible}): while it is open its
 * slots are active and the two grids under it are not.
 *
 * <h2>Synced numbers (after the shared ones)</h2>
 * Energy, capacity and use per tick; planted plots, plot capacity and waiting plots (two shorts each, up to 2^30);
 * the mode of each face; the status of each plot group (three groups per short, 4 bits each); the autocrafter's switch,
 * recipe count and limit and this viewer's selected recipe.
 */
public class EntropicFarmMatrixMenu extends AbstractFarmMatrixMenu {
    private static final MachineLayout LAYOUT = MachineLayout.ENTROPIC;

    public static final int INPUT_START = 0;
    public static final int INPUT_END = LAYOUT.inputCount();                          // 127
    public static final int OUTPUT_START = INPUT_END;
    public static final int OUTPUT_END = OUTPUT_START + LAYOUT.visibleOutputSlots();   // 151
    public static final int PLAYER_START = OUTPUT_END;
    public static final int HOTBAR_START = PLAYER_START + 27;
    public static final int PLAYER_END = HOTBAR_START + 9;                             // 187
    public static final int FILTER_START = PLAYER_END;
    public static final int CRAFT_GRID_START = FILTER_START + MachineFilter.PAGE_SIZE;             // 196
    public static final int CRAFT_RESULT = CRAFT_GRID_START + MachineCrafter.GRID_SIZE;            // 205
    public static final int CRAFT_LIST_START = CRAFT_RESULT + 1;                                   // 206
    public static final int CRAFT_LIST_END = CRAFT_LIST_START + MachineCrafter.MAX_RECIPES;        // 270

    /** Autocrafter buttons (after the shared ids). */
    public static final int BUTTON_CRAFT_SET = BUTTON_TIER_FIRST;
    public static final int BUTTON_CRAFT_TOGGLE = BUTTON_CRAFT_SET + 1;
    public static final int BUTTON_CRAFT_DESELECT = BUTTON_CRAFT_TOGGLE + 1;
    /** Select recipe i: {@code BUTTON_CRAFT_SELECT_FIRST + i}. */
    public static final int BUTTON_CRAFT_SELECT_FIRST = BUTTON_CRAFT_DESELECT + 1;
    /** Delete recipe i: {@code BUTTON_CRAFT_DELETE_FIRST + i}. */
    public static final int BUTTON_CRAFT_DELETE_FIRST = BUTTON_CRAFT_SELECT_FIRST + MachineCrafter.MAX_RECIPES;

    private static final int DATA_ENERGY = BASE_DATA_COUNT;          // 2 shorts
    private static final int DATA_ENERGY_CAPACITY = DATA_ENERGY + 2;  // 2 shorts
    private static final int DATA_ENERGY_USE = DATA_ENERGY_CAPACITY + 2;
    private static final int DATA_PLANTED = DATA_ENERGY_USE + 2;
    private static final int DATA_CAPACITY = DATA_PLANTED + 2;
    private static final int DATA_WAITING = DATA_CAPACITY + 2;
    private static final int DATA_FACE_MODES = DATA_WAITING + 2;      // one per side
    private static final int DATA_GROUP_STATUS = DATA_FACE_MODES + RelativeSide.values().length;
    private static final int GROUPS_PER_SHORT = 3;
    private static final int DATA_CRAFTER_ENABLED = DATA_GROUP_STATUS
            + (LAYOUT.groups() + GROUPS_PER_SHORT - 1) / GROUPS_PER_SHORT;
    private static final int DATA_CRAFTER_COUNT = DATA_CRAFTER_ENABLED + 1;
    private static final int DATA_CRAFTER_LIMIT = DATA_CRAFTER_COUNT + 1;
    private static final int DATA_CRAFTER_SELECTED = DATA_CRAFTER_LIMIT + 1; // selected recipe + 1 (0 = none)
    private static final int DATA_COUNT = DATA_CRAFTER_SELECTED + 1;

    /** This viewer's recipe grid (item types only). */
    private final Container craftGrid;
    /** The grid's result, computed by the server. */
    private final SimpleContainer craftResult = new SimpleContainer(1);
    /** Result of each recipe of the machine (server: refreshed with the synced numbers). */
    private final SimpleContainer recipeList = new SimpleContainer(MachineCrafter.MAX_RECIPES);
    /** Server side: the grid changed, recompute its result before the next sync. */
    private boolean previewDirty;
    /** Server side: this viewer's selected recipe (SET CRAFT replaces it), -1 for none. */
    private int selectedRecipe = -1;
    /** Client side: whether the autocrafter panel is open. */
    private boolean crafterVisible;

    /** Server constructor: backed by the real machine. */
    public EntropicFarmMatrixMenu(int containerId, Inventory playerInventory, FarmMatrixBlockEntity machine) {
        this(containerId, playerInventory, machine.tier(), machine.inputs(), machine.output(), machine,
                ContainerLevelAccess.create(machine.getLevel(), machine.getBlockPos()), machine.getBlockState().getBlock());
        refreshData();
    }

    /** Client constructor, from the open-menu packet (block position and tier). */
    public static EntropicFarmMatrixMenu fromNetwork(int containerId, Inventory playerInventory,
                                                     RegistryFriendlyByteBuf buf) {
        MachineTier tier = readOpenData(buf);
        return new EntropicFarmMatrixMenu(containerId, playerInventory, tier, new MachineInventory(tier, () -> {
        }), new OutputBuffer(LAYOUT.visibleOutputSlots(), () -> {
        }), null, ContainerLevelAccess.NULL, null);
    }

    private EntropicFarmMatrixMenu(int containerId, Inventory playerInventory, MachineTier tier, MachineInventory inputs,
                                   OutputBuffer output, @Nullable FarmMatrixBlockEntity machine,
                                   ContainerLevelAccess access, @Nullable Block block) {
        super(ModMenus.ENTROPIC_FARM_MATRIX.get(), containerId, tier, machine, access, block, DATA_COUNT);
        this.craftGrid = machine == null ? new SimpleContainer(MachineCrafter.GRID_SIZE)
                : new SimpleContainer(MachineCrafter.GRID_SIZE) {
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        previewDirty = true;
                    }
                };

        for (int group = 0; group < LAYOUT.groups(); group++) {
            addSlot(new GridSlot(inputs, LAYOUT.seedSlot(group), EntropicLayout.seedSlotX(group),
                    EntropicLayout.seedSlotY(group)));
        }
        for (int group = 0; group < LAYOUT.groups(); group++) {
            addSlot(new GridSlot(inputs, LAYOUT.soilSlot(group), EntropicLayout.seedSlotX(group),
                    EntropicLayout.soilSlotY(group)));
        }
        addSlot(new ResourceHandlerSlot(inputs, inputs::set, LAYOUT.waterSlot(), EntropicLayout.WATER_X,
                EntropicLayout.WATER_Y));
        // Side column cells: 4 growth (0..3), crux (4), hoe (5). Inventory order is water, hoe, growth, crux.
        addSlot(new ResourceHandlerSlot(inputs, inputs::set, LAYOUT.hoeSlot(), FarmMatrixLayout.PANEL_INTERIOR_X,
                EntropicLayout.upgradeCellY(5)));
        for (int i = 0; i < MachineSlots.GROWTH_COUNT; i++) {
            addSlot(new ResourceHandlerSlot(inputs, inputs::set, LAYOUT.growthSlot(i), FarmMatrixLayout.PANEL_INTERIOR_X,
                    EntropicLayout.upgradeCellY(i)));
        }
        addSlot(new ResourceHandlerSlot(inputs, inputs::set, LAYOUT.cruxSlot(), FarmMatrixLayout.PANEL_INTERIOR_X,
                EntropicLayout.upgradeCellY(4)));

        Runnable onPlayerPut = machine != null ? machine::requestFilterPurge : () -> {
        };
        for (int i = 0; i < LAYOUT.visibleOutputSlots(); i++) {
            addSlot(new OutputSlot(output, i, EntropicLayout.outputSlotX(i), EntropicLayout.outputSlotY(i), onPlayerPut));
        }
        addStandardInventorySlots(playerInventory, EntropicLayout.PLAYER_INVENTORY_X, EntropicLayout.PLAYER_INVENTORY_Y);
        addFilterSlots(EntropicLayout::filterSlotX, EntropicLayout::filterSlotY);

        for (int i = 0; i < MachineCrafter.GRID_SIZE; i++) {
            addSlot(new CraftGridSlot(craftGrid, i, EntropicLayout.crafterGridX(i), EntropicLayout.crafterGridY(i)));
        }
        addSlot(new CraftResultSlot(craftResult, EntropicLayout.CRAFTER_RESULT_X, EntropicLayout.CRAFTER_RESULT_Y));
        for (int i = 0; i < MachineCrafter.MAX_RECIPES; i++) {
            addSlot(new RecipeListSlot(recipeList, i));
        }
        addDataSlots(data);
    }

    // --- sync -------------------------------------------------------------------------------------------------------

    /** Server: recomputes the grid's result before vanilla sends the slot changes. */
    @Override
    public void broadcastChanges() {
        if (previewDirty && machine != null && machine.getLevel() instanceof ServerLevel level) {
            previewDirty = false;
            craftResult.setItem(0, MachineCrafter.preview(gridStacks(), level));
        }
        super.broadcastChanges();
    }

    @Override
    protected void refreshTierData(FarmMatrixBlockEntity machine) {
        MachineEnergy energy = machine.energy();
        setLong(DATA_ENERGY, energy == null ? 0 : energy.getAmountAsLong());
        setLong(DATA_ENERGY_CAPACITY, energy == null ? 0 : energy.getCapacityAsLong());
        setLong(DATA_ENERGY_USE, machine.energyPerTick());
        setLong(DATA_PLANTED, machine.totalPlots());
        setLong(DATA_CAPACITY, VfwServerConfig.machine(tier).plotCapacity(LAYOUT));
        setLong(DATA_WAITING, machine.waitingPlots());
        for (RelativeSide side : RelativeSide.all()) {
            data.set(DATA_FACE_MODES + side.ordinal(), machine.faceMode(side).ordinal());
        }
        for (int slot = 0; slot * GROUPS_PER_SHORT < LAYOUT.groups(); slot++) {
            int packed = 0;
            for (int k = 0; k < GROUPS_PER_SHORT; k++) {
                int group = slot * GROUPS_PER_SHORT + k;
                if (group < LAYOUT.groups()) {
                    packed |= machine.groupStatus(group).ordinal() << (4 * k);
                }
            }
            data.set(DATA_GROUP_STATUS + slot, packed);
        }

        machine.ensureCrafterResolved();
        MachineCrafter crafter = machine.crafter();
        int count = crafter == null ? 0 : crafter.size();
        for (int i = 0; i < MachineCrafter.MAX_RECIPES; i++) {
            ItemStack shown = i < count ? crafter.result(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(recipeList.getItem(i), shown)) {
                recipeList.setItem(i, shown);
            }
        }
        if (selectedRecipe >= count) {
            selectedRecipe = -1;
        }
        data.set(DATA_CRAFTER_ENABLED, crafter != null && crafter.isEnabled() ? 1 : 0);
        data.set(DATA_CRAFTER_COUNT, count);
        data.set(DATA_CRAFTER_LIMIT, machine.crafterRecipeLimit());
        data.set(DATA_CRAFTER_SELECTED, selectedRecipe + 1);
    }

    public long energy() {
        return getLong(DATA_ENERGY);
    }

    public long energyCapacity() {
        return getLong(DATA_ENERGY_CAPACITY);
    }

    public long energyUse() {
        return getLong(DATA_ENERGY_USE);
    }

    /** Planted plots (the owner's "active plots"). */
    public long plantedPlots() {
        return getLong(DATA_PLANTED);
    }

    public long plotCapacity() {
        return getLong(DATA_CAPACITY);
    }

    public long waitingPlots() {
        return getLong(DATA_WAITING);
    }

    public FaceMode faceMode(RelativeSide side) {
        return FaceMode.byOrdinal(data.get(DATA_FACE_MODES + side.ordinal()));
    }

    /** What a plot group shows (RUNNING = it grows; MISSING_SEED = empty). */
    public MachineStatus groupStatus(int group) {
        int packed = data.get(DATA_GROUP_STATUS + group / GROUPS_PER_SHORT);
        return MachineStatus.byOrdinal((packed >> (4 * (group % GROUPS_PER_SHORT))) & 0xF);
    }

    // --- autocrafter ------------------------------------------------------------------------------------------------

    /** CRAFT: ON/OFF. */
    public boolean isCrafterEnabled() {
        return data.get(DATA_CRAFTER_ENABLED) != 0;
    }

    public int crafterRecipeCount() {
        return Math.clamp(data.get(DATA_CRAFTER_COUNT), 0, MachineCrafter.MAX_RECIPES);
    }

    /** Recipes the machine may hold (config). */
    public int crafterRecipeLimit() {
        return data.get(DATA_CRAFTER_LIMIT);
    }

    /** This viewer's selected recipe (SET CRAFT replaces it), -1 for none. */
    public int selectedRecipe() {
        return data.get(DATA_CRAFTER_SELECTED) - 1;
    }

    /** What recipe {@code index} makes; empty when it no longer exists in the loaded data. */
    public ItemStack crafterResult(int index) {
        return slots.get(CRAFT_LIST_START + index).getItem();
    }

    /** What the recipe grid makes; empty when it holds no accepted recipe. */
    public ItemStack craftPreview() {
        return slots.get(CRAFT_RESULT).getItem();
    }

    /** The recipe grid's 9 cells (copies). */
    public List<ItemStack> gridStacks() {
        List<ItemStack> cells = new ArrayList<>(MachineCrafter.GRID_SIZE);
        for (int i = 0; i < MachineCrafter.GRID_SIZE; i++) {
            cells.add(craftGrid.getItem(i).copy());
        }
        return cells;
    }

    /** Client side: the screen opens and closes the autocrafter panel (JEI's "+" opens it too). */
    public void setCrafterVisible(boolean visible) {
        crafterVisible = visible;
    }

    public boolean isCrafterVisible() {
        return crafterVisible;
    }

    /**
     * Server side: puts a whole grid in the recipe grid (JEI's "+", items dragged from JEI; see
     * {@code network.SetCrafterGridPayload}). Item types only, like any ghost slot.
     */
    public void setCraftGrid(List<ItemStack> grid) {
        if (machine == null) {
            return;
        }
        List<ItemStack> cells = MachineCrafter.normalize(grid);
        for (int i = 0; i < MachineCrafter.GRID_SIZE; i++) {
            craftGrid.setItem(i, cells.get(i));
        }
        broadcastChanges();
    }

    @Override
    protected boolean clickTierButton(Player player, int id) {
        if (machine == null || machine.crafter() == null) {
            return false;
        }
        MachineCrafter crafter = machine.crafter();
        if (id == BUTTON_CRAFT_SET) {
            setCraft();
        } else if (id == BUTTON_CRAFT_TOGGLE) {
            machine.setCrafterEnabled(!crafter.isEnabled());
        } else if (id == BUTTON_CRAFT_DESELECT) {
            selectedRecipe = -1;
        } else if (id >= BUTTON_CRAFT_SELECT_FIRST && id < BUTTON_CRAFT_SELECT_FIRST + MachineCrafter.MAX_RECIPES) {
            int index = id - BUTTON_CRAFT_SELECT_FIRST;
            if (index < crafter.size()) {
                selectedRecipe = index;
                List<ItemStack> grid = crafter.grid(index);
                for (int i = 0; i < MachineCrafter.GRID_SIZE; i++) {
                    craftGrid.setItem(i, grid.get(i));
                }
            }
        } else if (id >= BUTTON_CRAFT_DELETE_FIRST && id < BUTTON_CRAFT_DELETE_FIRST + MachineCrafter.MAX_RECIPES) {
            int index = id - BUTTON_CRAFT_DELETE_FIRST;
            if (index < crafter.size()) {
                machine.removeCrafterRecipe(index);
                if (selectedRecipe == index) {
                    selectedRecipe = -1;
                } else if (selectedRecipe > index) {
                    selectedRecipe--;
                }
            }
        } else {
            return false;
        }
        return true;
    }

    /**
     * SET CRAFT: the grid's recipe replaces the selected one, or is added while the list has room (config limit). A grid
     * that makes no accepted recipe does nothing. Afterwards the grid is cleared, ready for the next recipe.
     */
    private void setCraft() {
        if (machine == null || machine.crafter() == null || !(machine.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<ItemStack> grid = gridStacks();
        Optional<RecipeHolder<CraftingRecipe>> recipe = MachineCrafter.find(grid, null, level);
        if (recipe.isEmpty()) {
            return;
        }
        MachineCrafter crafter = machine.crafter();
        if (selectedRecipe >= 0 && selectedRecipe < crafter.size()) {
            machine.replaceCrafterRecipe(selectedRecipe, grid, recipe.get().id());
        } else if (crafter.size() < machine.crafterRecipeLimit()) {
            machine.addCrafterRecipe(grid, recipe.get().id());
        } else {
            return;
        }
        selectedRecipe = -1;
        craftGrid.clearContent();
    }

    // --- shift-click ------------------------------------------------------------------------------------------------

    /**
     * Shift-click. Machine slots go to the player's inventory. Player items go into the machine where their slot rules
     * accept them — plantables into the seed grid, soils into the soil grid, upgrades and the hoe into their slots —
     * completing slots of the same item first; otherwise between inventory and hotbar. Never into the output buffer.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot instanceof GhostSlot || slot instanceof DisplaySlot || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INPUT_START, INPUT_END, false)) {
            boolean fromMainInventory = index < HOTBAR_START;
            if (!moveItemStackTo(stack, fromMainInventory ? HOTBAR_START : PLAYER_START,
                    fromMainInventory ? PLAYER_END : HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    // --- slots ------------------------------------------------------------------------------------------------------

    /** A seed or soil grid slot: hidden under the autocrafter panel while it is open. */
    private final class GridSlot extends ResourceHandlerSlot {
        GridSlot(MachineInventory inputs, int index, int x, int y) {
            super(inputs, inputs::set, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !crafterVisible;
        }
    }

    /** A cell of the recipe grid (item type only); active while the panel is open. */
    private final class CraftGridSlot extends GhostSlot {
        CraftGridSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return crafterVisible;
        }
    }

    /** The grid's result, computed by the server; active while the panel is open. */
    private final class CraftResultSlot extends DisplaySlot {
        CraftResultSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean isActive() {
            return crafterVisible;
        }
    }

    /** One recipe's result, carried to the client for the list the screen draws itself; never active. */
    private static final class RecipeListSlot extends DisplaySlot {
        RecipeListSlot(Container container, int index) {
            super(container, index, 0, 0);
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
