package com.virtualfarmworks.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.virtualfarmworks.machine.MachineTier;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The Farm Matrix machine block, shared by every tier (the tier is a constructor parameter, not a subclass).
 *
 * <p>It is horizontally directional because the machine has a "front": the per-face auto-output configuration is
 * expressed relative to it (front/back/left/right/top/bottom), like Mekanism or Industrial Foregoing machines. The
 * front faces the player who placed it.
 *
 * <p>Milestone 1, step 1: registration only. The BlockEntity (simulation, inventories, GUI) is added in a later step;
 * this block will then implement {@code EntityBlock}. Do NOT give it a random tick: growth is simulated by one global
 * progress bar in the BlockEntity (see CLAUDE.md, performance rules).
 */
public class FarmMatrixBlock extends HorizontalDirectionalBlock {
    /**
     * Block codec required by vanilla since 1.20.5 for every block type. The tier is part of it so that a codec
     * round-trip rebuilds the correct tier.
     */
    public static final MapCodec<FarmMatrixBlock> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                    MachineTier.CODEC.fieldOf("tier").forGetter(FarmMatrixBlock::tier),
                    propertiesCodec()
            ).apply(i, FarmMatrixBlock::new));

    private final MachineTier tier;

    public FarmMatrixBlock(MachineTier tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public MachineTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Same convention as the vanilla furnace: the front faces the placing player.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
