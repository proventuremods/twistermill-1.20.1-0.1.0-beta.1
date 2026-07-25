package com.proventure.twistermill.blockentity;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
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
    private transient RotaryConstraintHandle constraintHandle;
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
    private final TwisterMillDiagnostics.Target diagnosticsTarget;

    SableInteractiveContraptionBackend(TwisterMillDiagnostics.Target diagnosticsTarget) {
        this.diagnosticsTarget = diagnosticsTarget;
    }

    boolean isActive() {
        return active;
    }

    @Nullable
    UUID getActiveSubLevelId() {
        if (!active || subLevelId == null) {
            return null;
        }
        return subLevelId;
    }

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
        Vector3d comToContactLocal = null;
        if (diagnostic != null) {
            comToContactLocal = new Vector3d(attachedSubLevel.getMassTracker().getCenterOfMass())
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
                frame == null ? null : computeAnchorToComWorld(attachedSubLevel.logicalPose(),
                        attachedSubLevel.getMassTracker().getCenterOfMass(), frame),
                frame == null ? null : computeAnchorToComWorld(attachedSubLevel.logicalPose(),
                        attachedSubLevel.getMassTracker().getCenterOfMass(), frame)
        );
    }

    ReloadStabilizationResult manualCommandReseat(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        return reseatAttachedSubLevel(
                serverLevel,
                bearingPos,
                facing,
                stiffnessPerInertia,
                dampingPerInertia,
                minEffectiveInertia,
                "manual-command"
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

        BlockPos anchorWorld = bearingPos.relative(facing);
        DiagnosticConstraint diagnostic = buildDiagnosticConstraintConfiguration(serverLevel, attachedSubLevel, anchorWorld, facing);
        DiagnosticFrame frame = diagnostic == null
                ? null
                : computeDiagnosticFrame(diagnostic.baseSubLevel(), attachedSubLevel, diagnostic.configuration());
        boolean thresholdBreach = frame != null && isDiagnosticThresholdBreach(frame);
        Vector3d comToContactLocal = null;
        if (diagnostic != null) {
            comToContactLocal = new Vector3d(attachedSubLevel.getMassTracker().getCenterOfMass())
                    .sub(diagnostic.configuration().pos2());
        }
        Vector3d anchorToComWorldBefore = frame == null
                ? null
                : computeAnchorToComWorld(poseBefore, attachedSubLevel.getMassTracker().getCenterOfMass(), frame);

        String safetyFailure = reseatSafetyFailure(frame, actionPrefix);
        if (safetyFailure != null || diagnostic == null) {
            return new ReloadStabilizationResult(
                    safetyFailure == null ? actionPrefix + "-frame-unavailable" : safetyFailure,
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
                                    attachedSubLevel.getMassTracker().getCenterOfMass(),
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
        Vector3d rotationPoint = new Vector3d(attachedSubLevel.getMassTracker().getCenterOfMass());
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

        boolean reattached = attachConstraint(serverLevel, attachedSubLevel, bearingPos, anchorWorld, facing);
        if (reattached && constraintHandle != null && constraintHandle.isValid()) {
            double effectiveInertia = computeEffectiveInertia(attachedSubLevel, facing, minEffectiveInertia);
            double stiffness = stiffnessPerInertia * effectiveInertia;
            double damping = dampingPerInertia * effectiveInertia;
            constraintHandle.setMotor(RotaryConstraintHandle.DEFAULT_AXIS, computeServoAngleRadians(facing, 0.0F),
                    stiffness, damping, false, 0.0);
            constraintHandle.setContactsEnabled(false);
            pipeline.resetVelocity(attachedSubLevel);
            pipeline.wakeUp(attachedSubLevel);
            attachedSubLevel.updateLastPose();
        }

        DiagnosticConstraint diagnosticAfter = buildDiagnosticConstraintConfiguration(serverLevel, attachedSubLevel, anchorWorld, facing);
        DiagnosticFrame frameAfter = diagnosticAfter == null
                ? null
                : computeDiagnosticFrame(diagnosticAfter.baseSubLevel(), attachedSubLevel, diagnosticAfter.configuration());
        Vector3d anchorToComWorldAfter = frameAfter == null
                ? null
                : computeAnchorToComWorld(attachedSubLevel.logicalPose(), attachedSubLevel.getMassTracker().getCenterOfMass(), frameAfter);

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
        Vector3d attachedCom = new Vector3d(attachedSubLevel.getMassTracker().getCenterOfMass());
        Vector3d attachedRotationPoint = new Vector3d(attachedLogicalPose.rotationPoint());
        Vector3d comToContactLocal = diagnostic == null
                ? null
                : new Vector3d(attachedCom).sub(diagnostic.configuration().pos2());
        Vector3d anchorToComWorld = null;
        if (frame != null) {
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
            boolean requiresWindmillSails,
            @Nullable Consumer<AssemblyException> exceptionConsumer
    ) {
        return tryAssemble(serverLevel, bearingPos, facing, requiresWindmillSails, exceptionConsumer, null);
    }

    @Nullable
    AssemblyResult tryAssemble(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            boolean requiresWindmillSails,
            @Nullable Consumer<AssemblyException> exceptionConsumer,
            @Nullable RememberedSableShipMemory rememberedShipMemory
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

        ServerSubLevel assembledSubLevel = null;
        try {
            assembledSubLevel = SubLevelAssemblyHelper.assembleBlocks(serverLevel, anchorWorld, capturedBlocks, bounds);
        } catch (Exception ignored) {
            cleanupFailedAssembly(serverLevel, assembledSubLevel, bearingPos);
            return null;
        }

        return finishAssembly(serverLevel, bearingPos, facing, assembledSubLevel, capturedBlocks.size());
    }

    @Nullable
    private AssemblyResult finishAssembly(
            ServerLevel serverLevel,
            BlockPos bearingPos,
            Direction facing,
            @Nullable ServerSubLevel assembledSubLevel,
            int blockCount
    ) {
        if (assembledSubLevel == null || assembledSubLevel.getMassTracker().isInvalid()) {
            cleanupFailedAssembly(serverLevel, assembledSubLevel, bearingPos);
            return null;
        }

        if (!activate(assembledSubLevel, serverLevel, bearingPos, facing)) {
            cleanupFailedAssembly(serverLevel, assembledSubLevel, bearingPos);
            return null;
        }

        return new AssemblyResult(blockCount);
    }

    boolean refresh(ServerLevel serverLevel, BlockPos bearingPos, Direction facing) {
        return refreshDetailed(serverLevel, bearingPos, facing).success();
    }

    RefreshResult refreshDetailed(ServerLevel serverLevel, BlockPos bearingPos, Direction facing) {
        if (!active) {
            return RefreshResult.failed(RefreshFailureReason.INACTIVE);
        }

        ResolveSubLevelResult resolved = resolveSubLevelDetailed(serverLevel);
        if (resolved.subLevel() == null) {
            return RefreshResult.failed(resolved.failureReason());
        }

        if (!ensureConstraintAttached(serverLevel, resolved.subLevel(), bearingPos, facing)) {
            return RefreshResult.failed(RefreshFailureReason.CONSTRAINT_ATTACH_FAILED);
        }

        return RefreshResult.ok();
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
        return applyAngleMotor(
                serverLevel,
                bearingPos,
                facing,
                angleDegrees,
                stiffnessPerInertia,
                dampingPerInertia,
                minEffectiveInertia
        );
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
        double cos = clamp(currentReferenceWorld.dot(desiredWorld), -1.0D, 1.0D);
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
            float angleDegrees,
            double stiffnessPerInertia,
            double dampingPerInertia,
            double minEffectiveInertia
    ) {
        ServerSubLevel resolvedSubLevel = resolveSubLevel(serverLevel);
        if (resolvedSubLevel == null) {
            return false;
        }

        if (!ensureConstraintAttached(serverLevel, resolvedSubLevel, bearingPos, facing) || constraintHandle == null) {
            return false;
        }

        angleDegrees = snapAngle(angleDegrees);
        double goal = computeServoAngleRadians(facing, angleDegrees);
        double effectiveInertia = computeEffectiveInertia(resolvedSubLevel, facing, minEffectiveInertia);
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

    private boolean activate(ServerSubLevel serverSubLevel, ServerLevel level, BlockPos bearingPos, Direction facing) {
        active = true;
        subLevelId = serverSubLevel.getUniqueId();
        subLevel = serverSubLevel;
        constraintHandle = null;

        if (!ensureConstraintAttached(level, serverSubLevel, bearingPos, facing)) {
            clearState();
            return false;
        }

        return true;
    }

    private boolean ensureConstraintAttached(ServerLevel serverLevel, ServerSubLevel serverSubLevel, BlockPos bearingPos, Direction facing) {
        if (constraintHandle != null && constraintHandle.isValid()) {
            BlockPos anchorWorld = bearingPos.relative(facing);
            ServerSubLevel baseSubLevel = resolveBaseSubLevel(serverLevel, anchorWorld);
            logExistingConstraintCandidateDiagnostics(serverLevel, serverSubLevel, bearingPos, facing);
            DiagnosticConstraint diagnostic = buildDiagnosticConstraintConfiguration(serverLevel, serverSubLevel, anchorWorld, facing);
            DiagnosticFrame frame = diagnostic == null
                    ? null
                    : computeDiagnosticFrame(diagnostic.baseSubLevel(), serverSubLevel, diagnostic.configuration());
            boolean thresholdBreach = frame != null && isDiagnosticThresholdBreach(frame);
            if (isConstraintBaseCurrent(baseSubLevel) && !thresholdBreach) {
                return true;
            }

            removeConstraintHandle();
            if (thresholdBreach && diagnostic != null) {
                realignAttachedSubLevelToConstraintAnchor(
                        serverLevel,
                        serverSubLevel,
                        bearingPos,
                        anchorWorld,
                        facing,
                        diagnostic.baseSubLevel(),
                        diagnostic.configuration(),
                        frame
                );
            }
            return attachConstraint(serverLevel, serverSubLevel, bearingPos, anchorWorld, facing);
        }

        removeConstraintHandle();
        return attachConstraint(serverLevel, serverSubLevel, bearingPos, bearingPos.relative(facing), facing);
    }

    private boolean attachConstraint(ServerLevel serverLevel, ServerSubLevel attachedSubLevel, BlockPos bearingPos, BlockPos anchorWorld, Direction facing) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return false;
        }

        Vector3d axis = axisFromFacing(facing);
        if (axis.lengthSquared() <= 1.0E-12) {
            return false;
        }
        axis.normalize();

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        ServerSubLevel baseSubLevel = resolveBaseSubLevel(serverLevel, anchorWorld);

        RotaryConstraintConfiguration configuration = buildConstraintConfiguration(
                baseSubLevel,
                attachedSubLevel,
                anchorWorld,
                axis
        );
        if (configuration == null) {
            return false;
        }

        if (baseSubLevel != null) {
            DiagnosticFrame frame = computeDiagnosticFrame(baseSubLevel, attachedSubLevel, configuration);
            if (frame != null && isDiagnosticThresholdBreach(frame)) {
                realignAttachedSubLevelToConstraintAnchor(
                        serverLevel,
                        attachedSubLevel,
                        bearingPos,
                        anchorWorld,
                        facing,
                        baseSubLevel,
                        configuration,
                        frame
                );
            }
        }
        logServoTopFrameDiagnostics(pipeline, baseSubLevel, attachedSubLevel, anchorWorld, facing, configuration);
        logSableAttachDiagnostics("attach", pipeline, serverLevel, bearingPos, anchorWorld, facing, baseSubLevel,
                attachedSubLevel, configuration, true);

        try {
            constraintHandle = pipeline.addConstraint(baseSubLevel, attachedSubLevel, configuration);
        } catch (Exception e) {
            LOGGER.warn("Failed to attach Sable rotary constraint at {}", anchorWorld, e);
            constraintHandle = null;
        }

        if (constraintHandle != null && constraintHandle.isValid()) {
            rememberConstraintBase(baseSubLevel);
            return true;
        }
        return false;
    }

    private void logExistingConstraintCandidateDiagnostics(ServerLevel serverLevel, ServerSubLevel attachedSubLevel,
            BlockPos bearingPos, Direction facing) {
        if (!TwisterMillDiagnostics.isLoggingEnabled(diagnosticsTarget)) {
            return;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        BlockPos anchorWorld = bearingPos.relative(facing);
        DiagnosticConstraint diagnostic = buildDiagnosticConstraintConfiguration(serverLevel, attachedSubLevel, anchorWorld, facing);
        if (diagnostic == null) {
            return;
        }

        DiagnosticFrame frame = computeDiagnosticFrame(diagnostic.baseSubLevel(), attachedSubLevel, diagnostic.configuration());
        if (frame == null) {
            return;
        }

        boolean baseChanged = hasDiagnosticBaseChanged(diagnostic.baseSubLevel());
        boolean thresholdBreach = isDiagnosticThresholdBreach(frame);
        if (!baseChanged && !thresholdBreach) {
            return;
        }

        logSableAttachDiagnostics("refresh-valid-handle-candidate", pipeline, serverLevel, bearingPos, anchorWorld, facing,
                diagnostic.baseSubLevel(), attachedSubLevel, diagnostic.configuration(), false);
    }

    @Nullable
    private DiagnosticConstraint buildDiagnosticConstraintConfiguration(ServerLevel serverLevel, ServerSubLevel attachedSubLevel,
            BlockPos anchorWorld, Direction facing) {
        Vector3d axis = axisFromFacing(facing);
        if (axis.lengthSquared() <= 1.0E-12) {
            return null;
        }
        axis.normalize();

        SubLevel containing = Sable.HELPER.getContaining(serverLevel, anchorWorld);
        ServerSubLevel baseSubLevel = containing instanceof ServerSubLevel containingSubLevel ? containingSubLevel : null;

        RotaryConstraintConfiguration configuration = buildConstraintConfiguration(
                baseSubLevel,
                attachedSubLevel,
                anchorWorld,
                axis
        );
        if (configuration == null) {
            return null;
        }

        return new DiagnosticConstraint(baseSubLevel, configuration);
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
        if (frame == null) {
            return;
        }

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

    @Nullable
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
        realignAttachedSubLevelToConstraintAnchor(
                serverLevel,
                attachedSubLevel,
                bearingPos,
                anchorWorld,
                facing,
                baseSubLevel,
                configuration,
                previousFrame,
                "refresh-threshold-realign"
        );
    }

    private void realignAttachedSubLevelToConstraintAnchor(
            ServerLevel serverLevel,
            ServerSubLevel attachedSubLevel,
            BlockPos bearingPos,
            BlockPos anchorWorld,
            Direction facing,
            @Nullable ServerSubLevel baseSubLevel,
            RotaryConstraintConfiguration configuration,
            @Nullable DiagnosticFrame previousFrame,
            String eventName
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
                    eventName,
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double computeEffectiveInertia(ServerSubLevel subLevel, Direction facing, double minimum) {
        Vector3d axis = axisFromFacing(facing);
        if (axis.lengthSquared() <= 1.0E-12) {
            return minimum;
        }
        axis.normalize();

        Vector3d transformed = new Vector3d();
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

    private Vector3d readLinearVelocity(PhysicsPipeline pipeline, ServerSubLevel subLevel) {
        try {
            return pipeline.getLinearVelocity(subLevel, new Vector3d());
        } catch (Exception ignored) {
            return new Vector3d(subLevel.latestLinearVelocity);
        }
    }

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
            Vector3dc centerOfMass,
            @Nullable DiagnosticFrame frame
    ) {
        if (frame == null) {
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
        if (constraintHandle == null) {
            return;
        }

        try {
            if (constraintHandle.isValid()) {
                constraintHandle.remove();
            }
        } catch (Exception ignored) {
        }

        constraintHandle = null;
        clearConstraintBase();
    }

    private void clearRuntimeCache() {
        subLevel = null;
        constraintHandle = null;
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

    private static double computeServoAngleRadians(Direction facing, float angleDegrees) {
        if (servoMotorSign(facing) < 0.0D) {
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

    enum RefreshFailureReason {
        NONE,
        INACTIVE,
        CONTAINER_UNAVAILABLE,
        SUBLEVEL_NOT_FOUND,
        BASE_CONTEXT_UNAVAILABLE,
        PARENT_SUBLEVEL_NOT_READY,
        SUBLEVEL_REMOVED,
        NOT_SERVER_SUBLEVEL,
        CONSTRAINT_ATTACH_FAILED
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
            String shipId,
            boolean constraintHandleValid,
            @Nullable String attachedLogicalPose,
            @Nullable String attachedLastPose,
            String shipWorldTransform,
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
