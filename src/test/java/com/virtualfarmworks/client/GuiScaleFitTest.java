/*
 * GuiScaleFitTest — JUnit tests of client/GuiScaleFit: the GUI scale at which the Entropic screen (460x320 GUI pixels
 * with its side boxes) fits common monitors, never above the player's own. Run with `gradlew test`.
 */
package com.virtualfarmworks.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GuiScaleFitTest {
    private static final int WIDTH = 460;
    private static final int HEIGHT = 320;

    /** At the automatic scale (the largest keeping 320x240) the GUI never fits; one or more steps down it does. */
    @Test
    void automaticScalesDropUntilTheGuiFits() {
        assertEquals(3, GuiScaleFit.fittingScale(1920, 1080, 4, WIDTH, HEIGHT), "1080p: 4 -> 3 (640x360)");
        assertEquals(4, GuiScaleFit.fittingScale(2560, 1440, 6, WIDTH, HEIGHT), "1440p: 6 -> 4 (640x360)");
        assertEquals(6, GuiScaleFit.fittingScale(3840, 2160, 9, WIDTH, HEIGHT), "4K: 9 -> 6 (640x360)");
        assertEquals(2, GuiScaleFit.fittingScale(1366, 768, 3, WIDTH, HEIGHT), "768p: 3 -> 2 (683x384)");
    }

    @Test
    void aScaleThatFitsIsKeptAndNeverRaised() {
        assertEquals(3, GuiScaleFit.fittingScale(1920, 1080, 3, WIDTH, HEIGHT));
        assertEquals(2, GuiScaleFit.fittingScale(1920, 1080, 2, WIDTH, HEIGHT));
    }

    /** The width counts too: the face and filter boxes open left of the side column. */
    @Test
    void aNarrowWindowDropsFurther() {
        assertEquals(2, GuiScaleFit.fittingScale(1280, 1024, 4, WIDTH, HEIGHT), "426 px wide at 3 is too narrow");
    }

    @Test
    void aTinyWindowEndsAtOne() {
        assertEquals(1, GuiScaleFit.fittingScale(400, 300, 2, WIDTH, HEIGHT));
        assertEquals(1, GuiScaleFit.fittingScale(1920, 1080, 0, WIDTH, HEIGHT), "no scale below 1");
    }
}
