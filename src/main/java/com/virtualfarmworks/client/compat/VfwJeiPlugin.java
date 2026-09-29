/*
 * VfwJeiPlugin — optional JEI integration (client only): tells JEI which screen areas the Farm Matrix GUIs use outside
 * their texture (side column, face box, filter box) so JEI's ingredient list and bookmarks never cover them, and lets
 * players drag items from JEI onto the harvest filter's ghost slots.
 */
package com.virtualfarmworks.client.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.client.EntropicFarmMatrixScreen;
import com.virtualfarmworks.client.FarmMatrixJeiTargets;
import com.virtualfarmworks.client.FarmMatrixScreen;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Discovered by JEI through the {@link JeiPlugin} annotation; never loaded when JEI is absent (VFW only compiles
 * against JEI's API, see build.gradle). Keep JEI types out of every other VFW class: screens describe themselves
 * through {@link FarmMatrixJeiTargets}.
 */
@JeiPlugin
public final class VfwJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, "jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        register(registration, FarmMatrixScreen.class);
        register(registration, EntropicFarmMatrixScreen.class);
    }

    /** Registers the handlers for one screen class; the class must implement {@link FarmMatrixJeiTargets}. */
    private static <S extends AbstractContainerScreen<?>> void register(
            IGuiHandlerRegistration registration, Class<S> screen) {
        registration.addGuiContainerHandler(screen, new IGuiContainerHandler<S>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(S gui) {
                return ((FarmMatrixJeiTargets) gui).extraAreas();
            }
        });
        registration.addGhostIngredientHandler(screen, new FilterGhostHandler<>());
    }

    /**
     * Owner request: filter items the player does not have. While the filter box is open, each of its 9 ghost slots is
     * a drop target for items; dropping sends the item type to the server (the screen sends the packet).
     */
    private static final class FilterGhostHandler<S extends AbstractContainerScreen<?>>
            implements IGhostIngredientHandler<S> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(S screen, ITypedIngredient<I> ingredient, boolean doStart) {
            Optional<ItemStack> stack = ingredient.getItemStack();
            if (stack.isEmpty() || stack.get().isEmpty()) {
                return List.of(); // fluids and other ingredient types cannot be filtered
            }
            FarmMatrixJeiTargets targetsOf = (FarmMatrixJeiTargets) screen;
            List<Rect2i> areas = targetsOf.filterSlotAreas();
            List<Target<I>> targets = new ArrayList<>(areas.size());
            for (int i = 0; i < areas.size(); i++) {
                int slot = i;
                Rect2i area = areas.get(i);
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return area;
                    }

                    @Override
                    public void accept(I dropped) {
                        targetsOf.setFilterGhostFromJei(slot, stack.get());
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
            // Nothing to clean up: each drop is sent when it happens.
        }
    }
}
