package com.virtualfarmworks.machine;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The five Farm Matrix tiers, in progression order.
 *
 * <p>The declaration order IS the tier order and is relied upon by {@link #accepts(MachineTier)}; never reorder the
 * constants. The serialized name is persisted (block codec, future NBT), so never rename an existing constant's
 * {@link #getSerializedName()} either — that would break existing saves.
 *
 * <p>Tiers on upgrades are purely a compatibility gate, never a quality level (owner decision, see
 * {@code docs/specs/starter-farm-matrix.md}): every tier of an upgrade gives exactly the same effect, a higher tier
 * upgrade simply fits more machines.
 */
public enum MachineTier implements StringRepresentable {
    // Only the Starter theme color is specified so far. The other colors are placeholders until the owner provides
    // each tier's spec; do not rely on them for anything visible yet.
    STARTER("starter", 0xFFFFFF),
    VOLTAIC("voltaic", 0xFFFFFF),
    IONIC("ionic", 0xFFFFFF),
    RESONANT("resonant", 0xFFFFFF),
    ENTROPIC("entropic", 0xFFFFFF);

    public static final Codec<MachineTier> CODEC = StringRepresentable.fromEnum(MachineTier::values);

    private final String name;
    /** RGB theme color of the machine (GUI title, side-panel borders). */
    private final int themeColor;

    MachineTier(String name, int themeColor) {
        this.name = name;
        this.themeColor = themeColor;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int themeColor() {
        return themeColor;
    }

    /**
     * Whether an upgrade of THIS tier can be installed in a machine of {@code machineTier}.
     *
     * <p>Rule: {@code upgradeTier >= machineTier}. An Ionic upgrade fits Starter, Voltaic and Ionic machines but not
     * Resonant or Entropic; an Entropic upgrade fits every machine; a Starter upgrade fits only the Starter machine.
     */
    public boolean accepts(MachineTier machineTier) {
        return this.ordinal() >= machineTier.ordinal();
    }

    /** Translation key of the tier's display name, e.g. {@code tier.virtualfarmworks.starter}. */
    public String translationKey() {
        return "tier.virtualfarmworks." + name;
    }
}
