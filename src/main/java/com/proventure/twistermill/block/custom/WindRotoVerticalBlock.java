package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.blockentity.WindRotoVerticalBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WindRotoVerticalBlock extends BearingBlock implements IBE<WindRotoVerticalBlockEntity> {

    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public WindRotoVerticalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP)
                .setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUNNING);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            net.minecraft.world.InteractionHand hand,
            BlockHitResult hit
    ) {
        if (hit.getDirection() == Direction.UP && stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public Class<WindRotoVerticalBlockEntity> getBlockEntityClass() {
        return WindRotoVerticalBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WindRotoVerticalBlockEntity> getBlockEntityType() {
        return ModBlockEntities.WIND_ROTO_VERTICAL_BE.get();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown())
            return InteractionResult.PASS;

        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;

        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, be -> {
                if (!be.tryManualEmptyHandLostStateReset(player)) {
                    be.onPlayerToggle(player);
                }
            });
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(BlockStateProperties.FACING).getAxis();
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        Direction currentFacing = originalState.getValue(BlockStateProperties.FACING);

        if (currentFacing == Direction.UP) {
            return originalState.setValue(BlockStateProperties.FACING, Direction.DOWN);
        }

        if (currentFacing == Direction.DOWN) {
            return originalState.setValue(BlockStateProperties.FACING, Direction.UP);
        }

        if (targetedFace == Direction.UP || targetedFace == Direction.DOWN) {
            return originalState.setValue(BlockStateProperties.FACING, targetedFace);
        }

        return originalState;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        boolean blockTypeChanged = state.getBlock() != newState.getBlock();
        if (blockTypeChanged && !level.isClientSide) {
            withBlockEntityDo(level, pos, WindRotoVerticalBlockEntity::disassemble);
        }

        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction nearest = Direction.UP;

        if (context.getPlayer() != null) {
            nearest = context.getNearestLookingVerticalDirection().getOpposite();

            if (context.getPlayer().isShiftKeyDown()) {
                nearest = nearest.getOpposite();
            }
        }

        return defaultBlockState()
                .setValue(BlockStateProperties.FACING, nearest)
                .setValue(RUNNING, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (level.isClientSide || state.getValue(BlockStateProperties.FACING) != Direction.UP) {
            return;
        }

        BlockPos buttonPos = pos.south();
        if (!level.getBlockState(buttonPos).canBeReplaced()) {
            return;
        }

        BlockState buttonState = Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);

        if (buttonState.canSurvive(level, buttonPos)) {
            level.setBlock(buttonPos, buttonState, 3);
        }
    }

    public boolean hideStressImpact() {
        return true;
    }
}
