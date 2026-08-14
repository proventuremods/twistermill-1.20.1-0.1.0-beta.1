package com.proventure.twistermill.block.custom;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class BladeArmBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public BladeArmBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = resolveServoSlotAFacing(context);
        if (facing == null) {
            facing = context.getClickedFace();
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    private static @Nullable Direction resolveServoSlotAFacing(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos placementPos = context.getClickedPos();

        Direction clickedNeighborFacing = resolveServoFacingAt(level, placementPos.relative(context.getClickedFace().getOpposite()));
        if (clickedNeighborFacing == context.getClickedFace()) {
            return getServoSlotAFacing(clickedNeighborFacing);
        }

        for (Direction candidateFacing : Direction.values()) {
            BlockPos servoPos = placementPos.relative(candidateFacing.getOpposite());
            Direction servoFacing = resolveServoFacingAt(level, servoPos);
            if (servoFacing == candidateFacing) {
                return getServoSlotAFacing(servoFacing);
            }
        }

        return null;
    }

    private static @Nullable Direction resolveServoFacingAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ServoTwisterBlock)
                || !state.hasProperty(BlockStateProperties.FACING)) {
            return null;
        }
        return state.getValue(BlockStateProperties.FACING);
    }

    public static Direction getServoSlotAFacing(Direction servoFacing) {
        return switch (servoFacing) {
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            default -> servoFacing;
        };
    }
}
