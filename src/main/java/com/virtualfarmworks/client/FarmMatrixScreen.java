/*
 * FarmMatrixScreen — the Farm Matrix GUI (client only): the owner's texture, the dynamic texts (title, status,
 * hydration, seeds, growth), the green progress bar, 40% ghost placeholders in empty slots, and the side panel drawn by
 * code (auto-output button + face box, 5 upgrade slots, power button).
 */
package com.virtualfarmworks.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.virtualfarmworks.VirtualFarmWorks;
import com.virtualfarmworks.machine.MachineSlots;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.machine.RelativeSide;
import com.virtualfarmworks.menu.FarmMatrixLayout;
import com.virtualfarmworks.menu.FarmMatrixMenu;
import com.virtualfarmworks.registry.ModItems;
import com.virtualfarmworks.sim.MachineStatus;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
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

    /** Green fill of the owner's bar area, proportional to the cycle progress. */
    private void extractProgressBar(GuiGraphicsExtractor graphics, int x0, int y0) {
        int filled = (int) Math.round(menu.progress() * FarmMatrixLayout.BAR_WIDTH);
        if (filled > 0) {
            int x = x0 + FarmMatrixLayout.BAR_X;
            int y = y0 + FarmMatrixLayout.BAR_Y;
            graphics.fill(x, y, x + filled, y + FarmMatrixLayout.BAR_HEIGHT, FarmMatrixLayout.COLOR_BAR);
        }
    }

    /** "O" button, 5 upgrade slot frames and the power button, each with a 1 px theme-colored outer border. */
    private void extractSidePanel(GuiGraphicsExtractor graphics, int x0, int y0, int mouseX, int mouseY) {
        int frameX = x0 + FarmMatrixLayout.PANEL_FRAME_X;

        int outputY = y0 + FarmMatrixLayout.OUTPUT_BUTTON_Y;
        extractFrame(graphics, frameX, outputY, faceBoxOpen || isOver(frameX, outputY, mouseX, mouseY)
                ? FarmMatrixLayout.COLOR_BUTTON_ACTIVE : FarmMatrixLayout.COLOR_BACKGROUND);
        extractCenteredLabel(graphics, Component.literal("O"), frameX, outputY, themeColor);

        for (int i = 0; i < FarmMatrixLayout.UPGRADE_SLOTS; i++) {
            extractFrame(graphics, frameX, y0 + FarmMatrixLayout.upgradeFrameY(i), FarmMatrixLayout.COLOR_BACKGROUND);
        }

        int powerY = y0 + FarmMatrixLayout.POWER_BUTTON_Y;
        boolean on = menu.isEnabled();
        extractFrame(graphics, frameX, powerY, on ? FarmMatrixLayout.COLOR_ON : FarmMatrixLayout.COLOR_OFF);
        extractCenteredLabel(graphics, Component.translatable(on ? "gui.virtualfarmworks.on" : "gui.virtualfarmworks.off"),
                frameX, powerY, 0xFFFFFFFF);
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

    /** An 18x18 frame like the texture's slots, plus the owner's 1 px outer border in the theme color. */
    private void extractFrame(GuiGraphicsExtractor graphics, int x, int y, int interior) {
        int size = FarmMatrixLayout.FRAME_SIZE;
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, themeColor);
        graphics.fill(x, y, x + size, y + size, FarmMatrixLayout.COLOR_FRAME);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, interior);
    }

    /** Text centered in an 18x18 frame, scaled down if wider than the 16 px interior. */
    private void extractCenteredLabel(GuiGraphicsExtractor graphics, Component text, int frameX, int frameY, int color) {
        int inner = FarmMatrixLayout.FRAME_SIZE - 2;
        int width = font.width(text);
        float scale = width > inner ? (float) inner / width : 1.0F;
        float drawnWidth = width * scale;
        float drawnHeight = 8 * scale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(frameX + (FarmMatrixLayout.FRAME_SIZE - drawnWidth) / 2.0F,
                frameY + (FarmMatrixLayout.FRAME_SIZE - drawnHeight) / 2.0F);
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
        int y = FarmMatrixLayout.INFO_Y;
        extractFittedText(graphics, Component.translatable(status.translationKey()), y, 0xFF000000 | status.tone().rgb());
        y += FarmMatrixLayout.INFO_LINE_HEIGHT;
        extractFittedText(graphics, Component.translatable("gui.virtualfarmworks.hydration",
                formatMultiplier(menu.hydrationMultiplier())), y, FarmMatrixLayout.COLOR_TEXT);
        y += FarmMatrixLayout.INFO_LINE_HEIGHT;
        extractFittedText(graphics, Component.translatable("gui.virtualfarmworks.seeds", menu.plots(),
                MachineSlots.SEED_SOIL_LIMIT), y, FarmMatrixLayout.COLOR_TEXT);
        y += FarmMatrixLayout.INFO_LINE_HEIGHT;
        extractFittedText(graphics, Component.translatable("gui.virtualfarmworks.growth", displayPercent(),
                formatMultiplier(menu.growthMultiplier())), y, FarmMatrixLayout.COLOR_TEXT);
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

    /** Owner spec: the bar reads 1%..100% while plots exist (never 0% on a planted machine); 0% when empty. */
    private int displayPercent() {
        if (menu.plots() <= 0) {
            return 0;
        }
        return Math.clamp((int) Math.ceil(menu.progress() * 100.0), 1, 100);
    }

    /** 1.0 -> "1.0", 0.25 -> "0.25", 3.0 -> "3.0", 4.05 -> "4.05". */
    static String formatMultiplier(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
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
        int frameX = leftPos + FarmMatrixLayout.PANEL_FRAME_X;
        if (isOver(frameX, topPos + FarmMatrixLayout.OUTPUT_BUTTON_Y, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.output_sides"));
        } else if (isOver(frameX, topPos + FarmMatrixLayout.POWER_BUTTON_Y, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.virtualfarmworks.power",
                    Component.translatable(menu.isEnabled() ? "gui.virtualfarmworks.on" : "gui.virtualfarmworks.off")));
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
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            int frameX = leftPos + FarmMatrixLayout.PANEL_FRAME_X;
            if (isOver(frameX, topPos + FarmMatrixLayout.OUTPUT_BUTTON_Y, mouseX, mouseY)) {
                faceBoxOpen = !faceBoxOpen;
                playClick();
                return true;
            }
            if (isOver(frameX, topPos + FarmMatrixLayout.POWER_BUTTON_Y, mouseX, mouseY)) {
                sendButton(FarmMatrixMenu.BUTTON_POWER);
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

    /** Whether the mouse is over an 18x18 frame (border included) whose frame starts at (x, y). */
    private static boolean isOver(int x, int y, double mouseX, double mouseY) {
        return isInside(mouseX, mouseY, x - 1, y - 1, FarmMatrixLayout.FRAME_SIZE + 2, FarmMatrixLayout.FRAME_SIZE + 2);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private boolean isInSidePanel(double mouseX, double mouseY) {
        int x = leftPos + FarmMatrixLayout.PANEL_FRAME_X - 1;
        int top = topPos + FarmMatrixLayout.OUTPUT_BUTTON_Y - 1;
        int bottom = topPos + FarmMatrixLayout.POWER_BUTTON_Y + FarmMatrixLayout.FRAME_SIZE + 1;
        return isInside(mouseX, mouseY, x, top, FarmMatrixLayout.FRAME_SIZE + 2, bottom - top);
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
