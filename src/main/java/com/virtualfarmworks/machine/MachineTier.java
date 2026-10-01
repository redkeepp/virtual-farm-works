/*
 * MachineTier — the five Farm Matrix tiers (Starter, Voltaic, Ionic, Resonant, Entropic): progression order, theme
 * color, whether the tier's machine exists yet, and the upgrade compatibility rule (an upgrade fits when upgradeTier
 * >= machineTier).
 */
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
    // Starter: white (owner spec). Entropic: red, the border color of the owner's Entropic GUI texture. The other colors
    // are placeholders until the owner provides each tier's spec; do not rely on them for anything visible yet.
    STARTER("starter", 0xFFFFFF, true),
    VOLTAIC("voltaic", 0xFFFFFF, false),
    IONIC("ionic", 0xFFFFFF, false),
    RESONANT("resonant", 0xFFFFFF, false),
    ENTROPIC("entropic", 0xFF0000, true);

    public static final Codec<MachineTier> CODEC = StringRepresentable.fromEnum(MachineTier::values);

    private final String name;
    /** RGB theme color of the machine (GUI title, side-panel borders). */
    private final int themeColor;
    private final boolean built;

    MachineTier(String name, int themeColor, boolean built) {
        this.name = name;
        this.themeColor = themeColor;
        this.built = built;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public int themeColor() {
        return themeColor;
    }

    /**
     * Whether the tier's machine exists (Starter and Entropic so far). A tier not built registers no items at all, so a
     * release never shows upgrades nobody can craft or use (owner, 2026-09-30). Building a tier: flip this, register
     * its block in ModBlocks and add its section in VfwServerConfig.
     */
    public boolean isBuilt() {
        return built;
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
