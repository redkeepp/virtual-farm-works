/*
 * VfwJeiPlugin — optional JEI integration (client only): tells JEI which screen areas the Farm Matrix GUI uses outside
 * its texture (side column, face box) so JEI's ingredient list and bookmarks never cover them.
 */
package com.virtualfarmworks.client.compat;

import java.util.List;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.client.FarmMatrixScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

/**
 * Discovered by JEI through the {@link JeiPlugin} annotation; never loaded when JEI is absent (VFW only compiles
 * against JEI's API, see build.gradle). Keep JEI types out of every other VFW class.
 */
@JeiPlugin
public final class VfwJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(FarmMatrixScreen.class, new IGuiContainerHandler<FarmMatrixScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(FarmMatrixScreen screen) {
                return screen.extraAreas();
            }
        });
    }
}
