package com.proventure.twistermill.blockentity;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.bearing.WindRotoMovementBehaviour;
import com.proventure.twistermill.block.custom.WindRotoBlock;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.proventure.twistermill.diagnostics.TwisterMillReseatService;
import com.proventure.twistermill.weather.TwisterWeatherBackendValidator;
import com.proventure.twistermill.weather.TwisterWeatherService;
import com.proventure.twistermill.weather.WindSample;
import com.proventure.twistermill.util.CreateWindmillReflectionCleaner;
import com.simibubi.create.AllKeys;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.ClientSubLevelAccess;
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
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WindRotoBlockEntity extends MechanicalBearingBlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int OUTSIDE_CHECK_TICKS = 100;
    private static final int BOUND_SERVO_VALIDATE_TICKS = 20;
    private static final int MAX_MISSING_BOUND_SERVO_SAMPLES = 5;
    private static final int OVERSTRESS_CLEAR_CHECKS_REQUIRED = 40;
    private static final float SMOOTH_ALPHA = 0.25F;
    private static final float SU_PER_RPM = 12.8F;
    private static final float MAX_WIND_SPEED = 3.0F;
    private static final int MIN_RPM = 5;
    private static final int PENDING_BRAKE_HOLD_TICKS = 0;
    private static final float PENDING_CREEP_TO_ZERO_RPM = 12.0F; //rotation speed to zero point disassemble pos target (default 2.0F)
    private static final int PENDING_ZERO_HOLD_TICKS = 10;       //break before disassembly
    private static final float PENDING_ZERO_EPSILON = 2.0F;     //offset in degrees when zero point is nearZero:true (default 0.5F)
    private static final double SERVO_STIFFNESS_PER_INERTIA = 1600.0;  //settings for contraption forced target angle high ->strong
    private static final double SERVO_DAMPING_PER_INERTIA = 40.0;  //settings for contraption
    private static final double MIN_EFFECTIVE_INERTIA = 10.0;   //settings for contraption clamping for to
    private static final String TAG_SABLE_ACTIVE = "SableActive";
    private static final String TAG_SABLE_SUBLEVEL_ID = "SableSubLevelId";
    private static final String TAG_BOUND_SERVO_COUNT = "BoundServoCount";
    private static final String TAG_BOUND_SERVO_POS_PREFIX = "BoundServoPos";
    private static final String TAG_BOUND_SERVO_INV_PREFIX = "BoundServoInv";
    private static final String TAG_BOUND_SERVO_ANGLE_PREFIX = "BoundServoAngle";
    private static final String TAG_BOUND_SERVO_BLOCKS_PREFIX = "BoundServoBlocks";
    private static final String TAG_CONTRAPTION_BLOCK_COUNT = "ContraptionBlockCount";
    private static final String TAG_SAIL_LIKE_BLOCK_COUNT = "SailLikeBlockCount";
    private static final String TAG_PENDING_DISASSEMBLE_BRAKE = "PendingDisassembleBrake";
    private static final String TAG_PENDING_DISASSEMBLE_CREEP_TO_ZERO = "PendingDisassembleCreepToZero";

    private static final TagKey<Block> CREATE_WINDMILL_SAILS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", "windmill_sails"));
    private static final TagKey<Block> TWISTERMILL_SAIL_LIKE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("twistermill", "sail_like"));

    private record ForcedChunkKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
    }

    private static final Map<ForcedChunkKey, Integer> FORCED_CHUNK_REF_COUNTS = new HashMap<>();

    private static class BoundServoRef {
        private final BlockPos pos;
        private final boolean inverted;
        private float lastKnownAngle;
        private int lastKnownContraptionBlocks;
        private long lastRuntimeUpdateGameTime;
        private int missingSamples;

        private BoundServoRef(BlockPos pos, boolean inverted) {
            this.pos = pos.immutable();
            this.inverted = inverted;
            this.lastKnownAngle = 0.0F;
            this.lastKnownContraptionBlocks = 0;
            this.lastRuntimeUpdateGameTime = Long.MIN_VALUE;
            this.missingSamples = 0;
        }
    }

    private boolean needsInit = true;
    private boolean isOutsideCached = false;

    private float lastWindSpeed = 0.0F;
    private float windSmoothed = Float.NaN;

    private int targetRpmCached = 0;
    private int generatedRpm = 0;
    private float generatedSu = 0.0F;

    private long nextOutsideCheckAt = 0;
    private long nextWindSampleAt = 0;
    private long nextRampAt = 0;
    private long nextBoundServoValidateAt = 0L;

    private int lastSentRpm = Integer.MIN_VALUE;
    private boolean lastSentOutside = false;
    private float lastSentSu = Float.NaN;
    private int lastSentContraptionBlockCount = Integer.MIN_VALUE;
    private int lastSentSailLikeBlockCount = Integer.MIN_VALUE;

    private boolean lastVisualRunning = false;

    private int lastComparatorLevel = Integer.MIN_VALUE;

    private ScrollOptionBehaviour<WindmillBearingBlockEntity.RotationDirection> movementDirection;

    private int lastExternalRedstone = 0;
    private boolean stoppedByRedstone = false;
    private int lastRawExternalRedstone = 0;
    private boolean rotationBlockedByOverstress = false;
    private int consecutiveOverstressClearChecks = 0;

    private boolean chunkForceRegistered = false;
    private ForcedChunkKey forcedChunkKey = null;

    private int accumulatedContraptionBlocks = 0;
    private int syncedContraptionBlockCount = 0;
    private int syncedSailLikeBlockCount = 0;
    private final List<BoundServoRef> boundServos = new ArrayList<>();
    private final SableInteractiveContraptionBackend sableBackend = new SableInteractiveContraptionBackend(TwisterMillDiagnostics.Target.WRB);
    private final RememberedSableShipMemory rememberedShipMemory = new RememberedSableShipMemory();
    private boolean skipDisassembleDuringRemove = false;

    private boolean manuallyDisassembled = false;
    private boolean pendingDisassembleBrake = false;
    private int pendingDisassembleBrakeHoldTicks = 0;
    private boolean pendingDisassembleCreepToZero = false;
    private int pendingDisassembleZeroHoldTicks = 0;
    private float pendingCreepDirection = 1.0F;
    private float pendingCreepPreviousNormalizedAngle = 0.0F;
    private boolean pendingCreepHoldingAtZero = false;
    private transient boolean diagnosticFirstRefreshLogged = false;
    private transient boolean diagnosticRefreshFailureLogged = false;

    public WindRotoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_ROTO_BE.get(), pos, state);
        forceRotationModePlace();
    }

    private boolean canSendData() {
        return level instanceof ServerLevel serverLevel
                && serverLevel.getServer().isRunning();
    }

    private void logSableLifecycleDiagnostics(String event) {
        if (!TwisterMillDiagnostics.isWrbLoggingEnabled()) {
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

        LOGGER.info("[WrbWrvbLifecycleDiag] type=WRB event={} pos={} gameTime={} facing={} running={} sableActive={} activeSubLevelId={} assembleNextTick={} noLoadRecovery={} movedContraptionPresent={} pendingBrake={} pendingCreepToZero={} boundServoCount={}",
                event,
                worldPosition,
                level == null ? -1L : level.getGameTime(),
                getFacingDirection(),
                running,
                sableBackend.isActive(),
                sableBackend.getActiveSubLevelId(),
                assembleNextTick,
                true,
                movedContraption != null,
                pendingDisassembleBrake,
                pendingDisassembleCreepToZero,
                boundServos.size());
    }

    public void queueAssemblePublic() {
        pendingDisassembleBrake = false;
        pendingDisassembleBrakeHoldTicks = 0;
        pendingDisassembleCreepToZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        pendingCreepDirection = 1.0F;
        pendingCreepPreviousNormalizedAngle = 0.0F;
        pendingCreepHoldingAtZero = false;
        manuallyDisassembled = false;
        queueAssemble();
    }

    public void disassemblePublic() {
        if (running && sableBackend.isActive()) {
            float currentSpeed = getCommandedRotationSpeed();
            if (currentSpeed != 0.0F) {
                pendingCreepDirection = Math.signum(currentSpeed);
            } else {
                pendingCreepDirection = Math.signum(getAngleSpeedDirection());
                if (pendingCreepDirection == 0.0F) {
                    pendingCreepDirection = 1.0F;
                }
            }

            pendingDisassembleBrake = true;
            pendingDisassembleBrakeHoldTicks = 0;
            pendingDisassembleCreepToZero = false;
            pendingDisassembleZeroHoldTicks = 0;
            pendingCreepHoldingAtZero = false;
            pendingCreepPreviousNormalizedAngle = 0.0F;
            assembleNextTick = false;
            updateGeneratedRotation();
            setChanged();
            if (canSendData()) {
                sendData();
            }
            return;
        }

        manuallyDisassembled = true;
        assembleNextTick = false;
        disassemble();
    }

    public int getComparatorOutputLevel() {
        return computeComparatorLevel();
    }

    public int getGeneratedRpmForDisplay() {
        return generatedRpm;
    }

    public float getGeneratedSuForDisplay() {
        return generatedSu;
    }

    public float getRawWindSpeedForDisplay() {
        return lastWindSpeed;
    }

    public float getSmoothedWindSpeedForDisplay() {
        return Float.isNaN(windSmoothed) ? lastWindSpeed : windSmoothed;
    }

    public boolean isOutsideCachedForDisplay() {
        return isOutsideCached;
    }

    public int getExternalRedstoneInputForDisplay() {
        return Mth.clamp(lastExternalRedstone, 0, 15);
    }

    public boolean isStoppedByRedstoneForDisplay() {
        return stoppedByRedstone;
    }

    public int getBoundServoCount() {
        return boundServos.size();
    }

    public boolean hasBothServoTypes() {
        boolean hasNormal = false;
        boolean hasInverted = false;

        for (BoundServoRef ref : boundServos) {
            if (ref.inverted) {
                hasInverted = true;
            } else {
                hasNormal = true;
            }

            if (hasNormal && hasInverted) {
                return true;
            }
        }

        return false;
    }

    public float getAverageBoundServoAngle() {
        if (boundServos.isEmpty()) {
            return 0.0F;
        }

        float sum = 0.0F;
        int count = 0;

        for (BoundServoRef ref : boundServos) {
            sum += Math.abs(ref.lastKnownAngle);
            count++;
        }

        return count == 0 ? 0.0F : sum / count;
    }

    public int getTotalContraptionBlockCount() {
        updateAccumulatedContraptionBlocks();
        int total = syncedContraptionBlockCount;
        if (sableBackend.isActive()) {
            return total;
        }

        for (BoundServoRef ref : boundServos) {
            total += Math.max(0, ref.lastKnownContraptionBlocks);
        }

        return total;
    }

    public int getSailLikeBlockCountForDisplay() {
        return syncedSailLikeBlockCount;
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

    public boolean addBoundServo(BlockPos servoPos, boolean inverted) {
        if (servoPos == null || servoPos.equals(worldPosition)) {
            return false;
        }

        for (int i = 0; i < boundServos.size(); i++) {
            BoundServoRef existing = boundServos.get(i);
            if (!existing.pos.equals(servoPos)) {
                continue;
            }

            if (existing.inverted == inverted) {
                setServoBoundFlag(servoPos, inverted, true);
                return false;
            }

            setServoBoundFlag(existing.pos, existing.inverted, false);

            BoundServoRef replacement = new BoundServoRef(servoPos, inverted);
            boundServos.set(i, replacement);

            setServoBoundFlag(servoPos, inverted, true);
            setChanged();
            if (canSendData()) {
                sendData();
            }
            refreshGeneratedSuForBoundServoStateChange();
            return true;
        }

        BoundServoRef ref = new BoundServoRef(servoPos, inverted);
        boundServos.add(ref);

        setServoBoundFlag(servoPos, inverted, true);
        setChanged();
        if (canSendData()) {
            sendData();
        }
        refreshGeneratedSuForBoundServoStateChange();
        return true;
    }

    public void clearBoundServos() {
        if (boundServos.isEmpty()) {
            return;
        }

        for (BoundServoRef ref : boundServos) {
            setServoBoundFlag(ref.pos, ref.inverted, false);
        }

        boundServos.clear();
        setChanged();
        if (canSendData()) {
            sendData();
        }
        refreshGeneratedSuForBoundServoStateChange();
    }

    public void updateBoundServoRuntimeFromServo(BlockPos originalServoPos, boolean inverted, float angleDegrees, int contraptionBlocks) {
        if (originalServoPos == null) {
            return;
        }

        long time = level != null ? level.getGameTime() : 0L;

        for (BoundServoRef ref : boundServos) {
            if (ref.pos.equals(originalServoPos) && ref.inverted == inverted) {
                ref.lastKnownAngle = Math.abs(angleDegrees);
                ref.lastKnownContraptionBlocks = Math.max(0, contraptionBlocks);
                ref.lastRuntimeUpdateGameTime = time;
                ref.missingSamples = 0;
                setChanged();
                if (canSendData()) {
                    sendData();
                }
                refreshGeneratedSuForBoundServoStateChange();
                return;
            }
        }
    }

    public void removeBoundServoRuntimeFromServo(BlockPos originalServoPos, boolean inverted) {
        if (originalServoPos == null) {
            return;
        }

        boolean removed = boundServos.removeIf(ref ->
                ref.pos.equals(originalServoPos) && ref.inverted == inverted
        );

        if (removed) {
            setChanged();
            if (canSendData()) {
                sendData();
            }
            refreshGeneratedSuForBoundServoStateChange();
        }
    }

    private void setServoBoundFlag(BlockPos pos, boolean inverted, boolean bound) {
        if (level == null) {
            return;
        }

        var be = level.getBlockEntity(pos);

        if (!inverted && be instanceof ServoTwisterBlockEntity servo) {
            servo.setWindRotoBinding(bound, level.dimension(), worldPosition, pos);
        } else if (inverted && be instanceof InvServoTwisterBlockEntity invServo) {
            invServo.setWindRotoBinding(bound, level.dimension(), worldPosition, pos);
        }
    }

    private void validateBoundServos(long time) {
        if (time < nextBoundServoValidateAt) {
            return;
        }

        nextBoundServoValidateAt = time + BOUND_SERVO_VALIDATE_TICKS;

        if (boundServos.isEmpty()) {
            return;
        }

        boolean changed = false;
        boolean runtimeChanged = false;

        for (int i = boundServos.size() - 1; i >= 0; i--) {
            BoundServoRef ref = boundServos.get(i);

            boolean foundInCurrentLevel = false;
            if (level != null) {
                var be = level.getBlockEntity(ref.pos);
                if (!ref.inverted && be instanceof ServoTwisterBlockEntity servo) {
                    foundInCurrentLevel = true;
                    float liveAngle = servo.getWindRotoBindingAngleDegrees();
                    int liveBlocks = servo.getBoundContraptionBlockCount();
                    if (Float.compare(ref.lastKnownAngle, liveAngle) != 0
                            || ref.lastKnownContraptionBlocks != liveBlocks) {
                        runtimeChanged = true;
                    }
                    ref.lastKnownAngle = liveAngle;
                    ref.lastKnownContraptionBlocks = liveBlocks;
                    ref.lastRuntimeUpdateGameTime = time;
                    ref.missingSamples = 0;
                } else if (ref.inverted && be instanceof InvServoTwisterBlockEntity invServo) {
                    foundInCurrentLevel = true;
                    float liveAngle = invServo.getWindRotoBindingAngleDegrees();
                    int liveBlocks = invServo.getBoundContraptionBlockCount();
                    if (Float.compare(ref.lastKnownAngle, liveAngle) != 0
                            || ref.lastKnownContraptionBlocks != liveBlocks) {
                        runtimeChanged = true;
                    }
                    ref.lastKnownAngle = liveAngle;
                    ref.lastKnownContraptionBlocks = liveBlocks;
                    ref.lastRuntimeUpdateGameTime = time;
                    ref.missingSamples = 0;
                }
            }

            if (foundInCurrentLevel) {
                continue;
            }

            if (ref.lastRuntimeUpdateGameTime != Long.MIN_VALUE
                    && time - ref.lastRuntimeUpdateGameTime <= BOUND_SERVO_VALIDATE_TICKS * 2L) {
                continue;
            }

            ref.missingSamples++;
            if (ref.missingSamples >= MAX_MISSING_BOUND_SERVO_SAMPLES) {
                boundServos.remove(i);
                changed = true;
            }
        }

        if (changed || runtimeChanged) {
            setChanged();
            if (canSendData()) {
                sendData();
            }
            refreshGeneratedSuForBoundServoStateChange();
        }
    }

    private int computeComparatorLevel() {
        int signal = 0;
        if (lastVisualRunning) {
            signal = computeNormalComparatorLevelFromRpm(generatedRpm);
        }
        signal = Mth.clamp(signal, 0, 15);
        if (TwisterMillConfig.INVERT_WIND_ROTO_COMPARATOR.get()) {
            signal = 15 - signal;
        }
        return signal;
    }

    private static int computeNormalComparatorLevelFromRpm(int rpm) {
        if (rpm < 3) {
            return 0;
        }
        if (rpm < 8) {
            return 1;
        }
        if (rpm < 16) {
            return 2;
        }
        if (rpm < 24) {
            return 3;
        }
        if (rpm < 40) {
            return 4;
        }
        if (rpm < 70) {
            return 5;
        }
        if (rpm < 100) {
            return 6;
        }
        if (rpm < 120) {
            return 7;
        }
        if (rpm <= 136) {
            return 8;
        }
        if (rpm <= 153) {
            return 9;
        }
        if (rpm <= 170) {
            return 10;
        }
        if (rpm <= 187) {
            return 11;
        }
        if (rpm <= 204) {
            return 12;
        }
        if (rpm <= 221) {
            return 13;
        }
        if (rpm <= 238) {
            return 14;
        }
        return 15;
    }

    private void updateComparatorIfNeeded() {
        if (level == null || level.isClientSide) {
            return;
        }

        int now = computeComparatorLevel();
        if (now == lastComparatorLevel) {
            return;
        }

        lastComparatorLevel = now;

        BlockState state = getBlockState();
        level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        level.updateNeighborsAt(worldPosition, state.getBlock());
    }

    private void forceRotationModePlace() {
        if (movementMode != null) {
            movementMode.setValue(IControlContraption.RotationMode.ROTATE_NEVER_PLACE.ordinal());
        }
    }

    private void onDirectionChanged() {
        if (!running) {
            return;
        }

        if (level != null && !level.isClientSide) {
            updateGeneratedRotation();
        }
    }

    public void prepareWindRotoMovementBehaviour(List<BlockEntityBehaviour> behaviours) {
        behaviours.remove(movementMode);
        forceRotationModePlace();
    }

    public ValueBoxTransform getWindRotoMovementModeSlot() {
        return new DirectionalExtenderScrollOptionSlot((state, direction) -> {
            Direction facing = state.getValue(BearingBlock.FACING);

            if (facing == Direction.UP || facing == Direction.DOWN) {
                return direction == Direction.NORTH || direction == Direction.SOUTH;
            }

            if (facing.getAxis().isHorizontal()) {
                return direction == Direction.UP || direction == Direction.DOWN;
            }

            return false;
        });
    }

    public Direction getFacingDirectionForMovementGui() {
        return getFacingDirection();
    }

    public void onWindRotoMovementDirectionChanged() {
        onDirectionChanged();
    }

    public void setWindRotoMovementDirectionBehaviour(
            ScrollOptionBehaviour<WindmillBearingBlockEntity.RotationDirection> behaviour
    ) {
        this.movementDirection = behaviour;
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        boolean preserveAssemblyFlag = sableBackend.isActive();
        boolean previousAssembleNextTick = assembleNextTick;
        super.onSpeedChanged(prevSpeed);

        if (preserveAssemblyFlag) {
            assembleNextTick = previousAssembleNextTick;
        }
    }

    private float getAngleSpeedDirection() {
        if (movementDirection == null) {
            return 1;
        }

        WindmillBearingBlockEntity.RotationDirection dir =
                WindmillBearingBlockEntity.RotationDirection.values()[movementDirection.getValue()];
        return dir == WindmillBearingBlockEntity.RotationDirection.CLOCKWISE ? 1 : -1;
    }

    private boolean updateRotationCommandState() {
        boolean oldBlocked = rotationBlockedByOverstress;
        int oldClearChecks = consecutiveOverstressClearChecks;

        if (isOverStressed()) {
            rotationBlockedByOverstress = true;
            consecutiveOverstressClearChecks = 0;
        } else if (!rotationBlockedByOverstress) {
            consecutiveOverstressClearChecks = OVERSTRESS_CLEAR_CHECKS_REQUIRED;
        } else {
            consecutiveOverstressClearChecks++;

            if (consecutiveOverstressClearChecks >= OVERSTRESS_CLEAR_CHECKS_REQUIRED) {
                rotationBlockedByOverstress = false;
                consecutiveOverstressClearChecks = OVERSTRESS_CLEAR_CHECKS_REQUIRED;
            }
        }

        return oldBlocked != rotationBlockedByOverstress
                || oldClearChecks != consecutiveOverstressClearChecks;
    }

    private boolean shouldRotationCommandRun() {
        if (!running) {
            return false;
        }
        if (!isOutsideCached) {
            return false;
        }
        if (generatedRpm <= 0) {
            return false;
        }
        if (stoppedByRedstone) {
            return false;
        }
        if (rotationBlockedByOverstress) {
            return false;
        }
        return !isOverStressed();
    }

    private float getCommandedRotationSpeed() {
        if (pendingDisassembleBrake) {
            return 0.0F;
        }

        if (pendingDisassembleCreepToZero) {
            if (pendingCreepHoldingAtZero) {
                return 0.0F;
            }
            return pendingCreepDirection * PENDING_CREEP_TO_ZERO_RPM;
        }

        if (!shouldRotationCommandRun()) {
            return 0.0F;
        }

        return generatedRpm * getAngleSpeedDirection();
    }

    private Contraption getRootContraption() {
        if (movedContraption == null) {
            return null;
        }
        return movedContraption.getContraption();
    }

    private int getContraptionBlockCount() {
        updateAccumulatedContraptionBlocks();
        return accumulatedContraptionBlocks;
    }

    private int measureCurrentSailLikeBlockCountServer() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0;
        }

        if (sableBackend.isActive()) {
            return measureCurrentSableChildShipCounts(serverLevel).sailLikeBlocks();
        }

        Contraption root = getRootContraption();
        if (root == null || root.getBlocks() == null || root.getBlocks().isEmpty()) {
            return 0;
        }

        int sailLikeCount = 0;
        for (var info : root.getBlocks().values()) {
            if (info != null && info.state().is(TWISTERMILL_SAIL_LIKE)) {
                sailLikeCount++;
            }
        }

        return sailLikeCount;
    }

    private boolean updateSyncedSailLikeBlockCount() {
        if (!(level instanceof ServerLevel)) {
            return false;
        }

        int measured = Math.max(0, measureCurrentSailLikeBlockCountServer());
        if (measured == syncedSailLikeBlockCount) {
            return false;
        }

        syncedSailLikeBlockCount = measured;
        return true;
    }

    private void resetSyncedSailLikeBlockCount() {
        if (syncedSailLikeBlockCount != 0) {
            syncedSailLikeBlockCount = 0;
        }

        if (syncedContraptionBlockCount != 0) {
            syncedContraptionBlockCount = 0;
        }
    }

    private int getCurrentMeasuredContraptionBlockCount() {
        if (sableBackend.isActive() && level instanceof ServerLevel serverLevel) {
            return measureCurrentSableChildShipCounts(serverLevel).totalBlocks();
        }

        Contraption root = getRootContraption();
        if (root == null) {
            return 0;
        }

        Set<Contraption> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return countContraptionBlocksRecursive(root, visited);
    }

    private void updateAccumulatedContraptionBlocks() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (sableBackend.isActive()) {
            WindRotoChildShipSailCounter.CountResult measured = measureCurrentSableChildShipCounts(serverLevel);
            boolean changed = false;
            int totalBlocks = Math.max(0, measured.totalBlocks());
            int sailLikeBlocks = Math.max(0, measured.sailLikeBlocks());
            if (syncedContraptionBlockCount != totalBlocks) {
                syncedContraptionBlockCount = totalBlocks;
                changed = true;
            }
            if (totalBlocks > accumulatedContraptionBlocks) {
                accumulatedContraptionBlocks = totalBlocks;
                changed = true;
            }
            if (sailLikeBlocks != syncedSailLikeBlockCount) {
                syncedSailLikeBlockCount = sailLikeBlocks;
                changed = true;
            }
            if (changed) {
                setChanged();
            }
            return;
        }

        int measured = getCurrentMeasuredContraptionBlockCount();
        boolean sailLikeChanged = updateSyncedSailLikeBlockCount();
        boolean contraptionChanged = false;
        if (syncedContraptionBlockCount != measured) {
            syncedContraptionBlockCount = Math.max(0, measured);
            contraptionChanged = true;
        }
        if (measured > accumulatedContraptionBlocks) {
            accumulatedContraptionBlocks = measured;
            setChanged();
            return;
        }

        if (sailLikeChanged || contraptionChanged) {
            setChanged();
        }
    }

    private WindRotoChildShipSailCounter.CountResult measureCurrentSableChildShipCounts(ServerLevel serverLevel) {
        UUID activeSubLevelId = sableBackend.getActiveSubLevelId();
        if (activeSubLevelId == null) {
            return WindRotoChildShipSailCounter.CountResult.EMPTY;
        }

        return WindRotoChildShipSailCounter.countBlocksRecursive(serverLevel, activeSubLevelId, state -> state.is(TWISTERMILL_SAIL_LIKE));
    }

    private int countContraptionBlocksRecursive(Contraption contraption, Set<Contraption> visited) {
        if (contraption == null) {
            return 0;
        }

        if (!visited.add(contraption)) {
            return 0;
        }

        int total = contraption.getBlocks().size();

        AbstractContraptionEntity contraptionEntity = contraption.entity;
        if (contraptionEntity == null) {
            return total;
        }

        for (Entity passenger : contraptionEntity.getPassengers()) {
            if (passenger instanceof OrientedContraptionEntity oriented) {
                Contraption child = oriented.getContraption();
                if (child != null) {
                    total += countContraptionBlocksRecursive(child, visited);
                }
            }
        }

        return total;
    }

    private int getExternalRedstonePower() {
        if (level == null) {
            return 0;
        }
        return Mth.clamp(level.getBestNeighborSignal(worldPosition), 0, 15);
    }

    private int getCurrentEffectiveRedstoneSignal() {
        return Mth.clamp(getExternalRedstonePower(), 0, 15);
    }

    private boolean isPendingDisassembleFlowActive() {
        return pendingDisassembleBrake || pendingDisassembleCreepToZero;
    }

    private int updateRedstoneStateAndGetEffectiveSignal(int rawRs) {
        rawRs = Mth.clamp(rawRs, 0, 15);
        lastRawExternalRedstone = rawRs;
        return rawRs;
    }

    private static float getRedstoneBrakeFactor(int signal) {
        signal = Mth.clamp(signal, 0, 15);

        if (signal <= 0) {
            return 1.0F;
        }

        if (signal >= 15) {
            return 0.0F;
        }

        return Mth.lerp((signal - 1) / 13.0F, 0.90F, 0.10F);
    }

    private static int getRedstoneBrakeRampStep(int signal, int defaultRampStep) {
        signal = Mth.clamp(signal, 0, 15);

        if (signal <= 0) {
            return defaultRampStep;
        }

        if (signal >= 15) {
            return Math.max(defaultRampStep, 64);
        }

        return Math.max(defaultRampStep, Mth.floor(Mth.lerp(signal / 14.0F, 2.0F, 32.0F)));
    }

    private boolean isInsideSableSubLevel() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        try {
            SubLevel containing = Sable.HELPER.getContaining(serverLevel, worldPosition);
            return containing instanceof ServerSubLevel;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (level != null && !level.isClientSide && !isInsideSableSubLevel()) {
            ensureOwnChunkForced();
        }
        logSableLifecycleDiagnostics("on-load");
    }

    @Override
    public void onChunkUnloaded() {
        sableBackend.clearRuntimeForUnload();
        releaseOwnChunkForced();
        super.onChunkUnloaded();
    }

    @Override
    public void remove() {
        skipDisassembleDuringRemove = true;

        if (level != null && !level.isClientSide) {
            sableBackend.clearRuntimeForUnload();
            releaseOwnChunkForced();
        } else if (level != null) {
            sableBackend.clearClientFallback();
        }

        try {
            super.remove();
        } finally {
            skipDisassembleDuringRemove = false;
        }
    }

    public void releaseOwnChunkForced() {
        if (!(level instanceof ServerLevel serverLevel)) {
            chunkForceRegistered = false;
            forcedChunkKey = null;
            return;
        }

        if (!chunkForceRegistered || forcedChunkKey == null) {
            return;
        }

        ForcedChunkKey key = forcedChunkKey;

        synchronized (FORCED_CHUNK_REF_COUNTS) {
            int refs = FORCED_CHUNK_REF_COUNTS.getOrDefault(key, 0);

            if (refs <= 1) {
                FORCED_CHUNK_REF_COUNTS.remove(key);
                serverLevel.setChunkForced(key.chunkX(), key.chunkZ(), false);
            } else {
                FORCED_CHUNK_REF_COUNTS.put(key, refs - 1);
            }
        }

        chunkForceRegistered = false;
        forcedChunkKey = null;
    }

    private void ensureOwnChunkForced() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (isInsideSableSubLevel()) {
            releaseOwnChunkForced();
            return;
        }

        ForcedChunkKey key = new ForcedChunkKey(
                serverLevel.dimension(),
                worldPosition.getX() >> 4,
                worldPosition.getZ() >> 4
        );

        if (chunkForceRegistered && key.equals(forcedChunkKey)) {
            return;
        }

        if (chunkForceRegistered) {
            releaseOwnChunkForced();
        }

        synchronized (FORCED_CHUNK_REF_COUNTS) {
            int refs = FORCED_CHUNK_REF_COUNTS.getOrDefault(key, 0);

            if (refs <= 0) {
                serverLevel.setChunkForced(key.chunkX(), key.chunkZ(), true);
            }

            FORCED_CHUNK_REF_COUNTS.put(key, refs + 1);
        }

        chunkForceRegistered = true;
        forcedChunkKey = key;
    }

    public void queueAssemble() {
        if (manuallyDisassembled) {
            assembleNextTick = false;
            return;
        }

        assembleNextTick = true;

        if (level != null && !level.isClientSide) {
            if (!isInsideSableSubLevel()) {
                ensureOwnChunkForced();
            } else {
                releaseOwnChunkForced();
            }

            updateAccumulatedContraptionBlocks();

            int rs = getCurrentEffectiveRedstoneSignal();
            lastExternalRedstone = rs;
            stoppedByRedstone = rs >= 15;

            if (needsInit) {
                needsInit = false;
                updateIsOutside();
                long t = level.getGameTime();
                nextOutsideCheckAt = t + OUTSIDE_CHECK_TICKS;
                nextWindSampleAt = t;
                nextRampAt = t;
            } else {
                updateIsOutside();
            }

            recomputeNow(level.getGameTime(), false);
            updateRotationCommandState();
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();

            setChanged();
            syncIfNeeded();
            updateComparatorIfNeeded();
        } else {
            setChanged();
            if (canSendData()) {
                sendData();
            }
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        WindRotoMovementBehaviour.addBehaviours(this, behaviours);
    }

    @Override
    public void tick() {
        if (manuallyDisassembled) {
            assembleNextTick = false;
        }

        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        if (sableBackend.isActive()) {
            boolean refreshed = level instanceof ServerLevel serverLevel
                    && sableBackend.refresh(serverLevel, worldPosition, getFacingDirection());
            if (refreshed) {
                logSableLifecycleDiagnostics("refresh-success");
            } else {
                logSableLifecycleDiagnostics("refresh-failure");
                running = false;
                movedContraption = null;
                assembleNextTick = false;
                resetPendingDisassembleFlow();
                sableBackend.clearState();
                resetSyncedSailLikeBlockCount();
                updateGeneratedRotation();
                updateVisualRunning(false);
                setChanged();
                if (canSendData()) {
                    sendData();
                }
            }
        }

        if (pendingDisassembleBrake) {
            if (!running || !sableBackend.isActive()) {
                resetPendingDisassembleFlow();
                updateGeneratedRotation();
            } else {
                updateGeneratedRotation();
                if (getCommandedRotationSpeed() == 0.0F) {
                    pendingDisassembleBrakeHoldTicks++;
                } else {
                    pendingDisassembleBrakeHoldTicks = 0;
                }

                if (pendingDisassembleBrakeHoldTicks >= PENDING_BRAKE_HOLD_TICKS) {
                    pendingDisassembleBrake = false;
                    pendingDisassembleBrakeHoldTicks = 0;
                    pendingDisassembleCreepToZero = true;
                    pendingDisassembleZeroHoldTicks = 0;
                    pendingCreepHoldingAtZero = false;
                    pendingCreepPreviousNormalizedAngle = Mth.positiveModulo(angle, 360.0F);
                    updateGeneratedRotation();
                }
            }
        }

        if (pendingDisassembleCreepToZero) {
            if (!running || !sableBackend.isActive()) {
                resetPendingDisassembleFlow();
                updateGeneratedRotation();
            } else {
                float normalized = Mth.positiveModulo(angle, 360.0F);
                float distanceToZero = Math.min(normalized, 360.0F - normalized);
                boolean nearZero = distanceToZero <= PENDING_ZERO_EPSILON;

                boolean crossedZero;
                if (getCommandedRotationSpeed() >= 0.0F) {
                    crossedZero = normalized < pendingCreepPreviousNormalizedAngle;
                } else {
                    crossedZero = normalized > pendingCreepPreviousNormalizedAngle;
                }

                if (!pendingCreepHoldingAtZero && (nearZero || crossedZero)) {
                    pendingCreepHoldingAtZero = true;
                    angle = 0.0F;
                    applyRotation();
                    updateGeneratedRotation();
                }

                if (pendingCreepHoldingAtZero) {
                    updateGeneratedRotation();
                    if (getCommandedRotationSpeed() == 0.0F) {
                        pendingDisassembleZeroHoldTicks++;
                    } else {
                        pendingDisassembleZeroHoldTicks = 0;
                    }

                    if (pendingDisassembleZeroHoldTicks >= PENDING_ZERO_HOLD_TICKS) {
                        resetPendingDisassembleFlow();
                        manuallyDisassembled = true;
                        assembleNextTick = false;
                        disassemble();

                        running = false;
                        assembleNextTick = false;
                        updateGeneratedRotation();
                        setChanged();
                        if (canSendData()) {
                            sendData();
                        }
                        return;
                    }
                } else {
                    pendingCreepPreviousNormalizedAngle = normalized;
                    pendingDisassembleZeroHoldTicks = 0;
                    updateGeneratedRotation();
                }
            }
        }

        if (!isInsideSableSubLevel()) {
            ensureOwnChunkForced();
        } else {
            releaseOwnChunkForced();
        }

        updateAccumulatedContraptionBlocks();

        long time = level.getGameTime();
        int rawRs = getExternalRedstonePower();
        int effectiveRs = updateRedstoneStateAndGetEffectiveSignal(rawRs);

        if (effectiveRs != lastExternalRedstone) {
            lastExternalRedstone = effectiveRs;
            stoppedByRedstone = effectiveRs >= 15;
        }

        boolean rotationCommandStateChanged = updateRotationCommandState();

        validateBoundServos(time);

        int windUpdateTicks = step10(Mth.clamp(TwisterMillConfig.WIND_UPDATE_TICKS.get(), 10, 1000));
        int rpmUpdateTicks = step10(Mth.clamp(TwisterMillConfig.RPM_RAMP_TICKS.get(), 10, 1000));
        int rpmStep = Mth.clamp(TwisterMillConfig.RPM_RAMP_STEP.get(), 1, 64);
        int maxRpm = getConfiguredMaxRpm();

        if (needsInit) {
            needsInit = false;
            updateIsOutside();
            nextOutsideCheckAt = time + OUTSIDE_CHECK_TICKS;
            nextWindSampleAt = time;
            nextRampAt = time;
        } else if (time >= nextOutsideCheckAt) {
            updateIsOutside();
            nextOutsideCheckAt = time + OUTSIDE_CHECK_TICKS;
        }

        if (!isOutsideCached) {
            lastWindSpeed = 0.0F;
            windSmoothed = 0.0F;
            targetRpmCached = 0;

            if (generatedRpm != 0 || generatedSu != 0.0F) {
                generatedRpm = 0;
                refreshGeneratedSuFromState();

                if (running || assembleNextTick) {
                    updateGeneratedRotation();
                    zeroOutCreateWindmillContribution();
                }

                updateVisualRunning(false);
                setChanged();
                syncIfNeeded();
            }
        } else if (time >= nextWindSampleAt) {
            nextWindSampleAt = time + windUpdateTicks;

            WindSample windSample = TwisterWeatherService.sampleAtBlock(level, worldPosition);
            if (windSample.valid()) {
                lastWindSpeed = windSample.weather2WindSpeed();
            } else {
                lastWindSpeed = 0.0F;
            }

            lastWindSpeed = Mth.clamp(lastWindSpeed, 0.0F, MAX_WIND_SPEED);

            if (Float.isNaN(windSmoothed)) {
                windSmoothed = lastWindSpeed;
            } else {
                windSmoothed = windSmoothed + (lastWindSpeed - windSmoothed) * SMOOTH_ALPHA;
            }

            windSmoothed = Mth.clamp(windSmoothed, 0.0F, MAX_WIND_SPEED);

            targetRpmCached = windToRpm1Step(windSmoothed, maxRpm);
        }

        int targetRpm = Math.round((isOutsideCached ? targetRpmCached : 0) * getRedstoneBrakeFactor(effectiveRs));

        if (time >= nextRampAt) {
            nextRampAt = time + rpmUpdateTicks;

            int effectiveRampStep = generatedRpm > targetRpm
                    ? getRedstoneBrakeRampStep(effectiveRs, rpmStep)
                    : rpmStep;

            int newRpm = approachInt(generatedRpm, targetRpm, effectiveRampStep);
            newRpm = Mth.clamp(newRpm, 0, maxRpm);

            if (effectiveRs == 0 && newRpm > 1 && newRpm < MIN_RPM) {
                newRpm = MIN_RPM;
            }

            if (newRpm != generatedRpm) {
                generatedRpm = newRpm;
                refreshGeneratedSuFromState();

                if (running || assembleNextTick) {
                    updateGeneratedRotation();
                    zeroOutCreateWindmillContribution();
                }

                setChanged();
                syncIfNeeded();
            }
        }

        if (!running && !assembleNextTick) {
            syncIfNeeded();
            updateVisualRunning(false);
            zeroOutCreateWindmillContribution();
            updateComparatorIfNeeded();
            return;
        }

        if (running && !isOutsideCached && generatedRpm != 0) {
            generatedRpm = 0;
            refreshGeneratedSuFromState();

            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();

            updateVisualRunning(false);
            setChanged();
            syncIfNeeded();
        }

        if (rotationCommandStateChanged && (running || assembleNextTick)) {
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();
            setChanged();
        }

        syncIfNeeded();

        updateVisualRunning(shouldRotationCommandRun());
        zeroOutCreateWindmillContribution();
        updateComparatorIfNeeded();
    }

    @Override
    public float getGeneratedSpeed() {
        return getCommandedRotationSpeed();
    }



    @Override
    public float calculateAddedStressCapacity() {
        if (!running || generatedRpm <= 0 || generatedSu <= 0.0F) {
            this.lastCapacityProvided = 0.0F;
            return 0.0F;
        }

        float capacityPerRpm = generatedSu / generatedRpm;
        this.lastCapacityProvided = capacityPerRpm;
        return capacityPerRpm;
    }

    @Override
    protected boolean isWindmill() {
        return true;
    }

    @Override
    public void assemble() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (running) {
            return;
        }

        SableInteractiveContraptionBackend.AssemblyResult assembly = sableBackend.tryAssemble(
                serverLevel,
                worldPosition,
                getFacingDirection(),
                false,
                exception -> {
                    lastException = exception;
                    if (exception != null && canSendData()) {
                        sendData();
                    }
                },
                RememberedSableShipMemory.enabledFor(getBlockState(), rememberedShipMemory)
        );

        if (assembly != null) {
            lastException = null;
            AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

            running = true;
            angle = 0.0F;
            sequencedAngleLimit = -1.0;
            assembleNextTick = false;
            movedContraption = null;
            accumulatedContraptionBlocks = Math.max(accumulatedContraptionBlocks, assembly.blockCount());
            updateAccumulatedContraptionBlocks();

            updateRotationCommandState();
            applyRotation();
            updateGeneratedRotation();
            setChanged();
            if (canSendData()) {
                sendData();
            }
            return;
        }

        super.assemble();
    }

    @Override
    public void disassemble() {
        if (skipDisassembleDuringRemove) {
            if (level != null && !level.isClientSide) {
                sableBackend.clearRuntimeForUnload();
            } else if (level != null) {
                sableBackend.clearClientFallback();
            }

            resetSyncedSailLikeBlockCount();
            movedContraption = null;
            running = false;
            assembleNextTick = false;
            resetPendingDisassembleFlow();
            rotationBlockedByOverstress = false;
            consecutiveOverstressClearChecks = 0;
            return;
        }

        if (!sableBackend.isActive()) {
            resetSyncedSailLikeBlockCount();
            super.disassemble();
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            running = false;
            movedContraption = null;
            assembleNextTick = false;
            resetPendingDisassembleFlow();
            angle = 0.0F;
            sequencedAngleLimit = -1.0;
            rotationBlockedByOverstress = false;
            consecutiveOverstressClearChecks = 0;
            sableBackend.clearClientFallback();
            resetSyncedSailLikeBlockCount();
            updateGeneratedRotation();
            updateVisualRunning(false);
            setChanged();
            if (canSendData()) {
                sendData();
            }
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
        resetPendingDisassembleFlow();
        rotationBlockedByOverstress = false;
        consecutiveOverstressClearChecks = 0;
        resetSyncedSailLikeBlockCount();
        updateGeneratedRotation();
        updateVisualRunning(false);
        setChanged();
        if (canSendData()) {
            sendData();
        }
    }

    @Override
    protected void applyRotation() {
        if (sableBackend.isActive() && level instanceof ServerLevel serverLevel) {
            Direction facing = getFacingDirection();

            boolean applied = sableBackend.applyMotor(
                    serverLevel,
                    worldPosition,
                    facing,
                    angle,
                    SERVO_STIFFNESS_PER_INERTIA,
                    SERVO_DAMPING_PER_INERTIA,
                    MIN_EFFECTIVE_INERTIA
            );

            if (!applied) {
                running = false;
                movedContraption = null;
                assembleNextTick = false;
                resetPendingDisassembleFlow();
                sableBackend.clearState();
                resetSyncedSailLikeBlockCount();
                updateGeneratedRotation();
                updateVisualRunning(false);
                setChanged();
                if (canSendData()) {
                    sendData();
                }
            }
            return;
        }

        super.applyRotation();
    }

    public TwisterMillReseatService.ReseatResult reseatFromDiagnostics(TwisterMillReseatService.Trigger trigger) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return TwisterMillReseatService.ReseatResult.failed(
                    TwisterMillReseatService.TargetType.WRB,
                    worldPosition,
                    "not-server-level"
            );
        }

        Float visualAngleBefore = computeSableTopVisualAngleDegrees(0.0F);
        angle = 0.0F;
        sequencedAngleLimit = -1.0;
        resetPendingDisassembleFlow();
        rotationBlockedByOverstress = false;
        consecutiveOverstressClearChecks = 0;
        updateGeneratedRotation();
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
            if (canSendData()) {
                sendData();
            }
        }
        Float visualAngleAfter = computeSableTopVisualAngleDegrees(0.0F);
        return new TwisterMillReseatService.ReseatResult(
                TwisterMillReseatService.TargetType.WRB,
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

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Outside", isOutsideCached);
        tag.putFloat("LastWind", lastWindSpeed);
        tag.putFloat("WindSmoothed", windSmoothed);
        tag.putInt("GenRpm", generatedRpm);
        tag.putFloat("GenSu", generatedSu);
        tag.putInt("TargetRpm", targetRpmCached);
        tag.putBoolean("VisualRunning", lastVisualRunning);
        tag.putInt("AccumulatedContraptionBlocks", accumulatedContraptionBlocks);
        tag.putInt(TAG_CONTRAPTION_BLOCK_COUNT, syncedContraptionBlockCount);
        tag.putInt(TAG_SAIL_LIKE_BLOCK_COUNT, syncedSailLikeBlockCount);
        tag.putBoolean("ManuallyDisassembled", manuallyDisassembled);

        tag.putInt("ExtRS", lastExternalRedstone);
        tag.putBoolean("StoppedByRS", stoppedByRedstone);
        tag.putBoolean("RotationBlockedByOverstress", rotationBlockedByOverstress);
        tag.putInt("OverstressClearChecks", consecutiveOverstressClearChecks);
        tag.putInt(TAG_BOUND_SERVO_COUNT, boundServos.size());
        if (clientPacket) {
            tag.putInt("RawExtRS", lastRawExternalRedstone);
            tag.putBoolean(TAG_PENDING_DISASSEMBLE_BRAKE, pendingDisassembleBrake);
            tag.putBoolean(TAG_PENDING_DISASSEMBLE_CREEP_TO_ZERO, pendingDisassembleCreepToZero);
        }

        for (int i = 0; i < boundServos.size(); i++) {
            BoundServoRef ref = boundServos.get(i);
            tag.putLong(TAG_BOUND_SERVO_POS_PREFIX + i, ref.pos.asLong());
            tag.putBoolean(TAG_BOUND_SERVO_INV_PREFIX + i, ref.inverted);
            tag.putFloat(TAG_BOUND_SERVO_ANGLE_PREFIX + i, ref.lastKnownAngle);
            tag.putInt(TAG_BOUND_SERVO_BLOCKS_PREFIX + i, ref.lastKnownContraptionBlocks);
        }

        sableBackend.write(tag, TAG_SABLE_ACTIVE, TAG_SABLE_SUBLEVEL_ID);
        if (!clientPacket) {
            rememberedShipMemory.write(tag);
        }
    }

    @Override
    public void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        forceRotationModePlace();

        if (tag.contains("Outside")) {
            isOutsideCached = tag.getBoolean("Outside");
        }
        if (tag.contains("LastWind")) {
            lastWindSpeed = tag.getFloat("LastWind");
        }
        if (tag.contains("WindSmoothed")) {
            windSmoothed = tag.getFloat("WindSmoothed");
        }
        if (tag.contains("GenRpm")) {
            generatedRpm = tag.getInt("GenRpm");
        }
        if (tag.contains("GenSu")) {
            generatedSu = tag.getFloat("GenSu");
        }
        if (tag.contains("TargetRpm")) {
            targetRpmCached = tag.getInt("TargetRpm");
        }
        if (tag.contains("VisualRunning")) {
            lastVisualRunning = tag.getBoolean("VisualRunning");
        }
        if (tag.contains("AccumulatedContraptionBlocks")) {
            accumulatedContraptionBlocks = Math.max(0, tag.getInt("AccumulatedContraptionBlocks"));
        }
        if (tag.contains(TAG_CONTRAPTION_BLOCK_COUNT)) {
            syncedContraptionBlockCount = Math.max(0, tag.getInt(TAG_CONTRAPTION_BLOCK_COUNT));
        } else {
            syncedContraptionBlockCount = accumulatedContraptionBlocks;
        }
        if (tag.contains(TAG_SAIL_LIKE_BLOCK_COUNT)) {
            syncedSailLikeBlockCount = Math.max(0, tag.getInt(TAG_SAIL_LIKE_BLOCK_COUNT));
        } else {
            syncedSailLikeBlockCount = 0;
        }
        if (tag.contains("ManuallyDisassembled")) {
            manuallyDisassembled = tag.getBoolean("ManuallyDisassembled");
        }
        if (tag.contains("ExtRS")) {
            lastExternalRedstone = Mth.clamp(tag.getInt("ExtRS"), 0, 15);
        }
        if (tag.contains("StoppedByRS")) {
            stoppedByRedstone = tag.getBoolean("StoppedByRS");
        }
        if (tag.contains("RotationBlockedByOverstress")) {
            rotationBlockedByOverstress = tag.getBoolean("RotationBlockedByOverstress");
        }
        if (tag.contains("OverstressClearChecks")) {
            consecutiveOverstressClearChecks = Mth.clamp(tag.getInt("OverstressClearChecks"), 0, OVERSTRESS_CLEAR_CHECKS_REQUIRED);
        }
        if (tag.contains(TAG_PENDING_DISASSEMBLE_BRAKE)) {
            pendingDisassembleBrake = tag.getBoolean(TAG_PENDING_DISASSEMBLE_BRAKE);
        }
        if (tag.contains(TAG_PENDING_DISASSEMBLE_CREEP_TO_ZERO)) {
            pendingDisassembleCreepToZero = tag.getBoolean(TAG_PENDING_DISASSEMBLE_CREEP_TO_ZERO);
        }
        lastRawExternalRedstone = tag.contains("RawExtRS") ? Mth.clamp(tag.getInt("RawExtRS"), 0, 15) : 0;

        boundServos.clear();
        int boundCount = Math.max(0, tag.getInt(TAG_BOUND_SERVO_COUNT));
        for (int i = 0; i < boundCount; i++) {
            String posKey = TAG_BOUND_SERVO_POS_PREFIX + i;
            if (!tag.contains(posKey)) {
                continue;
            }

            BlockPos pos = BlockPos.of(tag.getLong(posKey));
            boolean inverted = tag.getBoolean(TAG_BOUND_SERVO_INV_PREFIX + i);
            BoundServoRef ref = new BoundServoRef(pos, inverted);

            if (tag.contains(TAG_BOUND_SERVO_ANGLE_PREFIX + i)) {
                ref.lastKnownAngle = tag.getFloat(TAG_BOUND_SERVO_ANGLE_PREFIX + i);
            }
            if (tag.contains(TAG_BOUND_SERVO_BLOCKS_PREFIX + i)) {
                ref.lastKnownContraptionBlocks = Math.max(0, tag.getInt(TAG_BOUND_SERVO_BLOCKS_PREFIX + i));
            }

            boundServos.add(ref);
        }

        sableBackend.read(tag, TAG_SABLE_ACTIVE, TAG_SABLE_SUBLEVEL_ID);
        if (!clientPacket) {
            rememberedShipMemory.read(tag);
        }
        diagnosticFirstRefreshLogged = false;
        diagnosticRefreshFailureLogged = false;
        if (!clientPacket) {
            logSableLifecycleDiagnostics("read");
        }

        int maxRpm = getConfiguredMaxRpm();

        lastWindSpeed = Mth.clamp(lastWindSpeed, 0.0F, MAX_WIND_SPEED);
        if (!Float.isNaN(windSmoothed)) {
            windSmoothed = Mth.clamp(windSmoothed, 0.0F, MAX_WIND_SPEED);
        }

        targetRpmCached = Mth.clamp(targetRpmCached, 0, maxRpm);
        generatedRpm = Mth.clamp(generatedRpm, 0, maxRpm);

        refreshGeneratedSuFromState();

        zeroOutCreateWindmillContribution();
        updateComparatorIfNeeded();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean details = AllKeys.ctrlDown();

        int rpmDisplay = lastVisualRunning ? generatedRpm : 0;
        float suDisplay = lastVisualRunning ? generatedSu : 0.0F;

        CreateLang.translate("gui.goggles.generator_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.generated_su")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(suDisplay)
                .style(ChatFormatting.AQUA)
                .add(Component.literal("su").withStyle(ChatFormatting.AQUA))
                .space()
                .add(CreateLang.translateDirect("tooltip.twistermill.at_current_speed")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.generated_speed")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(rpmDisplay)
                .style(ChatFormatting.AQUA)
                .space()
                .add(CreateLang.translateDirect("tooltip.twistermill.unit_rpm"))
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
                    .add(CreateLang.translateDirect("tooltip.twistermill.key_ctrl")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip);
            return true;
        }

        float smooth = Float.isNaN(windSmoothed) ? lastWindSpeed : windSmoothed;
        int comparatorOutput = computeComparatorLevel();
        int normalComparatorSignal = TwisterMillConfig.INVERT_WIND_ROTO_COMPARATOR.get()
                ? 15 - comparatorOutput
                : comparatorOutput;

        CreateLang.translate("tooltip.twistermill.comparator_output_preview")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text("")
                .add(Component.literal(Integer.toString(comparatorOutput)).withStyle(ChatFormatting.GOLD))
                .space()
                .add(Component.literal("(" + formatComparatorRpmRange(normalComparatorSignal) + ")")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        if (TwisterMillConfig.getAllowedBlocksAboveForOutside() != 0) {
            CreateLang.translate("tooltip.twistermill.wind_outside")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);

            CreateLang.text(isOutsideCached ? "true" : "false")
                    .style(isOutsideCached ? ChatFormatting.GREEN : ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.wind_raw")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(lastWindSpeed)
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.wind_smoothed")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(smooth)
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        int brakeSignal = Mth.clamp(lastExternalRedstone, 0, 15);
        int rawBrakeSignal = Mth.clamp(lastRawExternalRedstone, 0, 15);
        CreateLang.translate("tooltip.twistermill.redstone_brake_state")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (rawBrakeSignal <= 0 && brakeSignal <= 0) {
            CreateLang.translate("tooltip.twistermill.redstone_brake_no_input")
                    .style(ChatFormatting.WHITE)
                    .forGoggles(tooltip, 1);
        } else if (brakeSignal <= 0) {
            CreateLang.translate("tooltip.twistermill.redstone_brake_inactive")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else if (brakeSignal >= 15) {
            CreateLang.translate("tooltip.twistermill.redstone_brake_stopped_rotation")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        } else {
            int expectedRpm = Math.round(targetRpmCached * getRedstoneBrakeFactor(brakeSignal));
            CreateLang.translate("tooltip.twistermill.redstone_brake_limiter_level")
                    .style(ChatFormatting.YELLOW)
                    .space()
                    .add(Component.literal(Integer.toString(brakeSignal)).withStyle(ChatFormatting.GOLD))
                    .space()
                    .add(Component.literal("(").withStyle(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.translateDirect("tooltip.twistermill.redstone_brake_expected_rpm")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .space()
                    .add(Component.literal(Integer.toString(expectedRpm)).withStyle(ChatFormatting.GOLD))
                    .space()
                    .add(CreateLang.translateDirect("tooltip.twistermill.unit_rpm")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .add(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.connected_servo")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(getBoundServoCount())
                .style(ChatFormatting.GOLD)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.average_servo_degrees")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(getAverageBoundServoAngle())
                .style(ChatFormatting.GOLD)
                .add(Component.literal("°"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.sail_like_blocks")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(getSailLikeBlockCountForDisplay())
                .style(ChatFormatting.GOLD)
                .forGoggles(tooltip, 1);

        return true;
    }

    private Component getTooltipContraptionStatusComponent() {
        boolean assembledActive = running || sableBackend.isActive();
        if (isPendingDisassembleFlowActive() && assembledActive) {
            return CreateLang.translateDirect("tooltip.twistermill.status.disassembling")
                    .withStyle(ChatFormatting.GOLD);
        }

        if (assembledActive) {
            return getAssembledStatusWithBlockCount(getTotalContraptionBlockCount());
        }

        if (isAssemblyReadyBlinkActive()) {
            return getAssemblyReadyBlinkStatusComponent();
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

    private static String formatComparatorRpmRange(int normalSignal) {
        normalSignal = Mth.clamp(normalSignal, 0, 15);
        return switch (normalSignal) {
            case 0 -> "0-2 RPM";
            case 1 -> "3-7 RPM";
            case 2 -> "8-15 RPM";
            case 3 -> "16-23 RPM";
            case 4 -> "24-39 RPM";
            case 5 -> "40-69 RPM";
            case 6 -> "70-99 RPM";
            case 7 -> "100-119 RPM";
            case 8 -> "120-136 RPM";
            case 9 -> "137-153 RPM";
            case 10 -> "154-170 RPM";
            case 11 -> "171-187 RPM";
            case 12 -> "188-204 RPM";
            case 13 -> "205-221 RPM";
            case 14 -> "222-238 RPM";
            default -> "239+ RPM";
        };
    }

    private float getSuFactor() {
        return Mth.clamp(TwisterMillConfig.SU_FACTOR.get().floatValue(), 0.1F, 100.0F);
    }

    private int getServoAngleSuMaxMultiplier() {
        return Mth.clamp(TwisterMillConfig.SERVO_ANGLE_SU_MAX_MULTIPLIER.get(), 1, 4);
    }

    private float computeServoAngleSuFactor(float avgAngleDegrees) {
        float avg = Math.abs(avgAngleDegrees);
        if (avg < 1.0F || avg > 45.0F) {
            return 1.0F;
        }

        int maxFactor = getServoAngleSuMaxMultiplier();
        if (maxFactor <= 1) {
            return 1.0F + (avg * 0.01F);
        }

        return 1.0F + ((avg / 45.0F) * (maxFactor - 1.0F));
    }

    private float computeStaticContraptionSu() {
        int blockCount = getContraptionBlockCount();
        if (blockCount <= 0) {
            return 0.0F;
        }
        return blockCount * getSuPerBlock();
    }

    private float getSuPerBlock() {
        return Mth.clamp(TwisterMillConfig.SU_PER_BLOCK.get(), 1, 1024);
    }

    private float computeSuFromRpm(int rpm) {
        int r = Math.abs(rpm);
        if (r <= 0) {
            return 0.0F;
        }

        float rpmSu = (SU_PER_RPM * getSuFactor()) * r;
        float baseSu = rpmSu + computeStaticContraptionSu();
        float servoAngleSuFactor = computeServoAngleSuFactor(getAverageBoundServoAngle());
        return baseSu * servoAngleSuFactor;
    }

    private void refreshGeneratedSuFromState() {
        generatedSu = computeSuFromRpm(generatedRpm);
    }

    private void refreshGeneratedSuForBoundServoStateChange() {
        if (level == null || level.isClientSide) {
            return;
        }

        float previousSu = generatedSu;
        refreshGeneratedSuFromState();

        if (Float.compare(previousSu, generatedSu) == 0) {
            return;
        }

        if (running || assembleNextTick) {
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();
        }

        setChanged();
        syncIfNeeded();
    }

    private static int step10(int v) {
        v = Mth.clamp(v, 10, 1000);
        return (v / 10) * 10;
    }

    private void updateIsOutside() {
        if (level == null) {
            isOutsideCached = false;
            return;
        }

        int maxY = level.getMaxBuildHeight() - 1;
        int allowedBlocksAbove = TwisterMillConfig.getAllowedBlocksAboveForOutside();

        if (allowedBlocksAbove <= 0) {
            isOutsideCached = true;
            return;
        }

        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ());
        int blockingBlocksAbove = 0;

        while (mp.getY() <= maxY) {
            BlockState s = level.getBlockState(mp);

            if (s.is(CREATE_WINDMILL_SAILS)) {
                mp.move(0, 1, 0);
                continue;
            }

            boolean solid = !s.isAir() && !s.getCollisionShape(level, mp).isEmpty();

            if (solid) {
                blockingBlocksAbove++;
                if (blockingBlocksAbove > allowedBlocksAbove) {
                    isOutsideCached = false;
                    return;
                }
            }

            mp.move(0, 1, 0);
        }

        isOutsideCached = true;
    }

    private void syncIfNeeded() {
        if (lastSentRpm != generatedRpm
                || lastSentOutside != isOutsideCached
                || Float.compare(lastSentSu, generatedSu) != 0
                || lastSentContraptionBlockCount != syncedContraptionBlockCount
                || lastSentSailLikeBlockCount != syncedSailLikeBlockCount) {
            lastSentRpm = generatedRpm;
            lastSentOutside = isOutsideCached;
            lastSentSu = generatedSu;
            lastSentContraptionBlockCount = syncedContraptionBlockCount;
            lastSentSailLikeBlockCount = syncedSailLikeBlockCount;
            if (canSendData()) {
                sendData();
            }
        }
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
        if (state.hasProperty(WindRotoBlock.RUNNING) && state.getValue(WindRotoBlock.RUNNING) != runningVisual) {
            level.setBlock(worldPosition, state.setValue(WindRotoBlock.RUNNING, runningVisual), 3);
        }
    }

    private void recomputeNow(long time, boolean snapGeneratedRpm) {
        int maxRpm = getConfiguredMaxRpm();

        if (!isOutsideCached) {
            targetRpmCached = 0;
            generatedRpm = 0;
            refreshGeneratedSuFromState();
            lastWindSpeed = 0.0F;
            windSmoothed = 0.0F;
            return;
        }

        final Level lvl = level;
        if (lvl == null) {
            lastWindSpeed = 0.0F;
        } else {
            WindSample windSample = TwisterWeatherService.sampleAtBlock(lvl, worldPosition);
            if (windSample.valid()) {
                lastWindSpeed = windSample.weather2WindSpeed();
            } else {
                lastWindSpeed = 0.0F;
            }
        }

        lastWindSpeed = Mth.clamp(lastWindSpeed, 0.0F, MAX_WIND_SPEED);

        if (Float.isNaN(windSmoothed)) {
            windSmoothed = lastWindSpeed;
        } else {
            windSmoothed = windSmoothed + (lastWindSpeed - windSmoothed) * SMOOTH_ALPHA;
        }

        windSmoothed = Mth.clamp(windSmoothed, 0.0F, MAX_WIND_SPEED);

        targetRpmCached = windToRpm1Step(windSmoothed, maxRpm);

        if (snapGeneratedRpm) {
            generatedRpm = targetRpmCached;
            if (generatedRpm > 1 && generatedRpm < MIN_RPM) {
                generatedRpm = MIN_RPM;
            }
        } else {
            generatedRpm = 0;
            nextRampAt = time;
        }

        refreshGeneratedSuFromState();

        nextWindSampleAt = time + step10(Mth.clamp(TwisterMillConfig.WIND_UPDATE_TICKS.get(), 10, 1000));
        nextRampAt = time + step10(Mth.clamp(TwisterMillConfig.RPM_RAMP_TICKS.get(), 10, 1000));
    }

    private static int approachInt(int current, int target, int maxStep) {
        if (current == target) {
            return current;
        }
        if (current < target) {
            return Math.min(target, current + maxStep);
        }
        return Math.max(target, current - maxStep);
    }

    private static int getConfiguredMaxRpm() {
        boolean weather2Loaded = TwisterWeatherBackendValidator.isWeather2Loaded();
        boolean pmweatherLoaded = TwisterWeatherBackendValidator.isPmweatherLoaded();

        if (pmweatherLoaded && !weather2Loaded) {
            return TwisterMillConfig.getPmweatherMaxRpm();
        }

        return TwisterMillConfig.getWeather2MaxRpm();
    }

    private void resetPendingDisassembleFlow() {
        pendingDisassembleBrake = false;
        pendingDisassembleBrakeHoldTicks = 0;
        pendingDisassembleCreepToZero = false;
        pendingDisassembleZeroHoldTicks = 0;
        pendingCreepDirection = 1.0F;
        pendingCreepPreviousNormalizedAngle = 0.0F;
        pendingCreepHoldingAtZero = false;
    }

    private static int windToRpm1Step(float wind, int maxRpm) {
        if (wind <= 0.0001f) {
            return 1;
        }

        int minRpm = MIN_RPM;
        if (maxRpm < minRpm) {
            maxRpm = minRpm;
        }

        float w = Mth.clamp(wind, 0.0F, MAX_WIND_SPEED);
        float t = w / MAX_WIND_SPEED;

        float rpmFloat = minRpm + t * (maxRpm - minRpm);
        int rpm = Math.round(rpmFloat);

        if (rpm < minRpm) {
            rpm = minRpm;
        }
        if (rpm > maxRpm) {
            rpm = maxRpm;
        }

        return rpm;
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

    private Direction getFacingDirection() {
        if (level == null) {
            return Direction.NORTH;
        }

        BlockState state = getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING)) {
            return Direction.NORTH;
        }

        return state.getValue(BlockStateProperties.FACING);
    }

    private void zeroOutCreateWindmillContribution() {
        CreateWindmillReflectionCleaner.zeroOutCreateWindmillContribution(this);
    }
}
