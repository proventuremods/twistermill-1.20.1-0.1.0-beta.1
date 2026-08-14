package com.proventure.twistermill.weather;

import com.proventure.twistermill.config.TwisterMillConfig;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider.LiftProviderContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

public final class WeatherSailForceSnapshotServer {

    private static final int SYNC_INTERVAL_TICKS = 2;
    private static final int ACTIVE_SNAPSHOT_TICKS = 2;
    private static final int STALE_STATE_TICKS = 40;
    private static final int MAX_SAILS_PER_PLAYER = 128;
    private static final double MAX_VISIBLE_DISTANCE_SQUARED = 64.0D * 64.0D;

    private static final Map<SailKey, SnapshotState> SNAPSHOTS = new HashMap<>();
    private static final IdentityHashMap<LiftProviderContext, ContextTokenState> CONTEXT_TOKENS =
            new IdentityHashMap<>();
    private static final Map<UUID, SubscriptionState> SUBSCRIPTIONS = new HashMap<>();
    private static final Map<UUID, PlayerSyncState> PLAYER_SYNC_STATES = new HashMap<>();

    private static MinecraftServer activeServer;
    private static long nextContextToken = 1L;
    private static long nextSailId = 1L;

    private WeatherSailForceSnapshotServer() {
    }

    public static void recordAppliedForce(
            LiftProviderContext context,
            ServerSubLevel subLevel,
            boolean kinematicContraption,
            Vector3dc localCenter,
            Vector3dc localThicknessAxis,
            Vector3dc worldCenter,
            Vector3dc windDirection,
            double referenceForceMagnitude,
            Vector3dc appliedForce
    ) {
        if (subLevel == null
                || subLevel.isRemoved()
                || subLevel.getLevel() == null) {
            return;
        }

        MinecraftServer server = subLevel.getLevel().getServer();
        ensureServer(server);
        if (SUBSCRIPTIONS.isEmpty()) {
            return;
        }

        if (context == null
                || !isFinite(localCenter)
                || !isFinite(localThicknessAxis)
                || !isFinite(worldCenter)
                || !isFinite(windDirection)
                || !isFinite(appliedForce)
                || !Double.isFinite(referenceForceMagnitude)
                || referenceForceMagnitude < 0.0D
                || localThicknessAxis.lengthSquared() <= 1.0E-12D
                || appliedForce.lengthSquared() <= 1.0E-12D) {
            return;
        }

        int serverTick = server.getTickCount();
        long contextToken = kinematicContraption
                ? getOrCreateContextToken(context, serverTick)
                : 0L;

        SailKey key = new SailKey(
                subLevel.getUniqueId(),
                context.pos().asLong(),
                contextToken,
                kinematicContraption
        );
        SnapshotState state = SNAPSHOTS.get(key);
        if (state == null) {
            state = new SnapshotState(nextSailId++);
            SNAPSHOTS.put(key, state);
        }

        state.dimension = subLevel.getLevel().dimension().location();
        state.subLevelId = subLevel.getUniqueId();
        state.localCenter.set(localCenter);
        state.localThicknessAxis.set(localThicknessAxis).normalize();
        state.worldCenter.set(worldCenter);
        // Points toward the wind source; its length is the capped unpitched reference force.
        state.incomingWind.set(windDirection).mul(-referenceForceMagnitude);
        state.appliedForce.set(appliedForce);
        state.lastSeenServerTick = serverTick;
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ensureServer(server);
        pruneOfflineSubscribers(server);
        if (SUBSCRIPTIONS.isEmpty()) {
            clearSnapshotState();
            return;
        }

        int serverTick = server.getTickCount();
        cleanupStaleState(serverTick);

        if (serverTick % SYNC_INTERVAL_TICKS != 0) {
            return;
        }

        boolean windForceEnabled = TwisterMillConfig.ENABLE_SAIL_WIND_FORCE.get();
        float maximumForcePerBlock = Math.max(
                0.0F,
                TwisterMillConfig.SAIL_WIND_MAX_FORCE_PER_BLOCK.get().floatValue()
        );

        for (Map.Entry<UUID, SubscriptionState> entry : SUBSCRIPTIONS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                syncPlayer(player, entry.getValue(), serverTick, windForceEnabled, maximumForcePerBlock);
            }
        }
    }

    public static void updateSubscription(ServerPlayer player, boolean enabled, long subscriptionEpoch) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ensureServer(server);

        UUID playerId = player.getUUID();
        SubscriptionState existing = SUBSCRIPTIONS.get(playerId);
        if (existing != null && subscriptionEpoch < existing.epoch) {
            return;
        }

        if (!enabled) {
            removeSubscription(playerId);
            return;
        }
        if (existing != null && subscriptionEpoch == existing.epoch) {
            return;
        }

        SUBSCRIPTIONS.put(
                playerId,
                new SubscriptionState(subscriptionEpoch, server.getTickCount() + 1)
        );
        PLAYER_SYNC_STATES.remove(playerId);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            removeSubscription(player.getUUID());
        }
    }

    private static void syncPlayer(
            ServerPlayer player,
            SubscriptionState subscription,
            int serverTick,
            boolean windForceEnabled,
            float maximumForcePerBlock
    ) {
        ResourceLocation dimension = player.serverLevel().dimension().location();
        PlayerSyncState syncState = PLAYER_SYNC_STATES.computeIfAbsent(
                player.getUUID(),
                ignored -> new PlayerSyncState(dimension)
        );
        if (!dimension.equals(syncState.dimension)) {
            syncState.dimension = dimension;
            syncState.generation = 0L;
            syncState.hadEntries = false;
        }

        List<SnapshotState> nearest = windForceEnabled
                ? collectNearestSnapshots(player, dimension, serverTick, subscription.earliestSampleTick)
                : List.of();

        if (nearest.isEmpty()) {
            if (syncState.hadEntries) {
                long generation = ++syncState.generation;
                PacketDistributor.sendToPlayer(
                        player,
                        new WeatherSailForceSnapshotPayload(
                                subscription.epoch,
                                dimension,
                                generation,
                                0,
                                1,
                                maximumForcePerBlock,
                                List.of()
                        )
                );
                syncState.hadEntries = false;
            }
            return;
        }

        long generation = ++syncState.generation;
        int partCount = (nearest.size() + WeatherSailForceSnapshotPayload.MAX_ENTRIES_PER_PART - 1)
                / WeatherSailForceSnapshotPayload.MAX_ENTRIES_PER_PART;
        for (int partIndex = 0; partIndex < partCount; partIndex++) {
            int fromIndex = partIndex * WeatherSailForceSnapshotPayload.MAX_ENTRIES_PER_PART;
            int toIndex = Math.min(
                    nearest.size(),
                    fromIndex + WeatherSailForceSnapshotPayload.MAX_ENTRIES_PER_PART
            );
            List<WeatherSailForceSnapshotPayload.Entry> entries = new ArrayList<>(toIndex - fromIndex);
            for (int index = fromIndex; index < toIndex; index++) {
                entries.add(nearest.get(index).toPayloadEntry());
            }

            PacketDistributor.sendToPlayer(
                    player,
                    new WeatherSailForceSnapshotPayload(
                            subscription.epoch,
                            dimension,
                            generation,
                            partIndex,
                            partCount,
                            maximumForcePerBlock,
                            entries
                    )
            );
        }
        syncState.hadEntries = true;
    }

    private static List<SnapshotState> collectNearestSnapshots(
            ServerPlayer player,
            ResourceLocation dimension,
            int serverTick,
            int earliestSampleTick
    ) {
        PriorityQueue<Candidate> nearest = new PriorityQueue<>(MAX_SAILS_PER_PLAYER, Candidate.WORST_FIRST);
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();

        for (SnapshotState state : SNAPSHOTS.values()) {
            if (!dimension.equals(state.dimension)
                    || state.lastSeenServerTick < earliestSampleTick
                    || serverTick - state.lastSeenServerTick > ACTIVE_SNAPSHOT_TICKS) {
                continue;
            }

            double deltaX = state.worldCenter.x - playerX;
            double deltaY = state.worldCenter.y - playerY;
            double deltaZ = state.worldCenter.z - playerZ;
            double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
            if (!Double.isFinite(distanceSquared) || distanceSquared > MAX_VISIBLE_DISTANCE_SQUARED) {
                continue;
            }

            Candidate candidate = new Candidate(state, distanceSquared);
            if (nearest.size() < MAX_SAILS_PER_PLAYER) {
                nearest.add(candidate);
            } else if (candidate.isBetterThan(nearest.peek())) {
                nearest.poll();
                nearest.add(candidate);
            }
        }

        List<Candidate> sortedCandidates = new ArrayList<>(nearest);
        sortedCandidates.sort(Candidate.BEST_FIRST);
        List<SnapshotState> result = new ArrayList<>(sortedCandidates.size());
        for (Candidate candidate : sortedCandidates) {
            result.add(candidate.state);
        }
        return result;
    }

    private static long getOrCreateContextToken(LiftProviderContext context, int serverTick) {
        ContextTokenState tokenState = CONTEXT_TOKENS.get(context);
        if (tokenState == null) {
            tokenState = new ContextTokenState(nextContextToken++);
            CONTEXT_TOKENS.put(context, tokenState);
        }
        tokenState.lastSeenServerTick = serverTick;
        return tokenState.token;
    }

    private static void cleanupStaleState(int serverTick) {
        SNAPSHOTS.entrySet().removeIf(
                entry -> serverTick - entry.getValue().lastSeenServerTick > STALE_STATE_TICKS
        );

        Iterator<Map.Entry<LiftProviderContext, ContextTokenState>> iterator =
                CONTEXT_TOKENS.entrySet().iterator();
        while (iterator.hasNext()) {
            ContextTokenState state = iterator.next().getValue();
            if (serverTick - state.lastSeenServerTick > STALE_STATE_TICKS) {
                iterator.remove();
            }
        }
    }

    private static void pruneOfflineSubscribers(MinecraftServer server) {
        Iterator<UUID> iterator = SUBSCRIPTIONS.keySet().iterator();
        while (iterator.hasNext()) {
            UUID playerId = iterator.next();
            if (server.getPlayerList().getPlayer(playerId) == null) {
                iterator.remove();
                PLAYER_SYNC_STATES.remove(playerId);
            }
        }
        if (SUBSCRIPTIONS.isEmpty()) {
            clearSnapshotState();
        }
    }

    private static void removeSubscription(UUID playerId) {
        SUBSCRIPTIONS.remove(playerId);
        PLAYER_SYNC_STATES.remove(playerId);
        if (SUBSCRIPTIONS.isEmpty()) {
            clearSnapshotState();
        }
    }

    private static void clearSnapshotState() {
        SNAPSHOTS.clear();
        CONTEXT_TOKENS.clear();
    }

    private static void ensureServer(MinecraftServer server) {
        if (activeServer == server) {
            return;
        }
        activeServer = server;
        clearSnapshotState();
        SUBSCRIPTIONS.clear();
        PLAYER_SYNC_STATES.clear();
        nextContextToken = 1L;
        nextSailId = 1L;
    }

    private static boolean isFinite(Vector3dc vector) {
        return Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private record SailKey(UUID subLevelId, long blockPosition, long contextToken, boolean kinematicContraption) {
    }

    private static final class ContextTokenState {
        private final long token;
        private int lastSeenServerTick;

        private ContextTokenState(long token) {
            this.token = token;
        }
    }

    private static final class PlayerSyncState {
        private ResourceLocation dimension;
        private long generation;
        private boolean hadEntries;

        private PlayerSyncState(ResourceLocation dimension) {
            this.dimension = dimension;
        }
    }

    private record SubscriptionState(long epoch, int earliestSampleTick) {
    }

    private static final class SnapshotState {
        private final long sailId;
        private final Vector3d localCenter = new Vector3d();
        private final Vector3d localThicknessAxis = new Vector3d();
        private final Vector3d worldCenter = new Vector3d();
        private final Vector3d incomingWind = new Vector3d();
        private final Vector3d appliedForce = new Vector3d();
        private ResourceLocation dimension;
        private UUID subLevelId;
        private int lastSeenServerTick;

        private SnapshotState(long sailId) {
            this.sailId = sailId;
        }

        private WeatherSailForceSnapshotPayload.Entry toPayloadEntry() {
            return new WeatherSailForceSnapshotPayload.Entry(
                    subLevelId,
                    sailId,
                    localCenter.x,
                    localCenter.y,
                    localCenter.z,
                    (float) localThicknessAxis.x,
                    (float) localThicknessAxis.y,
                    (float) localThicknessAxis.z,
                    (float) incomingWind.x,
                    (float) incomingWind.y,
                    (float) incomingWind.z,
                    (float) appliedForce.x,
                    (float) appliedForce.y,
                    (float) appliedForce.z
            );
        }
    }

    private record Candidate(SnapshotState state, double distanceSquared) {
        private static final Comparator<Candidate> WORST_FIRST = (first, second) -> {
            int distanceComparison = Double.compare(second.distanceSquared, first.distanceSquared);
            if (distanceComparison != 0) {
                return distanceComparison;
            }
            return Long.compare(second.state.sailId, first.state.sailId);
        };

        private static final Comparator<Candidate> BEST_FIRST = (first, second) -> {
            int distanceComparison = Double.compare(first.distanceSquared, second.distanceSquared);
            if (distanceComparison != 0) {
                return distanceComparison;
            }
            return Long.compare(first.state.sailId, second.state.sailId);
        };

        private boolean isBetterThan(Candidate other) {
            return BEST_FIRST.compare(this, other) < 0;
        }
    }
}
