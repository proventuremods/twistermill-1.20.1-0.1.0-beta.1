package com.proventure.twistermill.util;

import com.proventure.twistermill.block.custom.TwisterSailBlock;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class TwisterSailSurfacePatternUtil {

    public static final int MAX_SIDE_DISTANCE = 4;

    private TwisterSailSurfacePatternUtil() {
    }

    public static List<BlockPos> collectNextPerSide(
            Level level,
            BlockPos centerPos,
            Direction playerHorizontalFacing,
            Predicate<BlockState> canContinueAlongLine,
            Predicate<BlockState> shouldSelect
    ) {
        List<BlockPos> targets = new ArrayList<>(2);
        for (Direction sideDir : getSideDirections(playerHorizontalFacing)) {
            for (int distance = 1; distance <= MAX_SIDE_DISTANCE; distance++) {
                BlockPos candidatePos = centerPos.relative(sideDir, distance);
                BlockState candidateState = level.getBlockState(candidatePos);

                if (!canContinueAlongLine.test(candidateState)) {
                    break;
                }

                if (shouldSelect.test(candidateState)) {
                    targets.add(candidatePos);
                    break;
                }
            }
        }
        return targets;
    }

    public static Direction[] getSideDirections(Direction playerHorizontalFacing) {
        if (playerHorizontalFacing == Direction.EAST || playerHorizontalFacing == Direction.WEST) {
            return new Direction[]{Direction.NORTH, Direction.SOUTH};
        }
        return new Direction[]{Direction.WEST, Direction.EAST};
    }

    public static boolean isSameFacingTwisterSail(BlockState state, Direction facing) {
        if (!(state.getBlock() instanceof TwisterSailBlock)) {
            return false;
        }
        if (!state.hasProperty(SailBlock.FACING)) {
            return false;
        }
        return state.getValue(SailBlock.FACING) == facing;
    }
}
