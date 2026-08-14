package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.util.SableLevelWrapper;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.function.IntConsumer;

public final class SecondaryServoRedstoneLinkBehaviour extends LinkBehaviour {

    public static final BehaviourType<SecondaryServoRedstoneLinkBehaviour> TYPE = new BehaviourType<>();

    private static final String TAG_SECONDARY_LINK = "SecondaryInternalRedstoneLink";
    private static final String TAG_FREQUENCY_FIRST = "FrequencyFirst";
    private static final String TAG_FREQUENCY_LAST = "FrequencyLast";
    private static final String TAG_LAST_KNOWN_POSITION = "LastKnownPosition";

    private Frequency secondaryFrequencyFirst = Frequency.EMPTY;
    private Frequency secondaryFrequencyLast = Frequency.EMPTY;
    private final ValueBoxTransform secondaryFirstSlot;
    private final ValueBoxTransform secondarySecondSlot;
    private final IntConsumer signalCallback;

    public SecondaryServoRedstoneLinkBehaviour(
            SmartBlockEntity blockEntity,
            Pair<ValueBoxTransform, ValueBoxTransform> slots,
            IntConsumer signalCallback
    ) {
        super(blockEntity, slots);
        secondaryFirstSlot = slots.getLeft();
        secondarySecondSlot = slots.getRight();
        this.signalCallback = signalCallback;
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean isListening() {
        return true;
    }

    @Override
    public int getTransmittedStrength() {
        return 0;
    }

    @Override
    public void setReceivedStrength(int networkPower) {
        if (!newPosition) {
            return;
        }
        signalCallback.accept(networkPower);
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return Couple.create(secondaryFrequencyFirst, secondaryFrequencyLast);
    }

    @Override
    public void setFrequency(boolean first, ItemStack stack) {
        ItemStack normalized = stack.copy();
        normalized.setCount(1);
        ItemStack current = first ? secondaryFrequencyFirst.getStack() : secondaryFrequencyLast.getStack();
        boolean changed = !ItemStack.isSameItemSameComponents(normalized, current);
        if (!changed) {
            return;
        }

        Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(getWorld(), this);
        if (first) {
            secondaryFrequencyFirst = Frequency.of(normalized);
        } else {
            secondaryFrequencyLast = Frequency.of(normalized);
        }

        Level level = getWorld();
        if (!level.isClientSide) {
            blockEntity.setChanged();
            if (SableLevelWrapper.isSubLevel(level)) {
                BlockState state = blockEntity.getBlockState();
                level.sendBlockUpdated(getPos(), state, state, Block.UPDATE_CLIENTS);
                level.blockUpdated(getPos(), state.getBlock());
            }
        }
        blockEntity.sendData();
        Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(getWorld(), this);
    }

    @Override
    public boolean testHit(Boolean first, Vec3 hit) {
        BlockState state = blockEntity.getBlockState();
        Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
        return getSlotTransform(first).testHit(getWorld(), getPos(), state, localHit);
    }

    public ValueBoxTransform getSlotTransform(boolean first) {
        return first ? secondaryFirstSlot : secondarySecondSlot;
    }

    public ItemStack getFrequencyStack(boolean first) {
        return (first ? secondaryFrequencyFirst : secondaryFrequencyLast).getStack();
    }

    public void copyItemsFrom(SecondaryServoRedstoneLinkBehaviour behaviour) {
        if (behaviour == null) {
            return;
        }
        secondaryFrequencyFirst = behaviour.secondaryFrequencyFirst;
        secondaryFrequencyLast = behaviour.secondaryFrequencyLast;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        CompoundTag secondary = new CompoundTag();
        secondary.put(TAG_FREQUENCY_FIRST, secondaryFrequencyFirst.getStack().saveOptional(registries));
        secondary.put(TAG_FREQUENCY_LAST, secondaryFrequencyLast.getStack().saveOptional(registries));
        secondary.putLong(TAG_LAST_KNOWN_POSITION, blockEntity.getBlockPos().asLong());
        nbt.put(TAG_SECONDARY_LINK, secondary);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        CompoundTag secondary = nbt.getCompound(TAG_SECONDARY_LINK);
        long currentPosition = blockEntity.getBlockPos().asLong();
        newPosition = currentPosition != secondary.getLong(TAG_LAST_KNOWN_POSITION);
        secondaryFrequencyFirst = Frequency.of(
                ItemStack.parseOptional(registries, secondary.getCompound(TAG_FREQUENCY_FIRST))
        );
        secondaryFrequencyLast = Frequency.of(
                ItemStack.parseOptional(registries, secondary.getCompound(TAG_FREQUENCY_LAST))
        );
    }

    @Override
    public String getClipboardKey() {
        return "SecondaryFrequencies";
    }

    @Override
    public boolean writeToClipboard(
            @NotNull HolderLookup.Provider registries,
            CompoundTag tag,
            Direction side
    ) {
        tag.put("First", secondaryFrequencyFirst.getStack().saveOptional(registries));
        tag.put("Last", secondaryFrequencyLast.getStack().saveOptional(registries));
        return true;
    }

    @Override
    public boolean readFromClipboard(
            @NotNull HolderLookup.Provider registries,
            CompoundTag tag,
            Player player,
            Direction side,
            boolean simulate
    ) {
        if (!tag.contains("First") || !tag.contains("Last")) {
            return false;
        }
        if (simulate) {
            return true;
        }
        setFrequency(true, ItemStack.parseOptional(registries, tag.getCompound("First")));
        setFrequency(false, ItemStack.parseOptional(registries, tag.getCompound("Last")));
        return true;
    }
}
