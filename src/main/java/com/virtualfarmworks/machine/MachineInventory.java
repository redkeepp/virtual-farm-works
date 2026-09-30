/*
 * MachineInventory — the input inventory of a Farm Matrix (seed and soil slots of every plot group, water provider,
 * tool slot, growth upgrades, crux): what each slot accepts and how many items it holds. Validation runs on both client
 * (menu) and server.
 */
package com.virtualfarmworks.machine;

import com.virtualfarmworks.config.VfwConfig;
import com.virtualfarmworks.config.VfwServerConfig;
import com.virtualfarmworks.item.CruxProviderUpgradeItem;
import com.virtualfarmworks.item.TieredUpgradeItem;
import com.virtualfarmworks.item.UpgradeType;
import com.virtualfarmworks.plant.PlantRules;
import com.virtualfarmworks.plant.SoilRules;
import com.virtualfarmworks.plant.VfwTags;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

/**
 * Transaction-aware item storage for the machine's inputs, built on NeoForge's {@link ItemStacksResourceHandler}.
 * Slot order: see {@link MachineLayout}.
 *
 * <p>Slot rules (owner specs, {@code docs/specs/}):
 * <ul>
 *   <li>seed slots: plantable (see {@code PlantRules}) and not blacklisted for this tier; Starter up to 64, other
 *       tiers config {@code machines.<tier>.seedsPerSlot} (default 64, may exceed a stack);</li>
 *   <li>soil slots: a soil (see {@code SoilRules}) and not blacklisted for this tier; same limits with
 *       {@code soilsPerSlot};</li>
 *   <li>water provider: a Water Provider Upgrade that fits this tier; 1;</li>
 *   <li>tool slot: a hoe (any hoe, any tier, damaged or not) on tiers that use one; the autocrafter catalyst
 *       ({@code #virtualfarmworks:crafter_catalysts}) on tiers with an autocrafter; 1;</li>
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
    private final MachineLayout layout;
    private final Runnable onChange;

    public MachineInventory(MachineTier tier, Runnable onChange) {
        super(MachineLayout.of(tier).inputCount());
        this.tier = tier;
        this.layout = MachineLayout.of(tier);
        this.onChange = onChange;
    }

    public MachineLayout layout() {
        return layout;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        ItemStack stack = resource.toStack();
        if (layout.isSeedSlot(index)) {
            return PlantRules.isPlantable(stack) && !VfwConfig.isSeedBlacklisted(stack, tier);
        }
        if (layout.isSoilSlot(index)) {
            return SoilRules.isAcceptableSoil(stack) && !VfwConfig.isSoilBlacklisted(stack, tier);
        }
        if (index == layout.waterSlot()) {
            return isUpgrade(stack, UpgradeType.WATER_PROVIDER);
        }
        if (index == layout.hoeSlot()) { // the tool slot: a hoe, or the autocrafter's catalyst (see MachineLayout)
            return layout.usesHoe() ? SoilRules.isHoe(stack)
                    : layout.hasCrafter() && stack.is(VfwTags.CRAFTER_CATALYSTS);
        }
        if (layout.isGrowthSlot(index)) {
            return isUpgrade(stack, UpgradeType.GROWTH_SPEED);
        }
        if (index == layout.cruxSlot()) {
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
        if (layout.groups() > 1 && (layout.isSeedSlot(index) || layout.isSoilSlot(index))) {
            return limit; // grid slots count plants, not stacks: a pack maker may allow more than a stack
        }
        return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
    }

    /** Maximum item count of a slot, before the item's own max stack size (Starter) or regardless of it (grids). */
    public int slotLimit(int index) {
        if (layout.isSeedSlot(index)) {
            return layout.groups() == 1 ? MachineSlots.SEED_SOIL_LIMIT : configValue(true);
        }
        if (layout.isSoilSlot(index)) {
            return layout.groups() == 1 ? MachineSlots.SEED_SOIL_LIMIT : configValue(false);
        }
        if (layout.isGrowthSlot(index)) {
            // Server config, synced to clients; outside a world (never expected here) fall back to the default.
            return VfwServerConfig.SPEC.isLoaded() ? VfwServerConfig.GROWTH_UPGRADES_PER_SLOT.get()
                    : VfwServerConfig.GROWTH_UPGRADES_PER_SLOT.getDefault();
        }
        return 1;
    }

    /** Grid tiers: {@code machines.<tier>.seedsPerSlot} or {@code soilsPerSlot} (default when no world is loaded). */
    private int configValue(boolean seeds) {
        VfwServerConfig.MachineSettings settings = VfwServerConfig.machine(tier);
        var value = seeds ? settings.seedsPerSlot : settings.soilsPerSlot;
        if (value == null) {
            return MachineSlots.SEED_SOIL_LIMIT;
        }
        return VfwServerConfig.SPEC.isLoaded() ? value.get() : value.getDefault();
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
     * After loading a save, make sure the inventory has exactly the layout's slot count (a save from a version with
     * fewer slots gets empty new slots). Never shrinks: slots beyond the current layout are kept, so no item is ever
     * lost by a version change.
     */
    void ensureMinimumSize() {
        if (size() >= layout.inputCount()) {
            return;
        }
        NonNullList<ItemStack> resized = NonNullList.withSize(layout.inputCount(), ItemStack.EMPTY);
        for (int i = 0; i < size(); i++) {
            resized.set(i, stackInSlot(i));
        }
        setStacks(resized);
    }
}
