/*
 * EntropicFarmMatrixScreen — the Entropic Farm Matrix GUI (client only): the owner's texture with its two 4x15 grids,
 * the dynamic texts (title, status, hydration, active and waiting plots, growth), the green progress bar, the striped
 * FE bar, the red tint of plot groups with a problem, and the side column drawn by code (face modes, upgrades and hoe,
 * Fertilized Essence, harvest filter, autocrafter, power).
 */
package com.virtualfarmworks.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.FaceMode;
import com.virtualfarmworks.machine.MachineFilter;
import com.virtualfarmworks.machine.MachineLayout;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.AbstractFarmMatrixMenu;
import com.virtualfarmworks.menu.DisplayFormats;
import com.virtualfarmworks.menu.EntropicFarmMatrixMenu;
import com.virtualfarmworks.menu.EntropicLayout;
import com.virtualfarmworks.menu.FarmMatrixLayout;
import com.virtualfarmworks.network.SetFilterGhostPayload;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Draws only; every action is sent to the server as a menu button click ({@link AbstractFarmMatrixMenu#clickMenuButton}),
 * and every number comes from the menu's synced data. Nothing here decides gameplay.
 *
 * <p>Same drawing techniques as the Starter's screen ({@code FarmMatrixScreen}): side-panel boxes as nested fills so
 * every line is one pixel, 40% ghost items covered by a translucent fill, texts shrunk to fit. Positions:
 * {@link EntropicLayout}.
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
    private final ItemStack hoeGhost;
    private final ItemStack crafterIcon = new ItemStack(Items.CRAFTING_TABLE);
    private boolean faceBoxOpen;
    private boolean filterBoxOpen;
    /** Smooths the 5-tick progress syncs into continuous movement (owner request, Starter step 8). */
    private final SmoothProgress smoothProgress = new SmoothProgress();
    private double shownProgress;
    /** Drawn FE fraction (0..1), eased toward the synced one; negative until the first synced value. */
    private double shownEnergy = -1.0;
    private long lastFrameMillis;

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
        this.hoeGhost = new ItemStack(Items.STONE_HOE);
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
     * The side column glued to the texture: "O" (face modes), the 6-cell upgrade block (4 Growth Speed, Crux, hoe),
     * Fertilized Essence ON/OFF, harvest filter (half white, half black), autocrafter (crafting table icon), ON/OFF.
     */
    private void extractSidePanel(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        int outputTop = y0 + EntropicLayout.OUTPUT_BOX_TOP;
        boolean outputActive = faceBoxOpen || isOverBox(EntropicLayout.OUTPUT_BOX_TOP, mouseX, mouseY);
        extractPanelBox(graphics, x0, outputTop, 1,
                outputActive ? FarmMatrixLayout.COLOR_BUTTON_ACTIVE : FarmMatrixLayout.COLOR_BACKGROUND);
        extractCellLabel(graphics, Component.literal("O"), x0, FarmMatrixLayout.cellY(outputTop, 0), themeColor);

        extractPanelBox(graphics, x0, y0 + EntropicLayout.UPGRADE_BOX_TOP, EntropicLayout.UPGRADE_CELLS,
                FarmMatrixLayout.COLOR_BACKGROUND);

        int fertilizedTop = y0 + EntropicLayout.FERTILIZED_BOX_TOP;
        boolean fertilized = menu.isFertilizedEssenceEnabled();
        extractPanelBox(graphics, x0, fertilizedTop, 1,
                fertilized ? FarmMatrixLayout.COLOR_FERTILIZED_ON : FarmMatrixLayout.COLOR_FERTILIZED_OFF);
        extractCellLabel(graphics, onOff(fertilized), x0, FarmMatrixLayout.cellY(fertilizedTop, 0), 0xFFFFFFFF);

        int filterTop = y0 + EntropicLayout.FILTER_BUTTON_TOP;
        extractPanelBox(graphics, x0, filterTop, 1, FarmMatrixLayout.COLOR_BLACK);
        int cellX = x0 + FarmMatrixLayout.PANEL_INTERIOR_X;
        int filterCellY = FarmMatrixLayout.cellY(filterTop, 0);
        graphics.fill(cellX, filterCellY, cellX + FarmMatrixLayout.CELL / 2, filterCellY + FarmMatrixLayout.CELL,
                FarmMatrixLayout.COLOR_WHITE);

        int crafterTop = y0 + EntropicLayout.CRAFTER_BUTTON_TOP;
        boolean crafterActive = isOverBox(EntropicLayout.CRAFTER_BUTTON_TOP, mouseX, mouseY);
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
        float scale = textWidth > width ? (float) width / textWidth : 1.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + (width - textWidth * scale) / 2.0F, y + (height - 8 * scale) / 2.0F);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** Text centered in a side-panel cell, shrunk if wider than the cell. */
    private void extractCellLabel(GuiGraphicsExtractor graphics, Component text, int x0, int cellY, int color) {
        extractFittedCentered(graphics, text, x0 + FarmMatrixLayout.PANEL_INTERIOR_X, cellY, FarmMatrixLayout.CELL,
                FarmMatrixLayout.CELL, color);
    }

    /**
     * 40% placeholders in the empty upgrade, crux, hoe and water slots, like the Starter. The 120 grid slots get none:
     * a full grid of faded seeds and farmland would hide what is really planted.
     */
    private void extractGhosts(GuiGraphicsExtractor graphics, int x0, int y0) {
        extractGhost(graphics, x0, y0, LAYOUT.waterSlot(), waterGhost);
        extractGhost(graphics, x0, y0, LAYOUT.hoeSlot(), hoeGhost);
        for (int i = 0; i < 4; i++) {
            extractGhost(graphics, x0, y0, LAYOUT.growthSlot(i), growthGhost);
        }
        extractGhost(graphics, x0, y0, LAYOUT.cruxSlot(), cruxGhost);
    }

    private void extractGhost(GuiGraphicsExtractor graphics, int x0, int y0, int menuIndex, ItemStack ghost) {
        Slot slot = menu.slots.get(menuIndex);
        if (slot.hasItem() || !slot.isActive()) {
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

    /** A seed in a plot group with a problem: the item tooltip ends with that problem. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack itemStack) {
        List<Component> lines = super.getTooltipFromContainerItem(itemStack);
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
        if (isOverBox(EntropicLayout.OUTPUT_BOX_TOP, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.output_sides"));
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
            lines.add(Component.translatable("gui.virtualfarmworks.energy_use", menu.energyUse()));
        } else if (hoveredSlot != null && menu.isFilterSlotIndex(hoveredSlot.index)) {
            lines.add(Component.translatable("gui.virtualfarmworks.filter.slot"));
            lines.add(Component.translatable("gui.virtualfarmworks.filter.slot_remove"));
        } else if (hoveredSlot != null && menu.getCarried().isEmpty() && hoveredSlot.index < LAYOUT.inputCount()) {
            lines.addAll(slotDescription(hoveredSlot.index));
        }
        return lines;
    }

    private List<Component> slotDescription(int index) {
        List<Component> lines = new ArrayList<>(2);
        if (LAYOUT.isSeedSlot(index)) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.seed_grid", slotLimit(index)));
        } else if (LAYOUT.isSoilSlot(index)) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.soil_grid", slotLimit(index)));
        } else if (index == LAYOUT.waterSlot()) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.water_provider"));
        } else if (index == LAYOUT.hoeSlot()) {
            lines.add(Component.translatable("gui.virtualfarmworks.slot.hoe"));
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
