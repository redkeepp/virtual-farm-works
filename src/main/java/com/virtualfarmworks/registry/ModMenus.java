/*
 * ModMenus — DeferredRegister of VFW menu types (the Starter's and the Entropic's). The matching screens are registered
 * client-side in VirtualFarmWorksClient.
 */
package com.virtualfarmworks.registry;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;
import com.virtualfarmworks.menu.FarmMatrixMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry names are persisted in network protocol / saves of other mods: never rename them. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, VirtualFarmWorks.MODID);

    /** Created from the open-menu packet on the client (see {@link FarmMatrixMenu#fromNetwork}). */
    public static final DeferredHolder<MenuType<?>, MenuType<FarmMatrixMenu>> FARM_MATRIX =
            MENUS.register("farm_matrix", () -> IMenuTypeExtension.create(FarmMatrixMenu::fromNetwork));

    /** The Entropic's own menu (its slots and synced numbers differ from the Starter's). */
    public static final DeferredHolder<MenuType<?>, MenuType<EntropicFarmMatrixMenu>> ENTROPIC_FARM_MATRIX =
            MENUS.register("entropic_farm_matrix",
                    () -> IMenuTypeExtension.create(EntropicFarmMatrixMenu::fromNetwork));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
