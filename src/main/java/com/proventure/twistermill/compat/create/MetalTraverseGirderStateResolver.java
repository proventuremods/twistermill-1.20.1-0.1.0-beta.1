package com.proventure.twistermill.compat.create;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.block.custom.MetalTraverseWithGirderBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock;
import com.simibubi.create.content.decoration.placard.PlacardBlock;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.trains.display.FlapDisplayBlock;
import com.simibubi.create.content.trains.track.TrackBlock;
import com.simibubi.create.content.trains.track.TrackShape;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public final class MetalTraverseGirderStateResolver {

    private MetalTraverseGirderStateResolver() {
    }

    public static @Nullable BlockState toEmbeddedGirderState(BlockState state) {
        if (state.getBlock() != ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
            return null;
        }
        Direction.Axis axis = state.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS);
        return AllBlocks.METAL_GIRDER.getDefaultState()
                .setValue(WATERLOGGED, state.getValue(WATERLOGGED))
                .setValue(GirderBlock.AXIS, axis)
                .setValue(GirderBlock.X, axis == Direction.Axis.X)
                .setValue(GirderBlock.Z, axis == Direction.Axis.Z)
                .setValue(GirderBlock.TOP, axis == Direction.Axis.Y)
                .setValue(GirderBlock.BOTTOM, axis == Direction.Axis.Y);
    }

    public static @Nullable BlockState resolveEmbeddedGirderState(BlockAndTintGetter world, BlockPos pos,
                                                                   BlockState compositeState) {
        BlockState girderState = toEmbeddedGirderState(compositeState);
        if (girderState == null) {
            return null;
        }
        BlockAndTintGetter virtualWorld = virtualWorld(world, pos, girderState);
        for (Direction direction : Iterate.directions) {
            girderState = updateState(virtualWorld, pos, girderState, direction);
        }
        return girderState;
    }

    public static BlockState connectToCompositeNeighbour(LevelAccessor level, BlockPos pos, BlockState state,
                                                          Direction direction) {
        if (state.getBlock() != AllBlocks.METAL_GIRDER.get()) {
            return state;
        }
        BlockState embeddedNeighbour = toEmbeddedGirderState(level.getBlockState(pos.relative(direction)));
        if (embeddedNeighbour == null) {
            return state;
        }
        if (direction.getAxis().isVertical()) {
            Property<Boolean> property = direction == Direction.UP ? GirderBlock.TOP : GirderBlock.BOTTOM;
            return state.setValue(property, true);
        }
        Property<Boolean> property = direction.getAxis() == Direction.Axis.X ? GirderBlock.X : GirderBlock.Z;
        return embeddedNeighbour.getValue(property) ? state.setValue(property, true) : state;
    }

    public static BlockAndTintGetter virtualWorld(BlockAndTintGetter world, BlockPos pos, BlockState girderState) {
        return new VirtualGirderWorld(world, pos, girderState);
    }

    private static BlockState updateState(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                          Direction direction) {
        Direction.Axis axis = direction.getAxis();
        if (axis.isHorizontal()) {
            Property<Boolean> property = axis == Direction.Axis.X ? GirderBlock.X : GirderBlock.Z;
            BlockState sideState = world.getBlockState(pos.relative(direction));
            if (state.getValue(GirderBlock.AXIS) == axis) {
                return state.setValue(property, true);
            }
            if (sideState.getBlock() instanceof GirderEncasedShaftBlock
                    && sideState.getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS) != axis) {
                return state.setValue(property, true);
            }
            if (sideState.getBlock() == state.getBlock() && sideState.getValue(property)) {
                return state.setValue(property, true);
            }
            if (sideState.getBlock() instanceof NixieTubeBlock && NixieTubeBlock.getFacing(sideState) == direction) {
                return state.setValue(property, true);
            }
            if (sideState.getBlock() instanceof PlacardBlock && PlacardBlock.connectedDirection(sideState) == direction) {
                return state.setValue(property, true);
            }
            if (GirderBlock.isFacingBracket(world, pos, direction)) {
                return state.setValue(property, true);
            }
            for (Direction perpendicular : Iterate.directionsInAxis(axis == Direction.Axis.X
                    ? Direction.Axis.Z
                    : Direction.Axis.X)) {
                BlockState above = world.getBlockState(pos.above().relative(perpendicular));
                if (AllTags.AllBlockTags.GIRDABLE_TRACKS.matches(above)) {
                    TrackShape shape = above.getValue(TrackBlock.SHAPE);
                    if (shape == (axis == Direction.Axis.X ? TrackShape.XO : TrackShape.ZO)) {
                        return state.setValue(property, true);
                    }
                }
            }
            return state;
        }

        Property<Boolean> property = direction == Direction.UP ? GirderBlock.TOP : GirderBlock.BOTTOM;
        BlockState sideState = world.getBlockState(pos.relative(direction));
        return canAttachVertically(world, pos, state, sideState, direction) ? state.setValue(property, true) : state;
    }

    private static boolean canAttachVertically(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                               BlockState sideState, Direction direction) {
        if (state.getValue(GirderBlock.AXIS) == Direction.Axis.Y || GirderBlock.isGirder(sideState)) {
            return true;
        }
        if (sideState.hasProperty(WallBlock.UP) && sideState.getValue(WallBlock.UP)) {
            return true;
        }
        if (sideState.getBlock() instanceof NixieTubeBlock && NixieTubeBlock.getFacing(sideState) == direction) {
            return true;
        }
        if (sideState.getBlock() instanceof FlapDisplayBlock) {
            return true;
        }
        if (sideState.getBlock() instanceof LanternBlock
                && (direction == Direction.DOWN) == sideState.getValue(LanternBlock.HANGING)) {
            return true;
        }
        if (sideState.getBlock() instanceof ChainBlock && sideState.getValue(ChainBlock.AXIS) == Direction.Axis.Y) {
            return true;
        }
        if (sideState.hasProperty(FaceAttachedHorizontalDirectionalBlock.FACE)) {
            AttachFace face = sideState.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
            if (face == AttachFace.CEILING && direction == Direction.DOWN) {
                return true;
            }
            if (face == AttachFace.FLOOR && direction == Direction.UP) {
                return true;
            }
        } else if (sideState.getBlock() instanceof PlacardBlock
                && PlacardBlock.connectedDirection(sideState) == direction) {
            return true;
        }
        return GirderBlock.isFacingBracket(world, pos, direction);
    }

    private static final class VirtualGirderWorld implements BlockAndTintGetter {
        private final BlockAndTintGetter delegate;
        private final BlockPos origin;
        private final BlockState centerState;

        private VirtualGirderWorld(BlockAndTintGetter delegate, BlockPos origin, BlockState centerState) {
            this.delegate = delegate;
            this.origin = origin.immutable();
            this.centerState = centerState;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return delegate.getBlockEntity(pos);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (origin.equals(pos)) {
                return centerState;
            }
            BlockState state = delegate.getBlockState(pos);
            BlockState embeddedState = toEmbeddedGirderState(state);
            return embeddedState == null ? state : embeddedState;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return delegate.getShade(direction, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return delegate.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return delegate.getBlockTint(pos, colorResolver);
        }

        @Override
        public int getHeight() {
            return delegate.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return delegate.getMinBuildHeight();
        }

        @Override
        public int getBrightness(LightLayer lightLayer, BlockPos pos) {
            return delegate.getBrightness(lightLayer, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return delegate.getRawBrightness(pos, amount);
        }

        @Override
        public boolean canSeeSky(BlockPos pos) {
            return delegate.canSeeSky(pos);
        }
    }
}
