/*
 * VfwJeiPlugin — optional JEI integration (client only): tells JEI which screen areas the Farm Matrix GUIs use outside
 * their texture (side column, face box, filter box) so JEI's ingredient list and bookmarks never cover them, lets
 * players drag items from JEI onto the harvest filter's and the autocrafter's ghost slots, and puts crafting recipes
 * into the autocrafter with JEI's "+" button (owner spec).
 */
package com.virtualfarmworks.client.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.client.EntropicFarmMatrixScreen;
import com.virtualfarmworks.client.FarmMatrixJeiTargets;
import com.virtualfarmworks.client.FarmMatrixScreen;
import com.virtualfarmworks.machine.MachineCrafter;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;
import com.virtualfarmworks.network.SetCrafterGridPayload;
import com.virtualfarmworks.registry.ModMenus;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

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

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CrafterTransferHandler(registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
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
        registration.addGhostIngredientHandler(screen, new GhostHandler<>());
    }

    /**
     * Owner request: filter items the player does not have. While the filter box is open, each of its 9 ghost slots is
     * a drop target for items; so are the autocrafter's 9 recipe cells while its panel is open. Dropping sends the item
     * type to the server (the screen sends the packet).
     */
    private static final class GhostHandler<S extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<S> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(S screen, ITypedIngredient<I> ingredient, boolean doStart) {
            Optional<ItemStack> stack = ingredient.getItemStack();
            if (stack.isEmpty() || stack.get().isEmpty()) {
                return List.of(); // fluids and other ingredient types are not items
            }
            FarmMatrixJeiTargets targetsOf = (FarmMatrixJeiTargets) screen;
            List<Target<I>> targets = new ArrayList<>();
            List<Rect2i> filterAreas = targetsOf.filterSlotAreas();
            for (int i = 0; i < filterAreas.size(); i++) {
                int slot = i;
                targets.add(target(filterAreas.get(i), () -> targetsOf.setFilterGhostFromJei(slot, stack.get())));
            }
            List<Rect2i> cellAreas = targetsOf.crafterCellAreas();
            for (int i = 0; i < cellAreas.size(); i++) {
                int cell = i;
                targets.add(target(cellAreas.get(i), () -> targetsOf.setCrafterCellFromJei(cell, stack.get())));
            }
            return targets;
        }

        private static <I> Target<I> target(Rect2i area, Runnable onDrop) {
            return new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I dropped) {
                    onDrop.run();
                }
            };
        }

        @Override
        public void onComplete() {
            // Nothing to clean up: each drop is sent when it happens.
        }
    }

    /**
     * JEI's "+" on a crafting recipe while an Entropic Farm Matrix is open (owner spec): the recipe's 9 cells, as JEI
     * shows them, go to the autocrafter's recipe grid (server packet) and the panel opens; the server then shows the
     * result, and SET CRAFT saves it. Nothing is taken from the player: the grid holds item types only.
     */
    private static final class CrafterTransferHandler
            implements IRecipeTransferHandler<EntropicFarmMatrixMenu, RecipeHolder<CraftingRecipe>> {
        private final IRecipeTransferHandlerHelper helper;

        CrafterTransferHandler(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

        @Override
        public Class<? extends EntropicFarmMatrixMenu> getContainerClass() {
            return EntropicFarmMatrixMenu.class;
        }

        @Override
        public Optional<MenuType<EntropicFarmMatrixMenu>> getMenuType() {
            return Optional.of(ModMenus.ENTROPIC_FARM_MATRIX.get());
        }

        @Override
        public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public @Nullable IRecipeTransferError transferRecipe(EntropicFarmMatrixMenu menu,
                                                              RecipeHolder<CraftingRecipe> recipe,
                                                              IRecipeSlotsView slots, Player player,
                                                              boolean maxTransfer, boolean doTransfer) {
            if (recipe.value().isSpecial() || recipe.value().placementInfo().isImpossibleToPlace()) {
                return helper.createUserErrorWithTooltip(
                        Component.translatable("gui.virtualfarmworks.crafter.jei_unsupported"));
            }
            if (doTransfer) {
                // JEI's crafting category always lays out 9 input slots, row by row.
                List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
                List<ItemStack> grid = new ArrayList<>(MachineCrafter.GRID_SIZE);
                for (int i = 0; i < MachineCrafter.GRID_SIZE; i++) {
                    grid.add(i < inputs.size() ? inputs.get(i).getDisplayedItemStack().orElse(ItemStack.EMPTY)
                            : ItemStack.EMPTY);
                }
                ClientPacketDistributor.sendToServer(new SetCrafterGridPayload(menu.containerId,
                        MachineCrafter.normalize(grid)));
                menu.setCrafterVisible(true);
            }
            return null;
        }
    }
}
