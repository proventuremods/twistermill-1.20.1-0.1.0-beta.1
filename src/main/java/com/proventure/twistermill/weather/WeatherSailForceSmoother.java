package com.proventure.twistermill.weather;

import com.proventure.twistermill.config.TwisterMillConfig;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider.LiftProviderContext;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WeatherSailForceSmoother {

    private static final double BASE_TIME_CONSTANT_SECONDS = 0.10D;
    private static final double MINIMUM_STRENGTH = 0.1D;
    private static final double MAXIMUM_STRENGTH = 10.0D;
    private static final double FORCE_EPSILON_SQUARED = 1.0E-18D;
    private static final int STALE_STATE_TICKS = 40;

    private static final Map<ContributionKey, SmoothingState> STATES = new HashMap<>();

    private static MinecraftServer activeServer;
    private static boolean smoothingEnabled;

    private WeatherSailForceSmoother() {
    }

    public static void updateEnabledState(ServerSubLevel subLevel, boolean enabled) {
        if (subLevel == null || subLevel.getLevel() == null) {
            return;
        }

        synchronizeEnabledState(subLevel.getLevel().getServer(), enabled);
    }

    public static boolean smooth(
            LiftProviderContext context,
            ServerSubLevel subLevel,
            boolean kinematicContraption,
            Vector3dc targetForceWorld,
            double timeStepSeconds,
            double strength,
            Vector3d destination
    ) {
        if (context == null
                || subLevel == null
                || subLevel.isRemoved()
                || subLevel.getLevel() == null
                || targetForceWorld == null
                || destination == null
                || !isFinite(targetForceWorld)
                || !Double.isFinite(timeStepSeconds)
                || timeStepSeconds <= 0.0D
                || !Double.isFinite(strength)) {
            discardContribution(context, subLevel, kinematicContraption);
            return false;
        }

        MinecraftServer server = subLevel.getLevel().getServer();
        ensureServer(server);
        if (!smoothingEnabled) {
            return false;
        }

        double clampedStrength = Math.max(MINIMUM_STRENGTH, Math.min(MAXIMUM_STRENGTH, strength));
        double timeConstantSeconds = BASE_TIME_CONSTANT_SECONDS * clampedStrength;
        double alpha = 1.0D - Math.exp(-timeStepSeconds / timeConstantSeconds);
        if (!Double.isFinite(alpha)) {
            discardContribution(context, subLevel, kinematicContraption);
            return false;
        }
        alpha = Math.max(0.0D, Math.min(1.0D, alpha));

        ContributionKey key = createKey(context, subLevel, kinematicContraption);
        int serverTick = server.getTickCount();
        SmoothingState state = STATES.get(key);
        if (state == null) {
            state = new SmoothingState(targetForceWorld, serverTick);
            STATES.put(key, state);
        } else {
            state.smoothedForce.lerp(targetForceWorld, alpha);
            state.lastSeenServerTick = serverTick;
        }

        double lengthSquared = state.smoothedForce.lengthSquared();
        if (!Double.isFinite(lengthSquared)) {
            STATES.remove(key);
            return false;
        }
        if (lengthSquared <= FORCE_EPSILON_SQUARED) {
            state.smoothedForce.zero();
        }

        destination.set(state.smoothedForce);
        return true;
    }

    public static void discardContribution(
            LiftProviderContext context,
            ServerSubLevel subLevel,
            boolean kinematicContraption
    ) {
        if (context == null || subLevel == null || subLevel.getLevel() == null) {
            return;
        }

        ensureServer(subLevel.getLevel().getServer());
        STATES.remove(createKey(context, subLevel, kinematicContraption));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        synchronizeEnabledState(
                server,
                TwisterMillConfig.ENABLE_SAIL_WIND_FORCE.get()
                        && TwisterMillConfig.isSailForceSmoothingEnabled()
        );
        int serverTick = server.getTickCount();
        STATES.entrySet().removeIf(
                entry -> serverTick - entry.getValue().lastSeenServerTick > STALE_STATE_TICKS
        );
    }

    private static ContributionKey createKey(
            LiftProviderContext context,
            ServerSubLevel subLevel,
            boolean kinematicContraption
    ) {
        return new ContributionKey(
                subLevel.getLevel(),
                subLevel.getUniqueId(),
                context.pos().asLong(),
                context,
                kinematicContraption
        );
    }

    private static void ensureServer(MinecraftServer server) {
        if (activeServer == server) {
            return;
        }

        activeServer = server;
        STATES.clear();
        smoothingEnabled = false;
    }

    private static void synchronizeEnabledState(MinecraftServer server, boolean enabled) {
        ensureServer(server);
        if (smoothingEnabled == enabled) {
            return;
        }

        STATES.clear();
        smoothingEnabled = enabled;
    }

    private static boolean isFinite(Vector3dc vector) {
        return Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private static final class ContributionKey {
        private final ServerLevel level;
        private final UUID subLevelId;
        private final long blockPosition;
        private final LiftProviderContext context;
        private final boolean kinematicContraption;

        private ContributionKey(
                ServerLevel level,
                UUID subLevelId,
                long blockPosition,
                LiftProviderContext context,
                boolean kinematicContraption
        ) {
            this.level = level;
            this.subLevelId = subLevelId;
            this.blockPosition = blockPosition;
            this.context = context;
            this.kinematicContraption = kinematicContraption;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ContributionKey key)) {
                return false;
            }
            return level == key.level
                    && subLevelId.equals(key.subLevelId)
                    && blockPosition == key.blockPosition
                    && context == key.context
                    && kinematicContraption == key.kinematicContraption;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(level);
            result = 31 * result + subLevelId.hashCode();
            result = 31 * result + Long.hashCode(blockPosition);
            result = 31 * result + System.identityHashCode(context);
            result = 31 * result + Boolean.hashCode(kinematicContraption);
            return result;
        }
    }

    private static final class SmoothingState {
        private final Vector3d smoothedForce = new Vector3d();
        private int lastSeenServerTick;

        private SmoothingState(Vector3dc initialForce, int serverTick) {
            smoothedForce.set(initialForce);
            lastSeenServerTick = serverTick;
        }
    }
}
