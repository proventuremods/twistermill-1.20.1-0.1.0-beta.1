package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

final class RememberedSableShipMemory {
    private static final String TAG_ROOT = "RememberedSableShip";
    private static final String TAG_VERSION = "Version";
    private static final String TAG_RELATIVE_BLOCKS = "RelativeBlocks";
    private static final int VERSION = 1;

    private final LinkedHashSet<BlockPos> relativeBlocks = new LinkedHashSet<>();

    @Nullable
    static RememberedSableShipMemory enabledFor(BlockState ownerState, RememberedSableShipMemory memory) {
        return memory != null && isRememberContraptionEnabledFor(ownerState) ? memory : null;
    }

    static boolean isRememberContraptionEnabledFor(BlockState ownerState) {
        if (ownerState == null) {
            return true;
        }
        if (ownerState.is(ModBlocks.WIND_ROTO_BLOCK.get())) {
            return TwisterMillConfig.isWindRotoContraptionMemoryEnabled();
        }
        if (ownerState.is(ModBlocks.WIND_ROTO_VERTICAL_BLOCK.get())) {
            return TwisterMillConfig.isWindRotoVerticalContraptionMemoryEnabled();
        }
        if (ownerState.is(ModBlocks.SERVO_TWISTER_BLOCK.get())) {
            return TwisterMillConfig.isServoTwisterContraptionMemoryEnabled();
        }
        if (ownerState.is(ModBlocks.INV_SERVO_TWISTER_BLOCK.get())) {
            return TwisterMillConfig.isInvServoTwisterContraptionMemoryEnabled();
        }
        return true;
    }

    void replaceFromWorldPositions(BlockPos ownerPos, Direction assemblyDirection, Collection<BlockPos> worldPositions) {
        relativeBlocks.clear();
        if (ownerPos == null || assemblyDirection == null || worldPositions == null || worldPositions.isEmpty()) {
            return;
        }

        for (BlockPos worldPos : worldPositions) {
            if (worldPos == null || !isOnAssemblySide(ownerPos, worldPos, assemblyDirection)) {
                continue;
            }
            relativeBlocks.add(toRelative(ownerPos, worldPos));
        }
    }

    Set<BlockPos> collectAssemblyCandidates(ServerLevel level, BlockPos ownerPos, Direction assemblyDirection,
            Collection<BlockPos> baseCandidates) {
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
        if (level == null || ownerPos == null || assemblyDirection == null) {
            return candidates;
        }

        if (baseCandidates != null) {
            for (BlockPos baseCandidate : baseCandidates) {
                if (isRememberShipAllowedBlock(level, baseCandidate, ownerPos, assemblyDirection, false)) {
                    candidates.add(baseCandidate.immutable());
                }
            }
        }

        int maxBlocks = Math.max(1, AllConfigs.server().kinetics.maxBlocksMoved.get());
        LinkedHashSet<BlockPos> retainedRelative = new LinkedHashSet<>();
        for (BlockPos relative : relativeBlocks) {
            BlockPos worldPos = toWorld(ownerPos, relative);
            CandidateStatus status = classifyCandidate(level, worldPos, ownerPos, assemblyDirection, true);
            if (status == CandidateStatus.VALID) {
                retainedRelative.add(relative.immutable());
                if (candidates.size() < maxBlocks) {
                    candidates.add(worldPos);
                }
            } else if (status == CandidateStatus.KEEP_UNLOADED) {
                retainedRelative.add(relative.immutable());
            }
        }

        relativeBlocks.clear();
        relativeBlocks.addAll(retainedRelative);
        expandConnectedCandidates(level, ownerPos, assemblyDirection, candidates, maxBlocks);
        rememberLoadedCandidates(level, ownerPos, assemblyDirection, candidates);
        return candidates;
    }

    void write(CompoundTag tag) {
        CompoundTag remembered = new CompoundTag();
        remembered.putInt(TAG_VERSION, VERSION);
        long[] packed = new long[relativeBlocks.size()];
        int index = 0;
        for (BlockPos relative : relativeBlocks) {
            packed[index++] = relative.asLong();
        }
        remembered.putLongArray(TAG_RELATIVE_BLOCKS, packed);
        tag.put(TAG_ROOT, remembered);
    }

    void read(CompoundTag tag) {
        relativeBlocks.clear();
        if (!tag.contains(TAG_ROOT)) {
            return;
        }

        CompoundTag remembered = tag.getCompound(TAG_ROOT);
        long[] packed = remembered.getLongArray(TAG_RELATIVE_BLOCKS);
        for (long value : packed) {
            relativeBlocks.add(BlockPos.of(value).immutable());
        }
    }

    private void expandConnectedCandidates(ServerLevel level, BlockPos ownerPos, Direction assemblyDirection,
            LinkedHashSet<BlockPos> candidates, int maxBlocks) {
        if (candidates.isEmpty() || candidates.size() >= maxBlocks) {
            return;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>(candidates);
        LinkedHashSet<BlockPos> visited = new LinkedHashSet<>(candidates);
        while (!queue.isEmpty() && candidates.size() < maxBlocks) {
            BlockPos seed = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                if (candidates.size() >= maxBlocks) {
                    return;
                }

                BlockPos neighbor = seed.relative(direction).immutable();
                if (!visited.add(neighbor)) {
                    continue;
                }
                if (classifyCandidate(level, neighbor, ownerPos, assemblyDirection, true) != CandidateStatus.VALID) {
                    continue;
                }

                candidates.add(neighbor);
                queue.addLast(neighbor);
            }
        }
    }

    private void rememberLoadedCandidates(ServerLevel level, BlockPos ownerPos, Direction assemblyDirection,
            Collection<BlockPos> candidates) {
        for (BlockPos candidate : candidates) {
            if (candidate != null
                    && isRememberShipAllowedBlock(level, candidate, ownerPos, assemblyDirection, false)) {
                relativeBlocks.add(toRelative(ownerPos, candidate));
            }
        }
    }

    static boolean isRememberShipAllowedBlock(ServerLevel level, BlockPos pos, BlockPos ownerPos,
            Direction assemblyDirection, @SuppressWarnings("SameParameterValue") boolean requireMovementAllowed) {
        return classifyCandidate(level, pos, ownerPos, assemblyDirection, requireMovementAllowed) == CandidateStatus.VALID;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isRememberShipAllowedState(BlockState state) {
        return state != null
                && !state.isAir()
                && !state.canBeReplaced()
                && !state.is(Blocks.DIRT)
                && !state.is(Blocks.GRASS_BLOCK)
                && !state.is(Blocks.SAND);
    }

    private static CandidateStatus classifyCandidate(ServerLevel level, BlockPos pos, BlockPos ownerPos,
            Direction assemblyDirection, boolean requireMovementAllowed) {
        if (level == null || pos == null || ownerPos == null || assemblyDirection == null
                || !isOnAssemblySide(ownerPos, pos, assemblyDirection) || level.isOutsideBuildHeight(pos)) {
            return CandidateStatus.DROP;
        }
        if (!level.isLoaded(pos)) {
            return CandidateStatus.KEEP_UNLOADED;
        }

        BlockState state = level.getBlockState(pos);
        if (!isRememberShipAllowedState(state)) {
            return CandidateStatus.DROP;
        }
        if (requireMovementAllowed && !BlockMovementChecks.isMovementAllowed(state, level, pos)) {
            return CandidateStatus.DROP;
        }
        return CandidateStatus.VALID;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isOnAssemblySide(BlockPos ownerPos, BlockPos pos, Direction assemblyDirection) {
        return signedAssemblyDistance(ownerPos, pos, assemblyDirection) >= 1;
    }

    private static int signedAssemblyDistance(BlockPos ownerPos, BlockPos pos, Direction assemblyDirection) {
        return (pos.getX() - ownerPos.getX()) * assemblyDirection.getStepX()
                + (pos.getY() - ownerPos.getY()) * assemblyDirection.getStepY()
                + (pos.getZ() - ownerPos.getZ()) * assemblyDirection.getStepZ();
    }

    private static BlockPos toRelative(BlockPos ownerPos, BlockPos worldPos) {
        return new BlockPos(
                worldPos.getX() - ownerPos.getX(),
                worldPos.getY() - ownerPos.getY(),
                worldPos.getZ() - ownerPos.getZ()
        );
    }

    private static BlockPos toWorld(BlockPos ownerPos, BlockPos relative) {
        return new BlockPos(
                ownerPos.getX() + relative.getX(),
                ownerPos.getY() + relative.getY(),
                ownerPos.getZ() + relative.getZ()
        );
    }

    private enum CandidateStatus {
        VALID,
        KEEP_UNLOADED,
        DROP
    }
}
