package com.proventure.twistermill.blockentity;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.block.custom.InvServoTwisterBlock;
import com.proventure.twistermill.binaryredstone.BinarySignalProtocol;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.proventure.twistermill.diagnostics.TwisterMillReseatService;
import com.proventure.twistermill.util.ServoRedstoneMappings;
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
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class InvServoTwisterBlockEntity extends KineticBlockEntity implements IDisplayAssemblyExceptions, InternalServoRedstoneLinkOwner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float ANGLE_EPSILON = 0.01F;
    private static final float NORMAL_MOTION_EPSILON = 0.0001F;
    private static final float SOUTH_CONTINUOUS_DEGREES_PER_TICK = 1.0F;
    private static final double SERVO_STIFFNESS_PER_INERTIA = 1600.0;
    private static final double SERVO_DAMPING_PER_INERTIA = 40.0;
    private static final double FREE_BEARING_DAMPING_PER_INERTIA = 0.03D;
    private static final double MIN_EFFECTIVE_INERTIA = 10.0;
    private static final float MIN_DISASSEMBLE_DEGREES_PER_TICK = 0.25F;
    private static final float DISASSEMBLE_RETURN_DECEL_START_DEGREES = 30.0F;
    private static final float DISASSEMBLE_RETURN_SLOW_ZONE_DEGREES = 5.0F;
    private static final float DISASSEMBLE_ZERO_SNAP_DEGREES = 0.25F;
    private static final int DISASSEMBLE_ZERO_HOLD_TICKS = 2;
    private static final float MODE3_EXIT_RETURN_DEGREES_PER_TICK = 2.0F;
    private static final float MODE6_MIN_DEGREES_PER_TICK = 0.05F;
    private static final double MODE6_TWO_PI = Math.PI * 2.0D;
    private static final int MODE_3_STEP = 7;
    private static final int MODE_ABSOLUTE_0_540 = 8;
    private static final int MODE_CENTERED = 9;
    private static final int MODE_FINE_0_180 = 10;
    private static final int MODE_FINE_CENTERED = 11;
    private static final int MODE_FLIP = 12;
    private static final int MODE_INVERTED_FLIP = 13;
    private static final int MODE_OSCILLATION_0_ANGLE = 14;
    private static final int MODE_CENTERED_OSCILLATION = 15;
    private static final float MAX_EXTENDED_MODE_DEGREES = 540.0F;
    private static final float MODE15_MAX_HALF_RANGE_DEGREES = 270.0F;
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
    private static final String TAG_PENDING_DISASSEMBLE_AFTER_ZERO = "PendingDisassembleAfterZero";
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

    protected ScrollOptionBehaviour<MaxAngleOption> maxAngleBehaviour;
    private LinkBehaviour internalRedstoneLink;
    @Nullable
    protected AssemblyException lastException;

    private boolean manualEnabled = false;
    private boolean running = false;
    private boolean assembleNextTick = false;
    private float angle = 0.0F;
    private float prevAngle = 0.0F;

    private boolean lastVisualRunning = false;
    private boolean needsStateRefresh = true;

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
    private boolean binaryVisualInputHigh = false;
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
    private boolean pendingDisassembleAfterZero = false;
    private int pendingDisassembleZeroHoldTicks = 0;
    private boolean speedZeroMovementEnabled = true;
    private boolean mode3ExitReturnActive = false;
    private double mode6OscillationPhase = 0.0D;
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

    private final SableInteractiveContraptionBackend sableBackend = new SableInteractiveContraptionBackend(TwisterMillDiagnostics.Target.INV_SERVO);
    private final RememberedSableShipMemory rememberedShipMemory = new RememberedSableShipMemory();

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

    public InvServoTwisterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INV_SERVO_TWISTER_BE.get(), pos, state);
    }

    private boolean canSendData() {
        return level instanceof ServerLevel serverLevel
                && serverLevel.getServer().isRunning();
    }

    private boolean refreshSpeedZeroMovementConfigFromServer() {
        boolean configured = TwisterMillConfig.isInvServoSpeedZeroMovementEnabled();
        if (speedZeroMovementEnabled == configured) {
            return false;
        }
        speedZeroMovementEnabled = configured;
        return true;
    }

    private void logSableLifecycleDiagnostics(String event) {
        if (!TwisterMillDiagnostics.isInvServoLoggingEnabled()) {
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

        LOGGER.info("[InvServoLifecycleDiag] event={} pos={} gameTime={} facing={} running={} sableActive={} activeSubLevelId={} assembleNextTick={} pendingDisassembleAfterZero={} lastWestSignal={} lastEastSignal={} lastOppositeTopSignal={} binaryModeSignal={} binarySpeedSignal={} binaryAngleSignal={} internalRedstoneLinkActive={} internalRedstoneLinkReceivedSignal={}",
                event,
                worldPosition,
                level == null ? -1L : level.getGameTime(),
                getFacingDirection(),
                running,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                assembleNextTick,
                pendingDisassembleAfterZero,
                lastWestSignal,
                lastEastSignal,
                lastOppositeTopSignal,
                binaryModeSignal,
                binarySpeedSignal,
                binaryAngleSignal,
                internalRedstoneLinkActive,
                internalRedstoneLinkReceivedSignal);
    }

    public float getWindRotoBindingAngleDegrees() {
        return Math.abs(angle);
    }

    public int getBoundContraptionBlockCount() {
        return assembledBlockCount;
    }

    @Nullable
    public UUID getActiveTopSubLevelIdForRender() {
        return sableBackend.getActiveSubLevelId();
    }

    void collectChildSubLevelIdsForWindRotoSailCount(Collection<UUID> target) {
        if (target == null) {
            return;
        }

        UUID activeSubLevelId = sableBackend.getActiveSubLevelId();
        if (activeSubLevelId != null) {
            target.add(activeSubLevelId);
        }
    }

    public void setBoundToWindRoto(boolean boundToWindRoto) {
        if (this.boundToWindRoto == boundToWindRoto)
            return;
        this.boundToWindRoto = boundToWindRoto;
        setChanged();
        if (canSendData()) sendData();
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

    @SuppressWarnings("unused")
    public enum MaxAngleOption implements INamedIconOptions {
        DEG_60(60, "twistermill.max_angle.option.60", AllIcons.I_ROTATE_NEVER_PLACE),
        DEG_120(120, "twistermill.max_angle.option.1.20", AllIcons.I_ROTATE_PLACE),
        DEG_240(240, "twistermill.max_angle.option.2.40", AllIcons.I_ROTATE_PLACE_RETURNED),
        DEG_60_LINK(60, "twistermill.max_angle.option.60_link", AllIcons.I_ROTATE_NEVER_PLACE, true),
        DEG_120_LINK(120, "twistermill.max_angle.option.1.20_link", AllIcons.I_ROTATE_PLACE, true),
        DEG_240_LINK(240, "twistermill.max_angle.option.2.40_link", AllIcons.I_ROTATE_PLACE_RETURNED, true);

        private final int maxDegrees;
        private final String translationKey;
        private final AllIcons icon;
        private final boolean internalRedstoneLink;

        MaxAngleOption(int maxDegrees, String translationKey, AllIcons icon) {
            this(maxDegrees, translationKey, icon, false);
        }

        MaxAngleOption(int maxDegrees, String translationKey, AllIcons icon, boolean internalRedstoneLink) {
            this.maxDegrees = maxDegrees;
            this.translationKey = translationKey;
            this.icon = icon;
            this.internalRedstoneLink = internalRedstoneLink;
        }

        public int getMaxDegrees() {
            return maxDegrees;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

        public boolean isInternalRedstoneLink() {
            return internalRedstoneLink;
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

    @Nullable
    private MaxAngleOption getCurrentMaxAngleOption() {
        return maxAngleBehaviour == null ? null : maxAngleBehaviour.get();
    }

    private static boolean isBinaryInputTooltipActiveMode(@Nullable MaxAngleOption option) {
        return option != null && option.isInternalRedstoneLink();
    }

    private boolean isBinaryInputTooltipActiveMode() {
        return isBinaryInputTooltipActiveMode(getCurrentMaxAngleOption());
    }

    @Override
    public boolean shouldRenderInternalRedstoneLinkSlots() {
        return isInternalRedstoneLinkMode();
    }

    @Override
    public Direction getInternalRedstoneLinkSide() {
        return InternalServoRedstoneLinkOwner.getInvServoInternalLinkSide(getBlockState());
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

    private static boolean isExtendedTargetModeSignal(int modeSignal) {
        return modeSignal >= MODE_3_STEP && modeSignal <= MODE_INVERTED_FLIP;
    }

    private static boolean isExtendedOscillationModeSignal(int modeSignal) {
        return modeSignal == MODE_OSCILLATION_0_ANGLE || modeSignal == MODE_CENTERED_OSCILLATION;
    }

    private static boolean isOscillationModeSignal(int modeSignal) {
        return isMode6OscillationSignal(modeSignal) || isExtendedOscillationModeSignal(modeSignal);
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

        if (!isInternalRedstoneLinkMode()) {
            disableInternalRedstoneLink(true);
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
                running && (manualEnabled || lastEastSignal > 0 || lastOppositeTopSignal > 0 || Math.abs(angle) > ANGLE_EPSILON);
        updateVisualRunning(visualRunning);
    }

    @SuppressWarnings("unused")
    public void onPlayerToggle(Player player) {
        if (level == null || level.isClientSide)
            return;

        executeManualToggle();
    }

    private void executeManualToggle() {
        if (running) {
            assembleNextTick = false;
            manualEnabled = false;
            pendingDisassembleAfterZero = true;
            pendingDisassembleZeroHoldTicks = 0;
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

    private void stopMotion() {
        targetAngle = 0.0F;
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
        return isInternalRedstoneLinkMode()
                && side == getInternalRedstoneLinkSide();
    }

    public boolean tryToggleInternalRedstoneLinkReceiver(Direction side, @Nullable Player player) {
        if (!shouldHandleInternalRedstoneLinkWrench(side))
            return false;

        if (level == null || level.isClientSide)
            return true;

        internalRedstoneLinkActive = !internalRedstoneLinkActive;
        internalRedstoneLinkReceivedSignal = 0;

        if (internalRedstoneLink != null) {
            internalRedstoneLink.notifySignalChange();
        }

        updateVisualPowerState();

        if (player != null) {
            player.displayClientMessage(
                    Component.translatable(internalRedstoneLinkActive
                                    ? "twistermill.servo.redstone_link.receiver_active"
                                    : "twistermill.servo.redstone_link.receiver_inactive")
                            .withStyle(internalRedstoneLinkActive ? ChatFormatting.GREEN : ChatFormatting.RED),
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

        if (!isInternalRedstoneLinkMode() || !internalRedstoneLinkActive)
            return;

        int clampedPower = Mth.clamp(power, 0, 15);
        if (internalRedstoneLinkReceivedSignal == clampedPower)
            return;

        internalRedstoneLinkReceivedSignal = clampedPower;
        updateVisualPowerState();
        setChanged();
        if (canSendData()) sendData();
    }

    private void disableInternalRedstoneLink(boolean clearFrequencies) {
        internalRedstoneLinkActive = false;
        internalRedstoneLinkReceivedSignal = 0;

        if (clearFrequencies && internalRedstoneLink != null) {
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
            return;
        }

        if (!TwisterMillConfig.isInvServoTwisterBinaryInputEnabled()) {
            resetControlMarkerReceiver(oppositeHigh);
            resetBinaryReceiver(oppositeHigh);
            clearBinaryControlState();
            lastOppositeTopSignal = 0;
            targetAngle = pendingDisassembleAfterZero ? 0.0F : angle;
            return;
        }

        boolean controlMarkerTriggered = false;
        if (advanceBinaryReceiver) {
            controlMarkerTriggered = tickControlMarkerReceiver(oppositeHigh);
            if (controlMarkerTriggered) {
                executeManualToggle();
                resetBinaryReceiver(oppositeHigh);
                clearBinaryControlState();
            } else {
                tickBinaryReceiver(oppositeHigh);
            }
        } else {
            binaryLastInputHigh = oppositeHigh;
            controlMarkerLastInputHigh = oppositeHigh;
        }

        if (controlMarkerTriggered) {
            lastOppositeTopSignal = 0;
            targetAngle = pendingDisassembleAfterZero ? 0.0F : angle;
            return;
        }

        int resolvedBinaryMode = getOppositeTopInputLocked() ? 0 : binaryModeSignal;
        lastOppositeTopSignal = resolvedBinaryMode;
        targetAngle = pendingDisassembleAfterZero
                ? 0.0F
                : getBinaryTargetAngleForMode(resolvedBinaryMode, binaryAngleSignal);
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
        return Mth.clamp(baseAngle, 0.0F, 360.0F);
    }

    private float getTargetAngleForSignal(int redstonePower) {
        if (isExtendedTargetModeSignal(lastOppositeTopSignal)) {
            return computeExtendedTargetAngle(lastOppositeTopSignal, redstonePower);
        }
        float requested = getBaseTargetAngleForSignal(redstonePower) * getAngleMultiplierForModeSignal(lastOppositeTopSignal);
        return Mth.clamp(requested, 0.0F, 360.0F);
    }

    private float getBinaryTargetAngleForMode(int modeSignal, int angleSignal) {
        if (isExtendedTargetModeSignal(modeSignal)) {
            return computeExtendedTargetAngle(modeSignal, angleSignal);
        }
        float magnitude = Math.abs(getBaseTargetAngleForSignal(angleSignal));
        if (modeSignal == 2) {
            return -magnitude;
        }
        if (modeSignal == 1 || modeSignal >= 3) {
            return magnitude;
        }
        return angle;
    }

    private float getDegreesPerTickForSignal(int redstonePower) {
        return ServoRedstoneMappings.effectiveSpeedDegreesPerTickFromSignal(redstonePower, speedZeroMovementEnabled);
    }

    private float computeExtendedTargetAngle(int modeSignal, int angleSignal) {
        return clampServoTargetDegrees(-computeServoEquivalentExtendedTargetAngle(modeSignal, angleSignal));
    }

    private float computeServoEquivalentExtendedTargetAngle(int modeSignal, int angleSignal) {
        int clampedAngle = ServoRedstoneMappings.clampSignal(angleSignal);
        if (clampedAngle <= 0) {
            return 0.0F;
        }

        return switch (modeSignal) {
            case MODE_3_STEP -> {
                if (clampedAngle <= 5) {
                    yield 0.0F;
                }
                yield clampedAngle <= 10 ? 120.0F : 240.0F;
            }
            case MODE_ABSOLUTE_0_540 -> clampedAngle * 36.0F;
            case MODE_CENTERED -> (clampedAngle - 8) * 36.0F;
            case MODE_FINE_0_180 -> clampedAngle * 12.0F;
            case MODE_FINE_CENTERED -> (clampedAngle - 8) * 12.0F;
            case MODE_FLIP -> clampedAngle * 36.0F;
            case MODE_INVERTED_FLIP -> -(clampedAngle * 36.0F);
            default -> -angle;
        };
    }

    private float computeMode6OscillationTarget(boolean directInputPriority) {
        int angleSignal = directInputPriority ? lastEastSignal : binaryAngleSignal;
        int speedSignal = directInputPriority ? lastWestSignal : binarySpeedSignal;
        float amplitude = 90.0F + (ServoRedstoneMappings.clampSignal(angleSignal) * 10.0F);
        return -computePositiveOscillationTarget(amplitude, amplitude, speedSignal);
    }

    private float computeExtendedOscillationTarget(int modeSignal, boolean directInputPriority) {
        int angleSignal = directInputPriority ? lastEastSignal : binaryAngleSignal;
        int speedSignal = directInputPriority ? lastWestSignal : binarySpeedSignal;
        int clampedAngle = ServoRedstoneMappings.clampSignal(angleSignal);
        if (clampedAngle <= 0) {
            return 0.0F;
        }

        if (modeSignal == MODE_OSCILLATION_0_ANGLE) {
            float amplitude = Mth.clamp(clampedAngle * 36.0F, 0.0F, MAX_EXTENDED_MODE_DEGREES);
            return -computePositiveOscillationTarget(amplitude, amplitude, speedSignal);
        }
        if (modeSignal == MODE_CENTERED_OSCILLATION) {
            float halfRange = Mth.clamp(clampedAngle * 18.0F, 0.0F, MODE15_MAX_HALF_RANGE_DEGREES);
            return -computeCenteredOscillationTarget(halfRange, halfRange * 2.0F, speedSignal);
        }
        return angle;
    }

    private float computePositiveOscillationTarget(float amplitude, float spanDegrees, int speedSignal) {
        if (amplitude <= 0.0F || spanDegrees <= 0.0F) {
            return 0.0F;
        }
        if (!advanceOscillationPhase(spanDegrees, speedSignal)) {
            return Math.abs(angle);
        }

        double normalized = (1.0D - Math.cos(mode6OscillationPhase)) * 0.5D;
        return Mth.clamp((float) (normalized * amplitude), 0.0F, MAX_EXTENDED_MODE_DEGREES);
    }

    private float computeCenteredOscillationTarget(float halfRange, float spanDegrees, int speedSignal) {
        if (halfRange <= 0.0F || spanDegrees <= 0.0F) {
            return 0.0F;
        }
        if (!advanceOscillationPhase(spanDegrees, speedSignal)) {
            return -angle;
        }

        return clampServoTargetDegrees((float) (Math.sin(mode6OscillationPhase) * halfRange));
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

    private static float computeDisassembleReturnStepDegrees(float currentAngle, float requestedStep) {
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

        return Math.min(remaining, Math.max(MIN_DISASSEMBLE_DEGREES_PER_TICK, limitedStep));
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
        if (state.hasProperty(InvServoTwisterBlock.RUNNING)
                && state.getValue(InvServoTwisterBlock.RUNNING) != runningVisual) {
            level.setBlock(worldPosition, state.setValue(InvServoTwisterBlock.RUNNING, runningVisual), 3);
        }
    }

    private InvServoTwisterBlock.PowerVisualState computePowerVisualState() {
        if (shouldBlinkForMissingBinaryReceivePath()) {
            return isBinaryWaitBlinkOn()
                    ? InvServoTwisterBlock.PowerVisualState.BI
                    : InvServoTwisterBlock.PowerVisualState.UNPOWERED;
        }

        if (isInternalRedstoneLinkMode()) {
            return isBinaryVisualInputHigh()
                    ? InvServoTwisterBlock.PowerVisualState.BI
                    : InvServoTwisterBlock.PowerVisualState.UNPOWERED;
        }

        boolean angleInputActive = lastEastSignal > 0;
        boolean speedInputActive = lastWestSignal > 0;

        if (angleInputActive && speedInputActive)
            return InvServoTwisterBlock.PowerVisualState.BI;
        if (angleInputActive)
            return InvServoTwisterBlock.PowerVisualState.ANGLE;
        if (speedInputActive)
            return InvServoTwisterBlock.PowerVisualState.SPEED;
        return InvServoTwisterBlock.PowerVisualState.UNPOWERED;
    }

    private boolean isBinaryVisualInputHigh() {
        if (internalRedstoneLinkActive)
            return internalRedstoneLinkReceivedSignal > 0;

        return binaryVisualInputHigh;
    }

    private boolean shouldBlinkForMissingBinaryReceivePath() {
        return TwisterMillConfig.isInvServoTwisterBinaryInputEnabled()
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
        if (!state.hasProperty(InvServoTwisterBlock.POWER_VISUAL))
            return;

        InvServoTwisterBlock.PowerVisualState next = computePowerVisualState();
        if (state.getValue(InvServoTwisterBlock.POWER_VISUAL) != next) {
            level.setBlock(worldPosition, state.setValue(InvServoTwisterBlock.POWER_VISUAL, next), 3);
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
                InternalServoRedstoneLinkSlots.makeSlots(true),
                this::setInternalRedstoneLinkSignal
        );
        behaviours.add(internalRedstoneLink);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

        prevAngle = angle;

        if (level.isClientSide)
            return;

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

        if (sableBackend.isActive()) {
            boolean refreshed = level instanceof ServerLevel serverLevel
                    && sableBackend.refresh(serverLevel, worldPosition, getFacingDirection());
            if (refreshed) {
                logSableLifecycleDiagnostics("refresh-success");
            } else {
                logSableLifecycleDiagnostics("refresh-failure");
                running = false;
                pendingDisassembleAfterZero = false;
                pendingDisassembleZeroHoldTicks = 0;
                mode3ExitReturnActive = false;
                sableBackend.clearState();
                setChanged();
                if (canSendData()) sendData();
            }
        }

        if (assembleNextTick) {
            assembleNextTick = false;
            if (running) {
                pendingDisassembleAfterZero = true;
                pendingDisassembleZeroHoldTicks = 0;
                mode3ExitReturnActive = false;
                targetAngle = 0.0F;
                stopMotion();
            } else {
                assemble();
            }
        }

        int previousModeSignal = lastOppositeTopSignal;
        updateControlSignalsFromInputs(true);
        updateVisualPowerState();
        boolean directInputPriority = hasDirectInputPriority(lastWestSignal, lastEastSignal);
        boolean freeBearingMode = lastOppositeTopSignal == 3;
        boolean worldLockedMode = isWorldLockedModeSignal(lastOppositeTopSignal);
        boolean oscillationMode = isOscillationModeSignal(lastOppositeTopSignal);
        if (oscillationMode && previousModeSignal != lastOppositeTopSignal) {
            mode6OscillationPhase = 0.0D;
        }
        boolean unboundedExitToPositioningMode = isUnboundedModeSignal(previousModeSignal)
                && isBoundedPositioningModeSignal(lastOppositeTopSignal)
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

        if (running) {
            boolean skipFinalSableApplyRotation = false;

            if (pendingDisassembleAfterZero) {
                float requestedStep = Math.max(
                        getDegreesPerTickForSignal(lastWestSignal),
                        MIN_DISASSEMBLE_DEGREES_PER_TICK
                );
                targetAngle = 0.0F;

                if (Math.abs(angle) <= DISASSEMBLE_ZERO_SNAP_DEGREES) {
                    boolean changed = Math.abs(angle) > ANGLE_EPSILON;
                    angle = 0.0F;
                    applyRotation();
                    pendingDisassembleZeroHoldTicks++;

                    if (changed) {
                        setChanged();
                        if (canSendData()) sendData();
                    }

                    if (pendingDisassembleZeroHoldTicks >= DISASSEMBLE_ZERO_HOLD_TICKS) {
                        pendingDisassembleAfterZero = false;
                        pendingDisassembleZeroHoldTicks = 0;
                        disassemble();
                    }
                } else {
                    pendingDisassembleZeroHoldTicks = 0;
                    float step = computeDisassembleReturnStepDegrees(angle, requestedStep);
                    float newAngle = approachAngle(angle, 0.0F, step);

                    if (Math.abs(newAngle - angle) > ANGLE_EPSILON) {
                        angle = newAngle;
                        applyRotation();
                        setChanged();
                        if (canSendData()) sendData();
                    }
                }
                skipFinalSableApplyRotation = true;
            } else if (mode3ExitReturnActive) {
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
            //noinspection StatementWithEmptyBody
            } else if (freeBearingMode) {
                // Intentionally empty: free bearing mode suppresses direct-input fallback.
            } else if (worldLockedMode) {
                if (!applyWorldLockedMotor(lastOppositeTopSignal)) {
                    applyFreeBearingNeutralMotor();
                }
                skipFinalSableApplyRotation = true;
            } else if (oscillationMode) {
                applyMode6Oscillation(directInputPriority);
                skipFinalSableApplyRotation = true;
            } else if (directInputPriority) {
                if (lastOppositeTopSignal == 1) {
                    angle += SOUTH_CONTINUOUS_DEGREES_PER_TICK;
                    applyRotation();
                    setChanged();
                    if (canSendData()) sendData();
                } else if (lastOppositeTopSignal == 2) {
                    angle -= SOUTH_CONTINUOUS_DEGREES_PER_TICK;
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

            if (sableBackend.isActive() && !skipFinalSableApplyRotation) {
                if (freeBearingMode && !pendingDisassembleAfterZero) {
                    applyFreeBearingNeutralMotor();
                } else if (worldLockedMode && !pendingDisassembleAfterZero) {
                    if (!applyWorldLockedMotor(lastOppositeTopSignal)) {
                        applyFreeBearingNeutralMotor();
                    }
                } else {
                    applyRotation();
                }
            }

        }

        boolean visualRunning =
                running && (manualEnabled || lastEastSignal > 0 || lastOppositeTopSignal > 0 || Math.abs(angle) > ANGLE_EPSILON);

        updateVisualRunning(visualRunning);
    }

    private void refreshLiveContraptionBlockCountIfNeeded(long time) {
        if (time < nextSableBlockCountRefreshAt) {
            return;
        }

        nextSableBlockCountRefreshAt = time + WIND_ROTO_RUNTIME_SYNC_TICKS;

        int measured = 0;
        if (sableBackend.isActive() && level instanceof ServerLevel serverLevel) {
            measured = Math.max(0, sableBackend.countBlocks(serverLevel));
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
                    true,
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
        if (level != null && !level.isClientSide)
            notifyBoundWindRotoRemoved();

        if (level != null && !level.isClientSide)
            sableBackend.clearRuntimeForUnload();

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
                    true
            );
        }
    }

    @Override
    public void onChunkUnloaded() {
        sableBackend.clearRuntimeForUnload();
        super.onChunkUnloaded();
    }

    public void assemble() {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        if (running)
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

        Direction facing = getFacingDirection();
        SableInteractiveContraptionBackend.AssemblyResult assembly = sableBackend.tryAssemble(
                serverLevel,
                worldPosition,
                facing,
                false,
                this::handleAssemblyException,
                RememberedSableShipMemory.enabledFor(getBlockState(), rememberedShipMemory)
        );
        if (assembly == null)
            return false;

        finishSuccessfulSableAssembly(assembly);
        return true;
    }

    private void handleAssemblyException(@Nullable AssemblyException exception) {
        lastException = exception;
        if (exception != null && canSendData()) {
            sendData();
        }
    }

    private void finishSuccessfulSableAssembly(SableInteractiveContraptionBackend.AssemblyResult assembly) {
        lastException = null;

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

        running = true;
        pendingDisassembleAfterZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        angle = 0.0F;
        prevAngle = 0.0F;
        assembledBlockCount = assembly.blockCount();
        assembleNextTick = false;
        applyRotation();
        if (canSendData()) sendData();
    }

    public void disassemble() {
        if (!running && !sableBackend.isActive())
            return;

        if (!(level instanceof ServerLevel serverLevel)) {
            running = false;
            pendingDisassembleAfterZero = false;
            pendingDisassembleZeroHoldTicks = 0;
            angle = 0.0F;
            prevAngle = 0.0F;
            assembleNextTick = false;
            assembledBlockCount = 0;
            sableBackend.clearClientFallback();
            updateVisualRunning(false);
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        pendingDisassembleAfterZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        angle = 0.0F;
        prevAngle = 0.0F;
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
        updateVisualRunning(false);
        setChanged();
        if (canSendData()) sendData();
    }

    private void applyRotation() {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        if (!sableBackend.isActive())
            return;

        boolean applied = sableBackend.applyMotor(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                angle,
                SERVO_STIFFNESS_PER_INERTIA,
                SERVO_DAMPING_PER_INERTIA,
                MIN_EFFECTIVE_INERTIA
        );
        if (!applied) {
            running = false;
            pendingDisassembleAfterZero = false;
            pendingDisassembleZeroHoldTicks = 0;
            sableBackend.clearState();
            setChanged();
            if (canSendData()) sendData();
        }
    }

    public TwisterMillReseatService.ReseatResult reseatFromDiagnostics(TwisterMillReseatService.Trigger trigger) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return TwisterMillReseatService.ReseatResult.failed(
                    TwisterMillReseatService.TargetType.INV_SERVO,
                    worldPosition,
                    "not-server-level"
            );
        }

        float visualAngleBefore = getInterpolatedAngle(0.0F);
        pendingDisassembleAfterZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        angle = 0.0F;
        prevAngle = 0.0F;
        targetAngle = 0.0F;
        updateVisualRunning(false);

        SableInteractiveContraptionBackend.ReloadStabilizationResult result =
                sableBackend.reseatAttachedSubLevel(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        SERVO_STIFFNESS_PER_INERTIA,
                        SERVO_DAMPING_PER_INERTIA,
                        MIN_EFFECTIVE_INERTIA,
                        trigger.actionPrefix()
                );
        if (result.poseReseatApplied()) {
            setChanged();
            if (canSendData()) sendData();
        }
        float visualAngleAfter = getInterpolatedAngle(0.0F);
        return new TwisterMillReseatService.ReseatResult(
                TwisterMillReseatService.TargetType.INV_SERVO,
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

    private void applyMode6Oscillation(boolean directInputPriority) {
        float nextAngle = isExtendedOscillationModeSignal(lastOppositeTopSignal)
                ? computeExtendedOscillationTarget(lastOppositeTopSignal, directInputPriority)
                : computeMode6OscillationTarget(directInputPriority);
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

    private boolean applyWorldLockedMotor(int modeSignal) {
        if (!(level instanceof ServerLevel serverLevel))
            return false;

        if (!sableBackend.isActive())
            return false;

        Float targetAngle = sableBackend.computeWorldLockedMotorAngleDegrees(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                modeSignal,
                angle
        );
        if (targetAngle == null) {
            return false;
        }

        boolean changed = Math.abs(targetAngle - angle) > ANGLE_EPSILON;
        angle = targetAngle;

        boolean applied = sableBackend.applyMotor(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                angle,
                SERVO_STIFFNESS_PER_INERTIA,
                SERVO_DAMPING_PER_INERTIA,
                MIN_EFFECTIVE_INERTIA
        );
        if (!applied) {
            running = false;
            pendingDisassembleAfterZero = false;
            pendingDisassembleZeroHoldTicks = 0;
            sableBackend.clearState();
            setChanged();
            if (canSendData()) sendData();
            return false;
        }

        if (changed) {
            setChanged();
            if (canSendData()) sendData();
        }
        return true;
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
                FREE_BEARING_DAMPING_PER_INERTIA,
                MIN_EFFECTIVE_INERTIA
        );
        if (!applied) {
            running = false;
            pendingDisassembleAfterZero = false;
            pendingDisassembleZeroHoldTicks = 0;
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
        if (freeBearingVisualAngle != null) {
            return freeBearingVisualAngle;
        }
        return Mth.lerp(partialTicks, prevAngle, angle);
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
        if (level == null || !running || pendingDisassembleAfterZero || lastOppositeTopSignal != 3 || !sableBackend.isActive()) {
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
        tag.putInt(TAG_ASSEMBLED_BLOCK_COUNT, assembledBlockCount);
        tag.putBoolean(TAG_BOUND_TO_WIND_ROTO, boundToWindRoto);
        tag.putBoolean(TAG_PENDING_DISASSEMBLE_AFTER_ZERO, pendingDisassembleAfterZero);
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
        if (clientPacket) {
            tag.putBoolean(TAG_SPEED_ZERO_MOVEMENT_ENABLED, speedZeroMovementEnabled);
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
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        manualEnabled = tag.getBoolean(TAG_MANUAL_ENABLED);
        running = tag.getBoolean(TAG_RUNNING);
        assembleNextTick = tag.getBoolean(TAG_ASSEMBLE_NEXT_TICK);
        angle = tag.getFloat(TAG_ANGLE);
        prevAngle = tag.contains(TAG_PREV_ANGLE) ? tag.getFloat(TAG_PREV_ANGLE) : angle;
        assembledBlockCount = tag.getInt(TAG_ASSEMBLED_BLOCK_COUNT);
        if (tag.contains(TAG_BOUND_TO_WIND_ROTO))
            boundToWindRoto = tag.getBoolean(TAG_BOUND_TO_WIND_ROTO);
        if (tag.contains(TAG_PENDING_DISASSEMBLE_AFTER_ZERO))
            pendingDisassembleAfterZero = tag.getBoolean(TAG_PENDING_DISASSEMBLE_AFTER_ZERO);
        pendingDisassembleZeroHoldTicks = 0;

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
        if (!clientPacket) {
            rememberedShipMemory.read(tag);
        }
        diagnosticFirstRefreshLogged = false;
        diagnosticRefreshFailureLogged = false;
        if (!clientPacket) {
            logSableLifecycleDiagnostics("read");
        }

        needsStateRefresh = true;
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

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean details = AllKeys.ctrlDown();
        boolean internalLinkMode = isInternalRedstoneLinkMode();
        int liveWestSignal = internalLinkMode ? 0 : getWestSpeedSignal();
        int liveEastSignal = internalLinkMode ? 0 : getEastAngleSignal();
        boolean binaryConfigEnabled = TwisterMillConfig.isInvServoTwisterBinaryInputEnabled();
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

        CreateLang.number(Math.abs(angle))
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
