package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.ServoPropellerSlotManager;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.proventure.twistermill.util.SableLevelWrapper;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@SuppressWarnings("unused")
public final class ServoOption7PreviewRenderer {
    private static final float GREEN_RED = 0.20F;
    private static final float GREEN_GREEN = 1.00F;
    private static final float GREEN_BLUE = 0.20F;

    private static final float GREEN_FILL_ALPHA = 0.22F;
    private static final float GREEN_LINE_ALPHA = 0.85F;
    private static final float GREEN_BEAM_FILL_ALPHA = 0.28F;

    private static final double MIN = 0.002D;
    private static final double MAX = 0.998D;
    private static final float BEAM_THICKNESS = 1.0F;
    private static final double SLOT_END_SHORTENING = 0.5D;

    private static final float PREVIEW_SCALE_MAX = 15.0F / 16.0F;
    private static final float PREVIEW_SCALE_MIN = 13.0F / 16.0F;
    private static final float PREVIEW_SCALE_CENTER = (PREVIEW_SCALE_MAX + PREVIEW_SCALE_MIN) * 0.5F;
    private static final float PREVIEW_SCALE_AMPLITUDE = (PREVIEW_SCALE_MAX - PREVIEW_SCALE_MIN) * 0.5F;
    private static final float TICKS_FROM_BIG_TO_SMALL = 15.0F;
    private static final double MIN_LINE_LENGTH_SQUARED = 1.0E-8D;
    private static final double SERVO_RENDER_SPACE_EPSILON = 1.0E-6D;
    private static final int LINE_FULL_VISIBLE_TICKS = 20 * 6;
    private static final int LINE_FADE_OUT_TICKS = 20 * 2;
    private static final int LINE_TOTAL_VISIBLE_TICKS = LINE_FULL_VISIBLE_TICKS + LINE_FADE_OUT_TICKS;
    private static final Map<ServoTwisterBlockEntity, LineLifetimeState> LINE_LIFETIMES = new WeakHashMap<>();

    private ServoOption7PreviewRenderer() {
    }

    @SuppressWarnings("unused")
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return;
        }

        ServoTwisterBlockEntity servo = findLookedAtServo(level, hit);
        if (servo == null || servo.isRemoved() || !servo.isPropellerSlotMode()) {
            return;
        }

        Level servoLevel = servo.getLevel();
        if (servoLevel == null) {
            return;
        }

        Direction facing = getFacing(servo.getBlockState());
        if (facing == null) {
            return;
        }

        BlockPos topPos = servo.getBlockPos().relative(facing);
        Vector3d topCenter = computeWorldCenter(servoLevel, topPos);
        if (topCenter == null) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        if (servo.hasActiveServoTopForPreview()) {
            return;
        }

        if (areAllPreviewTargetsPresent(level, servoLevel, servo, facing, topPos, topCenter)) {
            return;
        }

        float previewScale = getPreviewScale(player, partialTick);

        renderTopPreviewIfMissing(event.getPoseStack(), minecraft, level, servoLevel, servo, topPos, topCenter, previewScale);
        renderSlotPreviewsIfMissing(event.getPoseStack(), minecraft, level, servo, facing, topCenter, previewScale);
    }

    private static void renderTopPreviewIfMissing(
            PoseStack poseStack,
            Minecraft minecraft,
            ClientLevel rootLevel,
            Level servoLevel,
            ServoTwisterBlockEntity servo,
            BlockPos topPos,
            Vector3d topCenter,
            float previewScale
    ) {
        if (servo.hasActiveServoTopForPreview()) {
            return;
        }

        BlockPos topWorldPos = toBlockPos(topCenter);
        if (!isLoaded(rootLevel, topWorldPos)) {
            return;
        }

        if (hasRealBlockAt(servoLevel, topPos) || hasRealBlockAt(rootLevel, topWorldPos)) {
            return;
        }

        renderPreviewBox(poseStack, minecraft, topCenter, null, previewScale);
    }

    private static boolean areAllPreviewTargetsPresent(
            ClientLevel rootLevel,
            Level servoLevel,
            ServoTwisterBlockEntity servo,
            Direction facing,
            BlockPos topPos,
            Vector3d topCenter
    ) {
        BlockPos topWorldPos = toBlockPos(topCenter);
        if (!hasRealBlockAt(servoLevel, topPos) && !hasRealBlockAt(rootLevel, topWorldPos)) {
            return false;
        }

        for (int slot = 0; slot < ServoPropellerSlotManager.getPreviewSlotCount(); slot++) {
            if (servo.hasPropellerSlotForPreview(slot)) {
                continue;
            }

            Vector3d slotCenter = new Vector3d(topCenter)
                    .add(ServoPropellerSlotManager.computePreviewSlotOffset(slot, facing));
            if (!hasRealBlockAt(rootLevel, toBlockPos(slotCenter))) {
                return false;
            }
        }

        return true;
    }

    private static void renderSlotPreviewsIfMissing(
            PoseStack poseStack,
            Minecraft minecraft,
            ClientLevel rootLevel,
            ServoTwisterBlockEntity servo,
            Direction facing,
            Vector3d topCenter,
            float previewScale
    ) {
        for (int slot = 0; slot < ServoPropellerSlotManager.getPreviewSlotCount(); slot++) {
            if (servo.hasPropellerSlotForPreview(slot)) {
                continue;
            }

            Vector3d slotCenter = new Vector3d(topCenter)
                    .add(ServoPropellerSlotManager.computePreviewSlotOffset(slot, facing));
            Quaterniond slotOrientation = ServoPropellerSlotManager.computePreviewSlotOrientation(slot, facing);
            BlockPos slotWorldPos = toBlockPos(slotCenter);
            if (!isLoaded(rootLevel, slotWorldPos) || hasRealBlockAt(rootLevel, slotWorldPos)) {
                continue;
            }

            renderPreviewBox(poseStack, minecraft, slotCenter, slotOrientation, previewScale);
        }
    }

    @SuppressWarnings("unused")
    public static void renderPersistentConnectionBeams(
            ServoTwisterBlockEntity servo,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource
    ) {
        if (servo == null) {
            return;
        }

        if (servo.isRemoved() || !servo.isPropellerSlotMode() || !servo.hasActiveServoTopForPreview()) {
            resetLineLifetime(servo);
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel rootLevel = minecraft.level;
        Level servoLevel = servo.getLevel();
        if (rootLevel == null || servoLevel == null) {
            resetLineLifetime(servo);
            return;
        }

        Direction facing = getFacing(servo.getBlockState());
        if (facing == null) {
            resetLineLifetime(servo);
            return;
        }

        BlockPos topPos = servo.getBlockPos().relative(facing);
        ConnectionLinePoints worldPoints = resolveConnectionLinePoints(rootLevel, servoLevel, servo, topPos, partialTick);
        if (worldPoints == null) {
            resetLineLifetime(servo);
            return;
        }

        ConnectionLinePoints shortenedWorldPoints = shortenSlotEndsForRender(worldPoints);
        if (shortenedWorldPoints == null) {
            resetLineLifetime(servo);
            return;
        }

        ConnectionLinePoints localPoints = toServoRenderSpace(rootLevel, servoLevel, servo, shortenedWorldPoints, partialTick);
        if (localPoints == null) {
            resetLineLifetime(servo);
            return;
        }

        float alphaMultiplier = getLineLifetimeAlpha(servo, rootLevel.getGameTime());
        if (alphaMultiplier <= 0.0F) {
            return;
        }

        renderConnectionBeams(poseStack, bufferSource, localPoints, alphaMultiplier);
    }

    @Nullable
    private static ConnectionLinePoints resolveConnectionLinePoints(
            ClientLevel rootLevel,
            Level servoLevel,
            ServoTwisterBlockEntity servo,
            BlockPos topPos,
            float partialTick
    ) {
        SubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return null;
        }

        SubLevel topSubLevel = resolveSubLevel(container, servo.getActiveServoTopSubLevelIdForPreview());
        if (topSubLevel == null) {
            return null;
        }

        Vector3d topWorldCenter = computeWorldCenter(servoLevel, topPos);
        if (!isFinite(topWorldCenter)) {
            return null;
        }

        Pose3dc topPose = resolveRenderPose(topSubLevel, partialTick);
        Vector3d topLocalCenter = topPose.transformPositionInverse(topWorldCenter, new Vector3d());
        if (!hasSubLevelBlockAt(rootLevel, topSubLevel, toBlockPos(topLocalCenter))) {
            return null;
        }

        Vector3d topLineStart = topPose.transformPosition(topLocalCenter, new Vector3d());
        if (!isFinite(topLineStart)) {
            return null;
        }

        Vector3d[] slotCenters = new Vector3d[ServoPropellerSlotManager.getPreviewSlotCount()];
        for (int slot = 0; slot < slotCenters.length; slot++) {
            SubLevel slotSubLevel = resolveSubLevel(container, servo.getPropellerSlotSubLevelIdForPreview(slot));
            Vector3d slotLocalCenter = servo.getPropellerSlotAnchorLocalCenterForPreview(slot);
            if (slotSubLevel == null || !isFinite(slotLocalCenter)) {
                return null;
            }

            if (!hasSubLevelBlockAt(rootLevel, slotSubLevel, toBlockPos(slotLocalCenter))) {
                return null;
            }

            Pose3dc slotPose = resolveRenderPose(slotSubLevel, partialTick);
            Vector3d slotLineEnd = slotPose.transformPosition(slotLocalCenter, new Vector3d());
            if (!isRenderableLine(topLineStart, slotLineEnd)) {
                return null;
            }
            slotCenters[slot] = slotLineEnd;
        }

        return new ConnectionLinePoints(topLineStart, slotCenters);
    }

    @Nullable
    private static ConnectionLinePoints shortenSlotEndsForRender(ConnectionLinePoints originalPoints) {
        Vector3d originalStart = originalPoints.topCenter();
        Vector3d[] shortenedEnds = new Vector3d[originalPoints.slotCenters().length];
        for (int slot = 0; slot < shortenedEnds.length; slot++) {
            Vector3d originalEnd = originalPoints.slotCenters()[slot];
            Vector3d direction = new Vector3d(originalEnd).sub(originalStart);
            double originalDistance = direction.length();
            if (!Double.isFinite(originalDistance) || originalDistance <= SLOT_END_SHORTENING) {
                return null;
            }

            direction.div(originalDistance);
            Vector3d renderEnd = new Vector3d(originalEnd)
                    .sub(new Vector3d(direction).mul(SLOT_END_SHORTENING));
            if (!isRenderableLine(originalStart, renderEnd)) {
                return null;
            }
            shortenedEnds[slot] = renderEnd;
        }

        return new ConnectionLinePoints(new Vector3d(originalStart), shortenedEnds);
    }

    @Nullable
    private static ConnectionLinePoints toServoRenderSpace(
            ClientLevel rootLevel,
            Level servoLevel,
            ServoTwisterBlockEntity servo,
            ConnectionLinePoints worldPoints,
            float partialTick
    ) {
        SubLevel containingSubLevel = resolveContainingSubLevelForRender(rootLevel, servoLevel, servo);
        boolean requiresSubLevelPose = requiresSubLevelRenderSpace(rootLevel, servoLevel, servo);

        Vector3d localTopCenter = toServoRenderLocalPoint(
                servo,
                worldPoints.topCenter(),
                containingSubLevel,
                requiresSubLevelPose,
                partialTick
        );
        if (!isFinite(localTopCenter)) {
            return null;
        }

        Vector3d[] localSlotCenters = new Vector3d[worldPoints.slotCenters().length];
        for (int slot = 0; slot < localSlotCenters.length; slot++) {
            Vector3d localSlotCenter = toServoRenderLocalPoint(
                    servo,
                    worldPoints.slotCenters()[slot],
                    containingSubLevel,
                    requiresSubLevelPose,
                    partialTick
            );
            if (!isRenderableLine(localTopCenter, localSlotCenter)) {
                return null;
            }
            localSlotCenters[slot] = localSlotCenter;
        }

        return new ConnectionLinePoints(localTopCenter, localSlotCenters);
    }

    @Nullable
    private static Vector3d toServoRenderLocalPoint(
            ServoTwisterBlockEntity servo,
            Vector3d rootWorldPoint,
            @Nullable SubLevel containingSubLevel,
            boolean requiresSubLevelPose,
            float partialTick
    ) {
        Vector3d renderSpacePoint;
        if (containingSubLevel != null) {
            renderSpacePoint = resolveRenderPose(containingSubLevel, partialTick)
                    .transformPositionInverse(rootWorldPoint, new Vector3d());
        } else {
            if (requiresSubLevelPose) {
                return null;
            }
            renderSpacePoint = new Vector3d(rootWorldPoint);
        }

        if (!isFinite(renderSpacePoint)) {
            return null;
        }

        BlockPos servoPos = servo.getBlockPos();
        return renderSpacePoint.sub(servoPos.getX(), servoPos.getY(), servoPos.getZ());
    }

    @Nullable
    private static SubLevel resolveContainingSubLevelForRender(
            ClientLevel rootLevel,
            Level servoLevel,
            ServoTwisterBlockEntity servo
    ) {
        Vector3d servoWorldCenter = computeWorldCenter(servoLevel, servo.getBlockPos());
        if (!isFinite(servoWorldCenter)) {
            return null;
        }

        try {
            SubLevel containing = Sable.HELPER.getContaining(rootLevel, servoWorldCenter);
            return containing == null || containing.isRemoved() ? null : containing;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean requiresSubLevelRenderSpace(
            ClientLevel rootLevel,
            Level servoLevel,
            ServoTwisterBlockEntity servo
    ) {
        Vector3d servoWorldCenter = computeWorldCenter(servoLevel, servo.getBlockPos());
        if (!isFinite(servoWorldCenter)) {
            return false;
        }

        try {
            //noinspection DataFlowIssue
            if (Sable.HELPER.getContaining(rootLevel, servoWorldCenter) instanceof SubLevel) {
                return true;
            }
        } catch (RuntimeException ignored) {
        }

        BlockPos servoPos = servo.getBlockPos();
        Vector3d rootBlockCenter = new Vector3d(
                servoPos.getX() + 0.5D,
                servoPos.getY() + 0.5D,
                servoPos.getZ() + 0.5D
        );
        return servoWorldCenter.distanceSquared(rootBlockCenter) > SERVO_RENDER_SPACE_EPSILON;
    }

    @Nullable
    private static SubLevel resolveSubLevel(SubLevelContainer container, @Nullable UUID subLevelId) {
        if (subLevelId == null) {
            return null;
        }

        try {
            SubLevel subLevel = container.getSubLevel(subLevelId);
            return subLevel == null || subLevel.isRemoved() ? null : subLevel;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Pose3dc resolveRenderPose(SubLevel subLevel, float partialTick) {
        if (subLevel instanceof ClientSubLevelAccess clientSubLevelAccess) {
            return clientSubLevelAccess.renderPose(partialTick);
        }
        return subLevel.lastPose().lerp(subLevel.logicalPose(), partialTick, new Pose3d());
    }

    @Nullable
    private static ServoTwisterBlockEntity findLookedAtServo(ClientLevel level, BlockHitResult hit) {
        ServoTwisterBlockEntity direct = getServoAt(level, hit.getBlockPos());
        if (direct != null && isTargetingBlock(direct, hit)) {
            return direct;
        }

        try {
            Vector3d hitLocation = toVector3d(hit.getLocation());
            if (Sable.HELPER.getContaining(level, hitLocation) instanceof SubLevel containing) {
                Vector3d localHit = containing.logicalPose()
                        .transformPositionInverse(hitLocation, new Vector3d());
                BlockPos localPos = toBlockPos(localHit);
                ServoTwisterBlockEntity local = getServoAt(level, localPos);
                if (local != null && isTargetingBlock(local, hit)) {
                    return local;
                }
            }
        } catch (RuntimeException ignored) {
        }

        return null;
    }

    @Nullable
    private static ServoTwisterBlockEntity getServoAt(ClientLevel level, BlockPos pos) {
        if (!isLoaded(level, pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ServoTwisterBlockEntity servo ? servo : null;
    }

    private static boolean isTargetingBlock(ServoTwisterBlockEntity servo, BlockHitResult hit) {
        Level level = servo.getLevel();
        if (level == null) {
            return false;
        }

        BlockPos pos = servo.getBlockPos();
        if (hit.getBlockPos().equals(pos)) {
            return true;
        }

        Vec3 localHit = getLocalHitOrOriginal(level, pos, hit);
        return SablePlacementHitHelper.isHitLocationConsistentWithPos(pos, localHit);
    }

    private static Vec3 getLocalHitOrOriginal(Level level, BlockPos pos, BlockHitResult hit) {
        try {
            return SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, pos, hit);
        } catch (RuntimeException ignored) {
            return hit.getLocation();
        }
    }

    @Nullable
    private static Direction getFacing(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return null;
        }
        return state.getValue(BlockStateProperties.FACING);
    }

    @Nullable
    private static Vector3d computeWorldCenter(Level level, BlockPos pos) {
        try {
            return SableLevelWrapper.toWorldCenter(level, pos);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean hasRealBlockAt(Level level, BlockPos pos) {
        try {
            return isLoaded(level, pos) && !level.getBlockState(pos).isAir();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean hasSubLevelBlockAt(ClientLevel rootLevel, SubLevel expectedSubLevel, BlockPos pos) {
        try {
            if (!isLoaded(rootLevel, pos) || !isPosInSubLevel(rootLevel, expectedSubLevel, pos)) {
                return false;
            }
            return !rootLevel.getBlockState(pos).isAir();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isPosInSubLevel(ClientLevel rootLevel, SubLevel expectedSubLevel, BlockPos pos) {
        try {
            SubLevel containing = Sable.HELPER.getContaining(rootLevel, pos);
            if (containing != null && expectedSubLevel.getUniqueId().equals(containing.getUniqueId())) {
                return true;
            }
        } catch (RuntimeException ignored) {
        }

        try {
            var bounds = expectedSubLevel.getPlot().getBoundingBox();
            return pos.getX() >= bounds.minX() && pos.getX() <= bounds.maxX()
                    && pos.getY() >= bounds.minY() && pos.getY() <= bounds.maxY()
                    && pos.getZ() >= bounds.minZ() && pos.getZ() <= bounds.maxZ();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isLoaded(Level level, BlockPos pos) {
        try {
            return level.isLoaded(pos);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Vector3d toVector3d(Vec3 vec) {
        return new Vector3d(vec.x, vec.y, vec.z);
    }

    private static BlockPos toBlockPos(Vector3d center) {
        return BlockPos.containing(center.x, center.y, center.z);
    }

    private static boolean isFinite(@Nullable Vector3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isRenderableLine(Vector3d start, Vector3d end) {
        return isFinite(start)
                && isFinite(end)
                && new Vector3d(end).sub(start).lengthSquared() > MIN_LINE_LENGTH_SQUARED;
    }

    private static float getPreviewScale(LocalPlayer player, float partialTick) {
        double ticks = player.tickCount + partialTick;
        double angle = (ticks / TICKS_FROM_BIG_TO_SMALL) * Math.PI;
        return PREVIEW_SCALE_CENTER + (float) Math.cos(angle) * PREVIEW_SCALE_AMPLITUDE;
    }

    private static void renderPreviewBox(
            PoseStack poseStack,
            Minecraft minecraft,
            Vector3d center,
            @Nullable Quaterniond orientation,
            float previewScale
    ) {
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(
                center.x - camera.x,
                center.y - camera.y,
                center.z - camera.z
        );
        if (orientation != null) {
            poseStack.mulPose(toQuaternionf(orientation));
        }
        poseStack.scale(previewScale, previewScale, previewScale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        renderGreenHologram(poseStack, minecraft);
        poseStack.popPose();
    }

    private static float getLineLifetimeAlpha(ServoTwisterBlockEntity servo, long gameTime) {
        UUID topId = servo.getActiveServoTopSubLevelIdForPreview();
        UUID slot0 = servo.getPropellerSlotSubLevelIdForPreview(0);
        UUID slot1 = servo.getPropellerSlotSubLevelIdForPreview(1);
        UUID slot2 = servo.getPropellerSlotSubLevelIdForPreview(2);
        if (topId == null || slot0 == null || slot1 == null || slot2 == null) {
            resetLineLifetime(servo);
            return 0.0F;
        }

        LineLifetimeState state = LINE_LIFETIMES.get(servo);
        if (state == null || gameTime < state.startTick() || !state.matches(topId, slot0, slot1, slot2)) {
            state = new LineLifetimeState(gameTime, topId, slot0, slot1, slot2);
            LINE_LIFETIMES.put(servo, state);
        }

        long elapsedTicks = gameTime - state.startTick();
        if (elapsedTicks >= LINE_TOTAL_VISIBLE_TICKS) {
            return 0.0F;
        }

        if (elapsedTicks <= LINE_FULL_VISIBLE_TICKS) {
            return 1.0F;
        }

        return (LINE_TOTAL_VISIBLE_TICKS - elapsedTicks) / (float) LINE_FADE_OUT_TICKS;
    }

    private static void resetLineLifetime(ServoTwisterBlockEntity servo) {
        LINE_LIFETIMES.remove(servo);
    }

    private static void renderConnectionBeams(PoseStack poseStack, MultiBufferSource bufferSource, ConnectionLinePoints points, float alphaMultiplier) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        for (Vector3d slotCenter : points.slotCenters()) {
            renderGreenBeam(poseStack, bufferSource, points.topCenter(), slotCenter, alphaMultiplier);
        }
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void renderGreenBeam(PoseStack poseStack, MultiBufferSource bufferSource, Vector3d start, Vector3d end, float alphaMultiplier) {
        Vector3d delta = new Vector3d(end).sub(start);
        double length = delta.length();
        if (!Double.isFinite(length) || length <= MIN_LINE_LENGTH_SQUARED) {
            return;
        }

        delta.div(length);
        Vector3d midpoint = new Vector3d(start).add(end).mul(0.5D);
        Quaternionf rotation = new Quaternionf().rotationTo(
                0.0F, 0.0F, 1.0F,
                (float) delta.x,
                (float) delta.y,
                (float) delta.z
        );

        poseStack.pushPose();
        poseStack.translate(midpoint.x, midpoint.y, midpoint.z);
        poseStack.mulPose(rotation);
        poseStack.scale(BEAM_THICKNESS, BEAM_THICKNESS, (float) length);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                bufferSource.getBuffer(RenderType.debugFilledBox()),
                MIN, MIN, MIN,
                MAX, MAX, MAX,
                GREEN_RED, GREEN_GREEN, GREEN_BLUE, GREEN_BEAM_FILL_ALPHA * alphaMultiplier
        );
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                MIN, MIN, MIN,
                MAX, MAX, MAX,
                GREEN_RED, GREEN_GREEN, GREEN_BLUE, GREEN_LINE_ALPHA * alphaMultiplier
        );

        poseStack.popPose();
    }

    private static Quaternionf toQuaternionf(Quaterniond orientation) {
        return new Quaternionf(
                (float) orientation.x(),
                (float) orientation.y(),
                (float) orientation.z(),
                (float) orientation.w()
        ).normalize();
    }

    private static void renderGreenHologram(PoseStack poseStack, Minecraft minecraft) {
        renderFilledCube(poseStack, minecraft);

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                MIN, MIN, MIN,
                MAX, MAX, MAX,
                GREEN_RED, GREEN_GREEN, GREEN_BLUE, GREEN_LINE_ALPHA
        );
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderFilledCube(PoseStack poseStack, Minecraft minecraft) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                bufferSource.getBuffer(RenderType.debugFilledBox()),
                MIN, MIN, MIN,
                MAX, MAX, MAX,
                GREEN_RED, GREEN_GREEN, GREEN_BLUE, GREEN_FILL_ALPHA
        );
        bufferSource.endBatch(RenderType.debugFilledBox());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private record ConnectionLinePoints(Vector3d topCenter, Vector3d[] slotCenters) {
    }

    private record LineLifetimeState(long startTick, UUID topId, UUID slot0, UUID slot1, UUID slot2) {
        boolean matches(UUID topId, UUID slot0, UUID slot1, UUID slot2) {
            return this.topId.equals(topId)
                    && this.slot0.equals(slot0)
                    && this.slot1.equals(slot1)
                    && this.slot2.equals(slot2);
        }
    }
}
