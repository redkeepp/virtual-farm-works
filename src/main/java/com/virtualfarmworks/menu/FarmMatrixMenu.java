/*
 * FarmMatrixMenu — the Starter Farm Matrix container menu (both sides): slots at the owner's coordinates and the
 * shift-click rules. Sync, ghost slots and button intents are shared with the other tiers (AbstractFarmMatrixMenu).
 */
package com.virtualfarmworks.menu;

import org.jetbrains.annotations.Nullable;

import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineFilter;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.OutputBuffer;
import com.virtualfarmworks.registry.ModMenus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * The Starter's menu.
 *
 * <h2>Slot order (menu indices)</h2>
 * {@code [0, 9)} machine inputs in {@link MachineSlots} order, {@code [9, 18)} output buffer, {@code [18, 45)} player
 * inventory, {@code [45, 54)} hotbar, {@code [54, 63)} harvest filter ghost slots (one 3x3 page).
 *
 * <h2>Synced numbers</h2>
 * Only the shared ones ({@link AbstractFarmMatrixMenu}): status, progress, hydration, growth, plots, on/off, faces,
 * Fertilized Essence, harvest counter, filter mode/page/pages.
 */
public class FarmMatrixMenu extends AbstractFarmMatrixMenu {
    public static final int INPUT_START = 0;
    public static final int OUTPUT_START = INPUT_START + MachineSlots.INPUT_COUNT;
    public static final int PLAYER_START = OUTPUT_START + MachineSlots.OUTPUT_COUNT;
    public static final int HOTBAR_START = PLAYER_START + 27;
    public static final int PLAYER_END = HOTBAR_START + 9;
    /** Harvest filter ghost slots: one 3x3 page, after the player slots so no vanilla range ever reaches them. */
    public static final int FILTER_START = PLAYER_END;
    public static final int FILTER_END = FILTER_START + MachineFilter.PAGE_SIZE;

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
        MachineTier tier = readOpenData(buf);
        return new FarmMatrixMenu(containerId, playerInventory, tier, new MachineInventory(tier, () -> {
        }), new OutputBuffer(MachineSlots.OUTPUT_COUNT, () -> {
        }), null, ContainerLevelAccess.NULL, null);
    }

    private FarmMatrixMenu(int containerId, Inventory playerInventory, MachineTier tier, MachineInventory inputs,
                           OutputBuffer output, @Nullable FarmMatrixBlockEntity machine, ContainerLevelAccess access,
                           @Nullable Block block) {
        super(ModMenus.FARM_MATRIX.get(), containerId, tier, machine, access, block, BASE_DATA_COUNT);

        // Inputs, in MachineSlots order: 4 slots on the texture, then the side column (4 growth + crux).
        for (int i = 0; i < 4; i++) {
            addSlot(new HandlerSlot(inputs, i, FarmMatrixLayout.TOP_SLOT_X[i], FarmMatrixLayout.TOP_SLOT_Y));
        }
        for (int i = 0; i < FarmMatrixLayout.UPGRADE_SLOTS; i++) {
            addSlot(new HandlerSlot(inputs, MachineSlots.GROWTH_FIRST + i, FarmMatrixLayout.PANEL_INTERIOR_X,
                    FarmMatrixLayout.upgradeSlotY(i)));
        }
        // Output buffer: a plain inventory for the player (owner revision, step 8: take AND put items by hand).
        // Automation still only extracts (the capability is extract-only), and shift-click never fills it (see
        // quickMoveStack). Items the harvest filter rejects are removed by the machine (see OutputSlot).
        Runnable onPlayerPut = machine != null ? machine::requestFilterPurge : () -> {
        };
        for (int i = 0; i < MachineSlots.OUTPUT_COUNT; i++) {
            addSlot(new OutputSlot(output, i, FarmMatrixLayout.OUTPUT_X + i * FarmMatrixLayout.SLOT_SPACING,
                    FarmMatrixLayout.OUTPUT_Y, onPlayerPut));
        }
        addPlayerInventorySlots(playerInventory, FarmMatrixLayout.PLAYER_INVENTORY_X,
                FarmMatrixLayout.PLAYER_INVENTORY_Y);
        addFilterSlots(FarmMatrixLayout::filterSlotX, FarmMatrixLayout::filterSlotY);
        addDataSlots(data);
    }

    /** Whether a menu slot index is one of the filter's ghost slots. */
    public static boolean isFilterSlot(int slotIndex) {
        return slotIndex >= FILTER_START && slotIndex < FILTER_END;
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
}
