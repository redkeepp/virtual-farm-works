/*
 * GridInput — what a pipe sees on a face set to INPUT (owner's Entropic spec): an insert-only view of the seed and soil
 * grids that routes plantables to the seed grid and soils to the soil grid, preferring slots whose other half of the
 * plot group matches, so a chest of seeds and a chest of soils piped in build working plot groups.
 */
package com.virtualfarmworks.machine;

import java.util.HashMap;
import java.util.Map;

import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.plant.SoilRules;
import com.virtualfarmworks.transfer.ItemResource;
import com.virtualfarmworks.transfer.SlotTransaction;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Shows the grid slots {@code [0, 2 x groups)} of the machine's {@link MachineInventory}; extraction is always refused
 * (pipes never take seeds or soils out).
 *
 * <h2>Routing</h2>
 * NeoForge 1.21.1's item handlers have no index-less insertion (26.1's {@code insert(resource, amount)}, which this
 * class routed): pipes and hoppers insert slot by slot. So EVERY insertion is routed here, whatever slot the caller
 * names: the stack goes where the routing puts it and the caller gets back what is left, which is all a pipe looks at.
 * A plantable fills, in order: seed slots already holding it; empty seed slots whose soil can grow it; empty seed slots
 * whose soil slot is empty too; any other empty seed slot. A soil fills the soil grid the same way (slots already
 * holding it; empty soil slots whose seed grows on it; empty soil slots whose seed slot is empty; the rest). Anything
 * else is refused. So seeds and soils piped from two chests end up paired where they work. Each slot keeps its own
 * rules (what it accepts, how many).
 *
 * <p>Simulated and real insertions give the same answer: both plan on a {@code SlotTransaction}, and only a real one
 * commits it.
 *
 * <p>Cost: a pipe may knock every tick, and a slot-by-slot pipe knocks once per slot it tries. Filling slots that
 * already hold the item is a plain loop; the pairing checks only run when an empty slot is needed, and their answers
 * are cached per (plant, soil) pair until tags reload.
 */
public final class GridInput implements IItemHandler {
    private final MachineInventory inputs;
    private final MachineLayout layout;
    /** (plantable item, soil item) -> the plant grows on the soil (possibly after tilling). */
    private final Map<Long, Boolean> pairCache = new HashMap<>();
    private int pairCacheGeneration = -1;

    public GridInput(MachineInventory inputs) {
        this.inputs = inputs;
        this.layout = inputs.layout();
    }

    @Override
    public int getSlots() {
        return 2 * layout.groups();
    }

    /** The grid slot's stack. Read only ({@code IItemHandler} contract). */
    @Override
    public ItemStack getStackInSlot(int index) {
        return index >= 0 && index < getSlots() ? inputs.getStackInSlot(index) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int index) {
        return index >= 0 && index < getSlots() ? inputs.getSlotLimit(index) : 0;
    }

    /** Whether the routing would take this item at all (a plantable or a soil); the slot named does not matter. */
    @Override
    public boolean isItemValid(int index, ItemStack stack) {
        return !stack.isEmpty() && (PlantRules.isPlantable(stack) || SoilRules.isAcceptableSoil(stack));
    }

    /** Routed: see the class doc. {@code index} is ignored. */
    @Override
    public ItemStack insertItem(int index, ItemStack stack, boolean simulate) {
        return insert(stack, simulate);
    }

    @Override
    public ItemStack extractItem(int index, int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    /**
     * Routes a stack into the grids (see the class doc).
     *
     * @return what did not fit (the caller keeps it); empty when everything went in
     */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        boolean seed = PlantRules.isPlantable(stack);
        if (!seed && !SoilRules.isAcceptableSoil(stack)) {
            return stack;
        }
        ItemResource resource = ItemResource.of(stack);
        int amount = stack.getCount();
        SlotTransaction transaction = new SlotTransaction();
        int inserted = 0;
        // 1. Slots already holding this item.
        for (int group = 0; group < layout.groups() && inserted < amount; group++) {
            int slot = seed ? layout.seedSlot(group) : layout.soilSlot(group);
            if (resource.matches(inputs.getStackInSlot(slot))) {
                inserted += transaction.insert(inputs, slot, resource, amount - inserted);
            }
        }
        // 2-4. Empty slots, best partner first.
        for (int pass = 0; pass < 3 && inserted < amount; pass++) {
            for (int group = 0; group < layout.groups() && inserted < amount; group++) {
                int slot = seed ? layout.seedSlot(group) : layout.soilSlot(group);
                if (!transaction.peek(inputs, slot).isEmpty()) {
                    continue;
                }
                int partnerSlot = seed ? layout.soilSlot(group) : layout.seedSlot(group);
                ItemStack partner = transaction.peek(inputs, partnerSlot);
                boolean take = switch (pass) {
                    case 0 -> !partner.isEmpty() && (seed ? grows(stack, partner) : grows(partner, stack));
                    case 1 -> partner.isEmpty();
                    default -> true;
                };
                if (take) {
                    inserted += transaction.insert(inputs, slot, resource, amount - inserted);
                }
            }
        }
        if (!simulate) {
            transaction.commit();
        }
        return inserted == amount ? ItemStack.EMPTY : stack.copyWithCount(amount - inserted);
    }

    /** Whether {@code plant} grows on {@code soil} here, directly or after tilling (cached until tags reload). */
    private boolean grows(ItemStack plant, ItemStack soil) {
        if (pairCacheGeneration != SoilRules.cacheGeneration()) {
            pairCache.clear();
            pairCacheGeneration = SoilRules.cacheGeneration();
        }
        long key = ((long) Item.getId(plant.getItem()) << 32) | (Item.getId(soil.getItem()) & 0xFFFFFFFFL);
        Boolean cached = pairCache.get(key);
        if (cached == null) {
            cached = computeGrows(plant, soil);
            pairCache.put(key, cached);
        }
        return cached;
    }

    private static boolean computeGrows(ItemStack plant, ItemStack soil) {
        Block plantBlock = PlantRules.plantBlock(plant);
        BlockState soilState = SoilRules.soilState(soil);
        if (plantBlock == null || soilState == null) {
            return false;
        }
        if (!PlantRules.needsSoil(plantBlock)) {
            return true;
        }
        BlockState plantState = plantBlock.defaultBlockState();
        return PlantRules.canGrowOn(plantState, soilState)
                || (SoilRules.isTillable(soil) && PlantRules.canGrowOn(plantState, SoilRules.TILLED_SOIL));
    }
}
