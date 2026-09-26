/*
 * CruxProviderUpgradeItem — item class of the single, untiered Crux Provider Upgrade (a generic crux for Mystical
 * Agriculture seeds). Only provides the tooltip; the machine checks for its presence.
 */
package com.virtualfarmworks.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The single, untiered Crux Provider Upgrade.
 *
 * <p>Acts as a generic crux: it satisfies ANY crux requirement of any seed (e.g. Mystical Agriculture resource crops
 * that need a specific block under their farmland). One per machine, fits every tier. Like the tiered upgrades, it
 * carries no logic; the machine checks for its presence when the upgrade slots change.
 */
public class CruxProviderUpgradeItem extends Item {
    public CruxProviderUpgradeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.virtualfarmworks.crux_provider_upgrade")
                .withStyle(ChatFormatting.GRAY));
    }
}
