package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
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
import net.minecraft.world.phys.AABB;

public class InvServoTwisterRenderer extends KineticBlockEntityRenderer<InvServoTwisterBlockEntity> {
    private static final double ANTENNA_DOWN_OFFSET = 14.0D / 16.0D;

    public InvServoTwisterRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(InvServoTwisterBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();

        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        SuperByteBuffer top = CachedBuffers.partial(TwisterMillPartialModels.INV_SERVO_TWISTER_TOP, state);

        rotateTopToFacing(top, facing);

        float angle = be.getInterpolatedAngle(partialTicks);
        float sign = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : -1f;
        angle *= -sign;

        int packedLight = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : light;

        if (!SableTopVisualTransform.renderTop(
                be,
                be.getActiveTopSubLevelIdForRender(),
                facing,
                partialTicks,
                ms,
                () -> top.light(packedLight).renderInto(ms, buffer.getBuffer(RenderType.cutout()))
        )) {
            top.rotateCentered((float) Math.toRadians(angle), Direction.Axis.Z);
            top.light(packedLight)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        }

        if (be.isInternalRedstoneLinkReceiverActive()) {
            SuperByteBuffer antenna = CachedBuffers.partial(TwisterMillPartialModels.INV_SERVO_TWISTER_ANTENNA, state);
            rotateHousingFixedPartialToBlockstateFacing(antenna, facing);
            renderHousingFixedAntenna(antenna, facing, packedLight, ms, buffer);
        }

        if (be.shouldRenderInternalRedstoneLinkSlots()) {
            InternalServoRedstoneLinkRenderer.renderOnBlockEntity(be, true, ms, buffer, light, overlay);
        }

        InternalServoMovementModeIconRenderer.renderOnBlockEntity(
                be,
                be.getConfiguredMaxDegreesForDisplay(),
                ms,
                buffer
        );
    }

    @Override
    public AABB getRenderBoundingBox(InvServoTwisterBlockEntity be) {
        return SableTopVisualTransform.renderBounds(be.getBlockPos(), be.getActiveTopSubLevelIdForRender());
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

    private static void renderHousingFixedAntenna(
            SuperByteBuffer antenna,
            Direction facing,
            int packedLight,
            PoseStack ms,
            MultiBufferSource buffer
    ) {
        if (facing == Direction.DOWN) {
            renderTranslatedHousingFixedAntenna(
                    antenna,
                    0.0D,
                    ANTENNA_DOWN_OFFSET,
                    0.0D,
                    packedLight,
                    ms,
                    buffer
            );
            return;
        }

        antenna.light(packedLight)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }

    private static void renderTranslatedHousingFixedAntenna(
            SuperByteBuffer antenna,
            double x,
            double y,
            double z,
            int packedLight,
            PoseStack ms,
            MultiBufferSource buffer
    ) {
        ms.pushPose();
        ms.translate(x, y, z);
        antenna.light(packedLight)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
        ms.popPose();
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
