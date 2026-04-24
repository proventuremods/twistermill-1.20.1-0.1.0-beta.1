package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.block.custom.WindRotoBlock;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.debug.WindRotoDebugDumpService;
import com.proventure.twistermill.debug.WindRotoDebugTraceBuffer;
import com.proventure.twistermill.util.ServoBindingResolver;
import com.proventure.twistermill.util.WindRotoReflectionHelper;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.Pair;
import weather2.util.WindReader;
import weather2.weathersystem.WeatherManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class WindRotoBlockEntity extends MechanicalBearingBlockEntity {

    private static final int OUTSIDE_CHECK_TICKS = 100;
    private static final int SERVO_SAMPLE_TICKS = 20;
    private static final int MAX_MISSING_SERVO_SAMPLES = 10;
    private static final int STUCK_OVERSTRESS_REBUILD_TICKS = 100;
    private static final float SMOOTH_ALPHA = 0.25F;
    private static final float MAX_WIND_SPEED = 3.0F;
    private static final float MAX_SERVO_BIND_ANGLE = 45.0F;
    private static final float SERVO_FACTOR_EPSILON = 0.0001F;
    private static final float SYNC_FLOAT_EPSILON = 0.01F;
    private static final int MIN_RPM = 1;
    private static final float DEBUG_TRACE_FLOAT_EPSILON = 0.001F;
    private static final int REDSTONE_RPM_LIMIT_AT_SIGNAL_1 = 220;
    private static final int REDSTONE_RPM_LIMIT_AT_SIGNAL_14 = 20;

    private static final String NBT_BOUND_SERVOS = "BoundServos";
    private static final String NBT_SERVO_POS = "Pos";
    private static final String NBT_SERVO_TYPE = "Type";
    private static final String NBT_SERVO_LAST_ANGLE = "LastAngle";
    private static final String NBT_SERVO_VALID = "Valid";
    private static final String NBT_REL_X = "RelX";
    private static final String NBT_REL_Y = "RelY";
    private static final String NBT_REL_Z = "RelZ";
    private static final String NBT_MISSING_SAMPLES = "MissingSamples";
    private static final String SERVO_TYPE_NORMAL = "normal";
    private static final String SERVO_TYPE_INVERTED = "inverted";

    private static final TagKey<Block> CREATE_WINDMILL_SAILS = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("create", "windmill_sails")
    );

    private record ForcedChunkKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
    }

    private static final Map<ForcedChunkKey, Integer> FORCED_CHUNK_REF_COUNTS = new HashMap<>();

    private static class BoundServoData {
        private final BlockPos worldPos;
        private final BlockPos relativeOffset;
        private final boolean inverted;
        private float lastKnownAngle;
        private boolean valid;
        private int missingSamples;

        @Nullable
        private BlockPos lastResolvedPos;

        private BoundServoData(BlockPos worldPos, BlockPos relativeOffset, boolean inverted) {
            this.worldPos = worldPos;
            this.relativeOffset = relativeOffset;
            this.inverted = inverted;
            this.lastKnownAngle = 0.0F;
            this.valid = false;
            this.missingSamples = 0;
            this.lastResolvedPos = null;
        }

        private CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putLong(NBT_SERVO_POS, worldPos.asLong());
            tag.putString(NBT_SERVO_TYPE, inverted ? SERVO_TYPE_INVERTED : SERVO_TYPE_NORMAL);
            tag.putFloat(NBT_SERVO_LAST_ANGLE, lastKnownAngle);
            tag.putBoolean(NBT_SERVO_VALID, valid);
            tag.putInt(NBT_REL_X, relativeOffset.getX());
            tag.putInt(NBT_REL_Y, relativeOffset.getY());
            tag.putInt(NBT_REL_Z, relativeOffset.getZ());
            tag.putInt(NBT_MISSING_SAMPLES, missingSamples);
            return tag;
        }

        private static BoundServoData read(CompoundTag tag, BlockPos windRotoPos) {
            BlockPos pos = BlockPos.of(tag.getLong(NBT_SERVO_POS));
            boolean inverted = SERVO_TYPE_INVERTED.equals(tag.getString(NBT_SERVO_TYPE));
            BlockPos relativeOffset = tag.contains(NBT_REL_X) && tag.contains(NBT_REL_Y) && tag.contains(NBT_REL_Z)
                    ? new BlockPos(tag.getInt(NBT_REL_X), tag.getInt(NBT_REL_Y), tag.getInt(NBT_REL_Z))
                    : pos.subtract(windRotoPos);

            BoundServoData data = new BoundServoData(pos, relativeOffset, inverted);

            if (tag.contains(NBT_SERVO_LAST_ANGLE)) {
                data.lastKnownAngle = tag.getFloat(NBT_SERVO_LAST_ANGLE);
            }
            if (tag.contains(NBT_SERVO_VALID)) {
                data.valid = tag.getBoolean(NBT_SERVO_VALID);
            }
            if (tag.contains(NBT_MISSING_SAMPLES)) {
                data.missingSamples = Math.max(0, tag.getInt(NBT_MISSING_SAMPLES));
            }

            return data;
        }
    }

    private static class AllBlocksWindmillContraption extends BearingContraption {
        private final boolean windmill;

        public AllBlocksWindmillContraption(boolean isWindmill, Direction facing) {
            super(isWindmill, facing);
            this.windmill = isWindmill;
        }

        @Override
        public boolean assemble(Level world, BlockPos pos) throws AssemblyException {
            BlockPos offset = pos.relative(facing);
            if (!searchMovedStructure(world, offset, null)) {
                return false;
            }

            startMoving(world);
            expandBoundsAroundAxis(facing.getAxis());

            if (windmill && sailBlocks < 1) {
                throw AssemblyException.notEnoughSails(sailBlocks);
            }

            return !blocks.isEmpty();
        }

        @Override
        public void addBlock(Level level, BlockPos pos, Pair<StructureBlockInfo, BlockEntity> capture) {
            BlockPos localPos = pos.subtract(anchor);
            if (!getBlocks().containsKey(localPos)) {
                sailBlocks++;
            }
            super.addBlock(level, pos, capture);
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
    private long nextServoSampleAt = 0;
    private long nextStuckOverstressRebuildAt = 0;
    private int lastSentRpm = Integer.MIN_VALUE;
    private boolean lastSentOutside = false;
    private float lastSentWindSpeed = Float.NaN;
    private float lastSentWindSmoothed = Float.NaN;
    private float lastSentGeneratedSu = Float.NaN;
    private boolean lastSentVisualRunning = false;
    private int lastSentBoundServoCount = Integer.MIN_VALUE;
    private float lastSentAverageBoundServoAngle = Float.NaN;
    private float lastSentServoSuMultiplier = Float.NaN;
    private int lastSentBoundServoContraptionBlocks = Integer.MIN_VALUE;
    private boolean lastVisualRunning = false;
    private int lastComparatorLevel = Integer.MIN_VALUE;
    protected ScrollOptionBehaviour<WindmillBearingBlockEntity.RotationDirection> movementDirection;
    private int lastExternalRedstone = 0;
    private boolean stoppedByRedstone = false;
    private boolean chunkForceRegistered = false;
    private ForcedChunkKey forcedChunkKey = null;
    private final List<BoundServoData> boundServos = new ArrayList<>();
    private float lastAverageBoundServoAngle = 0.0F;
    private float lastServoSuMultiplier = 1.0F;
    private int lastBoundServoContraptionBlocks = 0;
    private long debugNextTraceSampleAt = 0L;
    private int debugDimensionId = Integer.MIN_VALUE;
    private int debugLastFlags = Integer.MIN_VALUE;
    private int debugLastGeneratedRpm = Integer.MIN_VALUE;
    private int debugLastTargetRpm = Integer.MIN_VALUE;
    private int debugLastExternalRedstone = Integer.MIN_VALUE;
    private int debugLastBoundServoCount = Integer.MIN_VALUE;
    private int debugLastComparatorLevel = Integer.MIN_VALUE;
    private int debugLastContraptionBlockCount = Integer.MIN_VALUE;
    private int debugLastBoundServoContraptionBlocks = Integer.MIN_VALUE;
    private int debugLastForcedChunkX = Integer.MIN_VALUE;
    private int debugLastForcedChunkZ = Integer.MIN_VALUE;
    private float debugLastGeneratedSu = Float.NaN;
    private float debugLastRawWind = Float.NaN;
    private float debugLastSmoothedWind = Float.NaN;
    private float debugLastGeneratedSpeed = Float.NaN;
    private float debugLastBearingAngle = Float.NaN;

    public WindRotoBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_ROTO_BE.get(), pos, state);
        forceRotationModePlace();
    }

    public void queueAssemblePublic() {
        queueAssemble();
    }

    public void disassemblePublic() {
        disassemble();
    }

    public int getComparatorOutputLevel() {
        return computeComparatorLevel();
    }

    public int getBoundServoCount() {
        return boundServos.size();
    }

    public boolean hasBothServoTypes() {
        boolean hasNormal = false;
        boolean hasInverted = false;

        for (BoundServoData data : boundServos) {
            if (data.inverted) {
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
        return lastAverageBoundServoAngle;
    }

    public int getTotalContraptionBlockCount() {
        return getContraptionBlockCount() + lastBoundServoContraptionBlocks;
    }

    public boolean addBoundServo(BlockPos servoPos, boolean inverted) {
        if (servoPos == null || servoPos.equals(worldPosition)) {
            return false;
        }

        BlockPos relativeOffset = servoPos.subtract(worldPosition);

        for (int i = 0; i < boundServos.size(); i++) {
            BoundServoData existing = boundServos.get(i);
            if (existing.worldPos.equals(servoPos)) {
                if (existing.inverted == inverted) {
                    refreshBoundServoStatsNow();
                    setServoBoundFlag(existing, true);
                    return false;
                }

                BoundServoData replacement = new BoundServoData(servoPos, relativeOffset, inverted);
                boundServos.set(i, replacement);
                refreshBoundServoStatsNow();
                setServoBoundFlag(replacement, true);
                markServoDataChanged();
                return true;
            }
        }

        BoundServoData data = new BoundServoData(servoPos, relativeOffset, inverted);
        boundServos.add(data);
        refreshBoundServoStatsNow();
        setServoBoundFlag(data, true);
        markServoDataChanged();
        return true;
    }

    public void clearBoundServos() {
        if (boundServos.isEmpty()
                && lastAverageBoundServoAngle == 0.0F
                && lastServoSuMultiplier == 1.0F
                && lastBoundServoContraptionBlocks == 0) {
            return;
        }

        for (BoundServoData data : boundServos) {
            setServoBoundFlag(data, false);
        }

        boundServos.clear();
        lastAverageBoundServoAngle = 0.0F;
        lastServoSuMultiplier = 1.0F;
        lastBoundServoContraptionBlocks = 0;
        generatedSu = computeSuFromRpm(generatedRpm);
        nextServoSampleAt = 0;

        if (running || assembleNextTick) {
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();
        }

        setChanged();
        sendData();
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (level != null && !level.isClientSide) {
            debugInitTraceSystem();
            ensureOwnChunkForced();
            nextServoSampleAt = level.getGameTime() + SERVO_SAMPLE_TICKS;
            nextStuckOverstressRebuildAt = level.getGameTime() + STUCK_OVERSTRESS_REBUILD_TICKS;
            refreshBoundServoStatsNow();
        }
    }

    @Override
    public void onChunkUnloaded() {
        releaseOwnChunkForced();
        super.onChunkUnloaded();
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
        if (!TwisterMillConfig.CHUNK_LOADING_ENABLED.get()) {
            if (chunkForceRegistered) {
                releaseOwnChunkForced();
            }
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
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
        assembleNextTick = true;

        if (level != null && !level.isClientSide) {
            debugInitTraceSystem();
            ensureOwnChunkForced();

            int rs = getExternalRedstonePower();
            if (isHardRedstoneStopSignal(rs)) {
                stoppedByRedstone = true;
                assembleNextTick = false;
                updateVisualRunning(false);
                updateComparatorIfNeeded();
                return;
            }

            if (needsInit) {
                needsInit = false;
                updateIsOutside();
                long t = level.getGameTime();
                nextOutsideCheckAt = t + OUTSIDE_CHECK_TICKS;
                nextWindSampleAt = t;
                nextRampAt = t;
                nextServoSampleAt = t + SERVO_SAMPLE_TICKS;
                nextStuckOverstressRebuildAt = t + STUCK_OVERSTRESS_REBUILD_TICKS;
            } else {
                updateIsOutside();
            }

            recomputeNow(level.getGameTime());
            if (hasRedstoneRpmLimitSignal(rs)) {
                int redstoneRpmLimit = getRedstoneRpmLimit(rs);
                if (generatedRpm > redstoneRpmLimit) {
                    generatedRpm = redstoneRpmLimit;
                    generatedSu = computeSuFromRpm(generatedRpm);
                }
            }
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();
            setChanged();
            syncIfNeeded();
            updateComparatorIfNeeded();
        } else {
            setChanged();
            sendData();
        }
    }

    @Override
    public void assemble() {
        if (level == null) {
            return;
        }

        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof BearingBlock)) {
            return;
        }

        Direction direction = state.getValue(BearingBlock.FACING);
        BearingContraption contraption = new AllBlocksWindmillContraption(isWindmill(), direction);

        try {
            if (!contraption.assemble(level, worldPosition)) {
                return;
            }
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }

        if (isWindmill()) {
            award(AllAdvancements.WINDMILL);
        }
        if (contraption.getSailBlocks() >= 16 * 8) {
            award(AllAdvancements.WINDMILL_MAXED);
        }

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);

        BlockPos anchor = worldPosition.relative(direction);
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(direction.getAxis());
        level.addFreshEntity(movedContraption);

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

        if (contraption.containsBlockBreakers()) {
            award(AllAdvancements.CONTRAPTION_ACTORS);
        }

        running = true;
        angle = 0;
        generatedSu = computeSuFromRpm(generatedRpm);
        sendData();
        updateGeneratedRotation();
        zeroOutCreateWindmillContribution();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        behaviours.remove(movementMode);
        forceRotationModePlace();

        movementDirection = new ScrollOptionBehaviour<>(
                WindmillBearingBlockEntity.RotationDirection.class,
                CreateLang.translateDirect("contraptions.windmill.rotation_direction"),
                this,
                getMovementModeSlot()
        );

        movementDirection.withCallback($ -> onDirectionChanged());
        behaviours.add(movementDirection);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        long time = level.getGameTime();

        if (!chunkForceRegistered || !TwisterMillConfig.CHUNK_LOADING_ENABLED.get() || time % 20L == 0L) {
            ensureOwnChunkForced();
        }

        int rs = getExternalRedstonePower();
        if (rs != lastExternalRedstone) {
            boolean nowPowered = isHardRedstoneStopSignal(rs);
            boolean wasPowered = isHardRedstoneStopSignal(lastExternalRedstone);

            if (nowPowered && !wasPowered) {
                stoppedByRedstone = true;
                assembleNextTick = false;
                nextRampAt = time;
            }

            if (!nowPowered && wasPowered) {
                if (stoppedByRedstone) {
                    stoppedByRedstone = false;
                    nextRampAt = time;
                    nextWindSampleAt = time;
                    if (!running) {
                        queueAssemble();
                    }
                }
            }

            lastExternalRedstone = rs;
        }

        updateBoundServoDataIfNeeded(time);

        int windUpdateTicks = step10(Mth.clamp(TwisterMillConfig.WIND_UPDATE_TICKS.get(), 10, 1000));
        int rpmUpdateTicks = step10(Mth.clamp(TwisterMillConfig.RPM_RAMP_TICKS.get(), 10, 1000));
        int rpmStep = Mth.clamp(TwisterMillConfig.RPM_RAMP_STEP.get(), 1, 64);
        int maxRpm = Mth.clamp(TwisterMillConfig.MAX_RPM.get(), 10, 256);

        if (needsInit) {
            needsInit = false;
            updateIsOutside();
            nextOutsideCheckAt = time + OUTSIDE_CHECK_TICKS;
            nextWindSampleAt = time;
            nextRampAt = time;
            nextServoSampleAt = time + SERVO_SAMPLE_TICKS;
            nextStuckOverstressRebuildAt = time + STUCK_OVERSTRESS_REBUILD_TICKS;
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
                generatedSu = 0.0F;

                if (running || assembleNextTick) {
                    updateGeneratedRotation();
                    zeroOutCreateWindmillContribution();
                }

                setChanged();
            }
        } else if (time >= nextWindSampleAt) {
            nextWindSampleAt = time + windUpdateTicks;

            WeatherManager weatherManager = WindReader.getWeatherManagerFor(level);
            if (weatherManager != null) {
                BlockPos weatherPos = getWorldBlockPosForExternalSystems();
                lastWindSpeed = weatherManager.getWindManager().getWindSpeedPositional(weatherPos, 2, false);
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

        if (time >= nextRampAt) {
            nextRampAt = time + rpmUpdateTicks;
            int targetRpm = (!isOutsideCached || stoppedByRedstone) ? 0 : targetRpmCached;
            if (targetRpm > 0 && hasRedstoneRpmLimitSignal(rs)) {
                targetRpm = Math.min(targetRpm, getRedstoneRpmLimit(rs));
            }
            int newRpm = approachInt(generatedRpm, targetRpm, rpmStep);
            newRpm = Mth.clamp(newRpm, 0, maxRpm);

            if (newRpm != generatedRpm) {
                generatedRpm = newRpm;
                generatedSu = computeSuFromRpm(generatedRpm);

                if (running || assembleNextTick) {
                    updateGeneratedRotation();
                    zeroOutCreateWindmillContribution();
                }

                setChanged();
            }
        }

        if (!running && !assembleNextTick) {
            updateVisualRunning(false);
            zeroOutCreateWindmillContribution();
            updateComparatorIfNeeded();
            syncIfNeeded();
            debugTraceMaybeSample(time);
            return;
        }

        if (running && !isOutsideCached && generatedRpm != 0) {
            generatedRpm = 0;
            generatedSu = 0.0F;
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();
            setChanged();
        }

        boolean visualRunning = running && isOutsideCached && generatedRpm > 0;
        updateVisualRunning(visualRunning);
        maybeRecoverFromStuckOverstress(time);
        zeroOutCreateWindmillContribution();
        updateComparatorIfNeeded();
        syncIfNeeded();
        debugTraceMaybeSample(time);
    }

    @Override
    public float getGeneratedSpeed() {
        if (!running) {
            return 0;
        }
        return generatedRpm * getAngleSpeedDirection();
    }

    @Override
    public float calculateAddedStressCapacity() {
        if (!running || generatedRpm <= 0) {
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
    public void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);

        tag.putBoolean("Outside", isOutsideCached);
        tag.putFloat("LastWind", lastWindSpeed);
        tag.putFloat("WindSmoothed", windSmoothed);
        tag.putInt("GenRpm", generatedRpm);
        tag.putFloat("GenSu", generatedSu);
        tag.putInt("TargetRpm", targetRpmCached);
        tag.putBoolean("VisualRunning", lastVisualRunning);
        tag.putInt("ExtRS", lastExternalRedstone);
        tag.putBoolean("StoppedByRS", stoppedByRedstone);

        ListTag servoList = new ListTag();
        for (BoundServoData data : boundServos) {
            servoList.add(data.write());
        }
        tag.put(NBT_BOUND_SERVOS, servoList);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
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
        if (tag.contains("ExtRS")) {
            lastExternalRedstone = Mth.clamp(tag.getInt("ExtRS"), 0, 15);
        }
        if (tag.contains("StoppedByRS")) {
            stoppedByRedstone = tag.getBoolean("StoppedByRS");
        }

        boundServos.clear();
        if (tag.contains(NBT_BOUND_SERVOS, Tag.TAG_LIST)) {
            ListTag servoList = tag.getList(NBT_BOUND_SERVOS, Tag.TAG_COMPOUND);
            for (int i = 0; i < servoList.size(); i++) {
                CompoundTag servoTag = servoList.getCompound(i);
                if (servoTag.contains(NBT_SERVO_POS)) {
                    boundServos.add(BoundServoData.read(servoTag, worldPosition));
                }
            }
        }

        int maxRpm = Mth.clamp(TwisterMillConfig.MAX_RPM.get(), 10, 256);
        lastWindSpeed = Mth.clamp(lastWindSpeed, 0.0F, MAX_WIND_SPEED);
        if (!Float.isNaN(windSmoothed)) {
            windSmoothed = Mth.clamp(windSmoothed, 0.0F, MAX_WIND_SPEED);
        }
        targetRpmCached = Mth.clamp(targetRpmCached, 0, maxRpm);
        generatedRpm = Mth.clamp(generatedRpm, 0, maxRpm);

        recomputeBoundServoStats();
        generatedSu = computeSuFromRpm(generatedRpm);
        zeroOutCreateWindmillContribution();
        updateComparatorIfNeeded();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean details = com.simibubi.create.AllKeys.ctrlDown();
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

        if (!details) {
            CreateLang.text("")
                    .add(Component.literal("details: ").withStyle(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.translateDirect("tooltip.twistermill.key_ctrl")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip);
            return true;
        }

        float smooth = Float.isNaN(windSmoothed) ? lastWindSpeed : windSmoothed;

        CreateLang.translate("tooltip.twistermill.wind_outside")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text(isOutsideCached ? "true" : "false")
                .style(isOutsideCached ? ChatFormatting.GREEN : ChatFormatting.RED)
                .forGoggles(tooltip, 1);

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

        CreateLang.number(getAssembledSailLikeBlockCount())
                .style(ChatFormatting.GOLD)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.contraption_blocks")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(getTotalContraptionBlockCount())
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        return true;
    }

    private int computeComparatorLevel() {
        if (!lastVisualRunning) {
            return 0;
        }
        if (isOverStressed()) {
            return 0;
        }

        int rpm = Math.max(0, generatedRpm);
        int lvl;

        if (rpm <= 9) {
            lvl = 1;
        } else if (rpm >= 247) {
            lvl = 15;
        } else {
            float normalized = (rpm - 9) / 238.0F;
            lvl = 1 + Math.round(normalized * 14.0F);
        }

        if (TwisterMillConfig.COMPARATOR_OUTPUT_INVERTED.get()) {
            lvl = 16 - lvl;
        }

        return Mth.clamp(lvl, 1, 15);
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
        movementMode.setValue(IControlContraption.RotationMode.ROTATE_NEVER_PLACE.ordinal());
    }

    private void onDirectionChanged() {
        if (!running || level == null) {
            return;
        }
        if (!level.isClientSide) {
            updateGeneratedRotation();
        }
    }

    private float getAngleSpeedDirection() {
        if (movementDirection == null) {
            return 1;
        }

        WindmillBearingBlockEntity.RotationDirection dir =
                WindmillBearingBlockEntity.RotationDirection.values()[movementDirection.getValue()];

        return (dir == WindmillBearingBlockEntity.RotationDirection.CLOCKWISE ? 1 : -1);
    }

    private int getContraptionBlockCount() {
        if (movedContraption == null || movedContraption.getContraption() == null) {
            return 0;
        }
        var blocks = movedContraption.getContraption().getBlocks();
        if (blocks == null) {
            return 0;
        }
        return blocks.size();
    }

    private int getAssembledSailLikeBlockCount() {
        if (movedContraption == null || movedContraption.getContraption() == null) {
            return 0;
        }

        var blocks = movedContraption.getContraption().getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return 0;
        }

        int sailLikeCount = 0;
        for (StructureBlockInfo info : blocks.values()) {
            if (info != null && info.state().is(CREATE_WINDMILL_SAILS)) {
                sailLikeCount++;
            }
        }

        return sailLikeCount;
    }

    private int getExternalRedstonePower() {
        if (level == null) {
            return 0;
        }
        return Mth.clamp(level.getBestNeighborSignal(worldPosition), 0, 15);
    }

    private static boolean isHardRedstoneStopSignal(int redstoneLevel) {
        return redstoneLevel == 15;
    }

    private static boolean hasRedstoneRpmLimitSignal(int redstoneLevel) {
        return redstoneLevel >= 1 && redstoneLevel <= 14;
    }

    private static int getRedstoneRpmLimit(int redstoneLevel) {
        if (!hasRedstoneRpmLimitSignal(redstoneLevel)) {
            return Integer.MAX_VALUE;
        }

        float normalized = (redstoneLevel - 1) / 13.0F;
        return Math.round(REDSTONE_RPM_LIMIT_AT_SIGNAL_1
                + normalized * (REDSTONE_RPM_LIMIT_AT_SIGNAL_14 - REDSTONE_RPM_LIMIT_AT_SIGNAL_1));
    }

    private void setServoBoundFlag(BoundServoData data, boolean bound) {
        if (level == null || data == null) {
            return;
        }

        if (!bound) {
            if (data.lastResolvedPos != null && applyServoBoundFlagAtPos(data.lastResolvedPos, data.inverted, false)) {
                data.lastResolvedPos = null;
                return;
            }

            if (applyServoBoundFlagAtPos(data.worldPos, data.inverted, false)) {
                data.lastResolvedPos = null;
                return;
            }

            ServoBindingResolver.ResolvedServoSample resolved = ServoBindingResolver.resolve(
                    level,
                    worldPosition,
                    getWorldBlockPosForExternalSystems(),
                    data.worldPos,
                    data.relativeOffset,
                    data.lastResolvedPos,
                    data.inverted
            );

            if (resolved.found() && resolved.resolvedPos() != null) {
                applyServoBoundFlagAtPos(resolved.resolvedPos(), data.inverted, false);
            }

            data.lastResolvedPos = null;
            return;
        }

        if (data.lastResolvedPos != null && applyServoBoundFlagAtPos(data.lastResolvedPos, data.inverted, true)) {
            return;
        }

        if (applyServoBoundFlagAtPos(data.worldPos, data.inverted, true)) {
            data.lastResolvedPos = data.worldPos;
            return;
        }

        ServoBindingResolver.ResolvedServoSample resolved = ServoBindingResolver.resolve(
                level,
                worldPosition,
                getWorldBlockPosForExternalSystems(),
                data.worldPos,
                data.relativeOffset,
                data.lastResolvedPos,
                data.inverted
        );

        if (resolved.found()
                && resolved.resolvedPos() != null
                && applyServoBoundFlagAtPos(resolved.resolvedPos(), data.inverted, true)) {
            data.lastResolvedPos = resolved.resolvedPos();
        }
    }

    private boolean applyServoBoundFlagAtPos(@Nullable BlockPos pos, boolean inverted, boolean bound) {
        if (level == null || pos == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return applyServoBoundFlagToBlockEntity(be, inverted, bound);
    }

    private boolean applyServoBoundFlagToBlockEntity(BlockEntity be, boolean inverted, boolean bound) {
        if (!inverted && be instanceof ServoTwisterBlockEntity servo) {
            servo.setBoundToWindRoto(bound);
            return true;
        }
        if (inverted && be instanceof InvServoTwisterBlockEntity invServo) {
            invServo.setBoundToWindRoto(bound);
            return true;
        }
        return false;
    }

    private void updateResolvedServoBinding(BoundServoData data, @Nullable BlockPos newResolvedPos) {
        if (Objects.equals(data.lastResolvedPos, newResolvedPos)) {
            return;
        }

        if (data.lastResolvedPos != null) {
            applyServoBoundFlagAtPos(data.lastResolvedPos, data.inverted, false);
        }

        data.lastResolvedPos = newResolvedPos;

        if (newResolvedPos != null) {
            applyServoBoundFlagAtPos(newResolvedPos, data.inverted, true);
        }
    }

    private void refreshBoundServoStatsNow() {
        recomputeBoundServoStats();
        generatedSu = computeSuFromRpm(generatedRpm);
        nextServoSampleAt = 0;
    }

    private void markServoDataChanged() {
        if (running || assembleNextTick) {
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();
        }
        setChanged();
        sendData();
    }

    private float getSuFactor() {
        return Mth.clamp(TwisterMillConfig.SU_FACTOR.get().floatValue(), 0.1F, 100.0F);
    }

    private float getSuPerRpm() {
        return Mth.clamp(TwisterMillConfig.SU_PER_RPM.get().floatValue(), 2.0F, 128.0F);
    }

    private float getSuPerBlock() {
        return Mth.clamp(TwisterMillConfig.SU_PER_BLOCK.get(), 1, 1024);
    }

    private float computeStaticContraptionSu() {
        int blockCount = getTotalContraptionBlockCount();
        if (blockCount <= 0) {
            return 0.0F;
        }
        return blockCount * getSuPerBlock();
    }

    private float computeSuFromRpm(int rpm) {
        int r = Math.abs(rpm);
        if (r <= 0) {
            return 0.0F;
        }

        float baseSu = (getSuPerRpm() * getSuFactor()) * r;
        float totalBaseSu = baseSu + computeStaticContraptionSu();
        return totalBaseSu * lastServoSuMultiplier;
    }

    private void updateBoundServoDataIfNeeded(long time) {
        if (time < nextServoSampleAt) {
            return;
        }

        nextServoSampleAt = time + SERVO_SAMPLE_TICKS;

        float previousAngle = lastAverageBoundServoAngle;
        float previousMultiplier = lastServoSuMultiplier;
        int previousCount = boundServos.size();
        int previousBoundServoBlocks = lastBoundServoContraptionBlocks;

        recomputeBoundServoStats();

        if (Math.abs(previousAngle - lastAverageBoundServoAngle) > SERVO_FACTOR_EPSILON
                || Math.abs(previousMultiplier - lastServoSuMultiplier) > SERVO_FACTOR_EPSILON
                || previousCount != boundServos.size()
                || previousBoundServoBlocks != lastBoundServoContraptionBlocks) {
            generatedSu = computeSuFromRpm(generatedRpm);

            if (running || assembleNextTick) {
                updateGeneratedRotation();
                zeroOutCreateWindmillContribution();
            }

            setChanged();
            sendData();
        }
    }

    private void recomputeBoundServoStats() {
        if (boundServos.isEmpty()) {
            lastAverageBoundServoAngle = 0.0F;
            lastServoSuMultiplier = 1.0F;
            lastBoundServoContraptionBlocks = 0;
            return;
        }

        float angleSum = 0.0F;
        int validCount = 0;
        int totalServoContraptionBlocks = 0;
        BlockPos currentWorldWindRotoPos = getWorldBlockPosForExternalSystems();
        Iterator<BoundServoData> iterator = boundServos.iterator();

        while (iterator.hasNext()) {
            BoundServoData data = iterator.next();

            ServoBindingResolver.ResolvedServoSample sample = ServoBindingResolver.resolve(
                    level,
                    worldPosition,
                    currentWorldWindRotoPos,
                    data.worldPos,
                    data.relativeOffset,
                    data.lastResolvedPos,
                    data.inverted
            );

            if (!sample.found()) {
                data.missingSamples++;

                if (data.missingSamples >= MAX_MISSING_SERVO_SAMPLES) {
                    setServoBoundFlag(data, false);
                    data.valid = false;
                    data.lastKnownAngle = 0.0F;
                    data.lastResolvedPos = null;
                    iterator.remove();
                    continue;
                }

                data.valid = true;
                angleSum += data.lastKnownAngle;
                validCount++;
                continue;
            }

            data.missingSamples = 0;
            data.lastKnownAngle = sample.normalizedAngle();
            data.valid = true;
            updateResolvedServoBinding(data, sample.resolvedPos());
            angleSum += data.lastKnownAngle;
            validCount++;
            totalServoContraptionBlocks += Math.max(0, sample.contraptionBlockCount());
        }

        lastBoundServoContraptionBlocks = totalServoContraptionBlocks;

        if (validCount <= 0) {
            lastAverageBoundServoAngle = 0.0F;
            lastServoSuMultiplier = 1.0F;
            return;
        }

        lastAverageBoundServoAngle = angleSum / validCount;
        lastServoSuMultiplier = servoAngleToSuMultiplier(lastAverageBoundServoAngle);
    }

    private float servoAngleToSuMultiplier(float angleDeg) {
        if (angleDeg <= 0.0F) {
            return 1.0F;
        }
        if (angleDeg > MAX_SERVO_BIND_ANGLE) {
            return 1.0F;
        }
        return 1.0F + (angleDeg / MAX_SERVO_BIND_ANGLE);
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

        BlockPos base = worldPosition;
        int maxY = level.getMaxBuildHeight() - 1;
        int solidBlocksAbove = 0;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(base.getX(), base.getY() + 1, base.getZ());

        while (mp.getY() <= maxY) {
            BlockState s = level.getBlockState(mp);

            if (s.is(CREATE_WINDMILL_SAILS)) {
                mp.move(0, 1, 0);
                continue;
            }

            boolean solid = !s.isAir() && !s.getCollisionShape(level, mp).isEmpty();
            if (solid) {
                solidBlocksAbove++;
                if (solidBlocksAbove > 5) {
                    isOutsideCached = false;
                    return;
                }
            }

            mp.move(0, 1, 0);
        }

        isOutsideCached = true;
    }

    private void syncIfNeeded() {
        float syncWindSmoothed = Float.isNaN(windSmoothed) ? lastWindSpeed : windSmoothed;

        boolean needsSync =
                lastSentRpm != generatedRpm
                        || lastSentOutside != isOutsideCached
                        || floatDiffers(lastSentWindSpeed, lastWindSpeed)
                        || floatDiffers(lastSentWindSmoothed, syncWindSmoothed)
                        || floatDiffers(lastSentGeneratedSu, generatedSu)
                        || lastSentVisualRunning != lastVisualRunning
                        || lastSentBoundServoCount != boundServos.size()
                        || floatDiffers(lastSentAverageBoundServoAngle, lastAverageBoundServoAngle)
                        || floatDiffers(lastSentServoSuMultiplier, lastServoSuMultiplier)
                        || lastSentBoundServoContraptionBlocks != lastBoundServoContraptionBlocks;

        if (!needsSync) {
            return;
        }

        lastSentRpm = generatedRpm;
        lastSentOutside = isOutsideCached;
        lastSentWindSpeed = lastWindSpeed;
        lastSentWindSmoothed = syncWindSmoothed;
        lastSentGeneratedSu = generatedSu;
        lastSentVisualRunning = lastVisualRunning;
        lastSentBoundServoCount = boundServos.size();
        lastSentAverageBoundServoAngle = lastAverageBoundServoAngle;
        lastSentServoSuMultiplier = lastServoSuMultiplier;
        lastSentBoundServoContraptionBlocks = lastBoundServoContraptionBlocks;

        sendData();
    }

    private static boolean floatDiffers(float a, float b) {
        if (Float.isNaN(a) != Float.isNaN(b)) {
            return true;
        }
        if (Float.isNaN(a)) {
            return false;
        }
        return Math.abs(a - b) > SYNC_FLOAT_EPSILON;
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

    private void recomputeNow(long time) {
        int maxRpm = Mth.clamp(TwisterMillConfig.MAX_RPM.get(), 10, 256);

        if (!isOutsideCached) {
            targetRpmCached = 0;
            generatedRpm = 0;
            generatedSu = 0.0F;
            lastWindSpeed = 0.0F;
            windSmoothed = 0.0F;
            return;
        }

        final var lvl = level;
        if (lvl == null) {
            lastWindSpeed = 0.0F;
        } else {
            WeatherManager weatherManager = WindReader.getWeatherManagerFor(lvl);
            if (weatherManager != null) {
                BlockPos weatherPos = getWorldBlockPosForExternalSystems();
                lastWindSpeed = weatherManager.getWindManager().getWindSpeedPositional(weatherPos, 2, false);
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
        generatedRpm = targetRpmCached;
        generatedSu = computeSuFromRpm(generatedRpm);

        nextWindSampleAt = time + step10(Mth.clamp(TwisterMillConfig.WIND_UPDATE_TICKS.get(), 10, 1000));
        nextRampAt = time + step10(Mth.clamp(TwisterMillConfig.RPM_RAMP_TICKS.get(), 10, 1000));
        nextServoSampleAt = time + SERVO_SAMPLE_TICKS;
        nextStuckOverstressRebuildAt = time + STUCK_OVERSTRESS_REBUILD_TICKS;
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

    private void maybeRecoverFromStuckOverstress(long time) {
        if (time < nextStuckOverstressRebuildAt) {
            return;
        }

        nextStuckOverstressRebuildAt = time + STUCK_OVERSTRESS_REBUILD_TICKS;

        if (level == null || level.isClientSide) {
            return;
        }
        if (!running || assembleNextTick) {
            return;
        }
        if (!isOverStressed()) {
            return;
        }
        if (isHardRedstoneStopSignal(lastExternalRedstone)) {
            return;
        }
        if (!isOutsideCached) {
            return;
        }

        float generatedSpeed = getGeneratedSpeed();
        if (Math.abs(generatedSpeed) <= SYNC_FLOAT_EPSILON) {
            return;
        }

        rebuildKineticNetworkFromSource(generatedSpeed);
    }

    private void rebuildKineticNetworkFromSource(float generatedSpeed) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (hasNetwork()) {
            getOrCreateNetwork().remove(this);
        }

        detachKinetics();
        source = null;
        setSpeed(generatedSpeed);
        setNetwork(createNetworkId());
        attachKinetics();
        updateGeneratedRotation();
        zeroOutCreateWindmillContribution();
        setChanged();
        syncIfNeeded();
        updateComparatorIfNeeded();
    }

    private void debugInitTraceSystem() {
        if (level == null || level.isClientSide) {
            return;
        }

        WindRotoDebugDumpService.initialize(level.getServer());

        if (debugDimensionId == Integer.MIN_VALUE) {
            debugDimensionId = WindRotoDebugTraceBuffer.getOrCreateDimensionId(level.dimension().location());
        }
    }

    private void debugTraceMaybeSample(long gameTime) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (debugDimensionId == Integer.MIN_VALUE) {
            debugDimensionId = WindRotoDebugTraceBuffer.getOrCreateDimensionId(level.dimension().location());
        }

        float smoothWind = Float.isNaN(windSmoothed) ? lastWindSpeed : windSmoothed;
        float generatedSpeed = getGeneratedSpeed();
        float bearingAngle = angle;

        int flags = 0;
        if (running) {
            flags |= WindRotoDebugTraceBuffer.FLAG_RUNNING;
        }
        if (assembleNextTick) {
            flags |= WindRotoDebugTraceBuffer.FLAG_ASSEMBLE_QUEUED;
        }
        if (movedContraption != null) {
            flags |= WindRotoDebugTraceBuffer.FLAG_ASSEMBLED;
        }
        if (isOutsideCached) {
            flags |= WindRotoDebugTraceBuffer.FLAG_OUTSIDE;
        }
        if (stoppedByRedstone) {
            flags |= WindRotoDebugTraceBuffer.FLAG_STOPPED_BY_REDSTONE;
        }
        if (isOverStressed()) {
            flags |= WindRotoDebugTraceBuffer.FLAG_OVERSTRESSED;
        }
        if (needsInit) {
            flags |= WindRotoDebugTraceBuffer.FLAG_NEEDS_INIT;
        }
        if (chunkForceRegistered) {
            flags |= WindRotoDebugTraceBuffer.FLAG_CHUNK_FORCED;
        }
        if (lastVisualRunning) {
            flags |= WindRotoDebugTraceBuffer.FLAG_VISUAL_RUNNING;
        }
        if (hasNetwork()) {
            flags |= WindRotoDebugTraceBuffer.FLAG_HAS_NETWORK;
        }
        if (source != null) {
            flags |= WindRotoDebugTraceBuffer.FLAG_SOURCE_PRESENT;
        }
        if (lastSentOutside) {
            flags |= WindRotoDebugTraceBuffer.FLAG_LAST_SENT_OUTSIDE;
        }
        if (lastSentVisualRunning) {
            flags |= WindRotoDebugTraceBuffer.FLAG_LAST_SENT_VISUAL_RUNNING;
        }

        int servoCount = boundServos.size();
        int contraptionBlockCount = getContraptionBlockCount();
        int forcedChunkX = forcedChunkKey != null ? forcedChunkKey.chunkX() : Integer.MIN_VALUE;
        int forcedChunkZ = forcedChunkKey != null ? forcedChunkKey.chunkZ() : Integer.MIN_VALUE;

        boolean changed =
                flags != debugLastFlags
                        || generatedRpm != debugLastGeneratedRpm
                        || targetRpmCached != debugLastTargetRpm
                        || lastExternalRedstone != debugLastExternalRedstone
                        || servoCount != debugLastBoundServoCount
                        || lastComparatorLevel != debugLastComparatorLevel
                        || contraptionBlockCount != debugLastContraptionBlockCount
                        || lastBoundServoContraptionBlocks != debugLastBoundServoContraptionBlocks
                        || forcedChunkX != debugLastForcedChunkX
                        || forcedChunkZ != debugLastForcedChunkZ
                        || debugFloatDiffers(generatedSu, debugLastGeneratedSu)
                        || debugFloatDiffers(lastWindSpeed, debugLastRawWind)
                        || debugFloatDiffers(smoothWind, debugLastSmoothedWind)
                        || debugFloatDiffers(generatedSpeed, debugLastGeneratedSpeed)
                        || debugFloatDiffers(bearingAngle, debugLastBearingAngle);

        if (!changed && gameTime < debugNextTraceSampleAt) {
            return;
        }

        debugNextTraceSampleAt = gameTime + WindRotoDebugTraceBuffer.TRACE_SAMPLE_INTERVAL_TICKS;
        debugLastFlags = flags;
        debugLastGeneratedRpm = generatedRpm;
        debugLastTargetRpm = targetRpmCached;
        debugLastExternalRedstone = lastExternalRedstone;
        debugLastBoundServoCount = servoCount;
        debugLastComparatorLevel = lastComparatorLevel;
        debugLastContraptionBlockCount = contraptionBlockCount;
        debugLastBoundServoContraptionBlocks = lastBoundServoContraptionBlocks;
        debugLastForcedChunkX = forcedChunkX;
        debugLastForcedChunkZ = forcedChunkZ;
        debugLastGeneratedSu = generatedSu;
        debugLastRawWind = lastWindSpeed;
        debugLastSmoothedWind = smoothWind;
        debugLastGeneratedSpeed = generatedSpeed;
        debugLastBearingAngle = bearingAngle;

        WindRotoDebugTraceBuffer.record(
                gameTime,
                worldPosition.asLong(),
                debugDimensionId,
                flags,
                generatedRpm,
                targetRpmCached,
                lastExternalRedstone,
                servoCount,
                lastComparatorLevel,
                contraptionBlockCount,
                lastBoundServoContraptionBlocks,
                forcedChunkX,
                forcedChunkZ,
                lastSentRpm,
                lastSentBoundServoCount,
                lastSentBoundServoContraptionBlocks,
                generatedSu,
                lastWindSpeed,
                smoothWind,
                lastAverageBoundServoAngle,
                lastServoSuMultiplier,
                generatedSpeed,
                bearingAngle,
                lastSentWindSpeed,
                lastSentWindSmoothed,
                lastSentGeneratedSu,
                lastSentAverageBoundServoAngle,
                lastSentServoSuMultiplier,
                nextOutsideCheckAt,
                nextWindSampleAt,
                nextRampAt,
                nextServoSampleAt,
                nextStuckOverstressRebuildAt
        );
    }

    private static boolean debugFloatDiffers(float a, float b) {
        if (Float.isNaN(a) != Float.isNaN(b)) {
            return true;
        }
        if (Float.isNaN(a)) {
            return false;
        }
        return Math.abs(a - b) > DEBUG_TRACE_FLOAT_EPSILON;
    }

    private BlockPos getWorldBlockPosForExternalSystems() {
        return WindRotoReflectionHelper.getWorldBlockPos(level, worldPosition);
    }

    private void zeroOutCreateWindmillContribution() {
        WindRotoReflectionHelper.zeroOutCreateWindmillContribution(this);
    }
}
