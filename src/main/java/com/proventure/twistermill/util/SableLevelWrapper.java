package com.proventure.twistermill.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3d;

public final class SableLevelWrapper {

    private SableLevelWrapper() {
    }

    public static Level getRootLevel(Level level) {
        //noinspection ConstantValue
        if (level instanceof SubLevelAccess access && access instanceof SubLevel subLevel) {
            return subLevel.getLevel();
        }
        return level;
    }

    public static Vector3d toWorldCenter(Level level, BlockPos pos) {
        Vector3d center = JOMLConversion.atCenterOf(pos);
        return Sable.HELPER.projectOutOfSubLevel(level, center);
    }

    public static BlockPos toWorldPos(Level level, BlockPos pos) {
        Vector3d worldCenter = toWorldCenter(level, pos);
        return BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z);
    }

    public static Vector3d toLocalCenter(Level level, BlockPos pos) {
        Vector3d center = JOMLConversion.atCenterOf(pos);
        SubLevelAccess containing = Sable.HELPER.getContaining(level, center);
        if (containing != null) {
            return containing.logicalPose().transformPositionInverse(center);
        }
        return center;
    }

    @SuppressWarnings("unused")
    public static BlockPos toLocalPos(Level level, BlockPos pos) {
        Vector3d localCenter = toLocalCenter(level, pos);
        return BlockPos.containing(localCenter.x, localCenter.y, localCenter.z);
    }

    public static boolean isSubLevel(Level level) {
        return level instanceof SubLevelAccess;
    }

    @SuppressWarnings("unused")
    public static boolean isSubLevel(Level level, BlockPos pos) {
        return Sable.HELPER.getContaining(level, pos) instanceof ServerSubLevel;
    }
}
