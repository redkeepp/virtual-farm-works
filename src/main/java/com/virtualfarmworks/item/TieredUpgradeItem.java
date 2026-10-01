/*
 * TieredUpgradeItem — item class of the tiered upgrades (Water Provider and Growth Speed, 5 tiers each). Holds the
 * upgrade type and tier, answers "does it fit this machine tier?" and builds the tooltip. No gameplay behavior.
 */
package com.virtualfarmworks.item;

import java.util.function.Consumer;

import com.virtualfarmworks.config.VfwConfig;
import com.virtualfarmworks.machine.MachineTier;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A tiered machine upgrade (Water Provider or Growth Speed).
 *
 * <p>The item itself holds no behavior: the machine reads {@link #type()} and {@link #tier()} when its upgrade slots
 * change and caches the resulting modifiers. Keeping the effect out of the item means pack-maker config changes
 * (bonus amounts, penalties) apply without touching item code.
 */
public class TieredUpgradeItem extends Item {
    private final UpgradeType type;
    private final MachineTier tier;

    public TieredUpgradeItem(UpgradeType type, MachineTier tier, Item.Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    public UpgradeType type() {
        return type;
    }

    public MachineTier tier() {
        return tier;
    }

    /** Whether this upgrade may be installed in a machine of the given tier (see {@link MachineTier#accepts}). */
    public boolean fits(MachineTier machineTier) {
        return tier.accepts(machineTier);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        // Effect line. The growth bonus comes from the (server-synced) config so the tooltip matches what the pack
        // maker configured, e.g. "+50% growth speed".
        Component effect = switch (type) {
            case GROWTH_SPEED -> Component.translatable("tooltip.virtualfarmworks." + type.registrySuffix(),
                    formatPercent(VfwConfig.growthBonusForDisplay()));
            case WATER_PROVIDER -> Component.translatable("tooltip.virtualfarmworks." + type.registrySuffix());
        };
        builder.accept(effect.copy().withStyle(ChatFormatting.GRAY));

        // Compatibility line: list every existing machine tier this upgrade fits, e.g. "Fits: Starter, Entropic".
        MutableComponent fits = Component.empty();
        boolean first = true;
        for (MachineTier machineTier : MachineTier.values()) {
            if (!machineTier.isBuilt() || !fits(machineTier)) {
                continue;
            }
            if (!first) {
                fits.append(", ");
            }
            fits.append(Component.translatable(machineTier.translationKey()));
            first = false;
        }
        builder.accept(Component.translatable("tooltip.virtualfarmworks.fits", fits).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 0.5 -> "50", 0.125 -> "12.5": a fraction as a percentage without useless trailing zeros. */
    private static String formatPercent(double fraction) {
        double percent = Math.round(fraction * 1000.0) / 10.0;
        return percent == Math.rint(percent) ? Long.toString((long) percent) : Double.toString(percent);
    }
}
