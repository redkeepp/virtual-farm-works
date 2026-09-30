/*
 * ConfigFileLayout — makes VFW's config file easier to read: a blank line before every setting, so each option stands
 * apart (owner, 2026-09-30). NightConfig, which FML uses to write config files, puts blank lines only between tables.
 */
package com.virtualfarmworks.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Plain text work, no Minecraft classes (JUnit-tested). FML writes the file (creating it, or correcting invalid values)
 * and then fires the config loading event, where {@link VfwConfig} calls {@link #apply}. Only blank lines are added:
 * keys, values and comments stay as FML wrote them, so the file means exactly the same and every later load parses it
 * the same way. Formatting a formatted file changes nothing, so the reload FML's file watcher runs after this write
 * ends there.
 */
public final class ConfigFileLayout {
    private ConfigFileLayout() {
    }

    /**
     * The same TOML with a blank line before each comment block that follows a value: every setting starts with its
     * comment, so each setting gets a blank line above it. Nothing is added after a table header (the first setting
     * sits right under it) or where a blank line already is. Keeps the file's newline style.
     */
    public static String withBlankLines(String toml) {
        String newline = toml.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = toml.split("\r?\n", -1);
        StringBuilder out = new StringBuilder(toml.length() + 512);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0 && line.strip().startsWith("#")) {
                String previous = lines[i - 1].strip();
                if (!previous.isEmpty() && !previous.startsWith("#") && !previous.startsWith("[")) {
                    out.append(newline);
                }
            }
            out.append(line);
            if (i < lines.length - 1) {
                out.append(newline);
            }
        }
        return out.toString();
    }

    /**
     * Rewrites the file with {@link #withBlankLines} when that changes it, through a temporary file and a move, so
     * nothing ever reads half a file.
     *
     * @return whether the file was rewritten
     */
    public static boolean apply(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        String formatted = withBlankLines(text);
        if (formatted.equals(text)) {
            return false;
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, formatted, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return true;
    }
}
