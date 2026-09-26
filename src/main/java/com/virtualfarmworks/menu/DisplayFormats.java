/*
 * DisplayFormats — how machine numbers are written for players (speed multipliers, growth percent), shared by the GUI
 * and the Jade tooltip so both always show the same text. No client classes: usable on both sides.
 */
package com.virtualfarmworks.menu;

import java.util.Locale;

public final class DisplayFormats {
    private DisplayFormats() {
    }

    /** Speed multiplier text: 1.0 -> "1.0", 0.25 -> "0.25", 3.0 -> "3.0", 4.05 -> "4.05". */
    public static String multiplier(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }

    /**
     * Growth percent shown to players (owner spec): 1%..100% while something is planted — never 0% on a planted
     * machine — and 0% when nothing is planted.
     */
    public static int growthPercent(double progress, int plots) {
        if (plots <= 0) {
            return 0;
        }
        return Math.clamp((int) Math.ceil(progress * 100.0), 1, 100);
    }
}
