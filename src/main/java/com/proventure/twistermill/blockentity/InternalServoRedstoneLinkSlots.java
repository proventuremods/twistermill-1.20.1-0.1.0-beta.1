package com.proventure.twistermill.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;

public final class InternalServoRedstoneLinkSlots {

    private InternalServoRedstoneLinkSlots() {
    }

    public static Pair<ValueBoxTransform, ValueBoxTransform> makeSlots(boolean inverted) {
        return makeSlots(inverted, false);
    }

    public static Pair<ValueBoxTransform, ValueBoxTransform> makeSlots(boolean inverted, boolean secondary) {
        return ValueBoxTransform.Dual.makeSlots(first -> new FrequencySlot(first, inverted, secondary));
    }

    public static ValueBoxTransform createSlot(boolean first, boolean inverted) {
        return createSlot(first, inverted, false);
    }

    public static ValueBoxTransform createSlot(boolean first, boolean inverted, boolean secondary) {
        return new FrequencySlot(first, inverted, secondary);
    }

    private static class FrequencySlot extends ValueBoxTransform.Dual {
        private static final double SIDE_OFFSET = 7.5D / 16.0D;
        private static final double TOP_SLOT_Y = 11.0D / 16.0D;
        private static final double BOTTOM_SLOT_Y = 5.0D / 16.0D;

        private final boolean inverted;
        private final boolean secondary;

        private FrequencySlot(boolean first, boolean inverted, boolean secondary) {
            super(first);
            this.inverted = inverted;
            this.secondary = secondary;
        }

        @Override
        public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
            Direction side = getSide(level, pos, state);
            if (side == null)
                return null;

            Vec3 sideOffset = Vec3.atLowerCornerOf(side.getNormal()).scale(SIDE_OFFSET);
            double y = isFirst() ? TOP_SLOT_Y : BOTTOM_SLOT_Y;
            return new Vec3(0.5D, y, 0.5D).add(sideOffset);
        }

        @Override
        public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
            Direction side = getSide(level, pos, state);
            if (side == null)
                return;

            TransformStack.of(ms)
                    .rotateYDegrees(AngleHelper.horizontalAngle(side) + 180);
        }

        @Override
        public float getScale() {
            return .4975f;
        }

        private Direction getSide(LevelAccessor level, BlockPos pos, BlockState state) {
            if (level == null)
                return null;

            if (!(level.getBlockEntity(pos) instanceof InternalServoRedstoneLinkOwner owner)
                    || !(secondary
                    ? owner.isSecondaryInternalRedstoneLinkEligible()
                    : owner.isInternalRedstoneLinkMode()))
                return null;

            Direction ownerSide = secondary
                    ? owner.getSecondaryInternalRedstoneLinkSide()
                    : owner.getInternalRedstoneLinkSide();
            return ownerSide;
        }
    }
}
