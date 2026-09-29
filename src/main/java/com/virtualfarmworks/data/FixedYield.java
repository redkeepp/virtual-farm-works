/*
 * FixedYield — value type of the fixed_yield data map: a plantable item whose plots yield a fixed amount of one item
 * per harvest instead of their normal harvest. Data file: data/virtualfarmworks/data_maps/item/fixed_yield.json.
 */
package com.virtualfarmworks.data;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * Data-map value attached to plantable ITEMS ({@code data/virtualfarmworks/data_maps/item/fixed_yield.json}): every
 * harvested plot yields {@code count} of {@code item}, the plant stays planted (nothing is paid back from the drops),
 * and the plant's own harvest (loot table, tree, Mystical Agriculture formula) is not used.
 *
 * <p>Why: some plants have no useful harvest of their own. The owner decided (2026-09-28) that Torchflower Seeds and
 * Pitcher Pod, whose seed turns into the flower in the real world, yield 10 flowers per harvest; VFW ships those two
 * entries. Pack makers can add entries for modded plants of the same kind, or change any plant's harvest, with a
 * datapack.
 *
 * @param item  what each plot yields; empty = the planting item itself
 * @param count how many per plot and harvest; empty = config {@code drops.otherPlantYield} (default 10)
 */
public record FixedYield(Optional<Item> item, Optional<Integer> count) {
    public static final Codec<FixedYield> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(FixedYield::item),
            Codec.intRange(0, 1_000_000).optionalFieldOf("count").forGetter(FixedYield::count)
    ).apply(i, FixedYield::new));
}
