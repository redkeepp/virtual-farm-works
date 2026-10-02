/*
 * CrafterRecipes — the item data component that keeps an autocrafter's recipes on the item of a broken machine, so
 * placing the machine again gives them back (owner, 2026-09-30). Only the recipes travel: the waiting ingredients are
 * deleted with the machine (owner rule for hidden items) and CRAFT is ON again, as on any machine just placed.
 */
package com.virtualfarmworks.machine;

import java.util.List;
import java.util.function.Consumer;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * The machine writes it when its item is made from the block (loot table {@code copy_components}, only when it holds
 * recipes, so an empty machine drops a plain item that stacks with new ones) and reads it when placed
 * ({@link FarmMatrixBlockEntity}). Immutable and compared by content, as item components must be. Trimmed like a loaded
 * save (at most {@link MachineCrafter#MAX_RECIPES} recipes, 9 cells of one item each), so an edited item cannot hold
 * more than a machine.
 */
public record CrafterRecipes(List<MachineCrafter.Pattern> patterns) implements TooltipProvider {
    public static final Codec<CrafterRecipes> CODEC =
            MachineCrafter.PATTERNS_CODEC.xmap(CrafterRecipes::new, CrafterRecipes::patterns);
    public static final StreamCodec<RegistryFriendlyByteBuf, CrafterRecipes> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public CrafterRecipes {
        patterns = MachineCrafter.trim(patterns);
    }

    /**
     * "Autocrafter recipes: N" on the item, so a player knows the machine kept them. Called by the machine block's
     * {@code appendHoverText} (1.21.1 shows modded components through their item or block only).
     */
    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag) {
        consumer.accept(Component.translatable("tooltip.virtualfarmworks.crafter_recipes", patterns.size())
                .withStyle(ChatFormatting.GRAY));
    }
}
