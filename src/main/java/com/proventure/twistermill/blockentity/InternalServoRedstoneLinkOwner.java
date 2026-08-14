package com.proventure.twistermill.blockentity;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public interface InternalServoRedstoneLinkOwner {

    boolean isInternalRedstoneLinkMode();

    boolean isInternalRedstoneLinkReceiverActive();

    Direction getInternalRedstoneLinkSide();

    boolean isSecondaryInternalRedstoneLinkEligible();

    boolean isSecondaryInternalRedstoneLinkReceiverActive();

    default Direction getSecondaryInternalRedstoneLinkSide() {
        return getInternalRedstoneLinkSide().getOpposite();
    }

    default boolean shouldRenderInternalRedstoneLinkSlots() {
        return isInternalRedstoneLinkMode();
    }

    default boolean shouldRenderSecondaryInternalRedstoneLinkSlots() {
        return isSecondaryInternalRedstoneLinkEligible();
    }

    default boolean isAnyInternalRedstoneLinkReceiverActive() {
        return isInternalRedstoneLinkReceiverActive()
                || isSecondaryInternalRedstoneLinkReceiverActive();
    }

    static Direction getServoInternalLinkSide(BlockState state) {
        Direction facing = getFacing(state);
        return switch (facing) {
            case NORTH, UP -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH, DOWN -> Direction.WEST;
            case WEST -> Direction.NORTH;
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
