package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.WindRotoVerticalBlockEntity;
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

public class WindRotoVerticalRenderer extends KineticBlockEntityRenderer<WindRotoVerticalBlockEntity> {

    public WindRotoVerticalRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(WindRotoVerticalBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();

        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        SuperByteBuffer top = CachedBuffers.partial(TwisterMillPartialModels.WIND_ROTO_VERTICAL_TOP, state);

        rotateTopToFacing(top, facing);

        Float sableTopAngleDegrees = be.computeSableTopVisualAngleDegrees(partialTicks);
        float angle;
        if (sableTopAngleDegrees != null) {
            angle = sableTopAngleDegrees;
            if (shouldInvertSableTopRotation(facing)) {
                angle = -angle;
            }
        } else {
            angle = be.getInterpolatedAngle(partialTicks);
            float sign = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : -1f;
            angle *= -sign;
        }

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
    }

    @Override
    public AABB getRenderBoundingBox(WindRotoVerticalBlockEntity be) {
        return SableTopVisualTransform.renderBounds(be.getBlockPos(), be.getActiveTopSubLevelIdForRender());
    }

    private static boolean shouldInvertSableTopRotation(Direction facing) {
        return true;
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
