package com.virtualfarmworks.data;

import com.virtualfarmworks.VirtualFarmWorks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Data map types owned by VFW. */
public final class ModDataMaps {
    /**
     * Soil growth bonuses, see {@link SoilProperties}. Synced (not mandatory) so the client GUI can show the same
     * multiplier the server uses; non-mandatory so the map never blocks a client from joining.
     */
    public static final DataMapType<Item, SoilProperties> SOIL_PROPERTIES = DataMapType
            .builder(Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, "soil_properties"), Registries.ITEM,
                    SoilProperties.CODEC)
            .synced(SoilProperties.CODEC, false)
            .build();

    private ModDataMaps() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterDataMapTypesEvent.class, event -> event.register(SOIL_PROPERTIES));
    }

    /**
     * Speed multiplier granted by a soil stack ({@code 1.0} when the soil has no entry). Data maps are reloaded with
     * datapacks, so machines must not cache this across a {@code /reload}; they re-read it on revalidation.
     */
    public static double soilSpeedMultiplier(ItemStack soil) {
        SoilProperties properties = soil.typeHolder().getData(SOIL_PROPERTIES);
        return properties == null ? 1.0 : properties.speedMultiplier();
    }
}
