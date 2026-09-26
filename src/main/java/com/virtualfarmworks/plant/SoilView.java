/*
 * SoilView — a two-block, in-memory BlockGetter (soil + plant) used to ask vanilla/NeoForge plant rules about a
 * virtual plot without ever touching the real world.
 */
package com.virtualfarmworks.plant;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * A tiny, in-memory "world" containing exactly two blocks: the soil at {@link #SOIL_POS} and the plant next to it
 * (above for normal plants, beside for cocoa, below for hanging glow berries). Everything else is air.
 *
 * <p>Why: plant/soil rules (NeoForge {@code canSustainPlant}, vanilla {@code mayPlaceOn}) take a {@link BlockGetter}.
 * Passing this view lets VFW ask those rules about a VIRTUAL plot without touching the real world — no chunk access,
 * no world scan, nothing depends on where the machine is placed (performance rule: never scan the world).
 *
 * <p>Cheap to create (three fields); a new one is made per check.
 */
final class SoilView implements BlockGetter {
    /** Arbitrary position inside any dimension's build height. */
    static final BlockPos SOIL_POS = new BlockPos(0, 64, 0);

    private final BlockState soil;
    private final BlockState plant;
    private final BlockPos plantPos;

    SoilView(BlockState soil, BlockState plant, Direction plantSide) {
        this.soil = soil;
        this.plant = plant;
        this.plantPos = SOIL_POS.relative(plantSide);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (pos.equals(SOIL_POS)) {
            return soil;
        }
        return pos.equals(plantPos) ? plant : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return 384;
    }

    @Override
    public int getMinY() {
        return -64;
    }
}
