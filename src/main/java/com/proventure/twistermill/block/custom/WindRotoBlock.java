package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.blockentity.WindRotoBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("deprecation")
@ParametersAreNonnullByDefault
public class WindRotoBlock extends BearingBlock implements IBE<WindRotoBlockEntity> {

    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public WindRotoBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUNNING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING).getAxis();
        }
        return Direction.Axis.Y;
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
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        return facing.getAxis().isHorizontal() || facing == Direction.UP;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        boolean allowed = facing.getAxis().isHorizontal() || facing == Direction.UP;
        if (!allowed)
            return 0;

        WindRotoBlockEntity be = getBlockEntity(level, pos);
        if (be == null)
            return 0;

        return be.getComparatorOutputLevel();
    }

    @Nonnull
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild())
            return InteractionResult.FAIL;
        if (player.isShiftKeyDown())
            return InteractionResult.FAIL;
        if (!player.getItemInHand(hand).isEmpty())
            return InteractionResult.PASS;

        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        withBlockEntityDo(level, pos, be -> {
            if (be.isRunning()) {
                be.disassemblePublic();
            } else {
                be.queueAssemblePublic();
            }
        });

        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<WindRotoBlockEntity> getBlockEntityClass() {
        return WindRotoBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WindRotoBlockEntity> getBlockEntityType() {
        return ModBlockEntities.WIND_ROTO_BE.get();
    }
}