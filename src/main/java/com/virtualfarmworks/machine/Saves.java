/*
 * Saves — (1.21.1 line) small helpers for the machine's NBT saves: a codec value written into or read from a key of a
 * CompoundTag with registry ops, and defaults for missing keys. Stands in for the conveniences of 26.1's ValueOutput /
 * ValueInput (store, read, getBooleanOr), so the save code reads the same on both lines.
 */
package com.virtualfarmworks.machine;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.virtualfarmworks.VirtualFarmWorks;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Codec values are encoded with registry ops ({@code HolderLookup.Provider#createSerializationContext}): item components
 * and recipe ids may refer to registries. A value that fails to encode or decode is logged and left out (encode) or
 * read as absent (decode), so one bad entry never breaks a whole save.
 */
final class Saves {
    private Saves() {
    }

    /** Writes {@code value} under {@code key} with {@code codec}. */
    static <T> void store(CompoundTag tag, String key, Codec<T> codec, T value, HolderLookup.Provider registries) {
        codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value)
                .resultOrPartial(error -> VirtualFarmWorks.LOGGER.error("Could not save {} of a Farm Matrix: {}", key,
                        error))
                .ifPresent(encoded -> tag.put(key, encoded));
    }

    /** Reads the value under {@code key} with {@code codec}; empty when the key is missing or unreadable. */
    static <T> Optional<T> read(CompoundTag tag, String key, Codec<T> codec, HolderLookup.Provider registries) {
        Tag encoded = tag.get(key);
        if (encoded == null) {
            return Optional.empty();
        }
        return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), encoded)
                .resultOrPartial(error -> VirtualFarmWorks.LOGGER.error("Could not load {} of a Farm Matrix: {}", key,
                        error));
    }

    /** The boolean under {@code key}, or {@code fallback} when the key is missing (NBT's own default is false). */
    static boolean booleanOr(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key, Tag.TAG_BYTE) ? tag.getBoolean(key) : fallback;
    }

    /** The compound under {@code key}, or empty when it is missing or of another type. */
    static Optional<CompoundTag> child(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_COMPOUND) ? Optional.of(tag.getCompound(key)) : Optional.empty();
    }
}
