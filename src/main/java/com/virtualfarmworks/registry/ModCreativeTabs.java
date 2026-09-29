/*
 * ModCreativeTabs — the "Virtual Farm Works" creative tab and the order of its items (progression order).
 */
package com.virtualfarmworks.registry;

import com.virtualfarmworks.VirtualFarmWorks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own creative tab, listing every VFW item in progression order. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, VirtualFarmWorks.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.virtualfarmworks"))
                    .icon(() -> ModItems.STARTER_FARM_MATRIX.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.STARTER_FARM_MATRIX.get());
                        output.accept(ModItems.ENTROPIC_FARM_MATRIX.get());
                        // EnumMap values iterate in tier order: Starter -> Entropic.
                        ModItems.WATER_PROVIDER_UPGRADES.values().forEach(item -> output.accept(item.get()));
                        ModItems.GROWTH_SPEED_UPGRADES.values().forEach(item -> output.accept(item.get()));
                        output.accept(ModItems.CRUX_PROVIDER_UPGRADE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
