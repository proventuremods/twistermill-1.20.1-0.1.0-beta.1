package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.binaryredstone.BinarySignalProtocol;
import com.proventure.twistermill.block.custom.DigitalSignalTxBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public class DigitalSignalTxBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final ResourceLocation REDSTONE_LINK_ID =
            ResourceLocation.fromNamespaceAndPath("create", "redstone_link");
    private static final int FRAME_BIT_LENGTH = BinarySignalProtocol.FRAME_BITS;
    private static final int HALF_PHASE_TICKS = BinarySignalProtocol.FRAME_HALF_PHASE_TICKS;
    private static final int INTER_FRAME_OFF_PAUSE_TICKS = BinarySignalProtocol.FRAME_HALF_PHASE_TICKS;
    private static final int OUTPUT_ON = 15;
    private static final int OUTPUT_OFF = 0;
    private static final String TAG_OWNER_UUID = "OwnerUuid";

    private enum TransmissionPhase {
        IDLE,
        START_ON,
        START_OFF,
        BIT_HALF_1,
        BIT_HALF_2,
        CONTROL_HIGH_1,
        CONTROL_LOW_GAP,
        CONTROL_HIGH_2
    }

    private String pendingFrame;
    private String activeFrame;
    private boolean pendingControlToggle;
    private boolean startPendingNextTick;
    private TransmissionPhase phase = TransmissionPhase.IDLE;
    private int phaseTicksRemaining;
    private int offPauseTicksRemaining;
    private int bitIndex;
    private boolean currentBitIsOne;
    private int currentOutputLevel = OUTPUT_OFF;
    private UUID ownerUuid;

    public DigitalSignalTxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIGITAL_SIGNAL_TX_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        if (ownerUuid == null || ownerUuid.equals(this.ownerUuid)) {
            return;
        }
        this.ownerUuid = ownerUuid;
        setChanged();
    }

    public Direction getFacing() {
        return getBlockState().getValue(DigitalSignalTxBlock.HORIZONTAL_FACING);
    }

    public BlockPos getExpectedControlTablePos() {
        return worldPosition.relative(getFacing().getOpposite());
    }

    public boolean hasRedstoneLinkAbove() {
        if (level == null) {
            return false;
        }
        BlockState above = level.getBlockState(worldPosition.above());
        return REDSTONE_LINK_ID.equals(BuiltInRegistries.BLOCK.getKey(above.getBlock()));
    }

    public boolean hasControlTableOpposite() {
        if (level == null) {
            return false;
        }
        BlockPos expectedControlTablePos = getExpectedControlTablePos();
        return level.getBlockState(expectedControlTablePos).is(ModBlocks.CONTROL_TABLE_BLOCK.get())
                && level.getBlockEntity(expectedControlTablePos) instanceof ControlTableBlockEntity;
    }

    public boolean isRunningAllowed() {
        return hasRedstoneLinkAbove() && hasControlTableOpposite();
    }

    public void notifyLinkedControlTableForAdvancementCheck() {
        ControlTableBlockEntity controlTable = getLinkedControlTableForAdvancement();
        if (controlTable != null) {
            controlTable.tryTriggerSystemCompleteAdvancement();
        }
    }

    public int getCurrentOutputLevel() {
        return currentOutputLevel;
    }

    public void queueCodeFromControlTable(String frame) {
        if (level == null || level.isClientSide) {
            return;
        }

        String sanitizedFrame = sanitizeFrame(frame);
        if (sanitizedFrame == null) {
            abortTransmission();
            return;
        }

        pendingFrame = sanitizedFrame;
        if (!isTransmissionActive() && offPauseTicksRemaining <= 0) {
            startPendingNextTick = true;
            setOutputLevel(OUTPUT_OFF);
        }
    }

    void clearPendingCodeFrameFromControlTable() {
        if (level == null || level.isClientSide) {
            return;
        }

        pendingFrame = null;
        if (!isTransmissionActive() && !pendingControlToggle) {
            startPendingNextTick = false;
        }
    }

    public void queueControlToggleFromControlTable() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!BinarySignalProtocol.isControlMarkerConfigurationSafe()) {
            return;
        }
        if (pendingControlToggle || isControlMarkerActive()) {
            return;
        }

        pendingControlToggle = true;
        if (!isTransmissionActive() && offPauseTicksRemaining <= 0) {
            startPendingNextTick = true;
            setOutputLevel(OUTPUT_OFF);
        }
    }

    public void abortTransmission() {
        if (level == null || level.isClientSide) {
            return;
        }

        pendingFrame = null;
        activeFrame = null;
        pendingControlToggle = false;
        startPendingNextTick = false;
        phase = TransmissionPhase.IDLE;
        phaseTicksRemaining = 0;
        offPauseTicksRemaining = 0;
        bitIndex = 0;
        currentBitIsOne = false;
        setOutputLevel(OUTPUT_OFF);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        if (!isRunningAllowed()) {
            abortTransmission();
            return;
        }

        if (offPauseTicksRemaining > 0) {
            offPauseTicksRemaining--;
            if (offPauseTicksRemaining == 0 && (pendingControlToggle || pendingFrame != null)) {
                startPendingNextTick = true;
            }
            return;
        }

        if (startPendingNextTick) {
            startPendingNextTick = false;
            if (pendingControlToggle) {
                beginPendingControlToggle();
            } else if (pendingFrame != null) {
                beginPendingFrame();
            }
            return;
        }

        if (!isTransmissionActive()) {
            if (pendingControlToggle || pendingFrame != null) {
                startPendingNextTick = true;
            }
            return;
        }

        progressTransmission();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isTransmissionActive() {
        return phase != TransmissionPhase.IDLE;
    }

    private boolean isControlMarkerActive() {
        return phase == TransmissionPhase.CONTROL_HIGH_1
                || phase == TransmissionPhase.CONTROL_LOW_GAP
                || phase == TransmissionPhase.CONTROL_HIGH_2;
    }

    private void beginPendingFrame() {
        if (pendingFrame == null) {
            return;
        }
        if (phase == TransmissionPhase.IDLE) {
            activeFrame = pendingFrame;
            pendingFrame = null;
            bitIndex = 0;
            currentBitIsOne = false;
            offPauseTicksRemaining = 0;
            phase = TransmissionPhase.START_ON;
            phaseTicksRemaining = HALF_PHASE_TICKS;
            setOutputLevel(OUTPUT_ON);
            tryTriggerBinaryCodeTransmitterAdvancement();
        }
    }

    private void tryTriggerBinaryCodeTransmitterAdvancement() {
        ControlTableBlockEntity controlTable = getLinkedControlTableForAdvancement();
        if (controlTable != null) {
            controlTable.tryTriggerBinaryCodeTransmitterAdvancement(this);
        }
    }

    private ControlTableBlockEntity getLinkedControlTableForAdvancement() {
        if (level == null || level.isClientSide) {
            return null;
        }
        BlockPos expectedControlTablePos = getExpectedControlTablePos();
        if (!level.getBlockState(expectedControlTablePos).is(ModBlocks.CONTROL_TABLE_BLOCK.get())) {
            return null;
        }
        if (level.getBlockEntity(expectedControlTablePos) instanceof ControlTableBlockEntity controlTable) {
            return controlTable;
        }
        return null;
    }

    private void beginPendingControlToggle() {
        if (phase != TransmissionPhase.IDLE) {
            return;
        }
        if (!BinarySignalProtocol.isControlMarkerConfigurationSafe()) {
            pendingControlToggle = false;
            return;
        }
        pendingControlToggle = false;
        activeFrame = null;
        bitIndex = 0;
        currentBitIsOne = false;
        offPauseTicksRemaining = 0;
        phase = TransmissionPhase.CONTROL_HIGH_1;
        phaseTicksRemaining = BinarySignalProtocol.CONTROL_MARKER_HIGH_TICKS;
        setOutputLevel(OUTPUT_ON);
    }

    private void progressTransmission() {
        if (phaseTicksRemaining > 0) {
            phaseTicksRemaining--;
        }
        if (phaseTicksRemaining > 0) {
            return;
        }

        switch (phase) {
            case START_ON -> {
                phase = TransmissionPhase.START_OFF;
                phaseTicksRemaining = HALF_PHASE_TICKS;
                setOutputLevel(OUTPUT_OFF);
            }
            case START_OFF -> startBitHalfOne();
            case BIT_HALF_1 -> {
                phase = TransmissionPhase.BIT_HALF_2;
                phaseTicksRemaining = HALF_PHASE_TICKS;
                setOutputLevel(OUTPUT_OFF);
            }
            case BIT_HALF_2 -> {
                bitIndex++;
                if (bitIndex >= FRAME_BIT_LENGTH) {
                    finishFrame();
                } else {
                    startBitHalfOne();
                }
            }
            case CONTROL_HIGH_1 -> {
                phase = TransmissionPhase.CONTROL_LOW_GAP;
                phaseTicksRemaining = BinarySignalProtocol.CONTROL_MARKER_LOW_TICKS;
                setOutputLevel(OUTPUT_OFF);
            }
            case CONTROL_LOW_GAP -> {
                phase = TransmissionPhase.CONTROL_HIGH_2;
                phaseTicksRemaining = BinarySignalProtocol.CONTROL_MARKER_HIGH_TICKS;
                setOutputLevel(OUTPUT_ON);
            }
            case CONTROL_HIGH_2 -> finishControlMarker();
            case IDLE -> {
            }
        }
    }

    private void startBitHalfOne() {
        if (activeFrame == null || bitIndex < 0 || bitIndex >= activeFrame.length()) {
            finishFrame();
            return;
        }

        currentBitIsOne = activeFrame.charAt(bitIndex) == '1';
        phase = TransmissionPhase.BIT_HALF_1;
        phaseTicksRemaining = HALF_PHASE_TICKS;
        setOutputLevel(currentBitIsOne ? OUTPUT_ON : OUTPUT_OFF);
    }

    private void finishFrame() {
        activeFrame = null;
        startPendingNextTick = false;
        phase = TransmissionPhase.IDLE;
        phaseTicksRemaining = 0;
        bitIndex = 0;
        currentBitIsOne = false;
        offPauseTicksRemaining = INTER_FRAME_OFF_PAUSE_TICKS;
        setOutputLevel(OUTPUT_OFF);
    }

    private void finishControlMarker() {
        activeFrame = null;
        startPendingNextTick = false;
        phase = TransmissionPhase.IDLE;
        phaseTicksRemaining = 0;
        bitIndex = 0;
        currentBitIsOne = false;
        offPauseTicksRemaining = INTER_FRAME_OFF_PAUSE_TICKS;
        setOutputLevel(OUTPUT_OFF);
    }

    private String sanitizeFrame(String frame) {
        if (frame == null || frame.length() != FRAME_BIT_LENGTH) {
            return null;
        }
        for (int i = 0; i < frame.length(); i++) {
            char c = frame.charAt(i);
            if (c != '0' && c != '1') {
                return null;
            }
        }
        return frame;
    }

    private void setOutputLevel(int levelValue) {
        int normalizedValue = levelValue > 0 ? OUTPUT_ON : OUTPUT_OFF;
        if (currentOutputLevel == normalizedValue) {
            return;
        }
        currentOutputLevel = normalizedValue;
        setChanged();
        notifyOutputChanged();
    }

    private void notifyOutputChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
        level.updateNeighborsAt(worldPosition, state.getBlock());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUuid);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        ownerUuid = tag.hasUUID(TAG_OWNER_UUID) ? tag.getUUID(TAG_OWNER_UUID) : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.dstb.redstone_link_up_present")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        CreateLang.text(Boolean.toString(hasRedstoneLinkAbove()))
                .style(hasRedstoneLinkAbove() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.dstb.control_table_present")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        CreateLang.text(Boolean.toString(hasControlTableOpposite()))
                .style(hasControlTableOpposite() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        return true;
    }
}
