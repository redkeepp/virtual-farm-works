/*
 * VfwTags — TagKeys of VFW's datapack-editable tags (tillable soils, unplantable seeds, extra plantables, universal
 * soils, mushroom soils, glow berry supports, harvest by-products, crafter catalysts). Default contents:
 * src/main/resources/data/virtualfarmworks/tags/.
 */
package com.virtualfarmworks.plant;

import com.virtualfarmworks.VirtualFarmWorks;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
     * Seed items that are never plantable in VFW even though their block would qualify. Empty by default (owner,
     * 2026-09-28: every plantable is accepted; torchflower seeds and pitcher pod used to be here). Separate from the
     * config blacklist so pack makers have a datapack way too.
     */
    public static final TagKey<Item> UNPLANTABLE = item("unplantable");

    /**
     * Extra items to accept in the seed slot even though VFW does not recognize their block as a plant (a modded plant
     * built on a plain {@code Block}). They are harvested like crops (their block's loot, one planting item replanted)
     * and grow only on soils whose NeoForge {@code canSustainPlant} hook accepts them. Empty by default.
     */
    public static final TagKey<Item> EXTRA_PLANTABLES = item("extra_plantables");

    /**
     * Soils every generic plant that stands on dirt grows on (owner, 2026-09-28: "dirt, grass, any farmland"), on top of
     * its natural soils. Default: {@code #minecraft:supports_vegetation} (dirt, coarse and rooted dirt, grass block,
     * podzol, mycelium, moss, mud, farmland). Every {@code FarmlandBlock} counts as well, even without being listed, so
     * modded farmlands work out of the box.
     */
    public static final TagKey<Block> UNIVERSAL_SOILS = block("universal_soils");

    /**
     * Harvest drops that count as SECONDARY (by-products), scaled by {@code drops.secondaryDropMultiplier} instead of
     * the production multipliers (default: poisonous potato, Mystical Agriculture's Fertilized Essence). Extra seeds
     * are secondary automatically when the planting item is in {@code #c:seeds}.
     */
    public static final TagKey<Item> HARVEST_BYPRODUCTS = item("harvest_byproducts");

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

    /**
     * What the autocrafter's catalyst slot accepts (owner, 2026-09-29: the Master Infusion Crystal, so Mystical
     * Agriculture's essence tiers can be crafted). A recipe uses the catalyst without spending it, and only if the
     * recipe gives it back whole: list only items that survive crafting (unbreakable tools). Default: Mystical
     * Agriculture's Master Infusion Crystal.
     */
    public static final TagKey<Item> CRAFTER_CATALYSTS = item("crafter_catalysts");

    private VfwTags() {
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(VirtualFarmWorks.MODID, path));
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(VirtualFarmWorks.MODID, path));
    }
}
