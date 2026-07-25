package com.proventure.twistermill.compat.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Vector3d;

public final class SableDisplayLinkPositionHelper {

    private SableDisplayLinkPositionHelper() {
    }

    public static BlockPos toWorldBlockPos(Level level, BlockPos pos) {
        if (level == null) {
            return pos;
        }

        if (level instanceof ServerLevel serverLevel) {
            SubLevel containing = Sable.HELPER.getContaining(serverLevel, pos);
            if (containing instanceof ServerSubLevel serverSubLevel) {
                Vector3d world = serverSubLevel.logicalPose()
                        .transformPosition(JOMLConversion.atCenterOf(pos), new Vector3d());
                return BlockPos.containing(world.x, world.y, world.z);
            }
            return pos;
        }

        if (Sable.HELPER.getContaining(level, pos) instanceof SubLevel containing) {
            Vector3d world = containing.logicalPose()
                    .transformPosition(JOMLConversion.atCenterOf(pos), new Vector3d());
            return BlockPos.containing(world.x, world.y, world.z);
        }

        return pos;
    }

    public static Direction toWorldDirection(Level level, BlockPos localPos, Direction localDirection) {
        if (level == null) {
            return localDirection;
        }

        if (!(Sable.HELPER.getContaining(level, localPos) instanceof SubLevel containing)) {
            return localDirection;
        }

        Vector3d local = new Vector3d(localDirection.getStepX(), localDirection.getStepY(), localDirection.getStepZ());
        Vector3d world = containing.logicalPose().transformNormal(local, new Vector3d());
        if (world.lengthSquared() <= 1.0E-9D) {
            return localDirection;
        }

        return Direction.getNearest(world.x, world.y, world.z);
    }

    public static BlockState toWorldBlockState(Level level, BlockPos localPos, BlockState localState) {
        if (level == null || localState == null) {
            return localState;
        }

        if (!(Sable.HELPER.getContaining(level, localPos) instanceof SubLevel)) {
            return localState;
        }

        BlockState worldState = localState;
        for (Property<?> property : localState.getProperties()) {
            if (property instanceof DirectionProperty directionProperty) {
                Direction localDirection = localState.getValue(directionProperty);
                Direction worldDirection = toWorldDirection(level, localPos, localDirection);
                if (directionProperty.getPossibleValues().contains(worldDirection)) {
                    worldState = worldState.setValue(directionProperty, worldDirection);
                }
            }
        }

        return worldState;
    }
}
