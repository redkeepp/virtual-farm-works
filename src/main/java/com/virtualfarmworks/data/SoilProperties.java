package com.virtualfarmworks.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Data-map value attached to soil ITEMS ({@code data/virtualfarmworks/data_maps/item/soil_properties.json}).
 *
 * <p>Why a data map: there is no universal API telling whether a modded soil accelerates growth, so VFW cannot detect
 * it. Instead the values are data: VFW ships defaults (Mystical Agriculture / Mystical Agradditions farmlands, guarded by
 * {@code neoforge:mod_loaded} conditions so nothing breaks when those mods are absent), and pack makers add, change or
 * remove entries with a datapack — no Java involved.
 *
 * <p>Keyed by item (not block) because the machine holds soils as items in its soil slot.
 *
 * @param growthBonus extra growth speed granted by this soil, as a fraction: 0.35 means +35%, i.e. the machine speed
 *                    is multiplied by {@code 1 + growthBonus}. Soils without an entry have no bonus.
 */
public record SoilProperties(double growthBonus) {
    public static final Codec<SoilProperties> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0.0, 100.0).optionalFieldOf("growth_bonus", 0.0).forGetter(SoilProperties::growthBonus)
    ).apply(i, SoilProperties::new));

    /** Speed multiplier contributed by this soil ({@code 1 + growthBonus}). */
    public double speedMultiplier() {
        return 1.0 + growthBonus;
    }
}
