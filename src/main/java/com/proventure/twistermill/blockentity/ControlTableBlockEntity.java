package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.advancement.ModCriteriaTriggers;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.AllKeys;
import com.proventure.twistermill.block.ModBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import com.proventure.twistermill.menu.ControlTableMenu;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class ControlTableBlockEntity extends SmartBlockEntity implements MenuProvider, IHaveGoggleInformation {

    public static final int DEFAULT_SEQUENCE_LENGTH_TICKS = 20;
    public static final int DEFAULT_PULSE_LENGTH_TICKS = 2;
    public static final int DEFAULT_PAUSE_LENGTH_TICKS = 2;
    public static final int DEFAULT_REPEAT_INTERVAL_TICKS = 0;
    public static final int LAUNCH_MODE_RS_PULSE = 0;
    public static final int LAUNCH_MODE_ON_CHANGE = 1;
    public static final int LAUNCH_MODE_OFF = 2;
    public static final int DEFAULT_LAUNCH_MODE = LAUNCH_MODE_OFF;

    private static final int MIN_TICKS = 0;
    private static final int MAX_TICKS = 400;
    private static final int SEND_COALESCE_TICKS = 10;
    private static final int RIBOB_SLOT_COUNT = 3;
    private static final int EMPTY_SIGNAL = -1;
    private static final int LEGACY_SEND_OPTION_1 = 0;
    private static final int LEGACY_SEND_OPTION_2 = 1;
    private static final int LEGACY_SEND_OPTION_3 = 2;
    private static final String TAG_SEND_ENABLED = "SendEnabled";
    private static final String TAG_SELECTED_SEND_OPTION = "SelectedSendOption";
    private static final String TAG_LAUNCH_MODE = "LaunchMode";
    private static final String TAG_RIBOB_SLOT_1_POS = "RibobSlot1Pos";
    private static final String TAG_RIBOB_SLOT_2_POS = "RibobSlot2Pos";
    private static final String TAG_RIBOB_SLOT_3_POS = "RibobSlot3Pos";
    private static final String TAG_RIBOB_SLOT_1_SIGNAL = "RibobSlot1Signal";
    private static final String TAG_RIBOB_SLOT_2_SIGNAL = "RibobSlot2Signal";
    private static final String TAG_RIBOB_SLOT_3_SIGNAL = "RibobSlot3Signal";
    private static final String TAG_OWNER_UUID = "OwnerUuid";
    private static final ResourceLocation BINARY_CODE_TRANSMITTER_ADVANCEMENT_ID =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "binary_code_transmitter");
    private static final int BINARY_CODE_TRANSMITTER_REWARD_ANCIENT_DEBRIS = 6;

    private int sequenceLengthTicks = DEFAULT_SEQUENCE_LENGTH_TICKS;
    private int pulseLengthTicks = DEFAULT_PULSE_LENGTH_TICKS;
    private int pauseLengthTicks = DEFAULT_PAUSE_LENGTH_TICKS;
    private int repeatIntervalTicks = DEFAULT_REPEAT_INTERVAL_TICKS;
    private int launchMode = DEFAULT_LAUNCH_MODE;
    private final BlockPos[] ribobSlotPositions = new BlockPos[RIBOB_SLOT_COUNT];
    private final int[] ribobSlotSignals = new int[]{EMPTY_SIGNAL, EMPTY_SIGNAL, EMPTY_SIGNAL};
    private String pendingDispatchCode;
    private int coalesceTicksRemaining;
    private boolean bottomPulseInitialized = false;
    private boolean lastBottomPulseHigh = false;
    private UUID ownerUuid;

    public ControlTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROL_TABLE_BLOCK_ENTITY.get(), pos, state);
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

    public int getSequenceLengthTicks() {
        return sequenceLengthTicks;
    }

    public int getPulseLengthTicks() {
        return pulseLengthTicks;
    }

    public int getPauseLengthTicks() {
        return pauseLengthTicks;
    }

    public int getRepeatIntervalTicks() {
        return repeatIntervalTicks;
    }

    public int getLaunchMode() {
        return launchMode;
    }

    public int getRibobSignal(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= RIBOB_SLOT_COUNT) {
            return EMPTY_SIGNAL;
        }
        return ribobSlotSignals[slotIndex];
    }

    public int getRibobSlotIndex(BlockPos ribobPos) {
        return findSlotIndexByRibobPos(ribobPos);
    }

    public String getCurrentCode() {
        int mode = slotSignalOrZero(0);
        int speed = slotSignalOrZero(1);
        int angle = slotSignalOrZero(2);
        return toFourBitBinary(mode) + toFourBitBinary(speed) + toFourBitBinary(angle);
    }

    public void registerOrUpdateRibobSignal(BlockPos ribobPos, int signal) {
        if (level == null || level.isClientSide) {
            return;
        }

        boolean changed = validateSlotsInternal();
        int clampedSignal = Math.clamp(signal, 0, 15);
        int slotIndex = findSlotIndexByRibobPos(ribobPos);
        if (slotIndex < 0) {
            slotIndex = findFirstFreeSlotIndex();
        }

        if (slotIndex < 0) {
            if (changed) {
                setChanged();
                sendData();
            }
            return;
        }

        BlockPos immutablePos = ribobPos.immutable();
        if (!immutablePos.equals(ribobSlotPositions[slotIndex])) {
            ribobSlotPositions[slotIndex] = immutablePos;
            changed = true;
        }

        if (ribobSlotSignals[slotIndex] != clampedSignal) {
            ribobSlotSignals[slotIndex] = clampedSignal;
            changed = true;
        }

        if (changed) {
            setChanged();
            sendData();
            requestCodeDispatch(false);
        }
        tryTriggerSystemCompleteAdvancement();
    }

    public void unregisterRibob(BlockPos ribobPos) {
        if (level == null || level.isClientSide) {
            return;
        }

        boolean changed = validateSlotsInternal();
        int slotIndex = findSlotIndexByRibobPos(ribobPos);
        if (slotIndex >= 0) {
            clearSlot(slotIndex);
            changed = true;
        }

        if (changed) {
            setChanged();
            sendData();
            requestCodeDispatch(false);
        }
    }

    public void validateSlots() {
        if (level == null || level.isClientSide) {
            return;
        }

        if (validateSlotsInternal()) {
            setChanged();
            sendData();
            requestCodeDispatch(false);
        }
    }

    public void applyConfig(int sequenceLengthTicks, int pulseLengthTicks, int pauseLengthTicks, int repeatIntervalTicks,
                            int launchMode) {
        this.sequenceLengthTicks = clampTicks(sequenceLengthTicks);
        this.pulseLengthTicks = clampTicks(pulseLengthTicks);
        this.pauseLengthTicks = clampTicks(pauseLengthTicks);
        this.repeatIntervalTicks = clampTicks(repeatIntervalTicks);
        this.launchMode = sanitizeLaunchMode(launchMode);
        setChanged();
        sendData();
        requestCodeDispatch(true);
    }

    private int clampTicks(int value) {
        return Math.clamp(value, MIN_TICKS, MAX_TICKS);
    }

    private int sanitizeLaunchMode(int value) {
        return switch (value) {
            case LAUNCH_MODE_RS_PULSE, LAUNCH_MODE_ON_CHANGE, LAUNCH_MODE_OFF -> value;
            default -> DEFAULT_LAUNCH_MODE;
        };
    }

    private int sanitizeLegacySelectedSendOption(int value) {
        return switch (value) {
            case LEGACY_SEND_OPTION_1, LEGACY_SEND_OPTION_2, LEGACY_SEND_OPTION_3 -> value;
            default -> LEGACY_SEND_OPTION_1;
        };
    }

    private int convertLegacyToLaunchMode(boolean legacySendEnabled, int legacySelectedSendOption) {
        if (legacySendEnabled && legacySelectedSendOption == LEGACY_SEND_OPTION_1) {
            return LAUNCH_MODE_ON_CHANGE;
        }
        return LAUNCH_MODE_OFF;
    }

    private void requestCodeDispatch(boolean immediate) {
        if (level == null || level.isClientSide) {
            return;
        }

        if (launchMode == LAUNCH_MODE_OFF) {
            clearPendingDispatchState();
            abortDstbTransmissions();
            return;
        }

        if (launchMode == LAUNCH_MODE_RS_PULSE) {
            clearPendingDispatchState();
            abortDstbTransmissions();
            return;
        }

        if (hasNoValidDstbNeighbor()) {
            clearPendingDispatchState();
            return;
        }

        String code = getCurrentCode();
        if (immediate) {
            clearPendingDispatchState();
            dispatchCodeToDstbs(code);
            return;
        }

        pendingDispatchCode = code;
        coalesceTicksRemaining = SEND_COALESCE_TICKS;
    }

    private void flushPendingDispatchIfDue() {
        if (coalesceTicksRemaining > 0) {
            coalesceTicksRemaining--;
        }

        if (coalesceTicksRemaining > 0 || pendingDispatchCode == null) {
            return;
        }

        String code = pendingDispatchCode;
        clearPendingDispatchState();

        if (launchMode != LAUNCH_MODE_ON_CHANGE) {
            abortDstbTransmissions();
            return;
        }

        if (hasNoValidDstbNeighbor()) {
            return;
        }

        dispatchCodeToDstbs(code);
    }

    private void dispatchCodeToDstbs(String code) {
        if (code == null || level == null || level.isClientSide) {
            return;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!(level.getBlockEntity(neighborPos) instanceof DigitalSignalTxBlockEntity dstb)) {
                continue;
            }
            if (!worldPosition.equals(dstb.getExpectedControlTablePos())) {
                continue;
            }
            dstb.queueCodeFromControlTable(code);
        }
    }

    public void requestDisassembleAssembleToggle() {
        if (level == null || level.isClientSide) {
            return;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!(level.getBlockEntity(neighborPos) instanceof DigitalSignalTxBlockEntity dstb)) {
                continue;
            }
            if (!worldPosition.equals(dstb.getExpectedControlTablePos())) {
                continue;
            }
            dstb.queueControlToggleFromControlTable();
        }
    }

    private void abortDstbTransmissions() {
        if (level == null || level.isClientSide) {
            return;
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!(level.getBlockEntity(neighborPos) instanceof DigitalSignalTxBlockEntity dstb)) {
                continue;
            }
            if (!worldPosition.equals(dstb.getExpectedControlTablePos())) {
                continue;
            }
            dstb.abortTransmission();
        }
    }

    private boolean hasNoValidDstbNeighbor() {
        return getDstbOutputStatusForTooltip() != DstbOutputStatus.CONNECTED;
    }

    private void clearPendingDispatchState() {
        pendingDispatchCode = null;
        coalesceTicksRemaining = 0;
    }

    private int getBottomPulseSignal() {
        if (level == null) {
            return 0;
        }
        return Math.clamp(level.getSignal(worldPosition.relative(Direction.DOWN), Direction.DOWN), 0, 15);
    }

    private void tickBottomPulseDispatch() {
        if (launchMode != LAUNCH_MODE_RS_PULSE) {
            bottomPulseInitialized = false;
            lastBottomPulseHigh = false;
            return;
        }

        boolean currentBottomPulseHigh = getBottomPulseSignal() > 0;

        if (!bottomPulseInitialized) {
            bottomPulseInitialized = true;
            lastBottomPulseHigh = currentBottomPulseHigh;
            return;
        }

        boolean risingEdge = !lastBottomPulseHigh && currentBottomPulseHigh;
        lastBottomPulseHigh = currentBottomPulseHigh;

        if (!risingEdge) {
            return;
        }

        if (hasNoValidDstbNeighbor()) {
            return;
        }

        clearPendingDispatchState();
        dispatchCodeToDstbs(getCurrentCode());
    }

    private int slotSignalOrZero(int slotIndex) {
        int signal = getRibobSignal(slotIndex);
        return signal < 0 ? 0 : Math.clamp(signal, 0, 15);
    }

    private String toFourBitBinary(int signal) {
        String binary = Integer.toBinaryString(signal & 0xF);
        if (binary.length() == 4) {
            return binary;
        }
        return "0".repeat(4 - binary.length()) + binary;
    }

    private String formatGroupedBinaryCode(String raw12Bit) {
        if (raw12Bit == null || raw12Bit.length() != 12) {
            return "0000 0000 0000";
        }
        return raw12Bit.substring(0, 4) + " " + raw12Bit.substring(4, 8) + " " + raw12Bit.substring(8, 12);
    }

    private String formatSignalValueForDisplay(int signal) {
        if (signal < 0) {
            return "-";
        }
        return Integer.toString(Math.clamp(signal, 0, 15));
    }

    private String formatSignalLineForDisplay(int slotIndex) {
        int rawSignal = getRibobSignal(slotIndex);
        int clampedSignalOrZero = slotSignalOrZero(slotIndex);
        String valuePart = formatSignalValueForDisplay(rawSignal);
        String binaryPart = toFourBitBinary(clampedSignalOrZero);
        return valuePart + " / " + binaryPart;
    }

    private int getConnectedRibobCount() {
        int connected = 0;
        for (int i = 0; i < RIBOB_SLOT_COUNT; i++) {
            if (getRibobSignal(i) != EMPTY_SIGNAL) {
                connected++;
            }
        }
        return connected;
    }

    private Component getRibobStatusComponent(int connectedCount) {
        Component statusLabel;
        if (connectedCount == 0) {
            statusLabel = CreateLang.translateDirect("tooltip.twistermill.control_table.ribob_none");
        } else if (connectedCount == RIBOB_SLOT_COUNT) {
            statusLabel = CreateLang.translateDirect("tooltip.twistermill.control_table.ribob_complete");
        } else {
            statusLabel = CreateLang.translateDirect("tooltip.twistermill.control_table.ribob_partial");
        }
        return Component.empty()
                .append(statusLabel)
                .append(Component.literal(" (" + connectedCount + "/" + RIBOB_SLOT_COUNT + ")"));
    }

    private Component getOverallSystemStatusComponent(int connectedRibobs, DstbOutputStatus outputStatus) {
        boolean complete = connectedRibobs == RIBOB_SLOT_COUNT && outputStatus == DstbOutputStatus.CONNECTED;
        return complete
                ? CreateLang.translateDirect("tooltip.twistermill.control_table.system_complete")
                : CreateLang.translateDirect("tooltip.twistermill.control_table.system_incomplete");
    }

    private DstbOutputStatus getDstbOutputStatusForTooltip() {
        if (level == null) {
            return DstbOutputStatus.MISSING;
        }

        boolean foundAdjacentDstb = false;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!(level.getBlockEntity(neighborPos) instanceof DigitalSignalTxBlockEntity dstb)) {
                continue;
            }

            foundAdjacentDstb = true;
            if (worldPosition.equals(dstb.getExpectedControlTablePos())) {
                return DstbOutputStatus.CONNECTED;
            }
        }

        return foundAdjacentDstb ? DstbOutputStatus.MISALIGNED : DstbOutputStatus.MISSING;
    }

    private enum DstbOutputStatus {
        MISSING,
        MISALIGNED,
        CONNECTED
    }

    private boolean validateSlotsInternal() {
        if (level == null) {
            return false;
        }

        boolean changed = false;

        for (int slotIndex = 0; slotIndex < RIBOB_SLOT_COUNT; slotIndex++) {
            BlockPos ribobPos = ribobSlotPositions[slotIndex];
            if (ribobPos == null) {
                if (ribobSlotSignals[slotIndex] != EMPTY_SIGNAL) {
                    ribobSlotSignals[slotIndex] = EMPTY_SIGNAL;
                    changed = true;
                }
                continue;
            }

            boolean valid = level.isLoaded(ribobPos)
                    && level.getBlockEntity(ribobPos) instanceof RedstoneInBitOutBlockEntity ribobBlockEntity
                    && worldPosition.equals(ribobBlockEntity.getExpectedControlTablePos());

            if (!valid) {
                clearSlot(slotIndex);
                changed = true;
            }
        }

        return changed;
    }

    private int findSlotIndexByRibobPos(BlockPos ribobPos) {
        for (int slotIndex = 0; slotIndex < RIBOB_SLOT_COUNT; slotIndex++) {
            if (ribobPos.equals(ribobSlotPositions[slotIndex])) {
                return slotIndex;
            }
        }
        return -1;
    }

    private int findFirstFreeSlotIndex() {
        for (int slotIndex = 0; slotIndex < RIBOB_SLOT_COUNT; slotIndex++) {
            if (ribobSlotPositions[slotIndex] == null) {
                return slotIndex;
            }
        }
        return -1;
    }

    private void clearSlot(int slotIndex) {
        ribobSlotPositions[slotIndex] = null;
        ribobSlotSignals[slotIndex] = EMPTY_SIGNAL;
    }

    public void tryTriggerSystemCompleteAdvancement() {
        ServerPlayer ownerPlayer = resolveCompleteSystemOwner(null);
        if (ownerPlayer != null) {
            ModCriteriaTriggers.CTB_SYSTEM_COMPLETE.trigger(ownerPlayer);
        }
    }

    public void tryTriggerBinaryCodeTransmitterAdvancement(DigitalSignalTxBlockEntity transmitter) {
        ServerPlayer ownerPlayer = resolveCompleteSystemOwner(transmitter);
        if (ownerPlayer != null) {
            boolean alreadyCompleted = isBinaryCodeTransmitterAdvancementDone(ownerPlayer);
            ModCriteriaTriggers.CTB_SYSTEM_COMPLETE.trigger(ownerPlayer);
            ModCriteriaTriggers.BINARY_CODE_TRANSMITTER.trigger(ownerPlayer);
            if (!alreadyCompleted
                    && TwisterMillConfig.isNetheriteAdvancementDropEnabled()
                    && isBinaryCodeTransmitterAdvancementDone(ownerPlayer)) {
                giveBinaryCodeTransmitterAdvancementReward(ownerPlayer);
            }
        }
    }

    private static boolean isBinaryCodeTransmitterAdvancementDone(ServerPlayer player) {
        AdvancementHolder advancement = player.server.getAdvancements().get(BINARY_CODE_TRANSMITTER_ADVANCEMENT_ID);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        return progress.isDone();
    }

    private static void giveBinaryCodeTransmitterAdvancementReward(ServerPlayer player) {
        ItemStack reward = new ItemStack(Items.ANCIENT_DEBRIS, BINARY_CODE_TRANSMITTER_REWARD_ANCIENT_DEBRIS);
        if (player.addItem(reward)) {
            player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS,
                    0.2F,
                    ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
            );
            player.containerMenu.broadcastChanges();
            return;
        }

        ItemEntity droppedReward = player.drop(reward, false);
        if (droppedReward != null) {
            droppedReward.setNoPickUpDelay();
            droppedReward.setTarget(player.getUUID());
        }
    }

    private ServerPlayer resolveCompleteSystemOwner(DigitalSignalTxBlockEntity requiredTransmitter) {
        if (level == null || level.isClientSide || ownerUuid == null) {
            return null;
        }

        if (validateSlotsInternal()) {
            setChanged();
            sendData();
        }

        if (!hasThreeOwnedRibobSlots(ownerUuid)) {
            return null;
        }

        DigitalSignalTxBlockEntity connectedDstb = findSingleConnectedDstbForAdvancement();
        if (connectedDstb == null) {
            return null;
        }
        if (requiredTransmitter != null && connectedDstb != requiredTransmitter) {
            return null;
        }
        if (!ownerUuid.equals(connectedDstb.getOwnerUuid())) {
            return null;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    private boolean hasThreeOwnedRibobSlots(UUID expectedOwner) {
        if (level == null) {
            return false;
        }

        for (int slotIndex = 0; slotIndex < RIBOB_SLOT_COUNT; slotIndex++) {
            BlockPos ribobPos = ribobSlotPositions[slotIndex];
            if (ribobPos == null || ribobSlotSignals[slotIndex] == EMPTY_SIGNAL) {
                return false;
            }
            if (!(level.getBlockEntity(ribobPos) instanceof RedstoneInBitOutBlockEntity ribob)) {
                return false;
            }
            if (!worldPosition.equals(ribob.getExpectedControlTablePos())) {
                return false;
            }
            if (!expectedOwner.equals(ribob.getOwnerUuid())) {
                return false;
            }
        }

        return true;
    }

    private DigitalSignalTxBlockEntity findSingleConnectedDstbForAdvancement() {
        if (level == null) {
            return null;
        }

        DigitalSignalTxBlockEntity connectedDstb = null;
        int connectedCount = 0;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!(level.getBlockEntity(neighborPos) instanceof DigitalSignalTxBlockEntity dstb)) {
                continue;
            }
            if (!worldPosition.equals(dstb.getExpectedControlTablePos())) {
                continue;
            }
            connectedDstb = dstb;
            connectedCount++;
        }

        return connectedCount == 1 ? connectedDstb : null;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("SequenceLengthTicks", sequenceLengthTicks);
        tag.putInt("PulseLengthTicks", pulseLengthTicks);
        tag.putInt("PauseLengthTicks", pauseLengthTicks);
        tag.putInt("RepeatIntervalTicks", repeatIntervalTicks);
        tag.putInt(TAG_LAUNCH_MODE, launchMode);
        tag.putBoolean(TAG_SEND_ENABLED, launchMode == LAUNCH_MODE_ON_CHANGE);
        tag.putInt(TAG_SELECTED_SEND_OPTION, launchMode == LAUNCH_MODE_ON_CHANGE ? LEGACY_SEND_OPTION_1 : LEGACY_SEND_OPTION_3);

        if (ribobSlotPositions[0] != null) tag.putLong(TAG_RIBOB_SLOT_1_POS, ribobSlotPositions[0].asLong());
        if (ribobSlotPositions[1] != null) tag.putLong(TAG_RIBOB_SLOT_2_POS, ribobSlotPositions[1].asLong());
        if (ribobSlotPositions[2] != null) tag.putLong(TAG_RIBOB_SLOT_3_POS, ribobSlotPositions[2].asLong());

        tag.putInt(TAG_RIBOB_SLOT_1_SIGNAL, ribobSlotSignals[0]);
        tag.putInt(TAG_RIBOB_SLOT_2_SIGNAL, ribobSlotSignals[1]);
        tag.putInt(TAG_RIBOB_SLOT_3_SIGNAL, ribobSlotSignals[2]);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUuid);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        sequenceLengthTicks = clampTicks(tag.getInt("SequenceLengthTicks"));
        pulseLengthTicks = clampTicks(tag.getInt("PulseLengthTicks"));
        pauseLengthTicks = clampTicks(tag.getInt("PauseLengthTicks"));
        repeatIntervalTicks = clampTicks(tag.getInt("RepeatIntervalTicks"));
        if (tag.contains(TAG_LAUNCH_MODE)) {
            launchMode = sanitizeLaunchMode(tag.getInt(TAG_LAUNCH_MODE));
        } else {
            boolean legacySendEnabled = tag.contains(TAG_SEND_ENABLED) && tag.getBoolean(TAG_SEND_ENABLED);
            int legacySelected = sanitizeLegacySelectedSendOption(tag.getInt(TAG_SELECTED_SEND_OPTION));
            launchMode = convertLegacyToLaunchMode(legacySendEnabled, legacySelected);
        }

        ribobSlotPositions[0] = tag.contains(TAG_RIBOB_SLOT_1_POS) ? BlockPos.of(tag.getLong(TAG_RIBOB_SLOT_1_POS)).immutable() : null;
        ribobSlotPositions[1] = tag.contains(TAG_RIBOB_SLOT_2_POS) ? BlockPos.of(tag.getLong(TAG_RIBOB_SLOT_2_POS)).immutable() : null;
        ribobSlotPositions[2] = tag.contains(TAG_RIBOB_SLOT_3_POS) ? BlockPos.of(tag.getLong(TAG_RIBOB_SLOT_3_POS)).immutable() : null;

        ribobSlotSignals[0] = tag.contains(TAG_RIBOB_SLOT_1_SIGNAL) ? Math.clamp(tag.getInt(TAG_RIBOB_SLOT_1_SIGNAL), EMPTY_SIGNAL, 15) : EMPTY_SIGNAL;
        ribobSlotSignals[1] = tag.contains(TAG_RIBOB_SLOT_2_SIGNAL) ? Math.clamp(tag.getInt(TAG_RIBOB_SLOT_2_SIGNAL), EMPTY_SIGNAL, 15) : EMPTY_SIGNAL;
        ribobSlotSignals[2] = tag.contains(TAG_RIBOB_SLOT_3_SIGNAL) ? Math.clamp(tag.getInt(TAG_RIBOB_SLOT_3_SIGNAL), EMPTY_SIGNAL, 15) : EMPTY_SIGNAL;

        if (ribobSlotPositions[0] == null) ribobSlotSignals[0] = EMPTY_SIGNAL;
        if (ribobSlotPositions[1] == null) ribobSlotSignals[1] = EMPTY_SIGNAL;
        if (ribobSlotPositions[2] == null) ribobSlotSignals[2] = EMPTY_SIGNAL;
        ownerUuid = tag.hasUUID(TAG_OWNER_UUID) ? tag.getUUID(TAG_OWNER_UUID) : null;
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            return;
        }

        flushPendingDispatchIfDue();
        tickBottomPulseDispatch();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        validateSlots();
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.twistermill.control_table_block");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory, @NotNull Player player) {
        return new ControlTableMenu(containerId, playerInventory, this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean details = AllKeys.ctrlDown();
        int connectedRibobs = getConnectedRibobCount();
        DstbOutputStatus outputStatus = getDstbOutputStatusForTooltip();
        boolean systemComplete = connectedRibobs == RIBOB_SLOT_COUNT && outputStatus == DstbOutputStatus.CONNECTED;

        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.control_table.system_status")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.text("")
                .add(getOverallSystemStatusComponent(connectedRibobs, outputStatus))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        if (!details) {
            CreateLang.text("")
                    .add(Component.literal("details: ").withStyle(ChatFormatting.DARK_GRAY))
                    .add(CreateLang.translateDirect("tooltip.twistermill.key_ctrl").withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip);
            return true;
        }

        if (!systemComplete) {
            CreateLang.translate("tooltip.twistermill.control_table.ribob_inputs")
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);

            CreateLang.text("")
                    .add(getRibobStatusComponent(connectedRibobs))
                    .style(ChatFormatting.AQUA)
                    .forGoggles(tooltip, 1);
        }

        CreateLang.translate("tooltip.twistermill.control_table.output_status")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        Component outputBlockName = ModBlocks.DIGITAL_SIGNAL_TX_BLOCK.get().getName();
        Component outputSuffix = switch (outputStatus) {
            case CONNECTED -> CreateLang.translateDirect("tooltip.twistermill.control_table.output_connected");
            case MISALIGNED -> CreateLang.translateDirect("tooltip.twistermill.control_table.output_misaligned");
            case MISSING -> CreateLang.translateDirect("tooltip.twistermill.control_table.output_missing");
        };

        ChatFormatting outputColor = switch (outputStatus) {
            case CONNECTED -> ChatFormatting.AQUA;
            case MISALIGNED -> ChatFormatting.YELLOW;
            case MISSING -> ChatFormatting.RED;
        };

        CreateLang.text("")
                .add(outputBlockName)
                .space()
                .add(outputSuffix)
                .style(outputColor)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.control_table.redstone_inputs")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.control_table.mode_signal")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        CreateLang.text(formatSignalLineForDisplay(0))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        CreateLang.translate("tooltip.twistermill.control_table.speed_signal")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        CreateLang.text(formatSignalLineForDisplay(1))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        CreateLang.translate("tooltip.twistermill.control_table.angle_signal")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        CreateLang.text(formatSignalLineForDisplay(2))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        CreateLang.translate("tooltip.twistermill.control_table.binary_code")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);

        CreateLang.text(formatGroupedBinaryCode(getCurrentCode()))
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 2);

        return true;
    }
}
