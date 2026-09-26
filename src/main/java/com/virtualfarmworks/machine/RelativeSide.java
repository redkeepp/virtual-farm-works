/*
 * RelativeSide — the six sides of a machine relative to its front (top, bottom, front, back, left, right), their
 * conversion to world directions and their bit in the persisted auto-output mask.
 */
package com.virtualfarmworks.machine;

import net.minecraft.core.Direction;

/**
 * A side of the machine as the PLAYER sees it, standing in front of the machine and looking at its front face (the
 * convention of Mekanism / Industrial Foregoing side configs). The machine's front faces the player who placed it.
 *
 * <p>Example: a machine facing NORTH (front on its north face) is looked at by a player standing north of it, facing
 * south. That player's left hand points east, so {@link #LEFT} = east = {@code facing.getClockWise()}.
 *
 * <p>{@link #bit()} uses the ordinal and is persisted in saves (auto-output mask): never reorder the constants.
 */
public enum RelativeSide {
    TOP,
    BOTTOM,
    FRONT,
    BACK,
    LEFT,
    RIGHT;

    /** Mask with every side enabled (the default: auto-output on all faces, owner spec). */
    public static final int ALL = (1 << values().length) - 1;

    private static final RelativeSide[] VALUES = values();

    /** World direction of this side for a machine whose front faces {@code facing} (a horizontal direction). */
    public Direction toWorld(Direction facing) {
        return switch (this) {
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
        };
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Lang key of the side name, e.g. {@code side.virtualfarmworks.top}. */
    public String translationKey() {
        return "side.virtualfarmworks." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static RelativeSide[] all() {
        return VALUES.clone();
    }
}
