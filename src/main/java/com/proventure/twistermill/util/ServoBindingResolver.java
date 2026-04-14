package com.proventure.twistermill.util;

import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;

public final class ServoBindingResolver {

    public record ResolvedServoSample(
            boolean found,
            @Nullable BlockPos resolvedPos,
            boolean inverted,
            float normalizedAngle,
            int contraptionBlockCount
    ) {
    }

    private static final int SEARCH_RADIUS = 1;

    private ServoBindingResolver() {
    }

    public static ResolvedServoSample resolve(Level level,
                                              BlockPos windRotoLocalPos,
                                              BlockPos windRotoWorldPos,
                                              BlockPos storedServoLocalPos,
                                              BlockPos relativeOffset,
                                              @Nullable BlockPos preferredResolvedPos,
                                              boolean inverted) {
        if (level == null || storedServoLocalPos == null || relativeOffset == null) {
            return new ResolvedServoSample(false, null, inverted, 0.0F, 0);
        }

        LinkedHashSet<BlockPos> exactCandidates = new LinkedHashSet<>();

        if (preferredResolvedPos != null) {
            exactCandidates.add(preferredResolvedPos);
        }

        exactCandidates.add(storedServoLocalPos);
        exactCandidates.add(windRotoLocalPos.offset(relativeOffset));
        exactCandidates.add(windRotoWorldPos.offset(relativeOffset));

        BlockPos transformedStored = tryTransformLocalToWorld(level, storedServoLocalPos);
        if (transformedStored != null) {
            exactCandidates.add(transformedStored);
        }

        BlockPos transformedRelative = tryTransformLocalToWorld(level, windRotoLocalPos.offset(relativeOffset));
        if (transformedRelative != null) {
            exactCandidates.add(transformedRelative);
        }

        BlockPos translatedFromWindRoto = storedServoLocalPos.offset(
                windRotoWorldPos.getX() - windRotoLocalPos.getX(),
                windRotoWorldPos.getY() - windRotoLocalPos.getY(),
                windRotoWorldPos.getZ() - windRotoLocalPos.getZ()
        );
        exactCandidates.add(translatedFromWindRoto);

        for (BlockPos candidate : exactCandidates) {
            ResolvedServoSample exact = findExact(level, candidate, inverted);
            if (exact.found()) {
                return exact;
            }
        }

        for (BlockPos candidate : exactCandidates) {
            ResolvedServoSample near = findNear(level, candidate, inverted);
            if (near.found()) {
                return near;
            }
        }

        return new ResolvedServoSample(false, null, inverted, 0.0F, 0);
    }

    private static ResolvedServoSample findExact(Level level, @Nullable BlockPos pos, boolean inverted) {
        if (pos == null) {
            return new ResolvedServoSample(false, null, inverted, 0.0F, 0);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!inverted && be instanceof ServoTwisterBlockEntity servo) {
            return new ResolvedServoSample(
                    true,
                    pos,
                    false,
                    servo.getWindRotoBindingAngleDegrees(),
                    servo.getBoundContraptionBlockCount()
            );
        }

        if (inverted && be instanceof InvServoTwisterBlockEntity invServo) {
            return new ResolvedServoSample(
                    true,
                    pos,
                    true,
                    invServo.getWindRotoBindingAngleDegrees(),
                    invServo.getBoundContraptionBlockCount()
            );
        }

        return new ResolvedServoSample(false, null, inverted, 0.0F, 0);
    }

    private static ResolvedServoSample findNear(Level level, @Nullable BlockPos origin, boolean inverted) {
        if (origin == null) {
            return new ResolvedServoSample(false, null, inverted, 0.0F, 0);
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    mutable.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    ResolvedServoSample sample = findExact(level, mutable, inverted);
                    if (sample.found()) {
                        return sample;
                    }
                }
            }
        }

        return new ResolvedServoSample(false, null, inverted, 0.0F, 0);
    }

    @Nullable
    private static BlockPos tryTransformLocalToWorld(Level level, BlockPos localPos) {
        if (level == null || localPos == null) {
            return null;
        }

        return WindRotoReflectionHelper.getWorldBlockPos(level, localPos);
    }
}