package com.proventure.twistermill.blockentity;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import com.proventure.twistermill.block.custom.WindRotoVerticalBlock;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.proventure.twistermill.diagnostics.TwisterMillReseatService;
import com.proventure.twistermill.weather.TwisterWeatherService;
import com.proventure.twistermill.weather.WindSample;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.slf4j.Logger;

public class WindRotoVerticalBlockEntity extends MechanicalBearingBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_SABLE_ACTIVE = "SableActive";
    private static final String TAG_SABLE_SUBLEVEL_ID = "SableSubLevelId";
    private static final String TAG_PENDING_DISASSEMBLE_AFTER_ZERO = "PendingDisassembleAfterZero";
    private static final String TAG_DISASSEMBLY_RETURN_MEASURED_ANGLE = "DisassemblyReturnMeasuredAngle";
    private static final String TAG_ASSEMBLED_BLOCK_COUNT = "AssembledBlockCount";

    private static final float DEGREES_PER_TICK_AT_1_RPM = 0.3F;
    private static final int[] RELOAD_REATTACH_DIAGNOSTIC_SNAPSHOT_TICKS = {0, 1, 5, 20, 60};
    private static final byte MARKER_STATUS_MISSING = 0;
    private static final byte MARKER_STATUS_FOUND = 1;
    private static final byte MARKER_STATUS_WRONG_BLOCK = 2;

    public enum TwisterRotationMode implements INamedIconOptions {
        FREE_RS_ON(AllIcons.I_ROTATE_NEVER_PLACE, "create.contraptions.movement_mode.rotate_never_place"),
        RETURNED(AllIcons.I_ROTATE_PLACE_RETURNED, "create.contraptions.movement_mode.rotate_place_returned"),
        PLACE(AllIcons.I_ROTATE_PLACE, "create.contraptions.movement_mode.rotate_place");

        private final AllIcons icon;
        private final String translationKey;

        TwisterRotationMode(AllIcons icon, String translationKey) {
            this.icon = icon;
            this.translationKey = translationKey;
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

    protected ScrollOptionBehaviour<TwisterRotationMode> assemblyMode;

    private boolean verticalManualEnabled = false;
    private boolean restoreFreeModeAfterManualDisassembly = false;

    private boolean placementNorthValid = false;
    private int placementNorthDirData = -1;
    private byte placementNorthMarkerStatus = MARKER_STATUS_MISSING;
    private boolean blockOnBearingForDisplay = false;
    private int assembledBlockCount = 0;
    private long nextPlacementStatusRefreshAt = 0;

    private float verticalCurrentYawDeg = 0.0F;
    private float verticalTargetYawDeg = 0.0F;
    private float verticalYawVelocityDegPerTick = 0.0F;
    private boolean verticalYawMoving = false;

    private boolean verticalParkedMode = false;
    private boolean verticalAutoParkedByMissingMarker = false;
    private boolean verticalPulsePowered = false;
    private long verticalPulseStartTick = -1;
    private long verticalPulseCooldownUntil = 0;

    private int generatedRpm = 0;
    private float generatedSpeedRpm = 0.0F;
    private float generatedSu = 0.0F;
    private boolean lastVisualRunning = false;

    private float lastWorldWindAngleDeg = 0.0F;
    private float lastLocalTargetYawDeg = 0.0F;
    private long nextWindAngleSampleAt = 0;
    private boolean pendingDisassembleAfterZero = false;
    private int pendingDisassembleStableTicks = 0;
    private transient float disassemblyReturnMeasuredAngleDeg = 0.0F;
    private transient int sableLoadRecoveryTicks = 0;
    private transient int sableReloadReattachGraceTicks = 0;
    private transient SableInteractiveContraptionBackend.RefreshFailureReason lastSableRefreshFailureReason =
            SableInteractiveContraptionBackend.RefreshFailureReason.NONE;
    private transient long lastSableRefreshFailureLogTick = Long.MIN_VALUE;
    private transient boolean diagnosticFirstRefreshLogged = false;
    private transient boolean diagnosticRefreshFailureLogged = false;
    private transient boolean sableReloadVelocityStabilizedThisLoad = false;
    private transient int reloadReattachDiagnosticAgeTicks = -1;
    private transient int reloadReattachDiagnosticNextSnapshotIndex = 0;
    private transient long reloadReattachDiagnosticStartGameTime = Long.MIN_VALUE;

    private final SableInteractiveContraptionBackend sableBackend = new SableInteractiveContraptionBackend(TwisterMillDiagnostics.Target.WRVB);
    private final RememberedSableShipMemory rememberedShipMemory = new RememberedSableShipMemory();
    private boolean skipDisassembleDuringRemove = false;

    public WindRotoVerticalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_ROTO_VERTICAL_BE.get(), pos, state);
        forceRotationModeNeverPlace();
    }

    private boolean canSendData() {
        if (!(level instanceof ServerLevel serverLevel))
            return false;
        return serverLevel.getServer().isRunning();
    }

    private void logSableLifecycleDiagnostics(String event) {
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
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

        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event={} pos={} gameTime={} facing={} running={} sableActive={} activeSubLevelId={} assembleNextTick={} recoveryTicks={} recoveryWindow={} reloadGraceTicks={} reloadGraceWindow={} lastRefreshFailureReason={} verticalManualEnabled={} pendingDisassembleAfterZero={}",
                event,
                worldPosition,
                level == null ? -1L : level.getGameTime(),
                getFacingDirection(),
                running,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                assembleNextTick,
                sableLoadRecoveryTicks,
                getSableLoadRecoveryTicks(),
                sableReloadReattachGraceTicks,
                getSableReloadReattachGraceTicks(),
                lastSableRefreshFailureReason,
                verticalManualEnabled,
                pendingDisassembleAfterZero);
    }

    private void logSableReadNbtDiagnostics(String event, CompoundTag tag) {
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            return;
        }

        boolean containsSableActive = tag.contains(TAG_SABLE_ACTIVE);
        Boolean rawSableActive = containsSableActive ? tag.getBoolean(TAG_SABLE_ACTIVE) : null;
        boolean hasSableSubLevelId = tag.hasUUID(TAG_SABLE_SUBLEVEL_ID);
        UUID rawSableSubLevelId = hasSableSubLevelId ? tag.getUUID(TAG_SABLE_SUBLEVEL_ID) : null;
        ChunkPos chunkPos = new ChunkPos(worldPosition);

        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event={} dimension={} pos={} chunk=[{}, {}] gameTime={} clientPacket={} running={} facing={} rawContainsSableActive={} rawSableActive={} rawHasSableSubLevelId={} rawSableSubLevelId={} backendActive={} backendActiveSubLevelId={}",
                event,
                level == null ? null : level.dimension().location(),
                worldPosition,
                chunkPos.x,
                chunkPos.z,
                level == null ? -1L : level.getGameTime(),
                false,
                running,
                getFacingDirection(),
                containsSableActive,
                rawSableActive,
                hasSableSubLevelId,
                rawSableSubLevelId,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId());
    }

    private void logSableWriteNbtDiagnostics(String event, CompoundTag tag, boolean clientPacket) {
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            return;
        }

        boolean containsSableActive = tag.contains(TAG_SABLE_ACTIVE);
        Boolean rawSableActive = containsSableActive ? tag.getBoolean(TAG_SABLE_ACTIVE) : null;
        boolean hasSableSubLevelId = tag.hasUUID(TAG_SABLE_SUBLEVEL_ID);
        UUID rawSableSubLevelId = hasSableSubLevelId ? tag.getUUID(TAG_SABLE_SUBLEVEL_ID) : null;
        ChunkPos chunkPos = new ChunkPos(worldPosition);

        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event={} dimension={} pos={} chunk=[{}, {}] gameTime={} clientPacket={} running={} facing={} backendActive={} backendActiveSubLevelId={} tagContainsSableActive={} tagSableActive={} tagHasSableSubLevelId={} tagSableSubLevelId={}",
                event,
                level == null ? null : level.dimension().location(),
                worldPosition,
                chunkPos.x,
                chunkPos.z,
                level == null ? -1L : level.getGameTime(),
                clientPacket,
                running,
                getFacingDirection(),
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                containsSableActive,
                rawSableActive,
                hasSableSubLevelId,
                rawSableSubLevelId);
    }

    private void logSableClearCauseDiagnostics(
            String event,
            String reason,
            boolean runningBefore,
            Direction facingBefore,
            @Nullable UUID activeSubLevelIdBefore,
            int recoveryTicksBefore,
            boolean assembleNextTickBefore,
            boolean pendingDisassembleAfterZeroBefore,
            int pendingDisassembleStableTicksBefore) {
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(worldPosition);

        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event={} reason={} dimension={} pos={} chunk=[{}, {}] gameTime={} runningBefore={} facingBefore={} activeSubLevelIdBefore={} recoveryTicksBefore={} recoveryWindow={} reloadGraceTicks={} reloadGraceWindow={} lastRefreshFailureReason={} assembleNextTickBefore={} pendingDisassembleAfterZeroBefore={} pendingDisassembleStableTicksBefore={} backendActive={} backendActiveSubLevelId={} persistentLinkAction=cleared",
                event,
                reason,
                level == null ? null : level.dimension().location(),
                worldPosition,
                chunkPos.x,
                chunkPos.z,
                level == null ? -1L : level.getGameTime(),
                runningBefore,
                facingBefore,
                activeSubLevelIdBefore,
                recoveryTicksBefore,
                getSableLoadRecoveryTicks(),
                sableReloadReattachGraceTicks,
                getSableReloadReattachGraceTicks(),
                lastSableRefreshFailureReason,
                assembleNextTickBefore,
                pendingDisassembleAfterZeroBefore,
                pendingDisassembleStableTicksBefore,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId());
    }

    private void logManualEmptyHandResetBeforeClear() {
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(worldPosition);
        Boolean blockStateRunning = null;
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(WindRotoVerticalBlock.RUNNING)) {
                blockStateRunning = state.getValue(WindRotoVerticalBlock.RUNNING);
            }
        }

        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event=manual-empty-hand-reset-before-clear dimension={} pos={} chunk=[{}, {}] gameTime={} facing={} running={} assembleNextTick={} verticalManualEnabled={} restoreFreeModeAfterManualDisassembly={} pendingDisassembleAfterZero={} pendingDisassembleStableTicks={} recoveryTicks={} recoveryWindow={} angle={} interpolatedAngle0={} blockStateRunning={} lastVisualRunning={} backendActive={} backendActiveSubLevelId={} hasBlockOnBearingTop={} blockOnBearingForDisplay={} assembledBlockCount={} generatedRpm={} generatedSpeedRpm={} generatedSu={} verticalCurrentYawDeg={} verticalTargetYawDeg={} verticalYawVelocityDegPerTick={} verticalYawMoving={}",
                level == null ? null : level.dimension().location(),
                worldPosition,
                chunkPos.x,
                chunkPos.z,
                level == null ? -1L : level.getGameTime(),
                getFacingDirection(),
                running,
                assembleNextTick,
                verticalManualEnabled,
                restoreFreeModeAfterManualDisassembly,
                pendingDisassembleAfterZero,
                pendingDisassembleStableTicks,
                sableLoadRecoveryTicks,
                getSableLoadRecoveryTicks(),
                angle,
                getInterpolatedAngle(0.0F),
                blockStateRunning,
                lastVisualRunning,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                hasBlockOnBearingTop(),
                blockOnBearingForDisplay,
                assembledBlockCount,
                generatedRpm,
                generatedSpeedRpm,
                generatedSu,
                verticalCurrentYawDeg,
                verticalTargetYawDeg,
                verticalYawVelocityDegPerTick,
                verticalYawMoving);
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

    @Nullable
    public UUID getActiveTopSubLevelIdForRender() {
        return sableBackend.getActiveSubLevelId();
    }

    private int getContraptionBlockCountForDisplay() {
        return Math.max(0, assembledBlockCount);
    }

    private int measureCurrentSableContraptionBlockCount(ServerLevel serverLevel) {
        UUID activeSubLevelId = sableBackend.getActiveSubLevelId();
        if (activeSubLevelId == null) {
            return 0;
        }

        return Math.max(0, WindRotoChildShipSailCounter.countBlocksRecursive(serverLevel, activeSubLevelId, state -> false)
                .totalBlocks());
    }

    private boolean updateAssembledBlockCountFromSable(ServerLevel serverLevel) {
        if (sableBackend.getActiveSubLevelId() == null) {
            return false;
        }

        int measured = measureCurrentSableContraptionBlockCount(serverLevel);
        if (measured == assembledBlockCount) {
            return false;
        }

        assembledBlockCount = measured;
        return true;
    }

    private void clearAssembledBlockCount() {
        assembledBlockCount = 0;
    }

    public boolean tryManualEmptyHandLostStateReset(Player player) {
        if (player == null || !isManualEmptyHandLostStateResetAllowed()) {
            return false;
        }

        logManualEmptyHandResetBeforeClear();

        running = false;
        movedContraption = null;
        assembleNextTick = false;
        verticalManualEnabled = false;
        restoreFreeModeAfterManualDisassembly = false;
        pendingDisassembleAfterZero = false;
        pendingDisassembleStableTicks = 0;
        disassemblyReturnMeasuredAngleDeg = 0.0F;
        sableLoadRecoveryTicks = 0;
        verticalAutoParkedByMissingMarker = false;

        setAngle(0.0F);
        sequencedAngleLimit = -1.0;

        sableBackend.clearState();
        clearAssembledBlockCount();
        stopAllMotionState();
        updateGeneratedRotation();

        verticalCurrentYawDeg = 0.0F;
        verticalTargetYawDeg = 0.0F;
        lastWorldWindAngleDeg = 0.0F;
        lastLocalTargetYawDeg = 0.0F;
        nextWindAngleSampleAt = 0;
        blockOnBearingForDisplay = false;

        forceVisualRunningOff();
        setChanged();
        if (canSendData()) sendData();
        return true;
    }

    private boolean isManualEmptyHandLostStateResetAllowed() {
        if (level == null || level.isClientSide) {
            return false;
        }
        if (running) {
            return false;
        }
        if (sableBackend.isActive() || sableBackend.getActiveSubLevelId() != null) {
            return false;
        }
        return !hasBlockOnBearingTop();
    }

    public void onPlayerToggle(Player player) {
        if (player == null) {
            return;
        }

        if (level == null || level.isClientSide) {
            return;
        }

        if (running || assembleNextTick || verticalManualEnabled) {
            performManualDisassembly();
            return;
        }

        if (isVerticalDisarmed()) {
            verticalManualEnabled = false;
            stopAllMotionState();
            updateVisualRunning(false);
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        verticalManualEnabled = true;
        queueAssemble();
    }

    @SuppressWarnings("unused")
    public void setPlacementNorth(Direction direction) {
        if (direction == null || direction.getAxis().isVertical()) {
            return;
        }

        placementNorthValid = true;
        placementNorthDirData = direction.get2DDataValue();
        placementNorthMarkerStatus = MARKER_STATUS_FOUND;
        setChanged();
        if (canSendData()) sendData();
    }

    private boolean refreshPlacementNorthFromMarker() {
        Direction detected = detectPlacementNorthFromMarker();

        if (detected != null) {
            boolean changed = !placementNorthValid
                    || placementNorthDirData != detected.get2DDataValue()
                    || placementNorthMarkerStatus != MARKER_STATUS_FOUND;
            placementNorthValid = true;
            placementNorthDirData = detected.get2DDataValue();
            placementNorthMarkerStatus = MARKER_STATUS_FOUND;

            if (changed) {
                setChanged();
            }

            return true;
        }

        byte detectedStatus = detectExpectedMarkerFailureStatus();
        boolean changed = placementNorthValid || placementNorthMarkerStatus != detectedStatus;
        placementNorthValid = false;
        placementNorthMarkerStatus = detectedStatus;

        if (changed) {
            setChanged();
        }

        return false;
    }

    private boolean refreshPlacementStatusForTooltipAndPark(long gameTime, boolean force) {
        if (level == null || level.isClientSide) {
            return placementNorthValid;
        }

        boolean assembledActive = running || sableBackend.isActive();
        int interval = assembledActive
                ? getPlacementStatusRefreshTicksAssembled()
                : getPlacementStatusRefreshTicksDisassembled();
        if (!force && gameTime < nextPlacementStatusRefreshAt) {
            return placementNorthValid;
        }
        nextPlacementStatusRefreshAt = gameTime + interval;

        boolean previousMarker = placementNorthValid;
        byte previousMarkerStatus = placementNorthMarkerStatus;
        int previousMarkerDir = placementNorthDirData;
        boolean previousBlockOnBearing = blockOnBearingForDisplay;
        boolean previousParked = verticalParkedMode;
        boolean previousAutoParked = verticalAutoParkedByMissingMarker;

        boolean hasMarker = refreshPlacementNorthFromMarker();
        blockOnBearingForDisplay = hasBlockOnBearingTop();

        if (assembledActive) {
            if (!hasMarker) {
                if (!verticalParkedMode) {
                    verticalParkedMode = true;
                    verticalAutoParkedByMissingMarker = true;
                }
            } else if (verticalAutoParkedByMissingMarker) {
                verticalParkedMode = false;
                verticalAutoParkedByMissingMarker = false;
            }
        } else if (!verticalParkedMode) {
            verticalAutoParkedByMissingMarker = false;
        }

        boolean changed = previousMarker != placementNorthValid
                || previousMarkerStatus != placementNorthMarkerStatus
                || previousMarkerDir != placementNorthDirData
                || previousBlockOnBearing != blockOnBearingForDisplay
                || previousParked != verticalParkedMode
                || previousAutoParked != verticalAutoParkedByMissingMarker;

        if (changed) {
            setChanged();
            if (canSendData()) sendData();
        }

        return hasMarker;
    }

    private BlockPos getBearingTopPos() {
        return worldPosition.relative(getFacingDirection());
    }

    private boolean hasBlockOnBearingTop() {
        if (level == null) {
            return false;
        }
        return !level.getBlockState(getBearingTopPos()).isAir();
    }

    private static byte normalizeMarkerStatus(byte status) {
        return switch (status) {
            case MARKER_STATUS_FOUND, MARKER_STATUS_WRONG_BLOCK -> status;
            default -> MARKER_STATUS_MISSING;
        };
    }

    private byte detectExpectedMarkerFailureStatus() {
        if (level == null) {
            return MARKER_STATUS_MISSING;
        }

        Direction expected = placementNorthDirData >= 0
                ? Direction.from2DDataValue(placementNorthDirData)
                : Direction.NORTH;
        BlockState expectedState = level.getBlockState(worldPosition.relative(expected));
        return expectedState.isAir() ? MARKER_STATUS_MISSING : MARKER_STATUS_WRONG_BLOCK;
    }

    @Nullable
    private Direction detectPlacementNorthFromMarker() {
        if (level == null) {
            return null;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos checkPos = worldPosition.relative(direction);
            BlockState checkState = level.getBlockState(checkPos);
            if (checkState.is(Blocks.SMOOTH_STONE_SLAB)) {
                return direction;
            }
        }

        return null;
    }

    private TwisterRotationMode getSelectedAssemblyMode() {
        return TwisterRotationMode.FREE_RS_ON;
    }

    private boolean isMoveNeverPlaceModeSelected() {
        return getSelectedAssemblyMode() == TwisterRotationMode.FREE_RS_ON;
    }

    private boolean isVerticalDisarmed() {
        return !isMoveNeverPlaceModeSelected();
    }

    private boolean isVerticalControlEnabled() {
        return verticalManualEnabled;
    }

    private void applySelectedRotationMode() {
        forceRotationModeNeverPlace();
    }

    private void forceRotationModeNeverPlace() {
        if (movementMode == null) {
            return;
        }
        movementMode.setValue(IControlContraption.RotationMode.ROTATE_NEVER_PLACE.ordinal());
    }

    private void setSelectedAssemblyMode(TwisterRotationMode mode) {
        if (assemblyMode != null) {
            assemblyMode.setValue(mode.ordinal());
        }
        applySelectedRotationMode();
    }



    private void preparePlaceModeForManualDisassembly() {
        setSelectedAssemblyMode(TwisterRotationMode.PLACE);
    }

    private void resetAssemblyModeAfterDisassembly() {
        setSelectedAssemblyMode(TwisterRotationMode.FREE_RS_ON);
    }

    @SuppressWarnings("unused")
    public void queueAssemblePublic() {
        if (isVerticalDisarmed()) {
            verticalManualEnabled = false;
            stopAllMotionState();
            updateVisualRunning(false);
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        if (restoreFreeModeAfterManualDisassembly && !running && !assembleNextTick) {
            restoreFreeModeAfterManualDisassembly = false;
            resetAssemblyModeAfterDisassembly();
        }

        verticalManualEnabled = true;
        queueAssemble();
    }



    private void performManualDisassembly() {
        if (level == null || level.isClientSide) {
            return;
        }

        preparePlaceModeForManualDisassembly();
        restoreFreeModeAfterManualDisassembly = true;
        verticalManualEnabled = false;
        assembleNextTick = false;

        if (running) {
            if (pendingDisassembleAfterZero) {
                return;
            }

            disassemblyReturnMeasuredAngleDeg = 0.0F;
            pendingDisassembleAfterZero = true;
            pendingDisassembleStableTicks = 0;
            stopAllMotionState();
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        disassemblyReturnMeasuredAngleDeg = 0.0F;
        stopAllMotionState();
        updateVisualRunning(false);

        setChanged();
        if (canSendData()) sendData();
    }



    public boolean isPlacementNorthValidForDisplay() {
        return placementNorthValid;
    }

    public float getVerticalCurrentYawDegForDisplay() {
        return wrap360(verticalCurrentYawDeg);
    }

    public int getGeneratedRpmForDisplay() {
        return Math.abs(generatedRpm);
    }

    public float getVerticalTargetYawDegForDisplay() {
        return wrap360(verticalTargetYawDeg);
    }

    public float getLastWorldWindAngleDegForDisplay() {
        return wrap360(lastWorldWindAngleDeg);
    }

    public float getLastLocalTargetYawDegForDisplay() {
        return wrap360(lastLocalTargetYawDeg);
    }

    public float getVerticalYawVelocityDegPerTickForDisplay() {
        return verticalYawVelocityDegPerTick;
    }

    public boolean isVerticalYawMovingForDisplay() {
        return verticalYawMoving;
    }

    public boolean isVerticalParkedModeForDisplay() {
        return verticalParkedMode;
    }

    public long getVerticalPulseCooldownUntilForDisplay() {
        return verticalPulseCooldownUntil;
    }

    public void queueAssemble() {
        if (level != null && !level.isClientSide) {
            forceRotationModeNeverPlace();

            if (!refreshPlacementStatusForTooltipAndPark(level.getGameTime(), true)) {
                assembleNextTick = false;
                verticalManualEnabled = false;
                stopAllMotionState();
                updateVisualRunning(false);
                setChanged();
                if (canSendData()) sendData();
                return;
            }

            assembleNextTick = true;
            initVerticalModeOnAssemble(level.getGameTime());
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();

            setChanged();
            if (canSendData()) sendData();
            return;
        }

        assembleNextTick = true;
        setChanged();
        if (canSendData()) sendData();
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        boolean previousAssembleNextTick = assembleNextTick;
        super.onSpeedChanged(prevSpeed);
        assembleNextTick = previousAssembleNextTick;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        behaviours.remove(movementMode);
        assemblyMode = null;
        forceRotationModeNeverPlace();
    }

    @Override
    public void onLoad() {
        super.onLoad();

        normalizePersistentStateAfterRead();
        clearTransientRuntimeFlags();

        if (level != null && !level.isClientSide && sableBackend.isActive()) {
            sableLoadRecoveryTicks = getSableLoadRecoveryTicks();
            sableReloadReattachGraceTicks = getSableReloadReattachGraceTicks();
            lastSableRefreshFailureReason = SableInteractiveContraptionBackend.RefreshFailureReason.NONE;
            lastSableRefreshFailureLogTick = Long.MIN_VALUE;
            sableReloadVelocityStabilizedThisLoad = false;
        }

        if (level != null && !level.isClientSide) {
            refreshPlacementStatusForTooltipAndPark(level.getGameTime(), true);
        }
        logSableLifecycleDiagnostics("on-load");
    }

    @Override
    public void onChunkUnloaded() {
        disassemblyReturnMeasuredAngleDeg = 0.0F;
        sableBackend.clearRuntimeForUnload();
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        skipDisassembleDuringRemove = true;
        disassemblyReturnMeasuredAngleDeg = 0.0F;

        if (level != null && !level.isClientSide) {
            sableBackend.clearRuntimeForUnload();
        } else if (level != null) {
            sableBackend.clearClientFallback();
        }

        try {
            super.remove();
        } finally {
            skipDisassembleDuringRemove = false;
        }
    }

    @Override
    public void tick() {
        if (level == null) {
            super.tick();
            return;
        }

        if (!level.isClientSide && sableBackend.isActive()) {
            ServerLevel serverLevel = level instanceof ServerLevel resolvedServerLevel ? resolvedServerLevel : null;
            SableInteractiveContraptionBackend.RefreshResult refresh = serverLevel != null
                    ? sableBackend.refreshDetailed(serverLevel, worldPosition, getFacingDirection())
                    : SableInteractiveContraptionBackend.RefreshResult.failed(
                            SableInteractiveContraptionBackend.RefreshFailureReason.CONTAINER_UNAVAILABLE);

            if (refresh.success()) {
                stabilizeSuccessfulReloadReattachIfNeeded(serverLevel);
                logSableRefreshRecoveredDiagnostics();
                logSableLifecycleDiagnostics("refresh-success");
                sableLoadRecoveryTicks = 0;
                sableReloadReattachGraceTicks = 0;
                lastSableRefreshFailureReason = SableInteractiveContraptionBackend.RefreshFailureReason.NONE;
                lastSableRefreshFailureLogTick = Long.MIN_VALUE;
                if (serverLevel != null) {
                    logScheduledReloadReattachDiagnostics(serverLevel);
                }
                if (updateAssembledBlockCountFromSable(serverLevel)) {
                    setChanged();
                    if (canSendData()) sendData();
                }
            } else {
                SableInteractiveContraptionBackend.RefreshFailureReason reason = refresh.failureReason();
                if (shouldKeepPersistentSableLinkOnRefreshFailure(reason)) {
                    resetDisassemblyReturnMeasuredAngleForClient();
                    handleRetryableSableRefreshFailure(reason);
                    return;
                }

                logSableLifecycleDiagnostics("refresh-failure");
                hardFallbackAfterSableFailure(reason);
                return;
            }
        }

        super.tick();

        if (level.isClientSide) {
            return;
        }

        if (level instanceof ServerLevel serverLevel) {
            updateDisassemblyReturnMeasuredAngleForClient(serverLevel);
        }

        long time = level.getGameTime();
        boolean hasNorthMarker = refreshPlacementStatusForTooltipAndPark(time, false);

        if (restoreFreeModeAfterManualDisassembly && !running && !assembleNextTick) {
            restoreFreeModeAfterManualDisassembly = false;
            resetAssemblyModeAfterDisassembly();
            setChanged();
            if (canSendData()) sendData();
        }

        handleVerticalRedstonePulse(time);

        if (!running && !assembleNextTick) {
            pendingDisassembleAfterZero = false;
            pendingDisassembleStableTicks = 0;
            zeroOutCreateWindmillContribution();
            updateVisualRunning(false);
            return;
        }

        if (pendingDisassembleAfterZero) {
            tickPendingDisassembleAfterZero();

            boolean visualRunning = running;
            updateVisualRunning(visualRunning);
            zeroOutCreateWindmillContribution();
            return;
        }

        if (!hasNorthMarker && !verticalParkedMode) {
            stopAllMotionState();
            updateVisualRunning(false);
            zeroOutCreateWindmillContribution();
            return;
        }

        tickVerticalWindAngleMode(time);

        boolean visualRunning = running
                && (placementNorthValid || verticalParkedMode)
                && isVerticalControlEnabled()
                && Math.abs(generatedSpeedRpm) > 0.001F;

        updateVisualRunning(visualRunning);
        zeroOutCreateWindmillContribution();
    }

    private void handleVerticalRedstonePulse(long time) {
        boolean powered = getExternalRedstonePower() > 0;

        boolean canUsePulse = isMoveNeverPlaceModeSelected()
                && running
                && isVerticalControlEnabled()
                && placementNorthValid;

        if (!canUsePulse) {
            if (!powered) {
                verticalPulsePowered = false;
                verticalPulseStartTick = -1;
            }
            return;
        }

        if (powered && !verticalPulsePowered) {
            verticalPulsePowered = true;
            verticalPulseStartTick = time;
            return;
        }

        if (!powered && verticalPulsePowered) {
            long duration = verticalPulseStartTick < 0 ? 0 : (time - verticalPulseStartTick);
            verticalPulsePowered = false;
            verticalPulseStartTick = -1;

            if (time >= verticalPulseCooldownUntil
                    && duration >= getVerticalPulseMinTicks()
                    && duration <= getVerticalPulseMaxTicks()) {

                verticalParkedMode = !verticalParkedMode;
                verticalAutoParkedByMissingMarker = false;
                verticalPulseCooldownUntil = time + getVerticalPulseCooldownTicks();
                setChanged();
                if (canSendData()) sendData();
            }
        }
    }

    private int getVerticalPulseMaxTicks() {
        return Mth.clamp(TwisterMillConfig.WIND_ROTO_VERTICAL_PULSE_MAX_TICKS.get(), 10, 60);
    }

    private int getVerticalPulseCooldownTicks() {
        int minCooldown = getVerticalPulseMaxTicks() + 10;
        return Mth.clamp(TwisterMillConfig.WIND_ROTO_VERTICAL_PULSE_COOLDOWN_TICKS.get(), minCooldown, 200);
    }

    private static float getSuPerRpm() {
        return TwisterMillConfig.getWindRotoVerticalSuPerRpm();
    }

    private static double getServoStiffnessPerInertia() {
        return TwisterMillConfig.getWindRotoVerticalServoStiffnessPerInertia();
    }

    private static double getServoDampingPerInertia() {
        return TwisterMillConfig.getWindRotoVerticalServoDampingPerInertia();
    }

    private static double getMinEffectiveInertia() {
        return TwisterMillConfig.getWindRotoVerticalMinEffectiveInertia();
    }

    private static int getMaxYawRpm() {
        return TwisterMillConfig.getWindRotoVerticalMaxYawRpm();
    }

    private static float getYawDeadzoneDeg() {
        return TwisterMillConfig.getWindRotoVerticalYawDeadzoneDeg();
    }

    private static float getYawTargetOffsetDeg() {
        return TwisterMillConfig.getWindRotoVerticalYawTargetOffsetDeg();
    }

    private static int getVerticalPulseMinTicks() {
        return TwisterMillConfig.getWindRotoVerticalPulseMinTicks();
    }

    private static int getWindAngleUpdateTicks() {
        return TwisterMillConfig.getWindRotoVerticalWindAngleUpdateTicks();
    }

    private static int getPlacementStatusRefreshTicksDisassembled() {
        return TwisterMillConfig.getWindRotoVerticalPlacementStatusRefreshTicksDisassembled();
    }

    private static int getPlacementStatusRefreshTicksAssembled() {
        return TwisterMillConfig.getWindRotoVerticalPlacementStatusRefreshTicksAssembled();
    }

    private static float getYawControllerGain() {
        return TwisterMillConfig.getWindRotoVerticalYawControllerGain();
    }

    private static float getMaxYawAccelDegPerTick2() {
        return TwisterMillConfig.getWindRotoVerticalMaxYawAccelDegPerTick2();
    }

    private static float getYawMinTrackingSpeedDegPerTick() {
        return TwisterMillConfig.getWindRotoVerticalYawMinTrackingSpeedDegPerTick();
    }

    private static float getYawStopVelocityDegPerTick() {
        return TwisterMillConfig.getWindRotoVerticalYawStopVelocityDegPerTick();
    }

    private static float getParkZeroSnapEpsilonDeg() {
        return TwisterMillConfig.getWindRotoVerticalParkZeroSnapEpsilonDeg();
    }

    private static float getVerticalDisassembleReturnDegreesPerTick() {
        return TwisterMillConfig.getWindRotoVerticalDisassembleReturnDegreesPerTick();
    }

    private static float getVerticalDisassembleZeroEpsilonDeg() {
        return TwisterMillConfig.getWindRotoVerticalDisassembleZeroEpsilonDeg();
    }

    private static int getVerticalDisassembleStableTicks() {
        return TwisterMillConfig.getWindRotoVerticalDisassembleStableTicks();
    }

    private static int getSableLoadRecoveryTicks() {
        return TwisterMillConfig.getWindRotoVerticalSableLoadRecoveryTicks();
    }

    private static int getSableReloadReattachGraceTicks() {
        return TwisterMillConfig.getWindRotoVerticalSableReloadReattachGraceTicks();
    }

    private static int getSableReloadReattachLogIntervalTicks() {
        return TwisterMillConfig.getWindRotoVerticalSableReloadReattachLogIntervalTicks();
    }

    private void initVerticalModeOnAssemble(long time) {
        generatedRpm = 0;
        generatedSpeedRpm = 0.0F;
        generatedSu = 0.0F;

        verticalCurrentYawDeg = 0.0F;
        verticalTargetYawDeg = 0.0F;

        verticalYawVelocityDegPerTick = 0.0F;
        verticalYawMoving = false;
        verticalParkedMode = false;
        verticalAutoParkedByMissingMarker = false;
        verticalPulsePowered = false;
        verticalPulseStartTick = -1;
        nextWindAngleSampleAt = time;
    }

    private void tickVerticalWindAngleMode(long time) {
        if (!isMoveNeverPlaceModeSelected()) {
            stopAllMotionState();
            return;
        }

        if ((!placementNorthValid && !verticalParkedMode) || !isVerticalControlEnabled()) {
            stopAllMotionState();
            return;
        }

        float rawLocalTarget;

        if (verticalParkedMode) {
            lastWorldWindAngleDeg = 0.0F;
            rawLocalTarget = 0.0F;
        } else {
            if (time >= nextWindAngleSampleAt) {
                nextWindAngleSampleAt = time + getWindAngleUpdateTicks();
                lastWorldWindAngleDeg = readWorldWindAngle();
            }

            rawLocalTarget = worldToLocalYaw(lastWorldWindAngleDeg);
        }

        lastLocalTargetYawDeg = rawLocalTarget;
        verticalTargetYawDeg = wrap360(rawLocalTarget);
        verticalCurrentYawDeg = readActualBearingYawDeg();

        float diff = Mth.wrapDegrees(verticalTargetYawDeg - verticalCurrentYawDeg);
        float desiredVelocity = computeDesiredYawVelocity(diff);

        verticalYawVelocityDegPerTick = approachValue(
                verticalYawVelocityDegPerTick,
                desiredVelocity
        );

        if (Math.abs(diff) <= getYawDeadzoneDeg()
                && Math.abs(verticalYawVelocityDegPerTick) <= getYawStopVelocityDegPerTick()) {

            verticalYawVelocityDegPerTick = 0.0F;
            verticalYawMoving = false;
            generatedRpm = 0;
            generatedSpeedRpm = 0.0F;
            generatedSu = 0.0F;
            if (isParkedZeroTarget()) {
                snapParkedYawToZeroIfNeeded();
            } else {
                verticalCurrentYawDeg = wrap360(verticalTargetYawDeg);
                updateGeneratedRotation();
            }
            setChanged();
            return;
        }

        verticalYawMoving = Math.abs(verticalYawVelocityDegPerTick) > getYawStopVelocityDegPerTick();
        updateGeneratedFromYawVelocity();
        updateGeneratedRotation();
        setChanged();
    }

    private boolean isParkedZeroTarget() {
        return verticalParkedMode
                && Math.abs(Mth.wrapDegrees(verticalTargetYawDeg)) <= getParkZeroSnapEpsilonDeg();
    }

    private void snapParkedYawToZeroIfNeeded() {
        float parkZeroSnapEpsilonDeg = getParkZeroSnapEpsilonDeg();
        boolean needsRotationApply = Math.abs(Mth.wrapDegrees(angle)) > parkZeroSnapEpsilonDeg
                || Math.abs(Mth.wrapDegrees(verticalCurrentYawDeg)) > parkZeroSnapEpsilonDeg;

        verticalTargetYawDeg = 0.0F;
        verticalCurrentYawDeg = 0.0F;
        lastWorldWindAngleDeg = 0.0F;
        lastLocalTargetYawDeg = 0.0F;
        verticalYawVelocityDegPerTick = 0.0F;
        verticalYawMoving = false;
        generatedRpm = 0;
        generatedSpeedRpm = 0.0F;
        generatedSu = 0.0F;

        if (needsRotationApply) {
            setAngle(0.0F);
            angle = 0.0F;
            applyRotation();
            return;
        }

        updateGeneratedRotation();
    }

    private float computeDesiredYawVelocity(float diffDeg) {
        float absDiff = Math.abs(diffDeg);

        float yawDeadzoneDeg = getYawDeadzoneDeg();
        if (absDiff <= yawDeadzoneDeg) {
            return 0.0F;
        }

        float maxStep = getMaxYawDegreesPerTick();
        float desired = Mth.clamp(diffDeg * getYawControllerGain(), -maxStep, maxStep);

        float yawMinTrackingSpeedDegPerTick = getYawMinTrackingSpeedDegPerTick();
        if (Math.abs(desired) < yawMinTrackingSpeedDegPerTick) {
            desired = Math.signum(diffDeg) * yawMinTrackingSpeedDegPerTick;
        }

        float distanceFactor = Mth.clamp((absDiff - yawDeadzoneDeg) / 20.0F, 0.3F, 1.0F);
        desired *= distanceFactor;

        return Mth.clamp(desired, -maxStep, maxStep);
    }

    private static float approachValue(float current, float target) {
        float maxYawAccelDegPerTick2 = getMaxYawAccelDegPerTick2();
        if (current < target) {
            return Math.min(current + maxYawAccelDegPerTick2, target);
        }
        if (current > target) {
            return Math.max(current - maxYawAccelDegPerTick2, target);
        }
        return current;
    }

    private void updateDisassemblyReturnMeasuredAngleForClient(ServerLevel serverLevel) {
        Float measuredAngle = null;
        if (pendingDisassembleAfterZero && running && sableBackend.isActive()) {
            measuredAngle = sableBackend.measureFacingAxisRelativeAngleDegrees(
                    serverLevel,
                    worldPosition,
                    getFacingDirection()
            );
        }

        syncDisassemblyReturnMeasuredAngleForClient(measuredAngle);
    }

    private void resetDisassemblyReturnMeasuredAngleForClient() {
        syncDisassemblyReturnMeasuredAngleForClient(0.0F);
    }

    private void syncDisassemblyReturnMeasuredAngleForClient(@Nullable Float measuredAngle) {
        float visibleAngle = normalizeDisassemblyReturnMeasuredAngle(measuredAngle);
        if (Float.compare(disassemblyReturnMeasuredAngleDeg, visibleAngle) == 0) {
            return;
        }

        disassemblyReturnMeasuredAngleDeg = visibleAngle;
        if (canSendData()) {
            sendData();
        }
    }

    private static float normalizeDisassemblyReturnMeasuredAngle(@Nullable Float measuredAngle) {
        if (measuredAngle == null || !Float.isFinite(measuredAngle)) {
            return 0.0F;
        }

        float wrappedAngle = wrap360(measuredAngle);
        if (!Float.isFinite(wrappedAngle) || wrappedAngle == 0.0F) {
            return 0.0F;
        }
        return wrappedAngle;
    }

    private void tickPendingDisassembleAfterZero() {
        if (!running) {
            pendingDisassembleAfterZero = false;
            pendingDisassembleStableTicks = 0;
            return;
        }

        float wrappedCurrent = Mth.wrapDegrees(angle);
        float wrappedNew = approachAngleToZero(wrappedCurrent, getVerticalDisassembleReturnDegreesPerTick());
        boolean changed = Math.abs(wrappedNew - wrappedCurrent) > 0.0001F;

        if (changed) {
            angle += wrappedNew - wrappedCurrent;
            applyRotation();
            setChanged();
            if (canSendData()) sendData();
        }

        boolean atZero = Math.abs(Mth.wrapDegrees(angle)) <= getVerticalDisassembleZeroEpsilonDeg();
        if (atZero) {
            if (Math.abs(angle) > 0.0001F) {
                angle = 0.0F;
                applyRotation();
                setChanged();
                if (canSendData()) sendData();
            }

            pendingDisassembleStableTicks++;
            if (pendingDisassembleStableTicks >= getVerticalDisassembleStableTicks()) {
                pendingDisassembleAfterZero = false;
                pendingDisassembleStableTicks = 0;
                disassemble();
            }
            return;
        }

        pendingDisassembleStableTicks = 0;
    }

    private static float approachAngleToZero(float current, float maxStep) {
        float diff = -current;

        if (Math.abs(diff) <= maxStep)
            return 0.0F;

        return current + Math.signum(diff) * maxStep;
    }

    @Nullable
    public Float computeSableTopVisualAngleDegrees(float partialTicks) {
        if (level == null || !running || !sableBackend.isActive()) {
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

        return Mth.wrapDegrees((float) angleDegrees);
    }

    private static Pose3dc resolveRenderPose(SubLevel subLevel, float partialTicks) {
        if (subLevel instanceof ClientSubLevelAccess clientSubLevelAccess) {
            return clientSubLevelAccess.renderPose(partialTicks);
        }
        return subLevel.lastPose().lerp(subLevel.logicalPose(), partialTicks, new Pose3d());
    }

    private float readActualBearingYawDeg() {
        Float angleValue = tryReadBearingAngleFromMethods(this);
        if (angleValue == null) {
            angleValue = tryReadBearingAngleFromFields(this);
        }
        return wrap360(angleValue != null ? angleValue : verticalCurrentYawDeg);
    }

    @Nullable
    private static Float tryReadBearingAngleFromMethods(Object target) {
        String[] methodNames = new String[]{
                "getInterpolatedAngle",
                "getInterpolatedRenderedAngle",
                "getRenderedAngle",
                "getVisualAngle",
                "getAngle",
                "getBearingAngle"
        };

        for (String methodName : methodNames) {
            try {
                Method m = findMethodInHierarchy(target.getClass(), methodName);
                if (m == null) {
                    continue;
                }

                m.setAccessible(true);

                Object out;
                if (m.getParameterCount() == 0) {
                    out = m.invoke(target);
                } else if (m.getParameterCount() == 1 && (m.getParameterTypes()[0] == float.class || m.getParameterTypes()[0] == Float.class)) {
                    out = m.invoke(target, 1.0F);
                } else {
                    continue;
                }

                if (out instanceof Number n) {
                    return n.floatValue();
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    @Nullable
    private static Float tryReadBearingAngleFromFields(Object target) {
        String[] fieldNames = new String[]{
                "angle",
                "clientAngle",
                "bearingAngle",
                "visualAngle",
                "renderedAngle",
                "yaw",
                "rotation"
        };

        for (String fieldName : fieldNames) {
            try {
                Field f = findFieldInHierarchy(target.getClass(), fieldName);
                if (f == null) {
                    continue;
                }

                f.setAccessible(true);
                Object out = f.get(target);
                if (out instanceof Number n) {
                    return n.floatValue();
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    @Nullable
    private static Method findMethodInHierarchy(Class<?> start, String name) {
        Class<?> c = start;
        while (c != null) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private void updateGeneratedFromYawVelocity() {
        float absVelocity = Math.abs(verticalYawVelocityDegPerTick);

        if (absVelocity <= getYawStopVelocityDegPerTick()) {
            generatedRpm = 0;
            generatedSpeedRpm = 0.0F;
            generatedSu = 0.0F;
            return;
        }

        float sign = Math.signum(verticalYawVelocityDegPerTick);
        float rpmMagnitude = Mth.clamp(absVelocity / DEGREES_PER_TICK_AT_1_RPM, 0.0F, (float) getMaxYawRpm());

        generatedSpeedRpm = sign * rpmMagnitude;
        generatedRpm = Math.round(generatedSpeedRpm);
        generatedSu = computeSuFromRpm(generatedSpeedRpm);
    }

    private void stopAllMotionState() {
        generatedRpm = 0;
        generatedSpeedRpm = 0.0F;
        generatedSu = 0.0F;
        verticalYawVelocityDegPerTick = 0.0F;
        verticalYawMoving = false;
        verticalParkedMode = false;
        verticalPulsePowered = false;
        verticalPulseStartTick = -1;
        verticalPulseCooldownUntil = 0;
        updateGeneratedRotation();
        zeroOutCreateWindmillContribution();
    }

    private int getExternalRedstonePower() {
        if (level == null) {
            return 0;
        }
        return Mth.clamp(level.getBestNeighborSignal(worldPosition), 0, 15);
    }

    private float readWorldWindAngle() {
        if (level == null) {
            return 0.0F;
        }

        WindSample windSample = TwisterWeatherService.sampleWrvbDirectionAtBlock(level, worldPosition);
        if (!windSample.valid()) {
            return "pmweather".equals(windSample.backendName()) ? lastWorldWindAngleDeg : 0.0F;
        }
        return wrap360(windSample.windAngleDegrees());
    }

    private float worldToLocalYaw(float worldYawDeg) {
        Direction placementNorth = getPlacementNorthDirection();
        if (placementNorth == null) {
            return 0.0F;
        }

        float base = directionToYaw(placementNorth);
        return wrap360(base - worldYawDeg + getYawTargetOffsetDeg());
    }

    @Nullable
    private Direction getPlacementNorthDirection() {
        if (!placementNorthValid || placementNorthDirData < 0) {
            return null;
        }
        return Direction.from2DDataValue(placementNorthDirData);
    }

    private Direction getFacingDirection() {
        if (level == null)
            return Direction.NORTH;

        BlockState state = getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING))
            return Direction.NORTH;

        return state.getValue(BlockStateProperties.FACING);
    }

    private float getMaxYawDegreesPerTick() {
        return getMaxYawRpm() * DEGREES_PER_TICK_AT_1_RPM;
    }

    private float getSuFactor() {
        return Mth.clamp(TwisterMillConfig.SU_FACTOR.get().floatValue(), 1.0F, 100.0F);
    }

    private float computeSuFromRpm(float rpm) {
        float r = Math.abs(rpm);
        if (r <= 0.0001F) {
            return 0.0F;
        }
        return (getSuPerRpm() * getSuFactor()) * r;
    }

    private static float directionToYaw(Direction dir) {
        return switch (dir) {
            case NORTH, UP, DOWN -> 0.0F;
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
        };
    }

    private static float wrap360(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    @Override
    public float getGeneratedSpeed() {
        if (!running) {
            return 0.0F;
        }
        return generatedSpeedRpm;
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (!running || Math.abs(generatedSpeedRpm) <= 0.0001F) {
            this.lastCapacityProvided = 0.0F;
            return 0.0F;
        }

        float capacityPerRpm = getSuPerRpm() * getSuFactor();
        this.lastCapacityProvided = capacityPerRpm;
        return capacityPerRpm;
    }

    @Override
    protected boolean isWindmill() {
        return true;
    }

    @Override
    public void assemble() {
        forceRotationModeNeverPlace();

        if (!(level instanceof ServerLevel serverLevel))
            return;

        if (running)
            return;

        SableInteractiveContraptionBackend.AssemblyResult assembly = sableBackend.tryAssemble(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                false,
                exception -> {
                    lastException = exception;
                    if (exception != null)
                        if (canSendData()) sendData();
                },
                RememberedSableShipMemory.enabledFor(getBlockState(), rememberedShipMemory)
        );
        if (assembly != null) {
            assembledBlockCount = Math.max(0, assembly.blockCount());
            updateAssembledBlockCountFromSable(serverLevel);
            lastException = null;
            AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

            running = true;
            pendingDisassembleAfterZero = false;
            pendingDisassembleStableTicks = 0;
            disassemblyReturnMeasuredAngleDeg = 0.0F;
            verticalAutoParkedByMissingMarker = false;
            angle = 0.0F;
            sequencedAngleLimit = -1.0;
            assembleNextTick = false;
            movedContraption = null;

            applyRotation();
            updateGeneratedRotation();
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        super.assemble();
    }

    @Override
    public void disassemble() {
        sableLoadRecoveryTicks = 0;
        pendingDisassembleAfterZero = false;
        pendingDisassembleStableTicks = 0;
        disassemblyReturnMeasuredAngleDeg = 0.0F;
        verticalAutoParkedByMissingMarker = false;
        clearAssembledBlockCount();

        if (skipDisassembleDuringRemove) {
            if (level != null && !level.isClientSide) {
                sableBackend.clearRuntimeForUnload();
            } else if (level != null) {
                sableBackend.clearClientFallback();
            }

            movedContraption = null;
            running = false;
            assembleNextTick = false;
            return;
        }

        if (!sableBackend.isActive()) {
            super.disassemble();
            angle = 0.0F;
            sequencedAngleLimit = -1.0;
            movedContraption = null;
            running = false;
            assembleNextTick = false;
            stopAllMotionState();
            updateGeneratedRotation();
            updateVisualRunning(false);
            if (level != null && !level.isClientSide) {
                refreshPlacementStatusForTooltipAndPark(level.getGameTime(), true);
            }
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            running = false;
            movedContraption = null;
            assembleNextTick = false;
            angle = 0.0F;
            sequencedAngleLimit = -1.0;
            sableBackend.clearClientFallback();
            updateGeneratedRotation();
            assembleNextTick = false;
            updateVisualRunning(false);
            setChanged();
            if (canSendData()) sendData();
            return;
        }

        angle = 0.0F;
        sequencedAngleLimit = -1.0;
        if (RememberedSableShipMemory.isRememberContraptionEnabledFor(getBlockState())) {
            rememberedShipMemory.replaceFromWorldPositions(
                    worldPosition,
                    getFacingDirection(),
                    sableBackend.snapshotRestoredBlockPositions(serverLevel, worldPosition)
            );
        }
        boolean restored = sableBackend.disassemble(serverLevel, worldPosition);
        if (restored) {
            AllSoundEvents.CONTRAPTION_DISASSEMBLE.playOnServer(level, worldPosition);
        }

        movedContraption = null;
        running = false;
        assembleNextTick = false;
        updateGeneratedRotation();
        assembleNextTick = false;
        updateVisualRunning(false);
        setChanged();
        if (canSendData()) sendData();
    }

    @Override
    protected void applyRotation() {
        if (sableBackend.isActive() && level instanceof ServerLevel serverLevel) {
            boolean applied = sableBackend.applyMotor(
                    serverLevel,
                    worldPosition,
                    getFacingDirection(),
                    angle,
                    getServoStiffnessPerInertia(),
                    getServoDampingPerInertia(),
                    getMinEffectiveInertia()
            );
            if (!applied) {
                logSableClearCauseDiagnostics(
                        "apply-rotation-before-clear",
                        "apply-motor-returned-false",
                        running,
                        getFacingDirection(),
                        sableBackend.getActiveSubLevelId(),
                        sableLoadRecoveryTicks,
                        assembleNextTick,
                        pendingDisassembleAfterZero,
                        pendingDisassembleStableTicks);
                running = false;
                movedContraption = null;
                assembleNextTick = false;
                sableBackend.clearState();
                clearAssembledBlockCount();
                updateGeneratedRotation();
                assembleNextTick = false;
                setChanged();
                if (canSendData()) sendData();
            }
            return;
        }

        super.applyRotation();
    }

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        tag.putInt("GenRpm", generatedRpm);
        tag.putFloat("GeneratedSpeedRpm", generatedSpeedRpm);
        tag.putFloat("GenSu", generatedSu);
        tag.putBoolean("VisualRunning", lastVisualRunning);

        tag.putBoolean("VerticalManualEnabled", verticalManualEnabled);
        tag.putBoolean("RestoreFreeModeAfterManualDisassembly", restoreFreeModeAfterManualDisassembly);

        tag.putBoolean("PlacementNorthValid", placementNorthValid);
        tag.putInt("PlacementNorthDir", placementNorthDirData);

        tag.putFloat("VerticalCurrentYawDeg", verticalCurrentYawDeg);
        tag.putFloat("VerticalTargetYawDeg", verticalTargetYawDeg);
        tag.putFloat("VerticalYawVelocityDegPerTick", verticalYawVelocityDegPerTick);
        tag.putBoolean("VerticalYawMoving", verticalYawMoving);

        tag.putBoolean("VerticalParkedMode", verticalParkedMode);
        tag.putBoolean("VerticalAutoParkedByMissingMarker", verticalAutoParkedByMissingMarker);
        tag.putBoolean("VerticalPulsePowered", verticalPulsePowered);
        tag.putLong("VerticalPulseCooldownUntil", verticalPulseCooldownUntil);
        tag.putInt(TAG_ASSEMBLED_BLOCK_COUNT, assembledBlockCount);
        if (clientPacket) {
            tag.putBoolean(TAG_PENDING_DISASSEMBLE_AFTER_ZERO, pendingDisassembleAfterZero);
            tag.putFloat(TAG_DISASSEMBLY_RETURN_MEASURED_ANGLE, disassemblyReturnMeasuredAngleDeg);
            tag.putBoolean("BlockOnBearingForDisplay", blockOnBearingForDisplay);
            tag.putByte("PlacementNorthMarkerStatus", placementNorthMarkerStatus);
        }

        tag.putFloat("LastWorldWindAngleDeg", lastWorldWindAngleDeg);
        tag.putFloat("LastLocalTargetYawDeg", lastLocalTargetYawDeg);
        logSableWriteNbtDiagnostics("write-before-backend", tag, clientPacket);
        sableBackend.write(tag, TAG_SABLE_ACTIVE, TAG_SABLE_SUBLEVEL_ID);
        if (!clientPacket) {
            rememberedShipMemory.write(tag);
        }
        logSableWriteNbtDiagnostics("write-after-backend", tag, clientPacket);
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        forceRotationModeNeverPlace();

        if (tag.contains("GenRpm")) {
            generatedRpm = tag.getInt("GenRpm");
        }
        if (tag.contains("GeneratedSpeedRpm")) {
            generatedSpeedRpm = tag.getFloat("GeneratedSpeedRpm");
        }
        if (tag.contains("GenSu")) {
            generatedSu = tag.getFloat("GenSu");
        }
        if (tag.contains("VisualRunning")) {
            lastVisualRunning = tag.getBoolean("VisualRunning");
        }

        if (tag.contains("VerticalManualEnabled")) {
            verticalManualEnabled = tag.getBoolean("VerticalManualEnabled");
        }
        if (tag.contains("RestoreFreeModeAfterManualDisassembly")) {
            restoreFreeModeAfterManualDisassembly = tag.getBoolean("RestoreFreeModeAfterManualDisassembly");
        }

        if (tag.contains("PlacementNorthValid")) {
            placementNorthValid = tag.getBoolean("PlacementNorthValid");
        }
        if (tag.contains("PlacementNorthDir")) {
            placementNorthDirData = tag.getInt("PlacementNorthDir");
        }

        if (tag.contains("VerticalCurrentYawDeg")) {
            verticalCurrentYawDeg = wrap360(tag.getFloat("VerticalCurrentYawDeg"));
        }
        if (tag.contains("VerticalTargetYawDeg")) {
            verticalTargetYawDeg = wrap360(tag.getFloat("VerticalTargetYawDeg"));
        }
        if (tag.contains("VerticalYawVelocityDegPerTick")) {
            verticalYawVelocityDegPerTick = tag.getFloat("VerticalYawVelocityDegPerTick");
        }
        if (tag.contains("VerticalYawMoving")) {
            verticalYawMoving = tag.getBoolean("VerticalYawMoving");
        }

        if (tag.contains("VerticalParkedMode")) {
            verticalParkedMode = tag.getBoolean("VerticalParkedMode");
        }
        if (tag.contains("VerticalAutoParkedByMissingMarker")) {
            verticalAutoParkedByMissingMarker = tag.getBoolean("VerticalAutoParkedByMissingMarker");
        } else {
            verticalAutoParkedByMissingMarker = false;
        }
        if (tag.contains("VerticalPulsePowered")) {
            verticalPulsePowered = tag.getBoolean("VerticalPulsePowered");
        }
        if (tag.contains("VerticalPulseCooldownUntil")) {
            verticalPulseCooldownUntil = tag.getLong("VerticalPulseCooldownUntil");
        }
        if (tag.contains(TAG_ASSEMBLED_BLOCK_COUNT)) {
            assembledBlockCount = Math.max(0, tag.getInt(TAG_ASSEMBLED_BLOCK_COUNT));
        } else {
            assembledBlockCount = 0;
        }
        if (clientPacket) {
            disassemblyReturnMeasuredAngleDeg = normalizeDisassemblyReturnMeasuredAngle(
                    tag.contains(TAG_DISASSEMBLY_RETURN_MEASURED_ANGLE)
                            ? tag.getFloat(TAG_DISASSEMBLY_RETURN_MEASURED_ANGLE)
                            : null
            );
        } else {
            disassemblyReturnMeasuredAngleDeg = 0.0F;
        }
        if (tag.contains(TAG_PENDING_DISASSEMBLE_AFTER_ZERO)) {
            pendingDisassembleAfterZero = tag.getBoolean(TAG_PENDING_DISASSEMBLE_AFTER_ZERO);
        }
        if (tag.contains("BlockOnBearingForDisplay")) {
            blockOnBearingForDisplay = tag.getBoolean("BlockOnBearingForDisplay");
        }
        if (tag.contains("PlacementNorthMarkerStatus")) {
            placementNorthMarkerStatus = normalizeMarkerStatus(tag.getByte("PlacementNorthMarkerStatus"));
        } else {
            placementNorthMarkerStatus = placementNorthValid ? MARKER_STATUS_FOUND : MARKER_STATUS_MISSING;
        }

        if (tag.contains("LastWorldWindAngleDeg")) {
            lastWorldWindAngleDeg = wrap360(tag.getFloat("LastWorldWindAngleDeg"));
        }
        if (tag.contains("LastLocalTargetYawDeg")) {
            lastLocalTargetYawDeg = wrap360(tag.getFloat("LastLocalTargetYawDeg"));
        }

        if (!clientPacket) {
            logSableReadNbtDiagnostics("read-before-backend", tag);
        }
        sableBackend.read(tag, TAG_SABLE_ACTIVE, TAG_SABLE_SUBLEVEL_ID);
        if (!clientPacket) {
            rememberedShipMemory.read(tag);
        }
        if (!clientPacket) {
            logSableReadNbtDiagnostics("read-after-backend", tag);
        }
        diagnosticFirstRefreshLogged = false;
        diagnosticRefreshFailureLogged = false;
        if (!clientPacket) {
            logSableLifecycleDiagnostics("read");
        }
        normalizePersistentStateAfterRead();
        forceRotationModeNeverPlace();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean details = com.simibubi.create.AllKeys.ctrlDown();

        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.vertical.yaw")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        float displayedYawDeg = pendingDisassembleAfterZero
                ? disassemblyReturnMeasuredAngleDeg
                : (running || sableBackend.isActive())
                        ? verticalCurrentYawDeg
                        : 0.0F;
        CreateLang.number(wrap360(displayedYawDeg))
                .style(ChatFormatting.YELLOW)
                .space()
                .add(Component.literal("°"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.status")
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
            return true;
        }

        boolean assembledActive = running || sableBackend.isActive();
        boolean markerWarning = placementNorthMarkerStatus != MARKER_STATUS_FOUND;

        if (!assembledActive && !isAssembleReadyBlinkActive()) {
            CreateLang.translate("tooltip.twistermill.vertical.ready")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);

            CreateLang.text("")
                    .add(getAssemblePossibleTooltipComponent())
                    .forGoggles(tooltip, 1);
        }

        if (!assembledActive || markerWarning) {
            CreateLang.translate("tooltip.twistermill.vertical.smooth_stone_slab_north_marker")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);

            CreateLang.text("")
                    .add(getMarkerStatusTooltipComponent())
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.vertical.rpm")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(Math.abs(generatedRpm))
                .style(ChatFormatting.AQUA)
                .space()
                .add(Component.literal("rpm"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.vertical.target_yaw")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(wrap360(verticalTargetYawDeg))
                .style(ChatFormatting.YELLOW)
                .space()
                .add(Component.literal("°"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.vertical.world_wind_angle")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(wrap360(lastWorldWindAngleDeg))
                .style(ChatFormatting.AQUA)
                .space()
                .add(Component.literal("°"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.vertical.local_target_yaw")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(wrap360(lastLocalTargetYawDeg))
                .style(ChatFormatting.AQUA)
                .space()
                .add(Component.literal("°"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.vertical.yaw_velocity")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(verticalYawVelocityDegPerTick)
                .style(ChatFormatting.WHITE)
                .space()
                .add(Component.literal("°/tick"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.vertical.yaw_moving")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text(verticalYawMoving ? "true" : "false")
                .style(verticalYawMoving ? ChatFormatting.GREEN : ChatFormatting.WHITE)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.vertical.park_mode")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text(verticalParkedMode ? "true" : "false")
                .style(verticalParkedMode ? ChatFormatting.YELLOW : ChatFormatting.WHITE)
                .forGoggles(tooltip, 1);

        long pulseCd = level == null ? 0 : Math.max(0, verticalPulseCooldownUntil - level.getGameTime());
        CreateLang.translate("tooltip.twistermill.vertical.redstone_pulse_cooldown")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number((int) pulseCd)
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip, 1);

        return true;
    }

    private Component getTooltipContraptionStatusComponent() {
        boolean assembledActive = running || sableBackend.isActive();
        if (pendingDisassembleAfterZero && assembledActive) {
            return CreateLang.translateDirect("tooltip.twistermill.status.disassembling")
                    .withStyle(ChatFormatting.GOLD);
        }

        if (assembledActive) {
            return getAssembledStatusWithBlockCount(getContraptionBlockCountForDisplay());
        }

        if (isAssembleReadyBlinkActive()) {
            return getAssemblePossibleTooltipComponent();
        }

        return CreateLang.translateDirect("tooltip.twistermill.status.disassembled")
                .withStyle(ChatFormatting.BLUE);
    }

    private Component getAssembledStatusWithBlockCount(int blockCount) {
        return Component.empty()
                .append(CreateLang.translateDirect("tooltip.twistermill.status.assembled")
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Integer.toString(Math.max(0, blockCount))).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" ").withStyle(ChatFormatting.GRAY))
                .append(CreateLang.translateDirect("tooltip.twistermill.status.blocks")
                        .withStyle(ChatFormatting.AQUA));
    }

    private boolean isAssembleReadyBlinkActive() {
        return placementNorthValid && blockOnBearingForDisplay;
    }

    private Component getAssemblePossibleTooltipComponent() {
        if (placementNorthValid && blockOnBearingForDisplay && level != null && ((level.getGameTime() / 20) % 2L) == 1L) {
            return CreateLang.translateDirect("tooltip.twistermill.vertical.ready_for_assemble_toggle")
                    .withStyle(ChatFormatting.GREEN);
        }

        boolean fulfilled = placementNorthValid && blockOnBearingForDisplay;
        ChatFormatting positiveStyle = fulfilled ? ChatFormatting.BLUE : ChatFormatting.GREEN;
        Component markerComponent = CreateLang.translateDirect(placementNorthValid
                        ? "tooltip.twistermill.vertical.marker_placed"
                        : markerStatusTranslationKey())
                .withStyle(placementNorthValid ? positiveStyle : ChatFormatting.RED);
        Component blockComponent = CreateLang.translateDirect(blockOnBearingForDisplay
                        ? "tooltip.twistermill.vertical.block_on_bearing"
                        : "tooltip.twistermill.vertical.no_block_on_bearing")
                .withStyle(blockOnBearingForDisplay ? positiveStyle : ChatFormatting.RED);

        return Component.empty()
                .append(markerComponent)
                .append(Component.literal(" + ").withStyle(ChatFormatting.GRAY))
                .append(blockComponent);
    }

    private Component getMarkerStatusTooltipComponent() {
        ChatFormatting style = placementNorthMarkerStatus == MARKER_STATUS_FOUND
                ? ChatFormatting.GREEN
                : ChatFormatting.RED;
        return CreateLang.translateDirect(markerStatusTranslationKey()).withStyle(style);
    }

    private String markerStatusTranslationKey() {
        return switch (placementNorthMarkerStatus) {
            case MARKER_STATUS_FOUND -> "tooltip.twistermill.vertical.marker_found";
            case MARKER_STATUS_WRONG_BLOCK -> "tooltip.twistermill.vertical.marker_wrong_block";
            default -> "tooltip.twistermill.vertical.marker_missing";
        };
    }

    private void normalizePersistentStateAfterRead() {
        applySelectedRotationMode();

        verticalCurrentYawDeg = wrap360(verticalCurrentYawDeg);
        verticalTargetYawDeg = wrap360(verticalTargetYawDeg);
        lastWorldWindAngleDeg = wrap360(lastWorldWindAngleDeg);
        lastLocalTargetYawDeg = wrap360(lastLocalTargetYawDeg);

        float maxVelocity = getMaxYawDegreesPerTick();
        verticalYawVelocityDegPerTick = Mth.clamp(verticalYawVelocityDegPerTick, -maxVelocity, maxVelocity);

        int maxYawRpm = getMaxYawRpm();
        generatedSpeedRpm = Mth.clamp(generatedSpeedRpm, -(float) maxYawRpm, (float) maxYawRpm);
        generatedRpm = Math.round(generatedSpeedRpm);
        generatedSu = computeSuFromRpm(generatedSpeedRpm);
        if (!verticalParkedMode) {
            verticalAutoParkedByMissingMarker = false;
        }

        zeroOutCreateWindmillContribution();
    }

    private void clearTransientRuntimeFlags() {
        verticalPulsePowered = false;
        verticalPulseStartTick = -1;
        nextWindAngleSampleAt = 0;
        pendingDisassembleAfterZero = false;
        pendingDisassembleStableTicks = 0;
        disassemblyReturnMeasuredAngleDeg = 0.0F;
        if (!verticalParkedMode) {
            verticalAutoParkedByMissingMarker = false;
        }
        sableLoadRecoveryTicks = 0;
        sableReloadReattachGraceTicks = 0;
        lastSableRefreshFailureReason = SableInteractiveContraptionBackend.RefreshFailureReason.NONE;
        lastSableRefreshFailureLogTick = Long.MIN_VALUE;
        sableReloadVelocityStabilizedThisLoad = false;
        clearReloadReattachDiagnostics();
    }

    private boolean shouldKeepPersistentSableLinkOnRefreshFailure(
            SableInteractiveContraptionBackend.RefreshFailureReason reason) {
        return reason == SableInteractiveContraptionBackend.RefreshFailureReason.CONTAINER_UNAVAILABLE
                || reason == SableInteractiveContraptionBackend.RefreshFailureReason.SUBLEVEL_NOT_FOUND
                || reason == SableInteractiveContraptionBackend.RefreshFailureReason.BASE_CONTEXT_UNAVAILABLE
                || reason == SableInteractiveContraptionBackend.RefreshFailureReason.PARENT_SUBLEVEL_NOT_READY
                || reason == SableInteractiveContraptionBackend.RefreshFailureReason.CONSTRAINT_ATTACH_FAILED
                && sableReloadReattachGraceTicks > 0;
    }

    private void handleRetryableSableRefreshFailure(SableInteractiveContraptionBackend.RefreshFailureReason reason) {
        logSableRefreshFailureRetainedDiagnostics(reason);
        logSableLifecycleDiagnostics("refresh-failure");
        if (sableReloadReattachGraceTicks > 0) {
            sableReloadReattachGraceTicks--;
        }
        if (sableLoadRecoveryTicks > 0) {
            sableLoadRecoveryTicks--;
        }
    }

    private void logSableRefreshFailureRetainedDiagnostics(SableInteractiveContraptionBackend.RefreshFailureReason reason) {
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            lastSableRefreshFailureReason = reason;
            return;
        }

        long time = level == null ? -1L : level.getGameTime();
        boolean reasonChanged = reason != lastSableRefreshFailureReason;
        boolean intervalElapsed = lastSableRefreshFailureLogTick == Long.MIN_VALUE
                || time < lastSableRefreshFailureLogTick
                || time - lastSableRefreshFailureLogTick >= getSableReloadReattachLogIntervalTicks();
        if (!reasonChanged && !intervalElapsed) {
            return;
        }

        lastSableRefreshFailureReason = reason;
        lastSableRefreshFailureLogTick = time;
        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event=refresh-retryable-retain-link reason={} dimension={} pos={} gameTime={} running={} sableActive={} activeSubLevelId={} reloadGraceTicks={} reloadGraceWindow={} legacyRecoveryTicks={} persistentLinkAction=kept",
                reason,
                level == null ? null : level.dimension().location(),
                worldPosition,
                time,
                running,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                sableReloadReattachGraceTicks,
                getSableReloadReattachGraceTicks(),
                sableLoadRecoveryTicks);
    }

    private void logSableRefreshRecoveredDiagnostics() {
        if (lastSableRefreshFailureReason == SableInteractiveContraptionBackend.RefreshFailureReason.NONE
                || !TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            return;
        }

        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event=refresh-recovered previousReason={} dimension={} pos={} gameTime={} running={} sableActive={} activeSubLevelId={} reloadGraceTicks={} persistentLinkAction=kept",
                lastSableRefreshFailureReason,
                level == null ? null : level.dimension().location(),
                worldPosition,
                level == null ? -1L : level.getGameTime(),
                running,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                sableReloadReattachGraceTicks);
    }

    private void stabilizeSuccessfulReloadReattachIfNeeded(ServerLevel serverLevel) {
        if (sableReloadReattachGraceTicks <= 0 || sableReloadVelocityStabilizedThisLoad || !sableBackend.isActive()) {
            return;
        }

        SableInteractiveContraptionBackend.ReloadStabilizationResult result =
                sableBackend.stabilizeReloadReattach(serverLevel, worldPosition, getFacingDirection());
        sableReloadVelocityStabilizedThisLoad = true;
        logReloadReattachStabilization(result);
        if (result.attachedId() != null) {
            scheduleReloadReattachDiagnostics();
        }
    }

    @SuppressWarnings("unused")
    public ManualReseatCommandResult manualReseatFromCommand() {
        TwisterMillReseatService.ReseatResult result =
                reseatFromDiagnostics(TwisterMillReseatService.Trigger.MANUAL_COMMAND);
        return new ManualReseatCommandResult(
                result.applied(),
                result.action(),
                result.visualAngleBefore(),
                result.visualAngleAfter(),
                result.anchorWorldErrorBefore(),
                result.normalWorldErrorBefore(),
                result.anchorWorldErrorAfter(),
                result.normalWorldErrorAfter()
        );
    }

    public TwisterMillReseatService.ReseatResult reseatFromDiagnostics(TwisterMillReseatService.Trigger trigger) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return TwisterMillReseatService.ReseatResult.failed(
                    TwisterMillReseatService.TargetType.WRVB,
                    worldPosition,
                    "not-server-level"
            );
        }

        Float visualAngleBefore = computeSableTopVisualAngleDegrees(0.0F);
        setAngle(0.0F);
        angle = 0.0F;
        sequencedAngleLimit = -1.0;
        verticalCurrentYawDeg = 0.0F;
        verticalTargetYawDeg = 0.0F;
        lastLocalTargetYawDeg = 0.0F;
        stopAllMotionState();
        forceVisualRunningOff();

        SableInteractiveContraptionBackend.ReloadStabilizationResult result =
                sableBackend.reseatAttachedSubLevel(
                        serverLevel,
                        worldPosition,
                        getFacingDirection(),
                        getServoStiffnessPerInertia(),
                        getServoDampingPerInertia(),
                        getMinEffectiveInertia(),
                        trigger.actionPrefix()
                );
        if (result.poseReseatApplied()) {
            setChanged();
            if (canSendData()) sendData();
        }
        Float visualAngleAfter = computeSableTopVisualAngleDegrees(0.0F);
        if (trigger == TwisterMillReseatService.Trigger.MANUAL_COMMAND) {
            logManualCommandReseat(result);
        }
        return new TwisterMillReseatService.ReseatResult(
                TwisterMillReseatService.TargetType.WRVB,
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

    private void logManualCommandReseat(SableInteractiveContraptionBackend.ReloadStabilizationResult result) {
        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event=manual-command-reseat pos={} gameTime={} facing={} running={} assembledBlockCount={} activeSubLevelId={} attachedId={} action={} velocityReset={} poseReseatApplied={} anchorWorldErrorBefore={} normalWorldErrorBefore={} anchorWorldErrorAfter={} normalWorldErrorAfter={} thresholdBreach={} comToContactLocal={} anchorToComWorldBefore={} anchorToComWorldAfter={} attachedPoseBefore={} attachedPoseAfter={} linearVelocityBefore={} angularVelocityBefore={} linearVelocityAfter={} angularVelocityAfter={}",
                worldPosition,
                level == null ? -1L : level.getGameTime(),
                getFacingDirection(),
                running,
                assembledBlockCount,
                result.activeSubLevelId(),
                result.attachedId(),
                result.action(),
                result.velocityReset(),
                result.poseReseatApplied(),
                formatDouble(result.anchorWorldError()),
                formatDouble(result.normalWorldError()),
                formatDouble(result.anchorWorldErrorAfter()),
                formatDouble(result.normalWorldErrorAfter()),
                result.thresholdBreach(),
                formatVector(result.comToContactLocal()),
                formatVector(result.anchorToComWorldBefore()),
                formatVector(result.anchorToComWorldAfter()),
                result.attachedPoseBefore(),
                result.attachedPoseAfter(),
                formatVector(result.linearVelocityBefore()),
                formatVector(result.angularVelocityBefore()),
                formatVector(result.linearVelocityAfter()),
                formatVector(result.angularVelocityAfter()));
    }

    public record ManualReseatCommandResult(
            boolean applied,
            String action,
            @Nullable Float visualAngleBefore,
            @Nullable Float visualAngleAfter,
            double anchorWorldErrorBefore,
            double normalWorldErrorBefore,
            double anchorWorldErrorAfter,
            double normalWorldErrorAfter
    ) {
    }

    private void logReloadReattachStabilization(SableInteractiveContraptionBackend.ReloadStabilizationResult result) {
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            return;
        }

        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event=reload-stabilize pos={} gameTime={} facing={} running={} assembledBlockCount={} reloadGraceTicks={} reloadGraceWindow={} activeSubLevelId={} attachedId={} action={} velocityReset={} poseReseatApplied={} anchorWorldError={} normalWorldError={} anchorWorldErrorAfter={} normalWorldErrorAfter={} thresholdBreach={} comToContactLocal={} linearVelocityBefore={} angularVelocityBefore={} linearVelocityAfter={} angularVelocityAfter={}",
                worldPosition,
                level == null ? -1L : level.getGameTime(),
                getFacingDirection(),
                running,
                assembledBlockCount,
                sableReloadReattachGraceTicks,
                getSableReloadReattachGraceTicks(),
                result.activeSubLevelId(),
                result.attachedId(),
                result.action(),
                result.velocityReset(),
                result.poseReseatApplied(),
                formatDouble(result.anchorWorldError()),
                formatDouble(result.normalWorldError()),
                formatDouble(result.anchorWorldErrorAfter()),
                formatDouble(result.normalWorldErrorAfter()),
                result.thresholdBreach(),
                formatVector(result.comToContactLocal()),
                formatVector(result.linearVelocityBefore()),
                formatVector(result.angularVelocityBefore()),
                formatVector(result.linearVelocityAfter()),
                formatVector(result.angularVelocityAfter()));
    }

    private void scheduleReloadReattachDiagnostics() {
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            clearReloadReattachDiagnostics();
            return;
        }

        reloadReattachDiagnosticAgeTicks = 0;
        reloadReattachDiagnosticNextSnapshotIndex = 0;
        reloadReattachDiagnosticStartGameTime = level == null ? -1L : level.getGameTime();
    }

    private void clearReloadReattachDiagnostics() {
        reloadReattachDiagnosticAgeTicks = -1;
        reloadReattachDiagnosticNextSnapshotIndex = 0;
        reloadReattachDiagnosticStartGameTime = Long.MIN_VALUE;
    }

    private void logScheduledReloadReattachDiagnostics(ServerLevel serverLevel) {
        if (reloadReattachDiagnosticAgeTicks < 0) {
            return;
        }
        if (!TwisterMillDiagnostics.isWrvbLoggingEnabled()) {
            clearReloadReattachDiagnostics();
            return;
        }

        while (reloadReattachDiagnosticNextSnapshotIndex < RELOAD_REATTACH_DIAGNOSTIC_SNAPSHOT_TICKS.length
                && reloadReattachDiagnosticAgeTicks >= RELOAD_REATTACH_DIAGNOSTIC_SNAPSHOT_TICKS[reloadReattachDiagnosticNextSnapshotIndex]) {
            int snapshotTick = RELOAD_REATTACH_DIAGNOSTIC_SNAPSHOT_TICKS[reloadReattachDiagnosticNextSnapshotIndex];
            logReloadReattachDiagnosticSnapshot(serverLevel, snapshotTick);
            reloadReattachDiagnosticNextSnapshotIndex++;
        }

        if (reloadReattachDiagnosticNextSnapshotIndex >= RELOAD_REATTACH_DIAGNOSTIC_SNAPSHOT_TICKS.length) {
            clearReloadReattachDiagnostics();
            return;
        }

        reloadReattachDiagnosticAgeTicks++;
    }

    private void logReloadReattachDiagnosticSnapshot(ServerLevel serverLevel, int snapshotTick) {
        SableInteractiveContraptionBackend.ReloadReattachDiagnosticsSnapshot snapshot =
                sableBackend.reloadReattachDiagnosticsSnapshot(serverLevel, worldPosition, getFacingDirection());
        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRVB event=reload-post-reattach-snapshot pos={} gameTime={} startGameTime={} snapshotTick={} facing={} running={} assembledBlockCount={} activeSubLevelId={} attachedId={} shipId={} constraintValid={} action={} anchorWorldError={} normalWorldError={} thresholdBreach={} attachedLogicalPose={} attachedLastPose={} shipWorldTransform={} baseAnchorWorld={} attachedAnchorWorld={} attachedCom={} attachedRotationPoint={} comToContactLocal={} anchorToComWorld={} linearVelocity={} angularVelocity={} velocityNonZeroSinceReset={} angularVelocityNonZeroSinceReset={}",
                worldPosition,
                level == null ? -1L : level.getGameTime(),
                reloadReattachDiagnosticStartGameTime,
                snapshotTick,
                getFacingDirection(),
                running,
                assembledBlockCount,
                snapshot.activeSubLevelId(),
                snapshot.attachedId(),
                snapshot.shipId(),
                snapshot.constraintHandleValid(),
                snapshot.action(),
                formatDouble(snapshot.anchorWorldError()),
                formatDouble(snapshot.normalWorldError()),
                snapshot.thresholdBreach(),
                formatNullableString(snapshot.attachedLogicalPose()),
                formatNullableString(snapshot.attachedLastPose()),
                snapshot.shipWorldTransform(),
                formatVector(snapshot.baseAnchorWorld()),
                formatVector(snapshot.attachedAnchorWorld()),
                formatVector(snapshot.attachedCom()),
                formatVector(snapshot.attachedRotationPoint()),
                formatVector(snapshot.comToContactLocal()),
                formatVector(snapshot.anchorToComWorld()),
                formatVector(snapshot.linearVelocity()),
                formatVector(snapshot.angularVelocity()),
                snapshot.velocityNonZeroSinceReset(),
                snapshot.angularVelocityNonZeroSinceReset());
    }

    private static String formatVector(@Nullable Vector3d vector) {
        if (vector == null) {
            return "<none>";
        }
        return String.format(Locale.ROOT, "(%.6f,%.6f,%.6f)", vector.x(), vector.y(), vector.z());
    }

    private static String formatDouble(double value) {
        if (!Double.isFinite(value)) {
            return "<unknown>";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String formatNullableString(@Nullable String value) {
        return value == null ? "<unknown>" : value;
    }

    private void hardFallbackAfterSableFailure(SableInteractiveContraptionBackend.RefreshFailureReason reason) {
        boolean runningBefore = running;
        Direction facingBefore = getFacingDirection();
        UUID activeSubLevelIdBefore = sableBackend.getActiveSubLevelId();
        int recoveryTicksBefore = sableLoadRecoveryTicks;
        boolean assembleNextTickBefore = assembleNextTick;
        boolean pendingDisassembleAfterZeroBefore = pendingDisassembleAfterZero;
        int pendingDisassembleStableTicksBefore = pendingDisassembleStableTicks;

        running = false;
        movedContraption = null;
        assembleNextTick = false;
        pendingDisassembleAfterZero = false;
        pendingDisassembleStableTicks = 0;
        disassemblyReturnMeasuredAngleDeg = 0.0F;
        verticalAutoParkedByMissingMarker = false;
        sableLoadRecoveryTicks = 0;
        logSableClearCauseDiagnostics(
                "hard-fallback-before-clear",
                "refresh-failed-" + reason,
                runningBefore,
                facingBefore,
                activeSubLevelIdBefore,
                recoveryTicksBefore,
                assembleNextTickBefore,
                pendingDisassembleAfterZeroBefore,
                pendingDisassembleStableTicksBefore);
        sableReloadReattachGraceTicks = 0;
        lastSableRefreshFailureReason = reason;
        lastSableRefreshFailureLogTick = level == null ? Long.MIN_VALUE : level.getGameTime();
        sableBackend.clearState();
        clearAssembledBlockCount();
        stopAllMotionState();
        updateGeneratedRotation();
        updateVisualRunning(false);
        setChanged();
        if (canSendData()) sendData();
    }

    private void updateVisualRunning(boolean runningVisual) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (runningVisual == lastVisualRunning) {
            return;
        }

        lastVisualRunning = runningVisual;

        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(WindRotoVerticalBlock.RUNNING) && state.getValue(WindRotoVerticalBlock.RUNNING) != runningVisual) {
            level.setBlock(worldPosition, state.setValue(WindRotoVerticalBlock.RUNNING, runningVisual), 3);
        }
    }

    private void forceVisualRunningOff() {
        if (level == null || level.isClientSide) {
            return;
        }

        lastVisualRunning = false;

        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(WindRotoVerticalBlock.RUNNING) && state.getValue(WindRotoVerticalBlock.RUNNING)) {
            level.setBlock(worldPosition, state.setValue(WindRotoVerticalBlock.RUNNING, false), 3);
        }
    }

    private void zeroOutCreateWindmillContribution() {
        try {
            zeroIntFieldIfPresent(this, "sailBlockCount");
            zeroIntFieldIfPresent(this, "sailBlocks");
            zeroIntFieldIfPresent(this, "sails");
            zeroIntFieldIfPresent(this, "sailCount");
            zeroIntFieldIfPresent(this, "numSails");
            zeroIntFieldIfPresent(this, "windmillSails");

            zeroFloatFieldIfPresent(this, "windmillEfficiency");
            zeroFloatFieldIfPresent(this, "efficiency");
            zeroFloatFieldIfPresent(this, "sailEfficiency");
            zeroFloatFieldIfPresent(this, "windMultiplier");
            zeroFloatFieldIfPresent(this, "sailMultiplier");

            zeroFloatFieldIfPresent(this, "windmillCapacity");
            zeroFloatFieldIfPresent(this, "windmillStressCapacity");
            zeroFloatFieldIfPresent(this, "windmillStress");
            zeroFloatFieldIfPresent(this, "cachedStressCapacity");
            zeroFloatFieldIfPresent(this, "cachedCapacity");
        } catch (Throwable ignored) {
        }
    }

    private static void zeroIntFieldIfPresent(Object target, String fieldName) {
        Field f = findFieldInHierarchy(target.getClass(), fieldName);
        if (f == null) {
            return;
        }

        try {
            f.setAccessible(true);
            if (f.getType() == int.class) {
                f.setInt(target, 0);
            } else if (f.getType() == Integer.class) {
                f.set(target, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void zeroFloatFieldIfPresent(Object target, String fieldName) {
        Field f = findFieldInHierarchy(target.getClass(), fieldName);
        if (f == null) {
            return;
        }

        try {
            f.setAccessible(true);
            if (f.getType() == float.class) {
                f.setFloat(target, 0.0F);
            } else if (f.getType() == Float.class) {
                f.set(target, 0.0F);
            } else if (f.getType() == double.class) {
                f.setDouble(target, 0.0D);
            } else if (f.getType() == Double.class) {
                f.set(target, 0.0D);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Field findFieldInHierarchy(Class<?> start, String name) {
        Class<?> c = start;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}
