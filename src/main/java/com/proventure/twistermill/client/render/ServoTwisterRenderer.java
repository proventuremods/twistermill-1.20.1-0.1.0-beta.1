package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.proventure.twistermill.client.TwisterMillPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class ServoTwisterRenderer extends KineticBlockEntityRenderer <ServoTwisterBlockEntity> {

    public ServoTwisterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(ServoTwisterBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();

        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        SuperByteBuffer top = CachedBuffers.partial(TwisterMillPartialModels.SERVO_TWISTER_TOP, state);

        rotateTopToFacing(top, facing);

        float angle = be.getInterpolatedAngle(partialTicks - 1);
        float sign = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : -1f;
        angle *= -sign;

        top.rotateCentered((float) Math.toRadians(angle), Direction.Axis.Z);

        int packedLight = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : light;

        top.light(packedLight)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }

    private static void rotateTopToFacing(SuperByteBuffer buf, Direction facing) {
        float modelYawFixDeg = 0f;

        switch (facing) {
            case NORTH -> {
                buf.rotateCentered(0f, Direction.UP);
                modelYawFixDeg = 0f;
            }
            case SOUTH -> {
                buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                modelYawFixDeg = 0f;
            }
            case EAST -> {
                buf.rotateCentered((float) Math.toRadians(90), Direction.UP);
                modelYawFixDeg = 180f;
            }
            case WEST -> {
                buf.rotateCentered((float) Math.toRadians(-90), Direction.UP);
                modelYawFixDeg = 180f;
            }
            case UP -> {
                buf.rotateCentered((float) Math.toRadians(-90), Direction.EAST);
                buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                modelYawFixDeg = 0f;
            }
            case DOWN -> {
                buf.rotateCentered((float) Math.toRadians(90), Direction.EAST);
                buf.rotateCentered((float) Math.toRadians(180), Direction.UP);
                modelYawFixDeg = 0f;
            }
        }

        if (modelYawFixDeg != 0f) {
            buf.rotateCentered((float) Math.toRadians(modelYawFixDeg), Direction.UP);
        }
    }
}
