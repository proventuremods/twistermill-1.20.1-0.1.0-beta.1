package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings("deprecation")
@ParametersAreNonnullByDefault
public class ServoTwisterBlock extends BearingBlock implements IBE<ServoTwisterBlockEntity> {

    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final EnumProperty<VentState> VENT_STATE = EnumProperty.create("vent_state", VentState.class);

    public ServoTwisterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(RUNNING, false)
                .setValue(VENT_STATE, VentState.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUNNING, VENT_STATE);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING))
            return state.getValue(BlockStateProperties.FACING).getAxis();
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

        withBlockEntityDo(level, pos, be -> be.onPlayerToggle(player));
        return InteractionResult.SUCCESS;
    }

    @Override
    public Class<ServoTwisterBlockEntity> getBlockEntityClass() {
        return ServoTwisterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ServoTwisterBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SERVO_TWISTER_BE.get();
    }

    public enum VentState implements StringRepresentable {
        NONE("none"),
        LEFT("left"),
        UP("up"),
        RIGHT("right"),
        LEFT_UP("left_up"),
        RIGHT_UP("right_up"),
        RIGHT_LEFT("right_left"),
        RIGHT_UP_LEFT("right_up_left");

        private final String serializedName;

        VentState(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}