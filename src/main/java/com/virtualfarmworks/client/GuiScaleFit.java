/*
 * GuiScaleFit — the GUI scale at which a screen fits the window: the largest one not above the player's (owner,
 * 2026-09-30: the Entropic's 296x320 GUI never fits at the automatic scale). Plain int math, JUnit-tested; the screen
 * applies it (EntropicFarmMatrixScreen, only while it is open).
 */
package com.virtualfarmworks.client;

/**
 * Minecraft's automatic GUI scale is the largest that keeps 320x240 GUI pixels, so it always leaves 240-270 px of height
 * (1080p: 270, 1440p and 4K: 240) and a taller GUI is cut at the top and bottom. The scale from here is an integer, so
 * the owner's pixel art stays sharp; it is never above the player's own, so a player whose scale already fits sees no
 * change.
 */
public final class GuiScaleFit {
    private GuiScaleFit() {
    }

    /**
     * The largest scale, at most {@code playerScale}, at which {@code width} x {@code height} GUI pixels fit a window of
     * {@code windowWidth} x {@code windowHeight} real pixels; 1 when none does (a tiny window).
     */
    public static int fittingScale(int windowWidth, int windowHeight, int playerScale, int width, int height) {
        int scale = Math.max(1, playerScale);
        while (scale > 1 && (windowWidth / scale < width || windowHeight / scale < height)) {
            scale--;
        }
        return scale;
    }
}
