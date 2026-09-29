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

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Covers the grid slots {@code [0, 2 x groups)} of the machine's {@link MachineInventory}: index-based insertion goes to
 * that slot (with its own rules), extraction is always refused (pipes never take seeds or soils out).
 *
 * <h2>Routing of index-less insertion (what most pipes call)</h2>
 * A plantable fills, in order: seed slots already holding it; empty seed slots whose soil can grow it; empty seed slots
 * whose soil slot is empty too; any other empty seed slot. A soil fills the soil grid the same way (slots already
 * holding it; empty soil slots whose seed grows on it; empty soil slots whose seed slot is empty; the rest). Anything
 * else is refused. So seeds and soils piped from two chests end up paired where they work.
 *
 * <p>Cost: a pipe may knock every tick. Filling slots that already hold the item is a plain loop; the pairing checks
 * only run when an empty slot is needed, and their answers are cached per (plant, soil) pair until tags reload.
 */
public final class GridInput implements ResourceHandler<ItemResource> {
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
    public int size() {
        return 2 * layout.groups();
    }

    @Override
    public ItemResource getResource(int index) {
        return inputs.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return inputs.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return inputs.getCapacityAsLong(index, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return inputs.isValid(index, resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return index >= 0 && index < size() ? inputs.insert(index, resource, amount, transaction) : 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        return 0;
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        if (resource.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemStack stack = resource.toStack();
        boolean seed = PlantRules.isPlantable(stack);
        if (!seed && !SoilRules.isAcceptableSoil(stack)) {
            return 0;
        }
        int inserted = 0;
        // 1. Slots already holding this item.
        for (int group = 0; group < layout.groups() && inserted < amount; group++) {
            int slot = seed ? layout.seedSlot(group) : layout.soilSlot(group);
            if (inputs.getResource(slot).equals(resource)) {
                inserted += inputs.insert(slot, resource, amount - inserted, transaction);
            }
        }
        // 2-4. Empty slots, best partner first.
        for (int pass = 0; pass < 3 && inserted < amount; pass++) {
            for (int group = 0; group < layout.groups() && inserted < amount; group++) {
                int slot = seed ? layout.seedSlot(group) : layout.soilSlot(group);
                if (!inputs.getResource(slot).isEmpty()) {
                    continue;
                }
                int partnerSlot = seed ? layout.soilSlot(group) : layout.seedSlot(group);
                ItemResource partner = inputs.getResource(partnerSlot);
                boolean take = switch (pass) {
                    case 0 -> !partner.isEmpty() && (seed ? grows(resource, partner) : grows(partner, resource));
                    case 1 -> partner.isEmpty();
                    default -> true;
                };
                if (take) {
                    inserted += inputs.insert(slot, resource, amount - inserted, transaction);
                }
            }
        }
        return inserted;
    }

    /** Whether {@code plant} grows on {@code soil} here, directly or after tilling (cached until tags reload). */
    private boolean grows(ItemResource plant, ItemResource soil) {
        if (pairCacheGeneration != SoilRules.cacheGeneration()) {
            pairCache.clear();
            pairCacheGeneration = SoilRules.cacheGeneration();
        }
        long key = ((long) Item.getId(plant.getItem()) << 32) | (Item.getId(soil.getItem()) & 0xFFFFFFFFL);
        return pairCache.computeIfAbsent(key, k -> computeGrows(plant.toStack(), soil.toStack()));
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
