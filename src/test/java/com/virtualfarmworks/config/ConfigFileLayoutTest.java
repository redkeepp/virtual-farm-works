/*
 * ConfigFileLayoutTest — JUnit tests of config/ConfigFileLayout: a blank line before every setting of the config file,
 * nothing else changed, idempotent, newline style kept. Run with `gradlew test`.
 */
package com.virtualfarmworks.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The layout the owner asked for (2026-09-30): each setting apart, as NightConfig writes tables. */
class ConfigFileLayoutTest {
    /** What NightConfig writes: settings of a table one after the other, a blank line only between tables. */
    private static final String WRITTEN = String.join("\n",
            "#Growth Speed Upgrades",
            "[growth]",
            "\t#Extra growth speed added by EACH Growth Speed Upgrade.",
            "\t# Default: 0.5",
            "\tbonusPerUpgrade = 0.5",
            "\t#How many Growth Speed Upgrades fit in EACH slot.",
            "\t# Default: 1",
            "\tupgradesPerSlot = 1",
            "",
            "#Blacklists",
            "[filters]",
            "\t#Seeds refused by every machine.",
            "\tseedBlacklist = [",
            "\t\t\"minecraft:wheat_seeds\"",
            "\t]",
            "\t#Soils refused by every machine.",
            "\tsoilBlacklist = []",
            "");

    private static final String EXPECTED = String.join("\n",
            "#Growth Speed Upgrades",
            "[growth]",
            "\t#Extra growth speed added by EACH Growth Speed Upgrade.",
            "\t# Default: 0.5",
            "\tbonusPerUpgrade = 0.5",
            "",
            "\t#How many Growth Speed Upgrades fit in EACH slot.",
            "\t# Default: 1",
            "\tupgradesPerSlot = 1",
            "",
            "#Blacklists",
            "[filters]",
            "\t#Seeds refused by every machine.",
            "\tseedBlacklist = [",
            "\t\t\"minecraft:wheat_seeds\"",
            "\t]",
            "",
            "\t#Soils refused by every machine.",
            "\tsoilBlacklist = []",
            "");

    @Test
    void blankLineBeforeEverySettingOnly() {
        assertEquals(EXPECTED, ConfigFileLayout.withBlankLines(WRITTEN));
    }

    @Test
    void formattingTwiceChangesNothing() {
        String once = ConfigFileLayout.withBlankLines(WRITTEN);
        assertEquals(once, ConfigFileLayout.withBlankLines(once));
    }

    @Test
    void keepsWindowsNewlines() {
        String windows = WRITTEN.replace("\n", "\r\n");
        assertEquals(EXPECTED.replace("\n", "\r\n"), ConfigFileLayout.withBlankLines(windows));
    }

    @Test
    void applyRewritesOnlyWhenNeeded(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("virtualfarmworks-server.toml");
        Files.writeString(file, WRITTEN, StandardCharsets.UTF_8);
        assertTrue(ConfigFileLayout.apply(file), "the first call adds the blank lines");
        assertEquals(EXPECTED, Files.readString(file, StandardCharsets.UTF_8));
        assertFalse(ConfigFileLayout.apply(file), "a formatted file is left alone (no endless reloads)");
        assertFalse(Files.exists(directory.resolve("virtualfarmworks-server.toml.tmp")), "no temporary file left");
    }
}
