package com.proventure.twistermill.block.custom;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public final class MetalFrameConnectionHelper {

    private MetalFrameConnectionHelper() {
    }

    public static boolean isMetalFrameConnector(BlockState state) {
        return state.getBlock() instanceof MetalTraverseBlock;
    }

    public static boolean hasMetalFrameAxis(BlockState state, Direction.Axis axis) {
        if (state.getBlock() instanceof MetalTraverseBlock) {
            return switch (axis) {
                case X -> state.getValue(MetalTraverseBlock.X);
                case Y -> state.getValue(MetalTraverseBlock.AXIS) == Direction.Axis.Y;
                case Z -> state.getValue(MetalTraverseBlock.Z);
            };
        }
        return false;
    }

    public static boolean isMetalFrameVertical(BlockState state) {
        return hasMetalFrameAxis(state, Direction.Axis.Y);
    }

    public static boolean canExtendMetalFrameToward(BlockState state, Direction side) {
        if (!isMetalFrameConnector(state)) {
            return false;
        }

        Direction.Axis axis = side.getAxis();
        boolean x = hasMetalFrameAxis(state, Direction.Axis.X);
        boolean z = hasMetalFrameAxis(state, Direction.Axis.Z);
        if (!x && !z) {
            return axis == Direction.Axis.Y;
        }
        if (x && z) {
            return true;
        }
        return axis == (x ? Direction.Axis.X : Direction.Axis.Z);
    }
}
