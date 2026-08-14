package com.proventure.twistermill.util;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public final class SableSubLevelBlockCounter {

    private SableSubLevelBlockCounter() {
    }

    @SuppressWarnings("unused")
    public static int countBlocksMatchingTag(ServerLevel serverLevel, UUID subLevelId, TagKey<Block> tag) {
        return countBlocksMatching(serverLevel, subLevelId, state -> state.is(tag));
    }

    public static int countBlocksMatching(ServerLevel serverLevel, UUID subLevelId, Predicate<BlockState> predicate) {
        var container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return 0;
        }

        SubLevel resolved = container.getSubLevel(subLevelId);
        if (!(resolved instanceof ServerSubLevel serverSubLevel) || serverSubLevel.isRemoved()) {
            return 0;
        }

        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosedStream(serverSubLevel.getPlot().getBoundingBox().toMojang()).map(BlockPos::immutable).toList()) {
            if (predicate.test(serverLevel.getBlockState(pos))) {
                count++;
            }
        }

        return count;
    }
}
