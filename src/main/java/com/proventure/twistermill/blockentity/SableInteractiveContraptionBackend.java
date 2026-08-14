package com.proventure.twistermill.blockentity;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.proventure.twistermill.util.ServoTwoAxisRotationMath;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.RotaryConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

final class SableInteractiveContraptionBackend {
    enum RotationProfile {
        FACING_AXIS(0),
        UP_PITCH_X(1),
        TWO_AXIS_TILT(2);

        private final int storedId;

        RotationProfile(int storedId) {
            this.storedId = storedId;
        }

        int storedId() {
            return storedId;
        }

        static RotationProfile fromStoredId(int storedId) {
            for (RotationProfile profile : values()) {
                if (profile.storedId == storedId) {
                    return profile;
                }
            }
            return FACING_AXIS;
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TagKey<Block> TWISTERMILL_SAIL_LIKE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("twistermill", "sail_like"));
    private static final Set<Block> VANILLA_WOOL_BLOCKS = Set.of(
            Blocks.WHITE_WOOL,
            Blocks.ORANGE_WOOL,
            Blocks.MAGENTA_WOOL,
            Blocks.LIGHT_BLUE_WOOL,
            Blocks.YELLOW_WOOL,
            Blocks.LIME_WOOL,
            Blocks.PINK_WOOL,
            Blocks.GRAY_WOOL,
            Blocks.LIGHT_GRAY_WOOL,
            Blocks.CYAN_WOOL,
            Blocks.PURPLE_WOOL,
            Blocks.BLUE_WOOL,
            Blocks.BROWN_WOOL,
            Blocks.GREEN_WOOL,
            Blocks.RED_WOOL,
            Blocks.BLACK_WOOL
    );
    private static final double CONSTRAINT_ANCHOR_NUDGE = 1.0E-3;
    private static final float ANGLE_STEP_DEGREES = 0.05F; //settings for contraption
    private static final float MODE3_DISASSEMBLY_PHYSICAL_ZERO_LIMIT_DEGREES = 2.0F;
    private static final double WORLD_LOCK_PROJECTION_EPSILON = 1.0E-8;
    private static final double DIAGNOSTIC_ANCHOR_ERROR_THRESHOLD = 0.125D;
    private static final double DIAGNOSTIC_NORMAL_ERROR_THRESHOLD = 0.01D;
    private static final double MANUAL_RESEAT_ANCHOR_ERROR_LIMIT = 2.0D;
    private static final double MANUAL_RESEAT_NORMAL_ERROR_LIMIT = 0.25D;
    private static final double DIAGNOSTIC_NON_ZERO_VELOCITY_EPSILON = 1.0E-6D;
    private static final int BACKEND_RESOLVE_FAILURE_LOG_INTERVAL_TICKS = 200;

    private boolean active;
    @Nullable
    private UUID subLevelId;
    @Nullable
    private transient ServerSubLevel subLevel;
    @Nullable
    private transient PhysicsConstraintHandle constraintHandle;
    @Nullable
    private transient RotationProfile constraintRotationProfile;
    private transient boolean constraintReattachPending;
    private transient RotationProfile pendingReattachProfile = RotationProfile.FACING_AXIS;
    @Nullable
    private transient UUID constraintBaseSubLevelId;
    private transient boolean constraintBaseInitialized;
    private transient boolean constraintBaseWasRoot;
    @Nullable
    private transient UUID diagnosticLastBaseSubLevelId;
    private transient boolean diagnosticLastBaseInitialized;
    private transient boolean diagnosticLastBaseWasRoot;
    @Nullable
    private transient UUID diagnosticLastResolveFailureSubLevelId;
    @Nullable
    private transient String diagnosticLastResolveFailureReason;
    private transient long diagnosticLastResolveFailureLogTick = Long.MIN_VALUE;
    private final transient Vector3d twoAxisInertiaAxisScratch = new Vector3d();
    private final transient Vector3d twoAxisInertiaTransformScratch = new Vector3d();
    private final TwisterMillDiagnostics.Target diagnosticsTarget;

    SableInteractiveContraptionBackend(TwisterMillDiagnostics.Target diagnosticsTarget) {
        this.diagnosticsTarget = diagnosticsTarget;
    }

    boolean isActive() {
        return active;
    }

    boolean requiresConstraintAttachment(RotationProfile rotationProfile) {
        return constraintHandle == null
                || !constraintHandle.isValid()
                || constraintRotationProfile != rotationProfile;
    }

    @Nullable
    UUID getActiveSubLevelId() {
        if (!active || subLevelId == null) {
            return null;
        }
        return subLevelId;
    }

    double measureFacingAxisRelativeAngularVelocityRadiansPerSecond(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing
    ) {
        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        ServerSubLevel attachedSubLevel = resolved.subLevel();
        if (attachedSubLevel == null
                || constraintHandle == null
                || !constraintHandle.isValid()
                || constraintRotationProfile != RotationProfile.FACING_AXIS) {
            return 0.0D;
        }

        BlockPos constraintPivot = constraintPivotBlock(bearingPos, facing, RotationProfile.FACING_AXIS);
        ServerSubLevel baseSubLevel = resolveBaseSubLevel(serverLevel, constraintPivot);
        if (!isConstraintBaseCurrent(baseSubLevel)) {
            return 0.0D;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return 0.0D;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        try {
            Vector3d relativeAngularVelocity = pipeline.getAngularVelocity(attachedSubLevel, new Vector3d());
            Vector3d axisWorld = axisFromFacing(facing);

            if (baseSubLevel != null) {
                Vector3d baseAngularVelocity = pipeline.getAngularVelocity(baseSubLevel, new Vector3d());
                if (!isFiniteVector(baseAngularVelocity)) {
                    return 0.0D;
                }
                relativeAngularVelocity.sub(baseAngularVelocity);
                axisWorld = baseSubLevel.logicalPose().transformNormal(axisWorld, new Vector3d());
            }

            if (!isFiniteVector(relativeAngularVelocity)
                    || !isFiniteVector(axisWorld)
                    || axisWorld.lengthSquared() <= 1.0E-12D) {
                return 0.0D;
            }

            double angularVelocityAlongAxis = relativeAngularVelocity.dot(axisWorld.normalize());
            return Double.isFinite(angularVelocityAlongAxis) ? angularVelocityAlongAxis : 0.0D;
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    @Nullable
    Float measureFacingAxisRelativeAngleDegrees(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing
    ) {
        return measureBearingAxisRelativeAngleDegrees(
                serverLevel,
                bearingPos,
                facing,
                RotationProfile.FACING_AXIS
        );
    }

    @Nullable
    Float measureBearingAxisRelativeAngleDegrees(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing
    ) {
        return measureBearingAxisRelativeAngleDegrees(serverLevel, bearingPos, facing, null);
    }

    @Nullable
    private Float measureBearingAxisRelativeAngleDegrees(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            @Nullable RotationProfile requiredProfile
    ) {
        try {
            ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
            RotationProfile measuredProfile = constraintRotationProfile;
            if (attachedSubLevel == null
                    || constraintHandle == null
                    || !constraintHandle.isValid()
                    || (measuredProfile != RotationProfile.FACING_AXIS
                    && measuredProfile != RotationProfile.TWO_AXIS_TILT)
                    || (requiredProfile != null && measuredProfile != requiredProfile)) {
                return null;
            }

            BlockPos constraintPivot = constraintPivotBlock(
                    bearingPos,
                    facing,
                    measuredProfile
            );
            Mode3RestoreContext constraintBaseContext =
                    resolveMode3RestoreContext(serverLevel, attachedSubLevel, constraintPivot);
            if (!constraintBaseContext.resolved()
                    || !isConstraintBaseCurrent(constraintBaseContext.parentSubLevel())) {
                return null;
            }

            Mode3RestoreContext poseContext =
                    resolveMode3RestoreContext(serverLevel, attachedSubLevel, bearingPos);
            if (!poseContext.resolved()) {
                return null;
            }

            Pose3d topPose = new Pose3d(attachedSubLevel.logicalPose());
            Pose3d parentPose = poseContext.parentSubLevel() == null
                    ? new Pose3d()
                    : new Pose3d(poseContext.parentSubLevel().logicalPose());
            if (!isFiniteUnitScalePose(topPose) || !isFiniteUnitScalePose(parentPose)) {
                return null;
            }

            double angleDegrees = measureFacingAxisAngleDegrees(parentPose, topPose, facing);
            if (!Double.isFinite(angleDegrees)) {
                return null;
            }

            float wrappedAngleDegrees = wrapDegrees((float) angleDegrees);
            return Float.isFinite(wrappedAngleDegrees) ? wrappedAngleDegrees : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    // Reserved diagnostics hook for command/debug integrations outside the normal tick path.
    @SuppressWarnings({"unused", "UnstableApiUsage"})
    RuntimeDiagnosticsSnapshot runtimeDiagnosticsSnapshot(ServerLevel serverLevel) {
        UUID activeId = getActiveSubLevelId();
        if (!active) {
            return new RuntimeDiagnosticsSnapshot(false, false, activeId, null, null, null, 0.0D);
        }

        ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
        if (attachedSubLevel == null) {
            return new RuntimeDiagnosticsSnapshot(true, false, activeId, null, null, null, 0.0D);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        PhysicsPipeline pipeline = container == null ? null : container.physicsSystem().getPipeline();
        Vector3d linearVelocity = pipeline == null
                ? new Vector3d(attachedSubLevel.latestLinearVelocity)
                : readLinearVelocity(pipeline, attachedSubLevel);
        Vector3d angularVelocity = pipeline == null
                ? new Vector3d(attachedSubLevel.latestAngularVelocity)
                : readAngularVelocity(pipeline, attachedSubLevel);

        return new RuntimeDiagnosticsSnapshot(
                true,
                true,
                activeId,
                attachedSubLevel.getUniqueId(),
                new Vector3d(linearVelocity),
                new Vector3d(angularVelocity),
                angularVelocity.length()
        );
    }

    ReloadStabilizationResult stabilizeReloadReattach(ServerLevel serverLevel, BlockPos bearingPos, Direction facing) {
        UUID activeId = getActiveSubLevelId();
        if (activeId == null) {
            return ReloadStabilizationResult.skipped("inactive", null, null);
        }

        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        ServerSubLevel attachedSubLevel = resolved.subLevel();
        if (attachedSubLevel == null) {
            return ReloadStabilizationResult.skipped("resolve-" + resolved.failureReason(), activeId, null);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return ReloadStabilizationResult.skipped("container-unavailable", activeId, attachedSubLevel.getUniqueId());
        }
        if (constraintHandle == null || !constraintHandle.isValid()) {
            return ReloadStabilizationResult.skipped("constraint-invalid", activeId, attachedSubLevel.getUniqueId());
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        Vector3d linearVelocityBefore = readLinearVelocity(pipeline, attachedSubLevel);
        Vector3d angularVelocityBefore = readAngularVelocity(pipeline, attachedSubLevel);

        BlockPos anchorWorld = bearingPos.relative(facing);
        DiagnosticConstraint diagnostic = buildDiagnosticConstraintConfiguration(serverLevel, attachedSubLevel, anchorWorld, facing);
        DiagnosticFrame frame = diagnostic == null
                ? null
                : computeDiagnosticFrame(diagnostic.baseSubLevel(), attachedSubLevel, diagnostic.configuration());
        boolean thresholdBreach = frame != null && isDiagnosticThresholdBreach(frame);
        Vector3dc centerOfMass = attachedSubLevel.getMassTracker().getCenterOfMass();
        Vector3d comToContactLocal = null;
        if (diagnostic != null && centerOfMass != null) {
            comToContactLocal = new Vector3d(centerOfMass)
                    .sub(diagnostic.configuration().pos2());
        }

        pipeline.resetVelocity(attachedSubLevel);
        pipeline.wakeUp(attachedSubLevel);

        return new ReloadStabilizationResult(
                "velocity-reset",
                true,
                false,
                activeId,
                attachedSubLevel.getUniqueId(),
                new Vector3d(linearVelocityBefore),
                new Vector3d(angularVelocityBefore),
                readLinearVelocity(pipeline, attachedSubLevel),
                readAngularVelocity(pipeline, attachedSubLevel),
                frame == null ? Double.NaN : frame.anchorWorldError(),
                frame == null ? Double.NaN : frame.normalWorldError(),
                frame == null ? Double.NaN : frame.anchorWorldError(),
                frame == null ? Double.NaN : frame.normalWorldError(),
                thresholdBreach,
                comToContactLocal == null ? null : new Vector3d(comToContactLocal),
                null,
                null,
                frame == null ? null : computeAnchorToComWorld(attachedSubLevel.logicalPose(), centerOfMass, frame),
                frame == null ? null : computeAnchorToComWorld(attachedSubLevel.logicalPose(), centerOfMass, frame)
        );
    }

    ReloadStabilizationResult reseatAttachedSubLevel(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia,
            String actionPrefix
    ) {
        return reseatAttachedSubLevel(serverLevel, bearingPos, facing, RotationProfile.FACING_AXIS,
                stiffnessPerInertia, dampingPerInertia, minEffectiveInertia, actionPrefix);
    }

    ReloadStabilizationResult reseatAttachedSubLevel(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            RotationProfile rotationProfile,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia,
            String actionPrefix
    ) {
        UUID activeId = getActiveSubLevelId();
        if (activeId == null) {
            return ReloadStabilizationResult.skipped("inactive", null, null);
        }

        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        ServerSubLevel attachedSubLevel = resolved.subLevel();
        if (attachedSubLevel == null) {
            return ReloadStabilizationResult.skipped("resolve-" + resolved.failureReason(), activeId, null);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return ReloadStabilizationResult.skipped("container-unavailable", activeId, attachedSubLevel.getUniqueId());
        }
        if (constraintHandle == null || !constraintHandle.isValid()) {
            return ReloadStabilizationResult.skipped("constraint-invalid", activeId, attachedSubLevel.getUniqueId());
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        Vector3d linearVelocityBefore = readLinearVelocity(pipeline, attachedSubLevel);
        Vector3d angularVelocityBefore = readAngularVelocity(pipeline, attachedSubLevel);
        Pose3dc poseBefore = attachedSubLevel.logicalPose();
        String poseBeforeText = formatPose(poseBefore);
        Vector3dc centerOfMass = attachedSubLevel.getMassTracker().getCenterOfMass();
        if (centerOfMass == null) {
            return ReloadStabilizationResult.skipped(
                    actionPrefix + "-center-of-mass-unavailable",
                    activeId,
                    attachedSubLevel.getUniqueId()
            );
        }

        DiagnosticConstraint diagnostic = buildDiagnosticConstraintConfiguration(serverLevel, attachedSubLevel,
                bearingPos, facing, rotationProfile);
        DiagnosticFrame frame = diagnostic == null
                ? null
                : computeDiagnosticFrame(diagnostic.baseSubLevel(), attachedSubLevel, diagnostic.configuration());
        boolean thresholdBreach = frame != null && isDiagnosticThresholdBreach(frame);
        Vector3d comToContactLocal = null;
        if (diagnostic != null) {
            comToContactLocal = new Vector3d(centerOfMass)
                    .sub(diagnostic.configuration().pos2());
        }
        Vector3d anchorToComWorldBefore = frame == null
                ? null
                : computeAnchorToComWorld(poseBefore, centerOfMass, frame);

        String safetyFailure = reseatSafetyFailure(frame, actionPrefix);
        if (safetyFailure != null) {
            return new ReloadStabilizationResult(
                    safetyFailure,
                    false,
                    false,
                    activeId,
                    attachedSubLevel.getUniqueId(),
                    new Vector3d(linearVelocityBefore),
                    new Vector3d(angularVelocityBefore),
                    readLinearVelocity(pipeline, attachedSubLevel),
                    readAngularVelocity(pipeline, attachedSubLevel),
                    frame == null ? Double.NaN : frame.anchorWorldError(),
                    frame == null ? Double.NaN : frame.normalWorldError(),
                    frame == null ? Double.NaN : frame.anchorWorldError(),
                    frame == null ? Double.NaN : frame.normalWorldError(),
                    thresholdBreach,
                    comToContactLocal == null ? null : new Vector3d(comToContactLocal),
                    poseBeforeText,
                    formatPose(attachedSubLevel.logicalPose()),
                    anchorToComWorldBefore == null ? null : new Vector3d(anchorToComWorldBefore),
                    frame == null
                            ? null
                            : computeAnchorToComWorld(
                                    attachedSubLevel.logicalPose(),
                                    centerOfMass,
                                    frame)
            );
        }

        Pose3dc basePose = diagnostic.baseSubLevel() == null ? null : diagnostic.baseSubLevel().logicalPose();
        RotaryConstraintConfiguration configuration = diagnostic.configuration();
        Quaterniond targetOrientation = basePose == null
                ? new Quaterniond()
                : new Quaterniond(basePose.orientation());
        Vector3d baseAnchorWorld = basePose == null
                ? new Vector3d(configuration.pos1())
                : basePose.transformPosition(configuration.pos1(), new Vector3d());
        Vector3d rotationPoint = new Vector3d(centerOfMass);
        if (!isFiniteVector(rotationPoint)) {
            rotationPoint.set(configuration.pos2());
        }
        Vector3d anchorOffsetFromRotationPoint = new Vector3d(configuration.pos2()).sub(rotationPoint);
        targetOrientation.transform(anchorOffsetFromRotationPoint);
        Vector3d posePosition = new Vector3d(baseAnchorWorld).sub(anchorOffsetFromRotationPoint);

        removeConstraintHandle();

        Pose3d attachedPose = attachedSubLevel.logicalPose();
        attachedPose.position().set(posePosition);
        attachedPose.orientation().set(targetOrientation);
        attachedPose.rotationPoint().set(rotationPoint);
        attachedPose.scale().set(1.0D);

        pipeline.teleport(attachedSubLevel, attachedPose.position(), attachedPose.orientation());
        attachedPose.rotationPoint().set(rotationPoint);
        attachedPose.scale().set(1.0D);
        pipeline.resetVelocity(attachedSubLevel);
        pipeline.wakeUp(attachedSubLevel);
        attachedSubLevel.updateLastPose();

        boolean reattached = attachConstraint(serverLevel, attachedSubLevel, bearingPos, facing, rotationProfile);
        if (reattached && constraintHandle != null && constraintHandle.isValid()) {
            double effectiveInertia = computeEffectiveInertia(attachedSubLevel, facing, rotationProfile,
                    minEffectiveInertia);
            double stiffness = stiffnessPerInertia * effectiveInertia;
            double damping = dampingPerInertia * effectiveInertia;
            constraintHandle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS,
                    computeServoAngleRadians(facing, rotationProfile, 0.0F),
                    stiffness, damping, false, 0.0);
            constraintHandle.setContactsEnabled(false);
            pipeline.resetVelocity(attachedSubLevel);
            pipeline.wakeUp(attachedSubLevel);
            attachedSubLevel.updateLastPose();
        }

        DiagnosticConstraint diagnosticAfter = buildDiagnosticConstraintConfiguration(serverLevel, attachedSubLevel,
                bearingPos, facing, rotationProfile);
        DiagnosticFrame frameAfter = diagnosticAfter == null
                ? null
                : computeDiagnosticFrame(diagnosticAfter.baseSubLevel(), attachedSubLevel, diagnosticAfter.configuration());
        Vector3d anchorToComWorldAfter = frameAfter == null
                ? null
                : computeAnchorToComWorld(attachedSubLevel.logicalPose(), centerOfMass, frameAfter);

        return new ReloadStabilizationResult(
                reattached ? actionPrefix + "-zero-pose-reseat" : actionPrefix + "-constraint-reattach-failed",
                true,
                reattached,
                activeId,
                attachedSubLevel.getUniqueId(),
                new Vector3d(linearVelocityBefore),
                new Vector3d(angularVelocityBefore),
                readLinearVelocity(pipeline, attachedSubLevel),
                readAngularVelocity(pipeline, attachedSubLevel),
                frame.anchorWorldError(),
                frame.normalWorldError(),
                frameAfter == null ? Double.NaN : frameAfter.anchorWorldError(),
                frameAfter == null ? Double.NaN : frameAfter.normalWorldError(),
                thresholdBreach,
                comToContactLocal == null ? null : new Vector3d(comToContactLocal),
                poseBeforeText,
                formatPose(attachedSubLevel.logicalPose()),
                anchorToComWorldBefore == null ? null : new Vector3d(anchorToComWorldBefore),
                anchorToComWorldAfter == null ? null : new Vector3d(anchorToComWorldAfter)
        );
    }

    ReloadReattachDiagnosticsSnapshot reloadReattachDiagnosticsSnapshot(ServerLevel serverLevel, BlockPos bearingPos, Direction facing) {
        UUID activeId = getActiveSubLevelId();
        if (activeId == null) {
            return ReloadReattachDiagnosticsSnapshot.skipped("inactive", null, null);
        }

        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        ServerSubLevel attachedSubLevel = resolved.subLevel();
        if (attachedSubLevel == null) {
            return ReloadReattachDiagnosticsSnapshot.skipped("resolve-" + resolved.failureReason(), activeId, null);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return ReloadReattachDiagnosticsSnapshot.skipped("container-unavailable", activeId, attachedSubLevel.getUniqueId());
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        BlockPos anchorWorld = bearingPos.relative(facing);
        DiagnosticConstraint diagnostic = buildDiagnosticConstraintConfiguration(serverLevel, attachedSubLevel, anchorWorld, facing);
        DiagnosticFrame frame = diagnostic == null
                ? null
                : computeDiagnosticFrame(diagnostic.baseSubLevel(), attachedSubLevel, diagnostic.configuration());

        Pose3dc attachedLogicalPose = attachedSubLevel.logicalPose();
        Vector3dc centerOfMass = attachedSubLevel.getMassTracker().getCenterOfMass();
        Vector3d attachedCom = centerOfMass == null ? null : new Vector3d(centerOfMass);
        Vector3d attachedRotationPoint = new Vector3d(attachedLogicalPose.rotationPoint());
        Vector3d comToContactLocal = diagnostic == null
                || attachedCom == null
                ? null
                : new Vector3d(attachedCom).sub(diagnostic.configuration().pos2());
        Vector3d anchorToComWorld = null;
        if (frame != null && attachedCom != null) {
            Vector3d attachedComWorld = attachedLogicalPose.transformPosition(attachedCom, new Vector3d());
            anchorToComWorld = new Vector3d(attachedComWorld).sub(frame.baseAnchorWorld());
        }

        Vector3d linearVelocity = readLinearVelocity(pipeline, attachedSubLevel);
        Vector3d angularVelocity = readAngularVelocity(pipeline, attachedSubLevel);
        boolean thresholdBreach = frame != null && isDiagnosticThresholdBreach(frame);
        return new ReloadReattachDiagnosticsSnapshot(
                "snapshot",
                activeId,
                attachedSubLevel.getUniqueId(),
                "<unavailable>",
                constraintHandle != null && constraintHandle.isValid(),
                formatPose(attachedLogicalPose),
                formatPose(attachedSubLevel.lastPose()),
                "<unavailable>",
                frame == null ? null : new Vector3d(frame.baseAnchorWorld()),
                frame == null ? null : new Vector3d(frame.attachedAnchorWorld()),
                attachedCom,
                attachedRotationPoint,
                comToContactLocal,
                anchorToComWorld,
                linearVelocity,
                angularVelocity,
                linearVelocity.length() > DIAGNOSTIC_NON_ZERO_VELOCITY_EPSILON,
                angularVelocity.length() > DIAGNOSTIC_NON_ZERO_VELOCITY_EPSILON,
                frame == null ? Double.NaN : frame.anchorWorldError(),
                frame == null ? Double.NaN : frame.normalWorldError(),
                thresholdBreach
        );
    }

    void write(CompoundTag tag, String activeKey, String idKey) {
        tag.putBoolean(activeKey, active);
        if (subLevelId != null) {
            tag.putUUID(idKey, subLevelId);
        }
    }

    void read(CompoundTag tag, String activeKey, String idKey) {
        boolean containsActive = tag.contains(activeKey);
        Boolean rawActive = containsActive ? tag.getBoolean(activeKey) : null;
        boolean hasId = tag.hasUUID(idKey);
        UUID rawId = hasId ? tag.getUUID(idKey) : null;

        logBackendReadDiagnostics("read-before", activeKey, idKey, containsActive, rawActive, hasId, rawId,
                "before-read");

        active = tag.getBoolean(activeKey);
        subLevelId = rawId;
        clearRuntimeCache();

        if (!active) {
            clearState();
        }

        String readReason = !active
                ? "inactive-from-nbt"
                : subLevelId == null ? "active-without-sublevel-id" : "active-id-read";
        logBackendReadDiagnostics("read-after", activeKey, idKey, containsActive, rawActive, hasId, rawId,
                readReason);
    }

    void retainPersistedSubLevelForRecovery(UUID expectedSubLevelId) {
        if (expectedSubLevelId == null) {
            return;
        }
        if (subLevelId != null && !expectedSubLevelId.equals(subLevelId)) {
            return;
        }
        active = true;
        subLevelId = expectedSubLevelId;
        clearRuntimeCache();
    }

    void clearState() {
        logBackendClearStateDiagnostics();
        removeConstraintHandle();
        active = false;
        subLevelId = null;
        clearRuntimeCache();
    }

    void clearClientFallback() {
        clearState();
    }

    @Nullable
    AssemblyResult tryAssemble(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            @SuppressWarnings("SameParameterValue") boolean requiresWindmillSails,
            @Nullable Consumer<AssemblyException> exceptionConsumer,
            @Nullable RememberedSableShipMemory rememberedShipMemory
    ) {
        return tryAssemble(serverLevel, bearingPos, facing, requiresWindmillSails, exceptionConsumer,
                rememberedShipMemory, RotationProfile.FACING_AXIS);
    }

    @Nullable
    AssemblyResult tryAssemble(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            @SuppressWarnings("SameParameterValue") boolean requiresWindmillSails,
            @Nullable Consumer<AssemblyException> exceptionConsumer,
            @Nullable RememberedSableShipMemory rememberedShipMemory,
            RotationProfile rotationProfile
    ) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return null;
        }

        BearingContraption contraption = new TwisterMillBearingContraption(requiresWindmillSails, facing);
        Set<BlockPos> capturedBlocks = new LinkedHashSet<>();

        try {
            boolean assembled = contraption.assemble(serverLevel, bearingPos);
            if (!assembled && rememberedShipMemory == null) {
                return null;
            }
            if (assembled) {
                capturedBlocks.addAll(collectCapturedWorldBlocks(contraption));
            }
        } catch (AssemblyException e) {
            if (exceptionConsumer != null) {
                exceptionConsumer.accept(e);
            }
            return null;
        }

        if (rememberedShipMemory != null) {
            capturedBlocks = new LinkedHashSet<>(
                    rememberedShipMemory.collectAssemblyCandidates(serverLevel, bearingPos, facing, capturedBlocks));
        }
        if (capturedBlocks.isEmpty()) {
            return null;
        }
        if (exceptionConsumer != null) {
            exceptionConsumer.accept(null);
        }

        BoundingBox3i bounds = BoundingBox3i.from(capturedBlocks);
        if (bounds == null) {
            return null;
        }

        bounds.expand(1, 1, 1);
        BlockPos anchorWorld = bearingPos.relative(facing);

        ServerSubLevel assembledSubLevel;
        try {
            assembledSubLevel = SubLevelAssemblyHelper.assembleBlocks(serverLevel, anchorWorld, capturedBlocks, bounds);
        } catch (Exception ignored) {
            return null;
        }

        return finishAssembly(serverLevel, bearingPos, facing, assembledSubLevel, capturedBlocks.size(),
                rotationProfile);
    }

    @Nullable
    private AssemblyResult finishAssembly(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            @Nullable ServerSubLevel assembledSubLevel,
            int blockCount,
            RotationProfile rotationProfile
    ) {
        if (assembledSubLevel == null || assembledSubLevel.getMassTracker().isInvalid()) {
            cleanupFailedAssembly(serverLevel, assembledSubLevel, bearingPos);
            return null;
        }

        if (!activate(assembledSubLevel, serverLevel, bearingPos, facing, rotationProfile)) {
            cleanupFailedAssembly(serverLevel, assembledSubLevel, bearingPos);
            return null;
        }

        return new AssemblyResult(blockCount);
    }

    boolean refresh(ServerLevel serverLevel, BlockPos bearingPos, Direction facing) {
        return refreshDetailed(serverLevel, bearingPos, facing, RotationProfile.FACING_AXIS).success();
    }

    RefreshResult refreshDetailed(ServerLevel serverLevel, BlockPos bearingPos, Direction facing) {
        return refreshDetailed(serverLevel, bearingPos, facing, RotationProfile.FACING_AXIS);
    }

    RefreshResult refreshDetailed(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            RotationProfile rotationProfile
    ) {
        if (!active) {
            return RefreshResult.failed(RefreshFailureReason.INACTIVE);
        }

        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        if (resolved.subLevel() == null) {
            return RefreshResult.failed(resolved.failureReason());
        }

        if (!ensureConstraintAttached(serverLevel, resolved.subLevel(), bearingPos, facing, rotationProfile)) {
            return RefreshResult.failed(constraintReattachPending
                    ? RefreshFailureReason.CONSTRAINT_REATTACH_PENDING
                    : RefreshFailureReason.CONSTRAINT_ATTACH_FAILED);
        }

        return RefreshResult.ok();
    }

    TwoAxisRecoveryRefreshResult refreshTwoAxisFromLoadedPoseDetailed(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia,
            float physicalToleranceDegrees
    ) {
        if (!active) {
            return TwoAxisRecoveryRefreshResult.failed(
                    VerifiedMotorApplyStatus.INVALID,
                    RefreshFailureReason.INACTIVE
            );
        }

        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        ServerSubLevel attachedSubLevel = resolved.subLevel();
        if (attachedSubLevel == null) {
            return TwoAxisRecoveryRefreshResult.failed(
                    mapVerifiedMotorFailure(resolved.failureReason()),
                    resolved.failureReason()
            );
        }

        Mode3RestoreContext poseContext = resolveMode3RestoreContext(serverLevel, attachedSubLevel, bearingPos);
        if (!poseContext.resolved()) {
            return TwoAxisRecoveryRefreshResult.failed(
                    VerifiedMotorApplyStatus.RETRYABLE_UNRESOLVED,
                    RefreshFailureReason.PARENT_SUBLEVEL_NOT_READY
            );
        }

        Pose3d topPose = new Pose3d(attachedSubLevel.logicalPose());
        Pose3d parentPose = poseContext.parentSubLevel() == null
                ? new Pose3d()
                : new Pose3d(poseContext.parentSubLevel().logicalPose());
        if (!isFiniteUnitScalePose(topPose) || !isFiniteUnitScalePose(parentPose)) {
            return TwoAxisRecoveryRefreshResult.failed(
                    VerifiedMotorApplyStatus.INVALID,
                    RefreshFailureReason.INVALID_TWO_AXIS_POSE
            );
        }

        TwoAxisAngles measured = measureTwoAxisAnglesDegrees(parentPose, topPose, facing);
        if (!isRepresentableTwoAxisRecoveryPose(measured, physicalToleranceDegrees)) {
            return TwoAxisRecoveryRefreshResult.failed(
                    VerifiedMotorApplyStatus.INVALID,
                    RefreshFailureReason.INVALID_TWO_AXIS_POSE
            );
        }

        PhysicsConstraintHandle previousHandle = constraintHandle;
        if (!ensureConstraintAttached(
                serverLevel,
                attachedSubLevel,
                bearingPos,
                facing,
                RotationProfile.TWO_AXIS_TILT
        )) {
            RefreshFailureReason failureReason = constraintReattachPending
                    ? RefreshFailureReason.CONSTRAINT_REATTACH_PENDING
                    : RefreshFailureReason.CONSTRAINT_ATTACH_FAILED;
            return TwoAxisRecoveryRefreshResult.failed(
                    mapVerifiedMotorFailure(failureReason),
                    failureReason
            );
        }

        if (constraintHandle == previousHandle) {
            return TwoAxisRecoveryRefreshResult.ready(null);
        }

        try {
            if (!applyTwoAxisMotorsToCurrentConstraint(
                    serverLevel,
                    attachedSubLevel,
                    bearingPos,
                    facing,
                    measured.axis1Degrees(),
                    measured.axis2Degrees(),
                    stiffnessPerInertia,
                    dampingPerInertia,
                    minEffectiveInertia
            )) {
                removeConstraintHandle();
                return TwoAxisRecoveryRefreshResult.failed(
                        VerifiedMotorApplyStatus.RETRYABLE_REBIND,
                        RefreshFailureReason.CONSTRAINT_REATTACH_PENDING
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to initialize Sable TWO_AXIS_TILT motors from loaded pose at {}", bearingPos,
                    exception);
            removeConstraintHandle();
            return TwoAxisRecoveryRefreshResult.failed(
                    VerifiedMotorApplyStatus.RETRYABLE_REBIND,
                    RefreshFailureReason.CONSTRAINT_REATTACH_PENDING
            );
        }

        return TwoAxisRecoveryRefreshResult.ready(measured);
    }

    boolean applyMotor(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            float angleDegrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        return applyMotor(serverLevel, bearingPos, facing, RotationProfile.FACING_AXIS, angleDegrees,
                stiffnessPerInertia, dampingPerInertia, minEffectiveInertia);
    }

    boolean applyMotor(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            RotationProfile rotationProfile,
            float angleDegrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        return applyAngleMotor(
                serverLevel,
                bearingPos,
                facing,
                rotationProfile,
                angleDegrees,
                stiffnessPerInertia,
                dampingPerInertia,
                minEffectiveInertia
        );
    }

    VerifiedMotorApplyResult applyVerifiedFacingAxisMotor(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            UUID expectedSubLevelId,
            float angleDegrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        if (expectedSubLevelId == null
                || !expectedSubLevelId.equals(subLevelId)
                || !active) {
            return VerifiedMotorApplyResult.failed(VerifiedMotorApplyStatus.INVALID);
        }

        RefreshResult refreshResult = refreshDetailed(
                serverLevel,
                bearingPos,
                facing,
                RotationProfile.FACING_AXIS
        );
        if (!refreshResult.success()) {
            return VerifiedMotorApplyResult.failed(mapVerifiedMotorFailure(refreshResult.failureReason()));
        }

        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        if (resolved.subLevel() == null) {
            return VerifiedMotorApplyResult.failed(mapVerifiedMotorFailure(resolved.failureReason()));
        }
        if (!expectedSubLevelId.equals(resolved.subLevel().getUniqueId())) {
            return VerifiedMotorApplyResult.failed(VerifiedMotorApplyStatus.INVALID);
        }
        if (!hasCurrentFacingAxisConstraint(serverLevel, bearingPos, facing)) {
            return VerifiedMotorApplyResult.failed(VerifiedMotorApplyStatus.RETRYABLE_REBIND);
        }

        PhysicsConstraintHandle activeHandle = constraintHandle;
        boolean applied = applyMotor(
                serverLevel,
                bearingPos,
                facing,
                RotationProfile.FACING_AXIS,
                angleDegrees,
                stiffnessPerInertia,
                dampingPerInertia,
                minEffectiveInertia
        );
        if (!applied) {
            return VerifiedMotorApplyResult.failed(VerifiedMotorApplyStatus.RETRYABLE_REBIND);
        }

        ResolveSubLevelResult postApply = resolveSubLevelDetailed(serverLevel);
        if (postApply.subLevel() == null) {
            return VerifiedMotorApplyResult.failed(mapVerifiedMotorFailure(postApply.failureReason()));
        }
        if (!expectedSubLevelId.equals(postApply.subLevel().getUniqueId())) {
            return VerifiedMotorApplyResult.failed(VerifiedMotorApplyStatus.INVALID);
        }
        if (constraintHandle != activeHandle || !hasCurrentFacingAxisConstraint(serverLevel, bearingPos, facing)) {
            return VerifiedMotorApplyResult.failed(VerifiedMotorApplyStatus.RETRYABLE_REBIND);
        }

        return VerifiedMotorApplyResult.applied();
    }

    @Nullable
    Mode3ReturnMotorCommand applyMode3DisassemblyReturnMotor(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            float targetAngleDegrees,
            double stiffness
    ) {
        ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
        if (attachedSubLevel == null
                || constraintHandle == null
                || !constraintHandle.isValid()
                || constraintRotationProfile != RotationProfile.FACING_AXIS
                || !Float.isFinite(targetAngleDegrees)
                || !Double.isFinite(stiffness)
                || stiffness <= 0.0D) {
            return null;
        }

        BlockPos constraintPivot = constraintPivotBlock(bearingPos, facing, RotationProfile.FACING_AXIS);
        Mode3RestoreContext baseContext = resolveMode3RestoreContext(serverLevel, attachedSubLevel, constraintPivot);
        if (!baseContext.resolved() || !isConstraintBaseCurrent(baseContext.parentSubLevel())) {
            return null;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return null;
        }

        float snappedTarget = snapAngle(targetAngleDegrees);
        if (!Float.isFinite(snappedTarget)) {
            return null;
        }
        if (snappedTarget == 0.0F) {
            snappedTarget = 0.0F;
        }

        double targetRadians = snappedTarget == 0.0F
                ? 0.0D
                : computeServoAngleRadians(facing, RotationProfile.FACING_AXIS, snappedTarget);
        double damping = 2.0D * Math.sqrt(stiffness);
        if (!Double.isFinite(targetRadians) || !Double.isFinite(damping)) {
            return null;
        }

        PhysicsConstraintHandle activeHandle = constraintHandle;
        activeHandle.setMotor(
                RotaryConstraintHandle.DEFAULT_AXIS,
                targetRadians,
                stiffness,
                damping,
                false,
                0.0D
        );
        activeHandle.setContactsEnabled(false);

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        pipeline.wakeUp(attachedSubLevel);
        if (baseContext.parentSubLevel() != null) {
            pipeline.wakeUp(baseContext.parentSubLevel());
        }

        return new Mode3ReturnMotorCommand(
                serverLevel.getGameTime(),
                attachedSubLevel.getUniqueId(),
                facing,
                activeHandle,
                snappedTarget,
                targetRadians
        );
    }

    boolean applyTwoAxisMotors(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            float axis1Degrees,
            float axis2Degrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
        if (attachedSubLevel == null
                || !ensureConstraintAttached(serverLevel, attachedSubLevel, bearingPos, facing,
                RotationProfile.TWO_AXIS_TILT)
                || constraintHandle == null
                || constraintRotationProfile != RotationProfile.TWO_AXIS_TILT) {
            return false;
        }

        return applyTwoAxisMotorsToCurrentConstraint(
                serverLevel,
                attachedSubLevel,
                bearingPos,
                facing,
                axis1Degrees,
                axis2Degrees,
                stiffnessPerInertia,
                dampingPerInertia,
                minEffectiveInertia
        );
    }

    private boolean applyTwoAxisMotorsToCurrentConstraint(
            ServerLevel serverLevel,
            ServerSubLevel attachedSubLevel,
            BlockPos bearingPos,
            Direction facing,
            float axis1Degrees,
            float axis2Degrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        PhysicsConstraintHandle activeHandle = constraintHandle;
        if (activeHandle == null
                || !activeHandle.isValid()
                || constraintRotationProfile != RotationProfile.TWO_AXIS_TILT) {
            return false;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return false;
        }

        float snappedAxis1 = snapAngle(axis1Degrees);
        float snappedAxis2 = snapAngle(axis2Degrees);
        double inertia1 = computeEffectiveInertia(
                attachedSubLevel,
                ServoTwoAxisRotationMath.setAxis1(facing, twoAxisInertiaAxisScratch),
                minEffectiveInertia,
                twoAxisInertiaTransformScratch
        );
        double inertia2 = computeEffectiveInertia(
                attachedSubLevel,
                ServoTwoAxisRotationMath.setAxis2(facing, twoAxisInertiaAxisScratch),
                minEffectiveInertia,
                twoAxisInertiaTransformScratch
        );
        activeHandle.setMotor(
                ConstraintJointAxis.ANGULAR_X,
                ServoTwoAxisRotationMath.motorTargetRadiansX(snappedAxis1, snappedAxis2),
                stiffnessPerInertia * inertia1,
                dampingPerInertia * inertia1,
                false,
                0.0D
        );
        activeHandle.setMotor(
                ConstraintJointAxis.ANGULAR_Z,
                ServoTwoAxisRotationMath.motorTargetRadiansZ(snappedAxis1, snappedAxis2),
                stiffnessPerInertia * inertia2,
                dampingPerInertia * inertia2,
                false,
                0.0D
        );
        activeHandle.setContactsEnabled(false);

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        pipeline.wakeUp(attachedSubLevel);
        SubLevel containing = Sable.HELPER.getContaining(serverLevel, bearingPos);
        if (containing instanceof ServerSubLevel containingSubLevel) {
            pipeline.wakeUp(containingSubLevel);
        }
        return true;
    }

    @Nullable
    Float measureCurrentMotorAngleDegrees(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            RotationProfile rotationProfile
    ) {
        ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
        if (attachedSubLevel == null) {
            return null;
        }

        Pose3dc attachedPose = attachedSubLevel.logicalPose();
        Pose3dc containingPose = new Pose3d();
        SubLevel containingSubLevel = Sable.HELPER.getContaining(serverLevel, bearingPos);
        if (containingSubLevel != null && !containingSubLevel.isRemoved()) {
            containingPose = containingSubLevel.logicalPose();
        }

        double angleDegrees;
        if (rotationProfile == RotationProfile.UP_PITCH_X) {
            Quaterniond relative = new Quaterniond(containingPose.orientation())
                    .conjugate()
                    .mul(new Quaterniond(attachedPose.orientation()))
                    .normalize();
            angleDegrees = 2.0D * Math.toDegrees(Math.atan2(relative.x(), relative.w()));
        } else {
            angleDegrees = measureFacingAxisAngleDegrees(containingPose, attachedPose, facing);
        }

        if (!Double.isFinite(angleDegrees)) {
            return null;
        }
        return wrapDegrees((float) angleDegrees);
    }

    private static double measureFacingAxisAngleDegrees(
            Pose3dc parentPose,
            Pose3dc topPose,
            Direction facing
    ) {
        Quaterniond facingRotation = new Quaterniond(facing.getRotation());
        Quaterniond relative = new Quaterniond(parentPose.orientation())
                .mul(facingRotation)
                .conjugate()
                .mul(new Quaterniond(topPose.orientation()).mul(facingRotation));
        double angleDegrees = -2.0D * Math.toDegrees(Math.atan2(-relative.y(), relative.w()));
        return servoMotorSign(facing) < 0.0D ? -angleDegrees : angleDegrees;
    }

    @Nullable
    TwoAxisAngles measureCurrentTwoAxisAnglesDegrees(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing
    ) {
        ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
        if (attachedSubLevel == null) {
            return null;
        }

        Pose3dc containingPose = new Pose3d();
        SubLevel containingSubLevel = Sable.HELPER.getContaining(serverLevel, bearingPos);
        if (containingSubLevel != null && !containingSubLevel.isRemoved()) {
            containingPose = containingSubLevel.logicalPose();
        }

        return measureTwoAxisAnglesDegrees(containingPose, attachedSubLevel.logicalPose(), facing);
    }

    @Nullable
    private static TwoAxisAngles measureTwoAxisAnglesDegrees(
            Pose3dc containingPose,
            Pose3dc attachedPose,
            Direction facing
    ) {
        Quaterniond canonicalFrame = ServoTwoAxisRotationMath.setCanonicalFrame(facing, new Quaterniond());
        Quaterniond parentFrame = new Quaterniond(containingPose.orientation()).mul(canonicalFrame);
        Quaterniond attachedFrame = new Quaterniond(attachedPose.orientation()).mul(canonicalFrame);
        Quaterniond relative = parentFrame.conjugate().mul(attachedFrame).normalize();
        if (relative.w() < 0.0D) {
            relative.set(-relative.x(), -relative.y(), -relative.z(), -relative.w());
        }

        double axis1Degrees = 2.0D * Math.toDegrees(Math.asin(clampUnit(relative.x())));
        double axis2Degrees = 2.0D * Math.toDegrees(Math.asin(clampUnit(relative.z())));
        double swingHalf = Math.min(1.0D, Math.hypot(relative.x(), relative.z()));
        double totalSwingDegrees = 2.0D * Math.toDegrees(Math.asin(swingHalf));
        double twistDegrees = 2.0D * Math.toDegrees(Math.atan2(relative.y(), relative.w()));
        if (!Double.isFinite(axis1Degrees) || !Double.isFinite(axis2Degrees)
                || !Double.isFinite(totalSwingDegrees) || !Double.isFinite(twistDegrees)) {
            return null;
        }
        return new TwoAxisAngles(
                (float) axis1Degrees,
                (float) axis2Degrees,
                (float) totalSwingDegrees,
                wrapDegrees((float) twistDegrees)
        );
    }

    private static boolean isRepresentableTwoAxisRecoveryPose(
            @Nullable TwoAxisAngles measured,
            float physicalToleranceDegrees
    ) {
        if (measured == null
                || !Float.isFinite(physicalToleranceDegrees)
                || physicalToleranceDegrees < 0.0F) {
            return false;
        }
        float maxAxisDegrees = ServoTwoAxisRotationMath.MAX_AXIS_DEGREES + physicalToleranceDegrees;
        return Math.abs(measured.axis1Degrees()) <= maxAxisDegrees
                && Math.abs(measured.axis2Degrees()) <= maxAxisDegrees
                && measured.totalSwingDegrees() <= maxAxisDegrees
                && Math.abs(measured.twistDegrees()) <= physicalToleranceDegrees;
    }

    boolean switchRotationProfileAtNeutral(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            RotationProfile currentProfile,
            RotationProfile requestedProfile,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        return switchRotationProfileAtNeutral(
                serverLevel,
                bearingPos,
                facing,
                currentProfile,
                requestedProfile,
                stiffnessPerInertia,
                dampingPerInertia,
                minEffectiveInertia,
                null
        );
    }

    boolean switchTwoAxisToFacingAxisForDisassemblyAtTiltNeutral(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            float currentBearingAngleDegrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        if (!Float.isFinite(currentBearingAngleDegrees)) {
            return false;
        }
        return switchRotationProfileAtNeutral(
                serverLevel,
                bearingPos,
                facing,
                RotationProfile.TWO_AXIS_TILT,
                RotationProfile.FACING_AXIS,
                stiffnessPerInertia,
                dampingPerInertia,
                minEffectiveInertia,
                currentBearingAngleDegrees
        );
    }

    private boolean switchRotationProfileAtNeutral(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            RotationProfile currentProfile,
            RotationProfile requestedProfile,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia,
            @Nullable Float requestedFacingAxisAngleDegrees
    ) {
        if (currentProfile == requestedProfile) {
            return constraintHandle != null && constraintHandle.isValid()
                    && constraintRotationProfile == currentProfile;
        }
        if (requestedProfile == RotationProfile.UP_PITCH_X && facing != Direction.UP) {
            return false;
        }

        ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (attachedSubLevel == null || container == null || constraintHandle == null
                || !constraintHandle.isValid() || constraintRotationProfile != currentProfile) {
            return false;
        }

        PreparedRotationConstraint requested = prepareRotationConstraint(serverLevel, attachedSubLevel, bearingPos, facing,
                requestedProfile);
        PreparedRotationConstraint previous = prepareRotationConstraint(serverLevel, attachedSubLevel, bearingPos, facing,
                currentProfile);
        if (!validatePreparedConstraint(container, attachedSubLevel, requested)
                || !validatePreparedConstraint(container, attachedSubLevel, previous)) {
            return false;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        removeConstraintHandle();

        if (attachPreparedConstraint(pipeline, attachedSubLevel, requested)) {
            try {
                if (requestedFacingAxisAngleDegrees != null) {
                    setFacingAxisMotorAtAngle(
                            constraintHandle,
                            attachedSubLevel,
                            facing,
                            requestedFacingAxisAngleDegrees,
                            stiffnessPerInertia,
                            dampingPerInertia,
                            minEffectiveInertia
                    );
                } else {
                    setMotorAtNeutral(constraintHandle, attachedSubLevel, facing, requestedProfile,
                            stiffnessPerInertia, dampingPerInertia, minEffectiveInertia);
                }
                pipeline.wakeUp(attachedSubLevel);
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to initialize Sable {} motor after profile switch at {}",
                        requestedProfile, bearingPos, e);
                removeConstraintHandle();
            }
        }

        if (attachPreparedConstraint(pipeline, attachedSubLevel, previous)) {
            try {
                setMotorAtNeutral(constraintHandle, attachedSubLevel, facing, currentProfile,
                        stiffnessPerInertia, dampingPerInertia, minEffectiveInertia);
                pipeline.wakeUp(attachedSubLevel);
                return false;
            } catch (Exception e) {
                LOGGER.error("Failed to initialize restored Sable {} motor at {}", currentProfile, bearingPos, e);
                removeConstraintHandle();
            }
        }

        constraintReattachPending = true;
        pendingReattachProfile = currentProfile;
        LOGGER.error("Sable rotation-profile switch {} -> {} failed at {}; previous constraint could not be restored. "
                        + "Keeping sublevel active and scheduling constraint reattach.",
                currentProfile, requestedProfile, bearingPos);
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validatePreparedConstraint(ServerSubLevelContainer container, ServerSubLevel attachedSubLevel,
            @Nullable PreparedRotationConstraint prepared) {
        if (prepared == null) {
            return false;
        }
        try {
            prepared.validate(container, attachedSubLevel);
            return true;
        } catch (Exception e) {
            LOGGER.error("Invalid Sable {} constraint configuration at {}", prepared.rotationProfile(),
                    prepared.constraintPivotBlock(), e);
            return false;
        }
    }

    private boolean attachPreparedConstraint(PhysicsPipeline pipeline, ServerSubLevel attachedSubLevel,
            PreparedRotationConstraint prepared) {
        try {
            PhysicsConstraintHandle handle = prepared.attach(pipeline, attachedSubLevel);
            if (handle == null || !handle.isValid()) {
                return false;
            }
            constraintHandle = handle;
            constraintRotationProfile = prepared.rotationProfile();
            rememberConstraintBase(prepared.baseSubLevel());
            constraintReattachPending = false;
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to attach prepared Sable {} constraint at {}", prepared.rotationProfile(),
                    prepared.constraintPivotBlock(), e);
            constraintHandle = null;
            constraintRotationProfile = null;
            clearConstraintBase();
            return false;
        }
    }

    private static void setMotorAtNeutral(
            @Nullable PhysicsConstraintHandle handle,
            ServerSubLevel attachedSubLevel,
            Direction facing,
            RotationProfile rotationProfile,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        if (handle == null || !handle.isValid()) {
            throw new IllegalStateException("Cannot initialize an invalid rotation constraint");
        }
        if (rotationProfile == RotationProfile.TWO_AXIS_TILT) {
            double inertia1 = computeEffectiveInertia(
                    attachedSubLevel, ServoTwoAxisRotationMath.setAxis1(facing, new Vector3d()), minEffectiveInertia);
            double inertia2 = computeEffectiveInertia(
                    attachedSubLevel, ServoTwoAxisRotationMath.setAxis2(facing, new Vector3d()), minEffectiveInertia);
            handle.setMotor(ConstraintJointAxis.ANGULAR_X, 0.0D,
                    stiffnessPerInertia * inertia1, dampingPerInertia * inertia1, false, 0.0D);
            handle.setMotor(ConstraintJointAxis.ANGULAR_Z, 0.0D,
                    stiffnessPerInertia * inertia2, dampingPerInertia * inertia2, false, 0.0D);
            handle.setContactsEnabled(false);
            return;
        }
        double inertia = computeEffectiveInertia(attachedSubLevel, facing, rotationProfile, minEffectiveInertia);
        handle.setMotor(
                RotaryConstraintHandle.DEFAULT_AXIS,
                computeServoAngleRadians(facing, rotationProfile, 0.0F),
                stiffnessPerInertia * inertia,
                dampingPerInertia * inertia,
                false,
                0.0D
        );
        handle.setContactsEnabled(false);
    }

    private static void setFacingAxisMotorAtAngle(
            @Nullable PhysicsConstraintHandle handle,
            ServerSubLevel attachedSubLevel,
            Direction facing,
            float angleDegrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        if (handle == null || !handle.isValid() || !Float.isFinite(angleDegrees)) {
            throw new IllegalStateException("Cannot initialize an invalid facing-axis motor target");
        }
        double targetRadians = computeServoAngleRadians(facing, RotationProfile.FACING_AXIS, angleDegrees);
        if (!Double.isFinite(targetRadians)) {
            throw new IllegalStateException("Cannot initialize a non-finite facing-axis motor target");
        }
        double inertia = computeEffectiveInertia(
                attachedSubLevel,
                facing,
                RotationProfile.FACING_AXIS,
                minEffectiveInertia
        );
        handle.setMotor(
                RotaryConstraintHandle.DEFAULT_AXIS,
                targetRadians,
                stiffnessPerInertia * inertia,
                dampingPerInertia * inertia,
                false,
                0.0D
        );
        handle.setContactsEnabled(false);
    }

    @Nullable
    Float computeWorldLockedMotorAngleDegrees(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            int modeSignal,
            float currentAngleDegrees
    ) {
        if (modeSignal != 4 && modeSignal != 5) {
            return null;
        }

        ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
        if (attachedSubLevel == null) {
            return null;
        }

        Vector3d bearingWorld = axisFromFacing(facing);
        if (bearingWorld.lengthSquared() <= WORLD_LOCK_PROJECTION_EPSILON) {
            return null;
        }
        bearingWorld.normalize();

        SubLevel containingSubLevel = Sable.HELPER.getContaining(serverLevel, bearingPos);
        if (containingSubLevel != null && !containingSubLevel.isRemoved()) {
            Pose3dc containingPose = containingSubLevel.logicalPose();
            bearingWorld = containingPose.transformNormal(bearingWorld, new Vector3d());
            if (bearingWorld.lengthSquared() <= WORLD_LOCK_PROJECTION_EPSILON) {
                return null;
            }
            bearingWorld.normalize();
        }

        Pose3dc attachedPose = attachedSubLevel.logicalPose();
        Vector3d currentReferenceWorld = attachedPose.transformNormal(worldLockReferenceAxisLocal(facing), new Vector3d());
        if (!projectOntoPlaneAndNormalize(currentReferenceWorld, bearingWorld)) {
            return null;
        }

        Vector3d desiredWorld = worldLockDesiredAxisWorld(facing, modeSignal, bearingWorld);
        if (desiredWorld == null || !projectOntoPlaneAndNormalize(desiredWorld, bearingWorld)) {
            return null;
        }

        Vector3d cross = currentReferenceWorld.cross(desiredWorld, new Vector3d());
        double sin = cross.dot(bearingWorld);
        double cos = clampUnit(currentReferenceWorld.dot(desiredWorld));
        double deltaRadians = Math.atan2(sin, cos);
        if (!Double.isFinite(deltaRadians)) {
            return null;
        }

        double motorSign = servoMotorSign(facing);
        float targetAngle = currentAngleDegrees + (float) Math.toDegrees(deltaRadians / motorSign);
        if (!Float.isFinite(targetAngle)) {
            return null;
        }
        return snapAngle(targetAngle);
    }

    private boolean applyAngleMotor(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            RotationProfile rotationProfile,
            float angleDegrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        ServerSubLevel resolvedSubLevel = resolveSubLevel(serverLevel);
        if (resolvedSubLevel == null) {
            return false;
        }

        if (!ensureConstraintAttached(serverLevel, resolvedSubLevel, bearingPos, facing, rotationProfile)
                || constraintHandle == null) {
            return false;
        }

        angleDegrees = snapAngle(angleDegrees);
        double goal = computeServoAngleRadians(facing, rotationProfile, angleDegrees);
        double effectiveInertia = computeEffectiveInertia(resolvedSubLevel, facing, rotationProfile,
                minEffectiveInertia);
        double stiffness = stiffnessPerInertia * effectiveInertia;
        double damping = dampingPerInertia * effectiveInertia;

        constraintHandle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, goal, stiffness, damping, false, 0.0);
        constraintHandle.setContactsEnabled(false);

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return false;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        pipeline.wakeUp(resolvedSubLevel);

        SubLevel containing = Sable.HELPER.getContaining(serverLevel, bearingPos);
        if (containing instanceof ServerSubLevel containingSubLevel) {
            pipeline.wakeUp(containingSubLevel);
        }

        return true;
    }

    @Nullable
    Mode3RelativePoseProbe sampleMode3RelativePose(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            Mode3ReturnMotorCommand command
    ) {
        ServerSubLevel attachedSubLevel = validateMode3ReturnCommand(
                serverLevel,
                bearingPos,
                facing,
                command,
                false
        );
        if (attachedSubLevel == null) {
            return null;
        }

        Mode3RestoreContext restoreContext =
                resolveMode3RestoreContext(serverLevel, attachedSubLevel, bearingPos);
        if (!restoreContext.resolved()) {
            return null;
        }

        Pose3d topPose = new Pose3d(attachedSubLevel.logicalPose());
        Pose3d parentPose = restoreContext.parentSubLevel() == null
                ? new Pose3d()
                : new Pose3d(restoreContext.parentSubLevel().logicalPose());
        if (!isFiniteUnitScalePose(topPose) || !isFiniteUnitScalePose(parentPose)) {
            return null;
        }

        var bounds = attachedSubLevel.getPlot().getBoundingBox();
        if (bounds.minX() > bounds.maxX()
                || bounds.minY() > bounds.maxY()
                || bounds.minZ() > bounds.maxZ()) {
            return null;
        }

        BlockPos plotCenter = attachedSubLevel.getPlot().getCenterBlock().immutable();
        BlockPos assemblyAnchor = bearingPos.relative(facing).immutable();
        double facingAxisAngleDegrees = measureFacingAxisAngleDegrees(parentPose, topPose, facing);
        if (!Double.isFinite(facingAxisAngleDegrees)) {
            return null;
        }

        return new Mode3RelativePoseProbe(
                serverLevel.getGameTime(),
                attachedSubLevel.getUniqueId(),
                restoreContext.parentSubLevel() == null
                        ? null
                        : restoreContext.parentSubLevel().getUniqueId(),
                restoreContext.rootParent(),
                plotCenter,
                assemblyAnchor,
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ(),
                topPose,
                restoreContext.parentSubLevel() == null ? null : parentPose,
                wrapDegrees((float) facingAxisAngleDegrees)
        );
    }

    Mode3PosePrecheck precheckMode3DisassemblyPose(
            @Nullable Mode3RelativePoseProbe previous,
            Mode3RelativePoseProbe current
    ) {
        if (!isFiniteMode3Probe(current)) {
            return Mode3PosePrecheck.failed("pose-sample-nonfinite");
        }
        if (previous != null) {
            if (!isSameMode3ProbeContext(previous, current)) {
                return Mode3PosePrecheck.failed("pose-context-changed");
            }
            if (current.gameTime() != previous.gameTime() + 1L) {
                return Mode3PosePrecheck.failed("pose-samples-not-consecutive");
            }
            if (!isFiniteMode3Probe(previous)) {
                return Mode3PosePrecheck.failed("pose-sample-nonfinite");
            }
        }

        float currentAngleDegrees = current.facingAxisAngleDegrees();
        if (!Float.isFinite(currentAngleDegrees)) {
            return Mode3PosePrecheck.failed("facing-axis-angle-nonfinite");
        }

        boolean physicalAngleWithinLimit =
                Math.abs(currentAngleDegrees) <= MODE3_DISASSEMBLY_PHYSICAL_ZERO_LIMIT_DEGREES;
        return new Mode3PosePrecheck(
                physicalAngleWithinLimit,
                physicalAngleWithinLimit ? "" : "facing-axis-angle-not-zero",
                currentAngleDegrees,
                physicalAngleWithinLimit
        );
    }

    Mode3DisassemblySafety inspectMode3DisassemblySafety(
            ServerLevel serverLevel,
            BlockPos protectedPos,
            Direction facing,
            Mode3ReturnMotorCommand command,
            @Nullable Mode3RelativePoseProbe previous,
            Mode3RelativePoseProbe current,
            Mode3PosePrecheck precheck
    ) {
        if (!precheck.eligible()) {
            return Mode3DisassemblySafety.failed(precheck.failureReason(), current, precheck);
        }

        Mode3RelativePoseProbe liveCurrent =
                sampleMode3RelativePose(serverLevel, protectedPos, facing, command);
        if (liveCurrent == null) {
            return Mode3DisassemblySafety.failed("live-pose-unavailable", current, precheck);
        }
        if (!isSameMode3Probe(current, liveCurrent)) {
            return Mode3DisassemblySafety.failed("live-pose-changed", liveCurrent, precheck);
        }

        Mode3PosePrecheck livePrecheck = precheckMode3DisassemblyPose(previous, liveCurrent);
        if (!livePrecheck.eligible()) {
            return Mode3DisassemblySafety.failed(
                    livePrecheck.failureReason(),
                    liveCurrent,
                    livePrecheck
            );
        }

        ServerSubLevel attachedSubLevel = validateMode3ReturnCommand(
                serverLevel,
                protectedPos,
                facing,
                command,
                true
        );
        if (attachedSubLevel == null) {
            return Mode3DisassemblySafety.failed(
                    "motor-command-no-longer-current",
                    liveCurrent,
                    livePrecheck
            );
        }

        SubLevelAssemblyHelper.AssemblyTransform expectedTransform =
                new SubLevelAssemblyHelper.AssemblyTransform(
                        liveCurrent.plotCenter(),
                        liveCurrent.assemblyAnchor(),
                        0,
                        Rotation.NONE,
                        serverLevel
                );
        Set<BlockPos> actualTargets = new LinkedHashSet<>();
        boolean mappingMatchesExpected = true;
        boolean actualTargetsUnique = true;
        boolean protectedPositionClear = true;
        int sourceBlockCount = 0;

        for (BlockPos sourcePos : BlockPos.betweenClosedStream(
                attachedSubLevel.getPlot().getBoundingBox().toMojang()
        ).map(BlockPos::immutable).toList()) {
            if (serverLevel.getBlockState(sourcePos).isAir()) {
                continue;
            }

            sourceBlockCount++;
            Vector3d sourceCenter = JOMLConversion.atCenterOf(sourcePos);
            Vector3d actualCenter = transformMode3RestorePosition(liveCurrent, sourceCenter);
            if (!isFiniteVector(actualCenter)) {
                return Mode3DisassemblySafety.failed(
                        "block-transform-nonfinite",
                        liveCurrent,
                        livePrecheck
                );
            }

            BlockPos actualTarget = BlockPos.containing(
                    actualCenter.x,
                    actualCenter.y,
                    actualCenter.z
            );
            BlockPos expectedTarget = expectedTransform.apply(sourcePos);

            mappingMatchesExpected &= actualTarget.equals(expectedTarget);
            actualTargetsUnique &= actualTargets.add(actualTarget.immutable());
            protectedPositionClear &= !actualTarget.equals(protectedPos)
                    && !expectedTarget.equals(protectedPos);
        }

        int uniqueActualTargetCount = actualTargets.size();
        boolean sourceAndTargetCountsMatch =
                sourceBlockCount > 0 && uniqueActualTargetCount == sourceBlockCount;
        boolean allFinite = isFiniteMode3Probe(liveCurrent)
                && Float.isFinite(livePrecheck.actualAngleDegrees());
        boolean motorTargetExactlyZero = isExactPositiveZero(command.targetAngleDegrees())
                && isExactPositiveZero(command.targetRadians());
        boolean safe = motorTargetExactlyZero
                && allFinite
                && livePrecheck.physicalAngleWithinLimit()
                && mappingMatchesExpected
                && actualTargetsUnique
                && sourceAndTargetCountsMatch
                && protectedPositionClear;

        String failureReason;
        if (safe) {
            failureReason = "";
        } else if (!motorTargetExactlyZero) {
            failureReason = "motor-target-not-zero";
        } else if (!allFinite) {
            failureReason = "safety-metric-nonfinite";
        } else if (!livePrecheck.physicalAngleWithinLimit()) {
            failureReason = "facing-axis-angle-not-zero";
        } else if (!mappingMatchesExpected) {
            failureReason = "restore-mapping-mismatch";
        } else if (!actualTargetsUnique) {
            failureReason = "restore-target-collision";
        } else if (!sourceAndTargetCountsMatch) {
            failureReason = "restore-target-count-mismatch";
        } else if (!protectedPositionClear) {
            failureReason = "protected-bearing-position-collision";
        } else {
            failureReason = "mode3-disassembly-safety-rejected";
        }

        return new Mode3DisassemblySafety(
                safe,
                failureReason,
                liveCurrent,
                motorTargetExactlyZero,
                true,
                true,
                allFinite,
                livePrecheck.physicalAngleWithinLimit(),
                mappingMatchesExpected,
                actualTargetsUnique,
                sourceAndTargetCountsMatch,
                protectedPositionClear,
                sourceBlockCount,
                uniqueActualTargetCount,
                livePrecheck.actualAngleDegrees()
        );
    }

    @Nullable
    private static String reseatSafetyFailure(@Nullable DiagnosticFrame frame, String actionPrefix) {
        if (frame == null) {
            return actionPrefix + "-frame-unavailable";
        }
        if (!Double.isFinite(frame.anchorWorldError()) || !Double.isFinite(frame.normalWorldError())) {
            return actionPrefix + "-frame-nonfinite";
        }
        if (frame.anchorWorldError() > MANUAL_RESEAT_ANCHOR_ERROR_LIMIT) {
            return actionPrefix + "-anchor-error-too-large";
        }
        if (frame.normalWorldError() > MANUAL_RESEAT_NORMAL_ERROR_LIMIT) {
            return actionPrefix + "-normal-error-too-large";
        }
        return null;
    }

    boolean disassemble(ServerLevel serverLevel, BlockPos protectedPos) {
        if (!active) {
            return false;
        }

        ServerSubLevel resolved = resolveSubLevel(serverLevel);
        if (resolved == null) {
            clearState();
            return false;
        }

        removeConstraintHandle();

        ServerSubLevel parentSubLevel = resolveParentRestoreSubLevel(serverLevel, resolved, protectedPos);
        if (parentSubLevel != null) {
            restoreSubLevelToParent(serverLevel, resolved, parentSubLevel, protectedPos);
        } else {
            restoreSubLevelToWorld(serverLevel, resolved, protectedPos);
        }
        return true;
    }

    boolean disassembleVerifiedFacingAxis(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            UUID expectedSubLevelId
    ) {
        if (!active
                || expectedSubLevelId == null
                || !expectedSubLevelId.equals(subLevelId)) {
            return false;
        }

        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        if (resolved.subLevel() == null
                || !expectedSubLevelId.equals(resolved.subLevel().getUniqueId())
                || !hasCurrentFacingAxisConstraint(serverLevel, bearingPos, facing)) {
            return false;
        }

        removeConstraintHandle();
        ServerSubLevel parentSubLevel = resolveParentRestoreSubLevel(serverLevel, resolved.subLevel(), bearingPos);
        if (parentSubLevel != null) {
            restoreSubLevelToParent(serverLevel, resolved.subLevel(), parentSubLevel, bearingPos);
        } else {
            restoreSubLevelToWorld(serverLevel, resolved.subLevel(), bearingPos);
        }
        return true;
    }

    Set<BlockPos> snapshotRestoredBlockPositions(ServerLevel serverLevel, BlockPos protectedPos) {
        ServerSubLevel resolved = resolveSubLevel(serverLevel);
        if (resolved == null) {
            return Collections.emptySet();
        }

        ServerSubLevel parentSubLevel = resolveParentRestoreSubLevel(serverLevel, resolved, protectedPos);
        Set<BlockPos> restoredPositions = new LinkedHashSet<>();
        for (BlockPos sourcePos : BlockPos.betweenClosedStream(resolved.getPlot().getBoundingBox().toMojang()).map(BlockPos::immutable).toList()) {
            BlockState state = serverLevel.getBlockState(sourcePos);
            if (!RememberedSableShipMemory.isRememberShipAllowedState(state)) {
                continue;
            }

            Vector3d worldCenter = resolved.logicalPose().transformPosition(JOMLConversion.atCenterOf(sourcePos), new Vector3d());
            BlockPos targetPos;
            if (parentSubLevel != null) {
                Vector3d parentLocalCenter = parentSubLevel.logicalPose().transformPositionInverse(worldCenter, new Vector3d());
                targetPos = BlockPos.containing(parentLocalCenter.x, parentLocalCenter.y, parentLocalCenter.z);
            } else {
                targetPos = BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z);
            }

            if (!targetPos.equals(protectedPos)) {
                restoredPositions.add(targetPos.immutable());
            }
        }
        return restoredPositions;
    }

    int countBlocks(ServerLevel serverLevel) {
        ServerSubLevel resolved = resolveSubLevel(serverLevel);
        if (resolved == null) {
            return 0;
        }

        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosedStream(resolved.getPlot().getBoundingBox().toMojang()).map(BlockPos::immutable).toList()) {
            if (!serverLevel.getBlockState(pos).isAir()) {
                count++;
            }
        }
        return count;
    }

    private void cleanupFailedAssembly(ServerLevel serverLevel, @Nullable ServerSubLevel level, BlockPos protectedPos) {
        if (level == null) {
            return;
        }

        removeConstraintHandle();
        ServerSubLevel parentSubLevel = resolveParentRestoreSubLevel(serverLevel, level, protectedPos);
        if (parentSubLevel != null) {
            restoreSubLevelToParent(serverLevel, level, parentSubLevel, protectedPos);
        } else {
            restoreSubLevelToWorld(serverLevel, level, protectedPos);
        }
        clearRuntimeCache();
    }

    private Set<BlockPos> collectCapturedWorldBlocks(BearingContraption contraption) {
        Set<BlockPos> worldBlocks = new LinkedHashSet<>();
        BlockPos anchor = contraption.anchor;
        if (anchor == null) {
            return worldBlocks;
        }

        for (BlockPos localPos : contraption.getBlocks().keySet()) {
            worldBlocks.add(localPos.offset(anchor));
        }

        return worldBlocks;
    }

    private boolean activate(ServerSubLevel serverSubLevel, ServerLevel level, BlockPos bearingPos, Direction facing,
            RotationProfile rotationProfile) {
        active = true;
        subLevelId = serverSubLevel.getUniqueId();
        subLevel = serverSubLevel;
        constraintHandle = null;
        constraintRotationProfile = null;
        constraintReattachPending = false;

        if (!ensureConstraintAttached(level, serverSubLevel, bearingPos, facing, rotationProfile)) {
            clearState();
            return false;
        }

        return true;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean ensureConstraintAttached(ServerLevel serverLevel, ServerSubLevel serverSubLevel, BlockPos bearingPos,
            Direction facing, RotationProfile rotationProfile) {
        if (rotationProfile == RotationProfile.UP_PITCH_X && facing != Direction.UP) {
            return false;
        }

        BlockPos constraintPivotBlock = constraintPivotBlock(bearingPos, facing, rotationProfile);
        if (constraintHandle != null && constraintHandle.isValid()) {
            ServerSubLevel baseSubLevel = resolveBaseSubLevel(serverLevel, constraintPivotBlock);
            if (rotationProfile == RotationProfile.TWO_AXIS_TILT) {
                if (constraintRotationProfile == rotationProfile && isConstraintBaseCurrent(baseSubLevel)) {
                    constraintReattachPending = false;
                    return true;
                }
                removeConstraintHandle();
                return attachConstraint(serverLevel, serverSubLevel, bearingPos, facing, rotationProfile);
            }
            logExistingConstraintCandidateDiagnostics(serverLevel, serverSubLevel, bearingPos, facing,
                    rotationProfile);
            DiagnosticConstraint diagnostic = buildDiagnosticConstraintConfiguration(serverLevel, serverSubLevel,
                    bearingPos, facing, rotationProfile);
            DiagnosticFrame frame = diagnostic == null
                    ? null
                    : computeDiagnosticFrame(diagnostic.baseSubLevel(), serverSubLevel, diagnostic.configuration());
            boolean thresholdBreach = frame != null && isDiagnosticThresholdBreach(frame);
            if (constraintRotationProfile == rotationProfile && isConstraintBaseCurrent(baseSubLevel)
                    && !thresholdBreach) {
                constraintReattachPending = false;
                return true;
            }

            removeConstraintHandle();
            if (rotationProfile == RotationProfile.FACING_AXIS && thresholdBreach) {
                realignAttachedSubLevelToConstraintAnchor(
                        serverLevel,
                        serverSubLevel,
                        bearingPos,
                        constraintPivotBlock,
                        facing,
                        diagnostic.baseSubLevel(),
                        diagnostic.configuration(),
                        frame
                );
            }
            return attachConstraint(serverLevel, serverSubLevel, bearingPos, facing, rotationProfile);
        }

        removeConstraintHandle();
        RotationProfile profileToAttach = constraintReattachPending ? pendingReattachProfile : rotationProfile;
        boolean attached = attachConstraint(serverLevel, serverSubLevel, bearingPos, facing, profileToAttach);
        if (attached) {
            constraintReattachPending = false;
        }
        return attached && profileToAttach == rotationProfile;
    }

    private boolean hasCurrentFacingAxisConstraint(ServerLevel serverLevel, BlockPos bearingPos, Direction facing) {
        if (constraintHandle == null
                || !constraintHandle.isValid()
                || constraintRotationProfile != RotationProfile.FACING_AXIS) {
            return false;
        }

        BlockPos pivotBlock = constraintPivotBlock(bearingPos, facing, RotationProfile.FACING_AXIS);
        ServerSubLevel baseSubLevel = resolveBaseSubLevel(serverLevel, pivotBlock);
        return isConstraintBaseCurrent(baseSubLevel);
    }

    private boolean attachConstraint(ServerLevel serverLevel, ServerSubLevel attachedSubLevel, BlockPos bearingPos,
            Direction facing, RotationProfile rotationProfile) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return false;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        if (rotationProfile == RotationProfile.TWO_AXIS_TILT) {
            PreparedTwoAxisConstraint prepared =
                    prepareTwoAxisConstraint(serverLevel, attachedSubLevel, bearingPos, facing);
            if (!validatePreparedConstraint(container, attachedSubLevel, prepared)) {
                return false;
            }
            return attachPreparedConstraint(pipeline, attachedSubLevel, prepared);
        }

        PreparedConstraint prepared = prepareConstraint(serverLevel, attachedSubLevel, bearingPos, facing,
                rotationProfile);
        if (prepared == null) {
            return false;
        }
        ServerSubLevel baseSubLevel = prepared.baseSubLevel();
        RotaryConstraintConfiguration configuration = prepared.configuration();
        BlockPos constraintPivotBlock = prepared.constraintPivotBlock();

        if (rotationProfile == RotationProfile.FACING_AXIS && baseSubLevel != null) {
            DiagnosticFrame frame = computeDiagnosticFrame(baseSubLevel, attachedSubLevel, configuration);
            if (isDiagnosticThresholdBreach(frame)) {
                realignAttachedSubLevelToConstraintAnchor(
                        serverLevel,
                        attachedSubLevel,
                        bearingPos,
                        constraintPivotBlock,
                        facing,
                        baseSubLevel,
                        configuration,
                        frame
                );
            }
        }
        logServoTopFrameDiagnostics(pipeline, baseSubLevel, attachedSubLevel, constraintPivotBlock, facing,
                configuration);
        logSableAttachDiagnostics("attach", pipeline, serverLevel, bearingPos, constraintPivotBlock, facing,
                baseSubLevel,
                attachedSubLevel, configuration, true);

        try {
            configuration.validate(container, baseSubLevel, attachedSubLevel);
            constraintHandle = pipeline.addConstraint(baseSubLevel, attachedSubLevel, configuration);
        } catch (Exception e) {
            LOGGER.warn("Failed to attach Sable {} rotary constraint at {}", rotationProfile, constraintPivotBlock,
                    e);
            constraintHandle = null;
        }

        if (constraintHandle != null && constraintHandle.isValid()) {
            rememberConstraintBase(baseSubLevel);
            constraintRotationProfile = rotationProfile;
            constraintReattachPending = false;
            return true;
        }
        return false;
    }

    private void logExistingConstraintCandidateDiagnostics(ServerLevel serverLevel, ServerSubLevel attachedSubLevel,
            BlockPos bearingPos, Direction facing, RotationProfile rotationProfile) {
        if (!TwisterMillDiagnostics.isLoggingEnabled(diagnosticsTarget)) {
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        BlockPos constraintPivotBlock = constraintPivotBlock(bearingPos, facing, rotationProfile);
        DiagnosticConstraint diagnostic = buildDiagnosticConstraintConfiguration(serverLevel, attachedSubLevel,
                bearingPos, facing, rotationProfile);
        if (diagnostic == null) {
            return;
        }

        DiagnosticFrame frame = computeDiagnosticFrame(diagnostic.baseSubLevel(), attachedSubLevel, diagnostic.configuration());

        boolean baseChanged = hasDiagnosticBaseChanged(diagnostic.baseSubLevel());
        boolean thresholdBreach = isDiagnosticThresholdBreach(frame);
        if (!baseChanged && !thresholdBreach) {
            return;
        }

        logSableAttachDiagnostics("refresh-valid-handle-candidate", pipeline, serverLevel, bearingPos,
                constraintPivotBlock, facing,
                diagnostic.baseSubLevel(), attachedSubLevel, diagnostic.configuration(), false);
    }

    @Nullable
    private PreparedRotationConstraint prepareRotationConstraint(
            ServerLevel serverLevel,
            ServerSubLevel attachedSubLevel,
            BlockPos bearingPos,
            Direction facing,
            RotationProfile rotationProfile
    ) {
        return rotationProfile == RotationProfile.TWO_AXIS_TILT
                ? prepareTwoAxisConstraint(serverLevel, attachedSubLevel, bearingPos, facing)
                : prepareConstraint(serverLevel, attachedSubLevel, bearingPos, facing, rotationProfile);
    }

    private PreparedTwoAxisConstraint prepareTwoAxisConstraint(
            ServerLevel serverLevel,
            ServerSubLevel attachedSubLevel,
            BlockPos bearingPos,
            Direction facing
    ) {
        ServerSubLevel baseSubLevel = resolveBaseSubLevel(serverLevel, bearingPos);
        Vector3d attachedPivot = computeAttachedLocalCenter(
                serverLevel,
                attachedSubLevel,
                bearingPos.relative(facing),
                bearingPos.getCenter()
        );
        Quaterniond canonicalFrame = ServoTwoAxisRotationMath.setCanonicalFrame(facing, new Quaterniond());
        GenericConstraintConfiguration configuration = new GenericConstraintConfiguration(
                JOMLConversion.atCenterOf(bearingPos),
                attachedPivot,
                new Quaterniond(canonicalFrame),
                new Quaterniond(canonicalFrame),
                EnumSet.of(
                        ConstraintJointAxis.LINEAR_X,
                        ConstraintJointAxis.LINEAR_Y,
                        ConstraintJointAxis.LINEAR_Z,
                        ConstraintJointAxis.ANGULAR_Y
                )
        );
        return new PreparedTwoAxisConstraint(
                baseSubLevel,
                configuration,
                bearingPos,
                RotationProfile.TWO_AXIS_TILT
        );
    }

    @Nullable
    private DiagnosticConstraint buildDiagnosticConstraintConfiguration(ServerLevel serverLevel, ServerSubLevel attachedSubLevel,
            BlockPos anchorWorld, Direction facing) {
        PreparedConstraint prepared = prepareConstraint(serverLevel, attachedSubLevel,
                anchorWorld.relative(facing.getOpposite()), facing, RotationProfile.FACING_AXIS);
        if (prepared == null) {
            return null;
        }
        return new DiagnosticConstraint(prepared.baseSubLevel(), prepared.configuration());
    }

    @Nullable
    private DiagnosticConstraint buildDiagnosticConstraintConfiguration(ServerLevel serverLevel,
            ServerSubLevel attachedSubLevel, BlockPos bearingPos, Direction facing, RotationProfile rotationProfile) {
        PreparedConstraint prepared = prepareConstraint(serverLevel, attachedSubLevel, bearingPos, facing,
                rotationProfile);
        if (prepared == null) {
            return null;
        }
        return new DiagnosticConstraint(prepared.baseSubLevel(), prepared.configuration());
    }

    @Nullable
    private PreparedConstraint prepareConstraint(ServerLevel serverLevel, ServerSubLevel attachedSubLevel,
            BlockPos bearingPos, Direction facing, RotationProfile rotationProfile) {
        if (rotationProfile == RotationProfile.TWO_AXIS_TILT
                || rotationProfile == RotationProfile.UP_PITCH_X && facing != Direction.UP) {
            return null;
        }

        BlockPos pivotBlock = constraintPivotBlock(bearingPos, facing, rotationProfile);
        ServerSubLevel baseSubLevel = resolveBaseSubLevel(serverLevel, pivotBlock);
        RotaryConstraintConfiguration configuration;
        if (rotationProfile == RotationProfile.UP_PITCH_X) {
            Vector3d attachedPivot = computeAttachedLocalCenter(
                    serverLevel,
                    attachedSubLevel,
                    bearingPos.relative(facing),
                    bearingPos.getCenter()
            );
            Vector3d localXAxis = new Vector3d(1.0D, 0.0D, 0.0D);
            configuration = new RotaryConstraintConfiguration(
                    JOMLConversion.atCenterOf(bearingPos),
                    attachedPivot,
                    new Vector3d(localXAxis),
                    new Vector3d(localXAxis)
            );
        } else {
            Vector3d axis = axisFromFacing(facing);
            if (axis.lengthSquared() <= 1.0E-12) {
                return null;
            }
            axis.normalize();
            configuration = buildConstraintConfiguration(baseSubLevel, attachedSubLevel, pivotBlock, axis);
        }

        if (configuration == null) {
            return null;
        }
        return new PreparedConstraint(baseSubLevel, configuration, pivotBlock, rotationProfile);
    }

    @Nullable
    private RotaryConstraintConfiguration buildConstraintConfiguration(
            @Nullable ServerSubLevel baseSubLevel,
            ServerSubLevel attachedSubLevel,
            BlockPos anchorWorld,
            Vector3d axis
    ) {
        Vector3d normal1 = new Vector3d(axis);
        if (normal1.lengthSquared() <= 1.0E-12) {
            return null;
        }
        normal1.normalize();

        if (baseSubLevel == null) {
            Vector3d anchorWorldCenter = JOMLConversion.atCenterOf(anchorWorld);
            Pose3dc attachedPose = attachedSubLevel.logicalPose();
            Vector3d normal2 = attachedPose.transformNormalInverse(normal1, new Vector3d());
            if (normal2.lengthSquared() <= 1.0E-12) {
                return null;
            }
            normal2.normalize();
            Vector3d attachedAnchorCenter = computeAnchorLocalCenter(attachedSubLevel, anchorWorld)
                    .sub(new Vector3d(normal2).mul(CONSTRAINT_ANCHOR_NUDGE));
            return new RotaryConstraintConfiguration(
                    anchorWorldCenter,
                    attachedAnchorCenter,
                    normal1,
                    normal2
            );
        }

        Vector3d normal2 = new Vector3d(axis);
        if (normal2.lengthSquared() <= 1.0E-12) {
            return null;
        }
        normal2.normalize();

        return new RotaryConstraintConfiguration(
                JOMLConversion.atCenterOf(anchorWorld),
                computeAnchorLocalCenter(attachedSubLevel, anchorWorld),
                normal1,
                normal2
        );
    }

    private boolean hasDiagnosticBaseChanged(@Nullable ServerSubLevel baseSubLevel) {
        UUID baseId = baseSubLevel == null ? null : baseSubLevel.getUniqueId();
        boolean baseIsRoot = baseSubLevel == null;
        return !diagnosticLastBaseInitialized
                || diagnosticLastBaseWasRoot != baseIsRoot
                || !Objects.equals(diagnosticLastBaseSubLevelId, baseId);
    }

    private void rememberDiagnosticBase(@Nullable ServerSubLevel baseSubLevel) {
        diagnosticLastBaseSubLevelId = baseSubLevel == null ? null : baseSubLevel.getUniqueId();
        diagnosticLastBaseInitialized = true;
        diagnosticLastBaseWasRoot = baseSubLevel == null;
    }

    @Nullable
    private ServerSubLevel resolveBaseSubLevel(ServerLevel serverLevel, BlockPos anchorWorld) {
        SubLevel containing = Sable.HELPER.getContaining(serverLevel, anchorWorld);
        if (containing instanceof ServerSubLevel containingSubLevel && !containingSubLevel.isRemoved()) {
            return containingSubLevel;
        }
        return null;
    }

    private boolean isConstraintBaseCurrent(@Nullable ServerSubLevel baseSubLevel) {
        UUID baseId = baseSubLevel == null ? null : baseSubLevel.getUniqueId();
        boolean baseIsRoot = baseSubLevel == null;
        return constraintBaseInitialized
                && constraintBaseWasRoot == baseIsRoot
                && Objects.equals(constraintBaseSubLevelId, baseId);
    }

    private void rememberConstraintBase(@Nullable ServerSubLevel baseSubLevel) {
        constraintBaseSubLevelId = baseSubLevel == null ? null : baseSubLevel.getUniqueId();
        constraintBaseInitialized = true;
        constraintBaseWasRoot = baseSubLevel == null;
    }

    private void clearConstraintBase() {
        constraintBaseSubLevelId = null;
        constraintBaseInitialized = false;
        constraintBaseWasRoot = false;
    }

    private void logSableAttachDiagnostics(String event, PhysicsPipeline pipeline, ServerLevel serverLevel, BlockPos bearingPos,
            BlockPos anchorWorld, Direction facing, @Nullable ServerSubLevel baseSubLevel, ServerSubLevel attachedSubLevel,
            RotaryConstraintConfiguration configuration, boolean force) {
        if (!TwisterMillDiagnostics.isLoggingEnabled(diagnosticsTarget)) {
            return;
        }

        DiagnosticFrame frame = computeDiagnosticFrame(baseSubLevel, attachedSubLevel, configuration);

        boolean baseChanged = hasDiagnosticBaseChanged(baseSubLevel);
        boolean thresholdBreach = frame.anchorWorldError() > DIAGNOSTIC_ANCHOR_ERROR_THRESHOLD
                || frame.normalWorldError() > DIAGNOSTIC_NORMAL_ERROR_THRESHOLD;
        if (!force && !baseChanged && !thresholdBreach) {
            return;
        }

        SubLevel bearingContaining = Sable.HELPER.getContaining(serverLevel, bearingPos);
        SubLevel anchorContaining = Sable.HELPER.getContaining(serverLevel, anchorWorld);
        BlockEntity owner = serverLevel.getBlockEntity(bearingPos);
        String ownerName = owner == null ? "<none>" : owner.getClass().getSimpleName();

        LOGGER.info(
                "[SableAttachDiag] event={} owner={} bearingPos={} anchorPos={} facing={} bearingContainingId={} anchorContainingId={} chosenBaseId={} chosenBaseIsRoot={} attachedId={} baseChanged={} thresholdBreach={} basePose={} baseLastPose={} attachedPose={} attachedLastPose={} pos1={} pos2={} normal1={} normal2={} expectedAttachedAnchorLocal={} pos2ExpectedDelta={} baseAnchorWorld={} attachedAnchorWorld={} anchorWorldError={} normal1World={} normal2World={} normalWorldError={} attachedCom={} attachedRotationPoint={} attachedLinearVelocity={} attachedAngularVelocity={}",
                event,
                ownerName,
                bearingPos,
                anchorWorld,
                facing,
                formatSubLevelId(bearingContaining),
                formatSubLevelId(anchorContaining),
                baseSubLevel == null ? "<root>" : baseSubLevel.getUniqueId(),
                baseSubLevel == null,
                attachedSubLevel.getUniqueId(),
                baseChanged,
                thresholdBreach,
                baseSubLevel == null ? "<root>" : formatPose(baseSubLevel.logicalPose()),
                baseSubLevel == null ? "<root>" : formatPose(baseSubLevel.lastPose()),
                formatPose(attachedSubLevel.logicalPose()),
                formatPose(attachedSubLevel.lastPose()),
                formatVector(configuration.pos1()),
                formatVector(configuration.pos2()),
                formatVector(configuration.normal1()),
                formatVector(configuration.normal2()),
                formatVector(frame.expectedAttachedAnchorLocal()),
                formatDouble(frame.pos2ExpectedDelta()),
                formatVector(frame.baseAnchorWorld()),
                formatVector(frame.attachedAnchorWorld()),
                formatDouble(frame.anchorWorldError()),
                formatVector(frame.normal1World()),
                formatVector(frame.normal2World()),
                formatDouble(frame.normalWorldError()),
                formatVector(attachedSubLevel.getMassTracker().getCenterOfMass()),
                formatVector(attachedSubLevel.logicalPose().rotationPoint()),
                formatVector(readLinearVelocity(pipeline, attachedSubLevel)),
                formatVector(readAngularVelocity(pipeline, attachedSubLevel))
        );

        rememberDiagnosticBase(baseSubLevel);
    }

    private DiagnosticFrame computeDiagnosticFrame(@Nullable ServerSubLevel baseSubLevel, ServerSubLevel attachedSubLevel,
            RotaryConstraintConfiguration configuration) {
        Pose3dc basePose = baseSubLevel == null ? null : baseSubLevel.logicalPose();
        Pose3dc attachedPose = attachedSubLevel.logicalPose();
        Vector3d baseAnchorWorld = basePose == null
                ? new Vector3d(configuration.pos1())
                : basePose.transformPosition(configuration.pos1(), new Vector3d());
        Vector3d attachedAnchorWorld = attachedPose.transformPosition(configuration.pos2(), new Vector3d());
        Vector3d expectedAttachedAnchorLocal = attachedPose.transformPositionInverse(baseAnchorWorld, new Vector3d());
        double pos2ExpectedDelta = distance(new Vector3d(configuration.pos2()), expectedAttachedAnchorLocal);
        Vector3d normal1World = basePose == null
                ? new Vector3d(configuration.normal1())
                : basePose.transformNormal(configuration.normal1(), new Vector3d());
        Vector3d normal2World = attachedPose.transformNormal(configuration.normal2(), new Vector3d());
        normalized(normal1World);
        normalized(normal2World);
        return new DiagnosticFrame(
                expectedAttachedAnchorLocal,
                pos2ExpectedDelta,
                baseAnchorWorld,
                attachedAnchorWorld,
                distance(baseAnchorWorld, attachedAnchorWorld),
                normal1World,
                normal2World,
                distance(normal1World, normal2World)
        );
    }

    private static boolean isDiagnosticThresholdBreach(DiagnosticFrame frame) {
        return frame.anchorWorldError() > DIAGNOSTIC_ANCHOR_ERROR_THRESHOLD
                || frame.normalWorldError() > DIAGNOSTIC_NORMAL_ERROR_THRESHOLD;
    }

    private void realignAttachedSubLevelToConstraintAnchor(
            ServerLevel serverLevel,
            ServerSubLevel attachedSubLevel,
            BlockPos bearingPos,
            BlockPos anchorWorld,
            Direction facing,
            @Nullable ServerSubLevel baseSubLevel,
            RotaryConstraintConfiguration configuration,
            @Nullable DiagnosticFrame previousFrame
    ) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        Pose3dc basePose = baseSubLevel == null ? null : baseSubLevel.logicalPose();
        Vector3d baseAnchorWorld = basePose == null
                ? new Vector3d(configuration.pos1())
                : basePose.transformPosition(configuration.pos1(), new Vector3d());
        Pose3d attachedPose = attachedSubLevel.logicalPose();
        Quaterniond attachedOrientation = new Quaterniond(attachedPose.orientation());
        if (basePose != null) {
            Vector3d targetNormalWorld = basePose.transformNormal(configuration.normal1(), new Vector3d());
            Vector3d currentNormalWorld = attachedOrientation.transform(configuration.normal2(), new Vector3d());
            if (targetNormalWorld.lengthSquared() > 1.0E-12D && currentNormalWorld.lengthSquared() > 1.0E-12D) {
                targetNormalWorld.normalize();
                currentNormalWorld.normalize();
                Quaterniond correction = new Quaterniond().rotationTo(currentNormalWorld, targetNormalWorld);
                attachedOrientation = new Quaterniond(correction).mul(attachedOrientation).normalize();
            }
        }
        Vector3d rotationPoint = new Vector3d(attachedPose.rotationPoint());
        Vector3d anchorOffsetFromRotationPoint = new Vector3d(configuration.pos2()).sub(rotationPoint);
        attachedOrientation.transform(anchorOffsetFromRotationPoint);
        Vector3d posePosition = new Vector3d(baseAnchorWorld).sub(anchorOffsetFromRotationPoint);

        attachedPose.position().set(posePosition);
        attachedPose.orientation().set(attachedOrientation);
        attachedPose.scale().set(1.0D);

        pipeline.teleport(attachedSubLevel, attachedPose.position(), attachedPose.orientation());
        attachedPose.scale().set(1.0D);
        pipeline.resetVelocity(attachedSubLevel);
        pipeline.wakeUp(attachedSubLevel);
        attachedSubLevel.updateLastPose();

        if (TwisterMillDiagnostics.isLoggingEnabled(diagnosticsTarget)) {
            BlockEntity owner = serverLevel.getBlockEntity(bearingPos);
            String ownerName = owner == null ? "<none>" : owner.getClass().getSimpleName();
            LOGGER.info(
                    "[SableAttachDiag] event={} owner={} bearingPos={} anchorPos={} facing={} chosenBaseId={} attachedId={} previousAnchorWorldError={} previousNormalWorldError={} previousPos2ExpectedDelta={} realignedPose={} attachedLinearVelocity={} attachedAngularVelocity={}",
                    "refresh-threshold-realign",
                    ownerName,
                    bearingPos,
                    anchorWorld,
                    facing,
                    baseSubLevel == null ? "<root>" : baseSubLevel.getUniqueId(),
                    attachedSubLevel.getUniqueId(),
                    previousFrame == null ? "<unknown>" : formatDouble(previousFrame.anchorWorldError()),
                    previousFrame == null ? "<unknown>" : formatDouble(previousFrame.normalWorldError()),
                    previousFrame == null ? "<unknown>" : formatDouble(previousFrame.pos2ExpectedDelta()),
                    formatPose(attachedPose),
                    formatVector(readLinearVelocity(pipeline, attachedSubLevel)),
                    formatVector(readAngularVelocity(pipeline, attachedSubLevel))
            );
        }
    }

    private static Vector3d axisFromFacing(Direction facing) {
        return new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    private static Vector3d worldLockReferenceAxisLocal(Direction facing) {
        Vector3d reference = new Vector3d(0.0D, 0.0D, 1.0D);
        new Quaterniond(facing.getRotation()).transform(reference);
        if (reference.lengthSquared() <= WORLD_LOCK_PROJECTION_EPSILON) {
            return new Vector3d(0.0D, 0.0D, 1.0D);
        }
        return reference.normalize();
    }

    @Nullable
    private static Vector3d worldLockDesiredAxisWorld(Direction facing, int modeSignal, Vector3d bearingWorld) {
        if (facing.getAxis() == Direction.Axis.Y) {
            return modeSignal == 4
                    ? new Vector3d(0.0D, 0.0D, 1.0D)
                    : new Vector3d(1.0D, 0.0D, 0.0D);
        }

        if (modeSignal == 4) {
            Vector3d horizontal = new Vector3d(0.0D, 1.0D, 0.0D).cross(bearingWorld, new Vector3d());
            if (horizontal.lengthSquared() <= WORLD_LOCK_PROJECTION_EPSILON) {
                return null;
            }
            return horizontal.normalize();
        }

        return new Vector3d(0.0D, 1.0D, 0.0D);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean projectOntoPlaneAndNormalize(Vector3d vector, Vector3d planeNormal) {
        vector.fma(-vector.dot(planeNormal), planeNormal);
        if (vector.lengthSquared() <= WORLD_LOCK_PROJECTION_EPSILON) {
            return false;
        }
        vector.normalize();
        return true;
    }

    private static double servoMotorSign(Direction facing) {
        return facing == Direction.NORTH || facing == Direction.WEST || facing == Direction.DOWN ? -1.0D : 1.0D;
    }

    private static double clampUnit(double value) {
        return Math.max(-1.0D, Math.min(1.0D, value));
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private static double computeEffectiveInertia(ServerSubLevel subLevel, Direction facing,
            RotationProfile rotationProfile, double minimum) {
        Vector3d axis = rotationProfile == RotationProfile.UP_PITCH_X
                ? new Vector3d(1.0D, 0.0D, 0.0D)
                : axisFromFacing(facing);
        return computeEffectiveInertia(subLevel, axis, minimum);
    }

    private static double computeEffectiveInertia(ServerSubLevel subLevel, Vector3d axis, double minimum) {
        return computeEffectiveInertia(subLevel, axis, minimum, new Vector3d());
    }

    private static double computeEffectiveInertia(
            ServerSubLevel subLevel,
            Vector3d axis,
            double minimum,
            Vector3d transformed
    ) {
        if (axis.lengthSquared() <= 1.0E-12) {
            return minimum;
        }
        axis.normalize();

        double inertia = subLevel.getMassTracker().getInertiaTensor().transform(axis, transformed).dot(axis);
        if (!Double.isFinite(inertia) || inertia <= 0.0) {
            inertia = subLevel.getMassTracker().getMass();
        }

        if (!Double.isFinite(inertia) || inertia <= 0.0) {
            inertia = minimum;
        }

        return Math.max(minimum, inertia);
    }

    private void logServoTopFrameDiagnostics(
            PhysicsPipeline pipeline,
            @Nullable ServerSubLevel baseSubLevel,
            ServerSubLevel attachedSubLevel,
            BlockPos anchorWorld,
            Direction facing,
            RotaryConstraintConfiguration configuration
    ) {
        if (!TwisterMillDiagnostics.isLoggingEnabled(diagnosticsTarget)) {
            return;
        }
        if (baseSubLevel == null) {
            return;
        }

        try {
            Pose3dc basePose = baseSubLevel.logicalPose();
            Pose3dc attachedPose = attachedSubLevel.logicalPose();
            Vector3d baseAnchorWorld = basePose.transformPosition(configuration.pos1(), new Vector3d());
            Vector3d attachedAnchorWorld = attachedPose.transformPosition(configuration.pos2(), new Vector3d());
            Vector3d normal1World = normalized(basePose.transformNormal(configuration.normal1(), new Vector3d()));
            Vector3d normal2World = normalized(attachedPose.transformNormal(configuration.normal2(), new Vector3d()));
            Vector3d normal2CandidateFromNormal1World = normalized(attachedPose.transformNormalInverse(normal1World, new Vector3d()));

            LOGGER.info(
                    "[ServoTopFrameDiag] baseName={} propSlotBase={} baseId={} attachedId={} anchorWorld={} facing={} pos1={} pos2={} normal1={} normal2={} basePose={} baseLastPose={} baseCom={} attachedPose={} attachedLastPose={} attachedCom={} baseAnchorWorld={} attachedAnchorWorld={} anchorWorldError={} normal1World={} normal2World={} normalWorldError={} normal2CandidateFromNormal1World={} baseLinearVelocity={} baseAngularVelocity={} attachedLinearVelocity={} attachedAngularVelocity={}",
                    baseSubLevel.getName(),
                    ServoPropellerSlotManager.isPropellerSlotSubLevel(baseSubLevel),
                    baseSubLevel.getUniqueId(),
                    attachedSubLevel.getUniqueId(),
                    anchorWorld,
                    facing,
                    formatVector(configuration.pos1()),
                    formatVector(configuration.pos2()),
                    formatVector(configuration.normal1()),
                    formatVector(configuration.normal2()),
                    formatPose(basePose),
                    formatPose(baseSubLevel.lastPose()),
                    formatVector(baseSubLevel.getMassTracker().getCenterOfMass()),
                    formatPose(attachedPose),
                    formatPose(attachedSubLevel.lastPose()),
                    formatVector(attachedSubLevel.getMassTracker().getCenterOfMass()),
                    formatVector(baseAnchorWorld),
                    formatVector(attachedAnchorWorld),
                    formatDouble(distance(baseAnchorWorld, attachedAnchorWorld)),
                    formatVector(normal1World),
                    formatVector(normal2World),
                    formatDouble(distance(normal1World, normal2World)),
                    formatVector(normal2CandidateFromNormal1World),
                    formatVector(readLinearVelocity(pipeline, baseSubLevel)),
                    formatVector(readAngularVelocity(pipeline, baseSubLevel)),
                    formatVector(readLinearVelocity(pipeline, attachedSubLevel)),
                    formatVector(readAngularVelocity(pipeline, attachedSubLevel))
            );
        } catch (Exception e) {
            LOGGER.warn("Failed to log ServoTop frame diagnostics at {}", anchorWorld, e);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private Vector3d readLinearVelocity(PhysicsPipeline pipeline, ServerSubLevel subLevel) {
        try {
            return pipeline.getLinearVelocity(subLevel, new Vector3d());
        } catch (Exception ignored) {
            return new Vector3d(subLevel.latestLinearVelocity);
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private Vector3d readAngularVelocity(PhysicsPipeline pipeline, ServerSubLevel subLevel) {
        try {
            return pipeline.getAngularVelocity(subLevel, new Vector3d());
        } catch (Exception ignored) {
            return new Vector3d(subLevel.latestAngularVelocity);
        }
    }

    private static Vector3d normalized(Vector3d vector) {
        if (vector.lengthSquared() > 1.0E-12) {
            vector.normalize();
        }
        return vector;
    }

    private static double distance(Vector3d a, Vector3d b) {
        return new Vector3d(a).sub(b).length();
    }

    @Nullable
    private static Vector3d computeAnchorToComWorld(
            Pose3dc pose,
            @Nullable Vector3dc centerOfMass,
            @Nullable DiagnosticFrame frame
    ) {
        if (frame == null || centerOfMass == null) {
            return null;
        }
        Vector3d comWorld = pose.transformPosition(centerOfMass, new Vector3d());
        return comWorld.sub(frame.baseAnchorWorld());
    }

    private static boolean isFiniteVector(Vector3dc vector) {
        return Double.isFinite(vector.x()) && Double.isFinite(vector.y()) && Double.isFinite(vector.z());
    }

    private static String formatPose(Pose3dc pose) {
        return "{pos=" + formatVector(pose.position())
                + ",rot=" + pose.orientation()
                + ",rotationPoint=" + formatVector(pose.rotationPoint())
                + "}";
    }

    private static String formatVector(@Nullable org.joml.Vector3dc vector) {
        if (vector == null) {
            return "null";
        }
        return "(" + formatDouble(vector.x()) + "," + formatDouble(vector.y()) + "," + formatDouble(vector.z()) + ")";
    }

    private static String formatDouble(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String formatSubLevelId(@Nullable SubLevel subLevel) {
        if (subLevel == null) {
            return "<root>";
        }
        return String.valueOf(subLevel.getUniqueId());
    }

    @Nullable
    private ServerSubLevel validateMode3ReturnCommand(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            Mode3ReturnMotorCommand command,
            boolean requireZeroTarget
    ) {
        ServerSubLevel attachedSubLevel = resolveSubLevel(serverLevel);
        if (attachedSubLevel == null
                || command.gameTime() != serverLevel.getGameTime()
                || !command.subLevelId().equals(attachedSubLevel.getUniqueId())
                || command.facing() != facing
                || command.constraintHandle() != constraintHandle
                || constraintHandle == null
                || !constraintHandle.isValid()
                || constraintRotationProfile != RotationProfile.FACING_AXIS
                || !Float.isFinite(command.targetAngleDegrees())
                || !Double.isFinite(command.targetRadians())
                || requireZeroTarget
                && (!isExactPositiveZero(command.targetAngleDegrees())
                || !isExactPositiveZero(command.targetRadians()))) {
            return null;
        }

        BlockPos constraintPivot = constraintPivotBlock(bearingPos, facing, RotationProfile.FACING_AXIS);
        Mode3RestoreContext baseContext = resolveMode3RestoreContext(serverLevel, attachedSubLevel, constraintPivot);
        if (!baseContext.resolved() || !isConstraintBaseCurrent(baseContext.parentSubLevel())) {
            return null;
        }
        return attachedSubLevel;
    }

    private Mode3RestoreContext resolveMode3RestoreContext(
            ServerLevel serverLevel,
            ServerSubLevel sourceSubLevel,
            BlockPos position
    ) {
        SubLevel containing = Sable.HELPER.getContaining(serverLevel, position);
        if (containing instanceof ServerSubLevel parentSubLevel
                && !parentSubLevel.isRemoved()
                && !parentSubLevel.getUniqueId().equals(sourceSubLevel.getUniqueId())) {
            return new Mode3RestoreContext(parentSubLevel, false, true);
        }
        if (containing != null) {
            return Mode3RestoreContext.unresolved();
        }

        ChunkPos chunkPos = new ChunkPos(position);
        if (Sable.HELPER.isInPlotGrid(serverLevel, chunkPos.x, chunkPos.z)) {
            return Mode3RestoreContext.unresolved();
        }
        return new Mode3RestoreContext(null, true, true);
    }

    private static boolean isSameMode3ProbeContext(
            Mode3RelativePoseProbe first,
            Mode3RelativePoseProbe second
    ) {
        return first.subLevelId().equals(second.subLevelId())
                && Objects.equals(first.parentSubLevelId(), second.parentSubLevelId())
                && first.rootParent() == second.rootParent()
                && first.plotCenter().equals(second.plotCenter())
                && first.assemblyAnchor().equals(second.assemblyAnchor())
                && first.minX() == second.minX()
                && first.minY() == second.minY()
                && first.minZ() == second.minZ()
                && first.maxX() == second.maxX()
                && first.maxY() == second.maxY()
                && first.maxZ() == second.maxZ();
    }

    private static boolean isSameMode3Probe(
            Mode3RelativePoseProbe first,
            Mode3RelativePoseProbe second
    ) {
        return first.gameTime() == second.gameTime()
                && isSameMode3ProbeContext(first, second)
                && isSamePose(first.topPose(), second.topPose())
                && (first.parentPose() == null && second.parentPose() == null
                || first.parentPose() != null
                && second.parentPose() != null
                && isSamePose(first.parentPose(), second.parentPose()))
                && sameFloat(
                        first.facingAxisAngleDegrees(),
                        second.facingAxisAngleDegrees()
                );
    }

    private static boolean isFiniteMode3Probe(Mode3RelativePoseProbe probe) {
        return isFiniteUnitScalePose(probe.topPose())
                && (probe.parentPose() == null || isFiniteUnitScalePose(probe.parentPose()))
                && Float.isFinite(probe.facingAxisAngleDegrees());
    }

    private static boolean isFiniteUnitScalePose(Pose3dc pose) {
        return isFiniteVector(pose.position())
                && isFiniteQuaternion(pose.orientation())
                && isFiniteVector(pose.rotationPoint())
                && isFiniteVector(pose.scale())
                && pose.scale().x() == 1.0D
                && pose.scale().y() == 1.0D
                && pose.scale().z() == 1.0D;
    }

    private static boolean isFiniteQuaternion(org.joml.Quaterniondc quaternion) {
        return Double.isFinite(quaternion.x())
                && Double.isFinite(quaternion.y())
                && Double.isFinite(quaternion.z())
                && Double.isFinite(quaternion.w());
    }

    private static boolean isSamePose(Pose3dc first, Pose3dc second) {
        return isSameVector(first.position(), second.position())
                && isSameQuaternion(first.orientation(), second.orientation())
                && isSameVector(first.rotationPoint(), second.rotationPoint())
                && isSameVector(first.scale(), second.scale());
    }

    private static boolean isSameVector(Vector3dc first, Vector3dc second) {
        return sameDouble(first.x(), second.x())
                && sameDouble(first.y(), second.y())
                && sameDouble(first.z(), second.z());
    }

    private static boolean isSameQuaternion(
            org.joml.Quaterniondc first,
            org.joml.Quaterniondc second
    ) {
        return sameDouble(first.x(), second.x())
                && sameDouble(first.y(), second.y())
                && sameDouble(first.z(), second.z())
                && sameDouble(first.w(), second.w());
    }

    private static boolean sameDouble(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    private static boolean sameFloat(float first, float second) {
        return Float.floatToIntBits(first) == Float.floatToIntBits(second);
    }

    private static boolean isExactPositiveZero(float value) {
        return Float.floatToRawIntBits(value) == Float.floatToRawIntBits(0.0F);
    }

    private static boolean isExactPositiveZero(double value) {
        return Double.doubleToRawLongBits(value) == Double.doubleToRawLongBits(0.0D);
    }

    private static Vector3d transformMode3RestorePosition(
            Mode3RelativePoseProbe probe,
            Vector3dc sourcePosition
    ) {
        Vector3d worldPosition = probe.topPose().transformPosition(sourcePosition, new Vector3d());
        if (probe.parentPose() == null) {
            return worldPosition;
        }
        return probe.parentPose().transformPositionInverse(worldPosition, new Vector3d());
    }

    void clearRuntimeForUnload() {
        removeConstraintHandle();
        clearRuntimeCache();
    }

    @Nullable
    private ServerSubLevel resolveParentRestoreSubLevel(ServerLevel serverLevel, ServerSubLevel sourceSubLevel, @Nullable BlockPos protectedPos) {
        if (protectedPos == null) {
            return null;
        }

        SubLevel containing = Sable.HELPER.getContaining(serverLevel, protectedPos);
        if (containing instanceof ServerSubLevel parentSubLevel
                && !parentSubLevel.isRemoved()
                && !parentSubLevel.getUniqueId().equals(sourceSubLevel.getUniqueId())) {
            return parentSubLevel;
        }

        return null;
    }

    private void restoreSubLevelToParent(
            ServerLevel serverLevel,
            ServerSubLevel sourceSubLevel,
            ServerSubLevel parentSubLevel,
            BlockPos protectedPos
    ) {
        Map<BlockPos, RestoredBlockData> restoreMap = new LinkedHashMap<>();
        List<BlockPos> sourceBlocks = new ArrayList<>();

        for (BlockPos sourcePos : BlockPos.betweenClosedStream(sourceSubLevel.getPlot().getBoundingBox().toMojang()).map(BlockPos::immutable).toList()) {
            BlockState state = serverLevel.getBlockState(sourcePos);
            if (state.isAir()) {
                continue;
            }

            sourceBlocks.add(sourcePos);

            BlockEntity blockEntity = serverLevel.getBlockEntity(sourcePos);
            CompoundTag blockEntityTag = blockEntity != null
                    ? blockEntity.saveWithFullMetadata(serverLevel.registryAccess())
                    : null;

            Vector3d worldCenter = sourceSubLevel.logicalPose().transformPosition(JOMLConversion.atCenterOf(sourcePos), new Vector3d());
            Vector3d parentLocalCenter = parentSubLevel.logicalPose().transformPositionInverse(worldCenter, new Vector3d());
            BlockPos targetParentPos = BlockPos.containing(parentLocalCenter.x, parentLocalCenter.y, parentLocalCenter.z);
            restoreMap.putIfAbsent(targetParentPos, new RestoredBlockData(state, blockEntityTag));
        }

        for (BlockPos sourcePos : sourceBlocks) {
            serverLevel.removeBlockEntity(sourcePos);
            serverLevel.setBlock(sourcePos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }

        removeSubLevel(serverLevel, sourceSubLevel);

        boolean parentChanged = false;
        for (Map.Entry<BlockPos, RestoredBlockData> entry : restoreMap.entrySet()) {
            BlockPos targetPos = entry.getKey();
            RestoredBlockData data = entry.getValue();

            if (targetPos.equals(protectedPos)) {
                continue;
            }

            serverLevel.removeBlockEntity(targetPos);
            if (!setBlockInSubLevelPlot(serverLevel, parentSubLevel, targetPos, data.state())) {
                continue;
            }
            parentChanged = true;

            CompoundTag blockEntityTag = data.blockEntityTag();
            if (blockEntityTag == null) {
                continue;
            }

            BlockEntity targetBlockEntity = serverLevel.getBlockEntity(targetPos);
            if (targetBlockEntity == null) {
                continue;
            }

            CompoundTag tagCopy = blockEntityTag.copy();
            tagCopy.putInt("x", targetPos.getX());
            tagCopy.putInt("y", targetPos.getY());
            tagCopy.putInt("z", targetPos.getZ());
            targetBlockEntity.loadWithComponents(tagCopy, serverLevel.registryAccess());
            targetBlockEntity.setChanged();
        }

        if (parentChanged) {
            refreshSubLevelAfterManualBlockWrites(serverLevel, parentSubLevel);
        }

        clearState();
    }

    private void restoreSubLevelToWorld(ServerLevel serverLevel, ServerSubLevel serverSubLevel, @Nullable BlockPos protectedPos) {
        Map<BlockPos, RestoredBlockData> restoreMap = new LinkedHashMap<>();
        List<BlockPos> sourceBlocks = new ArrayList<>();

        for (BlockPos sourcePos : BlockPos.betweenClosedStream(serverSubLevel.getPlot().getBoundingBox().toMojang()).map(BlockPos::immutable).toList()) {
            BlockState state = serverLevel.getBlockState(sourcePos);
            if (state.isAir()) {
                continue;
            }

            sourceBlocks.add(sourcePos);

            BlockEntity blockEntity = serverLevel.getBlockEntity(sourcePos);
            CompoundTag blockEntityTag = blockEntity != null
                    ? blockEntity.saveWithFullMetadata(serverLevel.registryAccess())
                    : null;

            Vector3d transformedCenter = serverSubLevel.logicalPose().transformPosition(JOMLConversion.atCenterOf(sourcePos), new Vector3d());
            BlockPos targetPos = BlockPos.containing(transformedCenter.x, transformedCenter.y, transformedCenter.z);
            restoreMap.putIfAbsent(targetPos, new RestoredBlockData(state, blockEntityTag));
        }

        for (BlockPos sourcePos : sourceBlocks) {
            serverLevel.removeBlockEntity(sourcePos);
            serverLevel.setBlock(sourcePos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
        }

        removeSubLevel(serverLevel, serverSubLevel);

        for (Map.Entry<BlockPos, RestoredBlockData> entry : restoreMap.entrySet()) {
            BlockPos targetPos = entry.getKey();
            RestoredBlockData data = entry.getValue();

            if (targetPos.equals(protectedPos)) {
                continue;
            }

            serverLevel.destroyBlock(targetPos, false);
            serverLevel.setBlock(targetPos, data.state(),
                    Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);

            CompoundTag blockEntityTag = data.blockEntityTag();
            if (blockEntityTag == null) {
                continue;
            }

            BlockEntity targetBlockEntity = serverLevel.getBlockEntity(targetPos);
            if (targetBlockEntity == null) {
                continue;
            }

            CompoundTag tagCopy = blockEntityTag.copy();
            tagCopy.putInt("x", targetPos.getX());
            tagCopy.putInt("y", targetPos.getY());
            tagCopy.putInt("z", targetPos.getZ());
            targetBlockEntity.loadWithComponents(tagCopy, serverLevel.registryAccess());
            targetBlockEntity.setChanged();
        }

        clearState();
    }

    private boolean setBlockInSubLevelPlot(ServerLevel serverLevel, ServerSubLevel targetSubLevel, BlockPos targetPos, BlockState state) {
        targetSubLevel.getPlot().expandIfNecessary(targetPos);

        ChunkPos chunkPos = new ChunkPos(targetPos);
        LevelChunk chunk = targetSubLevel.getPlot().getChunk(targetSubLevel.getPlot().toLocal(chunkPos));
        if (chunk == null) {
            targetSubLevel.getPlot().newEmptyChunk(chunkPos);
            chunk = targetSubLevel.getPlot().getChunk(targetSubLevel.getPlot().toLocal(chunkPos));
        }
        if (chunk == null) {
            return false;
        }

        BlockState oldState = chunk.setBlockState(targetPos, state, true);
        if (oldState == null) {
            oldState = Blocks.AIR.defaultBlockState();
        }

        SubLevelAssemblyHelper.markAndNotifyBlock(
                serverLevel,
                targetPos,
                chunk,
                oldState,
                state,
                Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE,
                512
        );
        targetSubLevel.getPlot().onBlockChange(targetPos, state);
        return true;
    }

    @SuppressWarnings("UnstableApiUsage")
    private void refreshSubLevelAfterManualBlockWrites(ServerLevel serverLevel, ServerSubLevel targetSubLevel) {
        targetSubLevel.getPlot().updateBoundingBox();
        targetSubLevel.updateMergedMassData(1.0F);

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container != null) {
            container.physicsSystem().getPipeline().onStatsChanged(targetSubLevel);
        }
    }

    private void removeSubLevel(ServerLevel serverLevel, ServerSubLevel serverSubLevel) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return;
        }

        try {
            if (!serverSubLevel.isRemoved()) {
                container.removeSubLevel(serverSubLevel, SubLevelRemovalReason.REMOVED);
            }
        } catch (Exception ignored) {
        }
    }

    @Nullable
    private ServerSubLevel resolveSubLevel(ServerLevel serverLevel) {
        return resolveSubLevelDetailed(serverLevel).subLevel();
    }

    private ResolveSubLevelResult resolveSubLevelDetailed(ServerLevel serverLevel) {
        if (!active || subLevelId == null) {
            return ResolveSubLevelResult.failure(RefreshFailureReason.INACTIVE);
        }

        if (subLevel != null && !subLevel.isRemoved() && subLevelId.equals(subLevel.getUniqueId())) {
            clearResolveFailureDiagnostics();
            return ResolveSubLevelResult.success(subLevel);
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            logBackendResolveFailure(serverLevel, subLevelId, false, false, false, "container-null");
            return ResolveSubLevelResult.failure(RefreshFailureReason.CONTAINER_UNAVAILABLE);
        }

        SubLevel resolved = container.getSubLevel(subLevelId);
        if (resolved instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            subLevel = serverSubLevel;
            clearResolveFailureDiagnostics();
            return ResolveSubLevelResult.success(serverSubLevel);
        }

        if (resolved instanceof ServerSubLevel) {
            logBackendResolveFailure(serverLevel, subLevelId, true, true, true, "sublevel-removed");
            return ResolveSubLevelResult.failure(RefreshFailureReason.SUBLEVEL_REMOVED);
        } else {
            logBackendResolveFailure(serverLevel, subLevelId, true, resolved != null, false,
                    resolved == null ? "getSubLevel-null" : "not-server-sublevel");
            return ResolveSubLevelResult.failure(resolved == null
                    ? RefreshFailureReason.SUBLEVEL_NOT_FOUND
                    : RefreshFailureReason.NOT_SERVER_SUBLEVEL);
        }
    }

    private static VerifiedMotorApplyStatus mapVerifiedMotorFailure(RefreshFailureReason failureReason) {
        return switch (failureReason) {
            case CONTAINER_UNAVAILABLE, SUBLEVEL_NOT_FOUND, BASE_CONTEXT_UNAVAILABLE,
                    PARENT_SUBLEVEL_NOT_READY -> VerifiedMotorApplyStatus.RETRYABLE_UNRESOLVED;
            case CONSTRAINT_ATTACH_FAILED, CONSTRAINT_REATTACH_PENDING ->
                    VerifiedMotorApplyStatus.RETRYABLE_REBIND;
            case INACTIVE, SUBLEVEL_REMOVED, NOT_SERVER_SUBLEVEL, INVALID_TWO_AXIS_POSE, NONE ->
                    VerifiedMotorApplyStatus.INVALID;
        };
    }

    private void logBackendReadDiagnostics(
            String event,
            String activeKey,
            String idKey,
            boolean containsActive,
            @Nullable Boolean rawActive,
            boolean hasId,
            @Nullable UUID rawId,
            String reason
    ) {
        if (!TwisterMillDiagnostics.isLoggingEnabled(diagnosticsTarget)) {
            return;
        }

        LOGGER.info("[SableBackendStateDiag] target={} event={} activeKey={} idKey={} rawContainsActive={} rawActive={} rawHasId={} rawId={} active={} subLevelId={} resolveAttempted=false reason={}",
                diagnosticsTarget,
                event,
                activeKey,
                idKey,
                containsActive,
                rawActive,
                hasId,
                rawId,
                active,
                subLevelId,
                reason);
    }

    private void logBackendClearStateDiagnostics() {
        if (!TwisterMillDiagnostics.isLoggingEnabled(diagnosticsTarget)) {
            return;
        }

        LOGGER.info("[SableBackendStateDiag] target={} event=clear-state activeBefore={} subLevelIdBefore={} hadCachedSubLevel={} hadConstraintHandle={} constraintBaseSubLevelId={}",
                diagnosticsTarget,
                active,
                subLevelId,
                subLevel != null,
                constraintHandle != null,
                constraintBaseSubLevelId);
    }

    private void logBackendResolveFailure(
            ServerLevel serverLevel,
            @Nullable UUID requestedSubLevelId,
            boolean containerPresent,
            boolean resolved,
            boolean removed,
            String reason
    ) {
        if (!TwisterMillDiagnostics.isLoggingEnabled(diagnosticsTarget)) {
            return;
        }

        long time = serverLevel.getGameTime();
        boolean failureChanged = !Objects.equals(requestedSubLevelId, diagnosticLastResolveFailureSubLevelId)
                || !Objects.equals(reason, diagnosticLastResolveFailureReason);
        boolean intervalElapsed = diagnosticLastResolveFailureLogTick == Long.MIN_VALUE
                || time < diagnosticLastResolveFailureLogTick
                || time - diagnosticLastResolveFailureLogTick >= BACKEND_RESOLVE_FAILURE_LOG_INTERVAL_TICKS;
        if (!failureChanged && !intervalElapsed) {
            return;
        }

        diagnosticLastResolveFailureSubLevelId = requestedSubLevelId;
        diagnosticLastResolveFailureReason = reason;
        diagnosticLastResolveFailureLogTick = time;
        LOGGER.info("[SableBackendStateDiag] target={} event=resolve-failed dimension={} requestedSubLevelId={} containerPresent={} resolved={} removed={} reason={}",
                diagnosticsTarget,
                serverLevel.dimension().location(),
                requestedSubLevelId,
                containerPresent,
                resolved,
                removed,
                reason);
    }

    private void clearResolveFailureDiagnostics() {
        diagnosticLastResolveFailureSubLevelId = null;
        diagnosticLastResolveFailureReason = null;
        diagnosticLastResolveFailureLogTick = Long.MIN_VALUE;
    }

    private void removeConstraintHandle() {
        if (constraintHandle != null) {
            try {
                if (constraintHandle.isValid()) {
                    constraintHandle.remove();
                }
            } catch (Exception ignored) {
            }
        }

        constraintHandle = null;
        constraintRotationProfile = null;
        clearConstraintBase();
    }

    private void clearRuntimeCache() {
        subLevel = null;
        constraintHandle = null;
        constraintRotationProfile = null;
        constraintReattachPending = false;
        pendingReattachProfile = RotationProfile.FACING_AXIS;
        clearConstraintBase();
        diagnosticLastBaseSubLevelId = null;
        diagnosticLastBaseInitialized = false;
        diagnosticLastBaseWasRoot = false;
        clearResolveFailureDiagnostics();
    }

    static Vector3d computeAnchorLocalCenter(ServerSubLevel subLevel, BlockPos anchorWorld) {
        if (!(subLevel.getLevel() instanceof ServerLevel serverLevel)) {
            return JOMLConversion.atCenterOf(subLevel.getPlot().getCenterBlock());
        }

        BlockPos plotCenter = subLevel.getPlot().getCenterBlock();
        SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(
                anchorWorld, plotCenter, 0, Rotation.NONE, serverLevel
        );

        BlockPos anchorLocal = transform.apply(anchorWorld);
        return JOMLConversion.atCenterOf(anchorLocal);
    }

    private static Vector3d computeAttachedLocalCenter(ServerLevel serverLevel, ServerSubLevel subLevel,
            BlockPos assemblyAnchor, Vec3 sourceCenter) {
        BlockPos plotCenter = subLevel.getPlot().getCenterBlock();
        SubLevelAssemblyHelper.AssemblyTransform transform = new SubLevelAssemblyHelper.AssemblyTransform(
                assemblyAnchor, plotCenter, 0, Rotation.NONE, serverLevel
        );
        Vec3 localCenter = transform.apply(sourceCenter);
        return new Vector3d(localCenter.x, localCenter.y, localCenter.z);
    }

    private static BlockPos constraintPivotBlock(BlockPos bearingPos, Direction facing,
            RotationProfile rotationProfile) {
        return rotationProfile == RotationProfile.FACING_AXIS ? bearingPos.relative(facing) : bearingPos;
    }

    private static double computeServoAngleRadians(Direction facing, RotationProfile rotationProfile,
            float angleDegrees) {
        if (rotationProfile == RotationProfile.FACING_AXIS && servoMotorSign(facing) < 0.0D) {
            angleDegrees = -angleDegrees;
        }

        return Math.toRadians(angleDegrees);
    }

    private static float snapAngle(float angle) {
        return Math.round(angle / ANGLE_STEP_DEGREES) * ANGLE_STEP_DEGREES;
    }



    private static void addConnectedVanillaWoolCluster(Level world, BlockPos startPos,
                                                       Queue<BlockPos> frontier) throws AssemblyException {
        BlockState startState = world.getBlockState(startPos);
        if (!startState.is(TWISTERMILL_SAIL_LIKE) && !isVanillaFullWool(startState)) {
            return;
        }

        Queue<BlockPos> scanQueue = new ArrayDeque<>();
        Set<BlockPos> visited = new LinkedHashSet<>();
        int maxWoolClusterBlocks = Math.max(1, AllConfigs.server().kinetics.maxBlocksMoved.get());

        if (isVanillaFullWool(startState)) {
            BlockPos immutableStart = startPos.immutable();
            scanQueue.add(immutableStart);
            visited.add(immutableStart);
        }

        for (Direction direction : Direction.values()) {
            BlockPos seed = startPos.relative(direction).immutable();
            if (world.isOutsideBuildHeight(seed) || !world.isLoaded(seed)) {
                continue;
            }

            if (isVanillaFullWool(world.getBlockState(seed)) && visited.add(seed)) {
                scanQueue.add(seed);
            }
        }

        while (!scanQueue.isEmpty()) {
            BlockPos current = scanQueue.poll();
            if (!current.equals(startPos)) {
                frontier.add(current);
            }

            if (visited.size() > maxWoolClusterBlocks) {
                throw AssemblyException.structureTooLarge();
            }

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (visited.contains(next) || world.isOutsideBuildHeight(next) || !world.isLoaded(next)) {
                    continue;
                }

                BlockState nextState = world.getBlockState(next);
                if (!isVanillaFullWool(nextState)) {
                    continue;
                }

                visited.add(next);
                scanQueue.add(next);
            }
        }
    }

    private static boolean isVanillaFullWool(BlockState state) {
        return VANILLA_WOOL_BLOCKS.contains(state.getBlock());
    }

    private static final class TwisterMillBearingContraption extends BearingContraption {
        private TwisterMillBearingContraption(boolean isWindmill, Direction facing) {
            super(isWindmill, facing);
        }

        @Override
        protected boolean addToInitialFrontier(Level world, BlockPos pos, Direction forcedDirection,
                                               Queue<BlockPos> frontier) throws AssemblyException {
            if (!super.addToInitialFrontier(world, pos, forcedDirection, frontier)) {
                return false;
            }

            addConnectedVanillaWoolCluster(world, pos, frontier);
            return true;
        }
    }

    record AssemblyResult(int blockCount) {
    }

    record Mode3ReturnMotorCommand(
            long gameTime,
            UUID subLevelId,
            Direction facing,
            PhysicsConstraintHandle constraintHandle,
            float targetAngleDegrees,
            double targetRadians
    ) {
    }

    record Mode3RelativePoseProbe(
            long gameTime,
            UUID subLevelId,
            @Nullable UUID parentSubLevelId,
            boolean rootParent,
            BlockPos plotCenter,
            BlockPos assemblyAnchor,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ,
            Pose3d topPose,
            @Nullable Pose3d parentPose,
            float facingAxisAngleDegrees
    ) {
    }

    record Mode3PosePrecheck(
            boolean eligible,
            String failureReason,
            float actualAngleDegrees,
            boolean physicalAngleWithinLimit
    ) {
        private static Mode3PosePrecheck failed(String failureReason) {
            return new Mode3PosePrecheck(
                    false,
                    failureReason,
                    Float.NaN,
                    false
            );
        }
    }

    record Mode3DisassemblySafety(
            boolean safe,
            String failureReason,
            @Nullable Mode3RelativePoseProbe currentProbe,
            boolean motorTargetExactlyZero,
            boolean parentOrRootResolved,
            boolean consecutivePhysicsTicks,
            boolean allFinite,
            boolean physicalAngleWithinLimit,
            boolean mappingMatchesExpected,
            boolean actualTargetsUnique,
            boolean sourceAndTargetCountsMatch,
            boolean protectedPositionClear,
            int sourceBlockCount,
            int uniqueActualTargetCount,
            float actualAngleDegrees
    ) {
        private static Mode3DisassemblySafety failed(
                String failureReason,
                @Nullable Mode3RelativePoseProbe currentProbe,
                @Nullable Mode3PosePrecheck precheck
        ) {
            return new Mode3DisassemblySafety(
                    false,
                    failureReason,
                    currentProbe,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    0,
                    0,
                    precheck == null ? Float.NaN : precheck.actualAngleDegrees()
            );
        }
    }

    enum RefreshFailureReason {
        NONE,
        INACTIVE,
        CONTAINER_UNAVAILABLE,
        SUBLEVEL_NOT_FOUND,
        BASE_CONTEXT_UNAVAILABLE,
        PARENT_SUBLEVEL_NOT_READY,
        SUBLEVEL_REMOVED,
        NOT_SERVER_SUBLEVEL,
        INVALID_TWO_AXIS_POSE,
        CONSTRAINT_ATTACH_FAILED,
        CONSTRAINT_REATTACH_PENDING
    }

    record RefreshResult(boolean success, RefreshFailureReason failureReason) {
        static RefreshResult ok() {
            return new RefreshResult(true, RefreshFailureReason.NONE);
        }

        static RefreshResult failed(RefreshFailureReason failureReason) {
            RefreshFailureReason reason = failureReason == null ? RefreshFailureReason.CONSTRAINT_ATTACH_FAILED : failureReason;
            return new RefreshResult(false, reason);
        }
    }

    enum VerifiedMotorApplyStatus {
        APPLIED,
        RETRYABLE_UNRESOLVED,
        RETRYABLE_REBIND,
        INVALID
    }

    record VerifiedMotorApplyResult(VerifiedMotorApplyStatus status) {
        static VerifiedMotorApplyResult applied() {
            return new VerifiedMotorApplyResult(VerifiedMotorApplyStatus.APPLIED);
        }

        static VerifiedMotorApplyResult failed(VerifiedMotorApplyStatus status) {
            return new VerifiedMotorApplyResult(status);
        }

        boolean appliedSuccessfully() {
            return status == VerifiedMotorApplyStatus.APPLIED;
        }
    }

    record TwoAxisRecoveryRefreshResult(
            VerifiedMotorApplyStatus status,
            RefreshFailureReason failureReason,
            @Nullable TwoAxisAngles initializedAngles
    ) {
        static TwoAxisRecoveryRefreshResult ready(@Nullable TwoAxisAngles initializedAngles) {
            return new TwoAxisRecoveryRefreshResult(
                    VerifiedMotorApplyStatus.APPLIED,
                    RefreshFailureReason.NONE,
                    initializedAngles
            );
        }

        static TwoAxisRecoveryRefreshResult failed(
                VerifiedMotorApplyStatus status,
                RefreshFailureReason failureReason
        ) {
            return new TwoAxisRecoveryRefreshResult(status, failureReason, null);
        }

        boolean readyForControl() {
            return status == VerifiedMotorApplyStatus.APPLIED;
        }

        boolean retryable() {
            return status == VerifiedMotorApplyStatus.RETRYABLE_UNRESOLVED
                    || status == VerifiedMotorApplyStatus.RETRYABLE_REBIND;
        }
    }

    @SuppressWarnings("unused")
    record RuntimeDiagnosticsSnapshot(
            boolean sableActive,
            boolean sableAttached,
            @Nullable UUID activeSubLevelId,
            @Nullable UUID attachedId,
            @Nullable Vector3d attachedLinearVelocity,
            @Nullable Vector3d attachedAngularVelocity,
            double attachedAngularVelocityLength
    ) {
    }

    record ReloadStabilizationResult(
            String action,
            boolean velocityReset,
            boolean poseReseatApplied,
            @Nullable UUID activeSubLevelId,
            @Nullable UUID attachedId,
            @Nullable Vector3d linearVelocityBefore,
            @Nullable Vector3d angularVelocityBefore,
            @Nullable Vector3d linearVelocityAfter,
            @Nullable Vector3d angularVelocityAfter,
            double anchorWorldError,
            double normalWorldError,
            double anchorWorldErrorAfter,
            double normalWorldErrorAfter,
            boolean thresholdBreach,
            @Nullable Vector3d comToContactLocal,
            @Nullable String attachedPoseBefore,
            @Nullable String attachedPoseAfter,
            @Nullable Vector3d anchorToComWorldBefore,
            @Nullable Vector3d anchorToComWorldAfter
    ) {
        private static ReloadStabilizationResult skipped(
                String action,
                @Nullable UUID activeSubLevelId,
                @Nullable UUID attachedId
        ) {
            return new ReloadStabilizationResult(
                    action,
                    false,
                    false,
                    activeSubLevelId,
                    attachedId,
                    null,
                    null,
                    null,
                    null,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    record ReloadReattachDiagnosticsSnapshot(
            String action,
            @Nullable UUID activeSubLevelId,
            @Nullable UUID attachedId,
            @SuppressWarnings("SameParameterValue") String shipId,
            boolean constraintHandleValid,
            @Nullable String attachedLogicalPose,
            @Nullable String attachedLastPose,
            @SuppressWarnings("SameParameterValue") String shipWorldTransform,
            @Nullable Vector3d baseAnchorWorld,
            @Nullable Vector3d attachedAnchorWorld,
            @Nullable Vector3d attachedCom,
            @Nullable Vector3d attachedRotationPoint,
            @Nullable Vector3d comToContactLocal,
            @Nullable Vector3d anchorToComWorld,
            @Nullable Vector3d linearVelocity,
            @Nullable Vector3d angularVelocity,
            boolean velocityNonZeroSinceReset,
            boolean angularVelocityNonZeroSinceReset,
            double anchorWorldError,
            double normalWorldError,
            boolean thresholdBreach
    ) {
        private static ReloadReattachDiagnosticsSnapshot skipped(
                String action,
                @Nullable UUID activeSubLevelId,
                @Nullable UUID attachedId
        ) {
            return new ReloadReattachDiagnosticsSnapshot(
                    action,
                    activeSubLevelId,
                    attachedId,
                    "<unavailable>",
                    false,
                    null,
                    null,
                    "<unavailable>",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    Double.NaN,
                    Double.NaN,
                    false
            );
        }
    }

    private record DiagnosticConstraint(@Nullable ServerSubLevel baseSubLevel, RotaryConstraintConfiguration configuration) {
    }

    private record Mode3RestoreContext(
            @Nullable ServerSubLevel parentSubLevel,
            boolean rootParent,
            boolean resolved
    ) {
        private static Mode3RestoreContext unresolved() {
            return new Mode3RestoreContext(null, false, false);
        }
    }

    private sealed interface PreparedRotationConstraint permits PreparedConstraint, PreparedTwoAxisConstraint {
        @Nullable
        ServerSubLevel baseSubLevel();

        BlockPos constraintPivotBlock();

        RotationProfile rotationProfile();

        void validate(ServerSubLevelContainer container, ServerSubLevel attachedSubLevel);

        PhysicsConstraintHandle attach(PhysicsPipeline pipeline, ServerSubLevel attachedSubLevel);
    }

    private record PreparedConstraint(
            @Nullable ServerSubLevel baseSubLevel,
            RotaryConstraintConfiguration configuration,
            BlockPos constraintPivotBlock,
            RotationProfile rotationProfile
    ) implements PreparedRotationConstraint {
        @Override
        public void validate(ServerSubLevelContainer container, ServerSubLevel attachedSubLevel) {
            configuration.validate(container, baseSubLevel, attachedSubLevel);
        }

        @Override
        public PhysicsConstraintHandle attach(PhysicsPipeline pipeline, ServerSubLevel attachedSubLevel) {
            return pipeline.addConstraint(baseSubLevel, attachedSubLevel, configuration);
        }
    }

    private record PreparedTwoAxisConstraint(
            @Nullable ServerSubLevel baseSubLevel,
            GenericConstraintConfiguration configuration,
            BlockPos constraintPivotBlock,
            @SuppressWarnings("SameParameterValue") RotationProfile rotationProfile
    ) implements PreparedRotationConstraint {
        private static final double LIMIT_RADIANS = Math.PI * 0.5D;

        @Override
        public void validate(ServerSubLevelContainer container, ServerSubLevel attachedSubLevel) {
            configuration.validate(container, baseSubLevel, attachedSubLevel);
            if (!Double.isFinite(LIMIT_RADIANS)) {
                throw new IllegalStateException("Two-axis angular limit is not finite");
            }
        }

        @Override
        public PhysicsConstraintHandle attach(PhysicsPipeline pipeline, ServerSubLevel attachedSubLevel) {
            GenericConstraintHandle handle = pipeline.addConstraint(baseSubLevel, attachedSubLevel, configuration);
            if (handle == null || !handle.isValid()) {
                return handle;
            }
            try {
                handle.lockAxes(
                        ConstraintJointAxis.LINEAR_X,
                        ConstraintJointAxis.LINEAR_Y,
                        ConstraintJointAxis.LINEAR_Z,
                        ConstraintJointAxis.ANGULAR_Y
                );
                handle.setLimit(ConstraintJointAxis.ANGULAR_X, -LIMIT_RADIANS, LIMIT_RADIANS);
                handle.setLimit(ConstraintJointAxis.ANGULAR_Z, -LIMIT_RADIANS, LIMIT_RADIANS);
                handle.setContactsEnabled(false);
                return handle;
            } catch (RuntimeException exception) {
                try {
                    if (handle.isValid()) {
                        handle.remove();
                    }
                } catch (RuntimeException removeFailure) {
                    exception.addSuppressed(removeFailure);
                }
                throw exception;
            }
        }
    }

    record TwoAxisAngles(float axis1Degrees, float axis2Degrees, float totalSwingDegrees, float twistDegrees) {
    }

    private record ResolveSubLevelResult(@Nullable ServerSubLevel subLevel, RefreshFailureReason failureReason) {
        private static ResolveSubLevelResult success(ServerSubLevel subLevel) {
            return new ResolveSubLevelResult(subLevel, RefreshFailureReason.NONE);
        }

        private static ResolveSubLevelResult failure(RefreshFailureReason failureReason) {
            return new ResolveSubLevelResult(null, failureReason);
        }
    }

    private record DiagnosticFrame(
            Vector3d expectedAttachedAnchorLocal,
            double pos2ExpectedDelta,
            Vector3d baseAnchorWorld,
            Vector3d attachedAnchorWorld,
            double anchorWorldError,
            Vector3d normal1World,
            Vector3d normal2World,
            double normalWorldError
    ) {
    }

    private record RestoredBlockData(BlockState state, @Nullable CompoundTag blockEntityTag) {
    }
}
