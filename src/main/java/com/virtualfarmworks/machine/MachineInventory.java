/*
 * MachineInventory — the input inventory of a Farm Matrix (seed, soil, water provider, hoe, growth upgrades, crux):
 * what each slot accepts and how many items it holds. Validation runs on both client (menu) and server.
 */
package com.virtualfarmworks.machine;

import com.virtualfarmworks.config.VfwConfig;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.item.CruxProviderUpgradeItem;
import com.virtualfarmworks.item.TieredUpgradeItem;
import com.virtualfarmworks.item.UpgradeType;
import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.plant.SoilRules;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Transaction-aware item storage for the machine's inputs, built on NeoForge's {@link ItemStacksResourceHandler}.
 *
 * <p>Slot rules (owner spec, {@code docs/specs/starter-farm-matrix.md}):
 * <ul>
 *   <li>seed: plantable (see {@code PlantRules}) and not blacklisted for this tier; up to 64;</li>
 *   <li>soil: a soil (see {@code SoilRules}) and not blacklisted for this tier; up to 64;</li>
 *   <li>water provider: a Water Provider Upgrade that fits this tier; 1;</li>
 *   <li>hoe: any hoe, any tier, damaged or not; 1;</li>
 *   <li>growth x4: Growth Speed Upgrades that fit this tier; {@code growth.upgradesPerSlot} each (default 1);</li>
 *   <li>crux: the Crux Provider Upgrade; 1.</li>
 * </ul>
 * These checks run on every insertion attempt (player clicks, automation), not per tick. They use synced data only
 * (tags, server config), so client and server agree.
 *
 * <p>Every change calls {@code onChange} (the machine marks itself for revalidation). NeoForge calls it at the end of
 * the transaction, once per changed slot.
 */
public final class MachineInventory extends ItemStacksResourceHandler {
    private final MachineTier tier;
    private final Runnable onChange;

    public MachineInventory(MachineTier tier, Runnable onChange) {
        super(MachineSlots.INPUT_COUNT);
        this.tier = tier;
        this.onChange = onChange;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        ItemStack stack = resource.toStack();
        if (index == MachineSlots.SEED) {
            return PlantRules.isPlantable(stack) && !VfwConfig.isSeedBlacklisted(stack, tier);
        }
        if (index == MachineSlots.SOIL) {
            return SoilRules.isAcceptableSoil(stack) && !VfwConfig.isSoilBlacklisted(stack, tier);
        }
        if (index == MachineSlots.WATER_PROVIDER) {
            return isUpgrade(stack, UpgradeType.WATER_PROVIDER);
        }
        if (index == MachineSlots.HOE) {
            return SoilRules.isHoe(stack);
        }
        if (MachineSlots.isGrowthSlot(index)) {
            return isUpgrade(stack, UpgradeType.GROWTH_SPEED);
        }
        if (index == MachineSlots.CRUX_PROVIDER) {
            return stack.getItem() instanceof CruxProviderUpgradeItem;
        }
        return false;
    }

    private boolean isUpgrade(ItemStack stack, UpgradeType type) {
        return stack.getItem() instanceof TieredUpgradeItem upgrade && upgrade.type() == type && upgrade.fits(tier);
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        int limit = slotLimit(index);
        return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
    }

    /** Maximum item count of a slot, before the item's own max stack size. */
    public static int slotLimit(int index) {
        if (index == MachineSlots.SEED || index == MachineSlots.SOIL) {
            return MachineSlots.SEED_SOIL_LIMIT;
        }
        if (MachineSlots.isGrowthSlot(index)) {
            // Server config, synced to clients; outside a world (never expected here) fall back to the default.
            return VfwServerConfig.SPEC.isLoaded() ? VfwServerConfig.GROWTH_UPGRADES_PER_SLOT.get()
                    : VfwServerConfig.GROWTH_UPGRADES_PER_SLOT.getDefault();
        }
        return 1;
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChange.run();
    }

    /** A copy of the stack in a slot (safe to read; changing it does not change the inventory). */
    public ItemStack stackInSlot(int index) {
        return getResource(index).toStack(getAmountAsInt(index));
    }

    /**
     * After loading a save, make sure the inventory has exactly {@link MachineSlots#INPUT_COUNT} slots (a save from a
     * version with fewer slots gets empty new slots). Never shrinks: slots beyond the current layout are kept, so no
     * item is ever lost by a version change.
     */
    void ensureMinimumSize() {
        if (size() >= MachineSlots.INPUT_COUNT) {
            return;
        }
        NonNullList<ItemStack> resized = NonNullList.withSize(MachineSlots.INPUT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < size(); i++) {
            resized.set(i, stackInSlot(i));
        }
        setStacks(resized);
    }
}
