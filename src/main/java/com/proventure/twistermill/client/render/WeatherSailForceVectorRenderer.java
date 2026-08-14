package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.weather.WeatherSailForceSnapshotPayload;
import com.proventure.twistermill.weather.WeatherSailForceSubscriptionPayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WeatherSailForceVectorRenderer {

    private static final int SNAPSHOT_INTERVAL_TICKS = 2;
    private static final int SNAPSHOT_EXPIRY_TICKS = 8;
    private static final int MAX_ENTRIES_PER_GENERATION = 128;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 64.0D * 64.0D;
    private static final double MAX_INTERPOLATION_DISTANCE_SQUARED = 16.0D * 16.0D;
    private static final double FORCE_EPSILON = 1.0E-9D;
    private static final double MIN_ARROW_LENGTH = 0.05D;
    private static final double MAX_REFERENCE_ARROW_LENGTH = 2.5D;
    private static final double MAX_ARROW_LENGTH = 3.0D;
    private static final double FRUSTUM_RADIUS = 3.25D;
    private static final double SAIL_MODEL_CENTER_OFFSET = -0.5D / 16.0D;
    private static final double SAIL_MODEL_HALF_THICKNESS = 2.5D / 16.0D;

    private static final int INCOMING_WIND_R = 0x55;
    private static final int INCOMING_WIND_G = 0xCC;
    private static final int INCOMING_WIND_B = 0xFF;
    private static final int GREEN_R = 0x35;
    private static final int GREEN_G = 0xFF;
    private static final int GREEN_B = 0x55;

    private static final Map<Long, ClientSailState> ACTIVE_SAILS = new HashMap<>();
    private static final RenderScratch SCRATCH = new RenderScratch();

    private static ClientLevel activeLevel;
    private static ResourceLocation activeDimension;
    private static PendingGeneration pendingGeneration;
    private static long activeGeneration = Long.MIN_VALUE;
    private static long lastCompleteClientTick = Long.MIN_VALUE;
    private static ClientPacketListener subscriptionConnection;
    private static long subscriptionEpoch;
    private static boolean subscriptionEnabled;

    private WeatherSailForceVectorRenderer() {
    }

    public static void acceptSnapshot(WeatherSailForceSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clearAll();
            return;
        }
        if (!TwisterMillConfig.isSailForceVectorsShown()) {
            clearSnapshotCache();
            return;
        }
        if (!subscriptionEnabled || payload.subscriptionEpoch() != subscriptionEpoch) {
            clearSnapshotCache();
            return;
        }

        ensureLevel(level);
        if (!payload.dimension().equals(activeDimension)
                || payload.generation() <= activeGeneration
                || !Float.isFinite(payload.maximumForcePerBlock())
                || payload.maximumForcePerBlock() < 0.0F) {
            return;
        }

        long clientTick = level.getGameTime();
        if (pendingGeneration == null || payload.generation() > pendingGeneration.generation) {
            pendingGeneration = new PendingGeneration(
                    payload.dimension(),
                    payload.generation(),
                    payload.partCount(),
                    payload.maximumForcePerBlock(),
                    clientTick
            );
        } else if (payload.generation() < pendingGeneration.generation) {
            return;
        }

        if (!pendingGeneration.accept(payload, clientTick)) {
            pendingGeneration = null;
            return;
        }
        if (!pendingGeneration.isComplete()) {
            return;
        }

        List<WeatherSailForceSnapshotPayload.Entry> entries = pendingGeneration.combine();
        if (entries == null || entries.size() > MAX_ENTRIES_PER_GENERATION) {
            pendingGeneration = null;
            return;
        }

        applyCompleteGeneration(
                pendingGeneration.generation,
                pendingGeneration.maximumForcePerBlock,
                entries,
                clientTick
        );
        pendingGeneration = null;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        syncSubscription(minecraft);
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            clearAll();
            return;
        }
        if (!TwisterMillConfig.isSailForceVectorsShown()) {
            clearSnapshotCache();
            return;
        }

        ensureLevel(level);
        long clientTick = level.getGameTime();
        if (pendingGeneration != null
                && clientTick - pendingGeneration.lastReceivedClientTick > SNAPSHOT_EXPIRY_TICKS) {
            pendingGeneration = null;
        }
        if (lastCompleteClientTick != Long.MIN_VALUE
                && clientTick - lastCompleteClientTick > SNAPSHOT_EXPIRY_TICKS) {
            ACTIVE_SAILS.clear();
            lastCompleteClientTick = Long.MIN_VALUE;
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!TwisterMillConfig.isSailForceVectorsShown()) {
            clearSnapshotCache();
            return;
        }
        if (ACTIVE_SAILS.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        ensureLevel(level);
        if (ACTIVE_SAILS.isEmpty()) {
            return;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double sampleTick = level.getGameTime() + partialTick;
        Vec3 cameraPosition = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());

        for (ClientSailState state : ACTIVE_SAILS.values()) {
            SubLevel subLevel = container.getSubLevel(state.subLevelId);
            if (subLevel == null || subLevel.isRemoved()) {
                continue;
            }

            state.sample(sampleTick, SCRATCH.localCenter, SCRATCH.incomingWind, SCRATCH.appliedForce);
            Pose3dc renderPose = resolveRenderPose(subLevel, partialTick, SCRATCH.fallbackPose);
            renderPose.transformPosition(SCRATCH.localCenter, SCRATCH.worldCenter);
            if (!isFinite(SCRATCH.worldCenter)) {
                continue;
            }

            double deltaX = SCRATCH.worldCenter.x - cameraPosition.x;
            double deltaY = SCRATCH.worldCenter.y - cameraPosition.y;
            double deltaZ = SCRATCH.worldCenter.z - cameraPosition.z;
            double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
            if (!Double.isFinite(distanceSquared) || distanceSquared > MAX_RENDER_DISTANCE_SQUARED) {
                continue;
            }

            AABB bounds = new AABB(
                    SCRATCH.worldCenter.x - FRUSTUM_RADIUS,
                    SCRATCH.worldCenter.y - FRUSTUM_RADIUS,
                    SCRATCH.worldCenter.z - FRUSTUM_RADIUS,
                    SCRATCH.worldCenter.x + FRUSTUM_RADIUS,
                    SCRATCH.worldCenter.y + FRUSTUM_RADIUS,
                    SCRATCH.worldCenter.z + FRUSTUM_RADIUS
            );
            if (!event.getFrustum().isVisible(bounds)) {
                continue;
            }

            double maximumForcePerBlock = state.maximumForcePerBlock;
            double incomingWindMagnitude = SCRATCH.incomingWind.length();
            double referenceForceMagnitude = incomingWindMagnitude;
            double appliedForceMagnitude = SCRATCH.appliedForce.length();
            if (!Double.isFinite(maximumForcePerBlock)
                    || maximumForcePerBlock <= FORCE_EPSILON
                    || !Double.isFinite(incomingWindMagnitude)
                    || !Double.isFinite(appliedForceMagnitude)) {
                continue;
            }

            renderPose.transformNormal(state.localThicknessAxis, SCRATCH.worldThicknessAxis);
            double thicknessAxisLengthSquared = SCRATCH.worldThicknessAxis.lengthSquared();
            if (!Double.isFinite(thicknessAxisLengthSquared)
                    || thicknessAxisLengthSquared <= FORCE_EPSILON * FORCE_EPSILON) {
                continue;
            }
            SCRATCH.worldThicknessAxis.div(Math.sqrt(thicknessAxisLengthSquared));
            SCRATCH.positiveSurface.set(SCRATCH.worldCenter).fma(
                    SAIL_MODEL_CENTER_OFFSET + SAIL_MODEL_HALF_THICKNESS,
                    SCRATCH.worldThicknessAxis
            );
            SCRATCH.negativeSurface.set(SCRATCH.worldCenter).fma(
                    SAIL_MODEL_CENTER_OFFSET - SAIL_MODEL_HALF_THICKNESS,
                    SCRATCH.worldThicknessAxis
            );

            double redLength = 0.0D;
            if (incomingWindMagnitude > FORCE_EPSILON) {
                double referenceRatio = Math.max(
                        0.0D,
                        Math.min(1.0D, referenceForceMagnitude / maximumForcePerBlock)
                );
                redLength = MAX_REFERENCE_ARROW_LENGTH * Math.sqrt(referenceRatio);
            }
            if (redLength >= MIN_ARROW_LENGTH) {
                SCRATCH.direction.set(SCRATCH.incomingWind).div(incomingWindMagnitude);
                double redOutwardDot = SCRATCH.direction.dot(SCRATCH.worldThicknessAxis);
                Vector3dc redAnchor = selectOutwardSurface(redOutwardDot, SCRATCH);
                SCRATCH.redOuter.set(redAnchor).fma(redLength, SCRATCH.direction);
                SCRATCH.reversedIncomingWind.set(SCRATCH.incomingWind).negate();
                renderArrow(
                        poseStack.last(),
                        lineBuffer,
                        SCRATCH.redOuter,
                        SCRATCH.reversedIncomingWind,
                        redLength,
                        INCOMING_WIND_R,
                        INCOMING_WIND_G,
                        INCOMING_WIND_B,
                        SCRATCH
                );
            }

            double greenLength;
            if (referenceForceMagnitude > FORCE_EPSILON) {
                double forceRatio = appliedForceMagnitude / referenceForceMagnitude;
                greenLength = Double.isFinite(forceRatio) && forceRatio >= 0.0D
                        ? Math.min(MAX_ARROW_LENGTH, redLength * forceRatio)
                        : 0.0D;
            } else {
                double appliedRatio = Math.max(
                        0.0D,
                        Math.min(1.0D, appliedForceMagnitude / maximumForcePerBlock)
                );
                greenLength = MAX_REFERENCE_ARROW_LENGTH * Math.sqrt(appliedRatio);
            }
            if (greenLength >= MIN_ARROW_LENGTH) {
                double greenOutwardDot = SCRATCH.appliedForce.dot(SCRATCH.worldThicknessAxis)
                        / appliedForceMagnitude;
                Vector3dc greenAnchor = selectOutwardSurface(greenOutwardDot, SCRATCH);
                renderArrow(
                        poseStack.last(),
                        lineBuffer,
                        greenAnchor,
                        SCRATCH.appliedForce,
                        greenLength,
                        GREEN_R,
                        GREEN_G,
                        GREEN_B,
                        SCRATCH
                );
            }
        }

        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void applyCompleteGeneration(
            long generation,
            float maximumForcePerBlock,
            List<WeatherSailForceSnapshotPayload.Entry> entries,
            long clientTick
    ) {
        Map<Long, ClientSailState> replacement = new HashMap<>(entries.size());
        for (WeatherSailForceSnapshotPayload.Entry entry : entries) {
            if (!isFinite(entry)) {
                return;
            }

            ClientSailState previousState = ACTIVE_SAILS.get(entry.sailId());
            ClientSailState newState = new ClientSailState(
                    entry.subLevelId(),
                    maximumForcePerBlock,
                    clientTick
            );
            newState.targetLocalCenter.set(entry.localCenterX(), entry.localCenterY(), entry.localCenterZ());
            newState.localThicknessAxis.set(
                    entry.localThicknessAxisX(),
                    entry.localThicknessAxisY(),
                    entry.localThicknessAxisZ()
            );
            newState.targetIncomingWind.set(entry.incomingWindX(), entry.incomingWindY(), entry.incomingWindZ());
            newState.targetAppliedForce.set(entry.appliedForceX(), entry.appliedForceY(), entry.appliedForceZ());

            if (previousState != null && previousState.subLevelId.equals(entry.subLevelId())) {
                previousState.sample(
                        clientTick,
                        newState.previousLocalCenter,
                        newState.previousIncomingWind,
                        newState.previousAppliedForce
                );
                if (newState.previousLocalCenter.distanceSquared(newState.targetLocalCenter)
                        > MAX_INTERPOLATION_DISTANCE_SQUARED) {
                    newState.snapPreviousToTarget();
                }
            } else {
                newState.snapPreviousToTarget();
            }
            replacement.put(entry.sailId(), newState);
        }

        ACTIVE_SAILS.clear();
        ACTIVE_SAILS.putAll(replacement);
        activeGeneration = generation;
        lastCompleteClientTick = clientTick;
    }

    private static Vector3dc selectOutwardSurface(double outwardDot, RenderScratch scratch) {
        if (outwardDot > FORCE_EPSILON) {
            return scratch.positiveSurface;
        }
        if (outwardDot < -FORCE_EPSILON) {
            return scratch.negativeSurface;
        }
        return scratch.positiveSurface;
    }

    private static void interpolateAppliedForce(
            Vector3dc previous,
            Vector3dc target,
            double alpha,
            Vector3d result
    ) {
        if (alpha <= 0.0D) {
            result.set(previous);
            return;
        }
        if (alpha >= 1.0D) {
            result.set(target);
            return;
        }

        result.set(previous).lerp(target, alpha);
        double epsilonSquared = FORCE_EPSILON * FORCE_EPSILON;
        double linearLengthSquared = result.lengthSquared();
        if (!Double.isFinite(linearLengthSquared) || linearLengthSquared <= epsilonSquared) {
            result.zero();
            return;
        }
        double linearLength = Math.sqrt(linearLengthSquared);

        double previousLengthSquared = previous.lengthSquared();
        double targetLengthSquared = target.lengthSquared();
        boolean previousDirectionDefined = Double.isFinite(previousLengthSquared)
                && previousLengthSquared > epsilonSquared;
        boolean targetDirectionDefined = Double.isFinite(targetLengthSquared)
                && targetLengthSquared > epsilonSquared;
        if (!previousDirectionDefined && !targetDirectionDefined) {
            result.zero();
            return;
        }

        if (!previousDirectionDefined) {
            double scale = linearLength / Math.sqrt(targetLengthSquared);
            result.set(target.x() * scale, target.y() * scale, target.z() * scale);
            return;
        }
        if (!targetDirectionDefined) {
            double scale = linearLength / Math.sqrt(previousLengthSquared);
            result.set(previous.x() * scale, previous.y() * scale, previous.z() * scale);
            return;
        }

        double previousLength = Math.sqrt(previousLengthSquared);
        double targetLength = Math.sqrt(targetLengthSquared);
        double previousX = previous.x() / previousLength;
        double previousY = previous.y() / previousLength;
        double previousZ = previous.z() / previousLength;
        double targetX = target.x() / targetLength;
        double targetY = target.y() / targetLength;
        double targetZ = target.z() / targetLength;

        double dot = Math.max(
                -1.0D,
                Math.min(
                        1.0D,
                        previousX * targetX + previousY * targetY + previousZ * targetZ
                )
        );
        double axisX = previousY * targetZ - previousZ * targetY;
        double axisY = previousZ * targetX - previousX * targetZ;
        double axisZ = previousX * targetY - previousY * targetX;
        double axisLengthSquared = axisX * axisX + axisY * axisY + axisZ * axisZ;

        double angle;
        if (axisLengthSquared > epsilonSquared) {
            double axisLength = Math.sqrt(axisLengthSquared);
            axisX /= axisLength;
            axisY /= axisLength;
            axisZ /= axisLength;
            angle = Math.atan2(axisLength, dot) * alpha;
        } else if (dot >= 0.0D) {
            result.set(
                    previousX * linearLength,
                    previousY * linearLength,
                    previousZ * linearLength
            );
            return;
        } else {
            double absoluteX = Math.abs(previousX);
            double absoluteY = Math.abs(previousY);
            double absoluteZ = Math.abs(previousZ);
            if (absoluteX <= absoluteY && absoluteX <= absoluteZ) {
                axisX = 0.0D;
                axisY = previousZ;
                axisZ = -previousY;
            } else if (absoluteY <= absoluteZ) {
                axisX = -previousZ;
                axisY = 0.0D;
                axisZ = previousX;
            } else {
                axisX = previousY;
                axisY = -previousX;
                axisZ = 0.0D;
            }

            double axisLength = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
            axisX /= axisLength;
            axisY /= axisLength;
            axisZ /= axisLength;
            angle = Math.PI * alpha;
        }

        double cosine = Math.cos(angle);
        double sine = Math.sin(angle);
        double perpendicularX = axisY * previousZ - axisZ * previousY;
        double perpendicularY = axisZ * previousX - axisX * previousZ;
        double perpendicularZ = axisX * previousY - axisY * previousX;
        double directionX = previousX * cosine + perpendicularX * sine;
        double directionY = previousY * cosine + perpendicularY * sine;
        double directionZ = previousZ * cosine + perpendicularZ * sine;
        double directionLengthSquared = directionX * directionX
                + directionY * directionY
                + directionZ * directionZ;
        if (!Double.isFinite(directionLengthSquared) || directionLengthSquared <= epsilonSquared) {
            result.zero();
            return;
        }
        double scale = linearLength / Math.sqrt(directionLengthSquared);
        result.set(directionX * scale, directionY * scale, directionZ * scale);
    }

    private static void renderArrow(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            Vector3dc origin,
            Vector3dc vector,
            double length,
            int red,
            int green,
            int blue,
            RenderScratch scratch
    ) {
        double vectorLengthSquared = vector.lengthSquared();
        if (vectorLengthSquared <= 1.0E-12D || !Double.isFinite(vectorLengthSquared)) {
            return;
        }

        scratch.direction.set(vector).div(Math.sqrt(vectorLengthSquared));
        scratch.tip.set(origin).fma(length, scratch.direction);
        putLine(pose, buffer, origin, scratch.tip, red, green, blue, scratch.lineDirection);

        double headLength = Math.min(0.30D, Math.max(0.12D, length * 0.18D));
        double headRadius = headLength * 0.45D;
        scratch.arrowBase.set(scratch.tip).fma(-headLength, scratch.direction);

        if (Math.abs(scratch.direction.y) < 0.9D) {
            scratch.perpendicular.set(0.0D, 1.0D, 0.0D);
        } else {
            scratch.perpendicular.set(1.0D, 0.0D, 0.0D);
        }
        scratch.direction.cross(scratch.perpendicular, scratch.perpendicular).normalize();
        scratch.direction.cross(scratch.perpendicular, scratch.orthogonal).normalize();

        scratch.wing.set(scratch.arrowBase).fma(headRadius, scratch.perpendicular);
        putLine(pose, buffer, scratch.tip, scratch.wing, red, green, blue, scratch.lineDirection);
        scratch.wing.set(scratch.arrowBase).fma(-headRadius, scratch.perpendicular);
        putLine(pose, buffer, scratch.tip, scratch.wing, red, green, blue, scratch.lineDirection);
        scratch.wing.set(scratch.arrowBase).fma(headRadius, scratch.orthogonal);
        putLine(pose, buffer, scratch.tip, scratch.wing, red, green, blue, scratch.lineDirection);
        scratch.wing.set(scratch.arrowBase).fma(-headRadius, scratch.orthogonal);
        putLine(pose, buffer, scratch.tip, scratch.wing, red, green, blue, scratch.lineDirection);
    }

    private static void putLine(
            PoseStack.Pose pose,
            VertexConsumer buffer,
            Vector3dc start,
            Vector3dc end,
            int red,
            int green,
            int blue,
            Vector3d lineDirection
    ) {
        lineDirection.set(end).sub(start);
        double lengthSquared = lineDirection.lengthSquared();
        if (lengthSquared <= 1.0E-12D || !Double.isFinite(lengthSquared)) {
            return;
        }
        lineDirection.div(Math.sqrt(lengthSquared));

        buffer.addVertex(pose, (float) start.x(), (float) start.y(), (float) start.z())
                .setColor(red, green, blue, 255)
                .setNormal(pose, (float) lineDirection.x, (float) lineDirection.y, (float) lineDirection.z);
        buffer.addVertex(pose, (float) end.x(), (float) end.y(), (float) end.z())
                .setColor(red, green, blue, 255)
                .setNormal(pose, (float) lineDirection.x, (float) lineDirection.y, (float) lineDirection.z);
    }

    private static Pose3dc resolveRenderPose(SubLevel subLevel, float partialTick, Pose3d fallbackPose) {
        if (subLevel instanceof ClientSubLevelAccess clientSubLevelAccess) {
            return clientSubLevelAccess.renderPose(partialTick);
        }
        return subLevel.lastPose().lerp(subLevel.logicalPose(), partialTick, fallbackPose);
    }

    private static void ensureLevel(ClientLevel level) {
        ResourceLocation dimension = level.dimension().location();
        if (activeLevel == level && dimension.equals(activeDimension)) {
            return;
        }
        clearAll();
        activeLevel = level;
        activeDimension = dimension;
    }

    private static void syncSubscription(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            subscriptionConnection = null;
            subscriptionEnabled = false;
            return;
        }

        boolean enabled = TwisterMillConfig.isSailForceVectorsShown();
        if (connection == subscriptionConnection && enabled == subscriptionEnabled) {
            return;
        }

        if (connection != subscriptionConnection || !enabled) {
            clearSnapshotCache();
        }
        subscriptionConnection = connection;
        subscriptionEnabled = enabled;
        subscriptionEpoch++;
        PacketDistributor.sendToServer(
                new WeatherSailForceSubscriptionPayload(enabled, subscriptionEpoch)
        );
    }

    private static void clearSnapshotCache() {
        ACTIVE_SAILS.clear();
        pendingGeneration = null;
        activeGeneration = Long.MIN_VALUE;
        lastCompleteClientTick = Long.MIN_VALUE;
    }

    private static void clearAll() {
        clearSnapshotCache();
        activeLevel = null;
        activeDimension = null;
    }

    private static boolean isFinite(WeatherSailForceSnapshotPayload.Entry entry) {
        double thicknessAxisLengthSquared =
                (double) entry.localThicknessAxisX() * entry.localThicknessAxisX()
                        + (double) entry.localThicknessAxisY() * entry.localThicknessAxisY()
                        + (double) entry.localThicknessAxisZ() * entry.localThicknessAxisZ();
        return Double.isFinite(entry.localCenterX())
                && Double.isFinite(entry.localCenterY())
                && Double.isFinite(entry.localCenterZ())
                && Float.isFinite(entry.localThicknessAxisX())
                && Float.isFinite(entry.localThicknessAxisY())
                && Float.isFinite(entry.localThicknessAxisZ())
                && Double.isFinite(thicknessAxisLengthSquared)
                && thicknessAxisLengthSquared > FORCE_EPSILON * FORCE_EPSILON
                && Float.isFinite(entry.incomingWindX())
                && Float.isFinite(entry.incomingWindY())
                && Float.isFinite(entry.incomingWindZ())
                && Float.isFinite(entry.appliedForceX())
                && Float.isFinite(entry.appliedForceY())
                && Float.isFinite(entry.appliedForceZ());
    }

    private static boolean isFinite(Vector3dc vector) {
        return Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private static final class PendingGeneration {
        private final ResourceLocation dimension;
        private final long generation;
        private final int partCount;
        private final float maximumForcePerBlock;
        private final Map<Integer, List<WeatherSailForceSnapshotPayload.Entry>> parts = new HashMap<>();
        private long lastReceivedClientTick;

        private PendingGeneration(
                ResourceLocation dimension,
                long generation,
                int partCount,
                float maximumForcePerBlock,
                long clientTick
        ) {
            this.dimension = dimension;
            this.generation = generation;
            this.partCount = partCount;
            this.maximumForcePerBlock = maximumForcePerBlock;
            this.lastReceivedClientTick = clientTick;
        }

        private boolean accept(WeatherSailForceSnapshotPayload payload, long clientTick) {
            if (!dimension.equals(payload.dimension())
                    || generation != payload.generation()
                    || partCount != payload.partCount()
                    || Float.compare(maximumForcePerBlock, payload.maximumForcePerBlock()) != 0) {
                return false;
            }
            parts.put(payload.partIndex(), payload.entries());
            lastReceivedClientTick = clientTick;
            return true;
        }

        private boolean isComplete() {
            return parts.size() == partCount;
        }

        private List<WeatherSailForceSnapshotPayload.Entry> combine() {
            List<WeatherSailForceSnapshotPayload.Entry> combined = new ArrayList<>();
            for (int partIndex = 0; partIndex < partCount; partIndex++) {
                List<WeatherSailForceSnapshotPayload.Entry> entries = parts.get(partIndex);
                if (entries == null) {
                    return null;
                }
                combined.addAll(entries);
                if (combined.size() > MAX_ENTRIES_PER_GENERATION) {
                    return null;
                }
            }
            return combined;
        }
    }

    private static final class ClientSailState {
        private final UUID subLevelId;
        private final float maximumForcePerBlock;
        private final long interpolationStartTick;
        private final Vector3d localThicknessAxis = new Vector3d();
        private final Vector3d previousLocalCenter = new Vector3d();
        private final Vector3d targetLocalCenter = new Vector3d();
        private final Vector3d previousIncomingWind = new Vector3d();
        private final Vector3d targetIncomingWind = new Vector3d();
        private final Vector3d previousAppliedForce = new Vector3d();
        private final Vector3d targetAppliedForce = new Vector3d();

        private ClientSailState(UUID subLevelId, float maximumForcePerBlock, long interpolationStartTick) {
            this.subLevelId = subLevelId;
            this.maximumForcePerBlock = maximumForcePerBlock;
            this.interpolationStartTick = interpolationStartTick;
        }

        private void sample(
                double sampleTick,
                Vector3d localCenter,
                Vector3d incomingWind,
                Vector3d appliedForce
        ) {
            double alpha = Math.max(
                    0.0D,
                    Math.min(1.0D, (sampleTick - interpolationStartTick) / SNAPSHOT_INTERVAL_TICKS)
            );
            localCenter.set(previousLocalCenter).lerp(targetLocalCenter, alpha);
            incomingWind.set(previousIncomingWind).lerp(targetIncomingWind, alpha);
            interpolateAppliedForce(previousAppliedForce, targetAppliedForce, alpha, appliedForce);
        }

        private void snapPreviousToTarget() {
            previousLocalCenter.set(targetLocalCenter);
            previousIncomingWind.set(targetIncomingWind);
            previousAppliedForce.set(targetAppliedForce);
        }
    }

    private static final class RenderScratch {
        private final Pose3d fallbackPose = new Pose3d();
        private final Vector3d localCenter = new Vector3d();
        private final Vector3d worldCenter = new Vector3d();
        private final Vector3d worldThicknessAxis = new Vector3d();
        private final Vector3d incomingWind = new Vector3d();
        private final Vector3d appliedForce = new Vector3d();
        private final Vector3d positiveSurface = new Vector3d();
        private final Vector3d negativeSurface = new Vector3d();
        private final Vector3d redOuter = new Vector3d();
        private final Vector3d reversedIncomingWind = new Vector3d();
        private final Vector3d direction = new Vector3d();
        private final Vector3d tip = new Vector3d();
        private final Vector3d arrowBase = new Vector3d();
        private final Vector3d perpendicular = new Vector3d();
        private final Vector3d orthogonal = new Vector3d();
        private final Vector3d wing = new Vector3d();
        private final Vector3d lineDirection = new Vector3d();
    }
}
