/*
 * FarmMatrixMenu — the Farm Matrix container menu (both sides): slots at the owner's coordinates, shift-click rules,
 * throttled sync of the numbers the GUI shows, and the player's button intents (power, output faces) validated and
 * applied on the server.
 */
package com.virtualfarmworks.menu;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineFilter;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.OutputBuffer;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.registry.ModMenus;
import com.virtualfarmworks.sim.GrowthSpeed;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * Server-authoritative menu. The client only ever sends intents (vanilla slot clicks and {@link #clickMenuButton}
 * ids); the server applies them to the real {@link FarmMatrixBlockEntity}. Real quantities are never taken from the
 * client (owner rule).
 *
 * <h2>Slot order (menu indices)</h2>
 * {@code [0, 9)} machine inputs in {@link MachineSlots} order, {@code [9, 18)} output buffer, {@code [18, 45)} player
 * inventory, {@code [45, 54)} hotbar, {@code [54, 63)} harvest filter ghost slots (one 3x3 page).
 *
 * <h2>Synced numbers</h2>
 * Vanilla syncs {@link ContainerData} values as 16-bit shorts, so every value is scaled and clamped to fit (see
 * {@link #DATA_COUNT} indices). The server refreshes its copy only every {@link #SYNC_INTERVAL} ticks (owner rule:
 * throttled GUI sync) and immediately after a button press; vanilla then sends only the values that changed.
 */
public class FarmMatrixMenu extends AbstractContainerMenu {
    public static final int INPUT_START = 0;
    public static final int OUTPUT_START = INPUT_START + MachineSlots.INPUT_COUNT;
    public static final int PLAYER_START = OUTPUT_START + MachineSlots.OUTPUT_COUNT;
    public static final int HOTBAR_START = PLAYER_START + 27;
    public static final int PLAYER_END = HOTBAR_START + 9;
    /** Harvest filter ghost slots: one 9x9 page, after the player slots so no vanilla range ever reaches them. */
    public static final int FILTER_START = PLAYER_END;
    public static final int FILTER_END = FILTER_START + MachineFilter.PAGE_SIZE;

    /** Button ids sent by the client (vanilla ServerboundContainerButtonClickPacket). */
    public static final int BUTTON_POWER = 0;
    /** Face toggles use {@code BUTTON_FACE_FIRST + RelativeSide.ordinal()} (ids 1..6). */
    public static final int BUTTON_FACE_FIRST = 1;
    /** Fertilized Essence switch. */
    public static final int BUTTON_FERTILIZED = BUTTON_FACE_FIRST + 6;
    /** Harvest filter: switch WHITELIST / BLACKLIST, previous page, next page. */
    public static final int BUTTON_FILTER_MODE = BUTTON_FERTILIZED + 1;
    public static final int BUTTON_FILTER_PREVIOUS = BUTTON_FILTER_MODE + 1;
    public static final int BUTTON_FILTER_NEXT = BUTTON_FILTER_PREVIOUS + 1;

    // ContainerData indices.
    private static final int DATA_STATUS = 0;
    private static final int DATA_PROGRESS = 1;   // 0..10000 (1/100 of a percent)
    private static final int DATA_HYDRATION = 2;  // x100
    private static final int DATA_GROWTH = 3;     // x100 (growth upgrades x soil bonus)
    private static final int DATA_PLOTS = 4;
    private static final int DATA_ENABLED = 5;    // 0 / 1
    private static final int DATA_FACES = 6;      // RelativeSide bit mask
    private static final int DATA_FERTILIZED = 7; // 0 / 1
    private static final int DATA_HARVESTS = 8;   // completed cycles, mod 32768 (only changes matter)
    private static final int DATA_FILTER_MODE = 9;   // 0 = blacklist, 1 = whitelist
    private static final int DATA_FILTER_PAGE = 10;  // page this menu shows (0-based)
    private static final int DATA_FILTER_PAGES = 11; // pages to show: last page with entries or current page, + 1
    private static final int DATA_COUNT = 12;

    private static final int SYNC_INTERVAL = 5;

    private final MachineTier tier;
    private final ContainerLevelAccess access;
    private final @Nullable Block block;
    /** Server side only: the real machine. Null on the client. */
    private final @Nullable FarmMatrixBlockEntity machine;
    private final ContainerData data;
    private int ticksUntilSync;
    /** Client side: every synced value has arrived at least once (see {@link #isDataSynced()}). */
    private boolean dataSynced;
    /** Server side: the harvest filter page this player is looking at (each viewer browses on their own). */
    private int filterPage;
    /** Client side: whether the filter box is open; its ghost slots are only active (drawn, clickable) then. */
    private boolean filterVisible;

    /** Server constructor: backed by the real machine. */
    public FarmMatrixMenu(int containerId, Inventory playerInventory, FarmMatrixBlockEntity machine) {
        this(containerId, playerInventory, machine.tier(), machine.inputs(), machine.output(), machine,
                ContainerLevelAccess.create(machine.getLevel(), machine.getBlockPos()), machine.getBlockState().getBlock());
        refreshData();
    }

    /**
     * Client constructor, from the open-menu packet written by {@code FarmMatrixBlock#useWithoutItem} (block position
     * and tier). The client gets local mirror inventories that vanilla slot sync keeps up to date.
     */
    public static FarmMatrixMenu fromNetwork(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        buf.readBlockPos(); // position, reserved for client-side lookups; not needed yet
        MachineTier tier = MachineTier.values()[Math.clamp(buf.readVarInt(), 0, MachineTier.values().length - 1)];
        return new FarmMatrixMenu(containerId, playerInventory, tier, new MachineInventory(tier, () -> {
        }), new OutputBuffer(() -> {
        }), null, ContainerLevelAccess.NULL, null);
    }

    /** Writes what {@link #fromNetwork} reads. */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, MachineTier tier) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(tier.ordinal());
    }

    private FarmMatrixMenu(int containerId, Inventory playerInventory, MachineTier tier, MachineInventory inputs,
                           OutputBuffer output, @Nullable FarmMatrixBlockEntity machine, ContainerLevelAccess access,
                           @Nullable Block block) {
        super(ModMenus.FARM_MATRIX.get(), containerId);
        this.tier = tier;
        this.machine = machine;
        this.access = access;
        this.block = block;
        this.data = new SimpleContainerData(DATA_COUNT);

        // Inputs, in MachineSlots order: 4 slots on the texture, then the side column (4 growth + crux).
        for (int i = 0; i < 4; i++) {
            addSlot(new ResourceHandlerSlot(inputs, inputs::set, i, FarmMatrixLayout.TOP_SLOT_X[i],
                    FarmMatrixLayout.TOP_SLOT_Y));
        }
        for (int i = 0; i < FarmMatrixLayout.UPGRADE_SLOTS; i++) {
            addSlot(new ResourceHandlerSlot(inputs, inputs::set, MachineSlots.GROWTH_FIRST + i,
                    FarmMatrixLayout.PANEL_INTERIOR_X, FarmMatrixLayout.upgradeSlotY(i)));
        }
        // Output buffer: a plain inventory for the player (owner revision, step 8: take AND put items by hand).
        // Automation still only extracts (the capability is OutputBuffer#externalView), and shift-click never fills
        // it (see quickMoveStack). Items the harvest filter rejects are removed by the machine.
        for (int i = 0; i < MachineSlots.OUTPUT_COUNT; i++) {
            addSlot(new ResourceHandlerSlot(output, output::set, i,
                    FarmMatrixLayout.OUTPUT_X + i * FarmMatrixLayout.SLOT_SPACING, FarmMatrixLayout.OUTPUT_Y));
        }
        addStandardInventorySlots(playerInventory, FarmMatrixLayout.PLAYER_INVENTORY_X,
                FarmMatrixLayout.PLAYER_INVENTORY_Y);
        // Harvest filter: 9 ghost slots showing one page. Server: a view of the machine's filter at this menu's page.
        // Client: a plain mirror that vanilla slot sync fills.
        Container filterPageContainer = machine != null
                ? new FilterPageView(machine.filter(), () -> filterPage)
                : new SimpleContainer(MachineFilter.PAGE_SIZE);
        for (int i = 0; i < MachineFilter.PAGE_SIZE; i++) {
            addSlot(new FilterSlot(filterPageContainer, i, FarmMatrixLayout.filterSlotX(i),
                    FarmMatrixLayout.filterSlotY(i)));
        }
        addDataSlots(data);
    }

    // --- sync -------------------------------------------------------------------------------------------------------

    @Override
    public void broadcastChanges() {
        if (machine != null && --ticksUntilSync <= 0) {
            refreshData();
        }
        super.broadcastChanges();
    }

    /** Copies the machine's current numbers into the synced data (server only). */
    private void refreshData() {
        if (machine == null) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        GrowthSpeed speed = machine.speed();
        data.set(DATA_STATUS, machine.status().ordinal());
        data.set(DATA_PROGRESS, toShort(Math.round(machine.progress() * 10_000)));
        data.set(DATA_HYDRATION, toShort(Math.round(speed.hydration() * 100)));
        data.set(DATA_GROWTH, toShort(Math.round(speed.upgrades() * speed.soil() * 100)));
        data.set(DATA_PLOTS, toShort(machine.totalPlots()));
        data.set(DATA_ENABLED, machine.isEnabled() ? 1 : 0);
        data.set(DATA_FACES, machine.outputFaces());
        data.set(DATA_FERTILIZED, machine.isFertilizedEssenceEnabled() ? 1 : 0);
        data.set(DATA_HARVESTS, machine.completedHarvests() & 0x7FFF); // stays a positive short
        MachineFilter filter = machine.filter();
        data.set(DATA_FILTER_MODE, filter.isWhitelist() ? 1 : 0);
        data.set(DATA_FILTER_PAGE, filterPage);
        data.set(DATA_FILTER_PAGES, Math.max(filter.lastUsedPage(), filterPage) + 1);
    }

    private static int toShort(long value) {
        return (int) Math.clamp(value, 0, Short.MAX_VALUE);
    }

    // --- values for the screen (client reads the synced copy) ------------------------------------------------------

    public MachineTier tier() {
        return tier;
    }

    public MachineStatus status() {
        return MachineStatus.byOrdinal(data.get(DATA_STATUS));
    }

    /** Cycle progress, 0.0..1.0. */
    public double progress() {
        return Math.clamp(data.get(DATA_PROGRESS) / 10_000.0, 0.0, 1.0);
    }

    public double hydrationMultiplier() {
        return data.get(DATA_HYDRATION) / 100.0;
    }

    /** Growth upgrades x soil bonus (the "Growth" line of the GUI). */
    public double growthMultiplier() {
        return data.get(DATA_GROWTH) / 100.0;
    }

    public int plots() {
        return data.get(DATA_PLOTS);
    }

    public boolean isEnabled() {
        return data.get(DATA_ENABLED) != 0;
    }

    public boolean isOutputEnabled(RelativeSide side) {
        return (data.get(DATA_FACES) & side.bit()) != 0;
    }

    /** Completed cycles, mod 32768: a change means the bar wrapped (see {@code client.SmoothProgress}). */
    public int harvestCount() {
        return data.get(DATA_HARVESTS);
    }

    /**
     * Client side: whether the server's values have arrived. Right after the GUI opens the data can still be the
     * defaults (0) for a frame or two; the smooth bar must not start from that 0 (owner report: the bar raced from 1%
     * to the real value on every open).
     */
    public boolean isDataSynced() {
        return dataSynced;
    }

    /**
     * Client side: called for every value the server sends (vanilla ClientboundContainerSetDataPacket). When a menu
     * opens, vanilla sends EVERY value in index order (ServerPlayer's sendInitialData), so receiving the last index
     * means all of them arrived — progress and harvest counter together, which the smooth bar needs.
     */
    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        if (id == DATA_COUNT - 1) {
            dataSynced = true;
        }
    }

    public boolean isFertilizedEssenceEnabled() {
        return data.get(DATA_FERTILIZED) != 0;
    }

    /** Harvest filter mode: true = WHITELISTED, false = BLACKLISTED. */
    public boolean isFilterWhitelist() {
        return data.get(DATA_FILTER_MODE) != 0;
    }

    /** Filter page shown, 0-based. */
    public int filterPage() {
        return data.get(DATA_FILTER_PAGE);
    }

    /** Filter pages to show: up to the last page holding an item, or the current page if it is further. */
    public int filterPageCount() {
        return Math.max(1, data.get(DATA_FILTER_PAGES));
    }

    /** Client side: the screen opens/closes the filter box; its ghost slots are active only while it is open. */
    public void setFilterVisible(boolean visible) {
        filterVisible = visible;
    }

    /** Whether a menu slot index is one of the filter's ghost slots. */
    public static boolean isFilterSlot(int slotIndex) {
        return slotIndex >= FILTER_START && slotIndex < FILTER_END;
    }

    // --- harvest filter ghost slots ---------------------------------------------------------------------------------

    /**
     * Vanilla slot clicks on the ghost slots never move real items. Both sides run this (the client predicts, the
     * server decides and corrects the client when it disagrees, e.g. a duplicate refused): with an item on the cursor
     * the slot records its type and the cursor stack is untouched; with an empty cursor, or shift-click, it is cleared.
     * Every other input (number keys, middle click, Q, drag) does nothing.
     */
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (!isFilterSlot(slotIndex)) {
            super.clicked(slotIndex, buttonNum, input, player);
            return;
        }
        Slot slot = slots.get(slotIndex);
        if (input == ContainerInput.QUICK_MOVE) {
            slot.set(ItemStack.EMPTY);
        } else if (input == ContainerInput.PICKUP) {
            ItemStack carried = getCarried();
            slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
        }
    }

    /**
     * Server side: puts an item type in a ghost slot of the current page, for items dragged from JEI (the player
     * does not hold them; see {@code network.SetFilterGhostPayload}). Returns false for an invalid slot.
     */
    public boolean setFilterGhost(int filterSlot, ItemStack stack) {
        if (machine == null || filterSlot < 0 || filterSlot >= MachineFilter.PAGE_SIZE) {
            return false;
        }
        slots.get(FILTER_START + filterSlot).set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        broadcastChanges();
        return true;
    }

    /** A drag over several slots never includes ghost slots. */
    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof FilterSlot) && super.canDragTo(slot);
    }

    /** Double-click collecting never takes ghosts. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return !(target instanceof FilterSlot) && super.canTakeItemForPickAll(carried, target);
    }

    // --- player intents ---------------------------------------------------------------------------------------------

    /**
     * Server side: a GUI button was pressed. Vanilla calls this only for the player who has this menu open and only
     * while {@link #stillValid} holds. Unknown ids are ignored.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine == null) {
            return false;
        }
        if (id == BUTTON_POWER) {
            machine.setEnabled(!machine.isEnabled());
        } else if (id >= BUTTON_FACE_FIRST && id < BUTTON_FACE_FIRST + RelativeSide.values().length) {
            machine.toggleOutput(RelativeSide.values()[id - BUTTON_FACE_FIRST]);
        } else if (id == BUTTON_FERTILIZED) {
            machine.toggleFertilizedEssence();
        } else if (id == BUTTON_FILTER_MODE) {
            machine.filter().toggleMode();
        } else if (id == BUTTON_FILTER_PREVIOUS) {
            filterPage = Math.max(0, filterPage - 1);
        } else if (id == BUTTON_FILTER_NEXT) {
            // Pages are created on demand (owner spec): going forward always works, up to the cap.
            filterPage = Math.min(MachineFilter.MAX_PAGES - 1, filterPage + 1);
        } else {
            return false;
        }
        refreshData();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return block == null || stillValid(access, player, block);
    }

    /**
     * Shift-click. Machine slots go to the player's inventory; player items go to the first machine input slot that
     * accepts them (the slots' own rules decide), otherwise between inventory and hotbar. Never into the output buffer,
     * even though players may put items there by clicking: extra seeds shift-clicked at a full seed slot would
     * otherwise land in the output and be auto-exported away.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (isFilterSlot(index) || !slot.hasItem()) {
            return ItemStack.EMPTY; // ghosts are not items (clicked() handles shift-click on them)
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, INPUT_START, OUTPUT_START, false)) {
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

    /**
     * Harvest filter ghost slot: shows an item type of the current page. Real items never go in or out (all clicks go
     * through {@link #clicked}); active, i.e. drawn and hoverable, only while the client's filter box is open.
     */
    private final class FilterSlot extends Slot {
        FilterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return filterVisible;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
