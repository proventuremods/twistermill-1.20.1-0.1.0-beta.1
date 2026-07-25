package com.proventure.twistermill.blockentity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public interface InternalServoRedstoneLinkOwner {

    boolean isInternalRedstoneLinkMode();

    boolean isInternalRedstoneLinkReceiverActive();

    Direction getInternalRedstoneLinkSide();

    default boolean shouldRenderInternalRedstoneLinkSlots() {
        return isInternalRedstoneLinkMode();
    }

    static Direction getServoInternalLinkSide(BlockState state) {
        Direction facing = getFacing(state);
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case UP -> Direction.EAST;
            case DOWN -> Direction.WEST;
        };
    }

    static Direction getInvServoInternalLinkSide(BlockState state) {
        return getServoInternalLinkSide(state).getOpposite();
    }

    private static Direction getFacing(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;
    }
}
