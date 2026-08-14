package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.blockentity.WrenchSideCycleBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public class MetalTraverseWithGirderBlock extends MetalTraverseBlock implements SpecialBlockItemRequirement {

    public static final EnumProperty<Direction.Axis> GIRDER_AXIS =
            EnumProperty.create("girder_axis", Direction.Axis.class);

    public MetalTraverseWithGirderBlock(Properties properties) {
        super(properties, false);
        registerDefaultState(defaultBlockState().setValue(GIRDER_AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        addCoreProperties(builder);
        super.createBlockStateDefinitionForComposite(builder.add(GIRDER_AXIS));
    }

    public static BlockState fromTraverseState(BlockState source, Direction.Axis girderAxis) {
        BlockState target = ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get().defaultBlockState()
                .setValue(GIRDER_AXIS, girderAxis);
        return copyCoreProperties(source, target);
    }

    private static BlockState embeddedGirderState(BlockState state) {
        Direction.Axis axis = state.getValue(GIRDER_AXIS);
        BlockState girder = AllBlocks.METAL_GIRDER.getDefaultState()
                .setValue(GirderBlock.AXIS, axis)
                .setValue(WATERLOGGED, state.getValue(WATERLOGGED));
        return switch (axis) {
            case X -> girder.setValue(GirderBlock.X, true);
            case Y -> girder.setValue(GirderBlock.TOP, true).setValue(GirderBlock.BOTTOM, true);
            case Z -> girder.setValue(GirderBlock.Z, true);
        };
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return activeLayerState(state, level, pos).getDestroyProgress(player, level, pos);
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return activeLayerState(state, level, pos).canHarvestBlock(level, pos, player);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        BlockState remainingState = isTraverseFirst(level.getBlockEntity(pos))
                ? restoredGirderState(level, pos, state)
                : toPlainTraverseState(state);
        return level.setBlock(pos, remainingState, Block.UPDATE_ALL);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (isTraverseFirst(blockEntity)) {
            BlockState traverse = toPlainTraverseState(state);
            ModBlocks.METAL_TRAVERSE.get().playerDestroy(level, player, pos, traverse, blockEntity, tool);
            return;
        }
        BlockState girder = embeddedGirderState(state);
        AllBlocks.METAL_GIRDER.get().playerDestroy(level, player, pos, girder, null, tool);
    }

    private static BlockState activeLayerState(BlockState state, BlockGetter level, BlockPos pos) {
        return isTraverseFirst(level.getBlockEntity(pos))
                ? toPlainTraverseState(state)
                : embeddedGirderState(state);
    }

    private static boolean isTraverseFirst(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof WrenchSideCycleBlockEntity sideCycle
                && sideCycle.isTraverseAddedToGirder();
    }

    private static BlockState restoredGirderState(Level level, BlockPos pos, BlockState state) {
        BlockState girder = embeddedGirderState(state);
        for (Direction direction : Iterate.directions) {
            girder = GirderBlock.updateState(level, pos, girder, direction);
        }
        return girder;
    }

    @Override
    public @NotNull VoxelShape getBlockSupportShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                                    @NotNull BlockPos pos) {
        return Shapes.or(super.getBlockSupportShape(state, level, pos), girderShape(state));
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos,
                                       @NotNull CollisionContext context) {
        return Shapes.or(super.getShape(state, level, pos, context), girderShape(state));
    }

    private static VoxelShape girderShape(BlockState state) {
        Direction.Axis axis = state.getValue(GIRDER_AXIS);
        return axis == Direction.Axis.Y
                ? AllShapes.EIGHT_VOXEL_POLE.get(Direction.Axis.Y)
                : AllShapes.GIRDER_BEAM.get(axis);
    }

    @Override
    protected @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        Direction.Axis girderAxis = rotation.rotate(Direction.fromAxisAndDirection(
                state.getValue(GIRDER_AXIS), Direction.AxisDirection.POSITIVE)).getAxis();
        return super.rotate(state, rotation).setValue(GIRDER_AXIS, girderAxis);
    }

    @Override
    public @NotNull ItemStack getCloneItemStack(@NotNull BlockState state, @NotNull HitResult target,
                                                @NotNull LevelReader level, @NotNull BlockPos pos,
                                                @NotNull Player player) {
        return new ItemStack(ModBlocks.METAL_TRAVERSE.get());
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, @Nullable BlockEntity blockEntity) {
        return new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, ModBlocks.METAL_TRAVERSE.get().asItem())
                .union(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, AllBlocks.METAL_GIRDER.asItem()));
    }
}
