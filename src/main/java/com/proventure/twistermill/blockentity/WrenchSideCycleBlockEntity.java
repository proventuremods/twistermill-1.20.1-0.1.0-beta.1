package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.block.custom.MetalTraverseBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class WrenchSideCycleBlockEntity extends SmartBlockEntity {

    public static final byte STAGE_AUTO_A = 0;
    public static final byte STAGE_BRACKET_ON = 1;
    @SuppressWarnings("unused")
    public static final byte STAGE_AUTO_B = 2;
    @SuppressWarnings("unused")
    public static final byte STAGE_EXTRA_1 = 3;
    @SuppressWarnings("unused")
    public static final byte STAGE_EXTRA_1_AND_2 = 4;
    public static final byte STAGE_EXTRA_2 = 5;
    public static final byte STAGE_FORCE_HIDDEN = 6;
    public static final byte STAGE_BRACKET_LADDER = 7;

    private static final String TAG_SIDE_CYCLE = "SideCycle";
    private static final String TAG_TRAVERSE_ADDED_TO_GIRDER = "TraverseAddedToGirder";
    private static final String TAG_SERVO_MODE_7_SLOT_OUTWARD = "ServoMode7SlotOutward";
    private static final int SIDE_COUNT = 6;

    public static final ModelProperty<SideCycleSnapshot> SIDE_CYCLE_PROPERTY = new ModelProperty<>();

    private final byte[] sideStages = new byte[SIDE_COUNT];
    private boolean traverseAddedToGirder;
    @Nullable
    private Direction servoMode7SlotOutward;

    public WrenchSideCycleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.METAL_SIDE_CYCLE_BLOCK_ENTITY.get(), pos, state);
        Arrays.fill(sideStages, STAGE_AUTO_A);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    @SuppressWarnings("unused")
    public byte getStage(Direction side) {
        return sideStages[indexOf(side)];
    }

    @SuppressWarnings("unused")
    public byte advance(Direction side, boolean extrasAllowed) {
        return advance(side, extrasAllowed, false);
    }

    public byte advance(Direction side, boolean extrasAllowed, boolean autoBracketVisible) {
        int index = indexOf(side);
        byte current = sideStages[index];
        byte next;

        if (extrasAllowed && current <= STAGE_EXTRA_2) {
            next = (byte) ((current + 1) % (STAGE_EXTRA_2 + 1));
        } else if (current == STAGE_AUTO_A) {
            next = autoBracketVisible ? STAGE_FORCE_HIDDEN : STAGE_BRACKET_ON;
        } else if (current == STAGE_BRACKET_ON || current == STAGE_FORCE_HIDDEN) {
            next = STAGE_AUTO_A;
        } else {
            next = STAGE_AUTO_A;
        }

        sideStages[index] = next;
        return next;
    }

    public byte advanceHorizontalBracketCycle(Direction side, boolean autoBracketVisible) {
        int index = indexOf(side);
        byte current = sideStages[index];
        byte next;

        if (current == STAGE_BRACKET_LADDER) {
            next = STAGE_FORCE_HIDDEN;
        } else if (current == STAGE_FORCE_HIDDEN) {
            next = STAGE_BRACKET_ON;
        } else if (isBracketStage(current) || autoBracketVisible) {
            next = STAGE_BRACKET_LADDER;
        } else {
            next = STAGE_BRACKET_ON;
        }

        sideStages[index] = next;
        return next;
    }

    public void markChangedAndSync() {
        setChanged();
        sendData();
        Level level = getLevel();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean isTraverseAddedToGirder() {
        return traverseAddedToGirder;
    }

    public void setTraverseAddedToGirder(boolean traverseAddedToGirder) {
        this.traverseAddedToGirder = traverseAddedToGirder;
    }

    @Nullable
    Direction getServoMode7SlotOutward() {
        return servoMode7SlotOutward;
    }

    boolean setServoMode7SlotOutward(Direction outward) {
        if (outward != Direction.SOUTH && outward != Direction.UP) {
            return false;
        }
        if (servoMode7SlotOutward == outward) {
            return false;
        }

        servoMode7SlotOutward = outward;
        setChanged();
        return true;
    }

    public static boolean isBracketStage(byte stage) {
        return stage == STAGE_BRACKET_ON || stage == STAGE_BRACKET_LADDER;
    }

    public static boolean isLadderStage(byte stage) {
        return stage == STAGE_BRACKET_LADDER;
    }

    public static boolean isHiddenStage(byte stage) {
        return stage == STAGE_FORCE_HIDDEN;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (hasNonDefaultStages()) {
            tag.putByteArray(TAG_SIDE_CYCLE, sideStages.clone());
        }
        if (traverseAddedToGirder) {
            tag.putBoolean(TAG_TRAVERSE_ADDED_TO_GIRDER, true);
        }
        if (servoMode7SlotOutward != null) {
            tag.putString(TAG_SERVO_MODE_7_SLOT_OUTWARD, servoMode7SlotOutward.getName());
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        Arrays.fill(sideStages, STAGE_AUTO_A);
        traverseAddedToGirder = tag.getBoolean(TAG_TRAVERSE_ADDED_TO_GIRDER);
        Direction storedSlotOutward = tag.contains(TAG_SERVO_MODE_7_SLOT_OUTWARD)
                ? Direction.byName(tag.getString(TAG_SERVO_MODE_7_SLOT_OUTWARD))
                : null;
        servoMode7SlotOutward = storedSlotOutward == Direction.SOUTH || storedSlotOutward == Direction.UP
                ? storedSlotOutward
                : null;

        if (tag.contains(TAG_SIDE_CYCLE)) {
            byte[] stored = tag.getByteArray(TAG_SIDE_CYCLE);
            int len = Math.min(stored.length, SIDE_COUNT);
            for (int i = 0; i < len; i++) {
                sideStages[i] = clampStage(stored[i]);
            }
        } else {
            migrateFromManualBracketState(getBlockState());
        }

        if (clientPacket) {
            requestModelDataUpdate();
            Level level = getLevel();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public @NotNull ModelData getModelData() {
        return ModelData.of(SIDE_CYCLE_PROPERTY, snapshot());
    }

    public SideCycleSnapshot snapshot() {
        return new SideCycleSnapshot(sideStages);
    }

    private void migrateFromManualBracketState(BlockState state) {
        for (Direction direction : Direction.values()) {
            if (hasManualBracketFlag(state, direction)) {
                sideStages[indexOf(direction)] = STAGE_BRACKET_ON;
            }
        }
    }

    private static boolean hasManualBracketFlag(BlockState state, Direction direction) {
        if (state.getBlock() instanceof MetalTraverseBlock) {
            net.minecraft.world.level.block.state.properties.BooleanProperty property =
                    getTraverseManualProperty(direction);
            return state.hasProperty(property) && state.getValue(property);
        }
        return false;
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty getTraverseManualProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> MetalTraverseBlock.MANUAL_BRACKET_NORTH;
            case SOUTH -> MetalTraverseBlock.MANUAL_BRACKET_SOUTH;
            case EAST -> MetalTraverseBlock.MANUAL_BRACKET_EAST;
            case WEST -> MetalTraverseBlock.MANUAL_BRACKET_WEST;
            case UP -> MetalTraverseBlock.MANUAL_BRACKET_UP;
            case DOWN -> MetalTraverseBlock.MANUAL_BRACKET_DOWN;
        };
    }

    private boolean hasNonDefaultStages() {
        for (byte stage : sideStages) {
            if (stage != STAGE_AUTO_A) {
                return true;
            }
        }
        return false;
    }

    private static byte clampStage(byte raw) {
        if (raw < STAGE_AUTO_A) {
            return STAGE_AUTO_A;
        }
        if (raw > STAGE_BRACKET_LADDER) {
            return STAGE_AUTO_A;
        }
        return raw;
    }

    private static int indexOf(Direction side) {
        return switch (side) {
            case NORTH -> 0;
            case SOUTH -> 1;
            case EAST -> 2;
            case WEST -> 3;
            case UP -> 4;
            case DOWN -> 5;
        };
    }

    public static final class SideCycleSnapshot {
        private final byte[] snapshot;

        private SideCycleSnapshot(byte[] source) {
            snapshot = source.clone();
        }

        public byte getStage(Direction side) {
            return snapshot[indexOf(side)];
        }
    }
}
