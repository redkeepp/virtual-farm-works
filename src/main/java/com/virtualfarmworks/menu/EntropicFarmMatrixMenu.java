/*
 * EntropicFarmMatrixMenu — the Entropic Farm Matrix container menu (both sides): the two 4x15 grids, water provider,
 * side-column upgrades and hoe, 24 output slots, harvest filter ghost slots, and the numbers only this tier shows
 * (energy, plot capacity, waiting plots, face modes, each plot group's status).
 */
package com.virtualfarmworks.menu;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.machine.FaceMode;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineEnergy;
import com.virtualfarmworks.machine.MachineInventory;
import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.OutputBuffer;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.registry.ModMenus;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

/**
 * The Entropic's menu.
 *
 * <h2>Slot order (menu indices)</h2>
 * {@code [0, 127)} machine inputs in inventory order ({@link MachineLayout}: 60 seed slots, 60 soil slots, water
 * provider, hoe, 4 growth, crux), {@code [127, 151)} output buffer, {@code [151, 178)} player inventory,
 * {@code [178, 187)} hotbar, {@code [187, 196)} harvest filter ghost slots.
 *
 * <h2>Synced numbers (after the shared ones)</h2>
 * Energy, capacity and use per tick; planted plots, plot capacity and waiting plots (two shorts each, up to 2^30);
 * the mode of each face; the status of each plot group (three groups per short, 4 bits each).
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

    private static final int DATA_ENERGY = BASE_DATA_COUNT;          // 2 shorts
    private static final int DATA_ENERGY_CAPACITY = DATA_ENERGY + 2;  // 2 shorts
    private static final int DATA_ENERGY_USE = DATA_ENERGY_CAPACITY + 2;
    private static final int DATA_PLANTED = DATA_ENERGY_USE + 2;
    private static final int DATA_CAPACITY = DATA_PLANTED + 2;
    private static final int DATA_WAITING = DATA_CAPACITY + 2;
    private static final int DATA_FACE_MODES = DATA_WAITING + 2;      // one per side
    private static final int DATA_GROUP_STATUS = DATA_FACE_MODES + RelativeSide.values().length;
    private static final int GROUPS_PER_SHORT = 3;
    private static final int DATA_COUNT = DATA_GROUP_STATUS + (LAYOUT.groups() + GROUPS_PER_SHORT - 1) / GROUPS_PER_SHORT;

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

        for (int group = 0; group < LAYOUT.groups(); group++) {
            addSlot(new ResourceHandlerSlot(inputs, inputs::set, LAYOUT.seedSlot(group),
                    EntropicLayout.seedSlotX(group), EntropicLayout.seedSlotY(group)));
        }
        for (int group = 0; group < LAYOUT.groups(); group++) {
            addSlot(new ResourceHandlerSlot(inputs, inputs::set, LAYOUT.soilSlot(group),
                    EntropicLayout.seedSlotX(group), EntropicLayout.soilSlotY(group)));
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
        addDataSlots(data);
    }

    // --- sync -------------------------------------------------------------------------------------------------------

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

    // --- shift-click ------------------------------------------------------------------------------------------------

    /**
     * Shift-click. Machine slots go to the player's inventory. Player items go into the machine where their slot rules
     * accept them — plantables into the seed grid, soils into the soil grid, upgrades and the hoe into their slots —
     * completing slots of the same item first; otherwise between inventory and hotbar. Never into the output buffer.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot instanceof GhostSlot || !slot.hasItem()) {
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
}
