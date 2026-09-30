/*
 * EntropicFarmMatrixScreen — the Entropic Farm Matrix GUI (client only): the owner's texture with its two 4x15 grids,
 * the dynamic texts (title, status, hydration, active and waiting plots, growth), the green progress bar, the striped
 * FE bar, the red tint of plot groups with a problem, the side column drawn by code (face modes, upgrades and hoe,
 * Fertilized Essence, harvest filter, autocrafter, power) and the autocrafter panel opened over the grids. While open,
 * it lowers the GUI scale when the machine would not fit (the player's own scale comes back on close).
 */
package com.virtualfarmworks.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.Window;
import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.FaceMode;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineCrafter;
import com.virtualfarmworks.machine.MachineFilter;
import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.AbstractFarmMatrixMenu;
import com.virtualfarmworks.menu.DisplayFormats;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;
import com.virtualfarmworks.menu.EntropicLayout;
import com.virtualfarmworks.menu.FarmMatrixLayout;
import com.virtualfarmworks.network.SetCrafterGridPayload;
import com.virtualfarmworks.network.SetFilterGhostPayload;
import com.virtualfarmworks.plant.VfwTags;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Draws only; every action is sent to the server as a menu button click ({@link AbstractFarmMatrixMenu#clickMenuButton}),
 * and every number comes from the menu's synced data. Nothing here decides gameplay.
 *
 * <p>Same drawing techniques as the Starter's screen ({@code FarmMatrixScreen}): side-panel boxes as nested fills so
 * every line is one pixel, 40% ghost items covered by a translucent fill, texts shrunk to fit. Positions:
 * {@link EntropicLayout}.
 *
 * <h2>Autocrafter panel (owner: a modal over the machine GUI)</h2>
 * Opened and closed by the crafting-table button of the side column (or by JEI's "+"). It is drawn at the end of the
 * background layer, after dimming the two grids, whose slots are inactive meanwhile: the GUI layers elements by
 * overlap in drawing order, so the recipe grid and result (menu slots, drawn later) come on top of the panel and the
 * grid items under it are simply not drawn. Buttons show a gradient under the mouse (owner spec). The recipe list:
 * one click loads a recipe into the grid, a double click on the same recipe deletes it, a click on empty list space
 * clears the selection; the mouse wheel scrolls it.
 */
public class EntropicFarmMatrixScreen extends AbstractContainerScreen<EntropicFarmMatrixMenu>
        implements FarmMatrixJeiTargets {
    private static final MachineLayout LAYOUT = MachineLayout.ENTROPIC;
    /** How fast the drawn FE level follows the synced one: it falls gradually when power is cut (owner spec). */
    private static final double ENERGY_SMOOTHING_SECONDS = 0.35;

    private final Identifier texture;
    private final Identifier crafterTexture;
    private final int themeColor;
    private final ItemStack waterGhost;
    private final ItemStack growthGhost;
    private final ItemStack cruxGhost;
    /** Placeholder of the catalyst slot: the first catalyst of the tag (the Master Infusion Crystal), or none. */
    private final ItemStack catalystGhost;
    private final ItemStack crafterIcon = new ItemStack(Items.CRAFTING_TABLE);
    private final ItemStack replantIcon = new ItemStack(Items.WHEAT_SEEDS);
    private boolean faceBoxOpen;
    private boolean filterBoxOpen;
    /** Smooths the 5-tick progress syncs into continuous movement (owner request, Starter step 8). */
    private final SmoothProgress smoothProgress = new SmoothProgress();
    private double shownProgress;
    /** Drawn FE fraction (0..1), eased toward the synced one; negative until the first synced value. */
    private double shownEnergy = -1.0;
    private long lastFrameMillis;
    /** First recipe row shown in the autocrafter's list. */
    private int recipeScroll;
    /** Recipe clicked last: a double click deletes only when both clicks hit the same recipe. */
    private int lastClickedRecipe = -1;
    /** This screen lowered the GUI scale to fit (see {@link #added}); the player's own comes back on close. */
    private boolean scaleLowered;

    public EntropicFarmMatrixScreen(EntropicFarmMatrixMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, EntropicLayout.GUI_WIDTH, EntropicLayout.GUI_HEIGHT);
        MachineTier tier = menu.tier();
        this.texture = Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID,
                "textures/gui/" + tier.getSerializedName() + "_farm_matrix_gui.png");
        this.crafterTexture = Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID,
                "textures/gui/crafter_farm_matrix_gui.png");
        this.themeColor = 0xFF000000 | tier.themeColor();
        this.waterGhost = new ItemStack(ModItems.WATER_PROVIDER_UPGRADES.get(tier).get());
        this.growthGhost = new ItemStack(ModItems.GROWTH_SPEED_UPGRADES.get(tier).get());
        this.cruxGhost = new ItemStack(ModItems.CRUX_PROVIDER_UPGRADE.get());
        this.catalystGhost = firstCatalyst();
    }

    /** The first item of {@code #virtualfarmworks:crafter_catalysts} (tags are synced to clients), or empty. */
    private static ItemStack firstCatalyst() {
        for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(VfwTags.CRAFTER_CATALYSTS)) {
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    // =================================================================================================================
    // GUI scale (owner, 2026-09-30)
    // =================================================================================================================

    /**
     * Opening (or coming back from JEI's recipe view), before the screen is sized: the automatic GUI scale never leaves
     * room for this 320 px GUI, so the scale drops to the largest one where the machine fits ({@link GuiScaleFit}),
     * only while the screen is open.
     */
    @Override
    public void added() {
        super.added();
        fitGuiScale();
    }

    /** The window changed: Minecraft has just set the player's own scale again, so fit again before laying out. */
    @Override
    public void resize(int width, int height) {
        scaleLowered = false;
        if (fitGuiScale()) {
            Window window = minecraft.getWindow();
            width = window.getGuiScaledWidth();
            height = window.getGuiScaledHeight();
        }
        super.resize(width, height);
    }

    /**
     * Closing (or leaving for another screen): the player's own scale comes back, as Minecraft computes it from the
     * options, before the next screen is sized. The options themselves are never changed.
     */
    @Override
    public void removed() {
        super.removed();
        if (scaleLowered) {
            Window window = minecraft.getWindow();
            window.setGuiScale(window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode()));
            scaleLowered = false;
        }
    }

    /** Lowers the GUI scale when the machine does not fit at the current one; returns whether it did. */
    private boolean fitGuiScale() {
        Window window = minecraft.getWindow();
        int scale = GuiScaleFit.fittingScale(window.getWidth(), window.getHeight(), window.getGuiScale(),
                EntropicLayout.FIT_WIDTH, EntropicLayout.FIT_HEIGHT);
        if (scale == window.getGuiScale()) {
            return false;
        }
        window.setGuiScale(scale);
        scaleLowered = true;
        return true;
    }

    // =================================================================================================================
    // Background layer
    // =================================================================================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        long now = Util.getMillis();
        shownProgress = menu.isDataSynced()
                ? smoothProgress.update(menu.progress(), menu.harvestCount(), now)
                : 0.0;
        updateShownEnergy(now);
        int x0 = leftPos;
        int y0 = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0, 0.0F, 0.0F, EntropicLayout.GUI_WIDTH,
                EntropicLayout.GUI_HEIGHT, EntropicLayout.TEXTURE_SIZE, EntropicLayout.TEXTURE_SIZE);
        extractProgressBar(graphics, x0, y0);
        extractEnergyBar(graphics, x0, y0);
        extractSidePanel(graphics, x0, y0, mouseX, mouseY);
        if (faceBoxOpen) {
            extractFaceBox(graphics, x0, y0, mouseX, mouseY);
        }
        if (filterBoxOpen) {
            extractFilterBox(graphics, x0, y0, mouseX, mouseY);
        }
        extractGhosts(graphics, x0, y0);
        extractGroupProblems(graphics, x0, y0);
        if (menu.isCrafterVisible()) {
            extractCrafter(graphics, x0, y0, mouseX, mouseY); // last: over everything drawn so far (see class doc)
        }
    }

    private void extractProgressBar(GuiGraphicsExtractor graphics, int x0, int y0) {
        int filled = (int) Math.round(shownProgress * EntropicLayout.BAR_WIDTH);
        if (filled > 0) {
            int x = x0 + EntropicLayout.BAR_X;
            int y = y0 + EntropicLayout.BAR_Y;
            graphics.fill(x, y, x + filled, y + EntropicLayout.BAR_HEIGHT, FarmMatrixLayout.COLOR_BAR);
        }
    }

    /** Eases the drawn FE level toward the synced one, frame by frame (never jumps, owner spec). */
    private void updateShownEnergy(long now) {
        long capacity = menu.energyCapacity();
        double target = capacity > 0 ? Math.clamp((double) menu.energy() / capacity, 0.0, 1.0) : 0.0;
        if (!menu.isDataSynced()) {
            lastFrameMillis = now;
            return;
        }
        if (shownEnergy < 0.0) {
            shownEnergy = target; // first frame with real data: start where the machine is
        } else {
            double seconds = Math.max(0.0, (now - lastFrameMillis) / 1000.0);
            shownEnergy += (target - shownEnergy) * (1.0 - Math.exp(-seconds / ENERGY_SMOOTHING_SECONDS));
        }
        lastFrameMillis = now;
    }

    /** FE bar: pixel columns alternating red / dark red (owner spec), as wide as the stored fraction. */
    private void extractEnergyBar(GuiGraphicsExtractor graphics, int x0, int y0) {
        int filled = (int) Math.round(Math.max(0.0, shownEnergy) * EntropicLayout.ENERGY_BAR_WIDTH);
        int x = x0 + EntropicLayout.ENERGY_BAR_X;
        int y = y0 + EntropicLayout.ENERGY_BAR_Y;
        for (int column = 0; column < filled; column++) {
            graphics.fill(x + column, y, x + column + 1, y + EntropicLayout.ENERGY_BAR_HEIGHT,
                    column % 2 == 0 ? EntropicLayout.COLOR_ENERGY : EntropicLayout.COLOR_ENERGY_DARK);
        }
    }

    /**
     * The side column glued to the texture: "O" (face modes), the lightning box (energy use, tooltip only), the 5-cell
     * upgrade block (4 Growth Speed, Crux), Fertilized Essence ON/OFF, replant (wheat seeds on green ON / red OFF, all
     * grey when the config disables it), harvest filter (half white, half black), autocrafter (crafting table icon),
     * ON/OFF.
     */
    private void extractSidePanel(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        int outputTop = y0 + EntropicLayout.OUTPUT_BOX_TOP;
        boolean outputActive = faceBoxOpen || isOverBox(EntropicLayout.OUTPUT_BOX_TOP, mouseX, mouseY);
        extractPanelBox(graphics, x0, outputTop, 1,
                outputActive ? FarmMatrixLayout.COLOR_BUTTON_ACTIVE : FarmMatrixLayout.COLOR_BACKGROUND);
        extractCellLabel(graphics, Component.literal("O"), x0, FarmMatrixLayout.cellY(outputTop, 0), themeColor);

        int cellX = x0 + FarmMatrixLayout.PANEL_INTERIOR_X;
        int energyTop = y0 + EntropicLayout.ENERGY_BOX_TOP;
        extractPanelBox(graphics, x0, energyTop, 1, FarmMatrixLayout.COLOR_BACKGROUND);
        int boltY = FarmMatrixLayout.cellY(energyTop, 0);
        for (int[] pixel : EntropicLayout.BOLT_SHADE) {
            graphics.fill(cellX + pixel[0], boltY + pixel[1], cellX + pixel[0] + 1, boltY + pixel[1] + 1,
                    EntropicLayout.COLOR_BOLT_SHADE);
        }
        for (int[] pixel : EntropicLayout.BOLT_LIGHT) {
            graphics.fill(cellX + pixel[0], boltY + pixel[1], cellX + pixel[0] + 1, boltY + pixel[1] + 1,
                    EntropicLayout.COLOR_BOLT_LIGHT);
        }

        extractPanelBox(graphics, x0, y0 + EntropicLayout.UPGRADE_BOX_TOP, EntropicLayout.UPGRADE_CELLS,
                FarmMatrixLayout.COLOR_BACKGROUND);

        int fertilizedTop = y0 + EntropicLayout.FERTILIZED_BOX_TOP;
        boolean fertilized = menu.isFertilizedEssenceEnabled();
        extractPanelBox(graphics, x0, fertilizedTop, 1,
                fertilized ? FarmMatrixLayout.COLOR_FERTILIZED_ON : FarmMatrixLayout.COLOR_FERTILIZED_OFF);
        extractCellLabel(graphics, onOff(fertilized), x0, FarmMatrixLayout.cellY(fertilizedTop, 0), 0xFFFFFFFF);

        int replantTop = y0 + EntropicLayout.REPLANT_BUTTON_TOP;
        int replantCellY = FarmMatrixLayout.cellY(replantTop, 0);
        FarmMatrixBlockEntity.ReplantState replant = menu.replantState();
        extractPanelBox(graphics, x0, replantTop, 1, switch (replant) {
            case ON -> FarmMatrixLayout.COLOR_ON;
            case OFF -> FarmMatrixLayout.COLOR_OFF;
            case DISABLED -> EntropicLayout.COLOR_DISABLED;
        });
        graphics.fakeItem(replantIcon, cellX, replantCellY);
        if (replant == FarmMatrixBlockEntity.ReplantState.DISABLED) {
            graphics.fill(cellX, replantCellY, cellX + FarmMatrixLayout.CELL, replantCellY + FarmMatrixLayout.CELL,
                    EntropicLayout.COLOR_DISABLED_COVER);
        }

        int filterTop = y0 + EntropicLayout.FILTER_BUTTON_TOP;
        extractPanelBox(graphics, x0, filterTop, 1, FarmMatrixLayout.COLOR_BLACK);
        int filterCellY = FarmMatrixLayout.cellY(filterTop, 0);
        graphics.fill(cellX, filterCellY, cellX + FarmMatrixLayout.CELL / 2, filterCellY + FarmMatrixLayout.CELL,
                FarmMatrixLayout.COLOR_WHITE);

        int crafterTop = y0 + EntropicLayout.CRAFTER_BUTTON_TOP;
        boolean crafterActive = menu.isCrafterVisible() || isOverBox(EntropicLayout.CRAFTER_BUTTON_TOP, mouseX, mouseY);
        extractPanelBox(graphics, x0, crafterTop, 1,
                crafterActive ? FarmMatrixLayout.COLOR_BUTTON_ACTIVE : FarmMatrixLayout.COLOR_BACKGROUND);
        graphics.fakeItem(crafterIcon, cellX, FarmMatrixLayout.cellY(crafterTop, 0));

        int powerTop = y0 + EntropicLayout.POWER_BOX_TOP;
        boolean on = menu.isEnabled();
        extractPanelBox(graphics, x0, powerTop, 1, on ? FarmMatrixLayout.COLOR_ON : FarmMatrixLayout.COLOR_OFF);
        extractCellLabel(graphics, onOff(on), x0, FarmMatrixLayout.cellY(powerTop, 0), 0xFFFFFFFF);
    }

    /**
     * One side-panel box with {@code cells} stacked cells, painted as nested rectangles so every line is one pixel:
     * theme color (left/top/bottom; its right column is covered next), blue frame (right column at x = -1, against the
     * texture's own border at x = 0), then each 16x16 interior. Same drawing as the Starter's column.
     */
    private void extractPanelBox(GuiGraphicsExtractor graphics, int x0, int top, int cells, int interior) {
        int bottom = top + FarmMatrixLayout.boxHeight(cells);
        int right = x0;
        graphics.fill(x0 + FarmMatrixLayout.PANEL_BORDER_X, top, right, bottom, themeColor);
        graphics.fill(x0 + FarmMatrixLayout.PANEL_BORDER_X + 1, top + 1, right, bottom - 1, FarmMatrixLayout.COLOR_FRAME);
        int interiorX = x0 + FarmMatrixLayout.PANEL_INTERIOR_X;
        for (int i = 0; i < cells; i++) {
            int y = FarmMatrixLayout.cellY(top, i);
            graphics.fill(interiorX, y, interiorX + FarmMatrixLayout.CELL, y + FarmMatrixLayout.CELL, interior);
        }
    }

    /** The face box (owner layout, {@link FarmMatrixLayout#FACE_GRID}): each face in the color of its mode. */
    private void extractFaceBox(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        int boxX = x0 + EntropicLayout.FACE_BOX_X;
        int boxY = y0 + EntropicLayout.FACE_BOX_Y;
        int size = FarmMatrixLayout.FACE_BOX_SIZE;
        graphics.fill(boxX - 1, boxY - 1, boxX + size + 1, boxY + size + 1, themeColor);
        graphics.fill(boxX, boxY, boxX + size, boxY + size, FarmMatrixLayout.COLOR_FRAME);
        graphics.fill(boxX + 1, boxY + 1, boxX + size - 1, boxY + size - 1, FarmMatrixLayout.COLOR_BACKGROUND);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                RelativeSide side = FarmMatrixLayout.FACE_GRID[row][col];
                if (side == null) {
                    continue;
                }
                int cx = x0 + EntropicLayout.faceCellX(col);
                int cy = y0 + EntropicLayout.faceCellY(row);
                int cell = FarmMatrixLayout.FACE_CELL;
                graphics.fill(cx, cy, cx + cell, cy + cell, menu.faceMode(side).color());
                if (isInside(mouseX, mouseY, cx, cy, cell, cell)) {
                    graphics.fill(cx, cy, cx + cell, cy + cell, 0x40FFFFFF);
                }
                Component label = Component.translatable(side.translationKey() + ".short");
                graphics.text(font, label, cx + (cell - font.width(label)) / 2, cy + 4, 0xFFFFFFFF, false);
            }
        }
    }

    /** The harvest filter box, like the Starter's: mode strip, 3x3 ghost-slot block, page row. */
    private void extractFilterBox(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        int boxX = x0 + EntropicLayout.FILTER_BOX_X;
        int boxY = y0 + EntropicLayout.FILTER_BOX_Y;
        int width = FarmMatrixLayout.FILTER_BOX_WIDTH;
        int height = FarmMatrixLayout.FILTER_BOX_HEIGHT;
        graphics.fill(boxX, boxY, boxX + width, boxY + height, themeColor);
        graphics.fill(boxX + 1, boxY + 1, boxX + width - 1, boxY + height - 1, FarmMatrixLayout.COLOR_FRAME);
        graphics.fill(boxX + 2, boxY + 2, boxX + width - 2, boxY + height - 2, FarmMatrixLayout.COLOR_BACKGROUND);

        int contentX = x0 + EntropicLayout.FILTER_CONTENT_X;
        int grid = FarmMatrixLayout.FILTER_GRID_SIZE;
        boolean whitelist = menu.isFilterWhitelist();
        int stripY = y0 + EntropicLayout.FILTER_STRIP_Y;
        graphics.fill(contentX, stripY, contentX + grid, stripY + FarmMatrixLayout.FILTER_STRIP_HEIGHT,
                whitelist ? FarmMatrixLayout.COLOR_WHITE : FarmMatrixLayout.COLOR_BLACK);
        extractFittedCentered(graphics, Component.translatable(whitelist ? "gui.virtualfarmworks.filter.whitelisted"
                        : "gui.virtualfarmworks.filter.blacklisted"), contentX + 1, stripY, grid - 2,
                FarmMatrixLayout.FILTER_STRIP_HEIGHT, whitelist ? FarmMatrixLayout.COLOR_BLACK : FarmMatrixLayout.COLOR_WHITE);

        int gridY = y0 + EntropicLayout.FILTER_GRID_Y;
        graphics.fill(contentX, gridY, contentX + grid, gridY + grid, FarmMatrixLayout.COLOR_FRAME);
        for (int i = 0; i < MachineFilter.PAGE_SIZE; i++) {
            int cx = x0 + EntropicLayout.filterSlotX(i);
            int cy = y0 + EntropicLayout.filterSlotY(i);
            graphics.fill(cx, cy, cx + FarmMatrixLayout.CELL, cy + FarmMatrixLayout.CELL, FarmMatrixLayout.COLOR_BACKGROUND);
        }

        int rowY = y0 + EntropicLayout.FILTER_PAGE_ROW_Y;
        int arrow = FarmMatrixLayout.FILTER_ARROW_SIZE;
        int page = menu.filterPage();
        extractArrow(graphics, "<", contentX, rowY, page > 0, mouseX, mouseY);
        extractArrow(graphics, ">", contentX + grid - arrow, rowY, page < MachineFilter.MAX_PAGES - 1, mouseX, mouseY);
        extractFittedCentered(graphics, Component.translatable("gui.virtualfarmworks.filter.page", page + 1,
                        menu.filterPageCount()), contentX + arrow + 1, rowY, grid - 2 * arrow - 2,
                FarmMatrixLayout.FILTER_PAGE_ROW_HEIGHT, FarmMatrixLayout.COLOR_TEXT);
    }

    private void extractArrow(GuiGraphicsExtractor graphics, String label, int x, int y, boolean enabled, int mouseX,
                              int mouseY) {
        int size = FarmMatrixLayout.FILTER_ARROW_SIZE;
        boolean hovered = enabled && isInside(mouseX, mouseY, x, y, size, size);
        graphics.fill(x, y, x + size, y + size, hovered ? FarmMatrixLayout.COLOR_BUTTON_ACTIVE : FarmMatrixLayout.COLOR_FRAME);
        extractFittedCentered(graphics, Component.literal(label), x, y, size, size, enabled ? 0xFFFFFFFF : 0x80FFFFFF);
    }

    /** Text centered in a box (absolute screen coordinates), shrunk if wider than the box. */
    private void extractFittedCentered(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int height,
                                       int color) {
        int textWidth = font.width(text);
        extractScaledCentered(graphics, text, x, y, width, height, textWidth > width ? (float) width / textWidth : 1.0F,
                color);
    }

    /** Text centered in a box (absolute screen coordinates) at a given scale. */
    private void extractScaledCentered(GuiGraphicsExtractor graphics, Component text, int x, int y, int width, int height,
                                       float scale, int color) {
        int textWidth = font.width(text);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + (width - textWidth * scale) / 2.0F, y + (height - 8 * scale) / 2.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    // =================================================================================================================
    // Autocrafter panel
    // =================================================================================================================

    /**
     * The panel over the dimmed grids (see the class doc): owner texture, the catalyst slot's frame (drawn here, it is
     * not on the texture) with its placeholder, SET CRAFT and CRAFT: ON/OFF, recipe list. The recipe grid, its result
     * and the catalyst are menu slots, drawn by vanilla on top.
     */
    private void extractCrafter(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        graphics.fill(x0 + EntropicLayout.GRIDS_X0, y0 + EntropicLayout.GRIDS_Y0, x0 + EntropicLayout.GRIDS_X1,
                y0 + EntropicLayout.GRIDS_Y1, EntropicLayout.COLOR_CRAFTER_BACKDROP);
        graphics.blit(RenderPipelines.GUI_TEXTURED, crafterTexture, x0 + EntropicLayout.CRAFTER_X,
                y0 + EntropicLayout.CRAFTER_Y, 0.0F, 0.0F, EntropicLayout.CRAFTER_WIDTH, EntropicLayout.CRAFTER_HEIGHT,
                EntropicLayout.CRAFTER_TEXTURE_WIDTH, EntropicLayout.CRAFTER_TEXTURE_HEIGHT);
        // Catalyst slot, glued below the result (owner): an 18x18 frame like the texture's slots, 1 px around the item.
        graphics.outline(x0 + EntropicLayout.CRAFTER_CATALYST_X - 1, y0 + EntropicLayout.CRAFTER_CATALYST_Y - 1,
                FarmMatrixLayout.CELL + 2, FarmMatrixLayout.CELL + 2, EntropicLayout.COLOR_CRAFTER_FRAME);
        extractGhost(graphics, x0, y0, LAYOUT.catalystSlot(), catalystGhost);

        Component set = Component.translatable("gui.virtualfarmworks.crafter.set");
        boolean on = menu.isCrafterEnabled();
        Component toggle = Component.translatable("gui.virtualfarmworks.crafter.toggle", onOff(on));
        float scale = EntropicLayout.CRAFTER_BUTTON_TEXT_SCALE;
        extractCrafterButton(graphics, x0, y0 + EntropicLayout.CRAFTER_SET_Y, set, canSetCraft()
                ? EntropicLayout.COLOR_CRAFTER_TEXT : EntropicLayout.COLOR_CRAFTER_TEXT_DISABLED, scale, mouseX, mouseY);
        extractCrafterButton(graphics, x0, y0 + EntropicLayout.CRAFTER_TOGGLE_Y, toggle, on
                ? EntropicLayout.COLOR_CRAFTER_ON : EntropicLayout.COLOR_CRAFTER_OFF, scale, mouseX, mouseY);
        extractRecipeList(graphics, x0, y0, mouseX, mouseY);
    }

    /** A panel button: its label, and a vertical gradient under the mouse (owner spec). */
    private void extractCrafterButton(GuiGraphicsExtractor graphics, int x0, int y, Component label, int color,
                                      float scale, int mouseX, int mouseY) {
        int x = x0 + EntropicLayout.CRAFTER_BUTTON_X;
        int width = EntropicLayout.CRAFTER_BUTTON_WIDTH;
        int height = EntropicLayout.CRAFTER_BUTTON_HEIGHT;
        if (isInside(mouseX, mouseY, x, y, width, height)) {
            graphics.fillGradient(x, y, x + width, y + height, EntropicLayout.COLOR_CRAFTER_HOVER_TOP,
                    EntropicLayout.COLOR_CRAFTER_HOVER_BOTTOM);
        }
        extractScaledCentered(graphics, label, x, y, width, height, scale, color);
    }

    /**
     * The recipe list: per row the result's icon (with its count) and name; the selected recipe highlighted; a
     * scrollbar when there are more recipes than rows.
     */
    private void extractRecipeList(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        int count = menu.crafterRecipeCount();
        recipeScroll = Math.clamp(recipeScroll, 0, Math.max(0, count - EntropicLayout.CRAFTER_ROWS));
        int listX = x0 + EntropicLayout.CRAFTER_LIST_X;
        int listY = y0 + EntropicLayout.CRAFTER_LIST_Y;
        if (count == 0) {
            extractFittedCentered(graphics, Component.translatable("gui.virtualfarmworks.crafter.empty"), listX, listY,
                    EntropicLayout.CRAFTER_LIST_WIDTH, EntropicLayout.CRAFTER_LIST_HEIGHT,
                    EntropicLayout.COLOR_CRAFTER_TEXT_DISABLED);
            return;
        }
        int rowWidth = recipeRowWidth(count);
        int selected = menu.selectedRecipe();
        int hovered = recipeAt(mouseX, mouseY);
        for (int row = 0; row < EntropicLayout.CRAFTER_ROWS && recipeScroll + row < count; row++) {
            int entry = recipeScroll + row;
            int rowY = y0 + EntropicLayout.CRAFTER_ROWS_Y + row * EntropicLayout.CRAFTER_ROW_HEIGHT;
            if (entry == selected || entry == hovered) {
                graphics.fill(listX, rowY, listX + rowWidth, rowY + EntropicLayout.CRAFTER_ROW_HEIGHT,
                        entry == selected ? EntropicLayout.COLOR_CRAFTER_SELECTED : EntropicLayout.COLOR_CRAFTER_ROW_HOVER);
            }
            ItemStack result = menu.crafterResult(entry);
            Component name;
            int color;
            if (result.isEmpty()) {
                name = Component.translatable("gui.virtualfarmworks.crafter.entry.invalid");
                color = EntropicLayout.COLOR_CRAFTER_OFF;
            } else {
                graphics.fakeItem(result, listX + 1, rowY);
                graphics.itemDecorations(font, result, listX + 1, rowY);
                name = result.getHoverName();
                color = EntropicLayout.COLOR_CRAFTER_TEXT;
            }
            graphics.text(font, ellipsize(name, rowWidth - 21), listX + 19, rowY + 4, color, false);
        }
        if (count > EntropicLayout.CRAFTER_ROWS) {
            int trackX = listX + EntropicLayout.CRAFTER_LIST_WIDTH - EntropicLayout.CRAFTER_SCROLLBAR_WIDTH;
            int trackHeight = EntropicLayout.CRAFTER_LIST_HEIGHT;
            int thumbHeight = Math.max(8, trackHeight * EntropicLayout.CRAFTER_ROWS / count);
            int maxScroll = count - EntropicLayout.CRAFTER_ROWS;
            int thumbY = listY + (trackHeight - thumbHeight) * recipeScroll / maxScroll;
            graphics.fill(trackX, listY, trackX + EntropicLayout.CRAFTER_SCROLLBAR_WIDTH, listY + trackHeight,
                    EntropicLayout.COLOR_CRAFTER_ROW_HOVER);
            graphics.fill(trackX, thumbY, trackX + EntropicLayout.CRAFTER_SCROLLBAR_WIDTH, thumbY + thumbHeight,
                    EntropicLayout.COLOR_CRAFTER_SELECTED);
        }
    }

    /** A text cut to {@code width} pixels, ending in "..." when it was longer. */
    private FormattedCharSequence ellipsize(Component text, int width) {
        if (font.width(text) <= width) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, width - font.width("...")));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of("...")));
    }

    /** Width of a list row: the whole list, minus the scrollbar when there is one. */
    private static int recipeRowWidth(int count) {
        return EntropicLayout.CRAFTER_LIST_WIDTH
                - (count > EntropicLayout.CRAFTER_ROWS ? EntropicLayout.CRAFTER_SCROLLBAR_WIDTH + 1 : 0);
    }

    /** SET CRAFT would do something: the grid makes a recipe, and a recipe is selected or the list has room. */
    private boolean canSetCraft() {
        return !menu.craftPreview().isEmpty()
                && (menu.selectedRecipe() >= 0 || menu.crafterRecipeCount() < menu.crafterRecipeLimit());
    }

    /** Text centered in a side-panel cell, shrunk if wider than the cell. */
    private void extractCellLabel(GuiGraphicsExtractor graphics, Component text, int x0, int cellY, int color) {
        extractFittedCentered(graphics, text, x0 + FarmMatrixLayout.PANEL_INTERIOR_X, cellY, FarmMatrixLayout.CELL,
                FarmMatrixLayout.CELL, color);
    }

    /**
     * 40% placeholders in the empty upgrade, crux and water slots, like the Starter (the catalyst gets its own in the
     * crafter panel). The 120 grid slots get none:
     * a full grid of faded seeds and farmland would hide what is really planted.
     */
    private void extractGhosts(GuiGraphicsExtractor graphics, int x0, int y0) {
        extractGhost(graphics, x0, y0, LAYOUT.waterSlot(), waterGhost);
        for (int i = 0; i < 4; i++) {
            extractGhost(graphics, x0, y0, LAYOUT.growthSlot(i), growthGhost);
        }
        extractGhost(graphics, x0, y0, LAYOUT.cruxSlot(), cruxGhost);
    }

    private void extractGhost(GuiGraphicsExtractor graphics, int x0, int y0, int menuIndex, ItemStack ghost) {
        Slot slot = menu.slots.get(menuIndex);
        if (ghost.isEmpty() || slot.hasItem() || !slot.isActive()) {
            return;
        }
        int x = x0 + slot.x;
        int y = y0 + slot.y;
        graphics.fakeItem(ghost, x, y);
        graphics.fill(x, y, x + 16, y + 16, FarmMatrixLayout.COLOR_GHOST_COVER);
    }

    /** Plot groups with a problem (owner decision): their seed slot gets a red tint; the tooltip names the problem. */
    private void extractGroupProblems(GuiGraphicsExtractor graphics, int x0, int y0) {
        for (int group = 0; group < LAYOUT.groups(); group++) {
            if (hasProblem(menu.groupStatus(group))) {
                Slot slot = menu.slots.get(LAYOUT.seedSlot(group));
                if (slot.isActive()) {
                    int x = x0 + slot.x;
                    int y = y0 + slot.y;
                    graphics.fill(x, y, x + 16, y + 16, EntropicLayout.COLOR_GROUP_PROBLEM);
                }
            }
        }
    }

    private static boolean hasProblem(MachineStatus groupStatus) {
        return groupStatus != MachineStatus.RUNNING && groupStatus != MachineStatus.MISSING_SEED;
    }

    // =================================================================================================================
    // Labels
    // =================================================================================================================

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Component title = getTitle();
        graphics.text(font, title, (EntropicLayout.GUI_WIDTH - font.width(title)) / 2, EntropicLayout.TITLE_Y,
                themeColor, false);

        MachineStatus status = menu.status();
        int[] lineY = EntropicLayout.INFO_LINE_Y;
        extractInfoLine(graphics, Component.translatable(status.translationKey()), lineY[0],
                0xFF000000 | status.tone().rgb());
        extractInfoLine(graphics, Component.translatable("gui.virtualfarmworks.hydration",
                DisplayFormats.multiplier(menu.hydrationMultiplier())), lineY[1], FarmMatrixLayout.COLOR_TEXT);
        extractInfoLine(graphics, Component.translatable("gui.virtualfarmworks.plots_active", menu.plantedPlots(),
                menu.plotCapacity()), lineY[2], FarmMatrixLayout.COLOR_TEXT);
        extractInfoLine(graphics, Component.translatable("gui.virtualfarmworks.plots_waiting", menu.waitingPlots()),
                lineY[3], FarmMatrixLayout.COLOR_TEXT);
        extractInfoLine(graphics, Component.translatable("gui.virtualfarmworks.growth",
                DisplayFormats.growthPercent(shownProgress, (int) Math.min(Integer.MAX_VALUE, menu.plantedPlots())),
                DisplayFormats.multiplier(menu.growthMultiplier())), lineY[4], FarmMatrixLayout.COLOR_TEXT);
    }

    private void extractInfoLine(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        int width = font.width(text);
        if (width <= EntropicLayout.INFO_WIDTH) {
            graphics.text(font, text, EntropicLayout.INFO_X, y, color, false);
            return;
        }
        float scale = (float) EntropicLayout.INFO_WIDTH / width;
        graphics.pose().pushMatrix();
        graphics.pose().translate(EntropicLayout.INFO_X, y + (1.0F - scale) * 4.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    // =================================================================================================================
    // JEI exclusion areas and ghost targets
    // =================================================================================================================

    /** Screen areas drawn outside the texture: the side column and the open boxes (see {@code VfwJeiPlugin}). */
    public List<Rect2i> extraAreas() {
        List<Rect2i> areas = new ArrayList<>(3);
        areas.add(new Rect2i(leftPos + FarmMatrixLayout.PANEL_BORDER_X, topPos + EntropicLayout.OUTPUT_BOX_TOP,
                -FarmMatrixLayout.PANEL_BORDER_X, EntropicLayout.PANEL_BOTTOM - EntropicLayout.OUTPUT_BOX_TOP));
        if (faceBoxOpen) {
            int size = FarmMatrixLayout.FACE_BOX_SIZE + 2;
            areas.add(new Rect2i(leftPos + EntropicLayout.FACE_BOX_X - 1, topPos + EntropicLayout.FACE_BOX_Y - 1,
                    size, size));
        }
        if (filterBoxOpen) {
            areas.add(new Rect2i(leftPos + EntropicLayout.FILTER_BOX_X, topPos + EntropicLayout.FILTER_BOX_Y,
                    FarmMatrixLayout.FILTER_BOX_WIDTH, FarmMatrixLayout.FILTER_BOX_HEIGHT));
        }
        return areas;
    }

    /** Screen areas of the filter's ghost slots while the box is open (JEI drag-and-drop). */
    public List<Rect2i> filterSlotAreas() {
        if (!filterBoxOpen) {
            return List.of();
        }
        List<Rect2i> areas = new ArrayList<>(MachineFilter.PAGE_SIZE);
        for (int i = 0; i < MachineFilter.PAGE_SIZE; i++) {
            areas.add(new Rect2i(leftPos + EntropicLayout.filterSlotX(i), topPos + EntropicLayout.filterSlotY(i),
                    FarmMatrixLayout.CELL, FarmMatrixLayout.CELL));
        }
        return areas;
    }

    /** Client: an item was dropped from JEI on filter ghost slot {@code slot}; the server records it. */
    public void setFilterGhostFromJei(int slot, ItemStack stack) {
        ClientPacketDistributor.sendToServer(new SetFilterGhostPayload(menu.containerId, slot, stack.copyWithCount(1)));
    }

    /** Screen areas of the autocrafter's recipe cells while its panel is open (JEI drag-and-drop). */
    @Override
    public List<Rect2i> crafterCellAreas() {
        if (!menu.isCrafterVisible()) {
            return List.of();
        }
        List<Rect2i> areas = new ArrayList<>(MachineCrafter.GRID_SIZE);
        for (int i = 0; i < MachineCrafter.GRID_SIZE; i++) {
            areas.add(new Rect2i(leftPos + EntropicLayout.crafterGridX(i), topPos + EntropicLayout.crafterGridY(i),
                    FarmMatrixLayout.CELL, FarmMatrixLayout.CELL));
        }
        return areas;
    }

    /** Client: an item was dropped from JEI on recipe cell {@code cell}; the server receives the whole new grid. */
    @Override
    public void setCrafterCellFromJei(int cell, ItemStack stack) {
        List<ItemStack> grid = menu.gridStacks();
        grid.set(cell, stack.copyWithCount(1));
        ClientPacketDistributor.sendToServer(new SetCrafterGridPayload(menu.containerId, grid));
    }

    // =================================================================================================================
    // Tooltips
    // =================================================================================================================

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            return; // the item's own tooltip is shown (with the group's problem added, see below)
        }
        List<Component> lines = tooltipAt(mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /**
     * A seed in a plot group with a problem: the item tooltip ends with that problem. An item in the recipe grid: how
     * to change it.
     */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack itemStack) {
        List<Component> lines = super.getTooltipFromContainerItem(itemStack);
        if (hoveredSlot != null && isCraftGridSlot(hoveredSlot.index)) {
            lines = new ArrayList<>(lines);
            lines.add(Component.translatable("gui.virtualfarmworks.crafter.grid").withColor(0xFFAAAAAA));
            return lines;
        }
        if (hoveredSlot != null && LAYOUT.isSeedSlot(hoveredSlot.index)) {
            MachineStatus groupStatus = menu.groupStatus(hoveredSlot.index);
            if (hasProblem(groupStatus)) {
                lines = new ArrayList<>(lines);
                lines.add(Component.translatable("gui.virtualfarmworks.group_problem",
                        Component.translatable(groupStatus.translationKey()))
                        .withColor(0xFF000000 | groupStatus.tone().rgb()));
            }
        }
        return lines;
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (menu.isCrafterVisible() && isInCrafter(mouseX, mouseY)) {
            crafterTooltip(mouseX, mouseY, lines);
        } else if (isOverBox(EntropicLayout.OUTPUT_BOX_TOP, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.output_sides"));
        } else if (isOverBox(EntropicLayout.ENERGY_BOX_TOP, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.energy_per_plot", menu.energyPerPlot()));
            lines.add(Component.translatable("gui.virtualfarmworks.energy_using", menu.energyUse()));
        } else if (isOverBox(EntropicLayout.REPLANT_BUTTON_TOP, mouseX, mouseY)) {
            FarmMatrixBlockEntity.ReplantState replant = menu.replantState();
            lines.add(Component.translatable("gui.virtualfarmworks.replant",
                    replant == FarmMatrixBlockEntity.ReplantState.DISABLED
                            ? Component.translatable("gui.virtualfarmworks.disabled")
                            : onOff(replant == FarmMatrixBlockEntity.ReplantState.ON)));
            lines.add(Component.translatable("gui.virtualfarmworks.replant.hint"));
            if (replant == FarmMatrixBlockEntity.ReplantState.DISABLED) {
                lines.add(Component.translatable("gui.virtualfarmworks.replant.disabled")
                        .withColor(0xFF000000 | EntropicLayout.COLOR_CRAFTER_OFF));
            }
        } else if (isOverBox(EntropicLayout.POWER_BOX_TOP, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.power", onOff(menu.isEnabled())));
        } else if (isOverBox(EntropicLayout.FERTILIZED_BOX_TOP, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.fertilized_essence",
                    onOff(menu.isFertilizedEssenceEnabled())));
            lines.add(Component.translatable("gui.virtualfarmworks.fertilized_essence.hint"));
        } else if (isOverBox(EntropicLayout.FILTER_BUTTON_TOP, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.filter", Component.translatable(menu.isFilterWhitelist()
                    ? "gui.virtualfarmworks.filter.whitelisted" : "gui.virtualfarmworks.filter.blacklisted")));
        } else if (isOverBox(EntropicLayout.CRAFTER_BUTTON_TOP, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.crafter"));
            lines.add(Component.translatable("gui.virtualfarmworks.crafter.button_hint", menu.crafterRecipeCount(),
                    menu.crafterRecipeLimit(), onOff(menu.isCrafterEnabled())));
        } else if (faceBoxOpen && faceAt(mouseX, mouseY) != null) {
            RelativeSide side = faceAt(mouseX, mouseY);
            lines.add(Component.translatable("gui.virtualfarmworks.face_mode", Component.translatable(side.translationKey()),
                    Component.translatable(menu.faceMode(side).translationKey())));
            lines.add(Component.translatable("gui.virtualfarmworks.face_mode.hint"));
        } else if (filterBoxOpen && isInFilterStrip(mouseX, mouseY)) {
            boolean whitelist = menu.isFilterWhitelist();
            lines.add(Component.translatable(whitelist ? "gui.virtualfarmworks.filter.whitelist_hint"
                    : "gui.virtualfarmworks.filter.blacklist_hint"));
            lines.add(Component.translatable("gui.virtualfarmworks.filter.empty_hint"));
            lines.add(Component.translatable(whitelist ? "gui.virtualfarmworks.filter.switch_to_blacklist"
                    : "gui.virtualfarmworks.filter.switch_to_whitelist"));
        } else if (filterBoxOpen && isOverFilterArrow(false, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.filter.previous"));
        } else if (filterBoxOpen && isOverFilterArrow(true, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.filter.next"));
        } else if (isOverEnergyBar(mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.energy", menu.energy(), menu.energyCapacity()));
        } else if (hoveredSlot != null && menu.isFilterSlotIndex(hoveredSlot.index)) {
            lines.add(Component.translatable("gui.virtualfarmworks.filter.slot"));
            lines.add(Component.translatable("gui.virtualfarmworks.filter.slot_remove"));
        } else if (hoveredSlot != null && menu.getCarried().isEmpty() && hoveredSlot.index < LAYOUT.inputCount()) {
            lines.addAll(slotDescription(hoveredSlot.index));
        }
        return lines;
    }

    /** Tooltips inside the autocrafter panel: buttons, recipe list, empty recipe cells. */
    private void crafterTooltip(int mouseX, int mouseY, List<Component> lines) {
        if (isOverCrafterButton(EntropicLayout.CRAFTER_SET_Y, mouseX, mouseY)) {
            String key = menu.craftPreview().isEmpty() ? "gui.virtualfarmworks.crafter.set.invalid"
                    : menu.selectedRecipe() >= 0 ? "gui.virtualfarmworks.crafter.set.replace"
                    : menu.crafterRecipeCount() >= menu.crafterRecipeLimit() ? "gui.virtualfarmworks.crafter.set.full"
                    : "gui.virtualfarmworks.crafter.set.add";
            lines.add(Component.translatable(key, menu.crafterRecipeCount(), menu.crafterRecipeLimit()));
        } else if (isOverCrafterButton(EntropicLayout.CRAFTER_TOGGLE_Y, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.crafter.toggle", onOff(menu.isCrafterEnabled())));
            lines.add(Component.translatable("gui.virtualfarmworks.crafter.toggle.hint"));
            if (menu.isCrafterEnabled()) {
                lines.add(Component.translatable("gui.virtualfarmworks.crafter.toggle.off_hint"));
            }
        } else if (recipeAt(mouseX, mouseY) >= 0) {
            ItemStack result = menu.crafterResult(recipeAt(mouseX, mouseY));
            if (result.isEmpty()) {
                lines.add(Component.translatable("gui.virtualfarmworks.crafter.entry.invalid"));
            } else {
                lines.addAll(getTooltipFromItem(minecraft, result));
                lines.add(Component.translatable("gui.virtualfarmworks.crafter.entry.edit").withColor(0xFFAAAAAA));
            }
            lines.add(Component.translatable("gui.virtualfarmworks.crafter.entry.delete").withColor(0xFFAAAAAA));
        } else if (hoveredSlot != null && isCraftGridSlot(hoveredSlot.index) && menu.getCarried().isEmpty()) {
            lines.add(Component.translatable("gui.virtualfarmworks.crafter.grid"));
            lines.add(Component.translatable("gui.virtualfarmworks.crafter.grid.jei"));
        } else if (hoveredSlot != null && hoveredSlot.index == LAYOUT.catalystSlot() && menu.getCarried().isEmpty()) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.catalyst"));
            lines.add(Component.translatable("gui.virtualfarmworks.slot.catalyst.hint").withColor(0xFFAAAAAA));
        }
    }

    private static boolean isCraftGridSlot(int index) {
        return index >= EntropicFarmMatrixMenu.CRAFT_GRID_START && index < EntropicFarmMatrixMenu.CRAFT_RESULT;
    }

    private List<Component> slotDescription(int index) {
        List<Component> lines = new ArrayList<>(2);
        if (LAYOUT.isSeedSlot(index)) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.seed_grid", slotLimit(index)));
        } else if (LAYOUT.isSoilSlot(index)) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.soil_grid", slotLimit(index)));
        } else if (index == LAYOUT.waterSlot()) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.water_provider"));
        } else if (index == LAYOUT.catalystSlot()) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.catalyst"));
        } else if (index == LAYOUT.cruxSlot()) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.crux"));
        } else {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.growth"));
        }
        return lines;
    }

    private int slotLimit(int index) {
        return menu.slots.get(index).getMaxStackSize();
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "gui.virtualfarmworks.on" : "gui.virtualfarmworks.off");
    }

    // =================================================================================================================
    // Input
    // =================================================================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 1 && faceBoxOpen) {
            RelativeSide side = faceAt(mouseX, mouseY);
            if (side != null) {
                sendButton(AbstractFarmMatrixMenu.BUTTON_FACE_BACK_FIRST + side.ordinal()); // previous mode
                return true;
            }
        }
        if (event.button() == 0) {
            if (isOverBox(EntropicLayout.CRAFTER_BUTTON_TOP, mouseX, mouseY)) {
                menu.setCrafterVisible(!menu.isCrafterVisible());
                playClick();
                return true;
            }
            if (menu.isCrafterVisible() && clickCrafter(mouseX, mouseY, doubleClick)) {
                return true;
            }
            if (isOverBox(EntropicLayout.OUTPUT_BOX_TOP, mouseX, mouseY)) {
                faceBoxOpen = !faceBoxOpen;
                playClick();
                return true;
            }
            if (isOverBox(EntropicLayout.FILTER_BUTTON_TOP, mouseX, mouseY)) {
                filterBoxOpen = !filterBoxOpen;
                menu.setFilterVisible(filterBoxOpen);
                playClick();
                return true;
            }
            if (filterBoxOpen) {
                if (isInFilterStrip(mouseX, mouseY)) {
                    sendButton(AbstractFarmMatrixMenu.BUTTON_FILTER_MODE);
                    return true;
                }
                if (isOverFilterArrow(false, mouseX, mouseY)) {
                    sendButton(AbstractFarmMatrixMenu.BUTTON_FILTER_PREVIOUS);
                    return true;
                }
                if (isOverFilterArrow(true, mouseX, mouseY)) {
                    sendButton(AbstractFarmMatrixMenu.BUTTON_FILTER_NEXT);
                    return true;
                }
            }
            if (isOverBox(EntropicLayout.POWER_BOX_TOP, mouseX, mouseY)) {
                sendButton(AbstractFarmMatrixMenu.BUTTON_POWER);
                return true;
            }
            if (isOverBox(EntropicLayout.FERTILIZED_BOX_TOP, mouseX, mouseY)) {
                sendButton(AbstractFarmMatrixMenu.BUTTON_FERTILIZED);
                return true;
            }
            if (isOverBox(EntropicLayout.REPLANT_BUTTON_TOP, mouseX, mouseY)) {
                if (menu.replantState() != FarmMatrixBlockEntity.ReplantState.DISABLED) {
                    sendButton(EntropicFarmMatrixMenu.BUTTON_REPLANT);
                }
                return true;
            }
            if (isOverBox(EntropicLayout.ENERGY_BOX_TOP, mouseX, mouseY)) {
                return true; // information only (owner: "not a button")
            }
            if (faceBoxOpen) {
                RelativeSide side = faceAt(mouseX, mouseY);
                if (side != null) {
                    sendButton(AbstractFarmMatrixMenu.BUTTON_FACE_FIRST + side.ordinal()); // next mode
                    return true;
                }
                if (isInFaceBox(mouseX, mouseY)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * A left click inside the autocrafter panel: its buttons and recipe list (the recipe grid is menu slots, handled by
     * vanilla). Returns false for clicks the panel does not use.
     */
    private boolean clickCrafter(double mouseX, double mouseY, boolean doubleClick) {
        if (isOverCrafterButton(EntropicLayout.CRAFTER_SET_Y, mouseX, mouseY)) {
            if (canSetCraft()) {
                sendButton(EntropicFarmMatrixMenu.BUTTON_CRAFT_SET);
            }
            return true;
        }
        if (isOverCrafterButton(EntropicLayout.CRAFTER_TOGGLE_Y, mouseX, mouseY)) {
            sendButton(EntropicFarmMatrixMenu.BUTTON_CRAFT_TOGGLE);
            return true;
        }
        if (!isInRecipeList(mouseX, mouseY)) {
            return false;
        }
        int count = menu.crafterRecipeCount();
        int trackX = leftPos + EntropicLayout.CRAFTER_LIST_X + EntropicLayout.CRAFTER_LIST_WIDTH
                - EntropicLayout.CRAFTER_SCROLLBAR_WIDTH;
        if (count > EntropicLayout.CRAFTER_ROWS && mouseX >= trackX) {
            // Scrollbar track: a page toward the click.
            int listMiddle = topPos + EntropicLayout.CRAFTER_LIST_Y + EntropicLayout.CRAFTER_LIST_HEIGHT / 2;
            recipeScroll += mouseY < listMiddle ? -EntropicLayout.CRAFTER_ROWS : EntropicLayout.CRAFTER_ROWS;
            recipeScroll = Math.clamp(recipeScroll, 0, count - EntropicLayout.CRAFTER_ROWS);
            return true;
        }
        int entry = recipeAt(mouseX, mouseY);
        if (entry < 0) {
            if (menu.selectedRecipe() >= 0) {
                sendButton(EntropicFarmMatrixMenu.BUTTON_CRAFT_DESELECT); // empty list space: nothing selected
            }
        } else if (doubleClick && entry == lastClickedRecipe) {
            sendButton(EntropicFarmMatrixMenu.BUTTON_CRAFT_DELETE_FIRST + entry);
            lastClickedRecipe = -1;
        } else {
            sendButton(EntropicFarmMatrixMenu.BUTTON_CRAFT_SELECT_FIRST + entry);
            lastClickedRecipe = entry;
        }
        return true;
    }

    /** The mouse wheel scrolls the recipe list while the panel is open. */
    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (menu.isCrafterVisible() && isInRecipeList(x, y) && scrollY != 0.0) {
            int maxScroll = Math.max(0, menu.crafterRecipeCount() - EntropicLayout.CRAFTER_ROWS);
            recipeScroll = Math.clamp(recipeScroll - (long) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    /** Clicks on the side column and open boxes are inside the GUI (not a throw of the carried item). */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        if (isInSidePanel(mouseX, mouseY) || (faceBoxOpen && isInFaceBox(mouseX, mouseY))
                || (filterBoxOpen && isInFilterBox(mouseX, mouseY))) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    private void sendButton(int id) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
        playClick();
    }

    private void playClick() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // =================================================================================================================
    // Hit testing
    // =================================================================================================================

    private boolean isOverBox(int top, double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + FarmMatrixLayout.PANEL_BORDER_X, topPos + top,
                -FarmMatrixLayout.PANEL_BORDER_X, FarmMatrixLayout.boxHeight(1));
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isInSidePanel(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + FarmMatrixLayout.PANEL_BORDER_X, topPos + EntropicLayout.OUTPUT_BOX_TOP,
                -FarmMatrixLayout.PANEL_BORDER_X, EntropicLayout.PANEL_BOTTOM - EntropicLayout.OUTPUT_BOX_TOP);
    }

    private boolean isInFaceBox(double mouseX, double mouseY) {
        int size = FarmMatrixLayout.FACE_BOX_SIZE + 2;
        return isInside(mouseX, mouseY, leftPos + EntropicLayout.FACE_BOX_X - 1, topPos + EntropicLayout.FACE_BOX_Y - 1,
                size, size);
    }

    private boolean isInFilterBox(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + EntropicLayout.FILTER_BOX_X, topPos + EntropicLayout.FILTER_BOX_Y,
                FarmMatrixLayout.FILTER_BOX_WIDTH, FarmMatrixLayout.FILTER_BOX_HEIGHT);
    }

    private boolean isInFilterStrip(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + EntropicLayout.FILTER_CONTENT_X, topPos + EntropicLayout.FILTER_STRIP_Y,
                FarmMatrixLayout.FILTER_GRID_SIZE, FarmMatrixLayout.FILTER_STRIP_HEIGHT);
    }

    private boolean isOverFilterArrow(boolean next, double mouseX, double mouseY) {
        int size = FarmMatrixLayout.FILTER_ARROW_SIZE;
        int x = leftPos + EntropicLayout.FILTER_CONTENT_X + (next ? FarmMatrixLayout.FILTER_GRID_SIZE - size : 0);
        return isInside(mouseX, mouseY, x, topPos + EntropicLayout.FILTER_PAGE_ROW_Y, size, size);
    }

    private boolean isInCrafter(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + EntropicLayout.CRAFTER_X, topPos + EntropicLayout.CRAFTER_Y,
                EntropicLayout.CRAFTER_WIDTH, EntropicLayout.CRAFTER_HEIGHT);
    }

    private boolean isOverCrafterButton(int y, double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + EntropicLayout.CRAFTER_BUTTON_X, topPos + y,
                EntropicLayout.CRAFTER_BUTTON_WIDTH, EntropicLayout.CRAFTER_BUTTON_HEIGHT);
    }

    private boolean isInRecipeList(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + EntropicLayout.CRAFTER_LIST_X, topPos + EntropicLayout.CRAFTER_LIST_Y,
                EntropicLayout.CRAFTER_LIST_WIDTH, EntropicLayout.CRAFTER_LIST_HEIGHT);
    }

    /** The recipe under the mouse in the list, or -1 (outside the rows, empty space, scrollbar, panel closed). */
    private int recipeAt(double mouseX, double mouseY) {
        int count = menu.crafterRecipeCount();
        if (!menu.isCrafterVisible() || count == 0) {
            return -1;
        }
        int x = leftPos + EntropicLayout.CRAFTER_LIST_X;
        int rowsY = topPos + EntropicLayout.CRAFTER_ROWS_Y;
        if (mouseX < x || mouseX >= x + recipeRowWidth(count) || mouseY < rowsY) {
            return -1;
        }
        int row = (int) ((mouseY - rowsY) / EntropicLayout.CRAFTER_ROW_HEIGHT);
        int entry = recipeScroll + row;
        return row < EntropicLayout.CRAFTER_ROWS && entry < count ? entry : -1;
    }

    private boolean isOverEnergyBar(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + EntropicLayout.ENERGY_BAR_X, topPos + EntropicLayout.ENERGY_BAR_Y - 1,
                EntropicLayout.ENERGY_BAR_WIDTH, EntropicLayout.ENERGY_BAR_HEIGHT + 2);
    }

    private @Nullable RelativeSide faceAt(double mouseX, double mouseY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                RelativeSide side = FarmMatrixLayout.FACE_GRID[row][col];
                if (side != null && isInside(mouseX, mouseY, leftPos + EntropicLayout.faceCellX(col),
                        topPos + EntropicLayout.faceCellY(row), FarmMatrixLayout.FACE_CELL, FarmMatrixLayout.FACE_CELL)) {
                    return side;
                }
            }
        }
        return null;
    }
}
