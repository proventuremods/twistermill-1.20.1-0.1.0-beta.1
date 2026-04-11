package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.ModBlockEntities;
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
public class InvServoTwisterBlock extends BearingBlock implements IBE<InvServoTwisterBlockEntity> {

    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public InvServoTwisterBlock(Properties properties) {
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

    @Nonnull
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild()) {
            return InteractionResult.FAIL;
        }
        if (player.isShiftKeyDown()) {
            return InteractionResult.FAIL;
        }
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        withBlockEntityDo(level, pos, be -> be.onPlayerToggle(player));
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<InvServoTwisterBlockEntity> getBlockEntityClass() {
        return InvServoTwisterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends InvServoTwisterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.INV_SERVO_TWISTER_BE.get();
    }
}