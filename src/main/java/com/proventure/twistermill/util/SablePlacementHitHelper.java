package com.proventure.twistermill.util;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class SablePlacementHitHelper {

    private static final double HIT_BLOCK_CONSISTENCY_LIMIT = 1.0000001D;

    private SablePlacementHitHelper() {
    }

    public static Vec3 ensureHitLocationInSameSpaceAsPos(Level level, BlockPos pos, BlockHitResult ray) {
        Vec3 hit = ray.getLocation();
        if (isHitLocationConsistentWithPos(pos, hit)) {
            return hit;
        }

        SubLevel containing = Sable.HELPER.getContaining(level, pos);
        if (containing == null) {
            return hit;
        }

        Vec3 localHit = containing.logicalPose().transformPositionInverse(hit);
        if (isHitLocationConsistentWithPos(pos, localHit)) {
            return localHit;
        }

        return hit;
    }

    public static boolean isHitLocationConsistentWithPos(BlockPos pos, Vec3 hit) {
        Vec3 delta = hit.subtract(Vec3.atCenterOf(pos));
        return Math.abs(delta.x) < HIT_BLOCK_CONSISTENCY_LIMIT
                && Math.abs(delta.y) < HIT_BLOCK_CONSISTENCY_LIMIT
                && Math.abs(delta.z) < HIT_BLOCK_CONSISTENCY_LIMIT;
    }
}
