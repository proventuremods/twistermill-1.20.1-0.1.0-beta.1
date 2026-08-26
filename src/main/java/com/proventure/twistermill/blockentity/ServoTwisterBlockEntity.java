package com.proventure.twistermill.blockentity;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.block.custom.BladeArmBlock;
import com.proventure.twistermill.block.custom.ServoTwisterBlock;
import com.proventure.twistermill.binaryredstone.BinarySignalFrameReceiver;
import com.proventure.twistermill.binaryredstone.BinarySignalProtocol;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.proventure.twistermill.diagnostics.TwisterMillReseatService;
import com.proventure.twistermill.util.ServoRedstoneMappings;
import com.proventure.twistermill.util.ServoTwoAxisRotationMath;
import com.proventure.twistermill.util.SableLevelWrapper;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ServoTwisterBlockEntity extends KineticBlockEntity implements IDisplayAssemblyExceptions,
        InternalServoRedstoneLinkOwner, BlockEntitySubLevelActor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float ANGLE_EPSILON = 0.1F;
    private static final float NORMAL_MOTION_EPSILON = 0.0001F;
    private static final float SOUTH_CONTINUOUS_DEGREES_PER_TICK = 1.0F;
    private static final int PROPELLER_SLOT_REJOIN_SYNC_RETRY_TICKS = 40;
    private static final int PROPELLER_SLOT_PREVIEW_SYNC_RETRY_TICKS = 40;
    private static final int PROPELLER_SLOT_PREVIEW_SYNC_INTERVAL_TICKS = 5;
    private static final float MIN_DISASSEMBLE_DEGREES_PER_TICK = 0.25F;
    private static final float DISASSEMBLE_RETURN_DECEL_START_DEGREES = 30.0F;
    private static final float DISASSEMBLE_RETURN_SLOW_ZONE_DEGREES = 5.0F;
    private static final float DISASSEMBLE_ZERO_SNAP_DEGREES = 0.25F;
    private static final int DISASSEMBLE_ZERO_HOLD_TICKS = 2;
    private static final int MODE3_DISASSEMBLY_CONFIRMATION_TICKS = 20;
    private static final int LATER_GUI_DISASSEMBLY_CONFIRMATION_TICKS = 3;
    private static final float LATER_GUI_DISASSEMBLY_PHYSICAL_ZERO_LIMIT_DEGREES = 0.49F;
    private static final float MODE_7_DISASSEMBLY_PHYSICAL_ZERO_LIMIT_DEGREES = 1.0F;
    private static final float LATER_GUI_DISASSEMBLY_MAX_ANGLE_DELTA_DEGREES_PER_TICK = 0.5F;
    private static final float ROTATION_PROFILE_NEUTRAL_SCALAR_TOLERANCE_DEGREES = 0.25F;
    private static final float ROTATION_PROFILE_NEUTRAL_PHYSICAL_TOLERANCE_DEGREES = 0.25F;
    private static final int ROTATION_PROFILE_NEUTRAL_STABLE_TICKS = 2;
    private static final float MODE3_EXIT_RETURN_DEGREES_PER_TICK = 2.0F;
    private static final float MODE3_DISASSEMBLY_MIN_TARGET_STEP_DEGREES = 0.5F;
    private static final float MODE3_DISASSEMBLY_MAX_TARGET_SPEED_DEGREES_PER_TICK = 4.0F;
    private static final float MODE3_DISASSEMBLY_TARGET_ACCELERATION_DEGREES_PER_TICK_SQUARED =
            0.5F;
    private static final float MODE3_DISASSEMBLY_TARGET_DECELERATION_DEGREES_PER_TICK_SQUARED =
            0.5F;
    private static final float MODE6_MIN_DEGREES_PER_TICK = 0.05F;
    private static final double MODE6_TWO_PI = Math.PI * 2.0D;
    private static final double RADIANS_PER_SECOND_TO_RPM = 60.0D / (2.0D * Math.PI);
    private static final double MODE_7_RPM_DISPLAY_SCALE = 100.0D;
    private static final int MODE_3_STEP = 7;
    private static final int MODE_ABSOLUTE_0_540 = 8;
    private static final int MODE_CENTERED = 9;
    private static final int MODE_FINE_0_180 = 10;
    private static final int MODE_FINE_CENTERED = 11;
    private static final int MODE_FLIP = 12;
    private static final int MODE_INVERTED_FLIP = 13;
    private static final int MODE_TWO_AXIS_TILT_INVERTED = 14;
    private static final int MODE_TWO_AXIS_TILT = 15;
    private static final float MAX_EXTENDED_MODE_DEGREES = 540.0F;
    private static final long WIND_ROTO_RUNTIME_SYNC_TICKS = 20L;
    private static final int BINARY_HALF_PHASE_TICKS = BinarySignalProtocol.FRAME_HALF_PHASE_TICKS;
    private static final int BINARY_START_MIN_TICKS = BinarySignalProtocol.FRAME_START_MIN_TICKS;
    private static final int BINARY_START_MAX_TICKS = BinarySignalProtocol.FRAME_START_MAX_TICKS;
    private static final int BINARY_FRAME_BITS = BinarySignalProtocol.FRAME_BITS;
    private static final int BINARY_BIT_CELL_TICKS = BINARY_HALF_PHASE_TICKS * 2;
    private static final int BINARY_BIT_SAMPLE_TICK = 1;
    private static final int BINARY_SECOND_HALF_START_TICK = BINARY_HALF_PHASE_TICKS;
    private static final int BINARY_RECEIVE_TIMEOUT_TICKS = 90;
    private static final int BINARY_WAIT_BLINK_OFF_TICKS = 12;
    private static final int BINARY_WAIT_BLINK_ON_TICKS = 8;
    private static final int BINARY_WAIT_BLINK_PERIOD_TICKS = BINARY_WAIT_BLINK_OFF_TICKS + BINARY_WAIT_BLINK_ON_TICKS;

    private static final String TAG_MANUAL_ENABLED = "ManualEnabled";
    private static final String TAG_RUNNING = "Running";
    private static final String TAG_ASSEMBLE_NEXT_TICK = "AssembleNextTick";
    private static final String TAG_ANGLE = "Angle";
    private static final String TAG_PREV_ANGLE = "PrevAngle";
    private static final String TAG_ASSEMBLED_BLOCK_COUNT = "AssembledBlockCount";
    private static final String TAG_BOUND_TO_WIND_ROTO = "BoundToWindRoto";
    private static final String TAG_SABLE_ACTIVE = "SableActive";
    private static final String TAG_SABLE_SUBLEVEL_ID = "SableSubLevelId";
    private static final String SABLE_ROTATION_PROFILE_TAG = "SableRotationProfile";
    private static final String TAG_PENDING_DISASSEMBLE_AFTER_ZERO = "PendingDisassembleAfterZero";
    private static final String TAG_PENDING_MODE3_DISASSEMBLY_RETURN =
            "PendingMode3DisassemblyReturn";
    private static final String TAG_PENDING_EXTENDED_BINARY_DISASSEMBLY_RETURN =
            "PendingExtendedBinaryDisassemblyReturn";
    private static final String TAG_PENDING_LATER_GUI_ROTATION_PROFILE =
            "PendingLaterGuiRotationProfile";
    private static final String TAG_FREE_BEARING_LIFECYCLE_PHASE = "Mode8LifecyclePhase";
    private static final String TAG_MODE_7_MEASURED_RPM = "Mode7MeasuredRpm";
    private static final String TAG_PHYSICAL_BEARING_MEASURED_ANGLE = "PhysicalBearingMeasuredAngle";
    private static final String TAG_BOUND_WIND_ROTO_DIMENSION = "BoundWindRotoDimension";
    private static final String TAG_BOUND_WIND_ROTO_POS = "BoundWindRotoPos";
    private static final String TAG_BOUND_SERVO_ORIGINAL_POS = "BoundServoOriginalPos";
    private static final String TAG_BINARY_MODE_SIGNAL = "BinaryModeSignal";
    private static final String TAG_BINARY_SPEED_SIGNAL = "BinarySpeedSignal";
    private static final String TAG_BINARY_ANGLE_SIGNAL = "BinaryAngleSignal";
    private static final String TAG_HAS_VALID_BINARY_FRAME = "HasValidBinaryFrame";
    private static final String TAG_INTERNAL_REDSTONE_LINK_ACTIVE = "InternalRedstoneLinkActive";
    private static final String TAG_INTERNAL_REDSTONE_LINK_RECEIVED_SIGNAL = "InternalRedstoneLinkReceivedSignal";
    private static final String TAG_SPEED_ZERO_MOVEMENT_ENABLED = "SpeedZeroMovementEnabled";
    private static final String TAG_SECOND_ANGLE = "SecondAngle";
    private static final String TAG_SECOND_PREV_ANGLE = "SecondPrevAngle";
    private static final String TAG_SECOND_TARGET_ANGLE = "SecondTargetAngle";
    private static final String TAG_SECONDARY_BINARY_ANGLE_SIGNAL = "SecondaryBinaryAngleSignal";
    private static final String TAG_SECONDARY_HAS_VALID_BINARY_FRAME = "SecondaryHasValidBinaryFrame";
    private static final String TAG_SECONDARY_INTERNAL_REDSTONE_LINK_ACTIVE =
            "SecondaryInternalRedstoneLinkActive";
    private static final String TAG_SECONDARY_INTERNAL_REDSTONE_LINK_RECEIVED_SIGNAL =
            "SecondaryInternalRedstoneLinkReceivedSignal";

    protected ScrollOptionBehaviour<MaxAngleOption> maxAngleBehaviour;
    private LinkBehaviour internalRedstoneLink;
    private SecondaryServoRedstoneLinkBehaviour secondaryInternalRedstoneLink;
    @Nullable
    protected AssemblyException lastException;

    private boolean manualEnabled = false;
    private boolean running = false;
    private boolean assembleNextTick = false;
    private float angle = 0.0F;
    private float prevAngle = 0.0F;
    private float secondAngle = 0.0F;
    private float secondPrevAngle = 0.0F;
    private float secondTargetAngle = 0.0F;

    private boolean lastVisualRunning = false;
    private boolean needsStateRefresh = true;
    private int pendingPropellerSlotRejoinSyncTicks = 0;
    private int pendingPropellerSlotPreviewSyncTicks = 0;
    private int propellerSlotPreviewSyncCooldownTicks = 0;

    private int lastWestSignal = 0;
    private int lastEastSignal = 0;
    private int lastOppositeTopSignal = 0;
    private int binaryModeSignal = 0;
    private int binarySpeedSignal = 0;
    private int binaryAngleSignal = 0;
    private int binaryWorkingBits = 0;
    private int binaryBitsRead = 0;
    private int binaryReceiveTicks = 0;
    private int binaryStartOnTicks = 0;
    private int binaryStartOffTicks = 0;
    private int binaryBitCellTick = 0;
    private boolean binaryLastInputHigh = false;
    private boolean binaryStartBitCellNextTick = false;
    private BinaryReceivePhase binaryReceivePhase = BinaryReceivePhase.IDLE;
    private ControlMarkerReceivePhase controlMarkerReceivePhase = ControlMarkerReceivePhase.IDLE;
    private int controlMarkerHighTicks = 0;
    private int controlMarkerLowTicks = 0;
    private int controlMarkerReceiveTicks = 0;
    private boolean controlMarkerLastInputHigh = false;
    private boolean hasValidBinaryFrame = false;
    private boolean internalRedstoneLinkActive = false;
    private int internalRedstoneLinkReceivedSignal = 0;
    private boolean secondaryInternalRedstoneLinkActive = false;
    private int secondaryInternalRedstoneLinkReceivedSignal = 0;
    private int secondaryBinaryAngleSignal = 0;
    private boolean secondaryHasValidBinaryFrame = false;
    private final BinarySignalFrameReceiver secondaryBinaryReceiver = new BinarySignalFrameReceiver();
    private transient boolean previousSecondaryInternalRedstoneLinkEligibility = false;
    private boolean binaryVisualInputHigh = false;
    private boolean pendingDisassembleAfterZero = false;
    private int pendingDisassembleZeroHoldTicks = 0;
    private boolean pendingMode3DisassemblyReturn = false;
    private boolean pendingExtendedBinaryDisassemblyReturn = false;
    @Nullable
    private SableInteractiveContraptionBackend.RotationProfile pendingLaterGuiRotationProfile = null;
    @Nullable
    private transient Float laterGuiPreviousPhysicalAngle = null;
    private transient long laterGuiPreviousPhysicalSampleGameTime = Long.MIN_VALUE;
    private transient int laterGuiDisassemblyConfirmationTicks = 0;
    private transient boolean laterGuiAssemblyCompletedThisTick = false;
    private transient boolean mode3DisassemblyReturnInitialized = false;
    private transient float mode3DisassemblyReturnCommandAngle = 0.0F;
    private transient float mode3DisassemblyReturnTargetSpeed = 0.0F;
    private transient int mode3DisassemblyConfirmationTicks = 0;
    @Nullable
    private transient SableInteractiveContraptionBackend.Mode3RelativePoseProbe
            mode3PreviousDisassemblyPoseProbe = null;
    private FreeBearingLifecyclePhase freeBearingLifecyclePhase = FreeBearingLifecyclePhase.UNASSEMBLED;
    private transient float mode7MeasuredRpm = 0.0F;
    private transient float physicalBearingMeasuredAngle = 0.0F;
    private transient boolean physicalBearingAngleSamplePublishedThisTick = false;
    private boolean speedZeroMovementEnabled = true;
    private boolean mode3ExitReturnActive = false;
    private double mode6OscillationPhase = 0.0D;
    private SableInteractiveContraptionBackend.RotationProfile activeRotationProfile =
            SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
    private transient boolean rotationProfileTagPresent = false;
    private transient boolean rotationProfileTransitionActive = false;
    private transient SableInteractiveContraptionBackend.RotationProfile rotationProfileTransitionTarget =
            SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
    private transient int rotationProfileNeutralStableTicks = 0;
    private transient int pitchClearanceNeutralStableTicks = 0;
    private transient boolean diagnosticFirstRefreshLogged = false;
    private transient boolean diagnosticRefreshFailureLogged = false;

    private float targetAngle = 0.0F;
    private int assembledBlockCount = 0;
    private boolean boundToWindRoto = false;

    @Nullable
    private ResourceKey<Level> boundWindRotoDimension = null;
    @Nullable
    private BlockPos boundWindRotoPos = null;
    @Nullable
    private BlockPos boundServoOriginalPos = null;
    private long nextWindRotoRuntimeSyncAt = 0L;
    private long nextSableBlockCountRefreshAt = 0L;



    private final SableInteractiveContraptionBackend sableBackend = new SableInteractiveContraptionBackend(TwisterMillDiagnostics.Target.SERVO);
    private final RememberedSableShipMemory rememberedShipMemory = new RememberedSableShipMemory();
    private final ServoPropellerSlotManager propellerSlotManager = new ServoPropellerSlotManager();

    private enum BinaryReceivePhase {
        IDLE,
        START_ON,
        START_OFF,
        BIT_CELL
    }

    private enum ControlMarkerReceivePhase {
        IDLE,
        HIGH_1,
        LOW_GAP,
        HIGH_2
    }

    public ServoTwisterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVO_TWISTER_BE.get(), pos, state);
    }




    private boolean canSendData() {
        return level instanceof ServerLevel serverLevel
                && serverLevel.getServer().isRunning();
    }

    private boolean refreshSpeedZeroMovementConfigFromServer() {
        boolean configured = TwisterMillConfig.isServoSpeedZeroMovementEnabled();
        if (speedZeroMovementEnabled == configured) {
            return false;
        }
        speedZeroMovementEnabled = configured;
        return true;
    }

    private void logSableLifecycleDiagnostics(String event) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }
        if ("refresh-success".equals(event)) {
            if (diagnosticFirstRefreshLogged) {
                return;
            }
            diagnosticFirstRefreshLogged = true;
            diagnosticRefreshFailureLogged = false;
        } else if ("refresh-failure".equals(event)) {
            if (diagnosticRefreshFailureLogged) {
                return;
            }
            diagnosticRefreshFailureLogged = true;
        }

        LOGGER.info("[ServoLifecycleDiag] event={} pos={} gameTime={} facing={} running={} sableActive={} activeSubLevelId={} assembleNextTick={} pendingPropellerSlotRejoinSyncTicks={} pendingPropellerSlotPreviewSyncTicks={} lastOppositeTopSignal={} binaryModeSignal={} option7Active={}",
                event,
                worldPosition,
                level == null ? -1L : level.getGameTime(),
                getFacingDirection(),
                running,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                assembleNextTick,
                pendingPropellerSlotRejoinSyncTicks,
                pendingPropellerSlotPreviewSyncTicks,
                lastOppositeTopSignal,
                binaryModeSignal,
                isPropellerSlotMode());
    }

    private void requestPropellerSlotPreviewSync() {
        if (!propellerSlotManager.hasAnySlot()) {
            return;
        }

        pendingPropellerSlotPreviewSyncTicks = Math.max(
                pendingPropellerSlotPreviewSyncTicks,
                PROPELLER_SLOT_PREVIEW_SYNC_RETRY_TICKS
        );
        propellerSlotPreviewSyncCooldownTicks = 0;
    }

    private void clearPropellerSlotPreviewSync() {
        pendingPropellerSlotPreviewSyncTicks = 0;
        propellerSlotPreviewSyncCooldownTicks = 0;
    }

    private void tickPropellerSlotPreviewSync(ServerLevel serverLevel) {
        if (pendingPropellerSlotPreviewSyncTicks <= 0) {
            return;
        }

        if (!propellerSlotManager.hasAnySlot()) {
            clearPropellerSlotPreviewSync();
            return;
        }

        if (propellerSlotPreviewSyncCooldownTicks > 0) {
            propellerSlotPreviewSyncCooldownTicks--;
            pendingPropellerSlotPreviewSyncTicks--;
            return;
        }

        boolean previewReady = propellerSlotManager.hasPreviewDataForClient(sableBackend.isActive());
        setChanged();
        if (canSendData()) {
            sendData();
        }
        logPropellerSlotPreviewSyncDiagnostics(serverLevel, previewReady);

        if (previewReady) {
            clearPropellerSlotPreviewSync();
            return;
        }

        pendingPropellerSlotPreviewSyncTicks--;
        propellerSlotPreviewSyncCooldownTicks = PROPELLER_SLOT_PREVIEW_SYNC_INTERVAL_TICKS;
    }

    private void logPropellerSlotPreviewSyncDiagnostics(ServerLevel serverLevel, boolean previewReady) {
        if (!TwisterMillDiagnostics.isServoLoggingEnabled()) {
            return;
        }

        LOGGER.info(
                "[PropellerSlotPreviewSyncDiag] event=preview-sync pos={} gameTime={} facing={} sableActive={} activeSubLevelId={} hasAnySlot={} hasCompleteSlotSet={} activeTopFollowReady={} previewReady={} pendingTicks={} cooldownTicks={}",
                worldPosition,
                serverLevel.getGameTime(),
                getFacingDirection(),
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                propellerSlotManager.hasAnySlot(),
                propellerSlotManager.hasCompleteSlotSet(),
                propellerSlotManager.hasActiveTopFollowForPreview(),
                previewReady,
                pendingPropellerSlotPreviewSyncTicks,
                propellerSlotPreviewSyncCooldownTicks
        );
    }

    public float getWindRotoBindingAngleDegrees() {
        return Math.abs(angle);
    }

    public int getBoundContraptionBlockCount() {
        return assembledBlockCount;
    }

    void collectChildSubLevelIdsForWindRotoSailCount(Collection<UUID> target) {
        if (target == null) {
            return;
        }

        UUID activeSubLevelId = sableBackend.getActiveSubLevelId();
        if (activeSubLevelId != null) {
            target.add(activeSubLevelId);
        }
        propellerSlotManager.collectActiveSlotSubLevelIds(target);
    }

    @Override
    public Iterable<SubLevel> sable$getConnectionDependencies() {
        return TwisterMillSableSchematicRemapper.resolveConnectionDependencies(
                level,
                this::collectChildSubLevelIdsForWindRotoSailCount
        );
    }

    @Override
    public Iterable<SubLevel> sable$getLoadingDependencies() {
        return List.of();
    }

    public void setBoundToWindRoto(boolean boundToWindRoto) {
        if (this.boundToWindRoto == boundToWindRoto)
            return;
        this.boundToWindRoto = boundToWindRoto;
        setChanged();
        if (canSendData()) sendData();
    }

    public boolean isBoundToWindRoto() {
        return boundToWindRoto;
    }

    @SuppressWarnings("unused")
    public float getTargetAngleDegrees() {
        return targetAngle;
    }

    public int getLastWestSignal() {
        return lastWestSignal;
    }

    public int getLastEastSignal() {
        return lastEastSignal;
    }

    public int getLastOppositeTopSignal() {
        return lastOppositeTopSignal;
    }

    public int getConfiguredMaxDegreesForDisplay() {
        return getConfiguredMaxDegrees();
    }

    @SuppressWarnings("unused")
    public int getEffectiveMaxDegreesForDisplay() {
        return getEffectiveMaxDegrees();
    }

    public int getActiveAngleMultiplierForDisplay() {
        return getAngleMultiplierForModeSignal(lastOppositeTopSignal);
    }

    public float getMappedSpeedDegreesPerTickForDisplay() {
        return getDegreesPerTickForSignal(lastWestSignal);
    }

    public float getMappedAngleDegreesForDisplay() {
        return Math.abs(getTargetAngleForSignal(lastEastSignal));
    }

    public void setWindRotoBinding(boolean bound, ResourceKey<Level> dimension, BlockPos windRotoPos, BlockPos servoOriginalPos) {
        setBoundToWindRoto(bound);

        if (!bound) {
            boundWindRotoDimension = null;
            boundWindRotoPos = null;
            boundServoOriginalPos = null;
        } else {
            boundWindRotoDimension = dimension;
            boundWindRotoPos = windRotoPos == null ? null : windRotoPos.immutable();
            boundServoOriginalPos = servoOriginalPos == null ? worldPosition.immutable() : servoOriginalPos.immutable();
            nextWindRotoRuntimeSyncAt = 0L;
        }

        setChanged();
        if (canSendData()) sendData();
    }



    @SuppressWarnings("unused")
    public enum MaxAngleOption implements INamedIconOptions {
        DEG_60(60, "twistermill.servo.max_angle.option.1", AllIcons.I_ROTATE_NEVER_PLACE),
        DEG_120(120, "twistermill.servo.max_angle.option.2", AllIcons.I_ROTATE_PLACE),
        DEG_240(240, "twistermill.servo.max_angle.option.3", AllIcons.I_ROTATE_PLACE_RETURNED),
        DEG_60_LINK(60, "twistermill.servo.max_angle.option.4", AllIcons.I_ROTATE_NEVER_PLACE, true),
        DEG_120_LINK(120, "twistermill.servo.max_angle.option.5", AllIcons.I_ROTATE_PLACE, true),
        DEG_240_LINK(240, "twistermill.servo.max_angle.option.6", AllIcons.I_ROTATE_PLACE_RETURNED, true),
        DEG_60_PROPELLER_SLOTS_FREE_ROTO(60, "twistermill.servo.max_angle.option.7", AllIcons.I_ROTATE_NEVER_PLACE, false, true, true);

        private final int maxDegrees;
        private final String translationKey;
        private final AllIcons icon;
        private final boolean internalRedstoneLink;
        private final boolean propellerSlots;
        private final boolean freeBearingRotation;

        MaxAngleOption(int maxDegrees, String translationKey, AllIcons icon) {
            this(maxDegrees, translationKey, icon, false, false, false);
        }

        MaxAngleOption(int maxDegrees, String translationKey, AllIcons icon, boolean internalRedstoneLink) {
            this(maxDegrees, translationKey, icon, internalRedstoneLink, false, false);
        }

        MaxAngleOption(int maxDegrees, String translationKey, AllIcons icon, boolean internalRedstoneLink,
                       boolean propellerSlots, boolean freeBearingRotation) {
            this.maxDegrees = maxDegrees;
            this.translationKey = translationKey;
            this.icon = icon;
            this.internalRedstoneLink = internalRedstoneLink;
            this.propellerSlots = propellerSlots;
            this.freeBearingRotation = freeBearingRotation;
        }

        public int getMaxDegrees() {
            return maxDegrees;
        }

        public boolean isInternalRedstoneLink() {
            return internalRedstoneLink;
        }

        public boolean isPropellerSlots() {
            return propellerSlots;
        }

        public boolean isFreeBearingRotation() {
            return freeBearingRotation;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }

    private enum FreeBearingLifecyclePhase {
        UNASSEMBLED(0),
        ASSEMBLING_REGULATED(1),
        ACTIVE_FREE_BEARING(2),
        RETURNING_TO_ZERO(3),
        RECOVERY_PENDING(4);

        private final int storedId;

        FreeBearingLifecyclePhase(int storedId) {
            this.storedId = storedId;
        }

        int storedId() {
            return storedId;
        }

        @Nullable
        static FreeBearingLifecyclePhase fromStoredId(int storedId) {
            for (FreeBearingLifecyclePhase phase : values()) {
                if (phase.storedId == storedId) {
                    return phase;
                }
            }
            return null;
        }
    }

    private int getConfiguredMaxDegrees() {
        if (maxAngleBehaviour == null)
            return 60;

        return maxAngleBehaviour.get().getMaxDegrees();
    }

    @Override
    public boolean isInternalRedstoneLinkMode() {
        return maxAngleBehaviour != null && maxAngleBehaviour.get().isInternalRedstoneLink();
    }

    @Override
    public boolean isInternalRedstoneLinkReceiverActive() {
        return isInternalRedstoneLinkMode() && internalRedstoneLinkActive;
    }

    @Override
    public boolean isSecondaryInternalRedstoneLinkEligible() {
        return isInternalRedstoneLinkMode()
                && hasValidBinaryFrame
                && isTwoAxisTiltModeSignal(binaryModeSignal);
    }

    @Override
    public boolean isSecondaryInternalRedstoneLinkReceiverActive() {
        return isSecondaryInternalRedstoneLinkEligible() && secondaryInternalRedstoneLinkActive;
    }

    private boolean requiresTwoAxisLoadedPoseRecovery() {
        return running
                && !pendingDisassembleAfterZero
                && isInternalRedstoneLinkMode()
                && hasValidBinaryFrame
                && isTwoAxisTiltModeSignal(binaryModeSignal)
                && activeRotationProfile
                == SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT
                && sableBackend.isActive()
                && sableBackend.getActiveSubLevelId() != null
                && sableBackend.requiresConstraintAttachment(
                        SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT);
    }

    public boolean isPropellerSlotMode() {
        return maxAngleBehaviour != null && maxAngleBehaviour.get().isPropellerSlots();
    }

    private boolean isConfiguredFreeBearingRotationMode() {
        return maxAngleBehaviour != null && maxAngleBehaviour.get().isFreeBearingRotation();
    }

    private boolean isLaterGuiAssemblyOption() {
        MaxAngleOption option = getCurrentMaxAngleOption();
        return option == MaxAngleOption.DEG_60_LINK
                || option == MaxAngleOption.DEG_120_LINK
                || option == MaxAngleOption.DEG_240_LINK
                || option == MaxAngleOption.DEG_60_PROPELLER_SLOTS_FREE_ROTO;
    }

    private boolean hasFreeBearingControlRequest() {
        return isConfiguredFreeBearingRotationMode() || lastOppositeTopSignal == 3;
    }

    private static boolean isExtendedBinaryDisassemblyCode(int modeSignal) {
        return modeSignal >= 1 && modeSignal <= 15 && modeSignal != 3;
    }

    private boolean isExtendedBinaryDisassemblyRequest() {
        return isInternalRedstoneLinkMode()
                && isExtendedBinaryDisassemblyCode(lastOppositeTopSignal);
    }

    private boolean isSafeBinaryDisassemblyReturnActive() {
        return pendingMode3DisassemblyReturn || pendingExtendedBinaryDisassemblyReturn;
    }

    private boolean isDisassemblyReturnActive() {
        return pendingDisassembleAfterZero || isSafeBinaryDisassemblyReturnActive();
    }

    private boolean isFreeBearingOperatingMode() {
        return hasFreeBearingControlRequest() && !isDisassemblyReturnActive();
    }

    @Nullable
    private MaxAngleOption getCurrentMaxAngleOption() {
        return maxAngleBehaviour == null ? null : maxAngleBehaviour.get();
    }

    private float getDisassemblyReturnSpeedMultiplierForCurrentGuiMode() {
        if (maxAngleBehaviour == null) {
            return 1.0F;
        }

        int rawOptionValue = maxAngleBehaviour.getValue();
        MaxAngleOption[] options = MaxAngleOption.values();
        if (rawOptionValue < 0 || rawOptionValue >= options.length) {
            return 1.0F;
        }

        return switch (options[rawOptionValue]) {
            case DEG_60, DEG_120, DEG_240 ->
                    TwisterMillConfig.getServoDisassemblyReturnSpeedMultiplierModes1To3();
            case DEG_60_LINK, DEG_120_LINK, DEG_240_LINK ->
                    TwisterMillConfig.getServoDisassemblyReturnSpeedMultiplierModes4To6();
            case DEG_60_PROPELLER_SLOTS_FREE_ROTO ->
                    TwisterMillConfig.getServoDisassemblyReturnSpeedMultiplier();
        };
    }

    private static boolean isBinaryInputTooltipActiveMode(@Nullable MaxAngleOption option) {
        return option != null && (option.isInternalRedstoneLink() || option.isPropellerSlots());
    }

    private boolean isBinaryInputTooltipActiveMode() {
        return isBinaryInputTooltipActiveMode(getCurrentMaxAngleOption());
    }

    public boolean hasActiveServoTopForPreview() {
        return running || sableBackend.isActive();
    }

    @Nullable
    public UUID getActiveServoTopSubLevelIdForPreview() {
        return sableBackend.getActiveSubLevelId();
    }

    public boolean hasPropellerSlotForPreview(int slot) {
        return propellerSlotManager.hasSlotForPreview(slot);
    }

    @Nullable
    public UUID getPropellerSlotSubLevelIdForPreview(int slot) {
        return propellerSlotManager.getSlotSubLevelIdForPreview(slot);
    }

    @Nullable
    public Vector3d getPropellerSlotAnchorLocalCenterForPreview(int slot) {
        return propellerSlotManager.getSlotAnchorLocalCenterForPreview(slot);
    }

    @Nullable
    public Vector3d getActiveServoTopAnchorLocalCenterForPreview() {
        return propellerSlotManager.getActiveTopAnchorLocalCenterForPreview();
    }

    public boolean shouldHandlePropellerSlotPlacement(Direction clickedFace, ItemStack stack) {
        Direction facing = getFacingDirection();
        return isPropellerSlotMode()
                && isSupportedPropellerSlotFacing(facing)
                && clickedFace == facing
                && stack.getItem() instanceof BlockItem
                && propellerSlotManager.hasRecordedOpenSlot();
    }

    public ItemInteractionResult tryPlacePropellerSlot(Player player, ItemStack stack, BlockItem blockItem, Direction clickedFace) {
        if (!shouldHandlePropellerSlotPlacement(clickedFace, stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!player.mayBuild()) {
            return ItemInteractionResult.FAIL;
        }

        if (level == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        Level rootLevel = SableLevelWrapper.getRootLevel(level);
        if (!(rootLevel instanceof ServerLevel rootServerLevel)) {
            return ItemInteractionResult.SUCCESS;
        }

        boolean placed = propellerSlotManager.tryPlaceNextSlot(this, rootServerLevel, player, stack, blockItem, getFacingDirection());
        if (placed) {
            tryAutoPlaceCompletedBladeArm();
            requestPropellerSlotPreviewSync();
            setChanged();
            if (canSendData()) sendData();
        }

        return ItemInteractionResult.SUCCESS;
    }

    private void tryAutoPlaceCompletedBladeArm() {
        if (!isPropellerSlotMode() || !propellerSlotManager.hasCompleteSlotSet() || level == null || level.isClientSide) {
            return;
        }

        Direction facing = getFacingDirection();
        BlockPos topPos = worldPosition.relative(facing);
        if (!level.isLoaded(topPos)) {
            return;
        }

        BlockState existingState = level.getBlockState(topPos);
        if (isBladeArmTopState(existingState)) {
            return;
        }
        if (!existingState.canBeReplaced()) {
            return;
        }

        BlockState bladeArmState = getCompletedBladeArmState(facing);
        level.setBlock(topPos, bladeArmState, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
    }

    private static boolean isBladeArmTopState(BlockState state) {
        return state.is(ModBlocks.BLADE_ARM_BLOCK.get())
                || state.is(ModBlocks.BLADE_ARM_EASTFACE_BLOCK.get())
                || state.is(ModBlocks.BLADE_ARM_WESTFACE_BLOCK.get());
    }

    private static BlockState getCompletedBladeArmState(Direction servoFacing) {
        return ModBlocks.BLADE_ARM_BLOCK.get()
                .defaultBlockState()
                .setValue(BladeArmBlock.FACING, BladeArmBlock.getServoSlotAFacing(servoFacing));
    }

    @Override
    public boolean shouldRenderInternalRedstoneLinkSlots() {
        return isInternalRedstoneLinkMode();
    }

    @Override
    public Direction getInternalRedstoneLinkSide() {
        return InternalServoRedstoneLinkOwner.getServoInternalLinkSide(getBlockState());
    }

    private int getEffectiveMaxDegrees() {
        return getConfiguredMaxDegrees() * getAngleMultiplierForModeSignal(lastOppositeTopSignal);
    }

    private int getAngleMultiplierForModeSignal(int modeSignal) {
        return switch (modeSignal) {
            case 3 -> 2;
            case 4 -> 3;
            case 5 -> 4;
            default -> 1;
        };
    }

    private static boolean isWorldLockedModeSignal(int modeSignal) {
        return modeSignal == 4 || modeSignal == 5;
    }

    private static boolean isMode6OscillationSignal(int modeSignal) {
        return modeSignal == 6;
    }

    private static boolean isTwoAxisTiltModeSignal(int modeSignal) {
        return modeSignal == MODE_TWO_AXIS_TILT_INVERTED
                || modeSignal == MODE_TWO_AXIS_TILT;
    }

    private static boolean isExtendedTargetModeSignal(int modeSignal) {
        return modeSignal >= MODE_3_STEP && modeSignal <= MODE_INVERTED_FLIP;
    }

    private static boolean isOscillationModeSignal(int modeSignal) {
        return isMode6OscillationSignal(modeSignal);
    }

    private static boolean isUnboundedModeSignal(int modeSignal) {
        return modeSignal == 3 || isWorldLockedModeSignal(modeSignal) || isOscillationModeSignal(modeSignal);
    }

    private static boolean isBoundedPositioningModeSignal(int modeSignal) {
        return modeSignal == 1 || modeSignal == 2;
    }

    private void onMaxAngleChanged() {
        if (level == null || level.isClientSide)
            return;

        sableBackend.disableFacingAxisHardHinge("gui-mode-changed");
        mode7MeasuredRpm = 0.0F;
        if (isSafeBinaryDisassemblyReturnActive()) {
            resetMode3DisassemblyConfirmation();
        }
        if (pendingExtendedBinaryDisassemblyReturn) {
            rotationProfileNeutralStableTicks = 0;
        }

        if (!isInternalRedstoneLinkMode()) {
            disableInternalRedstoneLink();
        }

        updateControlSignalsFromInputs(false);
        updateVisualPowerState();
        setChanged();
        if (canSendData()) sendData();
    }

    private void refreshRuntimeStateFromWorld() {
        updateControlSignalsFromInputs(false);
        updateVisualPowerState();

        boolean visualRunning =
                running && (manualEnabled || lastEastSignal > 0 || lastOppositeTopSignal > 0
                || Math.abs(angle) > ANGLE_EPSILON || Math.abs(secondAngle) > ANGLE_EPSILON);
        updateVisualRunning(visualRunning);
    }

    private void updateMode7MeasuredRpmForClient(ServerLevel serverLevel) {
        double measuredRpm = 0.0D;
        if (isConfiguredFreeBearingRotationMode()) {
            double angularVelocityRadiansPerSecond =
                    sableBackend.measureFacingAxisRelativeAngularVelocityRadiansPerSecond(
                            serverLevel,
                            worldPosition,
                            getFacingDirection()
                    );
            if (Double.isFinite(angularVelocityRadiansPerSecond)) {
                measuredRpm = Math.abs(angularVelocityRadiansPerSecond) * RADIANS_PER_SECOND_TO_RPM;
            }
        }

        float visibleRpm = normalizeMode7MeasuredRpm(measuredRpm);
        if (Float.compare(mode7MeasuredRpm, visibleRpm) == 0) {
            return;
        }

        mode7MeasuredRpm = visibleRpm;
        if (canSendData()) {
            sendData();
        }
    }

    private static float normalizeMode7MeasuredRpm(double measuredRpm) {
        if (!Double.isFinite(measuredRpm) || measuredRpm <= 0.0D || measuredRpm > Float.MAX_VALUE) {
            return 0.0F;
        }

        double roundedRpm = Math.floor(measuredRpm * MODE_7_RPM_DISPLAY_SCALE + 0.5D)
                / MODE_7_RPM_DISPLAY_SCALE;
        float visibleRpm = (float) roundedRpm;
        return Float.isFinite(visibleRpm) && visibleRpm > 0.0F ? visibleRpm : 0.0F;
    }

    private boolean isBinaryMode3PhysicalAngleTooltipActive() {
        return isInternalRedstoneLinkMode()
                && (lastOppositeTopSignal == 3 || pendingMode3DisassemblyReturn);
    }

    private boolean isDisassemblyPhysicalAngleTooltipActive() {
        return pendingDisassembleAfterZero && !isConfiguredFreeBearingRotationMode();
    }

    private boolean isPhysicalBearingAngleTooltipActive() {
        return isDisassemblyPhysicalAngleTooltipActive()
                || isBinaryMode3PhysicalAngleTooltipActive();
    }

    private void updatePhysicalBearingMeasuredAngleForClient(ServerLevel serverLevel) {
        float visibleAngle = 0.0F;
        if (isPhysicalBearingAngleTooltipActive()) {
            if (isDisassemblyPhysicalAngleTooltipActive()
                    && physicalBearingAngleSamplePublishedThisTick) {
                return;
            }
            Float measuredAngle;
            if (isDisassemblyPhysicalAngleTooltipActive()) {
                measuredAngle = sableBackend.measureBearingAxisRelativeAngleDegrees(
                        serverLevel,
                        worldPosition,
                        getFacingDirection()
                );
            } else {
                measuredAngle = sableBackend.measureFacingAxisRelativeAngleDegrees(
                        serverLevel,
                        worldPosition,
                        getFacingDirection()
                );
            }
            visibleAngle = normalizePhysicalBearingMeasuredAngle(measuredAngle);
        }

        setPhysicalBearingMeasuredAngleForClient(visibleAngle);
    }

    private void publishPhysicalBearingMeasuredAngleForClient(@Nullable Float measuredAngle) {
        physicalBearingAngleSamplePublishedThisTick = true;
        setPhysicalBearingMeasuredAngleForClient(normalizePhysicalBearingMeasuredAngle(measuredAngle));
    }

    private void setPhysicalBearingMeasuredAngleForClient(float visibleAngle) {
        if (Float.compare(physicalBearingMeasuredAngle, visibleAngle) == 0) {
            return;
        }

        physicalBearingMeasuredAngle = visibleAngle;
        if (canSendData()) {
            sendData();
        }
    }

    private static float normalizePhysicalBearingMeasuredAngle(@Nullable Float measuredAngle) {
        if (measuredAngle == null || !Float.isFinite(measuredAngle)) {
            return 0.0F;
        }

        float wrappedAngle = Mth.wrapDegrees(measuredAngle);
        return Float.isFinite(wrappedAngle) ? Math.abs(wrappedAngle) : 0.0F;
    }

    @SuppressWarnings("unused")
    public void onPlayerToggle(Player player) {
        if (level == null || level.isClientSide)
            return;

        if (isLaterGuiAssemblyOption()) {
            executeLaterGuiToggle();
        } else {
            executeManualToggle();
        }
    }

    private void executeLaterGuiToggle() {
        if (isConfiguredFreeBearingRotationMode()) {
            executeFreeBearingToggle();
            return;
        }

        if (running && pendingDisassembleAfterZero)
            return;

        if (running && (activeRotationProfile
                != SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS
                || hasFreeBearingControlRequest()
                || (isExtendedBinaryDisassemblyRequest() && sableBackend.isActive()))) {
            clearLaterGuiDisassemblyReturnState();
            executeManualToggle();
            return;
        }

        if (running) {
            assembleNextTick = false;
            manualEnabled = false;
            pendingDisassembleAfterZero = true;
            pendingDisassembleZeroHoldTicks = 0;
            clearMode3DisassemblyReturnState();
            pendingLaterGuiRotationProfile = activeRotationProfile;
            resetLaterGuiDisassemblyRuntimeState();
            targetAngle = 0.0F;
            stopMotion();
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        if (assembleNextTick) {
            manualEnabled = false;
            assembleNextTick = false;
            stopMotion();
            updateVisualRunning(false);
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        manualEnabled = true;
        assembleNextTick = true;
        setChanged();
        if (canSendData()) sendData();
    }

    private void executeFreeBearingToggle() {
        boolean lifecycleActive = freeBearingLifecyclePhase != FreeBearingLifecyclePhase.UNASSEMBLED
                || running
                || sableBackend.isActive();
        if (lifecycleActive) {
            if (pendingDisassembleAfterZero
                    || freeBearingLifecyclePhase == FreeBearingLifecyclePhase.RETURNING_TO_ZERO) {
                return;
            }

            assembleNextTick = false;
            manualEnabled = false;
            pendingDisassembleAfterZero = true;
            pendingDisassembleZeroHoldTicks = 0;
            clearMode3DisassemblyReturnState();
            clearLaterGuiDisassemblyReturnState();
            targetAngle = 0.0F;
            stopMotion();
            if (freeBearingLifecyclePhase == FreeBearingLifecyclePhase.UNASSEMBLED) {
                freeBearingLifecyclePhase = FreeBearingLifecyclePhase.RECOVERY_PENDING;
            }
            updateFreeBearingTopFollowPolicy();
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        if (assembleNextTick) {
            manualEnabled = false;
            assembleNextTick = false;
            stopMotion();
            updateVisualRunning(false);
        } else {
            manualEnabled = true;
            assembleNextTick = true;
        }
        setChanged();
        if (canSendData()) sendData();
    }

    private void executeManualToggle() {
        if (running) {
            assembleNextTick = false;
            manualEnabled = false;
            beginPendingDisassemblyReturn();
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        if (assembleNextTick) {
            manualEnabled = false;
            assembleNextTick = false;
            stopMotion();
            updateVisualRunning(false);
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        manualEnabled = true;
        assembleNextTick = true;
        setChanged();
        if (canSendData()) sendData();
    }

    private void beginPendingDisassemblyReturn() {
        boolean controlledFreeBearingReturn = hasFreeBearingControlRequest()
                && activeRotationProfile
                == SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS
                && sableBackend.isActive();
        boolean extendedBinaryReturn = isExtendedBinaryDisassemblyRequest()
                && sableBackend.isActive();
        pendingDisassembleAfterZero = true;
        pendingDisassembleZeroHoldTicks = 0;
        if (controlledFreeBearingReturn && isConfiguredFreeBearingRotationMode()) {
            clearMode3DisassemblyReturnState();
        } else if (controlledFreeBearingReturn) {
            beginMode3DisassemblyReturn();
        } else if (extendedBinaryReturn) {
            beginExtendedBinaryDisassemblyReturn();
        } else {
            clearMode3DisassemblyReturnState();
        }
        targetAngle = 0.0F;
        stopMotion();
    }

    private boolean isLaterGuiDisassemblyReturnActive() {
        return pendingDisassembleAfterZero && pendingLaterGuiRotationProfile != null;
    }

    private void resetLaterGuiDisassemblyRuntimeState() {
        laterGuiPreviousPhysicalAngle = null;
        laterGuiPreviousPhysicalSampleGameTime = Long.MIN_VALUE;
        laterGuiDisassemblyConfirmationTicks = 0;
    }

    private void clearLaterGuiDisassemblyReturnState() {
        pendingLaterGuiRotationProfile = null;
        resetLaterGuiDisassemblyRuntimeState();
    }

    private boolean tickLaterGuiPhysicalNeutralConfirmation(
            ServerLevel serverLevel,
            boolean useMode7PhysicalZeroLimit
    ) {
        if (pendingLaterGuiRotationProfile
                != SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS) {
            resetLaterGuiDisassemblyRuntimeState();
            return false;
        }

        Float measuredAngle = sableBackend.measureCurrentMotorAngleDegrees(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                pendingLaterGuiRotationProfile
        );
        if (measuredAngle == null || !Float.isFinite(measuredAngle)) {
            resetLaterGuiDisassemblyRuntimeState();
            return false;
        }

        long gameTime = serverLevel.getGameTime();
        float currentAngle = Mth.wrapDegrees(measuredAngle);
        boolean consecutive = laterGuiPreviousPhysicalAngle != null
                && gameTime == laterGuiPreviousPhysicalSampleGameTime + 1L;
        boolean safe = false;
        if (consecutive) {
            float angleDelta = Math.abs(Mth.wrapDegrees(currentAngle - laterGuiPreviousPhysicalAngle));
            float absoluteCurrentAngle = Math.abs(currentAngle);
            boolean withinPhysicalZeroLimit = useMode7PhysicalZeroLimit
                    ? absoluteCurrentAngle <= MODE_7_DISASSEMBLY_PHYSICAL_ZERO_LIMIT_DEGREES
                    : absoluteCurrentAngle < LATER_GUI_DISASSEMBLY_PHYSICAL_ZERO_LIMIT_DEGREES;
            safe = withinPhysicalZeroLimit
                    && angleDelta <= LATER_GUI_DISASSEMBLY_MAX_ANGLE_DELTA_DEGREES_PER_TICK;
        }

        laterGuiPreviousPhysicalAngle = currentAngle;
        laterGuiPreviousPhysicalSampleGameTime = gameTime;
        laterGuiDisassemblyConfirmationTicks = safe
                ? laterGuiDisassemblyConfirmationTicks + 1
                : 0;
        return laterGuiDisassemblyConfirmationTicks >= LATER_GUI_DISASSEMBLY_CONFIRMATION_TICKS;
    }

    private void tickFreeBearingLifecycle(ServerLevel serverLevel) {
        if (!isConfiguredFreeBearingRotationMode()) {
            propellerSlotManager.setFreeBearingTopFollowPolicy(false, null, false);
            return;
        }

        if (freeBearingLifecyclePhase == FreeBearingLifecyclePhase.UNASSEMBLED) {
            if (running || sableBackend.isActive()) {
                commitFreeBearingPhase(FreeBearingLifecyclePhase.RECOVERY_PENDING);
            } else {
                updateFreeBearingTopFollowPolicy();
                return;
            }
        }

        updateFreeBearingTopFollowPolicy();
        switch (freeBearingLifecyclePhase) {
            case UNASSEMBLED -> {
            }
            case ASSEMBLING_REGULATED -> tickFreeBearingAssemblingRegulated(serverLevel);
            case ACTIVE_FREE_BEARING -> tickFreeBearingActive(serverLevel);
            case RETURNING_TO_ZERO -> tickFreeBearingReturningToZero(serverLevel);
            case RECOVERY_PENDING -> tickFreeBearingRecoveryPending(serverLevel);
        }
    }

    private void tickFreeBearingAssemblingRegulated(ServerLevel serverLevel) {
        UUID expectedTopId = sableBackend.getActiveSubLevelId();
        if (expectedTopId == null || !applyFreeBearingRegulatedMotor(serverLevel, expectedTopId, angle)) {
            commitFreeBearingRecoveryPending();
            return;
        }
        if (pendingDisassembleAfterZero) {
            enterFreeBearingReturningToZero();
            return;
        }

        ServoPropellerSlotManager.TopFollowReadiness readiness =
                propellerSlotManager.inspectTopFollowReadiness(serverLevel, expectedTopId);
        if (readiness == ServoPropellerSlotManager.TopFollowReadiness.RETRYABLE_UNRESOLVED) {
            return;
        }
        if (readiness == ServoPropellerSlotManager.TopFollowReadiness.INVALID) {
            commitFreeBearingRecoveryPending();
            return;
        }
        if (readiness == ServoPropellerSlotManager.TopFollowReadiness.RETRYABLE_REBIND) {
            propellerSlotManager.updateFreeBearingSlotMotionFailClosed(
                    serverLevel,
                    expectedTopId,
                    worldPosition.relative(getFacingDirection()),
                    getFacingDirection(),
                    true
            );
            readiness = propellerSlotManager.inspectTopFollowReadiness(serverLevel, expectedTopId);
            if (readiness != ServoPropellerSlotManager.TopFollowReadiness.READY) {
                if (readiness == ServoPropellerSlotManager.TopFollowReadiness.RETRYABLE_UNRESOLVED
                        || readiness == ServoPropellerSlotManager.TopFollowReadiness.INVALID) {
                    commitFreeBearingRecoveryPending();
                }
                return;
            }
        }

        boolean freeBearingApplied = applyFreeBearingMotor(serverLevel, expectedTopId);
        ServoPropellerSlotManager.TopFollowReadiness postApplyReadiness =
                propellerSlotManager.inspectTopFollowReadiness(serverLevel, expectedTopId);
        if (freeBearingApplied
                && postApplyReadiness == ServoPropellerSlotManager.TopFollowReadiness.READY) {
            commitFreeBearingPhase(FreeBearingLifecyclePhase.ACTIVE_FREE_BEARING);
            return;
        }

        takeOverFreeBearingRegulatedMotor(serverLevel);
    }

    private void tickFreeBearingActive(ServerLevel serverLevel) {
        UUID expectedTopId = sableBackend.getActiveSubLevelId();
        if (expectedTopId == null) {
            commitFreeBearingRecoveryPending();
            return;
        }

        ServoPropellerSlotManager.TopFollowReadiness readiness =
                propellerSlotManager.inspectTopFollowReadiness(serverLevel, expectedTopId);
        if (pendingDisassembleAfterZero
                || readiness != ServoPropellerSlotManager.TopFollowReadiness.READY) {
            takeOverFreeBearingRegulatedMotor(serverLevel);
            return;
        }

        boolean applied = applyFreeBearingMotor(serverLevel, expectedTopId);
        ServoPropellerSlotManager.TopFollowReadiness postApplyReadiness =
                propellerSlotManager.inspectTopFollowReadiness(serverLevel, expectedTopId);
        if (!applied || postApplyReadiness != ServoPropellerSlotManager.TopFollowReadiness.READY) {
            takeOverFreeBearingRegulatedMotor(serverLevel);
        }
    }

    private void tickFreeBearingRecoveryPending(ServerLevel serverLevel) {
        takeOverFreeBearingRegulatedMotor(serverLevel);
    }

    private void tickFreeBearingReturningToZero(ServerLevel serverLevel) {
        UUID expectedTopId = sableBackend.getActiveSubLevelId();
        if (expectedTopId == null) {
            commitFreeBearingRecoveryPending();
            return;
        }

        targetAngle = 0.0F;
        secondTargetAngle = 0.0F;
        pendingDisassembleZeroHoldTicks = 0;
        float requestedStep = Math.max(
                getDegreesPerTickForSignal(lastWestSignal),
                MIN_DISASSEMBLE_DEGREES_PER_TICK
        );

        if (Math.abs(angle) <= DISASSEMBLE_ZERO_SNAP_DEGREES) {
            boolean changed = Math.abs(angle) > ANGLE_EPSILON;
            angle = 0.0F;
            if (!applyFreeBearingDisassemblyReturnMotor(serverLevel, expectedTopId, 0.0F)) {
                recoverFromFreeBearingDisassemblyReturn(serverLevel, expectedTopId);
                return;
            }
            if (changed) {
                setChanged();
                if (canSendData()) sendData();
            }
            if (tickLaterGuiPhysicalNeutralConfirmation(serverLevel, true)) {
                completeFreeBearingDisassembly(serverLevel, expectedTopId);
            }
            return;
        }

        resetLaterGuiDisassemblyRuntimeState();
        float multiplier = getDisassemblyReturnSpeedMultiplierForCurrentGuiMode();
        float step = computeDisassembleReturnStepDegrees(Math.abs(angle), requestedStep, multiplier);
        float newAngle = approachAngle(angle, 0.0F, step);
        if (!applyFreeBearingDisassemblyReturnMotor(serverLevel, expectedTopId, newAngle)) {
            recoverFromFreeBearingDisassemblyReturn(serverLevel, expectedTopId);
            return;
        }
        if (Math.abs(newAngle - angle) > NORMAL_MOTION_EPSILON) {
            angle = newAngle;
            setChanged();
            if (canSendData()) sendData();
        }
    }

    private void takeOverFreeBearingRegulatedMotor(ServerLevel serverLevel) {
        FreeBearingLifecyclePhase previousPhase = freeBearingLifecyclePhase;
        UUID expectedTopId = sableBackend.getActiveSubLevelId();
        if (expectedTopId == null) {
            commitFreeBearingRecoveryPending();
            return;
        }

        Float physicalAngle = sableBackend.measureCurrentMotorAngleDegrees(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS
        );
        if (physicalAngle == null || !Float.isFinite(physicalAngle)
                || !applyFreeBearingRegulatedMotor(serverLevel, expectedTopId, physicalAngle)) {
            commitFreeBearingRecoveryPending();
            return;
        }

        angle = physicalAngle;
        prevAngle = physicalAngle;
        activeRotationProfile = SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
        rotationProfileTagPresent = true;
        if (pendingDisassembleAfterZero) {
            enterFreeBearingReturningToZero();
        } else {
            if (previousPhase == FreeBearingLifecyclePhase.RECOVERY_PENDING) {
                ServoPropellerSlotManager.TopFollowReadiness readiness =
                        propellerSlotManager.inspectTopFollowReadiness(serverLevel, expectedTopId);
                if (readiness == ServoPropellerSlotManager.TopFollowReadiness.RETRYABLE_UNRESOLVED
                        || readiness == ServoPropellerSlotManager.TopFollowReadiness.INVALID) {
                    return;
                }
            }
            commitFreeBearingPhase(FreeBearingLifecyclePhase.ASSEMBLING_REGULATED);
        }
    }

    private boolean applyFreeBearingRegulatedMotor(ServerLevel serverLevel, UUID expectedTopId, float targetDegrees) {
        return sableBackend.applyVerifiedFacingAxisMotor(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                expectedTopId,
                targetDegrees,
                getServoStiffnessPerInertia(serverLevel),
                getServoDampingPerInertia(serverLevel),
                TwisterMillConfig.getServoMinEffectiveInertia()
        ).appliedSuccessfully();
    }

    private boolean applyFreeBearingDisassemblyReturnMotor(
            ServerLevel serverLevel,
            UUID expectedTopId,
            float targetDegrees
    ) {
        if (!isConfiguredFreeBearingRotationMode()
                || freeBearingLifecyclePhase != FreeBearingLifecyclePhase.RETURNING_TO_ZERO
                || !pendingDisassembleAfterZero
                || activeRotationProfile
                != SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS
                || pendingLaterGuiRotationProfile
                != SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS) {
            return false;
        }

        double strengthMultiplier =
                TwisterMillConfig.getMode7DisassemblyReturnMotorStrengthMultiplier();
        return sableBackend.applyVerifiedFacingAxisMotor(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                expectedTopId,
                targetDegrees,
                getServoStiffnessPerInertia(serverLevel) * strengthMultiplier,
                getServoDampingPerInertia(serverLevel) * Math.sqrt(strengthMultiplier),
                TwisterMillConfig.getServoMinEffectiveInertia()
        ).appliedSuccessfully();
    }

    private boolean applyFreeBearingMotor(ServerLevel serverLevel, UUID expectedTopId) {
        return sableBackend.applyVerifiedFacingAxisMotor(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                expectedTopId,
                0.0F,
                0.0D,
                TwisterMillConfig.getFreeBearingDampingPerInertia(),
                TwisterMillConfig.getServoMinEffectiveInertia()
        ).appliedSuccessfully();
    }

    private void enterFreeBearingReturningToZero() {
        pendingDisassembleAfterZero = true;
        pendingLaterGuiRotationProfile = SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
        resetLaterGuiDisassemblyRuntimeState();
        targetAngle = 0.0F;
        secondTargetAngle = 0.0F;
        commitFreeBearingPhase(FreeBearingLifecyclePhase.RETURNING_TO_ZERO);
    }

    private void commitFreeBearingRecoveryPending() {
        clearLaterGuiDisassemblyReturnState();
        commitFreeBearingPhase(FreeBearingLifecyclePhase.RECOVERY_PENDING);
    }

    private void recoverFromFreeBearingDisassemblyReturn(
            ServerLevel serverLevel,
            UUID expectedTopId
    ) {
        applyFreeBearingRegulatedMotor(serverLevel, expectedTopId, 0.0F);
        commitFreeBearingRecoveryPending();
    }

    private void commitFreeBearingPhase(FreeBearingLifecyclePhase phase) {
        if (freeBearingLifecyclePhase == phase) {
            updateFreeBearingTopFollowPolicy();
            return;
        }
        freeBearingLifecyclePhase = phase;
        updateFreeBearingTopFollowPolicy();
        setChanged();
        if (canSendData()) sendData();
    }

    private void updateFreeBearingTopFollowPolicy() {
        boolean failClosed = isConfiguredFreeBearingRotationMode()
                && freeBearingLifecyclePhase != FreeBearingLifecyclePhase.UNASSEMBLED;
        propellerSlotManager.setFreeBearingTopFollowPolicy(
                failClosed,
                sableBackend.getActiveSubLevelId(),
                failClosed && freeBearingLifecyclePhase == FreeBearingLifecyclePhase.ASSEMBLING_REGULATED
        );
    }

    private void completeFreeBearingDisassembly(ServerLevel serverLevel, UUID expectedTopId) {
        if (!preparePitchClearanceForDisassembly()) {
            return;
        }

        if (RememberedSableShipMemory.isRememberContraptionEnabledFor(getBlockState())) {
            rememberedShipMemory.replaceFromWorldPositions(
                    worldPosition,
                    getFacingDirection(),
                    sableBackend.snapshotRestoredBlockPositions(serverLevel, worldPosition)
            );
        }
        if (!sableBackend.disassembleVerifiedFacingAxis(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                expectedTopId
        )) {
            recoverFromFreeBearingDisassemblyReturn(serverLevel, expectedTopId);
            return;
        }

        AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
        running = false;
        assembleNextTick = false;
        manualEnabled = false;
        pendingDisassembleAfterZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        clearMode3DisassemblyReturnState();
        clearLaterGuiDisassemblyReturnState();
        angle = 0.0F;
        prevAngle = 0.0F;
        secondAngle = 0.0F;
        secondPrevAngle = 0.0F;
        secondTargetAngle = 0.0F;
        assembledBlockCount = 0;
        pendingPropellerSlotRejoinSyncTicks = 0;
        clearPropellerSlotPreviewSync();
        freeBearingLifecyclePhase = FreeBearingLifecyclePhase.UNASSEMBLED;
        updateFreeBearingTopFollowPolicy();
        updateVisualRunning(false);
        setChanged();
        if (canSendData()) sendData();
    }

    private void stopMotion() {
        targetAngle = 0.0F;
        secondTargetAngle = 0.0F;
    }

    private int getSideSignal(Direction side) {
        if (level == null)
            return 0;

        return Mth.clamp(level.getSignal(worldPosition.relative(side), side), 0, 15);
    }

    private Direction getRelativeWestInputSide() {
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        if (facing.getAxis().isHorizontal())
            return facing.getCounterClockWise();

        return Direction.WEST;
    }

    private Direction getRelativeEastInputSide() {
        BlockState state = getBlockState();
        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        if (facing.getAxis().isHorizontal())
            return facing.getClockWise();

        return Direction.EAST;
    }

    private Direction getOppositeTopInputSide() {
        return getFacingDirection().getOpposite();
    }

    private int getWestSpeedSignal() {
        return getSideSignal(getRelativeWestInputSide());
    }

    private int getEastAngleSignal() {
        return getSideSignal(getRelativeEastInputSide());
    }

    private int getOppositeTopModeSignal() {
        return getSideSignal(getOppositeTopInputSide());
    }

    public boolean shouldHandleInternalRedstoneLinkWrench(Direction side) {
        return isInternalRedstoneLinkMode() && side == getInternalRedstoneLinkSide()
                || isSecondaryInternalRedstoneLinkEligible()
                && side == getSecondaryInternalRedstoneLinkSide();
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean tryToggleInternalRedstoneLinkReceiver(Direction side, @Nullable Player player) {
        if (!shouldHandleInternalRedstoneLinkWrench(side))
            return false;

        if (level == null || level.isClientSide)
            return true;

        boolean secondary = side == getSecondaryInternalRedstoneLinkSide()
                && isSecondaryInternalRedstoneLinkEligible();
        boolean receiverActive;
        if (secondary) {
            secondaryInternalRedstoneLinkActive = !secondaryInternalRedstoneLinkActive;
            secondaryInternalRedstoneLinkReceivedSignal = 0;
            secondaryBinaryReceiver.reset(false);
            secondaryHasValidBinaryFrame = false;
            secondaryBinaryAngleSignal = 0;
            secondTargetAngle = 0.0F;
            receiverActive = secondaryInternalRedstoneLinkActive;
            if (secondaryInternalRedstoneLink != null) {
                secondaryInternalRedstoneLink.notifySignalChange();
            }
        } else {
            internalRedstoneLinkActive = !internalRedstoneLinkActive;
            internalRedstoneLinkReceivedSignal = 0;
            receiverActive = internalRedstoneLinkActive;
            if (internalRedstoneLink != null) {
                internalRedstoneLink.notifySignalChange();
            }
        }

        updateVisualPowerState();

        if (player != null) {
            player.displayClientMessage(
                    Component.translatable(receiverActive
                                     ? "twistermill.servo.redstone_link.receiver_active"
                                     : "twistermill.servo.redstone_link.receiver_inactive")
                            .withStyle(receiverActive ? ChatFormatting.GREEN : ChatFormatting.RED),
                    true
            );
        }

        setChanged();
        if (canSendData()) sendData();
        return true;
    }

    private void setInternalRedstoneLinkSignal(int power) {
        if (level == null || level.isClientSide)
            return;

        if (!isInternalRedstoneLinkMode() || !internalRedstoneLinkActive) {
            return;
        }

        int clampedPower = Mth.clamp(power, 0, 15);
        if (internalRedstoneLinkReceivedSignal == clampedPower)
            return;

        internalRedstoneLinkReceivedSignal = clampedPower;
        updateVisualPowerState();
        setChanged();
        if (canSendData()) sendData();
    }

    private void setSecondaryInternalRedstoneLinkSignal(int power) {
        if (level == null || level.isClientSide
                || !isSecondaryInternalRedstoneLinkEligible()
                || !secondaryInternalRedstoneLinkActive) {
            return;
        }

        int clampedPower = Mth.clamp(power, 0, 15);
        if (secondaryInternalRedstoneLinkReceivedSignal == clampedPower) {
            return;
        }
        secondaryInternalRedstoneLinkReceivedSignal = clampedPower;
        updateVisualPowerState();
        setChanged();
        if (canSendData()) sendData();
    }

    private void disableInternalRedstoneLink() {
        internalRedstoneLinkActive = false;
        internalRedstoneLinkReceivedSignal = 0;

        if (internalRedstoneLink != null) {
            internalRedstoneLink.setFrequency(true, ItemStack.EMPTY);
            internalRedstoneLink.setFrequency(false, ItemStack.EMPTY);
        }

        clearBinaryControlState();
    }

    private boolean hasDirectInputPriority(int speedSignal, int angleSignal) {
        return speedSignal > 0 || angleSignal > 0;
    }

    private void updateControlSignalsFromInputs(boolean advanceBinaryReceiver) {
        boolean internalLinkMode = isInternalRedstoneLinkMode();
        if (internalLinkMode) {
            lastWestSignal = 0;
            lastEastSignal = 0;
        } else {
            lastWestSignal = getWestSpeedSignal();
            lastEastSignal = getEastAngleSignal();
        }

        int rawOppositeTopSignal = internalLinkMode && internalRedstoneLinkActive
                ? internalRedstoneLinkReceivedSignal
                : getOppositeTopModeSignal();
        boolean oppositeHigh = rawOppositeTopSignal > 0;
        binaryVisualInputHigh = oppositeHigh;
        boolean directInputPriority = !internalLinkMode && hasDirectInputPriority(lastWestSignal, lastEastSignal);

        if (directInputPriority) {
            resetControlMarkerReceiver(oppositeHigh);
            resetBinaryReceiver(oppositeHigh);
            lastOppositeTopSignal = getOppositeTopInputLocked() ? 0 : rawOppositeTopSignal;
            targetAngle = pendingDisassembleAfterZero ? 0.0F : getTargetAngleForSignal(lastEastSignal);
            updateSecondaryBinaryInput(advanceBinaryReceiver);
            return;
        }

        if (!TwisterMillConfig.isServoTwisterBinaryInputEnabled()) {
            resetControlMarkerReceiver(oppositeHigh);
            resetBinaryReceiver(oppositeHigh);
            clearBinaryControlState();
            lastOppositeTopSignal = 0;
            targetAngle = pendingDisassembleAfterZero ? 0.0F : angle;
            updateSecondaryBinaryInput(advanceBinaryReceiver);
            return;
        }

        boolean controlMarkerTriggered = false;
        if (advanceBinaryReceiver) {
            controlMarkerTriggered = tickControlMarkerReceiver(oppositeHigh);
            if (controlMarkerTriggered) {
                if (isLaterGuiAssemblyOption()) {
                    executeLaterGuiToggle();
                } else {
                    executeManualToggle();
                }
                resetBinaryReceiver(oppositeHigh);
                secondaryBinaryReceiver.reset(secondaryInternalRedstoneLinkReceivedSignal > 0);
            } else {
                tickBinaryReceiver(oppositeHigh);
            }
        } else {
            binaryLastInputHigh = oppositeHigh;
            controlMarkerLastInputHigh = oppositeHigh;
        }

        if (controlMarkerTriggered) {
            int resolvedBinaryMode = getOppositeTopInputLocked() ? 0 : binaryModeSignal;
            lastOppositeTopSignal = resolvedBinaryMode;
            targetAngle = pendingDisassembleAfterZero
                    ? 0.0F
                    : getBinaryTargetAngleForMode(resolvedBinaryMode, binaryAngleSignal);
            updateSecondaryBinaryInput(false);
            return;
        }

        int resolvedBinaryMode = getOppositeTopInputLocked() ? 0 : binaryModeSignal;
        lastOppositeTopSignal = resolvedBinaryMode;
        targetAngle = pendingDisassembleAfterZero
                ? 0.0F
                : getBinaryTargetAngleForMode(resolvedBinaryMode, binaryAngleSignal);
        updateSecondaryBinaryInput(advanceBinaryReceiver);
    }

    private void updateSecondaryBinaryInput(boolean advanceReceiver) {
        boolean eligible = isSecondaryInternalRedstoneLinkEligible();
        synchronizeSecondaryInternalRedstoneLinkEligibility(eligible);
        boolean inputHigh = eligible
                && secondaryInternalRedstoneLinkActive
                && secondaryInternalRedstoneLinkReceivedSignal > 0;
        if (!eligible) {
            secondaryInternalRedstoneLinkReceivedSignal = 0;
            //noinspection ConstantValue
            secondaryBinaryReceiver.reset(inputHigh);
            secondaryHasValidBinaryFrame = false;
            secondaryBinaryAngleSignal = 0;
            secondTargetAngle = 0.0F;
            return;
        }

        boolean secondaryStateChanged = false;
        if (advanceReceiver) {
            int frame = secondaryBinaryReceiver.tick(inputHigh);
            if (frame != BinarySignalFrameReceiver.NO_FRAME) {
                secondaryBinaryAngleSignal = frame & 0xF;
                secondaryHasValidBinaryFrame = true;
                secondaryStateChanged = true;
            }
        } else {
            secondaryBinaryReceiver.synchronizeInput(inputHigh);
        }

        float resolvedSecondTargetAngle = secondaryHasValidBinaryFrame && !pendingDisassembleAfterZero
                ? computeTwoAxisTiltTarget(binaryModeSignal, secondaryBinaryAngleSignal)
                : 0.0F;
        if (Math.abs(resolvedSecondTargetAngle - secondTargetAngle) > ANGLE_EPSILON) {
            secondTargetAngle = resolvedSecondTargetAngle;
            secondaryStateChanged = true;
        }

        if (secondaryStateChanged) {
            setChanged();
            if (canSendData()) sendData();
        }
    }

    private void synchronizeSecondaryInternalRedstoneLinkEligibility(boolean eligible) {
        if (previousSecondaryInternalRedstoneLinkEligibility == eligible) {
            return;
        }
        previousSecondaryInternalRedstoneLinkEligibility = eligible;
        if (secondaryInternalRedstoneLink != null) {
            secondaryInternalRedstoneLink.notifySignalChange();
        }
        setChanged();
        if (canSendData()) sendData();
    }

    private void clearBinaryControlState() {
        binaryModeSignal = 0;
        binarySpeedSignal = 0;
        binaryAngleSignal = 0;
        hasValidBinaryFrame = false;
    }

    private void resetBinaryReceiver(boolean currentInputHigh) {
        binaryReceivePhase = BinaryReceivePhase.IDLE;
        binaryWorkingBits = 0;
        binaryBitsRead = 0;
        binaryReceiveTicks = 0;
        binaryStartOnTicks = 0;
        binaryStartOffTicks = 0;
        binaryBitCellTick = 0;
        binaryStartBitCellNextTick = false;
        binaryLastInputHigh = currentInputHigh;
    }

    private void resetControlMarkerReceiver(boolean currentInputHigh) {
        controlMarkerReceivePhase = ControlMarkerReceivePhase.IDLE;
        controlMarkerHighTicks = 0;
        controlMarkerLowTicks = 0;
        controlMarkerReceiveTicks = 0;
        controlMarkerLastInputHigh = currentInputHigh;
    }

    @SuppressWarnings("ConstantValue")
    private boolean tickControlMarkerReceiver(boolean currentInputHigh) {
        if (!BinarySignalProtocol.isControlMarkerConfigurationSafe()) {
            resetControlMarkerReceiver(currentInputHigh);
            return false;
        }

        boolean triggered = false;
        switch (controlMarkerReceivePhase) {
            case IDLE -> {
                if (!controlMarkerLastInputHigh && currentInputHigh) {
                    controlMarkerReceivePhase = ControlMarkerReceivePhase.HIGH_1;
                    controlMarkerHighTicks = 1;
                    controlMarkerReceiveTicks = 1;
                }
            }
            case HIGH_1 -> {
                controlMarkerReceiveTicks++;
                if (currentInputHigh) {
                    controlMarkerHighTicks++;
                    if (controlMarkerHighTicks > BinarySignalProtocol.CONTROL_MARKER_MAX_HIGH_TICKS) {
                        resetControlMarkerReceiver(currentInputHigh);
                    }
                } else {
                    if (!BinarySignalProtocol.isControlMarkerHighTicksInRange(controlMarkerHighTicks)) {
                        resetControlMarkerReceiver(currentInputHigh);
                    } else {
                        controlMarkerReceivePhase = ControlMarkerReceivePhase.LOW_GAP;
                        controlMarkerLowTicks = 1;
                    }
                }
            }
            case LOW_GAP -> {
                controlMarkerReceiveTicks++;
                if (currentInputHigh && !controlMarkerLastInputHigh) {
                    if (!BinarySignalProtocol.isControlMarkerLowGapTicksInRange(controlMarkerLowTicks)) {
                        resetControlMarkerReceiver(currentInputHigh);
                    } else {
                        controlMarkerReceivePhase = ControlMarkerReceivePhase.HIGH_2;
                        controlMarkerHighTicks = 1;
                    }
                } else if (!currentInputHigh) {
                    controlMarkerLowTicks++;
                    if (controlMarkerLowTicks > BinarySignalProtocol.CONTROL_MARKER_MAX_LOW_GAP_TICKS) {
                        resetControlMarkerReceiver(currentInputHigh);
                    }
                }
            }
            case HIGH_2 -> {
                controlMarkerReceiveTicks++;
                if (currentInputHigh) {
                    controlMarkerHighTicks++;
                    if (controlMarkerHighTicks > BinarySignalProtocol.CONTROL_MARKER_MAX_HIGH_TICKS) {
                        resetControlMarkerReceiver(currentInputHigh);
                    }
                } else {
                    if (BinarySignalProtocol.isControlMarkerHighTicksInRange(controlMarkerHighTicks)) {
                        triggered = true;
                    }
                    resetControlMarkerReceiver(currentInputHigh);
                }
            }
        }

        if (!triggered
                && controlMarkerReceivePhase != ControlMarkerReceivePhase.IDLE
                && controlMarkerReceiveTicks > BinarySignalProtocol.CONTROL_MARKER_RECEIVE_TIMEOUT_TICKS) {
            resetControlMarkerReceiver(currentInputHigh);
        }

        controlMarkerLastInputHigh = currentInputHigh;
        return triggered;
    }

    @SuppressWarnings("ConstantValue")
    private void tickBinaryReceiver(boolean currentInputHigh) {
        if (binaryStartBitCellNextTick) {
            binaryStartBitCellNextTick = false;
            binaryReceivePhase = BinaryReceivePhase.BIT_CELL;
            binaryBitCellTick = 0;
        }

        switch (binaryReceivePhase) {
            case IDLE -> {
                if (!binaryLastInputHigh && currentInputHigh) {
                    binaryReceivePhase = BinaryReceivePhase.START_ON;
                    binaryStartOnTicks = 1;
                    binaryReceiveTicks = 1;
                }
            }
            case START_ON -> {
                binaryReceiveTicks++;
                if (currentInputHigh) {
                    binaryStartOnTicks++;
                    if (binaryStartOnTicks > BINARY_START_MAX_TICKS) {
                        resetBinaryReceiver(currentInputHigh);
                    }
                } else {
                    if (binaryStartOnTicks < BINARY_START_MIN_TICKS || binaryStartOnTicks > BINARY_START_MAX_TICKS) {
                        resetBinaryReceiver(currentInputHigh);
                    } else {
                        binaryReceivePhase = BinaryReceivePhase.START_OFF;
                        binaryStartOffTicks = 1;
                    }
                }
            }
            case START_OFF -> {
                binaryReceiveTicks++;
                if (currentInputHigh && !binaryLastInputHigh) {
                    if (binaryStartOffTicks >= BINARY_START_MIN_TICKS && binaryStartOffTicks <= BINARY_START_MAX_TICKS) {
                        binaryStartBitCellNextTick = true;
                    } else {
                        resetBinaryReceiver(currentInputHigh);
                    }
                } else if (!currentInputHigh) {
                    binaryStartOffTicks++;
                    if (binaryStartOffTicks > BINARY_START_MAX_TICKS) {
                        resetBinaryReceiver(currentInputHigh);
                    } else if (binaryStartOffTicks == BINARY_HALF_PHASE_TICKS) {
                        binaryStartBitCellNextTick = true;
                    }
                } else {
                    resetBinaryReceiver(currentInputHigh);
                }
            }
            case BIT_CELL -> {
                binaryReceiveTicks++;

                if (binaryBitCellTick == BINARY_BIT_SAMPLE_TICK) {
                    int bit = currentInputHigh ? 1 : 0;
                    binaryWorkingBits = (binaryWorkingBits << 1) | bit;
                }

                if (binaryBitCellTick >= BINARY_SECOND_HALF_START_TICK && currentInputHigh) {
                    resetBinaryReceiver(currentInputHigh);
                    break;
                }

                if (binaryBitCellTick >= BINARY_BIT_CELL_TICKS - 1) {
                    binaryBitsRead++;
                    if (binaryBitsRead == BINARY_FRAME_BITS) {
                        commitBinaryFrame(binaryWorkingBits);
                        resetBinaryReceiver(currentInputHigh);
                    } else {
                        binaryBitCellTick = 0;
                    }
                } else {
                    binaryBitCellTick++;
                }
            }
        }

        if (binaryReceivePhase != BinaryReceivePhase.IDLE && binaryReceiveTicks > BINARY_RECEIVE_TIMEOUT_TICKS) {
            resetBinaryReceiver(currentInputHigh);
        }

        binaryLastInputHigh = currentInputHigh;
    }

    private void commitBinaryFrame(int frameBits) {
        binaryModeSignal = (frameBits >> 8) & 0xF;
        binarySpeedSignal = (frameBits >> 4) & 0xF;
        binaryAngleSignal = frameBits & 0xF;
        hasValidBinaryFrame = true;
        updateVisualPowerState();
        setChanged();
        if (canSendData()) sendData();
    }

    private float getBaseTargetAngleForSignal(int redstonePower) {
        int clampedPower = ServoRedstoneMappings.clampSignal(redstonePower);
        float baseAngle = ServoRedstoneMappings.baseAngleFromSignalAndConfiguredMax(clampedPower, getConfiguredMaxDegrees());
        float finalAngle = Mth.clamp(baseAngle, 0.0F, 360.0F);
        return -finalAngle;
    }

    private float getTargetAngleForSignal(int redstonePower) {
        if (isTwoAxisTiltModeSignal(lastOppositeTopSignal)) {
            return computeTwoAxisTiltTarget(lastOppositeTopSignal, redstonePower);
        }
        if (isExtendedTargetModeSignal(lastOppositeTopSignal)) {
            return computeExtendedTargetAngle(lastOppositeTopSignal, redstonePower);
        }
        float requested = Math.abs(getBaseTargetAngleForSignal(redstonePower)) * getAngleMultiplierForModeSignal(lastOppositeTopSignal);
        float finalAngle = Mth.clamp(requested, 0.0F, 360.0F);
        return -finalAngle;
    }

    private float getBinaryTargetAngleForMode(int modeSignal, int angleSignal) {
        if (isTwoAxisTiltModeSignal(modeSignal)) {
            return computeTwoAxisTiltTarget(modeSignal, angleSignal);
        }
        if (isExtendedTargetModeSignal(modeSignal)) {
            return computeExtendedTargetAngle(modeSignal, angleSignal);
        }
        float magnitude = Math.abs(getBaseTargetAngleForSignal(angleSignal));
        if (modeSignal == 2) {
            return magnitude;
        }
        if (modeSignal == 1 || modeSignal >= 3) {
            return -magnitude;
        }
        return angle;
    }

    private static float computeTwoAxisTiltTarget(int modeSignal, int angleSignal) {
        float magnitude = ServoTwoAxisRotationMath.magnitudeFromSignal(angleSignal);
        return modeSignal == MODE_TWO_AXIS_TILT ? -magnitude : magnitude;
    }

    private float getDegreesPerTickForSignal(int redstonePower) {
        return ServoRedstoneMappings.effectiveSpeedDegreesPerTickFromSignal(redstonePower, speedZeroMovementEnabled);
    }

    private float computeExtendedTargetAngle(int modeSignal, int angleSignal) {
        int clampedAngle = ServoRedstoneMappings.clampSignal(angleSignal);
        if (clampedAngle <= 0) {
            return 0.0F;
        }

        float target = switch (modeSignal) {
            case MODE_3_STEP -> {
                if (clampedAngle <= 5) {
                    yield 0.0F;
                }
                yield clampedAngle <= 10 ? 120.0F : 240.0F;
            }
            case MODE_ABSOLUTE_0_540, MODE_FLIP -> clampedAngle * 36.0F;
            case MODE_CENTERED -> (clampedAngle - 8) * 36.0F;
            case MODE_FINE_0_180 -> clampedAngle * 12.0F;
            case MODE_FINE_CENTERED -> (clampedAngle - 8) * 12.0F;
            case MODE_INVERTED_FLIP -> -(clampedAngle * 36.0F);
            default -> angle;
        };
        return clampServoTargetDegrees(target);
    }

    private float computeMode6OscillationTarget(boolean directInputPriority) {
        int angleSignal = directInputPriority ? lastEastSignal : binaryAngleSignal;
        int speedSignal = directInputPriority ? lastWestSignal : binarySpeedSignal;
        float amplitude = 90.0F + (ServoRedstoneMappings.clampSignal(angleSignal) * 10.0F);
        return computePositiveOscillationTarget(amplitude, amplitude, speedSignal);
    }

    private float computePositiveOscillationTarget(float amplitude, float spanDegrees, int speedSignal) {
        if (amplitude <= 0.0F || spanDegrees <= 0.0F) {
            return 0.0F;
        }
        if (!advanceOscillationPhase(spanDegrees, speedSignal)) {
            return angle;
        }

        double normalized = (1.0D - Math.cos(mode6OscillationPhase)) * 0.5D;
        return clampServoTargetDegrees((float) (normalized * amplitude));
    }

    private boolean advanceOscillationPhase(float spanDegrees, int speedSignal) {
        float mappedSpeed = getDegreesPerTickForSignal(speedSignal);
        if (mappedSpeed <= 0.0F) {
            return false;
        }
        float speed = Math.max(mappedSpeed, MODE6_MIN_DEGREES_PER_TICK);
        mode6OscillationPhase = wrapMode6Phase(mode6OscillationPhase + (2.0D * speed) / spanDegrees);
        return true;
    }

    private static float clampServoTargetDegrees(float target) {
        return Mth.clamp(target, -MAX_EXTENDED_MODE_DEGREES, MAX_EXTENDED_MODE_DEGREES);
    }

    private static double wrapMode6Phase(double phase) {
        phase %= MODE6_TWO_PI;
        return phase < 0.0D ? phase + MODE6_TWO_PI : phase;
    }

    private static float approachAngle(float current, float target, float maxStep) {
        float diff = target - current;

        if (Math.abs(diff) <= maxStep)
            return target;

        return current + Math.signum(diff) * maxStep;
    }

    private boolean approachTwoAxisTargets(float axis1Target, float axis2Target, float maxStep) {
        if (!(maxStep > 0.0F) || !Float.isFinite(maxStep)) {
            return false;
        }
        float delta1 = axis1Target - angle;
        float delta2 = axis2Target - secondAngle;
        double distance = Math.hypot(delta1, delta2);
        if (!(distance > NORMAL_MOTION_EPSILON) || !Double.isFinite(distance)) {
            return false;
        }
        if (distance <= maxStep) {
            angle = axis1Target;
            secondAngle = axis2Target;
        } else {
            float scale = (float) (maxStep / distance);
            angle += delta1 * scale;
            secondAngle += delta2 * scale;
        }
        return true;
    }

    private static float computeDisassembleReturnStepDegrees(
            float currentAngle,
            float requestedStep,
            float multiplier
    ) {
        float remaining = Math.abs(currentAngle);
        if (remaining <= 0.0F) {
            return 0.0F;
        }

        float normalStep = Math.max(requestedStep, MIN_DISASSEMBLE_DEGREES_PER_TICK);
        float limitedStep;
        if (remaining <= DISASSEMBLE_RETURN_SLOW_ZONE_DEGREES) {
            limitedStep = MIN_DISASSEMBLE_DEGREES_PER_TICK;
        } else if (remaining >= DISASSEMBLE_RETURN_DECEL_START_DEGREES) {
            limitedStep = normalStep;
        } else {
            float range = DISASSEMBLE_RETURN_DECEL_START_DEGREES - DISASSEMBLE_RETURN_SLOW_ZONE_DEGREES;
            float t = (remaining - DISASSEMBLE_RETURN_SLOW_ZONE_DEGREES) / range;
            limitedStep = MIN_DISASSEMBLE_DEGREES_PER_TICK
                    + (normalStep - MIN_DISASSEMBLE_DEGREES_PER_TICK) * t;
        }

        float unscaledRate = Math.max(MIN_DISASSEMBLE_DEGREES_PER_TICK, limitedStep);
        return scaleAndClampDisassemblyReturnRate(remaining, unscaledRate, multiplier);
    }

    private static float scaleAndClampDisassemblyReturnRate(
            float remaining,
            float unscaledRate,
            float multiplier
    ) {
        float scaledRate = unscaledRate * multiplier;
        return Math.min(remaining, scaledRate);
    }

    private static float computeMode3DisassemblyReturnTargetSpeed(
            float remaining,
            float previousSpeed
    ) {
        if (!Float.isFinite(remaining)
                || !Float.isFinite(previousSpeed)
                || remaining <= 0.0F) {
            return 0.0F;
        }

        float brakingSpeed = (float) Math.sqrt(
                MODE3_DISASSEMBLY_MIN_TARGET_STEP_DEGREES
                        * MODE3_DISASSEMBLY_MIN_TARGET_STEP_DEGREES
                        + 2.0F
                        * MODE3_DISASSEMBLY_TARGET_DECELERATION_DEGREES_PER_TICK_SQUARED
                        * remaining
        );
        float desiredSpeed = Math.min(
                MODE3_DISASSEMBLY_MAX_TARGET_SPEED_DEGREES_PER_TICK,
                brakingSpeed
        );
        float minimumNextSpeed = Math.max(
                MODE3_DISASSEMBLY_MIN_TARGET_STEP_DEGREES,
                previousSpeed
                        - MODE3_DISASSEMBLY_TARGET_DECELERATION_DEGREES_PER_TICK_SQUARED
        );
        float maximumNextSpeed = Math.min(
                MODE3_DISASSEMBLY_MAX_TARGET_SPEED_DEGREES_PER_TICK,
                previousSpeed
                        + MODE3_DISASSEMBLY_TARGET_ACCELERATION_DEGREES_PER_TICK_SQUARED
        );
        return Mth.clamp(desiredSpeed, minimumNextSpeed, maximumNextSpeed);
    }

    private void tickMode3DisassemblyReturn(
            ServerLevel serverLevel,
            boolean allowConfirmationThisTick
    ) {
        targetAngle = 0.0F;
        secondTargetAngle = 0.0F;
        pendingDisassembleZeroHoldTicks = 0;

        if (!running
                || !pendingDisassembleAfterZero
                || !isSafeBinaryDisassemblyReturnActive()
                || !sableBackend.isActive()
                || activeRotationProfile
                != SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS) {
            resetMode3DisassemblyConfirmation();
            return;
        }

        Float physicalAngle = sableBackend.measureCurrentMotorAngleDegrees(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS
        );
        publishPhysicalBearingMeasuredAngleForClient(physicalAngle);
        if (physicalAngle == null || !Float.isFinite(physicalAngle)) {
            resetMode3DisassemblyConfirmation();
            return;
        }

        if (!mode3DisassemblyReturnInitialized) {
            mode3DisassemblyReturnCommandAngle = physicalAngle;
            mode3DisassemblyReturnTargetSpeed = 0.0F;
            mode3DisassemblyReturnInitialized = true;
            resetMode3DisassemblyConfirmation();
        }

        float remaining = Math.abs(mode3DisassemblyReturnCommandAngle);
        if (remaining > 0.0F) {
            mode3DisassemblyReturnTargetSpeed = computeMode3DisassemblyReturnTargetSpeed(
                    remaining,
                    mode3DisassemblyReturnTargetSpeed
            );
            float multiplier = getDisassemblyReturnSpeedMultiplierForCurrentGuiMode();
            float step = scaleAndClampDisassemblyReturnRate(
                    remaining,
                    mode3DisassemblyReturnTargetSpeed,
                    multiplier
            );
            mode3DisassemblyReturnCommandAngle = remaining <= step
                    ? 0.0F
                    : approachAngle(mode3DisassemblyReturnCommandAngle, 0.0F, step);
        } else {
            mode3DisassemblyReturnCommandAngle = 0.0F;
            mode3DisassemblyReturnTargetSpeed = 0.0F;
        }

        SableInteractiveContraptionBackend.Mode3ReturnMotorCommand command =
                sableBackend.applyMode3DisassemblyReturnMotor(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        mode3DisassemblyReturnCommandAngle,
                        getServoStiffnessPerInertia(serverLevel)
                );
        if (command == null) {
            resetMode3DisassemblyConfirmation();
            return;
        }

        SableInteractiveContraptionBackend.Mode3RelativePoseProbe currentProbe =
                sableBackend.sampleMode3RelativePose(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        command
                );
        if (currentProbe == null) {
            resetMode3DisassemblyConfirmation();
            return;
        }
        publishPhysicalBearingMeasuredAngleForClient(currentProbe.facingAxisAngleDegrees());

        if (Float.floatToRawIntBits(mode3DisassemblyReturnCommandAngle)
                != Float.floatToRawIntBits(0.0F)) {
            resetMode3DisassemblyConfirmation(currentProbe);
            return;
        }

        if (!allowConfirmationThisTick) {
            resetMode3DisassemblyConfirmation(currentProbe);
            return;
        }

        SableInteractiveContraptionBackend.Mode3PosePrecheck precheck =
                sableBackend.precheckMode3DisassemblyPose(
                        mode3PreviousDisassemblyPoseProbe,
                        currentProbe
                );
        if (!precheck.eligible()) {
            resetMode3DisassemblyConfirmation(currentProbe);
            return;
        }

        SableInteractiveContraptionBackend.Mode3DisassemblySafety safety =
                sableBackend.inspectMode3DisassemblySafety(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        command,
                        mode3PreviousDisassemblyPoseProbe,
                        currentProbe,
                        precheck
                );
        if (!safety.safe() || safety.currentProbe() == null) {
            resetMode3DisassemblyConfirmation(safety.currentProbe());
            return;
        }

        mode3DisassemblyConfirmationTicks = Math.min(
                MODE3_DISASSEMBLY_CONFIRMATION_TICKS,
                mode3DisassemblyConfirmationTicks + 1
        );
        if (mode3DisassemblyConfirmationTicks < MODE3_DISASSEMBLY_CONFIRMATION_TICKS) {
            mode3PreviousDisassemblyPoseProbe = safety.currentProbe();
            return;
        }

        SableInteractiveContraptionBackend.Mode3DisassemblySafety finalSafety =
                sableBackend.inspectMode3DisassemblySafety(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        command,
                        mode3PreviousDisassemblyPoseProbe,
                        safety.currentProbe(),
                        precheck
                );
        if (!finalSafety.safe() || finalSafety.currentProbe() == null) {
            resetMode3DisassemblyConfirmation(finalSafety.currentProbe());
            return;
        }

        mode3PreviousDisassemblyPoseProbe = finalSafety.currentProbe();
        disassemble();
    }

    private void beginMode3DisassemblyReturn() {
        pendingMode3DisassemblyReturn = true;
        pendingExtendedBinaryDisassemblyReturn = false;
        resetMode3DisassemblyRuntimeState();
    }

    private void beginExtendedBinaryDisassemblyReturn() {
        pendingMode3DisassemblyReturn = false;
        pendingExtendedBinaryDisassemblyReturn = true;
        resetMode3DisassemblyRuntimeState();
    }

    private void clearMode3DisassemblyReturnState() {
        pendingMode3DisassemblyReturn = false;
        pendingExtendedBinaryDisassemblyReturn = false;
        resetMode3DisassemblyRuntimeState();
    }

    private void resetMode3DisassemblyRuntimeState() {
        mode3DisassemblyReturnInitialized = false;
        mode3DisassemblyReturnCommandAngle = 0.0F;
        mode3DisassemblyReturnTargetSpeed = 0.0F;
        resetMode3DisassemblyConfirmation();
    }

    private void resetMode3DisassemblyConfirmation() {
        resetMode3DisassemblyConfirmation(null);
    }

    private void resetMode3DisassemblyConfirmation(
            @Nullable SableInteractiveContraptionBackend.Mode3RelativePoseProbe baselineProbe
    ) {
        mode3DisassemblyConfirmationTicks = 0;
        mode3PreviousDisassemblyPoseProbe = baselineProbe;
    }

    private int getContraptionBlockCount() {
        return assembledBlockCount;
    }

    private void updateVisualRunning(boolean runningVisual) {
        if (level == null || level.isClientSide)
            return;

        if (runningVisual == lastVisualRunning)
            return;

        lastVisualRunning = runningVisual;

        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(ServoTwisterBlock.RUNNING)
                && state.getValue(ServoTwisterBlock.RUNNING) != runningVisual) {
            level.setBlock(worldPosition, state.setValue(ServoTwisterBlock.RUNNING, runningVisual), 3);
        }
    }

    private ServoTwisterBlock.PowerVisualState computePowerVisualState() {
        if (isPropellerSlotMode())
            return ServoTwisterBlock.PowerVisualState.UNPOWERED;

        if (shouldBlinkForMissingBinaryReceivePath()) {
            return isBinaryWaitBlinkOn()
                    ? ServoTwisterBlock.PowerVisualState.BI
                    : ServoTwisterBlock.PowerVisualState.UNPOWERED;
        }

        if (isInternalRedstoneLinkMode()) {
            return isBinaryVisualInputHigh()
                    ? ServoTwisterBlock.PowerVisualState.BI
                    : ServoTwisterBlock.PowerVisualState.UNPOWERED;
        }

        boolean angleInputActive = lastEastSignal > 0;
        boolean speedInputActive = lastWestSignal > 0;

        if (angleInputActive && speedInputActive)
            return ServoTwisterBlock.PowerVisualState.BI;
        if (angleInputActive)
            return ServoTwisterBlock.PowerVisualState.ANGLE;
        if (speedInputActive)
            return ServoTwisterBlock.PowerVisualState.SPEED;
        return ServoTwisterBlock.PowerVisualState.UNPOWERED;
    }

    private boolean isBinaryVisualInputHigh() {
        if (internalRedstoneLinkActive)
            return internalRedstoneLinkReceivedSignal > 0;

        return binaryVisualInputHigh;
    }

    private boolean shouldBlinkForMissingBinaryReceivePath() {
        return TwisterMillConfig.isServoTwisterBinaryInputEnabled()
                && isBinaryInputTooltipActiveMode()
                && !isBinaryReceivePathReady();
    }

    private boolean isBinaryReceivePathReady() {
        return isInternalRedstoneLinkReceiverActive() || hasExternalRedstoneLinkReceiverOnBinaryInputSide();
    }

    private boolean hasExternalRedstoneLinkReceiverOnBinaryInputSide() {
        if (level == null)
            return false;

        BlockState linkState = level.getBlockState(worldPosition.relative(getOppositeTopInputSide()));
        return linkState.getBlock() instanceof RedstoneLinkBlock
                && linkState.hasProperty(RedstoneLinkBlock.RECEIVER)
                && linkState.getValue(RedstoneLinkBlock.RECEIVER);
    }

    private boolean isBinaryWaitBlinkOn() {
        if (level == null)
            return false;

        long phase = Math.floorMod(level.getGameTime(), (long) BINARY_WAIT_BLINK_PERIOD_TICKS);
        return phase >= BINARY_WAIT_BLINK_OFF_TICKS;
    }

    private void updateVisualPowerState() {
        if (level == null || level.isClientSide)
            return;

        BlockState state = level.getBlockState(worldPosition);
        if (!state.hasProperty(ServoTwisterBlock.POWER_VISUAL))
            return;

        ServoTwisterBlock.PowerVisualState next = computePowerVisualState();
        if (state.getValue(ServoTwisterBlock.POWER_VISUAL) != next) {
            level.setBlock(worldPosition, state.setValue(ServoTwisterBlock.POWER_VISUAL, next), 3);
        }
    }

    private ValueBoxTransform getMaxAngleSlot() {
        return new DirectionalExtenderScrollOptionSlot((state, direction) -> {
            Direction facing = state.getValue(BearingBlock.FACING);

            if (facing == Direction.DOWN && (direction == Direction.WEST || direction == Direction.EAST)) {
                return false;
            }

            if (facing.getAxis().isHorizontal()) {
                return direction == Direction.UP || direction == Direction.DOWN;
            }


            if (facing == Direction.UP && (direction == Direction.WEST || direction == Direction.EAST)) {
                return false;
            }

            Direction.Axis sideAxis = direction.getAxis();
            Direction.Axis bearingAxis = facing.getAxis();
            return bearingAxis != sideAxis;
        });
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        maxAngleBehaviour = new ScrollOptionBehaviour<>(
                MaxAngleOption.class,
                Component.translatable("twistermill.max_angle"),
                this,
                getMaxAngleSlot()
        );
        maxAngleBehaviour.withCallback($ -> onMaxAngleChanged());
        behaviours.add(maxAngleBehaviour);

        internalRedstoneLink = LinkBehaviour.receiver(
                this,
                InternalServoRedstoneLinkSlots.makeSlots(false),
                this::setInternalRedstoneLinkSignal
        );
        behaviours.add(internalRedstoneLink);

        secondaryInternalRedstoneLink = new SecondaryServoRedstoneLinkBehaviour(
                this,
                InternalServoRedstoneLinkSlots.makeSlots(false, true),
                this::setSecondaryInternalRedstoneLinkSignal
        );
        behaviours.add(secondaryInternalRedstoneLink);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

        prevAngle = angle;
        secondPrevAngle = secondAngle;

        if (level.isClientSide)
            return;

        physicalBearingAngleSamplePublishedThisTick = false;

        if (refreshSpeedZeroMovementConfigFromServer()) {
            setChanged();
            if (canSendData()) sendData();
        }

        long time = level.getGameTime();
        refreshLiveContraptionBlockCountIfNeeded(time);
        notifyBoundWindRotoRuntimeIfNeeded(time);

        if (needsStateRefresh) {
            refreshRuntimeStateFromWorld();
            needsStateRefresh = false;
        }

        boolean pitchClearanceReadyForRefresh = ensurePitchClearanceBeforeRefresh();

        if (level instanceof ServerLevel serverLevel) {
            syncPropellerSlotRejoinIfNeeded(serverLevel);
        }

        boolean sableConstraintRefreshPending = false;
        if (sableBackend.isActive() && !isConfiguredFreeBearingRotationMode()) {
            if (!pitchClearanceReadyForRefresh) {
                sableConstraintRefreshPending = true;
            } else {
                boolean refreshSucceeded;
                boolean refreshRetryable;
                if (level instanceof ServerLevel serverLevel && requiresTwoAxisLoadedPoseRecovery()) {
                    SableInteractiveContraptionBackend.TwoAxisRecoveryRefreshResult recoveryResult =
                            sableBackend.refreshTwoAxisFromLoadedPoseDetailed(
                                    serverLevel,
                                    worldPosition,
                                    getFacingDirection(),
                                    getServoStiffnessPerInertia(serverLevel),
                                    getServoDampingPerInertia(serverLevel),
                                    TwisterMillConfig.getServoMinEffectiveInertia(),
                                    ROTATION_PROFILE_NEUTRAL_PHYSICAL_TOLERANCE_DEGREES
                            );
                    refreshSucceeded = recoveryResult.readyForControl();
                    refreshRetryable = recoveryResult.retryable();
                    if (refreshSucceeded && recoveryResult.initializedAngles() != null) {
                        applyRecoveredTwoAxisPose(recoveryResult.initializedAngles());
                    }
                } else {
                    SableInteractiveContraptionBackend.RefreshResult refreshResult =
                            level instanceof ServerLevel serverLevel
                                    ? sableBackend.refreshDetailed(serverLevel, worldPosition, getFacingDirection(),
                                    activeRotationProfile)
                                    : SableInteractiveContraptionBackend.RefreshResult.failed(
                                    SableInteractiveContraptionBackend.RefreshFailureReason.CONSTRAINT_ATTACH_FAILED);
                    refreshSucceeded = refreshResult.success();
                    refreshRetryable = refreshResult.failureReason()
                            == SableInteractiveContraptionBackend.RefreshFailureReason.CONSTRAINT_REATTACH_PENDING;
                }
                if (refreshSucceeded) {
                    logSableLifecycleDiagnostics("refresh-success");
                } else if (refreshRetryable) {
                    sableConstraintRefreshPending = true;
                    logSableLifecycleDiagnostics("refresh-reattach-pending");
                } else {
                    logSableLifecycleDiagnostics("refresh-failure");
                    running = false;
                    pendingDisassembleAfterZero = false;
                    pendingDisassembleZeroHoldTicks = 0;
                    clearMode3DisassemblyReturnState();
                    mode3ExitReturnActive = false;
                    pendingPropellerSlotRejoinSyncTicks = 0;
                    clearPropellerSlotPreviewSync();
                    sableBackend.clearState();
                    setChanged();
                    if (canSendData()) sendData();
                }
            }
        }

        if (level instanceof ServerLevel serverLevel && updatePropellerSlotMotion(serverLevel)) {
            setChanged();
            if (canSendData()) sendData();
        }

        if (level instanceof ServerLevel serverLevel && isConfiguredFreeBearingRotationMode()) {
            tickFreeBearingLifecycle(serverLevel);
        }

        if (assembleNextTick) {
            assembleNextTick = false;
            if (running) {
                beginPendingDisassemblyReturn();
                mode3ExitReturnActive = false;
            } else {
                assemble();
            }
        }

        SableInteractiveContraptionBackend.RotationProfile desiredRotationProfile =
                activeRotationProfile;
        boolean deferLaterGuiControl = laterGuiAssemblyCompletedThisTick;
        laterGuiAssemblyCompletedThisTick = false;
        if (!deferLaterGuiControl) {
            int previousModeSignal = lastOppositeTopSignal;
            updateControlSignalsFromInputs(true);
            updateVisualPowerState();
            boolean safeBinaryDisassemblySignalChangedThisTick = isSafeBinaryDisassemblyReturnActive()
                    && previousModeSignal != lastOppositeTopSignal;
            boolean directInputPriority = hasDirectInputPriority(lastWestSignal, lastEastSignal);
            desiredRotationProfile = getDesiredRotationProfile();
            boolean pitchClearanceMotionReady =
                    ensurePitchClearanceBeforeMotion(desiredRotationProfile, sableConstraintRefreshPending);
            boolean mode4HardHingeRequired = isMode4Code12HardHingeRequired(directInputPriority);
            boolean mode4HardHingeReady = reconcileMode4Code12HardHinge(
                    desiredRotationProfile,
                    sableConstraintRefreshPending,
                    pitchClearanceMotionReady,
                    mode4HardHingeRequired
            );
            boolean freeBearingMode = isFreeBearingOperatingMode();
            boolean worldLockedMode = isWorldLockedModeSignal(lastOppositeTopSignal);
            boolean oscillationMode = isOscillationModeSignal(lastOppositeTopSignal);
        if (oscillationMode && previousModeSignal != lastOppositeTopSignal) {
            mode6OscillationPhase = 0.0D;
        }
        boolean unboundedExitToPositioningMode = isUnboundedModeSignal(previousModeSignal)
                && isBoundedPositioningModeSignal(lastOppositeTopSignal)
                && desiredRotationProfile == activeRotationProfile
                && running
                && !pendingDisassembleAfterZero;

        if (unboundedExitToPositioningMode && sableBackend.isActive()) {
            Float startAngle = computeCurrentSableMotorAngleDegrees();
            if (startAngle != null) {
                angle = startAngle;
                prevAngle = startAngle;
                mode3ExitReturnActive = true;
                setChanged();
                if (canSendData()) sendData();
            } else {
                mode3ExitReturnActive = false;
            }
        }

        if (mode3ExitReturnActive) {
            if (!running
                    || pendingDisassembleAfterZero
                    || isUnboundedModeSignal(lastOppositeTopSignal)
                    || !isBoundedPositioningModeSignal(lastOppositeTopSignal)) {
                mode3ExitReturnActive = false;
            }
        }

        if (running && !isConfiguredFreeBearingRotationMode()) {
            boolean skipFinalSableApplyRotation = false;

            if (!pitchClearanceMotionReady) {
                if (isSafeBinaryDisassemblyReturnActive()) {
                    resetMode3DisassemblyConfirmation();
                }
                if (pendingExtendedBinaryDisassemblyReturn) {
                    rotationProfileNeutralStableTicks = 0;
                }
                skipFinalSableApplyRotation = true;
            } else if (pendingDisassembleAfterZero) {
                if (mode4HardHingeRequired && !mode4HardHingeReady) {
                    pendingDisassembleZeroHoldTicks = 0;
                    resetMode3DisassemblyConfirmation();
                    resetLaterGuiDisassemblyRuntimeState();
                } else if (isSafeBinaryDisassemblyReturnActive()
                        && level instanceof ServerLevel serverLevel) {
                    if (activeRotationProfile
                            == SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS) {
                        tickMode3DisassemblyReturn(
                                serverLevel,
                                !safeBinaryDisassemblySignalChangedThisTick
                        );
                    } else {
                        resetMode3DisassemblyRuntimeState();
                        if (sableConstraintRefreshPending) {
                            rotationProfileNeutralStableTicks = 0;
                        } else if (pendingExtendedBinaryDisassemblyReturn) {
                            tickRotationProfileTransition(
                                    SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS,
                                    directInputPriority,
                                    true
                            );
                        }
                    }
                } else if (isLaterGuiDisassemblyReturnActive() && sableConstraintRefreshPending) {
                    resetLaterGuiDisassemblyRuntimeState();
                } else {
                    float requestedStep = Math.max(
                            getDegreesPerTickForSignal(lastWestSignal),
                            MIN_DISASSEMBLE_DEGREES_PER_TICK
                    );
                    targetAngle = 0.0F;
                    secondTargetAngle = 0.0F;

                    if (Math.max(Math.abs(angle), Math.abs(secondAngle)) <= DISASSEMBLE_ZERO_SNAP_DEGREES) {
                        boolean changed = Math.abs(angle) > ANGLE_EPSILON
                                || Math.abs(secondAngle) > ANGLE_EPSILON;
                        angle = 0.0F;
                        secondAngle = 0.0F;
                        applyRotation();
                        boolean laterGuiDisassemblyReady = isLaterGuiDisassemblyReturnActive()
                                && level instanceof ServerLevel serverLevel
                                && tickLaterGuiPhysicalNeutralConfirmation(serverLevel, false);
                        if (!isLaterGuiDisassemblyReturnActive()) {
                            boolean physicallyNeutral = !isTiltRotationProfile(activeRotationProfile)
                                    || isActiveRotationProfilePhysicallyNeutral();
                            if (physicallyNeutral) {
                                pendingDisassembleZeroHoldTicks++;
                            } else {
                                pendingDisassembleZeroHoldTicks = 0;
                            }
                        }

                        if (changed) {
                            setChanged();
                            if (canSendData()) sendData();
                        }

                        if (laterGuiDisassemblyReady
                                || (!isLaterGuiDisassemblyReturnActive()
                                && pendingDisassembleZeroHoldTicks >= DISASSEMBLE_ZERO_HOLD_TICKS)) {
                            pendingDisassembleAfterZero = false;
                            pendingDisassembleZeroHoldTicks = 0;
                            clearMode3DisassemblyReturnState();
                            clearLaterGuiDisassemblyReturnState();
                            disassemble();
                        }
                    } else {
                        pendingDisassembleZeroHoldTicks = 0;
                        if (isLaterGuiDisassemblyReturnActive()) {
                            resetLaterGuiDisassemblyRuntimeState();
                        }
                        float maximumAngle = Math.max(Math.abs(angle), Math.abs(secondAngle));
                        float multiplier = getDisassemblyReturnSpeedMultiplierForCurrentGuiMode();
                        float step = computeDisassembleReturnStepDegrees(
                                maximumAngle,
                                requestedStep,
                                multiplier
                        );
                        if (approachTwoAxisTargets(0.0F, 0.0F, step)) {
                            applyRotation();
                            setChanged();
                            if (canSendData()) sendData();
                        }
                    }
                }
                skipFinalSableApplyRotation = true;
            } else if (sableConstraintRefreshPending) {
                skipFinalSableApplyRotation = true;
            } else if (rotationProfileTransitionActive || desiredRotationProfile != activeRotationProfile) {
                tickRotationProfileTransition(desiredRotationProfile, directInputPriority);
                skipFinalSableApplyRotation = true;
            } else if (!freeBearingMode && isTwoAxisTiltModeSignal(lastOppositeTopSignal)) {
                float speed = getDegreesPerTickForSignal(
                        directInputPriority ? lastWestSignal : binarySpeedSignal);
                if (approachTwoAxisTargets(targetAngle, secondTargetAngle, speed)) {
                    applyRotation();
                    setChanged();
                    if (canSendData()) sendData();
                }
                skipFinalSableApplyRotation = true;
            } else if (!freeBearingMode && mode3ExitReturnActive) {
                if (mode4HardHingeRequired && !mode4HardHingeReady) {
                    skipFinalSableApplyRotation = true;
                } else {
                    float previousAngle = angle;
                    float newAngle = approachAngle(angle, targetAngle, MODE3_EXIT_RETURN_DEGREES_PER_TICK);
                    boolean changed = Math.abs(newAngle - previousAngle) > ANGLE_EPSILON;

                    if (changed) {
                        angle = newAngle;
                        applyRotation();
                        setChanged();
                        if (canSendData()) sendData();
                    }

                    if (Math.abs(targetAngle - newAngle) <= ANGLE_EPSILON) {
                        angle = targetAngle;
                        mode3ExitReturnActive = false;
                    }

                    skipFinalSableApplyRotation = true;
                }
            } else if (!freeBearingMode) {
                if (mode4HardHingeRequired && !mode4HardHingeReady) {
                    skipFinalSableApplyRotation = true;
                } else if (worldLockedMode) {
                    if (applyWorldLockedMotorNeedsFallback(lastOppositeTopSignal)) {
                        applyFreeBearingNeutralMotor();
                    }
                    skipFinalSableApplyRotation = true;
                } else if (oscillationMode) {
                    applyMode6Oscillation(directInputPriority);
                    skipFinalSableApplyRotation = true;
                } else if (directInputPriority) {
                    if (lastOppositeTopSignal == 1) {
                        angle -= SOUTH_CONTINUOUS_DEGREES_PER_TICK;
                        applyRotation();
                        setChanged();
                        if (canSendData()) sendData();
                    } else if (lastOppositeTopSignal == 2) {
                        angle += SOUTH_CONTINUOUS_DEGREES_PER_TICK;
                        applyRotation();
                        setChanged();
                        if (canSendData()) sendData();
                    } else if (lastOppositeTopSignal < 6 || isExtendedTargetModeSignal(lastOppositeTopSignal)) {
                        float step = getDegreesPerTickForSignal(lastWestSignal);
                        float newAngle = approachAngle(angle, targetAngle, step);

                        if (Math.abs(newAngle - angle) > NORMAL_MOTION_EPSILON) {
                            angle = newAngle;
                            applyRotation();
                            setChanged();
                            if (canSendData()) sendData();
                        }
                    }
                } else {
                    float step = getDegreesPerTickForSignal(binarySpeedSignal);
                    if (lastOppositeTopSignal == 1 || lastOppositeTopSignal == 2 || lastOppositeTopSignal >= 3) {
                        float newAngle = approachAngle(angle, targetAngle, step);
                        if (Math.abs(newAngle - angle) > NORMAL_MOTION_EPSILON) {
                            angle = newAngle;
                            applyRotation();
                            setChanged();
                            if (canSendData()) sendData();
                        }
                    }
                }
            }

            if (sableBackend.isActive() && !skipFinalSableApplyRotation) {
                if (freeBearingMode) {
                    applyFreeBearingNeutralMotor();
                } else {
                    applyRotation();
                }
            }

            }
        }

        if (level instanceof ServerLevel serverLevel && updatePropellerSlotMotion(serverLevel)) {
            setChanged();
            if (canSendData()) sendData();
        }

        if (level instanceof ServerLevel serverLevel) {
            tickPropellerSlotPreviewSync(serverLevel);
        }

        reconcilePitchClearanceRelease(desiredRotationProfile, sableConstraintRefreshPending);

        boolean visualRunning =
                running && (manualEnabled || lastEastSignal > 0 || lastOppositeTopSignal > 0
                || Math.abs(angle) > ANGLE_EPSILON || Math.abs(secondAngle) > ANGLE_EPSILON);

        updateVisualRunning(visualRunning);

        if (level instanceof ServerLevel serverLevel) {
            updateMode7MeasuredRpmForClient(serverLevel);
            updatePhysicalBearingMeasuredAngleForClient(serverLevel);
        }
    }

    private boolean isMode4Code12HardHingeRequired(boolean directInputPriority) {
        if (level == null
                || level.isClientSide
                || getClass() != ServoTwisterBlockEntity.class
                || !running
                || assembleNextTick
                || !sableBackend.isActive()
                || getCurrentMaxAngleOption() != MaxAngleOption.DEG_60_LINK
                || !isInternalRedstoneLinkMode()
                || !TwisterMillConfig.isServoTwisterBinaryInputEnabled()
                || !hasValidBinaryFrame
                || (binaryModeSignal != 1 && binaryModeSignal != 2)) {
            return false;
        }

        boolean steadyState = !directInputPriority
                && !getOppositeTopInputLocked()
                && !pendingDisassembleAfterZero
                && !pendingMode3DisassemblyReturn
                && !pendingExtendedBinaryDisassemblyReturn
                && lastOppositeTopSignal == binaryModeSignal;
        boolean disassemblyReturnContinuation = pendingDisassembleAfterZero
                && pendingExtendedBinaryDisassemblyReturn;
        return steadyState || disassemblyReturnContinuation;
    }

    private boolean reconcileMode4Code12HardHinge(
            SableInteractiveContraptionBackend.RotationProfile desiredRotationProfile,
            boolean sableConstraintRefreshPending,
            boolean pitchClearanceMotionReady,
            boolean required
    ) {
        if (!required) {
            sableBackend.disableFacingAxisHardHinge("control-not-eligible");
            return true;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (activeRotationProfile != SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS
                || desiredRotationProfile != SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS) {
            sableBackend.disableFacingAxisHardHinge("rotation-profile-not-facing-axis");
            return false;
        }
        if (sableConstraintRefreshPending
                || rotationProfileTransitionActive
                || !pitchClearanceMotionReady) {
            return false;
        }

        SableInteractiveContraptionBackend.HardHingeEnsureResult result =
                sableBackend.ensureFacingAxisHardHinge(
                        serverLevel,
                        worldPosition,
                        getFacingDirection()
                );
        if (result.status() == SableInteractiveContraptionBackend.HardHingeEnsureStatus.INVALID) {
            handleTerminalMode4HardHingeFailure(result.reason());
            return false;
        }
        if (!result.readyForMotor() && TwisterMillDiagnostics.isServoLoggingEnabled()) {
            LOGGER.info(
                    "[ServoHardHingeDiag] event=control-paused pos={} gameTime={} code={} reason={}",
                    worldPosition,
                    serverLevel.getGameTime(),
                    binaryModeSignal,
                    result.reason()
            );
        }
        return result.readyForMotor();
    }

    private void handleTerminalMode4HardHingeFailure(String reason) {
        LOGGER.warn(
                "Stopping Servo Mode 4 Code {}/{} control at {} because the structural hard hinge is invalid: {}",
                binaryModeSignal,
                lastOppositeTopSignal,
                worldPosition,
                reason
        );
        running = false;
        pendingDisassembleAfterZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        clearMode3DisassemblyReturnState();
        clearLaterGuiDisassemblyReturnState();
        mode3ExitReturnActive = false;
        pendingPropellerSlotRejoinSyncTicks = 0;
        clearPropellerSlotPreviewSync();
        sableBackend.clearState();
        setChanged();
        if (canSendData()) {
            sendData();
        }
    }

    private SableInteractiveContraptionBackend.RotationProfile getDesiredRotationProfile() {
        if (isConfiguredFreeBearingRotationMode()) {
            return SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
        }
        if (isTwoAxisTiltModeSignal(lastOppositeTopSignal)) {
            return SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT;
        }
        return SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
    }

    private static boolean isTiltRotationProfile(
            SableInteractiveContraptionBackend.RotationProfile profile
    ) {
        return profile == SableInteractiveContraptionBackend.RotationProfile.UP_PITCH_X
                || profile == SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT;
    }

    private boolean ensurePitchClearanceBeforeRefresh() {
        return ensurePitchClearanceBeforeMotion(getDesiredRotationProfile(), false);
    }

    private boolean ensurePitchClearanceBeforeMotion(
            SableInteractiveContraptionBackend.RotationProfile desiredProfile,
            boolean sableConstraintRefreshPending
    ) {
        if (!requiresPitchClearance(desiredProfile, sableConstraintRefreshPending)) {
            return true;
        }

        pitchClearanceNeutralStableTicks = 0;
        return setPitchClearanceState(true);
    }

    private boolean requiresPitchClearance(
            SableInteractiveContraptionBackend.RotationProfile desiredProfile,
            boolean sableConstraintRefreshPending
    ) {
        boolean assembledOrRememberedActive = running || sableBackend.isActive();
        boolean activePitchProfile = assembledOrRememberedActive
                && isTiltRotationProfile(activeRotationProfile);
        boolean transitionTouchesPitch = assembledOrRememberedActive
                && rotationProfileTransitionActive
                && (isTiltRotationProfile(activeRotationProfile)
                || isTiltRotationProfile(rotationProfileTransitionTarget));
        boolean disassemblyTouchesPitch = pendingDisassembleAfterZero
                && (activePitchProfile
                || isTiltRotationProfile(desiredProfile)
                || isTiltRotationProfile(rotationProfileTransitionTarget));
        boolean pendingRefreshTouchesPitch = sableConstraintRefreshPending
                && (activePitchProfile
                || isTiltRotationProfile(desiredProfile)
                || isTiltRotationProfile(rotationProfileTransitionTarget));

        return isTiltRotationProfile(desiredProfile)
                || activePitchProfile
                || transitionTouchesPitch
                || disassemblyTouchesPitch
                || pendingRefreshTouchesPitch;
    }

    private boolean setPitchClearanceState(boolean enabled) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return !enabled;
        }

        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof ServoTwisterBlock)
                || !state.hasProperty(ServoTwisterBlock.PITCH_CLEARANCE)) {
            return !enabled;
        }

        //noinspection UnnecessaryLocalVariable
        boolean targetValue = enabled;
        if (state.getValue(ServoTwisterBlock.PITCH_CLEARANCE) == targetValue) {
            return true;
        }

        return serverLevel.setBlock(
                worldPosition,
                state.setValue(ServoTwisterBlock.PITCH_CLEARANCE, targetValue),
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
        );
    }

    private void reconcilePitchClearanceRelease(
            SableInteractiveContraptionBackend.RotationProfile desiredProfile,
            boolean sableConstraintRefreshPending
    ) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (requiresPitchClearance(desiredProfile, sableConstraintRefreshPending)) {
            pitchClearanceNeutralStableTicks = 0;
            return;
        }

        BlockState state = serverLevel.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof ServoTwisterBlock)
                || !state.hasProperty(ServoTwisterBlock.PITCH_CLEARANCE)
                || !state.getValue(ServoTwisterBlock.PITCH_CLEARANCE)) {
            pitchClearanceNeutralStableTicks = 0;
            return;
        }

        if (!sableBackend.isActive()) {
            pitchClearanceNeutralStableTicks = 0;
            setPitchClearanceState(false);
            return;
        }

        if (activeRotationProfile != SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS
                || rotationProfileTransitionActive
                || pendingDisassembleAfterZero
                || sableConstraintRefreshPending) {
            pitchClearanceNeutralStableTicks = 0;
            return;
        }

        Float physicalPitchAngle = measurePitchClearanceAngle();
        boolean physicallyNeutral = physicalPitchAngle != null
                && Float.isFinite(physicalPitchAngle)
                && Math.abs(physicalPitchAngle)
                <= ROTATION_PROFILE_NEUTRAL_PHYSICAL_TOLERANCE_DEGREES;
        if (!physicallyNeutral) {
            pitchClearanceNeutralStableTicks = 0;
            return;
        }

        pitchClearanceNeutralStableTicks++;
        if (pitchClearanceNeutralStableTicks < ROTATION_PROFILE_NEUTRAL_STABLE_TICKS) {
            return;
        }

        pitchClearanceNeutralStableTicks = 0;
        setPitchClearanceState(false);
    }

    @Nullable
    private Float measurePitchClearanceAngle() {
        if (!(level instanceof ServerLevel serverLevel) || !sableBackend.isActive()) {
            return null;
        }
        SableInteractiveContraptionBackend.TwoAxisAngles measured =
                sableBackend.measureCurrentTwoAxisAnglesDegrees(
                        serverLevel,
                        worldPosition,
                        getFacingDirection()
                );
        if (measured == null) {
            return null;
        }
        return Math.max(Math.abs(measured.totalSwingDegrees()), Math.abs(measured.twistDegrees()));
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean preparePitchClearanceForDisassembly() {
        if (!(level instanceof ServerLevel)) {
            return true;
        }

        SableInteractiveContraptionBackend.RotationProfile desiredProfile =
                getDesiredRotationProfile();
        if (!requiresPitchClearance(desiredProfile, false)) {
            return true;
        }

        pitchClearanceNeutralStableTicks = 0;
        return setPitchClearanceState(true);
    }

    private void tickRotationProfileTransition(
            SableInteractiveContraptionBackend.RotationProfile desiredProfile,
            boolean directInputPriority
    ) {
        tickRotationProfileTransition(desiredProfile, directInputPriority, false);
    }

    private void tickRotationProfileTransition(
            SableInteractiveContraptionBackend.RotationProfile desiredProfile,
            boolean directInputPriority,
            boolean disassemblyReturn
    ) {
        if (!(level instanceof ServerLevel serverLevel) || !sableBackend.isActive()) {
            rotationProfileTransitionActive = false;
            rotationProfileNeutralStableTicks = 0;
            return;
        }

        if (disassemblyReturn
                && activeRotationProfile
                == SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT) {
            publishPhysicalBearingMeasuredAngleForClient(
                    sableBackend.measureBearingAxisRelativeAngleDegrees(
                            serverLevel,
                            worldPosition,
                            getFacingDirection()
                    )
            );
        }

        if (desiredProfile == activeRotationProfile) {
            rotationProfileTransitionActive = false;
            rotationProfileTransitionTarget = activeRotationProfile;
            rotationProfileNeutralStableTicks = 0;
            return;
        }

        if (!rotationProfileTransitionActive || rotationProfileTransitionTarget != desiredProfile) {
            rotationProfileTransitionActive = true;
            rotationProfileTransitionTarget = desiredProfile;
            rotationProfileNeutralStableTicks = 0;
            if (activeRotationProfile == SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT) {
                SableInteractiveContraptionBackend.TwoAxisAngles measured =
                        measureActiveTwoAxisAngles();
                if (measured != null) {
                    angle = measured.axis1Degrees();
                    prevAngle = angle;
                    secondAngle = measured.axis2Degrees();
                    secondPrevAngle = secondAngle;
                }
            } else {
                Float physicalAngle = measureActiveRotationProfileAngle();
                if (physicalAngle != null && Float.isFinite(physicalAngle)) {
                    angle = physicalAngle;
                    prevAngle = physicalAngle;
                }
            }
            targetAngle = 0.0F;
            secondTargetAngle = 0.0F;
            setChanged();
            if (canSendData()) sendData();
        }

        float speed;
        if (disassemblyReturn) {
            float maximumAngle = Math.max(Math.abs(angle), Math.abs(secondAngle));
            float requestedStep = Math.max(
                    getDegreesPerTickForSignal(lastWestSignal),
                    MIN_DISASSEMBLE_DEGREES_PER_TICK
            );
            speed = computeDisassembleReturnStepDegrees(
                    maximumAngle,
                    requestedStep,
                    getDisassemblyReturnSpeedMultiplierForCurrentGuiMode()
            );
        } else {
            speed = getDegreesPerTickForSignal(
                    directInputPriority ? lastWestSignal : binarySpeedSignal);
        }
        if (speed > 0.0F) {
            if (approachTwoAxisTargets(0.0F, 0.0F, speed)) {
                setChanged();
                if (canSendData()) sendData();
            }
        }

        targetAngle = 0.0F;
        secondTargetAngle = 0.0F;
        applyRotation();
        if (!running || !sableBackend.isActive()) {
            rotationProfileNeutralStableTicks = 0;
            return;
        }

        boolean scalarNeutral = Float.isFinite(angle)
                && Float.isFinite(secondAngle)
                && Math.abs(angle) <= ROTATION_PROFILE_NEUTRAL_SCALAR_TOLERANCE_DEGREES
                && Math.abs(secondAngle) <= ROTATION_PROFILE_NEUTRAL_SCALAR_TOLERANCE_DEGREES;
        boolean disassemblyTwoAxisToFacingHandoff = disassemblyReturn
                && activeRotationProfile == SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT
                && desiredProfile == SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
        boolean physicalNeutral = isActiveRotationProfilePhysicallyNeutral(
                disassemblyTwoAxisToFacingHandoff
        );
        if (scalarNeutral && physicalNeutral) {
            rotationProfileNeutralStableTicks++;
        } else {
            rotationProfileNeutralStableTicks = 0;
        }

        if (rotationProfileNeutralStableTicks < ROTATION_PROFILE_NEUTRAL_STABLE_TICKS) {
            return;
        }

        Float disassemblyHandoffBearingAngle = null;
        if (disassemblyTwoAxisToFacingHandoff) {
            disassemblyHandoffBearingAngle = sableBackend.measureBearingAxisRelativeAngleDegrees(
                    serverLevel,
                    worldPosition,
                    getFacingDirection()
            );
            if (disassemblyHandoffBearingAngle == null
                    || !Float.isFinite(disassemblyHandoffBearingAngle)) {
                rotationProfileNeutralStableTicks = 0;
                return;
            }
            publishPhysicalBearingMeasuredAngleForClient(disassemblyHandoffBearingAngle);
        }

        double stiffnessPerInertia = getServoStiffnessPerInertia(serverLevel);
        double dampingPerInertia = getServoDampingPerInertia(serverLevel);
        double minEffectiveInertia = TwisterMillConfig.getServoMinEffectiveInertia();
        boolean switched;
        if (disassemblyHandoffBearingAngle != null) {
            switched = sableBackend.switchTwoAxisToFacingAxisForDisassemblyAtTiltNeutral(
                    serverLevel,
                    worldPosition,
                    getFacingDirection(),
                    disassemblyHandoffBearingAngle,
                    stiffnessPerInertia,
                    dampingPerInertia,
                    minEffectiveInertia
            );
        } else {
            switched = sableBackend.switchRotationProfileAtNeutral(
                    serverLevel,
                    worldPosition,
                    getFacingDirection(),
                    activeRotationProfile,
                    desiredProfile,
                    stiffnessPerInertia,
                    dampingPerInertia,
                    minEffectiveInertia
            );
        }
        rotationProfileNeutralStableTicks = 0;
        if (!switched) {
            return;
        }

        activeRotationProfile = desiredProfile;
        rotationProfileTagPresent = true;
        rotationProfileTransitionActive = false;
        rotationProfileTransitionTarget = desiredProfile;
        angle = 0.0F;
        prevAngle = 0.0F;
        targetAngle = 0.0F;
        secondAngle = 0.0F;
        secondPrevAngle = 0.0F;
        secondTargetAngle = 0.0F;
        setChanged();
        if (canSendData()) sendData();
    }

    @Nullable
    private Float measureActiveRotationProfileAngle() {
        if (!(level instanceof ServerLevel serverLevel) || !running || !sableBackend.isActive()) {
            return null;
        }
        return sableBackend.measureCurrentMotorAngleDegrees(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                activeRotationProfile
        );
    }

    @Nullable
    private SableInteractiveContraptionBackend.TwoAxisAngles measureActiveTwoAxisAngles() {
        if (!(level instanceof ServerLevel serverLevel) || !running || !sableBackend.isActive()) {
            return null;
        }
        return sableBackend.measureCurrentTwoAxisAnglesDegrees(
                serverLevel,
                worldPosition,
                getFacingDirection()
        );
    }

    private boolean isActiveRotationProfilePhysicallyNeutral() {
        return isActiveRotationProfilePhysicallyNeutral(false);
    }

    private boolean isActiveRotationProfilePhysicallyNeutral(boolean allowBearingAxisTwist) {
        if (activeRotationProfile == SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT) {
            SableInteractiveContraptionBackend.TwoAxisAngles measured = measureActiveTwoAxisAngles();
            return measured != null
                    && Math.abs(measured.axis1Degrees())
                    <= ROTATION_PROFILE_NEUTRAL_PHYSICAL_TOLERANCE_DEGREES
                    && Math.abs(measured.axis2Degrees())
                    <= ROTATION_PROFILE_NEUTRAL_PHYSICAL_TOLERANCE_DEGREES
                    && Math.abs(measured.totalSwingDegrees())
                    <= ROTATION_PROFILE_NEUTRAL_PHYSICAL_TOLERANCE_DEGREES
                    && (allowBearingAxisTwist
                    || Math.abs(measured.twistDegrees())
                            <= ROTATION_PROFILE_NEUTRAL_PHYSICAL_TOLERANCE_DEGREES);
        }
        Float physicalAngle = measureActiveRotationProfileAngle();
        return physicalAngle != null
                && Float.isFinite(physicalAngle)
                && Math.abs(physicalAngle) <= ROTATION_PROFILE_NEUTRAL_PHYSICAL_TOLERANCE_DEGREES;
    }

    private boolean updatePropellerSlotMotion(ServerLevel serverLevel) {
        if (!propellerSlotManager.hasAnySlot()) {
            return false;
        }

        if (isConfiguredFreeBearingRotationMode()
                && freeBearingLifecyclePhase != FreeBearingLifecyclePhase.UNASSEMBLED) {
            UUID expectedTopId = sableBackend.getActiveSubLevelId();
            boolean allowTopRebind = freeBearingLifecyclePhase == FreeBearingLifecyclePhase.ASSEMBLING_REGULATED;
            propellerSlotManager.setFreeBearingTopFollowPolicy(true, expectedTopId, allowTopRebind);
            if (expectedTopId == null) {
                return false;
            }
            boolean hadActiveTopFollow = propellerSlotManager.hasActiveTopFollowForPreview();
            boolean changed = propellerSlotManager.updateFreeBearingSlotMotionFailClosed(
                    serverLevel,
                    expectedTopId,
                    worldPosition.relative(getFacingDirection()),
                    getFacingDirection(),
                    allowTopRebind
            );
            if (!hadActiveTopFollow && propellerSlotManager.hasActiveTopFollowForPreview()) {
                requestPropellerSlotPreviewSync();
            }
            return changed;
        }

        propellerSlotManager.setFreeBearingTopFollowPolicy(false, null, false);
        boolean hadActiveTopFollow = propellerSlotManager.hasActiveTopFollowForPreview();
        ServerSubLevel topSubLevel = resolveActiveSableSubLevel(serverLevel);
        Direction facing = getFacingDirection();
        BlockPos assemblyAnchorPos = worldPosition.relative(facing);
        boolean changed = propellerSlotManager.updateSlotMotion(serverLevel, topSubLevel, assemblyAnchorPos, facing);
        if (!hadActiveTopFollow && propellerSlotManager.hasActiveTopFollowForPreview()) {
            requestPropellerSlotPreviewSync();
        }
        return changed;
    }

    private static boolean isSupportedPropellerSlotFacing(Direction facing) {
        return facing == Direction.UP || facing == Direction.DOWN || facing == Direction.NORTH || facing == Direction.SOUTH
                || facing == Direction.EAST || facing == Direction.WEST;
    }

    @Nullable
    private ServerSubLevel resolveActiveSableSubLevel(ServerLevel serverLevel) {
        UUID activeSubLevelId = sableBackend.getActiveSubLevelId();
        if (activeSubLevelId == null) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return null;
        }

        SubLevel subLevel = container.getSubLevel(activeSubLevelId);
        if (subLevel instanceof ServerSubLevel serverSubLevel && !serverSubLevel.isRemoved()) {
            return serverSubLevel;
        }
        return null;
    }

    void carryActiveSableTopWithParentDelta(
            ServerLevel serverLevel,
            PhysicsPipeline pipeline,
            Pose3dc oldParentPose,
            Pose3dc newParentPose
    ) {
        ServerSubLevel childSubLevel = resolveActiveSableSubLevel(serverLevel);
        if (childSubLevel == null || childSubLevel.isRemoved()) {
            return;
        }

        Pose3d childPose = childSubLevel.logicalPose();
        Vector3d childLocalPosition = oldParentPose.transformPositionInverse(childPose.position(), new Vector3d());
        Vector3d newChildPosition = newParentPose.transformPosition(childLocalPosition, new Vector3d());
        Quaterniond relativeOrientation = new Quaterniond(oldParentPose.orientation())
                .invert()
                .mul(childPose.orientation());
        Quaterniond newChildOrientation = new Quaterniond(newParentPose.orientation())
                .mul(relativeOrientation)
                .normalize();

        childPose.position().set(newChildPosition);
        childPose.orientation().set(newChildOrientation);
        childPose.scale().set(1.0D);

        pipeline.teleport(childSubLevel, childPose.position(), childPose.orientation());
        childPose.scale().set(1.0D);
        pipeline.wakeUp(childSubLevel);
        childSubLevel.updateLastPose();
    }

    private void syncPropellerSlotRejoinIfNeeded(ServerLevel serverLevel) {
        if (pendingPropellerSlotRejoinSyncTicks <= 0) {
            return;
        }

        if (syncActiveSableTopToPropellerSlotParentAfterRejoin(serverLevel)) {
            pendingPropellerSlotRejoinSyncTicks = 0;
            requestPropellerSlotPreviewSync();
            return;
        }

        pendingPropellerSlotRejoinSyncTicks--;
    }

    private boolean syncActiveSableTopToPropellerSlotParentAfterRejoin(ServerLevel serverLevel) {
        if (!sableBackend.isActive()) {
            return true;
        }

        ServerSubLevel childSubLevel = resolveActiveSableSubLevel(serverLevel);
        if (childSubLevel == null || childSubLevel.isRemoved()) {
            return false;
        }

        SubLevel containingSubLevel;
        try {
            containingSubLevel = Sable.HELPER.getContaining(serverLevel, worldPosition);
        } catch (RuntimeException ignored) {
            return false;
        }

        if (!(containingSubLevel instanceof ServerSubLevel parentSubLevel)
                || !ServoPropellerSlotManager.isPropellerSlotSubLevel(parentSubLevel)) {
            return true;
        }

        ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return false;
        }

        PhysicsPipeline pipeline = container.physicsSystem().getPipeline();
        Direction facing = getFacingDirection();
        BlockPos anchorPos = worldPosition.relative(facing);
        Pose3dc parentPose = parentSubLevel.logicalPose();
        Vector3d baseAnchorWorld = parentPose.transformPosition(JOMLConversion.atCenterOf(anchorPos), new Vector3d());
        Vector3d childAnchorLocal = SableInteractiveContraptionBackend.computeAnchorLocalCenter(childSubLevel, anchorPos);
        Pose3d childPose = childSubLevel.logicalPose();
        Vector3d rotationPoint = resolveRejoinRotationPoint(childSubLevel, childPose, childAnchorLocal);
        Quaterniond childOrientation = computePropellerSlotRejoinOrientation(parentPose, facing);
        Vector3d anchorOffsetFromRotationPoint = new Vector3d(childAnchorLocal).sub(rotationPoint);
        childOrientation.transform(anchorOffsetFromRotationPoint);
        Vector3d childPosition = new Vector3d(baseAnchorWorld).sub(anchorOffsetFromRotationPoint);

        childPose.position().set(childPosition);
        childPose.orientation().set(childOrientation);
        childPose.rotationPoint().set(rotationPoint);
        childPose.scale().set(1.0D);

        pipeline.teleport(childSubLevel, childPose.position(), childPose.orientation());
        childPose.rotationPoint().set(rotationPoint);
        childPose.scale().set(1.0D);
        pipeline.resetVelocity(childSubLevel);
        pipeline.wakeUp(childSubLevel);
        childSubLevel.updateLastPose();
        return true;
    }

    private Quaterniond computePropellerSlotRejoinOrientation(Pose3dc parentPose, Direction facing) {
        double angleDegrees = Float.isFinite(angle) ? angle : 0.0D;
        if (shouldInvertAngleForFacing()) {
            angleDegrees = -angleDegrees;
        }

        Quaterniond facingRotation = new Quaterniond(facing.getRotation());
        Quaterniond inverseFacingRotation = new Quaterniond(facingRotation).invert();
        return new Quaterniond(parentPose.orientation())
                .mul(facingRotation)
                .mul(new Quaterniond().rotateY(Math.toRadians(angleDegrees)))
                .mul(inverseFacingRotation)
                .normalize();
    }

    private static Vector3d resolveRejoinRotationPoint(ServerSubLevel childSubLevel, Pose3d childPose, Vector3d fallback) {
        Vector3dc centerOfMass = childSubLevel.getMassTracker().getCenterOfMass();
        if (isFinite(centerOfMass)) {
            return new Vector3d(centerOfMass);
        }
        if (isFinite(childPose.rotationPoint())) {
            return new Vector3d(childPose.rotationPoint());
        }
        return new Vector3d(fallback);
    }

    private static boolean isFinite(@Nullable Vector3dc vector) {
        return vector != null
                && Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    private void refreshLiveContraptionBlockCountIfNeeded(long time) {
        if (time < nextSableBlockCountRefreshAt) {
            return;
        }

        nextSableBlockCountRefreshAt = time + WIND_ROTO_RUNTIME_SYNC_TICKS;

        int measured = 0;
        if (sableBackend.isActive() && level instanceof ServerLevel serverLevel) {
            measured = Math.max(0, measureActiveChildShipBlockCount(serverLevel));
        }

        if (measured == assembledBlockCount) {
            return;
        }

        assembledBlockCount = measured;
        setChanged();
        if (canSendData()) {
            sendData();
        }
    }

    private int measureActiveChildShipBlockCount(ServerLevel serverLevel) {
        List<UUID> subLevelIds = new ArrayList<>();
        collectChildSubLevelIdsForWindRotoSailCount(subLevelIds);
        return WindRotoChildShipSailCounter.countBlocksRecursive(serverLevel, subLevelIds, state -> false).totalBlocks();
    }

    private void notifyBoundWindRotoRuntimeIfNeeded(long time) {
        if (!boundToWindRoto || boundWindRotoDimension == null || boundWindRotoPos == null) {
            return;
        }

        if (time < nextWindRotoRuntimeSyncAt) {
            return;
        }

        nextWindRotoRuntimeSyncAt = time + WIND_ROTO_RUNTIME_SYNC_TICKS;

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerLevel targetLevel = serverLevel.getServer().getLevel(boundWindRotoDimension);
        if (targetLevel == null) {
            return;
        }

        if (targetLevel.getBlockEntity(boundWindRotoPos) instanceof WindRotoBlockEntity windRoto) {
            windRoto.updateBoundServoRuntimeFromServo(
                    boundServoOriginalPos != null ? boundServoOriginalPos : worldPosition,
                    false,
                    getWindRotoBindingAngleDegrees(),
                    getBoundContraptionBlockCount()
            );
        }
    }

    private boolean getOppositeTopInputLocked() {
        return pendingDisassembleAfterZero;
    }

    @Override
    public void remove() {
        clearMode3DisassemblyReturnState();

        if (level != null && !level.isClientSide)
            notifyBoundWindRotoRemoved();

        if (level != null && !level.isClientSide)
            sableBackend.clearRuntimeForUnload();

        propellerSlotManager.clearRuntimeConstraints();
        propellerSlotManager.unregisterActiveManager();

        super.remove();
    }

    private void notifyBoundWindRotoRemoved() {
        if (!boundToWindRoto || boundWindRotoDimension == null || boundWindRotoPos == null) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ServerLevel targetLevel = serverLevel.getServer().getLevel(boundWindRotoDimension);
        if (targetLevel == null) {
            return;
        }

        if (targetLevel.getBlockEntity(boundWindRotoPos) instanceof WindRotoBlockEntity windRoto) {
            windRoto.removeBoundServoRuntimeFromServo(
                    boundServoOriginalPos != null ? boundServoOriginalPos : worldPosition,
                    false
            );
        }
    }

    @Override
    public void onChunkUnloaded() {
        resetMode3DisassemblyRuntimeState();
        if (pendingExtendedBinaryDisassemblyReturn) {
            rotationProfileNeutralStableTicks = 0;
        }
        sableBackend.clearRuntimeForUnload();
        propellerSlotManager.clearRuntimeConstraints();
        propellerSlotManager.unregisterActiveManager();
        super.onChunkUnloaded();
    }

    public void assemble() {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        if (running)
            return;
        if (isConfiguredFreeBearingRotationMode()
                && (freeBearingLifecyclePhase != FreeBearingLifecyclePhase.UNASSEMBLED
                || sableBackend.isActive()))
            return;

        if (tryAssembleSable(serverLevel))
            return;

        running = false;
        updateVisualRunning(false);
        setChanged();
        if (canSendData()) sendData();
    }

    private boolean tryAssembleSable(ServerLevel serverLevel) {
        if (!(serverLevel.getBlockState(worldPosition).getBlock() instanceof BearingBlock))
            return false;
        if (isConfiguredFreeBearingRotationMode()
                && !propellerSlotManager.hasCompleteDistinctSlotSet())
            return false;

        if (isInternalRedstoneLinkMode()) {
            updateControlSignalsFromInputs(false);
        }

        Direction facing = getFacingDirection();
        SableInteractiveContraptionBackend.RotationProfile assemblyRotationProfile =
                getDesiredRotationProfile();
        if (isTiltRotationProfile(assemblyRotationProfile)
                && !setPitchClearanceState(true)) {
            return false;
        }
        SableInteractiveContraptionBackend.AssemblyResult assembly = sableBackend.tryAssemble(
                serverLevel,
                worldPosition,
                facing,
                false,
                exception -> {
                    lastException = exception;
                    if (exception != null)
                        if (canSendData()) sendData();
                },
                RememberedSableShipMemory.enabledFor(getBlockState(), rememberedShipMemory),
                assemblyRotationProfile
        );
        if (assembly == null)
            return false;

        lastException = null;

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

        running = true;
        activeRotationProfile = assemblyRotationProfile;
        rotationProfileTagPresent = true;
        rotationProfileTransitionActive = false;
        rotationProfileTransitionTarget = activeRotationProfile;
        rotationProfileNeutralStableTicks = 0;
        pendingDisassembleAfterZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        clearMode3DisassemblyReturnState();
        pendingPropellerSlotRejoinSyncTicks = 0;
        angle = 0.0F;
        prevAngle = 0.0F;
        secondAngle = 0.0F;
        secondPrevAngle = 0.0F;
        secondTargetAngle = 0.0F;
        assembledBlockCount = assembly.blockCount();
        assembleNextTick = false;
        clearLaterGuiDisassemblyReturnState();
        if (isConfiguredFreeBearingRotationMode()) {
            UUID expectedTopId = sableBackend.getActiveSubLevelId();
            if (expectedTopId != null && applyFreeBearingRegulatedMotor(serverLevel, expectedTopId, 0.0F)) {
                commitFreeBearingPhase(FreeBearingLifecyclePhase.ASSEMBLING_REGULATED);
            } else {
                commitFreeBearingRecoveryPending();
            }
        } else {
            boolean mode4HardHingeRequired = isMode4Code12HardHingeRequired(false);
            boolean mode4HardHingeReady = reconcileMode4Code12HardHinge(
                    assemblyRotationProfile,
                    false,
                    true,
                    mode4HardHingeRequired
            );
            if (!mode4HardHingeRequired || mode4HardHingeReady) {
                applyRotation();
            }
        }
        laterGuiAssemblyCompletedThisTick = running
                && isLaterGuiAssemblyOption()
                && activeRotationProfile
                == SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
        requestPropellerSlotPreviewSync();
        if (canSendData()) sendData();

        return true;
    }

    public void disassemble() {
        if (!running && !sableBackend.isActive())
            return;

        if (!preparePitchClearanceForDisassembly())
            return;

        pendingPropellerSlotRejoinSyncTicks = 0;
        clearPropellerSlotPreviewSync();

        if (!(level instanceof ServerLevel serverLevel)) {
            running = false;
            pendingDisassembleAfterZero = false;
            pendingDisassembleZeroHoldTicks = 0;
            clearMode3DisassemblyReturnState();
            angle = 0.0F;
            prevAngle = 0.0F;
            secondAngle = 0.0F;
            secondPrevAngle = 0.0F;
            secondTargetAngle = 0.0F;
            assembleNextTick = false;
            assembledBlockCount = 0;
            sableBackend.clearClientFallback();
            if (isConfiguredFreeBearingRotationMode()) {
                freeBearingLifecyclePhase = FreeBearingLifecyclePhase.UNASSEMBLED;
                updateFreeBearingTopFollowPolicy();
            }
            updateVisualRunning(false);
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        pendingDisassembleAfterZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        clearMode3DisassemblyReturnState();
        angle = 0.0F;
        prevAngle = 0.0F;
        secondAngle = 0.0F;
        secondPrevAngle = 0.0F;
        secondTargetAngle = 0.0F;
        if (RememberedSableShipMemory.isRememberContraptionEnabledFor(getBlockState())) {
            rememberedShipMemory.replaceFromWorldPositions(
                    worldPosition,
                    getFacingDirection(),
                    sableBackend.snapshotRestoredBlockPositions(serverLevel, worldPosition)
            );
        }
        boolean restored = sableBackend.disassemble(serverLevel, worldPosition);
        if (restored)
            AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);

        running = false;
        assembleNextTick = false;
        assembledBlockCount = 0;
        if (isConfiguredFreeBearingRotationMode()) {
            freeBearingLifecyclePhase = restored
                    ? FreeBearingLifecyclePhase.UNASSEMBLED
                    : FreeBearingLifecyclePhase.RECOVERY_PENDING;
            updateFreeBearingTopFollowPolicy();
        }
        updateVisualRunning(false);
        setChanged();
        if (canSendData()) sendData();
    }

    private void applyRotation() {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        if (!sableBackend.isActive())
            return;

        double stiffnessPerInertia = getServoStiffnessPerInertia(serverLevel);
        double dampingPerInertia = getServoDampingPerInertia(serverLevel);

        boolean applied = activeRotationProfile
                == SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT
                ? sableBackend.applyTwoAxisMotors(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        angle,
                        secondAngle,
                        stiffnessPerInertia,
                        dampingPerInertia,
                        TwisterMillConfig.getServoMinEffectiveInertia()
                )
                : sableBackend.applyMotor(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        activeRotationProfile,
                        angle,
                        stiffnessPerInertia,
                        dampingPerInertia,
                        TwisterMillConfig.getServoMinEffectiveInertia()
                );
        if (!applied) {
            running = false;
            pendingDisassembleAfterZero = false;
            pendingDisassembleZeroHoldTicks = 0;
            clearMode3DisassemblyReturnState();
            sableBackend.clearState();
            setChanged();
            if (canSendData()) sendData();
        }
    }

    private void applyRecoveredTwoAxisPose(SableInteractiveContraptionBackend.TwoAxisAngles measured) {
        float recoveredAxis1 = Mth.clamp(
                measured.axis1Degrees(),
                -ServoTwoAxisRotationMath.MAX_AXIS_DEGREES,
                ServoTwoAxisRotationMath.MAX_AXIS_DEGREES
        );
        float recoveredAxis2 = Mth.clamp(
                measured.axis2Degrees(),
                -ServoTwoAxisRotationMath.MAX_AXIS_DEGREES,
                ServoTwoAxisRotationMath.MAX_AXIS_DEGREES
        );
        boolean changed = Float.floatToIntBits(angle) != Float.floatToIntBits(recoveredAxis1)
                || Float.floatToIntBits(prevAngle) != Float.floatToIntBits(recoveredAxis1)
                || Float.floatToIntBits(secondAngle) != Float.floatToIntBits(recoveredAxis2)
                || Float.floatToIntBits(secondPrevAngle) != Float.floatToIntBits(recoveredAxis2);
        angle = recoveredAxis1;
        prevAngle = recoveredAxis1;
        secondAngle = recoveredAxis2;
        secondPrevAngle = recoveredAxis2;
        if (changed) {
            setChanged();
            if (canSendData()) sendData();
        }
    }

    public TwisterMillReseatService.ReseatResult reseatFromDiagnostics(TwisterMillReseatService.Trigger trigger) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return TwisterMillReseatService.ReseatResult.failed(
                    TwisterMillReseatService.TargetType.SERVO,
                    worldPosition,
                    "not-server-level"
            );
        }

        float visualAngleBefore = getInterpolatedAngle(0.0F);
        pendingDisassembleAfterZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        clearMode3DisassemblyReturnState();
        angle = 0.0F;
        prevAngle = 0.0F;
        targetAngle = 0.0F;
        secondAngle = 0.0F;
        secondPrevAngle = 0.0F;
        secondTargetAngle = 0.0F;
        updateVisualRunning(false);
        clearPropellerSlotPreviewSync();

        SableInteractiveContraptionBackend.ReloadStabilizationResult result =
                sableBackend.reseatAttachedSubLevel(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        activeRotationProfile,
                        getServoStiffnessPerInertia(serverLevel),
                        getServoDampingPerInertia(serverLevel),
                        TwisterMillConfig.getServoMinEffectiveInertia(),
                        trigger.actionPrefix()
                );
        if (result.poseReseatApplied()) {
            requestPropellerSlotPreviewSync();
            setChanged();
            if (canSendData()) sendData();
        }
        float visualAngleAfter = getInterpolatedAngle(0.0F);
        return new TwisterMillReseatService.ReseatResult(
                TwisterMillReseatService.TargetType.SERVO,
                worldPosition,
                result.poseReseatApplied(),
                result.action(),
                visualAngleBefore,
                visualAngleAfter,
                result.anchorWorldError(),
                result.normalWorldError(),
                result.anchorWorldErrorAfter(),
                result.normalWorldErrorAfter()
        );
    }

    private double getServoStiffnessPerInertia(ServerLevel serverLevel) {
        return isMountedOnPropellerSlotSubLevel(serverLevel)
                ? TwisterMillConfig.getServoStiffnessPerInertia()
                * TwisterMillConfig.getPropellerSlotServoStiffnessMultiplier()
                : TwisterMillConfig.getServoStiffnessPerInertia();
    }

    private double getServoDampingPerInertia(ServerLevel serverLevel) {
        return isMountedOnPropellerSlotSubLevel(serverLevel)
                ? TwisterMillConfig.getServoDampingPerInertia()
                * TwisterMillConfig.getPropellerSlotServoDampingMultiplier()
                : TwisterMillConfig.getServoDampingPerInertia();
    }

    private void applyMode6Oscillation(boolean directInputPriority) {
        float nextAngle = computeMode6OscillationTarget(directInputPriority);
        targetAngle = nextAngle;
        boolean changed = Math.abs(nextAngle - angle) > ANGLE_EPSILON;
        if (changed) {
            angle = nextAngle;
        }

        applyRotation();

        if (changed) {
            setChanged();
            if (canSendData()) sendData();
        }
    }

    private boolean applyWorldLockedMotorNeedsFallback(int modeSignal) {
        if (!(level instanceof ServerLevel serverLevel))
            return true;

        if (!sableBackend.isActive())
            return true;

        Float targetAngle = sableBackend.computeWorldLockedMotorAngleDegrees(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                modeSignal,
                angle
        );
        if (targetAngle == null) {
            return true;
        }

        boolean changed = Math.abs(targetAngle - angle) > ANGLE_EPSILON;
        angle = targetAngle;

        boolean applied = sableBackend.applyMotor(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                angle,
                getServoStiffnessPerInertia(serverLevel),
                getServoDampingPerInertia(serverLevel),
                TwisterMillConfig.getServoMinEffectiveInertia()
        );
        if (!applied) {
            running = false;
            pendingDisassembleAfterZero = false;
            pendingDisassembleZeroHoldTicks = 0;
            clearMode3DisassemblyReturnState();
            sableBackend.clearState();
            setChanged();
            if (canSendData()) sendData();
            return true;
        }

        if (changed) {
            setChanged();
            if (canSendData()) sendData();
        }
        return false;
    }

    private boolean isMountedOnPropellerSlotSubLevel(ServerLevel serverLevel) {
        SubLevel containingSubLevel = Sable.HELPER.getContaining(serverLevel, worldPosition);
        return ServoPropellerSlotManager.isPropellerSlotSubLevel(containingSubLevel);
    }

    private void applyFreeBearingNeutralMotor() {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        if (!sableBackend.isActive())
            return;

        boolean applied = sableBackend.applyMotor(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                0.0F,
                0.0,
                TwisterMillConfig.getFreeBearingDampingPerInertia(),
                TwisterMillConfig.getServoMinEffectiveInertia()
        );
        if (!applied) {
            running = false;
            pendingDisassembleAfterZero = false;
            pendingDisassembleZeroHoldTicks = 0;
            clearMode3DisassemblyReturnState();
            sableBackend.clearState();
            setChanged();
            if (canSendData()) sendData();
        }
    }

    private Direction getFacingDirection() {
        if (level == null)
            return Direction.NORTH;

        BlockState state = getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING))
            return Direction.NORTH;

        return state.getValue(BlockStateProperties.FACING);
    }

    public float getInterpolatedAngle(float partialTicks) {
        Float freeBearingVisualAngle = computeFreeBearingVisualAngle(partialTicks);
        //noinspection ReplaceNullCheck
        if (freeBearingVisualAngle != null) {
            return freeBearingVisualAngle;
        }
        return Mth.lerp(partialTicks, prevAngle, angle);
    }

    public float getInterpolatedSecondAngle(float partialTicks) {
        return Mth.lerp(partialTicks, secondPrevAngle, secondAngle);
    }

    public boolean usesTwoAxisTiltRotationForRender() {
        return activeRotationProfile == SableInteractiveContraptionBackend.RotationProfile.TWO_AXIS_TILT;
    }

    public boolean usesUpPitchRotationForRender() {
        return activeRotationProfile == SableInteractiveContraptionBackend.RotationProfile.UP_PITCH_X
                && getFacingDirection() == Direction.UP;
    }

    @Nullable
    private Float computeCurrentSableMotorAngleDegrees() {
        if (!(level instanceof ServerLevel serverLevel) || !running || !sableBackend.isActive()) {
            return null;
        }

        UUID activeSubLevelId = sableBackend.getActiveSubLevelId();
        if (activeSubLevelId == null) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) {
            return null;
        }

        SubLevel attachedSubLevel = container.getSubLevel(activeSubLevelId);
        if (attachedSubLevel == null || attachedSubLevel.isRemoved()) {
            return null;
        }

        Pose3dc attachedPose = attachedSubLevel.logicalPose();
        Pose3dc containingPose = new Pose3d();

        SubLevel containingSubLevel = Sable.HELPER.getContaining(serverLevel, worldPosition);
        if (containingSubLevel != null && !containingSubLevel.isRemoved()) {
            containingPose = containingSubLevel.logicalPose();
        }

        Quaterniond facingRotation = new Quaterniond(getFacingDirection().getRotation());
        Quaterniond relative = new Quaterniond(containingPose.orientation())
                .mul(facingRotation)
                .conjugate()
                .mul(new Quaterniond(attachedPose.orientation()).mul(facingRotation));

        double axisDot = relative.y();
        double angleDegrees = -2.0D * Math.toDegrees(Math.atan2(-axisDot, relative.w()));
        if (!Double.isFinite(angleDegrees)) {
            return null;
        }

        if (shouldInvertAngleForFacing()) {
            angleDegrees = -angleDegrees;
        }

        return Mth.wrapDegrees((float) angleDegrees);
    }

    @Nullable
    private Float computeFreeBearingVisualAngle(float partialTicks) {
        if (level == null || !running || !isFreeBearingOperatingMode() || !sableBackend.isActive()) {
            return null;
        }

        UUID activeSubLevelId = sableBackend.getActiveSubLevelId();
        if (activeSubLevelId == null) {
            return null;
        }

        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }

        SubLevel attachedSubLevel = container.getSubLevel(activeSubLevelId);
        if (attachedSubLevel == null || attachedSubLevel.isRemoved()) {
            return null;
        }

        Pose3dc attachedPose = resolveRenderPose(attachedSubLevel, partialTicks);
        Pose3dc containingPose = new Pose3d();

        SubLevel containingSubLevel = Sable.HELPER.getContaining(level, worldPosition);
        if (containingSubLevel != null && !containingSubLevel.isRemoved()) {
            containingPose = resolveRenderPose(containingSubLevel, partialTicks);
        }

        Quaterniond facingRotation = new Quaterniond(getFacingDirection().getRotation());
        Quaterniond relative = new Quaterniond(containingPose.orientation())
                .mul(facingRotation)
                .conjugate()
                .mul(new Quaterniond(attachedPose.orientation()).mul(facingRotation));

        double axisDot = relative.y();
        double angleDegrees = -2.0D * Math.toDegrees(Math.atan2(-axisDot, relative.w()));
        if (!Double.isFinite(angleDegrees)) {
            return null;
        }

        if (shouldInvertAngleForFacing()) {
            angleDegrees = -angleDegrees;
        }

        return Mth.wrapDegrees((float) angleDegrees);
    }

    private boolean shouldInvertAngleForFacing() {
        Direction facing = getFacingDirection();
        return facing == Direction.DOWN || facing == Direction.NORTH || facing == Direction.WEST;
    }

    private static Pose3dc resolveRenderPose(SubLevel subLevel, float partialTicks) {
        if (subLevel instanceof ClientSubLevelAccess clientSubLevelAccess) {
            return clientSubLevelAccess.renderPose(partialTicks);
        }
        return subLevel.lastPose().lerp(subLevel.logicalPose(), partialTicks, new Pose3d());
    }

    @Override
    public AssemblyException getLastAssemblyException() {
        return lastException;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        tag.putBoolean(TAG_MANUAL_ENABLED, manualEnabled);
        tag.putBoolean(TAG_RUNNING, running);
        tag.putBoolean(TAG_ASSEMBLE_NEXT_TICK, assembleNextTick);
        tag.putFloat(TAG_ANGLE, angle);
        tag.putFloat(TAG_PREV_ANGLE, prevAngle);
        tag.putFloat(TAG_SECOND_ANGLE, secondAngle);
        tag.putFloat(TAG_SECOND_PREV_ANGLE, secondPrevAngle);
        tag.putFloat(TAG_SECOND_TARGET_ANGLE, secondTargetAngle);
        tag.putInt(SABLE_ROTATION_PROFILE_TAG, activeRotationProfile.storedId());
        tag.putInt(TAG_FREE_BEARING_LIFECYCLE_PHASE, freeBearingLifecyclePhase.storedId());
        tag.putInt(TAG_ASSEMBLED_BLOCK_COUNT, assembledBlockCount);
        tag.putBoolean(TAG_BOUND_TO_WIND_ROTO, boundToWindRoto);
        tag.putBoolean(TAG_PENDING_DISASSEMBLE_AFTER_ZERO, pendingDisassembleAfterZero);
        tag.putBoolean(TAG_PENDING_MODE3_DISASSEMBLY_RETURN, pendingMode3DisassemblyReturn);
        if (!clientPacket) {
            tag.putBoolean(TAG_PENDING_EXTENDED_BINARY_DISASSEMBLY_RETURN,
                    pendingExtendedBinaryDisassemblyReturn);
        }
        if (pendingDisassembleAfterZero && pendingLaterGuiRotationProfile != null) {
            tag.putInt(TAG_PENDING_LATER_GUI_ROTATION_PROFILE,
                    pendingLaterGuiRotationProfile.storedId());
        } else {
            tag.remove(TAG_PENDING_LATER_GUI_ROTATION_PROFILE);
        }
        tag.putInt("LastWestSignal", lastWestSignal);
        tag.putInt("LastEastSignal", lastEastSignal);
        tag.putInt("LastOppositeTopSignal", lastOppositeTopSignal);
        tag.putFloat("TargetAngle", targetAngle);
        tag.putInt(TAG_BINARY_MODE_SIGNAL, binaryModeSignal);
        tag.putInt(TAG_BINARY_SPEED_SIGNAL, binarySpeedSignal);
        tag.putInt(TAG_BINARY_ANGLE_SIGNAL, binaryAngleSignal);
        tag.putBoolean(TAG_HAS_VALID_BINARY_FRAME, hasValidBinaryFrame);
        tag.putBoolean(TAG_INTERNAL_REDSTONE_LINK_ACTIVE, internalRedstoneLinkActive);
        tag.putInt(TAG_INTERNAL_REDSTONE_LINK_RECEIVED_SIGNAL, internalRedstoneLinkReceivedSignal);
        tag.putInt(TAG_SECONDARY_BINARY_ANGLE_SIGNAL, secondaryBinaryAngleSignal);
        tag.putBoolean(TAG_SECONDARY_HAS_VALID_BINARY_FRAME, secondaryHasValidBinaryFrame);
        tag.putBoolean(TAG_SECONDARY_INTERNAL_REDSTONE_LINK_ACTIVE, secondaryInternalRedstoneLinkActive);
        tag.putInt(TAG_SECONDARY_INTERNAL_REDSTONE_LINK_RECEIVED_SIGNAL,
                secondaryInternalRedstoneLinkReceivedSignal);
        if (clientPacket) {
            tag.putBoolean(TAG_SPEED_ZERO_MOVEMENT_ENABLED, speedZeroMovementEnabled);
            tag.putFloat(TAG_MODE_7_MEASURED_RPM, mode7MeasuredRpm);
            tag.putFloat(TAG_PHYSICAL_BEARING_MEASURED_ANGLE, physicalBearingMeasuredAngle);
        }

        if (boundWindRotoDimension != null) {
            tag.putString(TAG_BOUND_WIND_ROTO_DIMENSION, boundWindRotoDimension.location().toString());
        }
        if (boundWindRotoPos != null) {
            tag.putLong(TAG_BOUND_WIND_ROTO_POS, boundWindRotoPos.asLong());
        }
        if (boundServoOriginalPos != null) {
            tag.putLong(TAG_BOUND_SERVO_ORIGINAL_POS, boundServoOriginalPos.asLong());
        }

        AssemblyException.write(tag, registries, lastException);
        sableBackend.write(tag, TAG_SABLE_ACTIVE, TAG_SABLE_SUBLEVEL_ID);
        if (!clientPacket) {
            rememberedShipMemory.write(tag);
        }
        propellerSlotManager.write(tag);
        TwisterMillSableSchematicRemapper.remapForWrite(
                tag,
                clientPacket,
                TwisterMillSableSchematicRemapper.OwnerType.SERVO
        );
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag = migrateLegacyServoGuiOptionForRead(tag);
        tag = TwisterMillSableSchematicRemapper.prepareForRead(
                tag,
                clientPacket,
                TwisterMillSableSchematicRemapper.OwnerType.SERVO
        );
        super.read(tag, registries, clientPacket);

        if (clientPacket) {
            float syncedRpm = tag.contains(TAG_MODE_7_MEASURED_RPM)
                    ? tag.getFloat(TAG_MODE_7_MEASURED_RPM)
                    : 0.0F;
            mode7MeasuredRpm = Float.isFinite(syncedRpm) && syncedRpm > 0.0F ? syncedRpm : 0.0F;
            physicalBearingMeasuredAngle = normalizePhysicalBearingMeasuredAngle(
                    tag.contains(TAG_PHYSICAL_BEARING_MEASURED_ANGLE)
                            ? tag.getFloat(TAG_PHYSICAL_BEARING_MEASURED_ANGLE)
                            : null
            );
        } else {
            mode7MeasuredRpm = 0.0F;
            physicalBearingMeasuredAngle = 0.0F;
        }

        boolean hasStoredFreeBearingPhase = tag.contains(TAG_FREE_BEARING_LIFECYCLE_PHASE);
        FreeBearingLifecyclePhase storedFreeBearingPhase = hasStoredFreeBearingPhase
                ? FreeBearingLifecyclePhase.fromStoredId(tag.getInt(TAG_FREE_BEARING_LIFECYCLE_PHASE))
                : null;
        UUID persistedFreeBearingTopId = tag.hasUUID(TAG_SABLE_SUBLEVEL_ID)
                ? tag.getUUID(TAG_SABLE_SUBLEVEL_ID)
                : null;
        boolean persistedFreeBearingBackendOrTop = tag.getBoolean(TAG_SABLE_ACTIVE)
                || persistedFreeBearingTopId != null;

        manualEnabled = tag.getBoolean(TAG_MANUAL_ENABLED);
        running = tag.getBoolean(TAG_RUNNING);
        assembleNextTick = tag.getBoolean(TAG_ASSEMBLE_NEXT_TICK);
        angle = tag.getFloat(TAG_ANGLE);
        prevAngle = tag.contains(TAG_PREV_ANGLE) ? tag.getFloat(TAG_PREV_ANGLE) : angle;
        secondAngle = tag.contains(TAG_SECOND_ANGLE) ? tag.getFloat(TAG_SECOND_ANGLE) : 0.0F;
        secondPrevAngle = tag.contains(TAG_SECOND_PREV_ANGLE)
                ? tag.getFloat(TAG_SECOND_PREV_ANGLE)
                : secondAngle;
        secondTargetAngle = tag.contains(TAG_SECOND_TARGET_ANGLE)
                ? tag.getFloat(TAG_SECOND_TARGET_ANGLE)
                : 0.0F;
        if (!Float.isFinite(secondAngle)) secondAngle = 0.0F;
        if (!Float.isFinite(secondPrevAngle)) secondPrevAngle = secondAngle;
        if (!Float.isFinite(secondTargetAngle)) secondTargetAngle = 0.0F;
        secondAngle = Mth.clamp(secondAngle, -ServoTwoAxisRotationMath.MAX_AXIS_DEGREES,
                ServoTwoAxisRotationMath.MAX_AXIS_DEGREES);
        secondPrevAngle = Mth.clamp(secondPrevAngle, -ServoTwoAxisRotationMath.MAX_AXIS_DEGREES,
                ServoTwoAxisRotationMath.MAX_AXIS_DEGREES);
        secondTargetAngle = Mth.clamp(secondTargetAngle, -ServoTwoAxisRotationMath.MAX_AXIS_DEGREES,
                ServoTwoAxisRotationMath.MAX_AXIS_DEGREES);
        rotationProfileTagPresent = tag.contains(SABLE_ROTATION_PROFILE_TAG);
        activeRotationProfile = rotationProfileTagPresent
                ? SableInteractiveContraptionBackend.RotationProfile.fromStoredId(
                tag.getInt(SABLE_ROTATION_PROFILE_TAG))
                : SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
        rotationProfileTransitionActive = false;
        rotationProfileTransitionTarget = activeRotationProfile;
        rotationProfileNeutralStableTicks = 0;
        pitchClearanceNeutralStableTicks = 0;
        assembledBlockCount = tag.getInt(TAG_ASSEMBLED_BLOCK_COUNT);
        if (tag.contains(TAG_BOUND_TO_WIND_ROTO))
            boundToWindRoto = tag.getBoolean(TAG_BOUND_TO_WIND_ROTO);
        pendingDisassembleAfterZero = tag.contains(TAG_PENDING_DISASSEMBLE_AFTER_ZERO)
                && tag.getBoolean(TAG_PENDING_DISASSEMBLE_AFTER_ZERO);
        boolean hasMode3DisassemblyOrigin = tag.contains(TAG_PENDING_MODE3_DISASSEMBLY_RETURN);
        pendingMode3DisassemblyReturn = pendingDisassembleAfterZero
                && hasMode3DisassemblyOrigin
                && tag.getBoolean(TAG_PENDING_MODE3_DISASSEMBLY_RETURN);
        boolean hasExtendedBinaryDisassemblyOrigin = !clientPacket
                && tag.contains(TAG_PENDING_EXTENDED_BINARY_DISASSEMBLY_RETURN);
        if (!clientPacket) {
            pendingExtendedBinaryDisassemblyReturn = pendingDisassembleAfterZero
                    && !pendingMode3DisassemblyReturn
                    && hasExtendedBinaryDisassemblyOrigin
                    && tag.getBoolean(TAG_PENDING_EXTENDED_BINARY_DISASSEMBLY_RETURN);
        }
        clearLaterGuiDisassemblyReturnState();
        boolean hasLaterGuiRotationProfile = tag.contains(TAG_PENDING_LATER_GUI_ROTATION_PROFILE);
        if (isConfiguredFreeBearingRotationMode()) {
            pendingMode3DisassemblyReturn = false;
            if (!clientPacket) {
                pendingExtendedBinaryDisassemblyReturn = false;
            }
            if (storedFreeBearingPhase == FreeBearingLifecyclePhase.RETURNING_TO_ZERO) {
                pendingDisassembleAfterZero = true;
            }
        } else {
            if (pendingDisassembleAfterZero
                    && !pendingMode3DisassemblyReturn
                    && !pendingExtendedBinaryDisassemblyReturn
                    && isLaterGuiAssemblyOption()
                    && hasLaterGuiRotationProfile
                    && tag.getInt(TAG_PENDING_LATER_GUI_ROTATION_PROFILE)
                    == SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS.storedId()) {
                pendingLaterGuiRotationProfile =
                        SableInteractiveContraptionBackend.RotationProfile.FACING_AXIS;
            }
            if (!clientPacket
                    && pendingDisassembleAfterZero
                    && !pendingMode3DisassemblyReturn
                    && !pendingExtendedBinaryDisassemblyReturn
                    && hasLaterGuiRotationProfile
                    && pendingLaterGuiRotationProfile == null) {
                pendingDisassembleAfterZero = false;
            }
            if (!clientPacket
                    && pendingDisassembleAfterZero
                    && !hasMode3DisassemblyOrigin
                    && !hasExtendedBinaryDisassemblyOrigin) {
                pendingDisassembleAfterZero = false;
                pendingMode3DisassemblyReturn = false;
                pendingExtendedBinaryDisassemblyReturn = false;
            }
            if (!clientPacket
                    && pendingDisassembleAfterZero
                    && isLaterGuiAssemblyOption()
                    && !pendingMode3DisassemblyReturn
                    && !pendingExtendedBinaryDisassemblyReturn
                    && pendingLaterGuiRotationProfile == null) {
                pendingDisassembleAfterZero = false;
            }
        }
        pendingDisassembleZeroHoldTicks = 0;
        resetMode3DisassemblyRuntimeState();

        if (tag.contains("LastWestSignal"))
            lastWestSignal = Mth.clamp(tag.getInt("LastWestSignal"), 0, 15);
        if (tag.contains("LastEastSignal"))
            lastEastSignal = Mth.clamp(tag.getInt("LastEastSignal"), 0, 15);
        if (tag.contains("LastOppositeTopSignal"))
            lastOppositeTopSignal = Mth.clamp(tag.getInt("LastOppositeTopSignal"), 0, 15);
        if (tag.contains("TargetAngle"))
            targetAngle = tag.getFloat("TargetAngle");
        if (tag.contains(TAG_BINARY_MODE_SIGNAL))
            binaryModeSignal = Mth.clamp(tag.getInt(TAG_BINARY_MODE_SIGNAL), 0, 15);
        if (tag.contains(TAG_BINARY_SPEED_SIGNAL))
            binarySpeedSignal = Mth.clamp(tag.getInt(TAG_BINARY_SPEED_SIGNAL), 0, 15);
        if (tag.contains(TAG_BINARY_ANGLE_SIGNAL))
            binaryAngleSignal = Mth.clamp(tag.getInt(TAG_BINARY_ANGLE_SIGNAL), 0, 15);
        hasValidBinaryFrame = tag.contains(TAG_HAS_VALID_BINARY_FRAME) && tag.getBoolean(TAG_HAS_VALID_BINARY_FRAME);
        internalRedstoneLinkActive = tag.contains(TAG_INTERNAL_REDSTONE_LINK_ACTIVE)
                && tag.getBoolean(TAG_INTERNAL_REDSTONE_LINK_ACTIVE)
                && isInternalRedstoneLinkMode();
        internalRedstoneLinkReceivedSignal = tag.contains(TAG_INTERNAL_REDSTONE_LINK_RECEIVED_SIGNAL)
                ? Mth.clamp(tag.getInt(TAG_INTERNAL_REDSTONE_LINK_RECEIVED_SIGNAL), 0, 15)
                : 0;
        if (!internalRedstoneLinkActive)
            internalRedstoneLinkReceivedSignal = 0;
        secondaryBinaryAngleSignal = tag.contains(TAG_SECONDARY_BINARY_ANGLE_SIGNAL)
                ? Mth.clamp(tag.getInt(TAG_SECONDARY_BINARY_ANGLE_SIGNAL), 0, 15)
                : 0;
        secondaryHasValidBinaryFrame = tag.contains(TAG_SECONDARY_HAS_VALID_BINARY_FRAME)
                && tag.getBoolean(TAG_SECONDARY_HAS_VALID_BINARY_FRAME);
        secondaryInternalRedstoneLinkActive = tag.contains(TAG_SECONDARY_INTERNAL_REDSTONE_LINK_ACTIVE)
                && tag.getBoolean(TAG_SECONDARY_INTERNAL_REDSTONE_LINK_ACTIVE);
        secondaryInternalRedstoneLinkReceivedSignal =
                tag.contains(TAG_SECONDARY_INTERNAL_REDSTONE_LINK_RECEIVED_SIGNAL)
                        ? Mth.clamp(tag.getInt(TAG_SECONDARY_INTERNAL_REDSTONE_LINK_RECEIVED_SIGNAL), 0, 15)
                        : 0;
        if (!secondaryInternalRedstoneLinkActive) {
            secondaryInternalRedstoneLinkReceivedSignal = 0;
        }
        secondaryBinaryReceiver.reset(secondaryInternalRedstoneLinkReceivedSignal > 0);
        if (tag.contains(TAG_SPEED_ZERO_MOVEMENT_ENABLED))
            speedZeroMovementEnabled = tag.getBoolean(TAG_SPEED_ZERO_MOVEMENT_ENABLED);

        if (tag.contains(TAG_BOUND_WIND_ROTO_DIMENSION)) {
            ResourceLocation location = ResourceLocation.parse(tag.getString(TAG_BOUND_WIND_ROTO_DIMENSION));
            boundWindRotoDimension = ResourceKey.create(Registries.DIMENSION, location);
        } else {
            boundWindRotoDimension = null;
        }

        boundWindRotoPos = tag.contains(TAG_BOUND_WIND_ROTO_POS)
                ? BlockPos.of(tag.getLong(TAG_BOUND_WIND_ROTO_POS)).immutable()
                : null;

        boundServoOriginalPos = tag.contains(TAG_BOUND_SERVO_ORIGINAL_POS)
                ? BlockPos.of(tag.getLong(TAG_BOUND_SERVO_ORIGINAL_POS)).immutable()
                : null;

        lastException = AssemblyException.read(tag, registries);
        sableBackend.read(tag, TAG_SABLE_ACTIVE, TAG_SABLE_SUBLEVEL_ID);
        if (!clientPacket
                && isConfiguredFreeBearingRotationMode()
                && persistedFreeBearingTopId != null
                && sableBackend.getActiveSubLevelId() == null) {
            sableBackend.retainPersistedSubLevelForRecovery(persistedFreeBearingTopId);
        }
        if (!clientPacket) {
            rememberedShipMemory.read(tag);
        }
        propellerSlotManager.read(tag);
        if (isConfiguredFreeBearingRotationMode()) {
            boolean knownRecoveryPhase = storedFreeBearingPhase != null
                    && storedFreeBearingPhase != FreeBearingLifecyclePhase.UNASSEMBLED;
            boolean requiresRecovery = persistedFreeBearingBackendOrTop
                    || knownRecoveryPhase
                    || pendingDisassembleAfterZero
                    || running;
            freeBearingLifecyclePhase = requiresRecovery
                    ? FreeBearingLifecyclePhase.RECOVERY_PENDING
                    : FreeBearingLifecyclePhase.UNASSEMBLED;
            if (requiresRecovery) {
                assembleNextTick = false;
            }
        } else {
            freeBearingLifecyclePhase = FreeBearingLifecyclePhase.UNASSEMBLED;
        }
        updateFreeBearingTopFollowPolicy();
        pendingPropellerSlotRejoinSyncTicks = !clientPacket && sableBackend.getActiveSubLevelId() != null
                ? PROPELLER_SLOT_REJOIN_SYNC_RETRY_TICKS
                : 0;
        if (!clientPacket && propellerSlotManager.hasAnySlot()) {
            requestPropellerSlotPreviewSync();
        } else {
            clearPropellerSlotPreviewSync();
        }
        diagnosticFirstRefreshLogged = false;
        diagnosticRefreshFailureLogged = false;
        if (!clientPacket) {
            logSableLifecycleDiagnostics("read");
        }

        needsStateRefresh = true;
    }

    private static CompoundTag migrateLegacyServoGuiOptionForRead(CompoundTag tag) {
        if (!tag.contains("ScrollValue") || tag.getInt("ScrollValue") != 7) {
            return tag;
        }

        CompoundTag migratedTag = tag.copy();
        migratedTag.putInt("ScrollValue", 6);
        return migratedTag;
    }

    private static String formatTooltipFloat(float value) {
        if (!Float.isFinite(value)) {
            return "0";
        }
        if (Math.abs(value - Math.round(value)) < 0.0001F) {
            return Integer.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private Component buildSpeedInputTooltip(int signal) {
        float speedDegPerTick = getDegreesPerTickForSignal(signal);
        return Component.literal(signal + " => " + formatTooltipFloat(speedDegPerTick) + "°/tick");
    }

    private Component buildAngleInputTooltip(int signal) {
        float angleDegrees = Math.abs(getTargetAngleForSignal(signal));
        return Component.literal(signal + " => " + formatTooltipFloat(angleDegrees) + "°");
    }

    private String formatGroupedBinaryCode(String raw12Bit) {
        if (raw12Bit == null || raw12Bit.length() != 12) {
            return "0000 0000 0000";
        }
        return raw12Bit.substring(0, 4) + " " + raw12Bit.substring(4, 8) + " " + raw12Bit.substring(8, 12);
    }

    private String buildBinaryCodeForDisplay() {
        String raw12Bit = Integer.toBinaryString(((binaryModeSignal & 0xF) << 8) | ((binarySpeedSignal & 0xF) << 4) | (binaryAngleSignal & 0xF));
        if (raw12Bit.length() < 12) {
            raw12Bit = "0".repeat(12 - raw12Bit.length()) + raw12Bit;
        }
        return formatGroupedBinaryCode(raw12Bit);
    }

    private Component getTooltipContraptionStatusComponent() {
        boolean assembledActive = running || sableBackend.isActive();

        if (pendingDisassembleAfterZero && assembledActive) {
            return CreateLang.translateDirect("tooltip.twistermill.servo.status.disassembling")
                    .withStyle(ChatFormatting.GOLD);
        }

        if (assembledActive) {
            return getAssembledStatusWithBlockCount(getContraptionBlockCount());
        }

        if (isAssemblyReadyBlinkActive()) {
            return getAssemblyReadyBlinkStatusComponent();
        }

        return CreateLang.translateDirect("tooltip.twistermill.servo.status.disassembled")
                .withStyle(ChatFormatting.BLUE);
    }

    private Component getAssembledStatusWithBlockCount(int blockCount) {
        return Component.empty()
                .append(CreateLang.translateDirect("tooltip.twistermill.servo.status.assembled")
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Integer.toString(Math.max(0, blockCount))).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(CreateLang.translateDirect("tooltip.twistermill.status.blocks")
                        .withStyle(ChatFormatting.AQUA));
    }

    private boolean isAssemblyReadyBlinkActive() {
        return level != null && !level.getBlockState(worldPosition.relative(getFacingDirection())).canBeReplaced();
    }

    private Component getAssemblyReadyBlinkStatusComponent() {
        if (level != null && ((level.getGameTime() / 20) % 2L) == 1L) {
            return CreateLang.translateDirect("tooltip.twistermill.vertical.ready_for_assemble_toggle")
                    .withStyle(ChatFormatting.GREEN);
        }
        return CreateLang.translateDirect("tooltip.twistermill.vertical.block_on_bearing")
                .withStyle(ChatFormatting.BLUE);
    }

    private void addAssemblyHintIfNeeded(List<Component> tooltip) {
        if (level == null || running || sableBackend.isActive()) {
            return;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return;
        }

        BlockState attachedState = level.getBlockState(worldPosition.relative(getFacingDirection()));
        if (attachedState.canBeReplaced()) {
            return;
        }

        tooltip.add(Component.empty());
        TooltipHelper.addHint(tooltip, "hint.empty_bearing");
    }

    private boolean addMode7GoggleTooltip(List<Component> tooltip) {
        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.servo.rotational_speed")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text(formatTooltipFloat(mode7MeasuredRpm))
                .style(ChatFormatting.YELLOW)
                .space()
                .add(CreateLang.translateDirect("tooltip.twistermill.unit_rpm")
                        .withStyle(ChatFormatting.YELLOW))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.status")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text("")
                .add(getTooltipContraptionStatusComponent())
                .forGoggles(tooltip, 1);
        return true;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (isConfiguredFreeBearingRotationMode()) {
            return false;
        }
        return super.addToTooltip(tooltip, isPlayerSneaking);
    }

    @Override
    public boolean addExceptionToTooltip(List<Component> tooltip) {
        if (isConfiguredFreeBearingRotationMode()) {
            return false;
        }
        return IDisplayAssemblyExceptions.super.addExceptionToTooltip(tooltip);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (isConfiguredFreeBearingRotationMode()) {
            return addMode7GoggleTooltip(tooltip);
        }

        float displayedAngle = isPhysicalBearingAngleTooltipActive()
                ? physicalBearingMeasuredAngle
                : angle;
        boolean details = AllKeys.ctrlDown();
        boolean internalLinkMode = isInternalRedstoneLinkMode();
        int liveWestSignal = internalLinkMode ? 0 : getWestSpeedSignal();
        int liveEastSignal = internalLinkMode ? 0 : getEastAngleSignal();
        boolean binaryConfigEnabled = TwisterMillConfig.isServoTwisterBinaryInputEnabled();
        boolean angleInputActive = liveEastSignal > 0;
        boolean speedInputActive = liveWestSignal > 0;
        Component activeState = Component.translatable("create.tooltip.twistermill.state.active").withStyle(ChatFormatting.AQUA);
        Component inactiveState = Component.translatable("create.tooltip.twistermill.state.inactive").withStyle(ChatFormatting.AQUA);
        Component disabledState = Component.translatable("create.tooltip.twistermill.state.disabled").withStyle(ChatFormatting.AQUA);
        Component binaryLinkRequiredState = Component.translatable("create.tooltip.twistermill.servo.binary_rs_link_mode_required").withStyle(ChatFormatting.AQUA);
        Component binaryStateComponent = !binaryConfigEnabled
                ? disabledState
                : (isBinaryInputTooltipActiveMode() ? activeState : binaryLinkRequiredState);

        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.servo.angle")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(Math.abs(displayedAngle))
                .style(ChatFormatting.YELLOW)
                .add(Component.literal("°"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.status")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text("")
                .add(getTooltipContraptionStatusComponent())
                .forGoggles(tooltip, 1);

        if (!details) {
            CreateLang.text("")
                    .add(Component.literal("details: ").withStyle(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.translateDirect("tooltip.twistermill.key_ctrl").withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip);
            addAssemblyHintIfNeeded(tooltip);
            return true;
        }

        CreateLang.translate("tooltip.twistermill.servo.west_speed_input")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text("")
                .add(buildSpeedInputTooltip(liveWestSignal))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.east_angle_input")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text("")
                .add(buildAngleInputTooltip(liveEastSignal))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.configured_angle")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text(getConfiguredMaxDegrees() + "°")
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.input_state")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.servo.input_angle")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("")
                .add(angleInputActive ? activeState : inactiveState)
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        CreateLang.translate("tooltip.twistermill.servo.input_speed")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("")
                .add(speedInputActive ? activeState : inactiveState)
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        CreateLang.translate("tooltip.twistermill.servo.input_binary")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        CreateLang.text("")
                .add(binaryStateComponent)
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        CreateLang.translate("tooltip.twistermill.servo.binary_code")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text(hasValidBinaryFrame ? buildBinaryCodeForDisplay() : Component.translatable("create.tooltip.twistermill.state.none").getString())
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        addAssemblyHintIfNeeded(tooltip);
        return true;
    }
}
