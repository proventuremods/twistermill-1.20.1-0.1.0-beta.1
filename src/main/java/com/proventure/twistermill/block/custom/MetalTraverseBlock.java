package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.blockentity.WrenchSideCycleBlockEntity;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public class MetalTraverseBlock extends Block
        implements SimpleWaterloggedBlock, IWrenchable, IBE<WrenchSideCycleBlockEntity> {

    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new MetalTraversePlacementHelper());

    public static final BooleanProperty X = BooleanProperty.create("x");
    public static final BooleanProperty Z = BooleanProperty.create("z");
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
    public static final BooleanProperty Y_ROTATED = BooleanProperty.create("y_rotated");
    public static final BooleanProperty SUPPRESS_CORNER_HIDE = BooleanProperty.create("suppress_corner_hide");
    public static final BooleanProperty MANUAL_BRACKET_NORTH = BooleanProperty.create("manual_bracket_north");
    public static final BooleanProperty MANUAL_BRACKET_SOUTH = BooleanProperty.create("manual_bracket_south");
    public static final BooleanProperty MANUAL_BRACKET_EAST = BooleanProperty.create("manual_bracket_east");
    public static final BooleanProperty MANUAL_BRACKET_WEST = BooleanProperty.create("manual_bracket_west");
    public static final BooleanProperty MANUAL_BRACKET_UP = BooleanProperty.create("manual_bracket_up");
    public static final BooleanProperty MANUAL_BRACKET_DOWN = BooleanProperty.create("manual_bracket_down");
    public static final EnumProperty<Axis> AXIS = BlockStateProperties.AXIS;

    private static final long TRAVERSE_HIDE_CORNER_BREAK_TTL_TICKS = 200L;
    private static final Map<TraverseCornerHideBreakKey, TraverseCornerHideBreakMarker>
            RECENT_TRAVERSE_HIDE_CORNER_BREAKS = new HashMap<>();

    public MetalTraverseBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(WATERLOGGED, false)
                .setValue(AXIS, Axis.Y)
                .setValue(TOP, false)
                .setValue(BOTTOM, false)
                .setValue(X, false)
                .setValue(Z, false)
                .setValue(Y_ROTATED, false)
                .setValue(SUPPRESS_CORNER_HIDE, false)
                .setValue(MANUAL_BRACKET_NORTH, false)
                .setValue(MANUAL_BRACKET_SOUTH, false)
                .setValue(MANUAL_BRACKET_EAST, false)
                .setValue(MANUAL_BRACKET_WEST, false)
                .setValue(MANUAL_BRACKET_UP, false)
                .setValue(MANUAL_BRACKET_DOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(
                X, Z, TOP, BOTTOM, AXIS, Y_ROTATED, WATERLOGGED,
                SUPPRESS_CORNER_HIDE,
                MANUAL_BRACKET_NORTH, MANUAL_BRACKET_SOUTH, MANUAL_BRACKET_EAST,
                MANUAL_BRACKET_WEST, MANUAL_BRACKET_UP, MANUAL_BRACKET_DOWN));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter reader, BlockPos pos) {
        return Shapes.or(super.getBlockSupportShape(state, reader, pos), AllShapes.EIGHT_VOXEL_POLE.get(Axis.Y));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player == null || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        IPlacementHelper helper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (helper.matchesItem(stack) && stack.getItem() instanceof BlockItem blockItem) {
            return helper.getOffset(player, level, state, pos, hitResult)
                    .placeInWorld(level, blockItem, player, hand, hitResult);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Block.updateOrDestroy(state, Block.updateFromNeighbourShapes(state, level, pos), level, pos, 3);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState, LevelAccessor world, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        }

        Axis axis = direction.getAxis();

        if (axis != Axis.Y && isVerticalLocked(state) && !canUnlockTraverseVerticalCross(world, pos, state, direction)) {
            return state;
        }

        if (axis != Axis.Y) {
            if (state.getValue(AXIS) != axis) {
                Property<Boolean> updateProperty = axis == Axis.X ? X : Z;
                if (!isConnected(world, pos, state, direction) && !isConnected(world, pos, state, direction.getOpposite())) {
                    state = state.setValue(updateProperty, false);
                }
            }
        } else if (state.getValue(AXIS) != Axis.Y) {
            if (world.getBlockState(pos.above()).getBlockSupportShape(world, pos.above()).isEmpty()) {
                state = state.setValue(TOP, false);
            }
            if (world.getBlockState(pos.below()).getBlockSupportShape(world, pos.below()).isEmpty()) {
                state = state.setValue(BOTTOM, false);
            }
        }

        for (Direction d : Iterate.directionsInAxis(axis)) {
            state = updateState(world, pos, state, d);
        }

        return state;
    }

    private static boolean isVerticalLocked(BlockState state) {
        return state.getValue(AXIS) == Axis.Y
                && state.getValue(TOP)
                && state.getValue(BOTTOM)
                && !state.getValue(X)
                && !state.getValue(Z);
    }

    private static boolean canUnlockTraverseVerticalCross(LevelAccessor world, BlockPos pos, BlockState state,
                                                          Direction direction) {
        if (!(state.getBlock() instanceof MetalTraverseBlock)) {
            return false;
        }

        BlockState sideState = world.getBlockState(pos.relative(direction));
        return MetalFrameConnectionHelper.isMetalFrameConnector(sideState)
                && MetalFrameConnectionHelper.hasMetalFrameAxis(sideState, direction.getAxis());
    }

    private static BooleanProperty getManualBracketProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> MANUAL_BRACKET_NORTH;
            case SOUTH -> MANUAL_BRACKET_SOUTH;
            case EAST -> MANUAL_BRACKET_EAST;
            case WEST -> MANUAL_BRACKET_WEST;
            case UP -> MANUAL_BRACKET_UP;
            case DOWN -> MANUAL_BRACKET_DOWN;
        };
    }

    public static boolean hasManualBracket(BlockState state, Direction direction) {
        if (!(state.getBlock() instanceof MetalTraverseBlock)) {
            return false;
        }
        return state.getValue(getManualBracketProperty(direction));
    }

    public static boolean shouldAutoCloseVerticalCaps(BlockAndTintGetter world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MetalTraverseBlock)
                || state.getValue(AXIS) != Axis.Y
                || !state.getValue(TOP)
                || !state.getValue(BOTTOM)) {
            return false;
        }

        boolean zPair = hasAxisTraverseNeighbour(world, pos, Direction.NORTH, Axis.Z, Z)
                && hasAxisTraverseNeighbour(world, pos, Direction.SOUTH, Axis.Z, Z);
        boolean xPair = hasAxisTraverseNeighbour(world, pos, Direction.EAST, Axis.X, X)
                && hasAxisTraverseNeighbour(world, pos, Direction.WEST, Axis.X, X);
        return zPair || xPair;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        FluidState fluidState = level.getFluidState(pos);

        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }

        state = state.setValue(X, face.getAxis() == Axis.X);
        state = state.setValue(Z, face.getAxis() == Axis.Z);
        state = state.setValue(AXIS, face.getAxis());

        BlockPos attachedPos = pos.relative(face.getOpposite());
        BlockState attachedState = level.getBlockState(attachedPos);
        if (isMetalTraverse(attachedState)) {
            state = state.setValue(Y_ROTATED, !attachedState.getValue(Y_ROTATED));
        } else {
            state = state.setValue(Y_ROTATED, false);
        }

        for (Direction d : Iterate.directions) {
            state = updateState(level, pos, state, d);
        }

        state = applyTraverseCornerHideSuppression(level, pos, state);

        return state.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    public static BlockState computePlacementHelperState(Level level, BlockPos pos, BlockState placedState,
                                                         BlockState sourceState, Direction extensionDirection,
                                                         Direction clickedFace, boolean yRotated) {
        if (!(placedState.getBlock() instanceof MetalTraverseBlock)) {
            return placedState;
        }

        Axis extensionAxis = extensionDirection.getAxis();
        BlockState state = placedState
                .setValue(X, extensionAxis == Axis.X)
                .setValue(Z, extensionAxis == Axis.Z)
                .setValue(AXIS, extensionAxis)
                .setValue(Y_ROTATED, yRotated)
                .setValue(SUPPRESS_CORNER_HIDE, false);

        for (Direction direction : Iterate.directions) {
            state = updateState(level, pos, state, direction);
        }

        if (isStrictVerticalSource(sourceState)
                && extensionAxis == Axis.Y
                && clickedFace.getAxis() != extensionAxis) {
            state = state.setValue(AXIS, Axis.Y)
                    .setValue(X, false)
                    .setValue(Z, false)
                    .setValue(Y_ROTATED, yRotated);
        }

        return applyTraverseCornerHideSuppression(level, pos, state);
    }

    public static BlockState updateState(LevelAccessor level, BlockPos pos, BlockState state, Direction direction) {
        Axis axis = direction.getAxis();
        Property<Boolean> updateProperty = axis == Axis.X ? X : axis == Axis.Z ? Z : direction == Direction.UP ? TOP : BOTTOM;
        BlockState sideState = level.getBlockState(pos.relative(direction));

        if (axis.isVertical()) {
            return updateVerticalProperty(level, pos, state, updateProperty, sideState, direction);
        }

        if (state.getValue(AXIS) == axis) {
            state = state.setValue(updateProperty, true);
        } else if (MetalFrameConnectionHelper.hasMetalFrameAxis(sideState, axis)) {
            state = state.setValue(updateProperty, true);
        } else if (canConnectToFace(level, pos, direction)) {
            state = state.setValue(updateProperty, true);
        }

        return state;
    }

    public static BlockState updateVerticalProperty(LevelAccessor level, BlockPos pos, BlockState state, Property<Boolean> updateProperty, BlockState sideState, Direction direction) {
        boolean canAttach = false;

        if (state.getValue(AXIS) == Axis.Y) {
            canAttach = true;
        } else if (MetalFrameConnectionHelper.isMetalFrameConnector(sideState)) {
            canAttach = true;
        } else if (sideState.hasProperty(WallBlock.UP) && sideState.getValue(WallBlock.UP)) {
            canAttach = true;
        } else if (sideState.getBlock() instanceof LanternBlock
                && (direction == Direction.DOWN) == sideState.getValue(LanternBlock.HANGING)) {
            canAttach = true;
        } else if (sideState.getBlock() instanceof ChainBlock && sideState.getValue(ChainBlock.AXIS) == Axis.Y) {
            canAttach = true;
        } else if (sideState.hasProperty(FACE)) {
            AttachFace face = sideState.getValue(FACE);
            if (face == AttachFace.CEILING && direction == Direction.DOWN) {
                canAttach = true;
            } else if (face == AttachFace.FLOOR && direction == Direction.UP) {
                canAttach = true;
            }
        } else if (canConnectToFace(level, pos, direction)) {
            canAttach = true;
        }

        if (canAttach) {
            return state.setValue(updateProperty, true);
        }
        return state;
    }

    private static boolean isMetalTraverse(BlockState state) {
        return state.getBlock() instanceof MetalTraverseBlock;
    }

    private static boolean isStrictVerticalSource(BlockState state) {
        return state.getBlock() instanceof MetalTraverseBlock
                && state.getValue(AXIS) == Axis.Y
                && state.getValue(TOP)
                && state.getValue(BOTTOM)
                && !state.getValue(X)
                && !state.getValue(Z);
    }

    public static boolean suppressesCornerHide(BlockState state) {
        return state.hasProperty(SUPPRESS_CORNER_HIDE) && state.getValue(SUPPRESS_CORNER_HIDE);
    }

    private static BlockState applyTraverseCornerHideSuppression(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MetalTraverseBlock)) {
            return state;
        }

        pruneExpiredTraverseHideCornerBreaks(level);

        TraverseCornerHideBreakKey key = new TraverseCornerHideBreakKey(level.dimension(), pos.asLong(), state.getBlock());
        TraverseCornerHideBreakMarker marker = level.isClientSide
                ? RECENT_TRAVERSE_HIDE_CORNER_BREAKS.get(key)
                : RECENT_TRAVERSE_HIDE_CORNER_BREAKS.remove(key);
        if (marker == null || marker.expiresAtTick() < level.getGameTime()) {
            return state.setValue(SUPPRESS_CORNER_HIDE, false);
        }

        TraverseCornerHideSignature currentSignature = getTraverseCornerHideSignature(level, pos, state);
        if (currentSignature != TraverseCornerHideSignature.NONE
                && currentSignature == marker.signature()) {
            return state.setValue(SUPPRESS_CORNER_HIDE, true);
        }

        return state.setValue(SUPPRESS_CORNER_HIDE, false);
    }

    private static void rememberTraverseHideCornerBreak(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MetalTraverseBlock) || suppressesCornerHide(state)) {
            return;
        }

        TraverseCornerHideSignature signature = getTraverseCornerHideSignature(level, pos, state);
        if (signature == TraverseCornerHideSignature.NONE) {
            return;
        }

        pruneExpiredTraverseHideCornerBreaks(level);
        TraverseCornerHideBreakKey key = new TraverseCornerHideBreakKey(level.dimension(), pos.asLong(), state.getBlock());
        long expiresAtTick = level.getGameTime() + TRAVERSE_HIDE_CORNER_BREAK_TTL_TICKS;
        RECENT_TRAVERSE_HIDE_CORNER_BREAKS.put(
                key,
                new TraverseCornerHideBreakMarker(signature, expiresAtTick));
    }

    private static void pruneExpiredTraverseHideCornerBreaks(Level level) {
        ResourceKey<Level> dimension = level.dimension();
        long gameTime = level.getGameTime();
        RECENT_TRAVERSE_HIDE_CORNER_BREAKS.entrySet().removeIf(entry ->
                entry.getKey().dimension().equals(dimension)
                        && entry.getValue().expiresAtTick() < gameTime);
    }

    private static TraverseCornerHideSignature getTraverseCornerHideSignature(
            BlockAndTintGetter world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MetalTraverseBlock)
                || state.getValue(AXIS) != Axis.Y
                || !state.getValue(TOP)
                || !state.getValue(BOTTOM)
                || !hasVerticalTraverseNeighbourForCornerHide(world, pos, Direction.DOWN)
                || hasVerticalTraverseNeighbourForCornerHide(world, pos, Direction.UP)) {
            return TraverseCornerHideSignature.NONE;
        }

        boolean eastX = hasTraverseNeighbourOnAxisForCornerHide(world, pos, Direction.EAST, Axis.X);
        boolean westX = hasTraverseNeighbourOnAxisForCornerHide(world, pos, Direction.WEST, Axis.X);
        boolean northZ = hasTraverseNeighbourOnAxisForCornerHide(world, pos, Direction.NORTH, Axis.Z);
        boolean southZ = hasTraverseNeighbourOnAxisForCornerHide(world, pos, Direction.SOUTH, Axis.Z);
        if (eastX == westX || northZ == southZ) {
            return TraverseCornerHideSignature.NONE;
        }

        if (eastX && northZ) {
            return TraverseCornerHideSignature.EAST_NORTH;
        }
        if (westX && southZ) {
            return TraverseCornerHideSignature.WEST_SOUTH;
        }
        if (westX && northZ) {
            return TraverseCornerHideSignature.WEST_NORTH;
        }
        if (eastX && southZ) {
            return TraverseCornerHideSignature.EAST_SOUTH;
        }
        return TraverseCornerHideSignature.NONE;
    }

    private static boolean hasTraverseNeighbourOnAxisForCornerHide(BlockAndTintGetter world, BlockPos pos,
                                                                   Direction direction, Axis axis) {
        BlockState neighbourState = world.getBlockState(pos.relative(direction));
        return neighbourState.getBlock() instanceof MetalTraverseBlock
                && MetalFrameConnectionHelper.hasMetalFrameAxis(neighbourState, axis);
    }

    private static boolean hasVerticalTraverseNeighbourForCornerHide(BlockAndTintGetter world, BlockPos pos,
                                                                     Direction direction) {
        BlockState neighbourState = world.getBlockState(pos.relative(direction));
        return neighbourState.getBlock() instanceof MetalTraverseBlock
                && MetalFrameConnectionHelper.isMetalFrameVertical(neighbourState);
    }

    private static boolean canConnectToFace(BlockAndTintGetter world, BlockPos pos, Direction side) {
        BlockPos relative = pos.relative(side);
        BlockState blockState = world.getBlockState(relative);
        if (blockState.isAir()) {
            return false;
        }
        VoxelShape shape = blockState.getShape(world, relative);
        if (shape.isEmpty()) {
            return false;
        }
        return Block.isFaceFull(shape, side.getOpposite()) && blockState.isSolid();
    }

    public static boolean wouldAutoRenderBracket(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                                 Direction side) {
        if (!(state.getBlock() instanceof MetalTraverseBlock)) {
            return false;
        }

        if (side.getAxis().isVertical()) {
            return hasNonTraverseSupport(world, pos, side)
                    || shouldAutoCloseVerticalCaps(world, pos, state);
        }

        boolean connected = isConnected(world, pos, state, side);
        return connected && !isStraightMetalFrameRow(world, pos, state, side, connected);
    }

    private static boolean hasNonTraverseSupport(BlockAndTintGetter world, BlockPos pos, Direction direction) {
        BlockPos relative = pos.relative(direction);
        BlockState blockState = world.getBlockState(relative);
        if (MetalFrameConnectionHelper.isMetalFrameConnector(blockState)) {
            return false;
        }
        if (blockState.isAir()) {
            return false;
        }
        VoxelShape shape = blockState.getShape(world, relative);
        if (shape.isEmpty()) {
            return false;
        }
        return Block.isFaceFull(shape, direction.getOpposite()) && blockState.isSolid();
    }

    private static boolean isStraightMetalFrameRow(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                                   Direction direction, boolean connected) {
        if (!connected || !direction.getAxis().isHorizontal()) {
            return false;
        }

        Direction.Axis axis = direction.getAxis();
        if (!MetalFrameConnectionHelper.hasMetalFrameAxis(state, axis)) {
            return false;
        }

        BlockState neighbourState = world.getBlockState(pos.relative(direction));
        return MetalFrameConnectionHelper.isMetalFrameConnector(neighbourState)
                && MetalFrameConnectionHelper.hasMetalFrameAxis(neighbourState, axis);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean x = state.getValue(X);
        boolean z = state.getValue(Z);
        return x ? (z ? AllShapes.GIRDER_CROSS : AllShapes.GIRDER_BEAM.get(Axis.X))
                : (z ? AllShapes.GIRDER_BEAM.get(Axis.Z) : AllShapes.EIGHT_VOXEL_POLE.get(Axis.Y));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            rememberTraverseHideCornerBreak(level, pos, state);
        }

        IBE.onRemove(state, level, pos, newState);
    }

    public static boolean isConnected(BlockAndTintGetter world, BlockPos pos, BlockState state, Direction side) {
        Axis axis = side.getAxis();
        if (state.getBlock() instanceof MetalTraverseBlock && !state.getValue(axis == Axis.X ? X : Z)) {
            return false;
        }

        BlockPos relative = pos.relative(side);
        BlockState blockState = world.getBlockState(relative);
        if (blockState.isAir()) {
            return false;
        }

        if (MetalFrameConnectionHelper.isMetalFrameConnector(blockState)) {
            return MetalFrameConnectionHelper.hasMetalFrameAxis(blockState, side.getAxis())
                    || MetalFrameConnectionHelper.isMetalFrameVertical(blockState);
        }

        VoxelShape shape = blockState.getShape(world, relative);
        if (shape.isEmpty()) {
            return false;
        }
        return Block.isFaceFull(shape, side.getOpposite()) && blockState.isSolid();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Axis rotatedAxis =
                rotation.rotate(Direction.fromAxisAndDirection(state.getValue(AXIS), AxisDirection.POSITIVE)).getAxis();
        boolean previousX = state.getValue(X);
        boolean previousZ = state.getValue(Z);
        state = state.setValue(AXIS, rotatedAxis);
        if (rotation.rotate(Direction.EAST).getAxis() != Axis.X) {
            state = state.setValue(X, previousZ).setValue(Z, previousX);
        }
        return rotateManualBrackets(state, rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirrorManualBrackets(state, mirror);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction side = context.getClickedFace();

        if (!level.isClientSide) {
            WrenchSideCycleBlockEntity be = getOrCreateSideCycleBlockEntity(level, pos);
            if (be != null) {
                BlockState currentState = level.getBlockState(pos);
                boolean extrasAllowed = isExtraCycleAllowed(state, level, pos, side);
                boolean autoBracketVisible = wouldAutoRenderBracket(level, pos, currentState, side);
                byte nextStage = be.advance(side, extrasAllowed, autoBracketVisible);

                BlockState updatedState = currentState.setValue(
                        getManualBracketProperty(side),
                        WrenchSideCycleBlockEntity.isBracketStage(nextStage));
                if (updatedState != currentState) {
                    level.setBlock(pos, updatedState, 3);
                }

                be.markChangedAndSync();
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean isExtraCycleAllowed(BlockState state, Level level, BlockPos pos, Direction side) {
        return false;
    }

    private static boolean hasAxisTraverseNeighbour(BlockAndTintGetter level, BlockPos pos, Direction direction, Axis axis,
                                                    BooleanProperty axisProperty) {
        BlockState neighbourState = level.getBlockState(pos.relative(direction));
        return MetalFrameConnectionHelper.isMetalFrameConnector(neighbourState)
                && MetalFrameConnectionHelper.hasMetalFrameAxis(neighbourState, axis);
    }

    private WrenchSideCycleBlockEntity getOrCreateSideCycleBlockEntity(Level level, BlockPos pos) {
        WrenchSideCycleBlockEntity existing = getBlockEntity(level, pos);
        if (existing != null) {
            return existing;
        }

        BlockEntity created = level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE);
        if (created instanceof WrenchSideCycleBlockEntity sideCycle) {
            return sideCycle;
        }

        return null;
    }

    @Override
    public Class<WrenchSideCycleBlockEntity> getBlockEntityClass() {
        return WrenchSideCycleBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends WrenchSideCycleBlockEntity> getBlockEntityType() {
        return ModBlockEntities.METAL_SIDE_CYCLE_BLOCK_ENTITY.get();
    }

    private static BlockState rotateManualBrackets(BlockState state, Rotation rotation) {
        boolean north = state.getValue(MANUAL_BRACKET_NORTH);
        boolean south = state.getValue(MANUAL_BRACKET_SOUTH);
        boolean east = state.getValue(MANUAL_BRACKET_EAST);
        boolean west = state.getValue(MANUAL_BRACKET_WEST);

        return switch (rotation) {
            case NONE -> state;
            case CLOCKWISE_180 -> state
                    .setValue(MANUAL_BRACKET_NORTH, south)
                    .setValue(MANUAL_BRACKET_SOUTH, north)
                    .setValue(MANUAL_BRACKET_EAST, west)
                    .setValue(MANUAL_BRACKET_WEST, east);
            case CLOCKWISE_90 -> state
                    .setValue(MANUAL_BRACKET_NORTH, west)
                    .setValue(MANUAL_BRACKET_EAST, north)
                    .setValue(MANUAL_BRACKET_SOUTH, east)
                    .setValue(MANUAL_BRACKET_WEST, south);
            case COUNTERCLOCKWISE_90 -> state
                    .setValue(MANUAL_BRACKET_NORTH, east)
                    .setValue(MANUAL_BRACKET_EAST, south)
                    .setValue(MANUAL_BRACKET_SOUTH, west)
                    .setValue(MANUAL_BRACKET_WEST, north);
        };
    }

    private static BlockState mirrorManualBrackets(BlockState state, Mirror mirror) {
        boolean north = state.getValue(MANUAL_BRACKET_NORTH);
        boolean south = state.getValue(MANUAL_BRACKET_SOUTH);
        boolean east = state.getValue(MANUAL_BRACKET_EAST);
        boolean west = state.getValue(MANUAL_BRACKET_WEST);

        return switch (mirror) {
            case NONE -> state;
            case LEFT_RIGHT -> state
                    .setValue(MANUAL_BRACKET_NORTH, south)
                    .setValue(MANUAL_BRACKET_SOUTH, north);
            case FRONT_BACK -> state
                    .setValue(MANUAL_BRACKET_EAST, west)
                    .setValue(MANUAL_BRACKET_WEST, east);
        };
    }

    private enum TraverseCornerHideSignature {
        NONE,
        EAST_NORTH,
        WEST_SOUTH,
        WEST_NORTH,
        EAST_SOUTH
    }

    private record TraverseCornerHideBreakKey(ResourceKey<Level> dimension, long blockPos, Block block) {
    }

    private record TraverseCornerHideBreakMarker(TraverseCornerHideSignature signature, long expiresAtTick) {
    }
}
