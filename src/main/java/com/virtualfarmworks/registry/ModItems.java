/*
 * ModItems — DeferredRegister of VFW items: Farm Matrix block items, Water Provider and Growth Speed upgrades (all
 * five tiers) and the Crux Provider Upgrade.
 */
package com.virtualfarmworks.registry;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.item.CruxProviderUpgradeItem;
import com.virtualfarmworks.item.TieredUpgradeItem;
import com.virtualfarmworks.item.UpgradeType;
import com.virtualfarmworks.machine.MachineTier;

import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Item registrations.
 *
 * <p>All upgrade tiers are registered already (even though only the Starter machine exists), because the Starter
 * machine accepts upgrades of every tier (see {@link MachineTier#accepts}). Registry names are persisted in saves:
 * never rename or remove one once released.
 *
 * <p>Upgrades stack to 64 in inventories on purpose: the machine slots enforce their own per-slot limits (1 by default,
 * configurable for growth upgrades by pack makers), so the item stack size must not be the limiting factor.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VirtualFarmWorks.MODID);

    public static final DeferredItem<BlockItem> STARTER_FARM_MATRIX =
            ITEMS.registerSimpleBlockItem("starter_farm_matrix", ModBlocks.STARTER_FARM_MATRIX);
    public static final DeferredItem<BlockItem> ENTROPIC_FARM_MATRIX =
            ITEMS.registerSimpleBlockItem("entropic_farm_matrix", ModBlocks.ENTROPIC_FARM_MATRIX);

    /** {@code <tier>_water_provider_upgrade}, one per built tier ({@link MachineTier#isBuilt}). */
    public static final Map<MachineTier, DeferredItem<TieredUpgradeItem>> WATER_PROVIDER_UPGRADES =
            registerTiered(UpgradeType.WATER_PROVIDER);

    /** {@code <tier>_growth_upgrade} (displayed as "Growth Speed Upgrade"), one per built tier. */
    public static final Map<MachineTier, DeferredItem<TieredUpgradeItem>> GROWTH_SPEED_UPGRADES =
            registerTiered(UpgradeType.GROWTH_SPEED);

    public static final DeferredItem<CruxProviderUpgradeItem> CRUX_PROVIDER_UPGRADE =
            ITEMS.registerItem("crux_provider_upgrade", CruxProviderUpgradeItem::new);

    private ModItems() {
    }

    /**
     * Registers one {@link TieredUpgradeItem} per built tier, in tier order (the map iterates in that order). Tiers not
     * built yet get none: their textures and names wait in the assets until the tier is made.
     */
    private static Map<MachineTier, DeferredItem<TieredUpgradeItem>> registerTiered(UpgradeType type) {
        Map<MachineTier, DeferredItem<TieredUpgradeItem>> byTier = new EnumMap<>(MachineTier.class);
        for (MachineTier tier : MachineTier.values()) {
            if (!tier.isBuilt()) {
                continue;
            }
            byTier.put(tier, ITEMS.registerItem(
                    tier.getSerializedName() + "_" + type.registrySuffix(),
                    properties -> new TieredUpgradeItem(type, tier, properties)));
        }
        return Collections.unmodifiableMap(byTier);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
