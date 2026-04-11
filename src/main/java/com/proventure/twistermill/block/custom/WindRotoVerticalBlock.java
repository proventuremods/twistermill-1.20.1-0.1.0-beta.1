package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.blockentity.WindRotoVerticalBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("deprecation")
@ParametersAreNonnullByDefault
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
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Nonnull
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(BlockStateProperties.FACING).getAxis();
    }

    @Nonnull
    @SuppressWarnings("deprecation")
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;

        if (player.isShiftKeyDown())
            return InteractionResult.PASS;

        if (!player.getItemInHand(hand).isEmpty())
            return InteractionResult.PASS;

        if (!level.isClientSide) {
            withBlockEntityDo(level, pos, be -> be.onPlayerToggle(player));
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction nearest = context.getNearestLookingDirection().getOpposite();

        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            nearest = nearest.getOpposite();
        }

        return defaultBlockState()
                .setValue(BlockStateProperties.FACING, nearest)
                .setValue(RUNNING, false);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof WindRotoVerticalBlockEntity windRotoVerticalBlockEntity) {
                windRotoVerticalBlockEntity.releaseOwnChunkForced();
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    public boolean hideStressImpact() {
        return true;
    }
}