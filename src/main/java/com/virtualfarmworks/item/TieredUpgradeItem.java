package com.virtualfarmworks.item;

import java.util.function.Consumer;

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
        // Effect line, e.g. "+50% growth speed". Amount text is static for now; it will read the server config once
        // the config exists (milestone 1, step 2).
        builder.accept(Component.translatable("tooltip.virtualfarmworks." + type.registrySuffix())
                .withStyle(ChatFormatting.GRAY));

        // Compatibility line: list every machine tier this upgrade fits, e.g. "Fits: Starter, Voltaic, Ionic".
        MutableComponent fits = Component.empty();
        boolean first = true;
        for (MachineTier machineTier : MachineTier.values()) {
            if (!fits(machineTier)) {
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
}
