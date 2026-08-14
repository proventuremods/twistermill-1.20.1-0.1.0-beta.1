package com.proventure.twistermill.block.custom;

import com.mojang.serialization.MapCodec;
import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ServoTwisterBlock extends BearingBlock implements IBE<ServoTwisterBlockEntity> {

    public static final MapCodec<ServoTwisterBlock> CODEC = simpleCodec(ServoTwisterBlock::new);
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    public static final BooleanProperty PITCH_CLEARANCE = BooleanProperty.create("pitch_clearance");
    public static final EnumProperty<PowerVisualState> POWER_VISUAL =
            EnumProperty.create("power_visual", PowerVisualState.class);
    private static final VoxelShape FULL_SHAPE = Shapes.block();
    private static final VoxelShape PITCH_CLEARANCE_COLLISION_SHAPE =
            Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);

    public ServoTwisterBlock(Properties properties) {
        super(properties
                .forceSolidOn()
                .isRedstoneConductor((state, level, pos) -> true)
                .isSuffocating((state, level, pos) -> true)
                .isViewBlocking((state, level, pos) -> true));
        registerDefaultState(defaultBlockState()
                .setValue(RUNNING, false)
                .setValue(PITCH_CLEARANCE, false)
                .setValue(POWER_VISUAL, PowerVisualState.UNPOWERED));
    }

    @Override
    protected MapCodec<? extends ServoTwisterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUNNING, PITCH_CLEARANCE, POWER_VISUAL);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return state.getValue(PITCH_CLEARANCE)
                ? PITCH_CLEARANCE_COLLISION_SHAPE
                : FULL_SHAPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL_SHAPE;
    }

    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return FULL_SHAPE;
    }

    @Override
    public VoxelShape getVisualShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return FULL_SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return FULL_SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        if (state.hasProperty(BlockStateProperties.FACING))
            return state.getValue(BlockStateProperties.FACING).getAxis();
        return Direction.Axis.Y;
    }

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
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            net.minecraft.world.InteractionHand hand,
            BlockHitResult hit
    ) {
        if (stack.getItem() instanceof BlockItem blockItem
                && level.getBlockEntity(pos) instanceof ServoTwisterBlockEntity be
                && be.shouldHandlePropellerSlotPlacement(hit.getDirection(), stack)) {
            return be.tryPlacePropellerSlot(player, stack, blockItem, hit.getDirection());
        }

        if (hit.getDirection() == Direction.UP && stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (!player.mayBuild())
            return InteractionResult.FAIL;

        if (player.isShiftKeyDown())
            return InteractionResult.FAIL;

        if (!player.getMainHandItem().isEmpty())
            return InteractionResult.PASS;

        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        withBlockEntityDo(level, pos, be -> be.onPlayerToggle(player));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                && level.getBlockEntity(pos) instanceof ServoTwisterBlockEntity be
                && be.shouldHandleInternalRedstoneLinkWrench(context.getClickedFace())) {
            if (!level.isClientSide)
                be.tryToggleInternalRedstoneLinkReceiver(context.getClickedFace(), context.getPlayer());
            return InteractionResult.SUCCESS;
        }

        BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
        if (!rotated.canSurvive(level, pos))
            return InteractionResult.PASS;

        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ServoTwisterBlockEntity be) {
            if (!be.preparePitchClearanceForDisassembly())
                return InteractionResult.FAIL;
            be.disassemble();
        }

        rotated = getRotatedBlockState(level.getBlockState(pos), context.getClickedFace());
        KineticBlockEntity.switchToBlockState(level, pos, updateAfterWrenched(rotated, context));

        if (level.getBlockState(pos) != state)
            IWrenchable.playRotateSound(level, pos);

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

    public enum PowerVisualState implements StringRepresentable {
        UNPOWERED("unpowered"),
        ANGLE("angle"),
        SPEED("speed"),
        BI("bi");

        private final String serializedName;

        PowerVisualState(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
