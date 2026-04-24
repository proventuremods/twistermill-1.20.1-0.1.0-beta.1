package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.block.custom.WindRotoBlock;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.debug.WindRotoDebugDumpService;
import com.proventure.twistermill.debug.WindRotoVerticalDebugTraceBuffer;
import com.proventure.twistermill.util.WindRotoReflectionHelper;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import weather2.util.WindReader;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WindRotoVerticalBlockEntity extends MechanicalBearingBlockEntity {

    private static final float SU_PER_RPM = 12.8F;
    private static final float YAW_TARGET_OFFSET_DEG = 0.0F;
    private static final float SYNC_FLOAT_EPSILON = 0.05F;
    private static final float DEBUG_TRACE_FLOAT_EPSILON = 0.001F;

    private static final int REDSTONE_PULSE_MIN_TICKS = 2;
    private static final int REDSTONE_PULSE_MAX_TICKS = 40;
    private static final int REDSTONE_PULSE_COOLDOWN_TICKS = 200;

    private static final int IDLE_OBSIDIAN_RECHECK_TICKS = 5;
    private static final int ACTIVE_OBSIDIAN_RECHECK_TICKS = 20;
    private static final int RUNTIME_DIRTY_INTERVAL_TICKS = 20;

    private record ForcedChunkKey(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
    }

    private record AngleAccessor(@Nullable Method method, boolean usePartialTicks, @Nullable Field field) {
        @Nullable
        Float read(Object target) {
            try {
                if (method != null) {
                    Object out = usePartialTicks ? method.invoke(target, 1.0F) : method.invoke(target);
                    if (out instanceof Number n) {
                        return n.floatValue();
                    }
                    return null;
                }

                if (field != null) {
                    Object out = field.get(target);
                    if (out instanceof Number n) {
                        return n.floatValue();
                    }
                }
            } catch (Throwable ignored) {
            }

            return null;
        }

        boolean isResolved() {
            return method != null || field != null;
        }
    }

    private static final Map<ForcedChunkKey, Integer> FORCED_CHUNK_REF_COUNTS = new HashMap<>();
    private static final Map<Class<?>, AngleAccessor> ANGLE_ACCESSOR_CACHE = new ConcurrentHashMap<>();
    private static final AngleAccessor NO_ANGLE_ACCESSOR = new AngleAccessor(null, false, null);

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

    protected ScrollOptionBehaviour<TwisterRotationMode> assemblyMode;

    private boolean verticalManualEnabled = false;
    private boolean restoreFreeModeAfterManualDisassembly = false;

    private boolean placementNorthValid = false;
    private int placementNorthDirData = -1;

    private float verticalCurrentYawDeg = 0.0F;
    private float verticalTargetYawDeg = 0.0F;
    private float verticalYawVelocityDegPerTick = 0.0F;
    private boolean verticalYawMoving = false;

    private boolean verticalParkedMode = false;
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
    private int obsidianCheckTimer = 0;

    private boolean chunkForceRegistered = false;

    @Nullable
    private ForcedChunkKey forcedChunkKey = null;

    private long lastRuntimeDirtyGameTime = Long.MIN_VALUE;

    private int lastSentGeneratedRpm = Integer.MIN_VALUE;
    private float lastSentGeneratedSpeedRpm = Float.NaN;
    private float lastSentGeneratedSu = Float.NaN;
    private boolean lastSentVisualRunning = false;
    private boolean lastSentPlacementNorthValid = false;
    private int lastSentPlacementNorthDirData = Integer.MIN_VALUE;
    private float lastSentVerticalCurrentYawDeg = Float.NaN;
    private float lastSentVerticalTargetYawDeg = Float.NaN;
    private float lastSentVerticalYawVelocityDegPerTick = Float.NaN;
    private boolean lastSentVerticalYawMoving = false;
    private boolean lastSentVerticalParkedMode = false;
    private float lastSentWorldWindAngleDeg = Float.NaN;
    private float lastSentLocalTargetYawDeg = Float.NaN;

    private long debugNextTraceSampleAt = 0L;
    private int debugDimensionId = Integer.MIN_VALUE;
    private int debugLastFlags = Integer.MIN_VALUE;
    private int debugLastExternalRedstone = Integer.MIN_VALUE;
    private int debugLastPlacementNorthDirData = Integer.MIN_VALUE;
    private int debugLastObsidianCheckTimer = Integer.MIN_VALUE;
    private int debugLastAssemblyModeValue = Integer.MIN_VALUE;
    private int debugLastGeneratedRpm = Integer.MIN_VALUE;
    private int debugLastSentGeneratedRpm = Integer.MIN_VALUE;
    private int debugLastSentPlacementNorthDirData = Integer.MIN_VALUE;
    private int debugLastForcedChunkX = Integer.MIN_VALUE;
    private int debugLastForcedChunkZ = Integer.MIN_VALUE;
    private float debugLastGeneratedSpeedRpm = Float.NaN;
    private float debugLastGeneratedSu = Float.NaN;
    private float debugLastCurrentYawDeg = Float.NaN;
    private float debugLastTargetYawDeg = Float.NaN;
    private float debugLastYawVelocityDegPerTick = Float.NaN;
    private float debugLastWorldWindAngleDeg = Float.NaN;
    private float debugLastLocalTargetYawDeg = Float.NaN;
    private float debugLastBearingAngle = Float.NaN;

    public WindRotoVerticalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIND_ROTO_VERTICAL_BE.get(), pos, state);
    }


    public void setPlacementNorth(Direction direction) {
        if (direction == null || direction.getAxis().isVertical()) {
            return;
        }

        placementNorthValid = true;
        placementNorthDirData = direction.get2DDataValue();
        obsidianCheckTimer = 0;
        setChanged();
        sendData();
    }

    public void onPlayerToggle(Player player) {
        if (player == null || !player.getMainHandItem().isEmpty()) {
            return;
        }

        if (level == null || level.isClientSide) {
            return;
        }

        if (!hasValidVerticalFacing()) {
            verticalManualEnabled = false;
            assembleNextTick = false;
            stopAllMotionState();
            updateVisualRunning(false);
            setChanged();
            sendData();
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
            sendData();
            return;
        }

        if (!refreshPlacementNorthFromObsidian()) {
            verticalManualEnabled = false;
            assembleNextTick = false;
            obsidianCheckTimer = 0;
            setChanged();
            sendData();
            return;
        }

        verticalManualEnabled = true;
        queueAssemble();
    }

    @SuppressWarnings("unused")
    public void queueAssemblePublic() {
        if (!hasValidVerticalFacing()) {
            verticalManualEnabled = false;
            assembleNextTick = false;
            stopAllMotionState();
            updateVisualRunning(false);
            setChanged();
            sendData();
            return;
        }

        if (isVerticalDisarmed()) {
            verticalManualEnabled = false;
            stopAllMotionState();
            updateVisualRunning(false);
            setChanged();
            sendData();
            return;
        }

        if (restoreFreeModeAfterManualDisassembly && !running && !assembleNextTick) {
            restoreFreeModeAfterManualDisassembly = false;
            resetAssemblyModeAfterDisassembly();
        }

        if (!refreshPlacementNorthFromObsidian()) {
            verticalManualEnabled = false;
            assembleNextTick = false;
            obsidianCheckTimer = 0;
            setChanged();
            sendData();
            return;
        }

        verticalManualEnabled = true;
        queueAssemble();
    }

    @SuppressWarnings("unused")
    public void disassemblePublic() {
        performManualDisassembly();
    }

    @Override
    public void onLoad() {
        super.onLoad();

        normalizePersistentStateAfterRead();
        clearTransientRuntimeFlags();

        if (level != null && !level.isClientSide) {
            debugInitTraceSystem();
            refreshPlacementNorthFromObsidian();
            ensureOwnChunkForced();
        }
    }

    @Override
    public void onChunkUnloaded() {
        releaseOwnChunkForced();
        clearTransientRuntimeFlags();
        super.onChunkUnloaded();
    }

    @Override
    public void assemble() {
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return;
        }

        if (!hasValidVerticalFacing()) {
            return;
        }

        if (!(currentLevel.getBlockState(worldPosition).getBlock() instanceof BearingBlock)) {
            return;
        }

        Direction direction = getBlockState().getValue(BearingBlock.FACING);
        BearingContraption contraption = new AllBlocksWindmillContraption(isWindmill(), direction);

        try {
            if (!contraption.assemble(currentLevel, worldPosition)) {
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

        contraption.removeBlocksFromWorld(currentLevel, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(currentLevel, this, contraption);

        BlockPos anchor = worldPosition.relative(direction);
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(direction.getAxis());
        currentLevel.addFreshEntity(movedContraption);

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(currentLevel, worldPosition);

        if (contraption.containsBlockBreakers()) {
            award(AllAdvancements.CONTRAPTION_ACTORS);
        }

        running = true;
        angle = 0;
        sendData();
        updateGeneratedRotation();
    }

    public void queueAssemble() {
        if (level != null && !level.isClientSide) {
            debugInitTraceSystem();
            if (!hasValidVerticalFacing()) {
                assembleNextTick = false;
                verticalManualEnabled = false;
                stopAllMotionState();
                updateVisualRunning(false);
                setChanged();
                sendData();
                return;
            }

            applySelectedRotationMode();

            if (!refreshPlacementNorthFromObsidian()) {
                assembleNextTick = false;
                verticalManualEnabled = false;
                stopAllMotionState();
                updateVisualRunning(false);
                obsidianCheckTimer = 0;
                setChanged();
                sendData();
                return;
            }

            assembleNextTick = true;
            initVerticalModeOnAssemble(level.getGameTime());
            updateGeneratedRotation();
            zeroOutCreateWindmillContribution();

            setChanged();
            sendData();
            return;
        }

        if (placementNorthValid) {
            assembleNextTick = true;
            setChanged();
            sendData();
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        behaviours.remove(movementMode);

        assemblyMode = new ScrollOptionBehaviour<>(
                TwisterRotationMode.class,
                CreateLang.translateDirect("contraptions.windmill.rotation_direction"),
                this,
                getMovementModeSlot()
        );
        assemblyMode.requiresWrench();
        assemblyMode.withCallback($ -> onAssemblyModeChanged());
        behaviours.add(assemblyMode);

        applySelectedRotationMode();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        long time = level.getGameTime();

        boolean verticalFacingValidNow = hasValidVerticalFacing();

        if (!verticalFacingValidNow) {
            if (running) {
                performManualDisassembly();
            }
            assembleNextTick = false;
            verticalManualEnabled = false;
            obsidianCheckTimer = 0;

            zeroOutCreateWindmillContribution();
            updateVisualRunning(false);
            syncIfNeeded();
            debugTraceMaybeSample(time);
            return;
        }

        boolean hasObsidianNorth = updatePlacementNorthState(running);

        if (!hasObsidianNorth) {
            if (running) {
                performManualDisassembly();
            }
            assembleNextTick = false;
            verticalManualEnabled = false;
            obsidianCheckTimer = 0;

            zeroOutCreateWindmillContribution();
            updateVisualRunning(false);
            syncIfNeeded();
            debugTraceMaybeSample(time);
            return;
        }

        if (!chunkForceRegistered || !TwisterMillConfig.CHUNK_LOADING_ENABLED.get() || time % 20L == 0L) {
            ensureOwnChunkForced();
        }

        if (restoreFreeModeAfterManualDisassembly && !running && !assembleNextTick) {
            restoreFreeModeAfterManualDisassembly = false;
            resetAssemblyModeAfterDisassembly();
            setChanged();
            sendData();
        }

        handleVerticalRedstonePulse(time);

        if (!running && !assembleNextTick) {
            zeroOutCreateWindmillContribution();
            updateVisualRunning(false);
            syncIfNeeded();
            debugTraceMaybeSample(time);
            return;
        }

        tickVerticalWindAngleMode(time);

        boolean visualRunning = running
                && placementNorthValid
                && isVerticalControlEnabled()
                && Math.abs(generatedSpeedRpm) > 0.001F;

        updateVisualRunning(visualRunning);
        zeroOutCreateWindmillContribution();
        syncIfNeeded();
        debugTraceMaybeSample(time);
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

        float capacityPerRpm = SU_PER_RPM * getSuFactor();
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
        tag.putBoolean("VerticalPulsePowered", verticalPulsePowered);
        tag.putLong("VerticalPulseCooldownUntil", verticalPulseCooldownUntil);

        tag.putFloat("LastWorldWindAngleDeg", lastWorldWindAngleDeg);
        tag.putFloat("LastLocalTargetYawDeg", lastLocalTargetYawDeg);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);

        applySelectedRotationMode();

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
        if (tag.contains("VerticalPulsePowered")) {
            verticalPulsePowered = tag.getBoolean("VerticalPulsePowered");
        }
        if (tag.contains("VerticalPulseCooldownUntil")) {
            verticalPulseCooldownUntil = tag.getLong("VerticalPulseCooldownUntil");
        }

        if (tag.contains("LastWorldWindAngleDeg")) {
            lastWorldWindAngleDeg = wrap360(tag.getFloat("LastWorldWindAngleDeg"));
        }
        if (tag.contains("LastLocalTargetYawDeg")) {
            lastLocalTargetYawDeg = wrap360(tag.getFloat("LastLocalTargetYawDeg"));
        }

        normalizePersistentStateAfterRead();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean details = com.simibubi.create.AllKeys.ctrlDown();
        boolean verticalFacingOkNow = hasValidVerticalFacing();
        boolean obsidianOkNow = detectPlacementNorthFromObsidian() != null;
        Level currentLevel = this.level;

        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.vertical.up_down_facing_check")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text(verticalFacingOkNow ? "true" : "false")
                .style(verticalFacingOkNow ? ChatFormatting.GREEN : ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.vertical.obsidian_north_marker")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (obsidianOkNow) {
            CreateLang.translate("tooltip.twistermill.vertical.present")
                    .style(ChatFormatting.GREEN)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.translate("tooltip.twistermill.vertical.missing_placed_obsidian_block")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
            CreateLang.translate("tooltip.twistermill.vertical.define_zero_degree_starting_point")
                    .style(ChatFormatting.RED)
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.vertical.yaw")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (!verticalFacingOkNow || !obsidianOkNow) {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else if (!running) {
            CreateLang.translate("tooltip.twistermill.vertical.standby")
                    .style(ChatFormatting.BLUE)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.number(wrap360(verticalCurrentYawDeg))
                    .style(ChatFormatting.YELLOW)
                    .space()
                    .add(Component.literal("°"))
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.vertical.rpm")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (!verticalFacingOkNow || !obsidianOkNow) {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else if (!running) {
            CreateLang.translate("tooltip.twistermill.vertical.standby")
                    .style(ChatFormatting.BLUE)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.number(Math.abs(generatedRpm))
                    .style(ChatFormatting.AQUA)
                    .space()
                    .add(Component.literal("RPM"))
                    .forGoggles(tooltip, 1);
        }

        if (!details) {
            CreateLang.text("")
                    .add(Component.literal("details: ").withStyle(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.translateDirect("tooltip.twistermill.key_ctrl")
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip);
            return true;
        }

        CreateLang.translate("tooltip.twistermill.vertical.target_yaw")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (!verticalFacingOkNow || !obsidianOkNow) {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else if (!running) {
            CreateLang.translate("tooltip.twistermill.vertical.standby")
                    .style(ChatFormatting.BLUE)
                    .forGoggles(tooltip, 1);
        } else {
            float targetRounded = Math.round(wrap360(verticalTargetYawDeg) * 10f) / 10f;
            CreateLang.number(targetRounded)
                    .style(ChatFormatting.YELLOW)
                    .space()
                    .add(Component.literal("°"))
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.vertical.world_wind_angle")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (!verticalFacingOkNow || !obsidianOkNow) {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else if (!running) {
            CreateLang.translate("tooltip.twistermill.vertical.standby")
                    .style(ChatFormatting.BLUE)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.number(wrap360(lastWorldWindAngleDeg))
                    .style(ChatFormatting.AQUA)
                    .space()
                    .add(Component.literal("°"))
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.vertical.local_target_yaw")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (!verticalFacingOkNow || !obsidianOkNow) {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else if (!running) {
            CreateLang.translate("tooltip.twistermill.vertical.standby")
                    .style(ChatFormatting.BLUE)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.number(wrap360(lastLocalTargetYawDeg))
                    .style(ChatFormatting.AQUA)
                    .space()
                    .add(Component.literal("°"))
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.vertical.yaw_velocity")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (verticalFacingOkNow && obsidianOkNow) {
            CreateLang.number(verticalYawVelocityDegPerTick)
                    .style(ChatFormatting.WHITE)
                    .space()
                    .add(Component.literal("°/t"))
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.vertical.yaw_moving")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (!verticalFacingOkNow || !obsidianOkNow) {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        } else if (!running) {
            CreateLang.translate("tooltip.twistermill.vertical.standby")
                    .style(ChatFormatting.BLUE)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.translate(verticalYawMoving
                            ? "tooltip.twistermill.vertical.targeting"
                            : "tooltip.twistermill.vertical.aligned")
                    .style(verticalYawMoving ? ChatFormatting.YELLOW : ChatFormatting.GREEN)
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.vertical.park_mode")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (verticalFacingOkNow && obsidianOkNow) {
            if (verticalParkedMode) {
                CreateLang.translate("tooltip.twistermill.vertical.active")
                        .style(ChatFormatting.GREEN)
                        .forGoggles(tooltip, 1);
            } else {
                CreateLang.translate("tooltip.twistermill.vertical.not_active")
                        .style(ChatFormatting.BLUE)
                        .forGoggles(tooltip, 1);
            }
        } else {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }

        long pulseCd = currentLevel == null ? 0 : Math.max(0, verticalPulseCooldownUntil - currentLevel.getGameTime());
        CreateLang.translate("tooltip.twistermill.vertical.redstone_pulse_cooldown")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (verticalFacingOkNow && obsidianOkNow) {
            if (pulseCd <= 0) {
                CreateLang.translate("tooltip.twistermill.vertical.standby")
                        .style(ChatFormatting.BLUE)
                        .forGoggles(tooltip, 1);
            } else {
                ChatFormatting cdColor;
                if (pulseCd >= 100) {
                    cdColor = ChatFormatting.RED;
                } else if (pulseCd > 50) {
                    cdColor = ChatFormatting.GOLD;
                } else {
                    cdColor = ChatFormatting.YELLOW;
                }

                CreateLang.number((int) pulseCd)
                        .style(cdColor)
                        .forGoggles(tooltip, 1);
            }
        } else {
            CreateLang.translate("tooltip.twistermill.vertical.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip, 1);
        }

        return true;
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

        int chunkX = worldPosition.getX() >> 4;
        int chunkZ = worldPosition.getZ() >> 4;

        if (chunkForceRegistered && forcedChunkKey != null
                && forcedChunkKey.chunkX() == chunkX
                && forcedChunkKey.chunkZ() == chunkZ
                && forcedChunkKey.dimension().equals(serverLevel.dimension())) {
            return;
        }

        ForcedChunkKey key = new ForcedChunkKey(serverLevel.dimension(), chunkX, chunkZ);

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

    private int getVerticalWindAngleUpdateTicks() {
        return Mth.clamp(TwisterMillConfig.VERTICAL_WIND_ANGLE_UPDATE_TICKS.get(), 10, 1000);
    }

    private int getVerticalMaxYawRpm() {
        return Mth.clamp(TwisterMillConfig.VERTICAL_MAX_YAW_RPM.get(), 1, 256);
    }

    private float getDegreesPerTickAt1Rpm() {
        return Mth.clamp(TwisterMillConfig.VERTICAL_DEGREES_PER_TICK_AT_1_RPM.get().floatValue(), 0.001F, 10.0F);
    }

    private float getMaxYawAccelDegPerTick2() {
        return Mth.clamp(TwisterMillConfig.VERTICAL_MAX_YAW_ACCEL_DEG_PER_TICK2.get().floatValue(), 0.0001F, 10.0F);
    }

    private float getYawDeadzoneDeg() {
        return Mth.clamp(TwisterMillConfig.VERTICAL_YAW_DEADZONE_DEG.get().floatValue(), 0.0F, 180.0F);
    }

    private float getYawStopVelocityDegPerTick() {
        return Mth.clamp(TwisterMillConfig.VERTICAL_YAW_STOP_VELOCITY_DEG_PER_TICK.get().floatValue(), 0.0001F, 10.0F);
    }

    private float getYawMinTrackingSpeedDegPerTick() {
        return Mth.clamp(TwisterMillConfig.VERTICAL_YAW_MIN_TRACKING_SPEED_DEG_PER_TICK.get().floatValue(), 0.0F, 10.0F);
    }

    private float getYawControllerGain() {
        return Mth.clamp(TwisterMillConfig.VERTICAL_YAW_CONTROLLER_GAIN.get().floatValue(), 0.0F, 10.0F);
    }

    private boolean refreshPlacementNorthFromObsidian() {
        Direction detected = detectPlacementNorthFromObsidian();

        if (detected == null) {
            boolean changed = placementNorthValid || placementNorthDirData != -1;
            placementNorthValid = false;
            placementNorthDirData = -1;

            if (changed) {
                setChanged();
            }

            return false;
        }

        boolean changed = !placementNorthValid || placementNorthDirData != detected.get2DDataValue();
        placementNorthValid = true;
        placementNorthDirData = detected.get2DDataValue();

        if (changed) {
            setChanged();
        }

        return true;
    }

    private boolean updatePlacementNorthState(boolean runningNow) {
        if (placementNorthValid && obsidianCheckTimer > 0) {
            obsidianCheckTimer--;
            return true;
        }

        boolean previousStatus = placementNorthValid;
        int previousDir = placementNorthDirData;

        boolean hasObsidianNorth = refreshPlacementNorthFromObsidian();
        obsidianCheckTimer = runningNow ? ACTIVE_OBSIDIAN_RECHECK_TICKS : IDLE_OBSIDIAN_RECHECK_TICKS;

        if (previousStatus != placementNorthValid || previousDir != placementNorthDirData) {
            syncIfNeeded();
        }

        return hasObsidianNorth;
    }

    @Nullable
    private Direction detectPlacementNorthFromObsidian() {
        if (level == null) {
            return null;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos checkPos = worldPosition.relative(direction);
            BlockState checkState = level.getBlockState(checkPos);
            if (checkState.is(Blocks.OBSIDIAN)) {
                return direction;
            }
        }

        return null;
    }

    private boolean hasValidVerticalFacing() {
        BlockState state = getBlockState();
        if (!state.hasProperty(BearingBlock.FACING)) {
            return false;
        }

        Direction facing = state.getValue(BearingBlock.FACING);
        return facing == Direction.UP || facing == Direction.DOWN;
    }

    private TwisterRotationMode getSelectedAssemblyMode() {
        if (assemblyMode == null) {
            return TwisterRotationMode.FREE_RS_ON;
        }

        int idx = Mth.clamp(assemblyMode.getValue(), 0, TwisterRotationMode.values().length - 1);
        return TwisterRotationMode.values()[idx];
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
        if (movementMode == null) {
            return;
        }

        IControlContraption.RotationMode mapped = switch (getSelectedAssemblyMode()) {
            case FREE_RS_ON -> IControlContraption.RotationMode.ROTATE_NEVER_PLACE;
            case RETURNED -> IControlContraption.RotationMode.ROTATE_PLACE_RETURNED;
            case PLACE -> IControlContraption.RotationMode.ROTATE_PLACE;
        };

        movementMode.setValue(mapped.ordinal());
    }

    private void setSelectedAssemblyMode(TwisterRotationMode mode) {
        if (assemblyMode != null) {
            assemblyMode.setValue(mode.ordinal());
        }
        applySelectedRotationMode();
    }

    private void onAssemblyModeChanged() {
        applySelectedRotationMode();

        if (isVerticalDisarmed()) {
            verticalManualEnabled = false;
            stopAllMotionState();
            updateVisualRunning(false);
        }

        setChanged();
        sendData();
    }

    private void preparePlaceModeForManualDisassembly() {
        setSelectedAssemblyMode(TwisterRotationMode.PLACE);
    }

    private void resetAssemblyModeAfterDisassembly() {
        setSelectedAssemblyMode(TwisterRotationMode.FREE_RS_ON);
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
            disassemble();
        }

        stopAllMotionState();
        updateVisualRunning(false);

        setChanged();
        sendData();
    }

    private void handleVerticalRedstonePulse(long time) {
        boolean powered = getExternalRedstonePower() > 0;

        boolean canUsePulse = isMoveNeverPlaceModeSelected()
                && running
                && isVerticalControlEnabled()
                && placementNorthValid
                && hasValidVerticalFacing();

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
                    && duration >= REDSTONE_PULSE_MIN_TICKS
                    && duration <= REDSTONE_PULSE_MAX_TICKS) {

                verticalParkedMode = !verticalParkedMode;
                verticalPulseCooldownUntil = time + REDSTONE_PULSE_COOLDOWN_TICKS;
                setChanged();
                sendData();
            }
        }
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
        verticalPulsePowered = false;
        verticalPulseStartTick = -1;
        nextWindAngleSampleAt = time;
        lastRuntimeDirtyGameTime = Long.MIN_VALUE;
    }

    private void tickVerticalWindAngleMode(long time) {
        if (!isMoveNeverPlaceModeSelected()) {
            stopAllMotionState();
            return;
        }

        if (!placementNorthValid || !isVerticalControlEnabled() || !hasValidVerticalFacing()) {
            stopAllMotionState();
            return;
        }

        float rawLocalTarget;

        if (verticalParkedMode) {
            lastWorldWindAngleDeg = 0.0F;
            rawLocalTarget = 0.0F;
        } else {
            if (time >= nextWindAngleSampleAt) {
                nextWindAngleSampleAt = time + getVerticalWindAngleUpdateTicks();
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
                desiredVelocity,
                getMaxYawAccelDegPerTick2()
        );

        if (Math.abs(diff) <= getYawDeadzoneDeg()
                && Math.abs(verticalYawVelocityDegPerTick) <= getYawStopVelocityDegPerTick()) {

            verticalYawVelocityDegPerTick = 0.0F;
            verticalYawMoving = false;
            generatedRpm = 0;
            generatedSpeedRpm = 0.0F;
            generatedSu = 0.0F;
            verticalCurrentYawDeg = wrap360(verticalTargetYawDeg);
            updateGeneratedRotation();
            markRuntimeStateDirty(time);
            return;
        }

        verticalYawMoving = Math.abs(verticalYawVelocityDegPerTick) > getYawStopVelocityDegPerTick();
        updateGeneratedFromYawVelocity();
        updateGeneratedRotation();
        markRuntimeStateDirty(time);
    }

    private float computeDesiredYawVelocity(float diffDeg) {
        float absDiff = Math.abs(diffDeg);

        if (absDiff <= getYawDeadzoneDeg()) {
            return 0.0F;
        }

        float maxStep = getMaxYawDegreesPerTick();
        float desired = Mth.clamp(diffDeg * getYawControllerGain(), -maxStep, maxStep);

        if (Math.abs(desired) < getYawMinTrackingSpeedDegPerTick()) {
            desired = Math.signum(diffDeg) * getYawMinTrackingSpeedDegPerTick();
        }

        float distanceFactor = Mth.clamp((absDiff - getYawDeadzoneDeg()) / 20.0F, 0.3F, 1.0F);
        desired *= distanceFactor;

        return Mth.clamp(desired, -maxStep, maxStep);
    }

    private float readActualBearingYawDeg() {
        AngleAccessor accessor = ANGLE_ACCESSOR_CACHE.computeIfAbsent(getClass(), WindRotoVerticalBlockEntity::resolveAngleAccessor);
        if (accessor.isResolved()) {
            Float value = accessor.read(this);
            if (value != null) {
                return wrap360(value);
            }
        }

        return wrap360(verticalCurrentYawDeg);
    }

    private static AngleAccessor resolveAngleAccessor(Class<?> targetClass) {
        String[] methodNames = new String[]{
                "getInterpolatedAngle",
                "getInterpolatedRenderedAngle",
                "getRenderedAngle",
                "getVisualAngle",
                "getAngle",
                "getBearingAngle"
        };

        for (String methodName : methodNames) {
            Method zeroArgMethod = findMethodInHierarchy(targetClass, methodName, 0);
            if (zeroArgMethod != null) {
                return new AngleAccessor(zeroArgMethod, false, null);
            }

            Method partialTickMethod = findMethodInHierarchy(targetClass, methodName, 1, float.class, Float.class);
            if (partialTickMethod != null) {
                return new AngleAccessor(partialTickMethod, true, null);
            }
        }

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
            Field field = WindRotoReflectionHelper.findFieldInHierarchy(targetClass, fieldName);
            if (field == null) {
                continue;
            }

            Class<?> fieldType = field.getType();
            if (Number.class.isAssignableFrom(fieldType)
                    || fieldType == int.class
                    || fieldType == float.class
                    || fieldType == double.class
                    || fieldType == long.class) {
                return new AngleAccessor(null, false, field);
            }
        }

        return NO_ANGLE_ACCESSOR;
    }

    @Nullable
    private static Method findMethodInHierarchy(Class<?> start, String name, int parameterCount, Class<?>... acceptedParameterTypes) {
        Class<?> c = start;
        while (c != null) {
            for (Method method : c.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) {
                    continue;
                }

                if (parameterCount == 1) {
                    Class<?> parameterType = method.getParameterTypes()[0];
                    boolean matches = false;
                    for (Class<?> acceptedType : acceptedParameterTypes) {
                        if (parameterType == acceptedType) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        continue;
                    }
                }

                try {
                    method.setAccessible(true);
                } catch (Throwable ignored) {
                }

                return method;
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
        float rpmMagnitude = Mth.clamp(absVelocity / getDegreesPerTickAt1Rpm(), 0.0F, (float) getVerticalMaxYawRpm());

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
        Level currentLevel = this.level;
        if (currentLevel == null) {
            return 0.0F;
        }

        Vec3 worldCenter = getWorldCenterForExternalSystems();
        return wrap360(WindReader.getWindAngle(currentLevel, worldCenter));
    }

    private float worldToLocalYaw(float worldYawDeg) {
        Direction placementNorth = getPlacementNorthDirection();
        if (placementNorth == null) {
            return 0.0F;
        }

        float base = directionToYaw(placementNorth);
        return wrap360(base - worldYawDeg + YAW_TARGET_OFFSET_DEG);
    }

    @Nullable
    private Direction getPlacementNorthDirection() {
        if (!placementNorthValid || placementNorthDirData < 0) {
            return null;
        }
        return Direction.from2DDataValue(placementNorthDirData);
    }

    private float getMaxYawDegreesPerTick() {
        return getVerticalMaxYawRpm() * getDegreesPerTickAt1Rpm();
    }

    private float getSuFactor() {
        return Mth.clamp(TwisterMillConfig.SU_FACTOR.get().floatValue(), 1.0F, 100.0F);
    }

    private float computeSuFromRpm(float rpm) {
        float r = Math.abs(rpm);
        if (r <= 0.0001F) {
            return 0.0F;
        }
        return (SU_PER_RPM * getSuFactor()) * r;
    }

    private void normalizePersistentStateAfterRead() {
        applySelectedRotationMode();

        verticalCurrentYawDeg = wrap360(verticalCurrentYawDeg);
        verticalTargetYawDeg = wrap360(verticalTargetYawDeg);
        lastWorldWindAngleDeg = wrap360(lastWorldWindAngleDeg);
        lastLocalTargetYawDeg = wrap360(lastLocalTargetYawDeg);

        float maxVelocity = getMaxYawDegreesPerTick();
        verticalYawVelocityDegPerTick = Mth.clamp(verticalYawVelocityDegPerTick, -maxVelocity, maxVelocity);

        generatedSpeedRpm = Mth.clamp(generatedSpeedRpm, -(float) getVerticalMaxYawRpm(), (float) getVerticalMaxYawRpm());
        generatedRpm = Math.round(generatedSpeedRpm);
        generatedSu = computeSuFromRpm(generatedSpeedRpm);

        zeroOutCreateWindmillContribution();
    }

    private void clearTransientRuntimeFlags() {
        verticalPulsePowered = false;
        verticalPulseStartTick = -1;
        nextWindAngleSampleAt = 0;
        obsidianCheckTimer = 0;
        lastRuntimeDirtyGameTime = Long.MIN_VALUE;
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

    private void syncIfNeeded() {
        boolean needsSync =
                lastSentGeneratedRpm != generatedRpm
                        || floatDiffers(lastSentGeneratedSpeedRpm, generatedSpeedRpm)
                        || floatDiffers(lastSentGeneratedSu, generatedSu)
                        || lastSentVisualRunning != lastVisualRunning
                        || lastSentPlacementNorthValid != placementNorthValid
                        || lastSentPlacementNorthDirData != placementNorthDirData
                        || floatDiffers(lastSentVerticalCurrentYawDeg, verticalCurrentYawDeg)
                        || floatDiffers(lastSentVerticalTargetYawDeg, verticalTargetYawDeg)
                        || floatDiffers(lastSentVerticalYawVelocityDegPerTick, verticalYawVelocityDegPerTick)
                        || lastSentVerticalYawMoving != verticalYawMoving
                        || lastSentVerticalParkedMode != verticalParkedMode
                        || floatDiffers(lastSentWorldWindAngleDeg, lastWorldWindAngleDeg)
                        || floatDiffers(lastSentLocalTargetYawDeg, lastLocalTargetYawDeg);

        if (!needsSync) {
            return;
        }

        lastSentGeneratedRpm = generatedRpm;
        lastSentGeneratedSpeedRpm = generatedSpeedRpm;
        lastSentGeneratedSu = generatedSu;
        lastSentVisualRunning = lastVisualRunning;
        lastSentPlacementNorthValid = placementNorthValid;
        lastSentPlacementNorthDirData = placementNorthDirData;
        lastSentVerticalCurrentYawDeg = verticalCurrentYawDeg;
        lastSentVerticalTargetYawDeg = verticalTargetYawDeg;
        lastSentVerticalYawVelocityDegPerTick = verticalYawVelocityDegPerTick;
        lastSentVerticalYawMoving = verticalYawMoving;
        lastSentVerticalParkedMode = verticalParkedMode;
        lastSentWorldWindAngleDeg = lastWorldWindAngleDeg;
        lastSentLocalTargetYawDeg = lastLocalTargetYawDeg;

        sendData();
    }

    private Vec3 getWorldCenterForExternalSystems() {
        if (level == null) {
            return new Vec3(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D);
        }
        return WindRotoReflectionHelper.getWorldCenterFallbackTransform(level, worldPosition);
    }

    private void zeroOutCreateWindmillContribution() {
        WindRotoReflectionHelper.zeroOutCreateWindmillContribution(this);
    }

    private void markRuntimeStateDirty(long time) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (lastRuntimeDirtyGameTime == Long.MIN_VALUE || time - lastRuntimeDirtyGameTime >= RUNTIME_DIRTY_INTERVAL_TICKS) {
            lastRuntimeDirtyGameTime = time;
            setChanged();
        }
    }


    private void debugInitTraceSystem() {
        if (level == null || level.isClientSide) {
            return;
        }

        WindRotoDebugDumpService.initialize(level.getServer());

        if (debugDimensionId == Integer.MIN_VALUE) {
            debugDimensionId = WindRotoVerticalDebugTraceBuffer.getOrCreateDimensionId(level.dimension().location());
        }
    }

    private void debugTraceMaybeSample(long gameTime) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (debugDimensionId == Integer.MIN_VALUE) {
            debugDimensionId = WindRotoVerticalDebugTraceBuffer.getOrCreateDimensionId(level.dimension().location());
        }

        int redstone = getExternalRedstonePower();
        int assemblyModeValue = assemblyMode != null ? assemblyMode.getValue() : -1;
        int forcedChunkX = forcedChunkKey != null ? forcedChunkKey.chunkX() : Integer.MIN_VALUE;
        int forcedChunkZ = forcedChunkKey != null ? forcedChunkKey.chunkZ() : Integer.MIN_VALUE;
        long pulseCooldownRemaining = Math.max(0L, verticalPulseCooldownUntil - gameTime);
        float bearingAngle = angle;

        int flags = 0;

        if (running) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_RUNNING;
        if (assembleNextTick) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_ASSEMBLE_QUEUED;
        if (movedContraption != null) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_ASSEMBLED;
        if (verticalManualEnabled) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_MANUAL_ENABLED;
        if (restoreFreeModeAfterManualDisassembly)
            flags |= WindRotoVerticalDebugTraceBuffer.FLAG_RESTORE_FREE_MODE_AFTER_MANUAL_DISASSEMBLY;
        if (placementNorthValid) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_PLACEMENT_NORTH_VALID;
        if (verticalYawMoving) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_YAW_MOVING;
        if (verticalParkedMode) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_PARKED_MODE;
        if (verticalPulsePowered) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_PULSE_POWERED;
        if (chunkForceRegistered) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_CHUNK_FORCED;
        if (lastVisualRunning) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_VISUAL_RUNNING;
        if (isMoveNeverPlaceModeSelected()) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_MOVE_NEVER_PLACE_MODE_SELECTED;
        if (isVerticalControlEnabled()) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_CONTROL_ENABLED;
        if (hasValidVerticalFacing()) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_FACING_VALID;
        if (isOverStressed()) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_OVERSTRESSED;
        if (lastSentVisualRunning) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_LAST_SENT_VISUAL_RUNNING;
        if (lastSentPlacementNorthValid) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_LAST_SENT_PLACEMENT_NORTH_VALID;
        if (lastSentVerticalYawMoving) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_LAST_SENT_VERTICAL_YAW_MOVING;
        if (lastSentVerticalParkedMode) flags |= WindRotoVerticalDebugTraceBuffer.FLAG_LAST_SENT_VERTICAL_PARKED_MODE;

        boolean changed =
                flags != debugLastFlags
                        || redstone != debugLastExternalRedstone
                        || placementNorthDirData != debugLastPlacementNorthDirData
                        || obsidianCheckTimer != debugLastObsidianCheckTimer
                        || assemblyModeValue != debugLastAssemblyModeValue
                        || generatedRpm != debugLastGeneratedRpm
                        || lastSentGeneratedRpm != debugLastSentGeneratedRpm
                        || lastSentPlacementNorthDirData != debugLastSentPlacementNorthDirData
                        || forcedChunkX != debugLastForcedChunkX
                        || forcedChunkZ != debugLastForcedChunkZ
                        || debugFloatDiffers(generatedSpeedRpm, debugLastGeneratedSpeedRpm)
                        || debugFloatDiffers(generatedSu, debugLastGeneratedSu)
                        || debugFloatDiffers(verticalCurrentYawDeg, debugLastCurrentYawDeg)
                        || debugFloatDiffers(verticalTargetYawDeg, debugLastTargetYawDeg)
                        || debugFloatDiffers(verticalYawVelocityDegPerTick, debugLastYawVelocityDegPerTick)
                        || debugFloatDiffers(lastWorldWindAngleDeg, debugLastWorldWindAngleDeg)
                        || debugFloatDiffers(lastLocalTargetYawDeg, debugLastLocalTargetYawDeg)
                        || debugFloatDiffers(bearingAngle, debugLastBearingAngle);

        if (!changed && gameTime < debugNextTraceSampleAt) {
            return;
        }

        debugNextTraceSampleAt = gameTime + WindRotoVerticalDebugTraceBuffer.TRACE_SAMPLE_INTERVAL_TICKS;
        debugLastFlags = flags;
        debugLastExternalRedstone = redstone;
        debugLastPlacementNorthDirData = placementNorthDirData;
        debugLastObsidianCheckTimer = obsidianCheckTimer;
        debugLastAssemblyModeValue = assemblyModeValue;
        debugLastGeneratedRpm = generatedRpm;
        debugLastSentGeneratedRpm = lastSentGeneratedRpm;
        debugLastSentPlacementNorthDirData = lastSentPlacementNorthDirData;
        debugLastForcedChunkX = forcedChunkX;
        debugLastForcedChunkZ = forcedChunkZ;
        debugLastGeneratedSpeedRpm = generatedSpeedRpm;
        debugLastGeneratedSu = generatedSu;
        debugLastCurrentYawDeg = verticalCurrentYawDeg;
        debugLastTargetYawDeg = verticalTargetYawDeg;
        debugLastYawVelocityDegPerTick = verticalYawVelocityDegPerTick;
        debugLastWorldWindAngleDeg = lastWorldWindAngleDeg;
        debugLastLocalTargetYawDeg = lastLocalTargetYawDeg;
        debugLastBearingAngle = bearingAngle;

        WindRotoVerticalDebugTraceBuffer.record(
                gameTime,
                worldPosition.asLong(),
                debugDimensionId,
                flags,
                redstone,
                placementNorthDirData,
                obsidianCheckTimer,
                assemblyModeValue,
                generatedRpm,
                lastSentGeneratedRpm,
                lastSentPlacementNorthDirData,
                forcedChunkX,
                forcedChunkZ,
                generatedSpeedRpm,
                generatedSu,
                verticalCurrentYawDeg,
                verticalTargetYawDeg,
                verticalYawVelocityDegPerTick,
                lastWorldWindAngleDeg,
                lastLocalTargetYawDeg,
                lastSentGeneratedSpeedRpm,
                lastSentGeneratedSu,
                lastSentVerticalCurrentYawDeg,
                lastSentVerticalTargetYawDeg,
                lastSentVerticalYawVelocityDegPerTick,
                lastSentWorldWindAngleDeg,
                lastSentLocalTargetYawDeg,
                bearingAngle,
                nextWindAngleSampleAt,
                verticalPulseStartTick,
                verticalPulseCooldownUntil,
                pulseCooldownRemaining,
                lastRuntimeDirtyGameTime
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
    private static float approachValue(float current, float target, float maxDelta) {
        if (current < target) {
            return Math.min(current + maxDelta, target);
        }
        if (current > target) {
            return Math.max(current - maxDelta, target);
        }
        return current;
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

    private static boolean floatDiffers(float a, float b) {
        if (Float.isNaN(a) != Float.isNaN(b)) {
            return true;
        }
        if (Float.isNaN(a)) {
            return false;
        }
        return Math.abs(a - b) > SYNC_FLOAT_EPSILON;
    }
}


