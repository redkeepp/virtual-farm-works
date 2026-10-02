/*
 * EntropicFarmMatrixMenu — the Entropic Farm Matrix container menu (both sides): the two 4x15 grids, water provider,
 * side-column upgrades, 24 output slots, harvest filter ghost slots, the autocrafter (recipe grid, result, catalyst,
 * recipe list, SET CRAFT, CRAFT ON/OFF), the replant switch and the numbers only this tier shows (energy and its use,
 * plot capacity, waiting plots, face modes, each plot group's status).
 */
package com.virtualfarmworks.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

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
import com.virtualfarmworks.network.CrafterRecipesPayload;
import com.virtualfarmworks.registry.ModMenus;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Entropic's menu.
 *
 * <h2>Slot order (menu indices)</h2>
 * {@code [0, 127)} machine inputs in inventory order ({@link MachineLayout}: 60 seed slots, 60 soil slots, water
 * provider, the autocrafter's catalyst, 4 growth, crux), {@code [127, 151)} output buffer, {@code [151, 178)} player
 * inventory, {@code [178, 187)} hotbar, {@code [187, 196)} harvest filter ghost slots, {@code [196, 205)} autocrafter
 * recipe grid (ghost slots), {@code 205} its result.
 *
 * <h2>Autocrafter</h2>
 * The recipe grid belongs to this menu, not the machine (owner: "purely visual, the machine does not use it"): each
 * viewer arranges their own; the server shows the recipe's result in the result slot. Buttons: SET CRAFT (saves the
 * grid as a recipe, replacing the selected one), CRAFT ON/OFF, select a recipe (loads it into the grid), delete a
 * recipe, clear the selection. The panel is a client-side modal ({@link #setCrafterVisible}): while it is open its
 * slots (grid, result, catalyst) are active and the two grids under it are not. The recipe list is dynamic (owner: up to
 * 100, "a slot is only created when the player needs it"): it travels as a {@link CrafterRecipesPayload} whenever it
 * changes, not as menu slots.
 *
 * <h2>Synced numbers (after the shared ones)</h2>
 * Energy shown, capacity, use right now and use per plot; planted plots, plot capacity and waiting plots (two shorts
 * each, up to 2^30); the mode of each face; the status of each plot group (three groups per short, 4 bits each); the
 * autocrafter's switch and limit and this viewer's selected recipe; the replant switch.
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

    /** Replant switch (owner, 2026-09-29). */
    public static final int BUTTON_REPLANT = BUTTON_TIER_FIRST;
    /** Autocrafter buttons. */
    public static final int BUTTON_CRAFT_SET = BUTTON_REPLANT + 1;
    public static final int BUTTON_CRAFT_TOGGLE = BUTTON_CRAFT_SET + 1;
    public static final int BUTTON_CRAFT_DESELECT = BUTTON_CRAFT_TOGGLE + 1;
    /** Select recipe i: {@code BUTTON_CRAFT_SELECT_FIRST + i}. */
    public static final int BUTTON_CRAFT_SELECT_FIRST = BUTTON_CRAFT_DESELECT + 1;
    /** Delete recipe i: {@code BUTTON_CRAFT_DELETE_FIRST + i}. */
    public static final int BUTTON_CRAFT_DELETE_FIRST = BUTTON_CRAFT_SELECT_FIRST + MachineCrafter.MAX_RECIPES;

    private static final int DATA_ENERGY = BASE_DATA_COUNT;           // 2 shorts each from here
    private static final int DATA_ENERGY_CAPACITY = DATA_ENERGY + 2;
    private static final int DATA_ENERGY_USE = DATA_ENERGY_CAPACITY + 2;
    private static final int DATA_ENERGY_PER_PLOT = DATA_ENERGY_USE + 2;
    private static final int DATA_PLANTED = DATA_ENERGY_PER_PLOT + 2;
    private static final int DATA_CAPACITY = DATA_PLANTED + 2;
    private static final int DATA_WAITING = DATA_CAPACITY + 2;
    private static final int DATA_FACE_MODES = DATA_WAITING + 2;      // one per side
    private static final int DATA_GROUP_STATUS = DATA_FACE_MODES + RelativeSide.values().length;
    private static final int GROUPS_PER_SHORT = 3;
    private static final int DATA_CRAFTER_ENABLED = DATA_GROUP_STATUS
            + (LAYOUT.groups() + GROUPS_PER_SHORT - 1) / GROUPS_PER_SHORT;
    private static final int DATA_CRAFTER_LIMIT = DATA_CRAFTER_ENABLED + 1;
    private static final int DATA_CRAFTER_SELECTED = DATA_CRAFTER_LIMIT + 1; // selected recipe + 1 (0 = none)
    private static final int DATA_REPLANT = DATA_CRAFTER_SELECTED + 1;       // FarmMatrixBlockEntity.ReplantState
    private static final int DATA_COUNT = DATA_REPLANT + 1;

    /** Server side: the viewer, to send the recipe list to. Null on the client. */
    private final @Nullable ServerPlayer viewer;
    /** This viewer's recipe grid (item types only). */
    private final Container craftGrid;
    /** The grid's result, computed by the server. */
    private final SimpleContainer craftResult = new SimpleContainer(1);
    /** What each recipe makes. Server: the machine's list as of the last refresh; client: the last list received. */
    private List<ItemStack> recipeResults = List.of();
    /** Server side: the list the client has (to send changes only). */
    private @Nullable List<ItemStack> sentResults;
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
        this.viewer = machine != null && playerInventory.player instanceof ServerPlayer player ? player : null;
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
        addSlot(new HandlerSlot(inputs, LAYOUT.waterSlot(), EntropicLayout.WATER_X, EntropicLayout.WATER_Y));
        // Inventory order is water, tool (here the catalyst, shown in the crafter panel), growth, crux.
        addSlot(new CatalystSlot(inputs, LAYOUT.catalystSlot(), EntropicLayout.CRAFTER_CATALYST_X,
                EntropicLayout.CRAFTER_CATALYST_Y));
        for (int i = 0; i < MachineSlots.GROWTH_COUNT; i++) {
            addSlot(new HandlerSlot(inputs, LAYOUT.growthSlot(i), FarmMatrixLayout.PANEL_INTERIOR_X,
                    EntropicLayout.upgradeCellY(i)));
        }
        addSlot(new HandlerSlot(inputs, LAYOUT.cruxSlot(), FarmMatrixLayout.PANEL_INTERIOR_X,
                EntropicLayout.upgradeCellY(4)));

        Runnable onPlayerPut = machine != null ? machine::requestFilterPurge : () -> {
        };
        for (int i = 0; i < LAYOUT.visibleOutputSlots(); i++) {
            addSlot(new OutputSlot(output, i, EntropicLayout.outputSlotX(i), EntropicLayout.outputSlotY(i), onPlayerPut));
        }
        addPlayerInventorySlots(playerInventory, EntropicLayout.PLAYER_INVENTORY_X, EntropicLayout.PLAYER_INVENTORY_Y);
        addFilterSlots(EntropicLayout::filterSlotX, EntropicLayout::filterSlotY);

        for (int i = 0; i < MachineCrafter.GRID_SIZE; i++) {
            addSlot(new CraftGridSlot(craftGrid, i, EntropicLayout.crafterGridX(i), EntropicLayout.crafterGridY(i)));
        }
        addSlot(new CraftResultSlot(craftResult, EntropicLayout.CRAFTER_RESULT_X, EntropicLayout.CRAFTER_RESULT_Y));
        addDataSlots(data);
    }

    // --- sync -------------------------------------------------------------------------------------------------------

    /** Server: recomputes the grid's result, and sends the recipe list if it changed, before vanilla's sync. */
    @Override
    public void broadcastChanges() {
        if (previewDirty && machine != null && machine.getLevel() instanceof ServerLevel level) {
            previewDirty = false;
            craftResult.setItem(0, MachineCrafter.preview(gridStacks(), level));
        }
        super.broadcastChanges();
        if (sentResults != null && !ItemStack.listMatches(sentResults, recipeResults)) {
            sendRecipeList();
        }
    }

    /** Server: vanilla sends everything when the menu opens (after the open-screen packet); the recipe list goes too. */
    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        if (viewer != null) {
            sendRecipeList();
        }
    }

    private void sendRecipeList() {
        if (viewer == null) {
            return;
        }
        sentResults = recipeResults;
        PacketDistributor.sendToPlayer(viewer, new CrafterRecipesPayload(containerId, recipeResults));
    }

    @Override
    protected void refreshTierData(FarmMatrixBlockEntity machine) {
        MachineEnergy energy = machine.energy();
        setLong(DATA_ENERGY, energy == null ? 0 : energy.shownAmount());
        setLong(DATA_ENERGY_CAPACITY, energy == null ? 0 : energy.getCapacityAsLong());
        setLong(DATA_ENERGY_USE, energy == null ? 0 : energy.lastUse());
        setLong(DATA_ENERGY_PER_PLOT, machine.energyPerPlot());
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
        data.set(DATA_REPLANT, machine.replantState().ordinal());

        machine.ensureCrafterResolved();
        MachineCrafter crafter = machine.crafter();
        recipeResults = crafter == null ? List.of() : List.copyOf(crafter.results());
        if (selectedRecipe >= recipeResults.size()) {
            selectedRecipe = -1;
        }
        data.set(DATA_CRAFTER_ENABLED, crafter != null && crafter.isEnabled() ? 1 : 0);
        data.set(DATA_CRAFTER_LIMIT, machine.crafterRecipeLimit());
        data.set(DATA_CRAFTER_SELECTED, selectedRecipe + 1);
    }

    /** FE the GUI shows: the level before the machine's payment of its last tick (see {@code MachineEnergy}). */
    public long energy() {
        return getLong(DATA_ENERGY);
    }

    public long energyCapacity() {
        return getLong(DATA_ENERGY_CAPACITY);
    }

    /** FE per tick the machine uses right now: 0 while it does not grow. */
    public long energyUse() {
        return getLong(DATA_ENERGY_USE);
    }

    /** FE per active plot per tick (config). */
    public long energyPerPlot() {
        return getLong(DATA_ENERGY_PER_PLOT);
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

    /** The replant button's look: ON, OFF, or DISABLED by the config. */
    public FarmMatrixBlockEntity.ReplantState replantState() {
        return FarmMatrixBlockEntity.ReplantState.byOrdinal(data.get(DATA_REPLANT));
    }

    // --- autocrafter ------------------------------------------------------------------------------------------------

    /** CRAFT: ON/OFF. */
    public boolean isCrafterEnabled() {
        return data.get(DATA_CRAFTER_ENABLED) != 0;
    }

    public int crafterRecipeCount() {
        return recipeResults.size();
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
        return index >= 0 && index < recipeResults.size() ? recipeResults.get(index) : ItemStack.EMPTY;
    }

    /** Client side: the recipe list sent by the server ({@link CrafterRecipesPayload}). */
    public void setRecipeResults(List<ItemStack> results) {
        recipeResults = List.copyOf(results);
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
        if (machine == null) {
            return false;
        }
        if (id == BUTTON_REPLANT) {
            machine.toggleReplant();
            return true;
        }
        MachineCrafter crafter = machine.crafter();
        if (crafter == null) {
            return false;
        }
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
     * accept them — plantables into the seed grid, soils into the soil grid, upgrades and the catalyst into their slots
     * — completing slots of the same item first; otherwise between inventory and hotbar. Never into the output buffer.
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
    private final class GridSlot extends HandlerSlot {
        GridSlot(MachineInventory inputs, int index, int x, int y) {
            super(inputs, index, x, y);
        }

        @Override
        public boolean isActive() {
            return !crafterVisible;
        }
    }

    /** The autocrafter's catalyst (a real item, e.g. the Master Infusion Crystal), shown in the crafter panel. */
    private final class CatalystSlot extends HandlerSlot {
        CatalystSlot(MachineInventory inputs, int index, int x, int y) {
            super(inputs, index, x, y);
        }

        @Override
        public boolean isActive() {
            return crafterVisible;
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
}
