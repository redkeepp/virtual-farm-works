/*
 * AbstractFarmMatrixMenu — what every Farm Matrix menu shares (both sides): the machine link, throttled sync of the
 * numbers every GUI shows, the harvest filter's ghost slots, ghost-slot click handling, the output slot that tells the
 * machine about hand-placed items, and the player's common button intents (power, faces, Fertilized Essence, filter).
 */
package com.virtualfarmworks.menu;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineFilter;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.OutputBuffer;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.sim.GrowthSpeed;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * Server-authoritative menu base. The client only ever sends intents (vanilla slot clicks and
 * {@link #clickMenuButton} ids); the server applies them to the real {@link FarmMatrixBlockEntity}. Real quantities are
 * never taken from the client (owner rule).
 *
 * <h2>Synced numbers</h2>
 * Vanilla syncs {@link ContainerData} values as 16-bit shorts, so every value is scaled and clamped to fit. Indices
 * {@code [0, BASE_DATA_COUNT)} are shared by every tier (see the constants); tier menus append their own after them.
 * The server refreshes its copy only every {@link #SYNC_INTERVAL} ticks (owner rule: throttled GUI sync) and right after
 * a button press; vanilla then sends only the values that changed.
 *
 * <h2>Ghost slots</h2>
 * {@link GhostSlot}s show item TYPES (harvest filter, autocrafter grid). Every click on them goes through
 * {@link #clicked}: with an item on the cursor the slot records its type and the cursor stack is untouched; with an
 * empty cursor, or shift-click, it is cleared. Real items never move in or out.
 */
public abstract class AbstractFarmMatrixMenu extends AbstractContainerMenu {
    /** Button ids sent by the client (vanilla ServerboundContainerButtonClickPacket). */
    public static final int BUTTON_POWER = 0;
    /** Next face mode (click): {@code BUTTON_FACE_FIRST + RelativeSide.ordinal()} (ids 1..6). */
    public static final int BUTTON_FACE_FIRST = 1;
    /** Fertilized Essence switch. */
    public static final int BUTTON_FERTILIZED = BUTTON_FACE_FIRST + 6;
    /** Harvest filter: switch WHITELIST / BLACKLIST, previous page, next page. */
    public static final int BUTTON_FILTER_MODE = BUTTON_FERTILIZED + 1;
    public static final int BUTTON_FILTER_PREVIOUS = BUTTON_FILTER_MODE + 1;
    public static final int BUTTON_FILTER_NEXT = BUTTON_FILTER_PREVIOUS + 1;
    /** Previous face mode (right click): {@code BUTTON_FACE_BACK_FIRST + RelativeSide.ordinal()} (ids 11..16). */
    public static final int BUTTON_FACE_BACK_FIRST = BUTTON_FILTER_NEXT + 1;
    /** First id a tier menu may use for its own buttons. */
    protected static final int BUTTON_TIER_FIRST = BUTTON_FACE_BACK_FIRST + 6;

    // Shared ContainerData indices.
    protected static final int DATA_STATUS = 0;
    protected static final int DATA_PROGRESS = 1;   // 0..10000 (1/100 of a percent)
    protected static final int DATA_HYDRATION = 2;  // x100
    protected static final int DATA_GROWTH = 3;     // x100 (growth upgrades x soil bonus)
    protected static final int DATA_PLOTS = 4;
    protected static final int DATA_ENABLED = 5;    // 0 / 1
    protected static final int DATA_FACES = 6;      // RelativeSide bit mask of exporting faces
    protected static final int DATA_FERTILIZED = 7; // 0 / 1
    protected static final int DATA_HARVESTS = 8;   // completed cycles, mod 32768 (only changes matter)
    protected static final int DATA_FILTER_MODE = 9;   // 0 = blacklist, 1 = whitelist
    protected static final int DATA_FILTER_PAGE = 10;  // page this menu shows (0-based)
    protected static final int DATA_FILTER_PAGES = 11; // pages to show: last page with entries or current page, + 1
    protected static final int BASE_DATA_COUNT = 12;

    private static final int SYNC_INTERVAL = 5;

    protected final MachineTier tier;
    private final ContainerLevelAccess access;
    private final @Nullable Block block;
    /** Server side only: the real machine. Null on the client. */
    protected final @Nullable FarmMatrixBlockEntity machine;
    protected final ContainerData data;
    private int ticksUntilSync;
    /** Client side: every synced value has arrived at least once (see {@link #isDataSynced()}). */
    private boolean dataSynced;
    /** Server side: the harvest filter page this player is looking at (each viewer browses on their own). */
    protected int filterPage;
    /** Client side: whether the filter box is open; its ghost slots are only active (drawn, clickable) then. */
    private boolean filterVisible;
    /** Menu index of the first filter ghost slot, -1 before {@link #addFilterSlots}. */
    private int filterStart = -1;

    protected AbstractFarmMatrixMenu(MenuType<?> type, int containerId, MachineTier tier,
                                     @Nullable FarmMatrixBlockEntity machine, ContainerLevelAccess access,
                                     @Nullable Block block, int dataCount) {
        super(type, containerId);
        this.tier = tier;
        this.machine = machine;
        this.access = access;
        this.block = block;
        this.data = new SimpleContainerData(dataCount);
    }

    /** Writes the open-menu packet data (position and tier) the client constructors read. */
    public static void writeOpenData(RegistryFriendlyByteBuf buf, BlockPos pos, MachineTier tier) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(tier.ordinal());
    }

    /** Reads what {@link #writeOpenData} wrote: the tier (the position is reserved for client-side lookups). */
    protected static MachineTier readOpenData(RegistryFriendlyByteBuf buf) {
        buf.readBlockPos();
        return MachineTier.values()[Math.clamp(buf.readVarInt(), 0, MachineTier.values().length - 1)];
    }

    /**
     * Adds the harvest filter's 9 ghost slots (one page). Server: a view of the machine's filter at this menu's page.
     * Client: a plain mirror that vanilla slot sync fills.
     */
    protected void addFilterSlots(java.util.function.IntUnaryOperator x, java.util.function.IntUnaryOperator y) {
        Container page = machine != null
                ? new FilterPageView(machine.filter(), () -> filterPage)
                : new SimpleContainer(MachineFilter.PAGE_SIZE);
        filterStart = slots.size();
        for (int i = 0; i < MachineFilter.PAGE_SIZE; i++) {
            addSlot(new FilterSlot(page, i, x.applyAsInt(i), y.applyAsInt(i)));
        }
    }

    /** Menu index of the first filter ghost slot. */
    public int filterSlotStart() {
        return filterStart;
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
    protected final void refreshData() {
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
        refreshTierData(machine);
    }

    /** Tier menus fill their own data indices here (server side). */
    protected void refreshTierData(FarmMatrixBlockEntity machine) {
    }

    protected static int toShort(long value) {
        return (int) Math.clamp(value, 0, Short.MAX_VALUE);
    }

    /** Writes a value up to 2^30 - 1 into two data indices (15 bits each: shorts must stay positive). */
    protected void setLong(int index, long value) {
        long clamped = Math.clamp(value, 0, (1L << 30) - 1);
        data.set(index, (int) (clamped >>> 15));
        data.set(index + 1, (int) (clamped & 0x7FFF));
    }

    protected long getLong(int index) {
        return ((long) (data.get(index) & 0x7FFF) << 15) | (data.get(index + 1) & 0x7FFF);
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
        if (id == data.getCount() - 1) {
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
    public boolean isFilterSlotIndex(int slotIndex) {
        return filterStart >= 0 && slotIndex >= filterStart && slotIndex < filterStart + MachineFilter.PAGE_SIZE;
    }

    // --- ghost slots ------------------------------------------------------------------------------------------------

    /**
     * Vanilla slot clicks on ghost slots never move real items. Both sides run this (the client predicts, the server
     * decides and corrects the client when it disagrees, e.g. a duplicate refused): with an item on the cursor the slot
     * records its type and the cursor stack is untouched; with an empty cursor, or shift-click, it is cleared. Every
     * other input (number keys, middle click, Q, drag) does nothing.
     */
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex < 0 || slotIndex >= slots.size() || !(slots.get(slotIndex) instanceof GhostSlot slot)) {
            super.clicked(slotIndex, buttonNum, input, player);
            return;
        }
        if (input == ContainerInput.QUICK_MOVE) {
            slot.set(ItemStack.EMPTY);
        } else if (input == ContainerInput.PICKUP) {
            ItemStack carried = getCarried();
            slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
        }
    }

    /**
     * Server side: puts an item type in a ghost slot of the current filter page, for items dragged from JEI (the
     * player does not hold them; see {@code network.SetFilterGhostPayload}). Returns false for an invalid slot.
     */
    public boolean setFilterGhost(int filterSlot, ItemStack stack) {
        if (machine == null || filterStart < 0 || filterSlot < 0 || filterSlot >= MachineFilter.PAGE_SIZE) {
            return false;
        }
        slots.get(filterStart + filterSlot).set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        broadcastChanges();
        return true;
    }

    /** A drag over several slots never includes ghost slots. */
    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof GhostSlot) && super.canDragTo(slot);
    }

    /** Double-click collecting never takes ghosts. */
    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return !(target instanceof GhostSlot) && super.canTakeItemForPickAll(carried, target);
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
        int sides = RelativeSide.values().length;
        if (id == BUTTON_POWER) {
            machine.setEnabled(!machine.isEnabled());
        } else if (id >= BUTTON_FACE_FIRST && id < BUTTON_FACE_FIRST + sides) {
            machine.cycleFaceMode(RelativeSide.values()[id - BUTTON_FACE_FIRST], true);
        } else if (id >= BUTTON_FACE_BACK_FIRST && id < BUTTON_FACE_BACK_FIRST + sides) {
            machine.cycleFaceMode(RelativeSide.values()[id - BUTTON_FACE_BACK_FIRST], false);
        } else if (id == BUTTON_FERTILIZED) {
            machine.toggleFertilizedEssence();
        } else if (id == BUTTON_FILTER_MODE) {
            machine.filter().toggleMode();
        } else if (id == BUTTON_FILTER_PREVIOUS) {
            filterPage = Math.max(0, filterPage - 1);
        } else if (id == BUTTON_FILTER_NEXT) {
            // Pages are created on demand (owner spec): going forward always works, up to the cap.
            filterPage = Math.min(MachineFilter.MAX_PAGES - 1, filterPage + 1);
        } else if (!clickTierButton(player, id)) {
            return false;
        }
        refreshData();
        return true;
    }

    /** Tier menus handle their own button ids ({@code >= BUTTON_TIER_FIRST}); false = unknown id. */
    protected boolean clickTierButton(Player player, int id) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return block == null || stillValid(access, player, block);
    }

    /** A slot showing an item TYPE, never holding a real item; all clicks go through {@link #clicked}. */
    public abstract static class GhostSlot extends Slot {
        protected GhostSlot(Container container, int index, int x, int y) {
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
        public int getMaxStackSize() {
            return 1;
        }
    }

    /**
     * Harvest filter ghost slot: shows an item type of the current page; active, i.e. drawn and hoverable, only while
     * the client's filter box is open.
     */
    private final class FilterSlot extends GhostSlot {
        FilterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return filterVisible;
        }
    }

    /**
     * Output buffer slot: players take and put items (owner revision, step 8). Vanilla routes every player placement
     * (click, drag, number keys) through {@code setByPlayer}; when an item goes in, the machine is asked to delete it
     * next tick if the harvest filter rejects it. This keeps the filter cleanup off every other output change.
     */
    protected static class OutputSlot extends ResourceHandlerSlot {
        private final Runnable onPlayerPut;

        public OutputSlot(OutputBuffer output, int index, int x, int y, Runnable onPlayerPut) {
            super(output, output::set, index, x, y);
            this.onPlayerPut = onPlayerPut;
        }

        @Override
        public void setByPlayer(ItemStack stack, ItemStack previous) {
            super.setByPlayer(stack, previous);
            if (!stack.isEmpty()) {
                onPlayerPut.run();
            }
        }
    }
}
