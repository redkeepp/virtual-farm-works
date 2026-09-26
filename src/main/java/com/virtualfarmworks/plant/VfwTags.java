package com.virtualfarmworks.plant;

import com.virtualfarmworks.VirtualFarmWorks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Tags owned by VFW. All of them are datapack-editable, so pack makers can change plant/soil rules without Java.
 * Default contents live in {@code src/main/resources/data/virtualfarmworks/tags/}.
 */
public final class VfwTags {
    /**
     * Soil items that count as vanilla farmland when a hoe is installed (default: dirt, grass block, dirt path, coarse
     * dirt, rooted dirt). Needed because real tilling ({@code getToolModifiedState}) requires a real world position.
     */
    public static final TagKey<Item> TILLABLE_SOILS = item("tillable_soils");

    /**
     * Seed items that are never plantable in VFW even though their block would qualify (default: torchflower seeds and
     * pitcher pod, which grow flowers — owner decision). Separate from the config blacklist so the config lists can
     * stay empty by default ("everything allowed").
     */
    public static final TagKey<Item> UNPLANTABLE = item("unplantable");

    /**
     * Extra seed items to accept even though VFW does not recognize their block type (e.g. a modded plant that is not
     * a {@code CropBlock}). Their soil rule is the plant's own {@code mayPlaceOn}. Empty by default.
     */
    public static final TagKey<Item> EXTRA_PLANTABLES = item("extra_plantables");

    /**
     * Soils for mushrooms. Vanilla mushrooms accept any solid block in the dark, which would let the soil slot accept
     * stone, cobblestone... A virtual machine has no light level, so VFW uses the natural mushroom soils instead
     * (default: {@code #minecraft:overrides_mushroom_light_requirement} = mycelium, podzol, nylium).
     */
    public static final TagKey<Block> SUPPORTS_MUSHROOMS = block("supports_mushrooms");

    /**
     * Blocks glow berries (cave vines) can hang from. Vanilla accepts any block with a sturdy bottom face, which is
     * nearly everything; VFW narrows it to cave-ceiling blocks (default: overworld stone, moss, dirt).
     */
    public static final TagKey<Block> SUPPORTS_GLOW_BERRIES = block("supports_glow_berries");

    private VfwTags() {
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, path));
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(VirtualFarmWorks.MODID, path));
    }
}
