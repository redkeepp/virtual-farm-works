/*
 * FarmMatrixJeiTargets — what a Farm Matrix screen tells recipe viewers (JEI): the areas it draws outside its texture,
 * and the ghost slots that accept items dragged from the ingredient list (harvest filter; the autocrafter's recipe
 * grid on tiers that have one). Both machine screens implement it, so the JEI plugin handles every tier the same way.
 */
package com.virtualfarmworks.client;

import java.util.List;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

/** Client only. Kept free of JEI types so screens never load JEI classes. */
public interface FarmMatrixJeiTargets {
    /** Screen areas drawn outside the texture (side column, open boxes), where JEI must not draw. */
    List<Rect2i> extraAreas();

    /** Screen areas of the harvest filter's ghost slots while its box is open; empty when closed. */
    List<Rect2i> filterSlotAreas();

    /** An item type was dropped on filter ghost slot {@code slot}; the screen sends it to the server. */
    void setFilterGhostFromJei(int slot, ItemStack stack);

    /** Screen areas of the autocrafter's 9 recipe cells while its panel is open; empty otherwise (and without one). */
    default List<Rect2i> crafterCellAreas() {
        return List.of();
    }

    /** An item type was dropped on recipe cell {@code cell}; the screen sends the new grid to the server. */
    default void setCrafterCellFromJei(int cell, ItemStack stack) {
    }
}
