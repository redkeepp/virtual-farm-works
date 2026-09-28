/*
 * FarmMatrixScreen — the Farm Matrix GUI (client only): the owner's texture, the dynamic texts (title, status,
 * hydration, seeds, growth), the green progress bar, 40% ghost placeholders in empty slots, and the side column drawn
 * by code and glued to the texture (auto-output button + face box, 5-cell upgrade block, Fertilized Essence switch,
 * power button).
 */
package com.virtualfarmworks.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.DisplayFormats;
import com.virtualfarmworks.menu.FarmMatrixLayout;
import com.virtualfarmworks.menu.FarmMatrixMenu;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Draws only; every action is sent to the server as a menu button click ({@link FarmMatrixMenu#clickMenuButton}),
 * and every number comes from the menu's synced data. Nothing here decides gameplay.
 *
 * <p>Render order inside a frame (26.1 "extract" GUI API): {@link #extractBackground} (texture, bar, side panel, face
 * box, ghost items) -> slots and their items (vanilla) -> {@link #extractLabels} (texts, translated to the GUI
 * origin) -> tooltips. Ghost items are covered by a translucent rectangle drawn right after them; the GUI renderer puts
 * an element that overlaps an earlier item on a higher layer, which is what makes the item look 40% opaque (vanilla
 * recipe-book technique).
 */
public class FarmMatrixScreen extends AbstractContainerScreen<FarmMatrixMenu> {
    private final Identifier texture;
    private final int themeColor;
    /** Placeholder shown in each empty input slot, by {@link MachineSlots} index (owner spec). */
    private final ItemStack[] ghosts;
    /** Whether the auto-output face box is open (client-only UI state). */
    private boolean faceBoxOpen;
    /** Smooths the 5-tick progress syncs into continuous movement (owner request, step 8). */
    private final SmoothProgress smoothProgress = new SmoothProgress();
    /** Progress drawn in the current frame, shared by the bar and the Growth line so both always agree. */
    private double shownProgress;

    public FarmMatrixScreen(FarmMatrixMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, FarmMatrixLayout.GUI_WIDTH, FarmMatrixLayout.GUI_HEIGHT);
        MachineTier tier = menu.tier();
        this.texture = Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID,
                "textures/gui/" + tier.getSerializedName() + "_farm_matrix_gui.png");
        this.themeColor = 0xFF000000 | tier.themeColor();
        this.ghosts = new ItemStack[MachineSlots.INPUT_COUNT];
        ghosts[MachineSlots.SEED] = new ItemStack(Items.WHEAT_SEEDS);
        ghosts[MachineSlots.SOIL] = new ItemStack(Items.FARMLAND);
        ghosts[MachineSlots.WATER_PROVIDER] = new ItemStack(ModItems.WATER_PROVIDER_UPGRADES.get(MachineTier.STARTER).get());
        ghosts[MachineSlots.HOE] = new ItemStack(Items.STONE_HOE);
        for (int i = 0; i < MachineSlots.GROWTH_COUNT; i++) {
            ghosts[MachineSlots.GROWTH_FIRST + i] =
                    new ItemStack(ModItems.GROWTH_SPEED_UPGRADES.get(MachineTier.STARTER).get());
        }
        ghosts[MachineSlots.CRUX_PROVIDER] = new ItemStack(ModItems.CRUX_PROVIDER_UPGRADE.get());
    }

    // =================================================================================================================
    // Background layer
    // =================================================================================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        // Once per frame, before the bar (here) and the Growth line (extractLabels, drawn later in the same frame). Until
        // the server's first value arrives the menu reads 0: show nothing rather than animating up from that 0.
        shownProgress = menu.isDataSynced()
                ? smoothProgress.update(menu.progress(), menu.harvestCount(), Util.getMillis())
                : 0.0;
        int x0 = leftPos;
        int y0 = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x0, y0, 0.0F, 0.0F, FarmMatrixLayout.GUI_WIDTH,
                FarmMatrixLayout.GUI_HEIGHT, FarmMatrixLayout.TEXTURE_SIZE, FarmMatrixLayout.TEXTURE_SIZE);
        extractProgressBar(graphics, x0, y0);
        extractSidePanel(graphics, x0, y0, mouseX, mouseY);
        if (faceBoxOpen) {
            extractFaceBox(graphics, x0, y0, mouseX, mouseY);
        }
        extractGhosts(graphics, x0, y0);
    }

    /** Green fill of the owner's bar area, proportional to the (smoothed) cycle progress. */
    private void extractProgressBar(GuiGraphicsExtractor graphics, int x0, int y0) {
        int filled = (int) Math.round(shownProgress * FarmMatrixLayout.BAR_WIDTH);
        if (filled > 0) {
            int x = x0 + FarmMatrixLayout.BAR_X;
            int y = y0 + FarmMatrixLayout.BAR_Y;
            graphics.fill(x, y, x + filled, y + FarmMatrixLayout.BAR_HEIGHT, FarmMatrixLayout.COLOR_BAR);
        }
    }

    /**
     * The side column glued to the texture (see {@link FarmMatrixLayout}): the "O" box, the 5-cell upgrade block, the
     * Fertilized Essence ON/OFF box and the machine ON/OFF box. No theme border on the right: the texture's own white
     * border is the column's right edge.
     */
    private void extractSidePanel(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        int outputTop = y0 + FarmMatrixLayout.OUTPUT_BOX_TOP;
        boolean outputActive = faceBoxOpen || isOverBox(FarmMatrixLayout.OUTPUT_BOX_TOP, 1, mouseX, mouseY);
        extractPanelBox(graphics, x0, outputTop, 1,
                outputActive ? FarmMatrixLayout.COLOR_BUTTON_ACTIVE : FarmMatrixLayout.COLOR_BACKGROUND);
        extractCellLabel(graphics, Component.literal("O"), x0, FarmMatrixLayout.cellY(outputTop, 0), themeColor);

        extractPanelBox(graphics, x0, y0 + FarmMatrixLayout.UPGRADE_BOX_TOP, FarmMatrixLayout.UPGRADE_SLOTS,
                FarmMatrixLayout.COLOR_BACKGROUND);

        // Fertilized Essence switch: light pink = generated, dark pink = not generated (owner spec).
        int fertilizedTop = y0 + FarmMatrixLayout.FERTILIZED_BOX_TOP;
        boolean fertilized = menu.isFertilizedEssenceEnabled();
        extractPanelBox(graphics, x0, fertilizedTop, 1,
                fertilized ? FarmMatrixLayout.COLOR_FERTILIZED_ON : FarmMatrixLayout.COLOR_FERTILIZED_OFF);
        extractCellLabel(graphics, Component.translatable(fertilized ? "gui.virtualfarmworks.on" : "gui.virtualfarmworks.off"),
                x0, FarmMatrixLayout.cellY(fertilizedTop, 0), 0xFFFFFFFF);

        int powerTop = y0 + FarmMatrixLayout.POWER_BOX_TOP;
        boolean on = menu.isEnabled();
        extractPanelBox(graphics, x0, powerTop, 1, on ? FarmMatrixLayout.COLOR_ON : FarmMatrixLayout.COLOR_OFF);
        extractCellLabel(graphics, Component.translatable(on ? "gui.virtualfarmworks.on" : "gui.virtualfarmworks.off"),
                x0, FarmMatrixLayout.cellY(powerTop, 0), 0xFFFFFFFF);
    }

    /**
     * One side-panel box with {@code cells} stacked cells, painted as three nested rectangles so every line is exactly
     * one pixel: theme color (left/top/bottom border; its right column is covered by the next rectangle), blue frame
     * (whose right column is x = -1, touching the texture's white border at x = 0), then each 16x16 interior. The blue
     * rows left between two interiors are the single shared separator line the owner asked for.
     */
    private void extractPanelBox(GuiGraphicsExtractor graphics, int x0, int top, int cells, int interior) {
        int bottom = top + FarmMatrixLayout.boxHeight(cells); // exclusive
        int right = x0; // exclusive: x = 0 belongs to the texture
        graphics.fill(x0 + FarmMatrixLayout.PANEL_BORDER_X, top, right, bottom, themeColor);
        graphics.fill(x0 + FarmMatrixLayout.PANEL_BORDER_X + 1, top + 1, right, bottom - 1, FarmMatrixLayout.COLOR_FRAME);
        int interiorX = x0 + FarmMatrixLayout.PANEL_INTERIOR_X;
        for (int i = 0; i < cells; i++) {
            int y = FarmMatrixLayout.cellY(top, i);
            graphics.fill(interiorX, y, interiorX + FarmMatrixLayout.CELL, y + FarmMatrixLayout.CELL, interior);
        }
    }

    /** The auto-output face box, owner layout (see {@link FarmMatrixLayout#FACE_GRID}). */
    private void extractFaceBox(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        int boxX = x0 + FarmMatrixLayout.FACE_BOX_X;
        int boxY = y0 + FarmMatrixLayout.FACE_BOX_Y;
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
                int cx = x0 + FarmMatrixLayout.faceCellX(col);
                int cy = y0 + FarmMatrixLayout.faceCellY(row);
                int cell = FarmMatrixLayout.FACE_CELL;
                graphics.fill(cx, cy, cx + cell, cy + cell,
                        menu.isOutputEnabled(side) ? FarmMatrixLayout.COLOR_ON : FarmMatrixLayout.COLOR_OFF);
                if (isInside(mouseX, mouseY, cx, cy, cell, cell)) {
                    graphics.fill(cx, cy, cx + cell, cy + cell, 0x40FFFFFF); // hover highlight
                }
                Component label = Component.translatable(side.translationKey() + ".short");
                int textX = cx + (cell - font.width(label)) / 2;
                graphics.text(font, label, textX, cy + 4, 0xFFFFFFFF, false);
            }
        }
    }

    /** 40% placeholders in empty machine input slots. */
    private void extractGhosts(GuiGraphicsExtractor graphics, int x0, int y0) {
        for (int i = 0; i < MachineSlots.INPUT_COUNT; i++) {
            Slot slot = menu.slots.get(FarmMatrixMenu.INPUT_START + i);
            if (slot.hasItem()) {
                continue;
            }
            int x = x0 + slot.x;
            int y = y0 + slot.y;
            graphics.fakeItem(ghosts[i], x, y);
            graphics.fill(x, y, x + 16, y + 16, FarmMatrixLayout.COLOR_GHOST_COVER);
        }
    }

    /** Text centered in a side-panel cell (16x16 interior at y {@code cellY}), shrunk if wider than the cell. */
    private void extractCellLabel(GuiGraphicsExtractor graphics, Component text, int x0, int cellY, int color) {
        int cell = FarmMatrixLayout.CELL;
        int width = font.width(text);
        float scale = width > cell ? (float) cell / width : 1.0F;
        float drawnWidth = width * scale;
        float drawnHeight = 8 * scale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0 + FarmMatrixLayout.PANEL_INTERIOR_X + (cell - drawnWidth) / 2.0F,
                cellY + (cell - drawnHeight) / 2.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    // =================================================================================================================
    // Labels (drawn translated to the GUI origin)
    // =================================================================================================================

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // Title: centered, theme color (owner spec). No "Inventory" label: the owner's layout only has these texts.
        Component title = getTitle();
        graphics.text(font, title, (FarmMatrixLayout.GUI_WIDTH - font.width(title)) / 2, FarmMatrixLayout.TITLE_Y,
                themeColor, false);

        MachineStatus status = menu.status();
        int[] lineY = FarmMatrixLayout.INFO_LINE_Y;
        extractFittedText(graphics, Component.translatable(status.translationKey()), lineY[0],
                0xFF000000 | status.tone().rgb());
        extractFittedText(graphics, Component.translatable("gui.virtualfarmworks.hydration",
                DisplayFormats.multiplier(menu.hydrationMultiplier())), lineY[1], FarmMatrixLayout.COLOR_TEXT);
        extractFittedText(graphics, Component.translatable("gui.virtualfarmworks.seeds", menu.plots(),
                MachineSlots.SEED_SOIL_LIMIT), lineY[2], FarmMatrixLayout.COLOR_TEXT);
        extractFittedText(graphics, Component.translatable("gui.virtualfarmworks.growth",
                DisplayFormats.growthPercent(shownProgress, menu.plots()),
                DisplayFormats.multiplier(menu.growthMultiplier())), lineY[3], FarmMatrixLayout.COLOR_TEXT);
    }

    /** Info line at {@link FarmMatrixLayout#INFO_X}, shrunk to the panel width if needed (long translations). */
    private void extractFittedText(GuiGraphicsExtractor graphics, Component text, int y, int color) {
        int width = font.width(text);
        if (width <= FarmMatrixLayout.INFO_WIDTH) {
            graphics.text(font, text, FarmMatrixLayout.INFO_X, y, color, false);
            return;
        }
        float scale = (float) FarmMatrixLayout.INFO_WIDTH / width;
        graphics.pose().pushMatrix();
        graphics.pose().translate(FarmMatrixLayout.INFO_X, y + (1.0F - scale) * 4.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    // =================================================================================================================
    // JEI (and future EMI) exclusion areas
    // =================================================================================================================

    /**
     * Screen areas drawn outside the texture: the side column, and the face box while it is open. Recipe viewers use
     * this to keep their item list and bookmarks off our side panel (see {@code client.compat.VfwJeiPlugin}).
     */
    public List<Rect2i> extraAreas() {
        List<Rect2i> areas = new ArrayList<>(2);
        areas.add(new Rect2i(leftPos + FarmMatrixLayout.PANEL_BORDER_X, topPos + FarmMatrixLayout.OUTPUT_BOX_TOP,
                -FarmMatrixLayout.PANEL_BORDER_X, FarmMatrixLayout.PANEL_BOTTOM - FarmMatrixLayout.OUTPUT_BOX_TOP));
        if (faceBoxOpen) {
            int size = FarmMatrixLayout.FACE_BOX_SIZE + 2;
            areas.add(new Rect2i(leftPos + FarmMatrixLayout.FACE_BOX_X - 1, topPos + FarmMatrixLayout.FACE_BOX_Y - 1,
                    size, size));
        }
        return areas;
    }

    // =================================================================================================================
    // Tooltips
    // =================================================================================================================

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot != null && hoveredSlot.hasItem()) {
            return; // the item's own tooltip is shown
        }
        List<Component> lines = tooltipAt(mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (isOverBox(FarmMatrixLayout.OUTPUT_BOX_TOP, 1, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.output_sides"));
        } else if (isOverBox(FarmMatrixLayout.POWER_BOX_TOP, 1, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.power",
                    Component.translatable(menu.isEnabled() ? "gui.virtualfarmworks.on" : "gui.virtualfarmworks.off")));
        } else if (isOverBox(FarmMatrixLayout.FERTILIZED_BOX_TOP, 1, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.fertilized_essence", Component.translatable(
                    menu.isFertilizedEssenceEnabled() ? "gui.virtualfarmworks.on" : "gui.virtualfarmworks.off")));
            lines.add(Component.translatable("gui.virtualfarmworks.fertilized_essence.hint"));
        } else if (faceBoxOpen && faceAt(mouseX, mouseY) != null) {
            RelativeSide side = faceAt(mouseX, mouseY);
            lines.add(Component.translatable("gui.virtualfarmworks.output_side", Component.translatable(side.translationKey()),
                    Component.translatable(menu.isOutputEnabled(side) ? "gui.virtualfarmworks.on" : "gui.virtualfarmworks.off")));
        } else if (hoveredSlot != null && menu.getCarried().isEmpty()) {
            int index = hoveredSlot.index - FarmMatrixMenu.INPUT_START;
            if (index >= 0 && index < MachineSlots.INPUT_COUNT) {
                lines.add(Component.translatable(slotDescriptionKey(index)));
            }
        }
        return lines;
    }

    private static String slotDescriptionKey(int index) {
        if (index == MachineSlots.SEED) {
            return "gui.virtualfarmworks.slot.seed";
        }
        if (index == MachineSlots.SOIL) {
            return "gui.virtualfarmworks.slot.soil";
        }
        if (index == MachineSlots.WATER_PROVIDER) {
            return "gui.virtualfarmworks.slot.water_provider";
        }
        if (index == MachineSlots.HOE) {
            return "gui.virtualfarmworks.slot.hoe";
        }
        if (MachineSlots.isGrowthSlot(index)) {
            return "gui.virtualfarmworks.slot.growth";
        }
        return "gui.virtualfarmworks.slot.crux";
    }

    // =================================================================================================================
    // Input
    // =================================================================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            if (isOverBox(FarmMatrixLayout.OUTPUT_BOX_TOP, 1, mouseX, mouseY)) {
                faceBoxOpen = !faceBoxOpen;
                playClick();
                return true;
            }
            if (isOverBox(FarmMatrixLayout.POWER_BOX_TOP, 1, mouseX, mouseY)) {
                sendButton(FarmMatrixMenu.BUTTON_POWER);
                return true;
            }
            if (isOverBox(FarmMatrixLayout.FERTILIZED_BOX_TOP, 1, mouseX, mouseY)) {
                sendButton(FarmMatrixMenu.BUTTON_FERTILIZED);
                return true;
            }
            if (faceBoxOpen) {
                RelativeSide side = faceAt(mouseX, mouseY);
                if (side != null) {
                    sendButton(FarmMatrixMenu.BUTTON_FACE_FIRST + side.ordinal());
                    return true;
                }
                if (isInFaceBox(mouseX, mouseY)) {
                    return true; // clicks on the box background do nothing (and must not reach the world)
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * The side panel and the face box are outside the texture; without this, vanilla would treat clicks there as
     * "outside the GUI" and throw the carried item on the ground.
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        if (isInSidePanel(mouseX, mouseY) || (faceBoxOpen && isInFaceBox(mouseX, mouseY))) {
            return false;
        }
        return super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    /** Sends an intent to the server (vanilla container button packet); the server validates and applies it. */
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

    /** Whether the mouse is over a side-panel box (borders included) whose white top line is at GUI y {@code top}. */
    private boolean isOverBox(int top, int cells, double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + FarmMatrixLayout.PANEL_BORDER_X, topPos + top,
                -FarmMatrixLayout.PANEL_BORDER_X, FarmMatrixLayout.boxHeight(cells));
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** The whole column, gaps included (a click between two boxes must not count as "outside the GUI" either). */
    private boolean isInSidePanel(double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, leftPos + FarmMatrixLayout.PANEL_BORDER_X, topPos + FarmMatrixLayout.OUTPUT_BOX_TOP,
                -FarmMatrixLayout.PANEL_BORDER_X, FarmMatrixLayout.PANEL_BOTTOM - FarmMatrixLayout.OUTPUT_BOX_TOP);
    }

    private boolean isInFaceBox(double mouseX, double mouseY) {
        int size = FarmMatrixLayout.FACE_BOX_SIZE + 2;
        return isInside(mouseX, mouseY, leftPos + FarmMatrixLayout.FACE_BOX_X - 1, topPos + FarmMatrixLayout.FACE_BOX_Y - 1,
                size, size);
    }

    private @Nullable RelativeSide faceAt(double mouseX, double mouseY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                RelativeSide side = FarmMatrixLayout.FACE_GRID[row][col];
                if (side != null && isInside(mouseX, mouseY, leftPos + FarmMatrixLayout.faceCellX(col),
                        topPos + FarmMatrixLayout.faceCellY(row), FarmMatrixLayout.FACE_CELL, FarmMatrixLayout.FACE_CELL)) {
                    return side;
                }
            }
        }
        return null;
    }
}
