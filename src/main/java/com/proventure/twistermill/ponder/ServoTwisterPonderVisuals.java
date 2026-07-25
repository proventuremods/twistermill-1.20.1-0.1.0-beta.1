package com.proventure.twistermill.ponder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.proventure.twistermill.client.TwisterMillPartialModels;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.PonderElementBase;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.createmod.ponder.foundation.instruction.TickingInstruction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class ServoTwisterPonderVisuals {
    private static final double ANTENNA_DOWN_OFFSET = 14.0D / 16.0D;
    private static final int FULL_BRIGHT = 0xF000F0;

    private ServoTwisterPonderVisuals() {
    }

    static void showServoAntenna(SceneBuilder scene, BlockPos pos) {
        scene.addInstruction(PonderInstruction.simple(ponderScene ->
                ponderScene.addElement(new ServoAntennaElement(pos, false))));
    }

    static void showInvServoAntenna(SceneBuilder scene, BlockPos pos) {
        scene.addInstruction(PonderInstruction.simple(ponderScene ->
                ponderScene.addElement(new ServoAntennaElement(pos, true))));
    }

    static void rotateServoTop(SceneBuilder scene, BlockPos pos, float totalDelta, int ticks) {
        scene.addInstruction(new ServoTopAngleInstruction(pos, totalDelta, ticks));
    }

    private static final class ServoTopAngleInstruction extends TickingInstruction {
        private final BlockPos pos;
        private final float totalDelta;
        private float startAngle;
        private float previousAngle;

        private ServoTopAngleInstruction(BlockPos pos, float totalDelta, int ticks) {
            super(false, ticks);
            this.pos = pos;
            this.totalDelta = totalDelta;
        }

        @Override
        protected void firstTick(PonderScene scene) {
            startAngle = readCurrentAngle(scene);
            previousAngle = startAngle;
        }

        @Override
        public void tick(PonderScene scene) {
            super.tick(scene);

            float progress = totalTicks <= 0 ? 1.0F : (totalTicks - remainingTicks) / (float) totalTicks;
            float nextAngle = startAngle - totalDelta * progress;
            writeAngle(scene, previousAngle, nextAngle);
            previousAngle = nextAngle;
        }

        private float readCurrentAngle(PonderScene scene) {
            BlockEntity blockEntity = scene.getWorld().getBlockEntity(pos);
            if (blockEntity instanceof ServoTwisterBlockEntity servo) {
                return servo.getInterpolatedAngle(1.0F);
            }
            if (blockEntity instanceof InvServoTwisterBlockEntity invServo) {
                return invServo.getInterpolatedAngle(1.0F);
            }
            return 0.0F;
        }

        private void writeAngle(PonderScene scene, float prevAngle, float angle) {
            BlockEntity blockEntity = scene.getWorld().getBlockEntity(pos);
            if (!(blockEntity instanceof ServoTwisterBlockEntity)
                    && !(blockEntity instanceof InvServoTwisterBlockEntity)) {
                return;
            }

            CompoundTag tag = blockEntity.saveWithFullMetadata(scene.getWorld().registryAccess());
            tag.putFloat("PrevAngle", prevAngle);
            tag.putFloat("Angle", angle);
            blockEntity.loadWithComponents(tag, scene.getWorld().registryAccess());
            blockEntity.setChanged();
        }
    }

    private static final class ServoAntennaElement extends PonderElementBase implements PonderSceneElement {
        private final BlockPos pos;
        private final boolean inverted;

        private ServoAntennaElement(BlockPos pos, boolean inverted) {
            this.pos = pos;
            this.inverted = inverted;
        }

        @Override
        public void renderFirst(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {
        }

        @Override
        public void renderLayer(PonderLevel world, MultiBufferSource buffer, RenderType type, GuiGraphics graphics, float pt) {
            if (type != RenderType.cutout()) {
                return;
            }

            BlockState state = world.getBlockState(pos);
            Direction facing = state.hasProperty(BlockStateProperties.FACING)
                    ? state.getValue(BlockStateProperties.FACING)
                    : Direction.NORTH;
            PartialModel partialModel = inverted
                    ? TwisterMillPartialModels.INV_SERVO_TWISTER_ANTENNA
                    : TwisterMillPartialModels.SERVO_TWISTER_ANTENNA;
            SuperByteBuffer antenna = CachedBuffers.partial(partialModel, state);
            rotateHousingFixedPartialToBlockstateFacing(antenna, facing);

            PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            if (facing == Direction.DOWN) {
                poseStack.translate(0.0D, ANTENNA_DOWN_OFFSET, 0.0D);
            }
            antenna.light(FULL_BRIGHT)
                    .renderInto(poseStack, buffer.getBuffer(RenderType.cutout()));
            poseStack.popPose();
        }

        @Override
        public void renderLast(PonderLevel world, MultiBufferSource buffer, GuiGraphics graphics, float pt) {
        }
    }

    private static void rotateHousingFixedPartialToBlockstateFacing(SuperByteBuffer buf, Direction facing) {
        switch (facing) {
            case NORTH -> {
            }
            case SOUTH -> buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
            case EAST -> buf.rotateCentered((float) Math.toRadians(90), Direction.UP);
            case WEST -> buf.rotateCentered((float) Math.toRadians(-90), Direction.UP);
            case UP -> {
                buf.rotateCentered((float) Math.toRadians(-90), Direction.EAST);
                buf.rotateCentered((float) Math.toRadians(180), Direction.Axis.Z);
            }
            case DOWN -> buf.rotateCentered((float) Math.toRadians(90), Direction.EAST);
        }
    }
}
