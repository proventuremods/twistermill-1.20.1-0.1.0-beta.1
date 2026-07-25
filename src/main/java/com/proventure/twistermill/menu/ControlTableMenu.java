package com.proventure.twistermill.menu;

import com.proventure.twistermill.blockentity.ControlTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

public class ControlTableMenu extends AbstractContainerMenu {

    private final ControlTableBlockEntity blockEntity;
    private final BlockPos blockPos;

    private final int sequenceLengthTicks;
    private final int pulseLengthTicks;
    private final int pauseLengthTicks;
    private final int repeatIntervalTicks;
    private final int launchMode;

    public ControlTableMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(ModMenuTypes.CONTROL_TABLE_MENU.get(), containerId, playerInventory, getClientBlockEntity(extraData.readBlockPos()));
    }

    public ControlTableMenu(int containerId, Inventory playerInventory, ControlTableBlockEntity blockEntity) {
        this(ModMenuTypes.CONTROL_TABLE_MENU.get(), containerId, playerInventory, blockEntity);
    }

    private ControlTableMenu(MenuType<?> menuType, int containerId, Inventory playerInventory, ControlTableBlockEntity blockEntity) {
        super(menuType, containerId);
        this.blockEntity = blockEntity;
        this.blockPos = blockEntity != null ? blockEntity.getBlockPos() : BlockPos.ZERO;

        this.sequenceLengthTicks = blockEntity != null ? blockEntity.getSequenceLengthTicks() : ControlTableBlockEntity.DEFAULT_SEQUENCE_LENGTH_TICKS;
        this.pulseLengthTicks = blockEntity != null ? blockEntity.getPulseLengthTicks() : ControlTableBlockEntity.DEFAULT_PULSE_LENGTH_TICKS;
        this.pauseLengthTicks = blockEntity != null ? blockEntity.getPauseLengthTicks() : ControlTableBlockEntity.DEFAULT_PAUSE_LENGTH_TICKS;
        this.repeatIntervalTicks = blockEntity != null ? blockEntity.getRepeatIntervalTicks() : ControlTableBlockEntity.DEFAULT_REPEAT_INTERVAL_TICKS;
        this.launchMode = blockEntity != null ? blockEntity.getLaunchMode() : ControlTableBlockEntity.DEFAULT_LAUNCH_MODE;
    }

    private static ControlTableBlockEntity getClientBlockEntity(BlockPos pos) {
        if (Minecraft.getInstance().level == null) {
            return null;
        }
        if (Minecraft.getInstance().level.getBlockEntity(pos) instanceof ControlTableBlockEntity controlTableBlockEntity) {
            return controlTableBlockEntity;
        }
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null || blockEntity.isRemoved()) {
            return false;
        }
        if (player.level().getBlockEntity(blockPos) != blockEntity) {
            return false;
        }
        return player.canInteractWithBlock(blockPos, 8.0);
    }

    public BlockPos getBlockPos() {
        return blockPos;
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
        ControlTableBlockEntity liveBlockEntity = getLiveBlockEntity();
        if (liveBlockEntity == null) {
            return -1;
        }
        return liveBlockEntity.getRibobSignal(slotIndex);
    }

    public String getCurrentCode() {
        ControlTableBlockEntity liveBlockEntity = getLiveBlockEntity();
        if (liveBlockEntity == null) {
            return "000000000000";
        }
        return liveBlockEntity.getCurrentCode();
    }

    private ControlTableBlockEntity getLiveBlockEntity() {
        if (blockEntity != null && !blockEntity.isRemoved()) {
            return blockEntity;
        }
        return getClientBlockEntity(blockPos);
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
