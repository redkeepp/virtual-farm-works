/*
 * FarmMatrixBlock — the Farm Matrix machine block, one class for every tier: horizontal facing (front = placing
 * player), block codec, right-click behavior (upgrade in hand = insert it, otherwise open the GUI), and its block
 * entity (FarmMatrixBlockEntity), ticked on the server only.
 */
package com.virtualfarmworks.block;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.virtualfarmworks.item.CruxProviderUpgradeItem;
import com.virtualfarmworks.item.TieredUpgradeItem;
import com.virtualfarmworks.machine.CrafterRecipes;
import com.virtualfarmworks.machine.FarmMatrixBlockEntity;
import com.virtualfarmworks.machine.MachineTier;
import com.virtualfarmworks.menu.FarmMatrixMenu;
import com.virtualfarmworks.registry.ModBlockEntities;
import com.virtualfarmworks.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * <p>All machine logic lives in {@link FarmMatrixBlockEntity}. Do NOT give this block a random tick: growth is
 * simulated by one global progress bar in the block entity (see CLAUDE.md, performance rules). The ticker is created
 * on the server only; the client never simulates anything.
 */
public class FarmMatrixBlock extends HorizontalDirectionalBlock implements EntityBlock {
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

    /**
     * Right-click holding an upgrade (Water Provider, Growth Speed or Crux Provider — owner spec): the machine pulls
     * as many as fit straight from the hand, no GUI needed. Anything else, or an upgrade that does not fit (slots full,
     * tier too low), falls through to {@link #useWithoutItem} ({@code PASS_TO_DEFAULT_BLOCK_INTERACTION}) and opens the
     * GUI, so the player can see why.
     *
     * <p>The server decides; the client only predicts a swing. Creative players (infinite materials) keep their items,
     * like vanilla containers such as the composter.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        boolean isUpgrade = stack.getItem() instanceof TieredUpgradeItem
                || stack.getItem() instanceof CruxProviderUpgradeItem;
        if (!isUpgrade) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof FarmMatrixBlockEntity machine
                && machine.insertUpgradesFrom(stack, !player.hasInfiniteMaterials()) > 0) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Right-click with an empty hand (or an item the machine does not take directly): open the machine GUI. Server
     * side only; the client just reports success so the hand swings. The open packet carries the position and tier so
     * the client can build its menu mirror.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof FarmMatrixBlockEntity machine) {
            serverPlayer.openMenu(machine, buf -> FarmMatrixMenu.writeOpenData(buf, pos, tier));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
     * The block is being removed (broken, replaced): the machine drops its inputs and visible output first (1.21.1 calls
     * this before the block entity goes; 26.1 called the block entity's {@code preRemoveSideEffects}). A state change of
     * the same block (turning it) drops nothing.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FarmMatrixBlockEntity machine) {
            machine.dropContents(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * The machine item's tooltip: "Autocrafter recipes: N" when it kept an autocrafter's recipes ({@link CrafterRecipes},
     * owner, 2026-09-30). 1.21.1 shows no tooltip for modded item components on its own; a block item asks its block.
     */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CrafterRecipes recipes = stack.get(ModDataComponents.CRAFTER_RECIPES.get());
        if (recipes != null) {
            recipes.addToTooltip(context, tooltip::add, flag);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FarmMatrixBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                             BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.FARM_MATRIX.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof FarmMatrixBlockEntity machine && tickLevel instanceof ServerLevel serverLevel) {
                machine.serverTick(serverLevel);
            }
        };
    }
}
