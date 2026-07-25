package com.proventure.twistermill.blockentity;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

final class WindRotoChildShipSailCounter {
    private static final int MAX_CHILD_SHIP_DEPTH = 8;
    private static final int MAX_SCANNED_SUBLEVELS = 64;

    private WindRotoChildShipSailCounter() {
    }

    static int countSailLikeBlocksRecursive(ServerLevel rootLevel, UUID rootSubLevelId, Predicate<BlockState> sailLikePredicate) {
        return countBlocksRecursive(rootLevel, rootSubLevelId, sailLikePredicate).sailLikeBlocks();
    }

    static CountResult countBlocksRecursive(ServerLevel rootLevel, UUID rootSubLevelId, Predicate<BlockState> sailLikePredicate) {
        if (rootSubLevelId == null) {
            return CountResult.EMPTY;
        }
        List<UUID> roots = new ArrayList<>(1);
        roots.add(rootSubLevelId);
        return countBlocksRecursive(rootLevel, roots, sailLikePredicate);
    }

    static CountResult countBlocksRecursive(ServerLevel rootLevel, Collection<UUID> rootSubLevelIds, Predicate<BlockState> sailLikePredicate) {
        if (rootLevel == null || rootSubLevelIds == null || rootSubLevelIds.isEmpty() || sailLikePredicate == null) {
            return CountResult.EMPTY;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return CountResult.EMPTY;
        }

        Set<UUID> visited = new HashSet<>();
        ArrayDeque<ScanNode> queue = new ArrayDeque<>();
        for (UUID rootSubLevelId : rootSubLevelIds) {
            if (rootSubLevelId != null) {
                queue.add(new ScanNode(rootSubLevelId, 0));
            }
        }

        int scannedSubLevels = 0;
        int totalBlocks = 0;
        int sailLikeBlocks = 0;
        while (!queue.isEmpty() && scannedSubLevels < MAX_SCANNED_SUBLEVELS) {
            ScanNode node = queue.removeFirst();
            if (node.depth() > MAX_CHILD_SHIP_DEPTH || !visited.add(node.subLevelId())) {
                continue;
            }

            ServerSubLevel subLevel = resolveSubLevel(container, node.subLevelId());
            if (subLevel == null) {
                continue;
            }

            scannedSubLevels++;
            List<UUID> childSubLevelIds = new ArrayList<>();
            CountResult subLevelCounts = scanSubLevel(rootLevel, subLevel, sailLikePredicate, childSubLevelIds);
            totalBlocks += subLevelCounts.totalBlocks();
            sailLikeBlocks += subLevelCounts.sailLikeBlocks();

            if (node.depth() >= MAX_CHILD_SHIP_DEPTH) {
                continue;
            }

            for (UUID childSubLevelId : childSubLevelIds) {
                if (childSubLevelId == null || visited.contains(childSubLevelId)) {
                    continue;
                }
                if (scannedSubLevels + queue.size() >= MAX_SCANNED_SUBLEVELS) {
                    break;
                }
                queue.addLast(new ScanNode(childSubLevelId, node.depth() + 1));
            }
        }

        return new CountResult(totalBlocks, sailLikeBlocks);
    }

    private static ServerSubLevel resolveSubLevel(ServerSubLevelContainer container, UUID subLevelId) {
        SubLevel resolved = container.getSubLevel(subLevelId);
        if (!(resolved instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return null;
        }
        return serverSubLevel;
    }

    private static CountResult scanSubLevel(ServerLevel rootLevel, ServerSubLevel subLevel, Predicate<BlockState> sailLikePredicate, List<UUID> childSubLevelIds) {
        int totalBlocks = 0;
        int sailLikeBlocks = 0;
        BoundingBox3ic bounds = subLevel.getPlot().getBoundingBox();
        try (var positions = BlockPos.betweenClosedStream(bounds.toMojang())) {
            var iterator = positions.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next().immutable();
                BlockState state = rootLevel.getBlockState(pos);
                if (!state.isAir()) {
                    totalBlocks++;
                }
                if (sailLikePredicate.test(state)) {
                    sailLikeBlocks++;
                }

                BlockEntity blockEntity = rootLevel.getBlockEntity(pos);
                if (blockEntity != null && isBlockInSubLevel(rootLevel, subLevel, pos)) {
                    collectKnownAnchorChildSubLevels(blockEntity, childSubLevelIds);
                }
            }
        }
        return new CountResult(totalBlocks, sailLikeBlocks);
    }

    private static void collectKnownAnchorChildSubLevels(BlockEntity blockEntity, List<UUID> childSubLevelIds) {
        if (blockEntity instanceof ServoTwisterBlockEntity servo) {
            servo.collectChildSubLevelIdsForWindRotoSailCount(childSubLevelIds);
        } else if (blockEntity instanceof InvServoTwisterBlockEntity invServo) {
            invServo.collectChildSubLevelIdsForWindRotoSailCount(childSubLevelIds);
        } else if (blockEntity instanceof WindRotoBlockEntity windRoto) {
            windRoto.collectChildSubLevelIdsForWindRotoSailCount(childSubLevelIds);
        } else if (blockEntity instanceof WindRotoVerticalBlockEntity windRotoVertical) {
            windRotoVertical.collectChildSubLevelIdsForWindRotoSailCount(childSubLevelIds);
        }
    }

    private static boolean isBlockInSubLevel(ServerLevel rootLevel, ServerSubLevel expectedSubLevel, BlockPos pos) {
        try {
            SubLevel containing = Sable.HELPER.getContaining(rootLevel, pos);
            return containing instanceof ServerSubLevel serverSubLevel
                    && expectedSubLevel.getUniqueId().equals(serverSubLevel.getUniqueId());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record ScanNode(UUID subLevelId, int depth) {
    }

    record CountResult(int totalBlocks, int sailLikeBlocks) {
        static final CountResult EMPTY = new CountResult(0, 0);
    }
}
