package com.proventure.twistermill.diagnostics;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class TwisterMillAutoReseatEvents {

    private static final int CHUNK_LOAD_DELAY_TICKS = 2;
    private static final int LEVEL_SWEEP_DELAY_TICKS = 20;
    private static final int AUTO_RESEAT_THROTTLE_TICKS = 40;
    private static final int MAX_CHUNK_SCANS_PER_TICK = 32;

    private static final Map<ResourceKey<Level>, Set<Long>> LOADED_CHUNKS = new ConcurrentHashMap<>();
    private static final Queue<QueuedWork> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<WorkKey> QUEUED = ConcurrentHashMap.newKeySet();
    private static final Map<TargetKey, Long> LAST_AUTO_RESEAT_TICK = new ConcurrentHashMap<>();

    private TwisterMillAutoReseatEvents() {
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            queueLevelSweep(serverLevel, TwisterMillReseatService.Trigger.AUTO_LEVEL_LOAD);
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            LOADED_CHUNKS.remove(serverLevel.dimension());
            LAST_AUTO_RESEAT_TICK.keySet().removeIf(key -> key.dimension().equals(serverLevel.dimension()));
            QUEUED.removeIf(key -> key.dimension().equals(serverLevel.dimension()));
        }
    }

    public static void onServerStarted(ServerStartedEvent event) {
        queueAllLevelSweeps(event.getServer(), TwisterMillReseatService.Trigger.AUTO_SERVER_STARTED);
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            queueAllLevelSweeps(player.getServer(), TwisterMillReseatService.Trigger.AUTO_PLAYER_JOIN);
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ChunkPos chunkPos = event.getChunk().getPos();
        LOADED_CHUNKS
                .computeIfAbsent(serverLevel.dimension(), key -> ConcurrentHashMap.newKeySet())
                .add(chunkPos.toLong());
        queueChunkScan(serverLevel, chunkPos.toLong(), TwisterMillReseatService.Trigger.AUTO_CHUNK_LOAD, CHUNK_LOAD_DELAY_TICKS);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            LOADED_CHUNKS
                    .computeIfAbsent(serverLevel.dimension(), key -> ConcurrentHashMap.newKeySet())
                    .remove(event.getChunk().getPos().toLong());
        }
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int currentTick = server.getTickCount();
        int processed = 0;
        int queuedAtStart = QUEUE.size();

        while (queuedAtStart-- > 0 && processed < MAX_CHUNK_SCANS_PER_TICK) {
            QueuedWork work = QUEUE.poll();
            if (work == null) {
                return;
            }

            if (work.dueTick() > currentTick) {
                QUEUE.add(work);
                continue;
            }

            QUEUED.remove(work.key());
            if (!TwisterMillReseatService.anyAutoReseatOnLoadEnabled()) {
                continue;
            }

            if (work.chunkSweep()) {
                processChunkScan(server, work);
                processed++;
            } else {
                processLevelSweep(server, work);
            }
        }
    }

    private static void queueAllLevelSweeps(
            MinecraftServer server,
            TwisterMillReseatService.Trigger trigger
    ) {
        if (!TwisterMillReseatService.anyAutoReseatOnLoadEnabled()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            queueLevelSweep(level, trigger);
        }
    }

    private static void queueLevelSweep(
            ServerLevel level,
            TwisterMillReseatService.Trigger trigger
    ) {
        if (!TwisterMillReseatService.anyAutoReseatOnLoadEnabled()) {
            return;
        }
        int dueTick = level.getServer().getTickCount() + LEVEL_SWEEP_DELAY_TICKS;
        WorkKey key = WorkKey.level(level.dimension(), trigger);
        if (QUEUED.add(key)) {
            QUEUE.add(QueuedWork.levelSweep(key, dueTick, trigger));
        }
    }

    private static void queueChunkScan(
            ServerLevel level,
            long chunkPos,
            TwisterMillReseatService.Trigger trigger,
            int delayTicks
    ) {
        if (!TwisterMillReseatService.anyAutoReseatOnLoadEnabled()) {
            return;
        }
        int dueTick = level.getServer().getTickCount() + delayTicks;
        WorkKey key = WorkKey.chunk(level.dimension(), chunkPos, trigger);
        if (QUEUED.add(key)) {
            QUEUE.add(QueuedWork.chunkScan(key, dueTick, trigger));
        }
    }

    private static void processLevelSweep(MinecraftServer server, QueuedWork work) {
        ServerLevel level = server.getLevel(work.key().dimension());
        if (level == null) {
            return;
        }
        Set<Long> chunks = LOADED_CHUNKS.get(level.dimension());
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (long chunkPos : new ArrayList<>(chunks)) {
            queueChunkScan(level, chunkPos, work.trigger(), 0);
        }
    }

    private static void processChunkScan(MinecraftServer server, QueuedWork work) {
        ServerLevel level = server.getLevel(work.key().dimension());
        if (level == null) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(work.key().chunkPos());
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return;
        }

        for (BlockEntity blockEntity : new ArrayList<>(chunk.getBlockEntities().values())) {
            TwisterMillReseatService.TargetType targetType = TwisterMillReseatService.identify(blockEntity);
            if (targetType == null || !TwisterMillReseatService.isAutoEnabled(targetType)) {
                continue;
            }

            TargetKey targetKey = new TargetKey(level.dimension(), blockEntity.getBlockPos().immutable(), targetType);
            long lastTick = LAST_AUTO_RESEAT_TICK.getOrDefault(targetKey, Long.MIN_VALUE);
            if (lastTick != Long.MIN_VALUE && level.getServer().getTickCount() - lastTick < AUTO_RESEAT_THROTTLE_TICKS) {
                continue;
            }

            LAST_AUTO_RESEAT_TICK.put(targetKey, (long) level.getServer().getTickCount());
            TwisterMillReseatService.reseatAuto(blockEntity, work.trigger());
        }
    }

    private record QueuedWork(WorkKey key, int dueTick, TwisterMillReseatService.Trigger trigger) {
        static QueuedWork levelSweep(WorkKey key, int dueTick, TwisterMillReseatService.Trigger trigger) {
            return new QueuedWork(key, dueTick, trigger);
        }

        static QueuedWork chunkScan(WorkKey key, int dueTick, TwisterMillReseatService.Trigger trigger) {
            return new QueuedWork(key, dueTick, trigger);
        }

        boolean chunkSweep() {
            return key.chunkPos() != ChunkPos.INVALID_CHUNK_POS;
        }
    }

    private record WorkKey(ResourceKey<Level> dimension, long chunkPos, TwisterMillReseatService.Trigger trigger) {
        static WorkKey level(ResourceKey<Level> dimension, TwisterMillReseatService.Trigger trigger) {
            return new WorkKey(dimension, ChunkPos.INVALID_CHUNK_POS, trigger);
        }

        static WorkKey chunk(ResourceKey<Level> dimension, long chunkPos, TwisterMillReseatService.Trigger trigger) {
            return new WorkKey(dimension, chunkPos, trigger);
        }
    }

    private record TargetKey(ResourceKey<Level> dimension, net.minecraft.core.BlockPos pos, TwisterMillReseatService.TargetType targetType) {
    }
}
