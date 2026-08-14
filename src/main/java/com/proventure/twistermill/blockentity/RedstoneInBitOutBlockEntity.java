package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.block.custom.RedstoneInBitOutBlock;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public class RedstoneInBitOutBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final String TAG_CACHED_INPUT_SIGNAL = "CachedInputSignal";
    private static final String TAG_OWNER_UUID = "OwnerUuid";

    private int cachedInputSignal;
    private BlockPos linkedControlTablePos;
    private UUID ownerUuid;

    public RedstoneInBitOutBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_IN_BIT_OUT_BLOCK_ENTITY.get(), pos, state);
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
        return getBlockState().getValue(RedstoneInBitOutBlock.HORIZONTAL_FACING);
    }

    public BlockPos getExpectedControlTablePos() {
        return worldPosition.relative(getFacing().getOpposite());
    }

    private int computeInputSignalFromWorld() {
        if (level == null) {
            return 0;
        }
        Direction facing = getFacing();
        return Mth.clamp(level.getSignal(worldPosition.relative(facing), facing), 0, 15);
    }

    public void refreshControlTableLinkAndSignal() {
        if (level == null || level.isClientSide) {
            return;
        }

        int nextValue = computeInputSignalFromWorld();
        boolean signalChanged = nextValue != cachedInputSignal;
        cachedInputSignal = nextValue;
        updatePoweredState(nextValue > 0);

        BlockPos expectedControlTablePos = getExpectedControlTablePos();
        ControlTableBlockEntity expectedControlTable = null;
        if (level.getBlockState(expectedControlTablePos).is(ModBlocks.CONTROL_TABLE_BLOCK.get())
                && level.getBlockEntity(expectedControlTablePos) instanceof ControlTableBlockEntity controlTableBlockEntity) {
            expectedControlTable = controlTableBlockEntity;
        }

        boolean linkChanged = (linkedControlTablePos == null) != (expectedControlTable == null);
        if (!linkChanged && linkedControlTablePos != null) {
            linkChanged = !linkedControlTablePos.equals(expectedControlTablePos);
        }

        if (linkChanged && linkedControlTablePos != null
                && level.getBlockEntity(linkedControlTablePos) instanceof ControlTableBlockEntity previousControlTable) {
            previousControlTable.unregisterRibob(worldPosition);
        }

        if (expectedControlTable != null) {
            expectedControlTable.registerOrUpdateRibobSignal(worldPosition, cachedInputSignal);
            linkedControlTablePos = expectedControlTablePos.immutable();
        } else {
            linkedControlTablePos = null;
        }

        if (signalChanged) {
            setChanged();
            sendData();
        }
    }

    private void updatePoweredState(boolean powered) {
        if (level == null || level.isClientSide) {
            return;
        }

        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(RedstoneInBitOutBlock.POWERED)
                && state.getValue(RedstoneInBitOutBlock.POWERED) != powered) {
            level.setBlock(worldPosition, state.setValue(RedstoneInBitOutBlock.POWERED, powered), 3);
        }
    }

    public int getInputSignal() {
        return cachedInputSignal;
    }

    public void unregisterFromLinkedControlTable() {
        if (level == null || level.isClientSide || linkedControlTablePos == null) {
            return;
        }

        if (level.getBlockEntity(linkedControlTablePos) instanceof ControlTableBlockEntity controlTableBlockEntity) {
            controlTableBlockEntity.unregisterRibob(worldPosition);
        }

        linkedControlTablePos = null;
    }

    public boolean hasControlTableOpposite() {
        if (level == null) {
            return false;
        }
        Direction opposite = getFacing().getOpposite();
        return level.getBlockState(worldPosition.relative(opposite)).is(ModBlocks.CONTROL_TABLE_BLOCK.get());
    }

    @SuppressWarnings("unused")
    public boolean isRunningAllowed() {
        return hasControlTableOpposite();
    }

    private Component getAssignedControlTableRoleLabel() {
        if (level == null) {
            return null;
        }

        BlockPos expectedControlTablePos = getExpectedControlTablePos();
        if (!level.getBlockState(expectedControlTablePos).is(ModBlocks.CONTROL_TABLE_BLOCK.get())
                || !(level.getBlockEntity(expectedControlTablePos) instanceof ControlTableBlockEntity controlTable)) {
            return null;
        }

        return switch (controlTable.getRibobSlotIndex(worldPosition)) {
            case 0 -> CreateLang.translateDirect("tooltip.twistermill.control_table.mode_signal");
            case 1 -> CreateLang.translateDirect("tooltip.twistermill.control_table.speed_signal");
            case 2 -> CreateLang.translateDirect("tooltip.twistermill.control_table.angle_signal");
            default -> null;
        };
    }

    private Component getInputSignalTooltipValue() {
        Component roleLabel = getAssignedControlTableRoleLabel();
        if (roleLabel == null) {
            return Component.literal(Integer.toString(getInputSignal()));
        }

        return Component.empty()
                .append(Component.literal(Integer.toString(getInputSignal())))
                .append(Component.literal(" - "))
                .append(roleLabel);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        refreshControlTableLinkAndSignal();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt(TAG_CACHED_INPUT_SIGNAL, cachedInputSignal);
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUuid);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        cachedInputSignal = Mth.clamp(tag.getInt(TAG_CACHED_INPUT_SIGNAL), 0, 15);
        ownerUuid = tag.hasUUID(TAG_OWNER_UUID) ? tag.getUUID(TAG_OWNER_UUID) : null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);

        CreateLang.translate("tooltip.twistermill.ribob.redstone_input")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        CreateLang.text("")
                .add(getInputSignalTooltipValue())
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        CreateLang.translate("tooltip.twistermill.ribob.control_table_present")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);
        CreateLang.text(Boolean.toString(hasControlTableOpposite()))
                .style(hasControlTableOpposite() ? ChatFormatting.GREEN : ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        return true;
    }
}
