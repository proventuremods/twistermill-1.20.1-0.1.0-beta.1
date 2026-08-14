package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.blockentity.ServoPropellerSlotManager;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.proventure.twistermill.client.TwisterMillPartialModels;
import com.proventure.twistermill.util.SableLevelWrapper;
import com.proventure.twistermill.util.ServoTwoAxisRotationMath;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

import java.util.UUID;

public class ServoTwisterRenderer extends KineticBlockEntityRenderer<ServoTwisterBlockEntity> {
    private static final double OPTION7_BEAM_RENDER_BOUNDS = 4.0D;
    private static final double SERVO_RENDER_SPACE_EPSILON = 1.0E-6D;
    private static final double ANTENNA_DOWN_OFFSET = 14.0D / 16.0D;
    private final Quaternionf twoAxisRotation = new Quaternionf();

    public ServoTwisterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ServoTwisterBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();

        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        SuperByteBuffer top = CachedBuffers.partial(TwisterMillPartialModels.SERVO_TWISTER_TOP, state);

        rotateTopToFacing(top, facing);

        float rawAngle = be.getInterpolatedAngle(partialTicks);
        float secondRawAngle = be.getInterpolatedSecondAngle(partialTicks);
        float sign = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : -1f;
        float facingAxisAngle = rawAngle * -sign;

        int packedLight = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : light;

        if (!SableTopVisualTransform.renderTop(
                be,
                be.getActiveServoTopSubLevelIdForPreview(),
                facing,
                partialTicks,
                ms,
                () -> top.light(packedLight).renderInto(ms, buffer.getBuffer(RenderType.cutout()))
        )) {
            if (be.usesTwoAxisTiltRotationForRender()) {
                ms.pushPose();
                ms.translate(0.5D, 0.5D, 0.5D);
                ms.mulPose(ServoTwoAxisRotationMath.setBlockMovementQuaternion(
                        facing, rawAngle, secondRawAngle, twoAxisRotation));
                ms.translate(-0.5D, -0.5D, -0.5D);
                top.light(packedLight)
                        .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
                ms.popPose();
            } else if (be.usesUpPitchRotationForRender()) {
                ms.pushPose();
                ms.translate(0.5D, 0.5D, 0.5D);
                ms.mulPose(Axis.XP.rotationDegrees(rawAngle));
                ms.translate(-0.5D, -0.5D, -0.5D);
                top.light(packedLight)
                        .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
                ms.popPose();
            } else {
                top.rotateCentered((float) Math.toRadians(facingAxisAngle), Direction.Axis.Z);
                top.light(packedLight)
                        .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
            }
        }

        if (be.isAnyInternalRedstoneLinkReceiverActive()) {
            SuperByteBuffer antenna = CachedBuffers.partial(TwisterMillPartialModels.SERVO_TWISTER_ANTENNA, state);
            rotateHousingFixedPartialToBlockstateFacing(antenna, facing);
            renderHousingFixedAntenna(antenna, facing, packedLight, ms, buffer);
        }

        if (be.shouldRenderInternalRedstoneLinkSlots()
                || be.shouldRenderSecondaryInternalRedstoneLinkSlots()) {
            InternalServoRedstoneLinkRenderer.renderOnBlockEntity(be, false, ms, buffer, light, overlay);
        }

        InternalServoMovementModeIconRenderer.renderOnBlockEntity(
                be,
                be.getConfiguredMaxDegreesForDisplay(),
                ms,
                buffer
        );

        renderOption7BladeArmClones(be, facing, facingAxisAngle, partialTicks, ms, buffer, packedLight, overlay);
    }

    private static void rotateHousingFixedPartialToBlockstateFacing(SuperByteBuffer buf, Direction facing) {
        switch (facing) {
            case NORTH -> {
            }
            case SOUTH -> buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
            case EAST -> buf.rotateCentered((float) Math.toRadians(90), Direction.UP);
            case WEST -> buf.rotateCentered((float) Math.toRadians(-90), Direction.UP);
            case UP -> {
                buf.rotateCentered((float) Math.toRadians(-90), Direction.EAST);
                buf.rotateCentered((float) Math.toRadians(180), Direction.Axis.Z);
            }
            case DOWN -> buf.rotateCentered((float) Math.toRadians(90), Direction.EAST);
        }
    }

    private static void renderHousingFixedAntenna(
            SuperByteBuffer antenna,
            Direction facing,
            int packedLight,
            PoseStack ms,
            MultiBufferSource buffer
    ) {
        if (facing == Direction.DOWN) {
            renderTranslatedHousingFixedAntenna(
                    antenna,
                    packedLight,
                    ms,
                    buffer
            );
            return;
        }

        antenna.light(packedLight)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }

    private static void renderTranslatedHousingFixedAntenna(
            SuperByteBuffer antenna,
            int packedLight,
            PoseStack ms,
            MultiBufferSource buffer
    ) {
        ms.pushPose();
        ms.translate(0.0D, ANTENNA_DOWN_OFFSET, 0.0D);
        antenna.light(packedLight)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NotNull ServoTwisterBlockEntity be) {
        if (be != null && be.isPropellerSlotMode()) {
            return new AABB(be.getBlockPos()).inflate(OPTION7_BEAM_RENDER_BOUNDS);
        }
        return SableTopVisualTransform.renderBounds(be.getBlockPos(), be.getActiveServoTopSubLevelIdForPreview());
    }

    private static void renderOption7BladeArmClones(
            ServoTwisterBlockEntity be,
            Direction facing,
            float topAngle,
            float partialTicks,
            PoseStack ms,
            MultiBufferSource buffer,
            int fallbackLight,
            int overlay
    ) {
        if (!be.isPropellerSlotMode()) {
            return;
        }

        BladeArmRenderState armState = resolveBladeArmRenderState(be, facing, partialTicks, fallbackLight);
        if (armState == null) {
            return;
        }

        renderOption7BladeArmClone(be, facing, topAngle, armState, 1, 120.0F, partialTicks, ms, buffer, overlay);
        renderOption7BladeArmClone(be, facing, topAngle, armState, 2, 240.0F, partialTicks, ms, buffer, overlay);
    }

    @Nullable
    private static BladeArmRenderState resolveBladeArmRenderState(
            ServoTwisterBlockEntity be,
            Direction facing,
            float partialTicks,
            int fallbackLight
    ) {
        Level level = be.getLevel();
        if (level == null) {
            return null;
        }

        BlockPos topPos = be.getBlockPos().relative(facing);
        BladeArmRenderState rootState = resolveRootBladeArmState(level, topPos, fallbackLight);
        if (rootState != null) {
            return rootState;
        }

        ClientLevel rootLevel = Minecraft.getInstance().level;
        if (rootLevel != null) {
            Vector3d topWorldCenter = computeWorldCenter(level, topPos);
            if (isFinite(topWorldCenter)) {
                BlockPos topWorldPos = toBlockPos(topWorldCenter);
                if (rootLevel != level || !topWorldPos.equals(topPos)) {
                    BladeArmRenderState rootWorldState = resolveRootBladeArmState(rootLevel, topWorldPos, fallbackLight);
                    if (rootWorldState != null) {
                        return rootWorldState;
                    }
                }
            }
        }

        if (!be.hasActiveServoTopForPreview()) {
            return null;
        }

        if (rootLevel == null) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return null;
        }

        SubLevel topSubLevel = resolveSubLevel(container, be.getActiveServoTopSubLevelIdForPreview());
        if (topSubLevel == null) {
            return null;
        }

        Vector3d topWorldCenter = computeWorldCenter(level, topPos);
        if (!isFinite(topWorldCenter)) {
            return null;
        }

        Pose3dc topPose = resolveRenderPose(topSubLevel, partialTicks);
        Vector3d topLocalCenter = topPose.transformPositionInverse(topWorldCenter, new Vector3d());
        if (!isFinite(topLocalCenter)) {
            return null;
        }

        BlockPos localTopPos = toBlockPos(topLocalCenter);
        if (!hasSubLevelBlockAt(rootLevel, topSubLevel, localTopPos)) {
            return null;
        }

        BlockState state = rootLevel.getBlockState(localTopPos);
        if (!isBladeArmState(state)) {
            return null;
        }

        int packedLight = getLightOrFallback(rootLevel, localTopPos, fallbackLight);
        return new BladeArmRenderState(state, packedLight, topSubLevel, localTopPos);
    }

    @Nullable
    private static BladeArmRenderState resolveRootBladeArmState(Level level, BlockPos topPos, int fallbackLight) {
        if (!isLoaded(level, topPos)) {
            return null;
        }

        BlockState state = level.getBlockState(topPos);
        if (!isBladeArmState(state)) {
            return null;
        }

        int packedLight = getLightOrFallback(level, topPos, fallbackLight);
        return new BladeArmRenderState(state, packedLight, null, null);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isBladeArmState(BlockState state) {
        return state.is(ModBlocks.BLADE_ARM_BLOCK.get())
                || state.is(ModBlocks.BLADE_ARM_EASTFACE_BLOCK.get())
                || state.is(ModBlocks.BLADE_ARM_WESTFACE_BLOCK.get());
    }

    private static void renderOption7BladeArmClone(
            ServoTwisterBlockEntity be,
            Direction facing,
            float topAngle,
            BladeArmRenderState armState,
            int slot,
            float slotAngle,
            float partialTicks,
            PoseStack ms,
            MultiBufferSource buffer,
            int overlay
    ) {
        if (renderOption7BladeArmCloneFromTopPose(be, facing, armState, slot, partialTicks, ms, buffer, overlay)) {
            return;
        }

        ms.pushPose();
        ms.translate(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        ms.translate(0.5D, 0.5D, 0.5D);
        rotateAroundFacing(ms, facing, topAngle);
        rotateAroundFacing(ms, facing, slotAngle);
        ms.translate(-0.5D, -0.5D, -0.5D);

        //noinspection deprecation
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                armState.state(),
                ms,
                buffer,
                armState.packedLight(),
                overlay
        );
        ms.popPose();
    }

    private static boolean renderOption7BladeArmCloneFromTopPose(
            ServoTwisterBlockEntity be,
            Direction facing,
            BladeArmRenderState armState,
            int slot,
            float partialTicks,
            PoseStack ms,
            MultiBufferSource buffer,
            int overlay
    ) {
        if (armState.topSubLevel() == null || armState.localTopPos() == null) {
            return false;
        }

        ClientLevel rootLevel = Minecraft.getInstance().level;
        Level servoLevel = be.getLevel();
        if (rootLevel == null || servoLevel == null) {
            return false;
        }

        Pose3dc topPose = resolveRenderPose(armState.topSubLevel(), partialTicks);
        Vector3d topLocalCenter = be.getActiveServoTopAnchorLocalCenterForPreview();
        if (!isFinite(topLocalCenter)) {
            topLocalCenter = centerOf(armState.localTopPos());
        }
        Vector3d topWorldCenter = topPose.transformPosition(topLocalCenter, new Vector3d());
        if (!isFinite(topWorldCenter)) {
            return false;
        }

        SubLevel containingSubLevel = resolveContainingSubLevelForRender(rootLevel, servoLevel, be);
        boolean requiresSubLevelPose = requiresSubLevelRenderSpace(rootLevel, servoLevel, be);
        Vector3d renderLocalCenter = toServoRenderLocalPoint(
                be,
                topWorldCenter,
                containingSubLevel,
                requiresSubLevelPose,
                partialTicks
        );
        if (!isFinite(renderLocalCenter)) {
            return false;
        }

        Quaterniond renderOrientation = resolveBladeArmCloneRenderOrientation(
                be,
                rootLevel,
                topPose,
                containingSubLevel,
                facing,
                slot,
                partialTicks
        );
        if (renderOrientation == null) {
            return false;
        }

        ms.pushPose();
        ms.translate(renderLocalCenter.x, renderLocalCenter.y, renderLocalCenter.z);
        ms.mulPose(toQuaternionf(renderOrientation));
        ms.translate(-0.5D, -0.5D, -0.5D);

        //noinspection deprecation
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                armState.state(),
                ms,
                buffer,
                armState.packedLight(),
                overlay
        );
        ms.popPose();
        return true;
    }

    @Nullable
    private static Quaterniond resolveBladeArmCloneRenderOrientation(
            ServoTwisterBlockEntity be,
            ClientLevel rootLevel,
            Pose3dc topPose,
            @Nullable SubLevel containingSubLevel,
            Direction facing,
            int slot,
            float partialTicks
    ) {
        Quaterniond slotPoseOrientation = resolveSlotSubLevelRenderOrientation(
                be,
                rootLevel,
                containingSubLevel,
                slot,
                partialTicks
        );
        if (slotPoseOrientation != null) {
            return slotPoseOrientation;
        }

        return resolveComputedTopRenderOrientation(topPose, containingSubLevel, facing, slot, partialTicks);
    }

    @Nullable
    private static Quaterniond resolveSlotSubLevelRenderOrientation(
            ServoTwisterBlockEntity be,
            ClientLevel rootLevel,
            @Nullable SubLevel containingSubLevel,
            int slot,
            float partialTicks
    ) {
        SubLevelContainer container = SubLevelContainer.getContainer(rootLevel);
        if (container == null) {
            return null;
        }

        SubLevel slotSubLevel = resolveSubLevel(container, be.getPropellerSlotSubLevelIdForPreview(slot));
        if (slotSubLevel == null) {
            return null;
        }

        Pose3dc slotPose = resolveRenderPose(slotSubLevel, partialTicks);
        Quaterniond orientation = new Quaterniond(slotPose.orientation());
        if (containingSubLevel == null) {
            return orientation.normalize();
        }

        Pose3dc containingPose = resolveRenderPose(containingSubLevel, partialTicks);
        return new Quaterniond(containingPose.orientation()).invert().mul(orientation).normalize();
    }

    @Nullable
    private static Quaterniond resolveComputedTopRenderOrientation(
            Pose3dc topPose,
            @Nullable SubLevel containingSubLevel,
            Direction facing,
            int slot,
            float partialTicks
    ) {
        Quaterniond slotRotation = ServoPropellerSlotManager.computePreviewSlotOrientation(slot, facing);
        Quaterniond orientation = new Quaterniond(topPose.orientation()).mul(slotRotation);

        if (containingSubLevel == null) {
            return orientation.normalize();
        }

        Pose3dc containingPose = resolveRenderPose(containingSubLevel, partialTicks);
        return new Quaterniond(containingPose.orientation()).invert().mul(orientation).normalize();
    }

    @Nullable
    private static Vector3d toServoRenderLocalPoint(
            ServoTwisterBlockEntity servo,
            Vector3d rootWorldPoint,
            @Nullable SubLevel containingSubLevel,
            boolean requiresSubLevelPose,
            float partialTicks
    ) {
        Vector3d renderSpacePoint;
        if (containingSubLevel != null) {
            renderSpacePoint = resolveRenderPose(containingSubLevel, partialTicks)
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
        SubLevel directContaining = resolveDirectContainingSubLevel(servo);
        if (directContaining != null) {
            return directContaining;
        }

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
        if (resolveDirectContainingSubLevel(servo) != null) {
            return true;
        }

        Vector3d servoWorldCenter = computeWorldCenter(servoLevel, servo.getBlockPos());
        if (!isFinite(servoWorldCenter)) {
            return false;
        }

        try {
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
    private static SubLevel resolveDirectContainingSubLevel(ServoTwisterBlockEntity servo) {
        try {
            SubLevel containing = Sable.HELPER.getContainingClient(servo);
            return containing == null || containing.isRemoved() ? null : containing;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void rotateAroundFacing(PoseStack ms, Direction facing, float angleDegrees) {
        if (angleDegrees == 0.0F) {
            return;
        }
        ms.mulPose(new Quaternionf().rotationAxis(
                (float) Math.toRadians(angleDegrees),
                facing.getStepX(),
                facing.getStepY(),
                facing.getStepZ()
        ));
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

    private static Pose3dc resolveRenderPose(SubLevel subLevel, float partialTicks) {
        if (subLevel instanceof ClientSubLevelAccess clientSubLevelAccess) {
            return clientSubLevelAccess.renderPose(partialTicks);
        }
        return subLevel.lastPose().lerp(subLevel.logicalPose(), partialTicks, new Pose3d());
    }

    @Nullable
    private static Vector3d computeWorldCenter(Level level, BlockPos pos) {
        try {
            return SableLevelWrapper.toWorldCenter(level, pos);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

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

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean isLoaded(Level level, BlockPos pos) {
        try {
            return level.isLoaded(pos);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int getLightOrFallback(Level level, BlockPos pos, int fallbackLight) {
        try {
            return LevelRenderer.getLightColor(level, pos);
        } catch (RuntimeException ignored) {
            return fallbackLight;
        }
    }

    private static BlockPos toBlockPos(Vector3d center) {
        return BlockPos.containing(center.x, center.y, center.z);
    }

    private static Vector3d centerOf(BlockPos pos) {
        return new Vector3d(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        );
    }

    private static boolean isFinite(@Nullable Vector3d vector) {
        return vector != null
                && Double.isFinite(vector.x)
                && Double.isFinite(vector.y)
                && Double.isFinite(vector.z);
    }

    private static Quaternionf toQuaternionf(Quaterniond orientation) {
        return new Quaternionf(
                (float) orientation.x(),
                (float) orientation.y(),
                (float) orientation.z(),
                (float) orientation.w()
        ).normalize();
    }

    private record BladeArmRenderState(
            BlockState state,
            int packedLight,
            @Nullable SubLevel topSubLevel,
            @Nullable BlockPos localTopPos
    ) {
    }

    private static void rotateTopToFacing(SuperByteBuffer buf, Direction facing) {
        float modelYawFixDeg = 0f;

        switch (facing) {
            case NORTH -> {
                buf.rotateCentered(0f, Direction.UP);
                modelYawFixDeg = 0f;
            }
            case SOUTH -> {
                buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                modelYawFixDeg = 0f;
            }
            case EAST -> {
                buf.rotateCentered((float) Math.toRadians(90), Direction.UP);
                modelYawFixDeg = 180f;
            }
            case WEST -> {
                buf.rotateCentered((float) Math.toRadians(-90), Direction.UP);
                modelYawFixDeg = 180f;
            }
            case UP -> {
                buf.rotateCentered((float) Math.toRadians(-90), Direction.EAST);
                buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                modelYawFixDeg = 0f;
            }
            case DOWN -> {
                buf.rotateCentered((float) Math.toRadians(90), Direction.EAST);
                buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                modelYawFixDeg = 0f;
            }
        }

        if (modelYawFixDeg != 0f) {
            buf.rotateCentered((float) Math.toRadians(modelYawFixDeg), Direction.UP);
        }
    }
}
