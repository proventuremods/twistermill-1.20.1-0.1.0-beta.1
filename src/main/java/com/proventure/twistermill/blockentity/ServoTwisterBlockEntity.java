package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.block.custom.ServoTwisterBlock;
import com.proventure.twistermill.debug.ServoTwisterDebugTraceBuffer;
import com.proventure.twistermill.debug.WindRotoDebugDumpService;
import com.proventure.twistermill.util.ServoTwisterVentStateHelper;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.List;

public class ServoTwisterBlockEntity extends MechanicalBearingBlockEntity {

    private static final float MIN_DEGREES_PER_TICK = 0.5F;
    private static final float MAX_DEGREES_PER_TICK = 8.0F;
    private static final float ANGLE_EPSILON = 0.01F;
    private static final float MAX_WIND_ROTO_BIND_ANGLE = 45.0F;
    private static final int RUNTIME_DIRTY_INTERVAL_TICKS = 20;
    private static final int NEVER_PLACE_MODE_ORDINAL = RotationMode.ROTATE_NEVER_PLACE.ordinal();
    private static final int SIGNAL_STABILITY_TICKS = 2;
    private static final int TOOLTIP_SIGNAL_STABILITY_TICKS = 3;

    protected ScrollOptionBehaviour<MaxAngleOption> maxAngleBehaviour;

    private boolean manualEnabled = false;
    private boolean lastVisualRunning = false;
    private boolean needsStateRefresh = true;
    private boolean boundToWindRoto = false;

    private int lastWestSignal = 0;
    private int lastEastSignal = 0;
    private int lastSouthSignal = 0;

    private int pendingWestSignal = 0;
    private int pendingEastSignal = 0;
    private int pendingSouthSignal = 0;

    private int pendingWestTicks = 0;
    private int pendingEastTicks = 0;
    private int pendingSouthTicks = 0;

    private int displayWestSignal = 0;
    private int displayEastSignal = 0;
    private int displaySouthSignal = 0;

    private int pendingDisplayWestSignal = 0;
    private int pendingDisplayEastSignal = 0;
    private int pendingDisplaySouthSignal = 0;

    private int pendingDisplayWestTicks = 0;
    private int pendingDisplayEastTicks = 0;
    private int pendingDisplaySouthTicks = 0;

    private float targetAngle = 0.0F;
    private long lastRuntimeDirtyGameTime = Long.MIN_VALUE;

    private static final float DEBUG_TRACE_FLOAT_EPSILON = 0.001F;

    private long debugNextTraceSampleAt = 0L;
    private int debugDimensionId = Integer.MIN_VALUE;
    private int debugLastFlags = Integer.MIN_VALUE;
    private int debugLastContraptionBlockCount = Integer.MIN_VALUE;
    private int debugLastLastWestSignal = Integer.MIN_VALUE;
    private int debugLastLastEastSignal = Integer.MIN_VALUE;
    private int debugLastLastSouthSignal = Integer.MIN_VALUE;
    private int debugLastPendingWestSignal = Integer.MIN_VALUE;
    private int debugLastPendingEastSignal = Integer.MIN_VALUE;
    private int debugLastPendingSouthSignal = Integer.MIN_VALUE;
    private int debugLastPendingWestTicks = Integer.MIN_VALUE;
    private int debugLastPendingEastTicks = Integer.MIN_VALUE;
    private int debugLastPendingSouthTicks = Integer.MIN_VALUE;
    private int debugLastDisplayWestSignal = Integer.MIN_VALUE;
    private int debugLastDisplayEastSignal = Integer.MIN_VALUE;
    private int debugLastDisplaySouthSignal = Integer.MIN_VALUE;
    private int debugLastPendingDisplayWestSignal = Integer.MIN_VALUE;
    private int debugLastPendingDisplayEastSignal = Integer.MIN_VALUE;
    private int debugLastPendingDisplaySouthSignal = Integer.MIN_VALUE;
    private int debugLastPendingDisplayWestTicks = Integer.MIN_VALUE;
    private int debugLastPendingDisplayEastTicks = Integer.MIN_VALUE;
    private int debugLastPendingDisplaySouthTicks = Integer.MIN_VALUE;
    private int debugLastConfiguredMaxDegrees = Integer.MIN_VALUE;
    private int debugLastEffectiveMaxDegrees = Integer.MIN_VALUE;
    private int debugLastMovementModeValue = Integer.MIN_VALUE;
    private int debugLastFacingOrdinal = Integer.MIN_VALUE;
    private int debugLastWestInputOrdinal = Integer.MIN_VALUE;
    private int debugLastEastInputOrdinal = Integer.MIN_VALUE;
    private int debugLastSouthInputOrdinal = Integer.MIN_VALUE;
    private long debugLastRuntimeDirtyGameTime = Long.MIN_VALUE;
    private float debugLastAngle = Float.NaN;
    private float debugLastTargetAngle = Float.NaN;
    private float debugLastBindingAngle = Float.NaN;
    private float debugLastGeneratedSpeed = Float.NaN;

    public enum MaxAngleOption implements INamedIconOptions {
        DEG_30(30, "twistermill.max_angle.option.30", AllIcons.I_ROTATE_NEVER_PLACE),
        DEG_60(60, "twistermill.max_angle.option.60", AllIcons.I_ROTATE_PLACE),
        DEG_90(90, "twistermill.max_angle.option.90", AllIcons.I_ROTATE_PLACE_RETURNED);

        private final int maxDegrees;
        private final String translationKey;
        private final AllIcons icon;

        MaxAngleOption(int maxDegrees, String translationKey, AllIcons icon) {
            this.maxDegrees = maxDegrees;
            this.translationKey = translationKey;
            this.icon = icon;
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

            if (!searchMovedStructure(world, offset, null))
                return false;

            startMoving(world);
            expandBoundsAroundAxis(facing.getAxis());

            if (windmill && sailBlocks < 1)
                throw AssemblyException.notEnoughSails(sailBlocks);

            return !blocks.isEmpty();
        }

        @Override
        public void addBlock(Level level, BlockPos pos, Pair<StructureBlockInfo, BlockEntity> capture) {
            BlockPos localPos = pos.subtract(anchor);

            if (!getBlocks().containsKey(localPos))
                sailBlocks++;

            super.addBlock(level, pos, capture);
        }
    }

    public ServoTwisterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SERVO_TWISTER_BE.get(), pos, state);
        ensureNeverPlaceMode();
    }

    public float getWindRotoBindingAngleDegrees() {
        return normalizeAngleForWindRoto(angle);
    }

    public int getBoundContraptionBlockCount() {
        return getContraptionBlockCount();
    }

    public void setBoundToWindRoto(boolean boundToWindRoto) {
        if (this.boundToWindRoto == boundToWindRoto)
            return;

        this.boundToWindRoto = boundToWindRoto;
        setChanged();
        sendData();
    }

    public boolean isBoundToWindRoto() {
        return boundToWindRoto;
    }

    public void onPlayerToggle(@SuppressWarnings("unused") Player player) {
        if (level == null || level.isClientSide)
            return;

        if (running || assembleNextTick) {
            manualEnabled = false;
            disassemble();
            stopMotion();
            updateVisualRunning(false);
            setChanged();
            sendData();
            return;
        }

        manualEnabled = true;
        assembleNextTick = true;
        setChanged();
        sendData();
    }

    @Override
    public void assemble() {
        if (level == null)
            return;

        BlockState state = level.getBlockState(worldPosition);
        if (!(state.getBlock() instanceof BearingBlock))
            return;

        Direction direction = state.getValue(BearingBlock.FACING);
        BearingContraption contraption = new AllBlocksWindmillContraption(isWindmill(), direction);

        try {
            if (!contraption.assemble(level, worldPosition))
                return;
            lastException = null;
        } catch (AssemblyException e) {
            lastException = e;
            sendData();
            return;
        }

        if (isWindmill())
            award(AllAdvancements.WINDMILL);
        if (contraption.getSailBlocks() >= 16 * 8)
            award(AllAdvancements.WINDMILL_MAXED);

        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        movedContraption = ControlledContraptionEntity.create(level, this, contraption);

        BlockPos anchor = worldPosition.relative(direction);
        movedContraption.setPos(anchor.getX(), anchor.getY(), anchor.getZ());
        movedContraption.setRotationAxis(direction.getAxis());
        level.addFreshEntity(movedContraption);

        AllSoundEvents.CONTRAPTION_ASSEMBLE.playOnServer(level, worldPosition);

        if (contraption.containsBlockBreakers())
            award(AllAdvancements.CONTRAPTION_ACTORS);

        running = true;
        angle = 0;
        sendData();
        updateGeneratedRotation();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        behaviours.remove(movementMode);
        ensureNeverPlaceMode();

        maxAngleBehaviour = new ScrollOptionBehaviour<>(
                MaxAngleOption.class,
                Component.translatable("twistermill.max_angle"),
                this,
                getMovementModeSlot()
        );
        maxAngleBehaviour.withCallback($ -> onMaxAngleChanged());
        behaviours.add(maxAngleBehaviour);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null)
            return;

        boolean serverSide = !level.isClientSide;
        if (serverSide) {
            boolean inputsChanged = updateCachedInputsFromWorld();
            if (inputsChanged) {
                sendData();
            }
        }

        if (needsStateRefresh) {
            refreshRuntimeStateFromWorld();
            needsStateRefresh = false;
        } else if (serverSide) {
            ensureNeverPlaceMode();
        }

        updateDisplayedSignals();

        if (serverSide) {
            ServoTwisterVentStateHelper.syncVentState(
                    level,
                    worldPosition,
                    getBlockState(),
                    displayWestSignal,
                    displayEastSignal,
                    displaySouthSignal
            );
        }

        if (level.isClientSide)
            return;

        long time = level.getGameTime();

        if (running) {
            if (lastSouthSignal == 1) {
                float step = getDegreesPerTickForSignal(lastWestSignal);
                updateAngleAndSync(angle - step, time);
            } else if (lastSouthSignal == 2) {
                float step = getDegreesPerTickForSignal(lastWestSignal);
                updateAngleAndSync(angle + step, time);
            } else {
                float step = getDegreesPerTickForSignal(lastWestSignal);
                float newAngle = approachAngle(angle, targetAngle, step);
                updateAngleAndSync(newAngle, time);
            }
        }

        boolean visualRunning =
                running && (manualEnabled || lastEastSignal > 0 || lastSouthSignal > 0 || Math.abs(angle) > ANGLE_EPSILON);

        updateVisualRunning(visualRunning);
        debugTraceMaybeSample(time);
    }

    @Override
    public float getGeneratedSpeed() {
        return 0.0F;
    }

    @Override
    protected boolean isWindmill() {
        return true;
    }

    @Override
    public void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putBoolean("ManualEnabled", manualEnabled);
        tag.putBoolean("BoundToWindRoto", boundToWindRoto);
        tag.putInt("LastWestSignal", lastWestSignal);
        tag.putInt("LastEastSignal", lastEastSignal);
        tag.putInt("LastSouthSignal", lastSouthSignal);
        tag.putFloat("TargetAngle", targetAngle);
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        ensureNeverPlaceMode();

        if (tag.contains("ManualEnabled"))
            manualEnabled = tag.getBoolean("ManualEnabled");
        if (tag.contains("BoundToWindRoto"))
            boundToWindRoto = tag.getBoolean("BoundToWindRoto");
        if (tag.contains("LastWestSignal"))
            lastWestSignal = tag.getInt("LastWestSignal");
        if (tag.contains("LastEastSignal"))
            lastEastSignal = tag.getInt("LastEastSignal");
        if (tag.contains("LastSouthSignal"))
            lastSouthSignal = tag.getInt("LastSouthSignal");
        if (tag.contains("TargetAngle"))
            targetAngle = tag.getFloat("TargetAngle");

        pendingWestSignal = lastWestSignal;
        pendingEastSignal = lastEastSignal;
        pendingSouthSignal = lastSouthSignal;
        pendingWestTicks = 0;
        pendingEastTicks = 0;
        pendingSouthTicks = 0;

        displayWestSignal = lastWestSignal;
        displayEastSignal = lastEastSignal;
        displaySouthSignal = lastSouthSignal;

        pendingDisplayWestSignal = displayWestSignal;
        pendingDisplayEastSignal = displayEastSignal;
        pendingDisplaySouthSignal = displaySouthSignal;
        pendingDisplayWestTicks = 0;
        pendingDisplayEastTicks = 0;
        pendingDisplaySouthTicks = 0;

        needsStateRefresh = true;
        lastRuntimeDirtyGameTime = Long.MIN_VALUE;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean details = com.simibubi.create.AllKeys.ctrlDown();

        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.servo.angle")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(Math.abs(angle))
                .style(ChatFormatting.YELLOW)
                .add(Component.literal("°"))
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.max_angle")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        if (displaySouthSignal == 6) {
            CreateLang.number(5)
                    .style(ChatFormatting.GOLD)
                    .add(Component.literal("°/step"))
                    .forGoggles(tooltip, 1);
        } else if (displaySouthSignal == 7) {
            CreateLang.number(10)
                    .style(ChatFormatting.GOLD)
                    .add(Component.literal("°/step"))
                    .forGoggles(tooltip, 1);
        } else if (displaySouthSignal == 8) {
            CreateLang.number(20)
                    .style(ChatFormatting.GOLD)
                    .add(Component.literal("°/step"))
                    .forGoggles(tooltip, 1);
        } else if (displaySouthSignal == 9) {
            CreateLang.number(30)
                    .style(ChatFormatting.GOLD)
                    .add(Component.literal("°/step"))
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.number(getDisplayedEffectiveMaxDegrees())
                    .style(ChatFormatting.GOLD)
                    .add(Component.literal("°"))
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

        CreateLang.translate("tooltip.twistermill.bound_to_wind_roto")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text(boundToWindRoto ? "true" : "false")
                .style(boundToWindRoto ? ChatFormatting.GREEN : ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.west_speed_input")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(displayWestSignal)
                .style(ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.east_angle_input")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(displayEastSignal)
                .style(ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.south_input")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(displaySouthSignal)
                .style(ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.servo.contraption_blocks")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.number(getContraptionBlockCount())
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        return true;
    }

    private static float normalizeAngleForWindRoto(float rawAngle) {
        float absAngle = Math.abs(rawAngle);

        if (absAngle <= ANGLE_EPSILON || absAngle > MAX_WIND_ROTO_BIND_ANGLE)
            return 0.0F;

        return absAngle;
    }

    private void ensureNeverPlaceMode() {
        if (movementMode == null)
            return;

        if (movementMode.getValue() != NEVER_PLACE_MODE_ORDINAL) {
            movementMode.setValue(NEVER_PLACE_MODE_ORDINAL);
        }
    }

    private boolean updateCachedInputsFromWorld() {
        int previousWestSignal = lastWestSignal;
        int previousEastSignal = lastEastSignal;
        int previousSouthSignal = lastSouthSignal;
        float previousTargetAngle = targetAngle;

        lastWestSignal = updateStableWestSignal();
        lastEastSignal = updateStableEastSignal();
        lastSouthSignal = updateStableSouthSignal();
        targetAngle = getTargetAngleForCurrentMode();

        return previousWestSignal != lastWestSignal
                || previousEastSignal != lastEastSignal
                || previousSouthSignal != lastSouthSignal
                || Float.compare(previousTargetAngle, targetAngle) != 0;
    }

    private int updateStableWestSignal() {
        int raw = getWestSpeedSignal();

        if (raw == lastWestSignal) {
            pendingWestSignal = raw;
            pendingWestTicks = 0;
            return lastWestSignal;
        }

        if (raw != pendingWestSignal) {
            pendingWestSignal = raw;
            pendingWestTicks = 1;
            return lastWestSignal;
        }

        pendingWestTicks++;
        if (pendingWestTicks >= SIGNAL_STABILITY_TICKS) {
            lastWestSignal = pendingWestSignal;
            pendingWestTicks = 0;
        }

        return lastWestSignal;
    }

    private int updateStableEastSignal() {
        int raw = getEastAngleSignal();

        if (raw == lastEastSignal) {
            pendingEastSignal = raw;
            pendingEastTicks = 0;
            return lastEastSignal;
        }

        if (raw != pendingEastSignal) {
            pendingEastSignal = raw;
            pendingEastTicks = 1;
            return lastEastSignal;
        }

        pendingEastTicks++;
        if (pendingEastTicks >= SIGNAL_STABILITY_TICKS) {
            lastEastSignal = pendingEastSignal;
            pendingEastTicks = 0;
        }

        return lastEastSignal;
    }

    private int updateStableSouthSignal() {
        int raw = getSouthModeSignal();

        if (raw == lastSouthSignal) {
            pendingSouthSignal = raw;
            pendingSouthTicks = 0;
            return lastSouthSignal;
        }

        if (raw != pendingSouthSignal) {
            pendingSouthSignal = raw;
            pendingSouthTicks = 1;
            return lastSouthSignal;
        }

        pendingSouthTicks++;
        if (pendingSouthTicks >= SIGNAL_STABILITY_TICKS) {
            lastSouthSignal = pendingSouthSignal;
            pendingSouthTicks = 0;
        }

        return lastSouthSignal;
    }

    private void updateDisplayedSignals() {
        displayWestSignal = updateDisplayedWestSignal();
        displayEastSignal = updateDisplayedEastSignal();
        displaySouthSignal = updateDisplayedSouthSignal();
    }

    private int updateDisplayedWestSignal() {
        int raw = lastWestSignal;

        if (raw == displayWestSignal) {
            pendingDisplayWestSignal = raw;
            pendingDisplayWestTicks = 0;
            return displayWestSignal;
        }

        if (raw != pendingDisplayWestSignal) {
            pendingDisplayWestSignal = raw;
            pendingDisplayWestTicks = 1;
            return displayWestSignal;
        }

        pendingDisplayWestTicks++;
        if (pendingDisplayWestTicks >= TOOLTIP_SIGNAL_STABILITY_TICKS) {
            displayWestSignal = pendingDisplayWestSignal;
            pendingDisplayWestTicks = 0;
        }

        return displayWestSignal;
    }

    private int updateDisplayedEastSignal() {
        int raw = lastEastSignal;

        if (raw == displayEastSignal) {
            pendingDisplayEastSignal = raw;
            pendingDisplayEastTicks = 0;
            return displayEastSignal;
        }

        if (raw != pendingDisplayEastSignal) {
            pendingDisplayEastSignal = raw;
            pendingDisplayEastTicks = 1;
            return displayEastSignal;
        }

        pendingDisplayEastTicks++;
        if (pendingDisplayEastTicks >= TOOLTIP_SIGNAL_STABILITY_TICKS) {
            displayEastSignal = pendingDisplayEastSignal;
            pendingDisplayEastTicks = 0;
        }

        return displayEastSignal;
    }

    private int updateDisplayedSouthSignal() {
        int raw = lastSouthSignal;

        if (raw == displaySouthSignal) {
            pendingDisplaySouthSignal = raw;
            pendingDisplaySouthTicks = 0;
            return displaySouthSignal;
        }

        if (raw != pendingDisplaySouthSignal) {
            pendingDisplaySouthSignal = raw;
            pendingDisplaySouthTicks = 1;
            return displaySouthSignal;
        }

        pendingDisplaySouthTicks++;
        if (pendingDisplaySouthTicks >= TOOLTIP_SIGNAL_STABILITY_TICKS) {
            displaySouthSignal = pendingDisplaySouthSignal;
            pendingDisplaySouthTicks = 0;
        }

        return displaySouthSignal;
    }

    private int getConfiguredMaxDegrees() {
        if (maxAngleBehaviour == null)
            return 60;

        return maxAngleBehaviour.get().getMaxDegrees();
    }

    private int getEffectiveMaxDegrees() {
        int configured = getConfiguredMaxDegrees();

        return switch (lastSouthSignal) {
            case 3 -> configured * 2;
            case 4 -> configured * 3;
            case 5 -> configured * 4;
            default -> configured;
        };
    }

    private int getDisplayedEffectiveMaxDegrees() {
        int configured = getConfiguredMaxDegrees();

        return switch (displaySouthSignal) {
            case 3 -> configured * 2;
            case 4 -> configured * 3;
            case 5 -> configured * 4;
            default -> configured;
        };
    }

    private float getTargetAngleForCurrentMode() {
        if (lastSouthSignal >= 10)
            return 0.0F;

        if (lastSouthSignal == 6)
            return -(8 - Mth.clamp(lastEastSignal, 0, 15)) * 5.0F;

        if (lastSouthSignal == 7)
            return -(8 - Mth.clamp(lastEastSignal, 0, 15)) * 10.0F;

        if (lastSouthSignal == 8)
            return -(8 - Mth.clamp(lastEastSignal, 0, 15)) * 20.0F;

        if (lastSouthSignal == 9)
            return -(8 - Mth.clamp(lastEastSignal, 0, 15)) * 30.0F;

        return getTargetAngleForSignal(lastEastSignal);
    }

    private float getTargetAngleForSignal(int redstonePower) {
        int clampedPower = Mth.clamp(redstonePower, 0, 15);
        float requested = Mth.lerp(clampedPower / 15.0F, 0.0F, (float) getEffectiveMaxDegrees());
        return -requested;
    }

    private void onMaxAngleChanged() {
        if (level == null || level.isClientSide)
            return;

        updateCachedInputsFromWorld();
        setChanged();
        sendData();
    }

    private void refreshRuntimeStateFromWorld() {
        ensureNeverPlaceMode();

        boolean visualRunning =
                running && (manualEnabled || lastEastSignal > 0 || lastSouthSignal > 0 || Math.abs(angle) > ANGLE_EPSILON);
        updateVisualRunning(visualRunning);
    }

    private void stopMotion() {
        targetAngle = 0.0F;
    }

    private int getSideSignal(Direction side) {
        if (level == null)
            return 0;

        BlockPos neighborPos = worldPosition.relative(side);
        int direct = level.getDirectSignal(neighborPos, side);
        int normal = level.getSignal(neighborPos, side);
        return Mth.clamp(Math.max(direct, normal), 0, 15);
    }

    private Direction getBlockFacing() {
        BlockState state = getBlockState();
        if (state.hasProperty(BlockStateProperties.FACING))
            return state.getValue(BlockStateProperties.FACING);
        return Direction.NORTH;
    }

    private Direction mapLocalSideToWorld(Direction localSide) {
        Direction facing = getBlockFacing();

        return switch (facing) {
            case NORTH -> localSide;

            case SOUTH -> switch (localSide) {
                case NORTH -> Direction.SOUTH;
                case SOUTH -> Direction.NORTH;
                case WEST -> Direction.EAST;
                case EAST -> Direction.WEST;
                default -> localSide;
            };

            case EAST -> switch (localSide) {
                case NORTH -> Direction.EAST;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                case EAST -> Direction.SOUTH;
                default -> localSide;
            };

            case WEST -> switch (localSide) {
                case NORTH -> Direction.WEST;
                case SOUTH -> Direction.EAST;
                case WEST -> Direction.SOUTH;
                case EAST -> Direction.NORTH;
                default -> localSide;
            };

            case UP -> switch (localSide) {
                case NORTH -> Direction.UP;
                case SOUTH -> Direction.DOWN;
                case WEST -> Direction.WEST;
                case EAST -> Direction.EAST;
                default -> localSide;
            };

            case DOWN -> switch (localSide) {
                case NORTH -> Direction.DOWN;
                case SOUTH -> Direction.UP;
                case WEST -> Direction.WEST;
                case EAST -> Direction.EAST;
                default -> localSide;
            };
        };
    }

    private Direction getRelativeWestInputSide() {
        Direction facing = getBlockFacing();

        if (facing.getAxis().isVertical()) {
            return Direction.WEST;
        }

        return mapLocalSideToWorld(Direction.WEST);
    }

    private Direction getRelativeEastInputSide() {
        return mapLocalSideToWorld(Direction.EAST);
    }

    private Direction getRelativeSouthInputSide() {
        Direction facing = getBlockFacing();

        if (facing.getAxis().isHorizontal()) {
            return Direction.UP;
        }

        if (facing == Direction.UP) {
            return Direction.SOUTH;
        }

        return Direction.NORTH;
    }

    private int getWestSpeedSignal() {
        return getSideSignal(getRelativeWestInputSide());
    }

    private int getEastAngleSignal() {
        return getSideSignal(getRelativeEastInputSide());
    }

    private int getSouthModeSignal() {
        return getSideSignal(getRelativeSouthInputSide());
    }

    private float getDegreesPerTickForSignal(int redstonePower) {
        if (redstonePower <= 0)
            return MIN_DEGREES_PER_TICK;

        float t = (redstonePower - 1) / 14.0F;
        return Mth.lerp(t, MIN_DEGREES_PER_TICK, MAX_DEGREES_PER_TICK);
    }

    private static float approachAngle(float current, float target, float maxStep) {
        float diff = target - current;

        if (Math.abs(diff) <= maxStep)
            return target;

        return current + Math.signum(diff) * maxStep;
    }

    private int getContraptionBlockCount() {
        if (movedContraption == null || movedContraption.getContraption() == null)
            return 0;

        return movedContraption.getContraption().getBlocks().size();
    }

    private void updateAngleAndSync(float newAngle, long gameTime) {
        if (Math.abs(newAngle - angle) <= ANGLE_EPSILON) {
            return;
        }

        angle = newAngle;
        applyRotation();
        markRuntimeStateDirty(gameTime);
        sendData();
    }

    private void markRuntimeStateDirty(long gameTime) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (lastRuntimeDirtyGameTime == Long.MIN_VALUE || gameTime - lastRuntimeDirtyGameTime >= RUNTIME_DIRTY_INTERVAL_TICKS) {
            lastRuntimeDirtyGameTime = gameTime;
            setChanged();
        }
    }

    private void debugInitTraceSystem() {
        if (level == null || level.isClientSide) {
            return;
        }

        WindRotoDebugDumpService.initialize(level.getServer());

        if (debugDimensionId == Integer.MIN_VALUE) {
            debugDimensionId = ServoTwisterDebugTraceBuffer.getOrCreateDimensionId(level.dimension().location());
        }
    }

    private void debugTraceMaybeSample(long gameTime) {
        if (level == null || level.isClientSide) {
            return;
        }

        debugInitTraceSystem();

        int movementModeValue = movementMode != null ? movementMode.getValue() : -1;
        int configuredMaxDegrees = getConfiguredMaxDegrees();
        int effectiveMaxDegrees = getEffectiveMaxDegrees();
        int contraptionBlockCount = getContraptionBlockCount();

        Direction facing = getBlockFacing();
        Direction westInput = getRelativeWestInputSide();
        Direction eastInput = getRelativeEastInputSide();
        Direction southInput = getRelativeSouthInputSide();

        int facingOrdinal = debugDirectionOrdinal(facing);
        int westInputOrdinal = debugDirectionOrdinal(westInput);
        int eastInputOrdinal = debugDirectionOrdinal(eastInput);
        int southInputOrdinal = debugDirectionOrdinal(southInput);

        float currentAngle = angle;
        float currentTargetAngle = targetAngle;
        float bindingAngle = getWindRotoBindingAngleDegrees();
        float generatedSpeed = getGeneratedSpeed();

        int flags = 0;
        if (running) flags |= ServoTwisterDebugTraceBuffer.FLAG_RUNNING;
        if (assembleNextTick) flags |= ServoTwisterDebugTraceBuffer.FLAG_ASSEMBLE_QUEUED;
        if (movedContraption != null) flags |= ServoTwisterDebugTraceBuffer.FLAG_ASSEMBLED;
        if (manualEnabled) flags |= ServoTwisterDebugTraceBuffer.FLAG_MANUAL_ENABLED;
        if (needsStateRefresh) flags |= ServoTwisterDebugTraceBuffer.FLAG_NEEDS_STATE_REFRESH;
        if (boundToWindRoto) flags |= ServoTwisterDebugTraceBuffer.FLAG_BOUND_TO_WIND_ROTO;
        if (lastVisualRunning) flags |= ServoTwisterDebugTraceBuffer.FLAG_VISUAL_RUNNING;
        if (hasNetwork()) flags |= ServoTwisterDebugTraceBuffer.FLAG_HAS_NETWORK;
        if (source != null) flags |= ServoTwisterDebugTraceBuffer.FLAG_SOURCE_PRESENT;

        boolean changed =
                flags != debugLastFlags
                        || contraptionBlockCount != debugLastContraptionBlockCount
                        || lastWestSignal != debugLastLastWestSignal
                        || lastEastSignal != debugLastLastEastSignal
                        || lastSouthSignal != debugLastLastSouthSignal
                        || pendingWestSignal != debugLastPendingWestSignal
                        || pendingEastSignal != debugLastPendingEastSignal
                        || pendingSouthSignal != debugLastPendingSouthSignal
                        || pendingWestTicks != debugLastPendingWestTicks
                        || pendingEastTicks != debugLastPendingEastTicks
                        || pendingSouthTicks != debugLastPendingSouthTicks
                        || displayWestSignal != debugLastDisplayWestSignal
                        || displayEastSignal != debugLastDisplayEastSignal
                        || displaySouthSignal != debugLastDisplaySouthSignal
                        || pendingDisplayWestSignal != debugLastPendingDisplayWestSignal
                        || pendingDisplayEastSignal != debugLastPendingDisplayEastSignal
                        || pendingDisplaySouthSignal != debugLastPendingDisplaySouthSignal
                        || pendingDisplayWestTicks != debugLastPendingDisplayWestTicks
                        || pendingDisplayEastTicks != debugLastPendingDisplayEastTicks
                        || pendingDisplaySouthTicks != debugLastPendingDisplaySouthTicks
                        || configuredMaxDegrees != debugLastConfiguredMaxDegrees
                        || effectiveMaxDegrees != debugLastEffectiveMaxDegrees
                        || movementModeValue != debugLastMovementModeValue
                        || facingOrdinal != debugLastFacingOrdinal
                        || westInputOrdinal != debugLastWestInputOrdinal
                        || eastInputOrdinal != debugLastEastInputOrdinal
                        || southInputOrdinal != debugLastSouthInputOrdinal
                        || lastRuntimeDirtyGameTime != debugLastRuntimeDirtyGameTime
                        || debugFloatDiffers(currentAngle, debugLastAngle)
                        || debugFloatDiffers(currentTargetAngle, debugLastTargetAngle)
                        || debugFloatDiffers(bindingAngle, debugLastBindingAngle)
                        || debugFloatDiffers(generatedSpeed, debugLastGeneratedSpeed);

        if (!changed && gameTime < debugNextTraceSampleAt) {
            return;
        }

        debugNextTraceSampleAt = gameTime + ServoTwisterDebugTraceBuffer.TRACE_SAMPLE_INTERVAL_TICKS;
        debugLastFlags = flags;
        debugLastContraptionBlockCount = contraptionBlockCount;
        debugLastLastWestSignal = lastWestSignal;
        debugLastLastEastSignal = lastEastSignal;
        debugLastLastSouthSignal = lastSouthSignal;
        debugLastPendingWestSignal = pendingWestSignal;
        debugLastPendingEastSignal = pendingEastSignal;
        debugLastPendingSouthSignal = pendingSouthSignal;
        debugLastPendingWestTicks = pendingWestTicks;
        debugLastPendingEastTicks = pendingEastTicks;
        debugLastPendingSouthTicks = pendingSouthTicks;
        debugLastDisplayWestSignal = displayWestSignal;
        debugLastDisplayEastSignal = displayEastSignal;
        debugLastDisplaySouthSignal = displaySouthSignal;
        debugLastPendingDisplayWestSignal = pendingDisplayWestSignal;
        debugLastPendingDisplayEastSignal = pendingDisplayEastSignal;
        debugLastPendingDisplaySouthSignal = pendingDisplaySouthSignal;
        debugLastPendingDisplayWestTicks = pendingDisplayWestTicks;
        debugLastPendingDisplayEastTicks = pendingDisplayEastTicks;
        debugLastPendingDisplaySouthTicks = pendingDisplaySouthTicks;
        debugLastConfiguredMaxDegrees = configuredMaxDegrees;
        debugLastEffectiveMaxDegrees = effectiveMaxDegrees;
        debugLastMovementModeValue = movementModeValue;
        debugLastFacingOrdinal = facingOrdinal;
        debugLastWestInputOrdinal = westInputOrdinal;
        debugLastEastInputOrdinal = eastInputOrdinal;
        debugLastSouthInputOrdinal = southInputOrdinal;
        debugLastRuntimeDirtyGameTime = lastRuntimeDirtyGameTime;
        debugLastAngle = currentAngle;
        debugLastTargetAngle = currentTargetAngle;
        debugLastBindingAngle = bindingAngle;
        debugLastGeneratedSpeed = generatedSpeed;

        ServoTwisterDebugTraceBuffer.record(
                gameTime,
                worldPosition.asLong(),
                debugDimensionId,
                ServoTwisterDebugTraceBuffer.TYPE_SERVO,
                flags,
                contraptionBlockCount,
                lastWestSignal,
                lastEastSignal,
                lastSouthSignal,
                pendingWestSignal,
                pendingEastSignal,
                pendingSouthSignal,
                pendingWestTicks,
                pendingEastTicks,
                pendingSouthTicks,
                displayWestSignal,
                displayEastSignal,
                displaySouthSignal,
                pendingDisplayWestSignal,
                pendingDisplayEastSignal,
                pendingDisplaySouthSignal,
                pendingDisplayWestTicks,
                pendingDisplayEastTicks,
                pendingDisplaySouthTicks,
                configuredMaxDegrees,
                effectiveMaxDegrees,
                movementModeValue,
                facingOrdinal,
                westInputOrdinal,
                eastInputOrdinal,
                southInputOrdinal,
                lastRuntimeDirtyGameTime,
                currentAngle,
                currentTargetAngle,
                bindingAngle,
                generatedSpeed
        );
    }

    private static int debugDirectionOrdinal(@Nullable Direction direction) {
        return direction == null ? -1 : direction.ordinal();
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
}