package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.WindRotoBlockEntity;
import com.proventure.twistermill.client.TwisterMillPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

public class WindRotoRenderer extends KineticBlockEntityRenderer<WindRotoBlockEntity> {

    public WindRotoRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(WindRotoBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        BlockState state = be.getBlockState();

        Direction facing = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;

        Level level = be.getLevel();
        int packedLight = level != null
                ? LevelRenderer.getLightColor(level, be.getBlockPos())
                : light;

        float shaftBaseAngleRad = getShaftAngle(be, facing);
        float shaftAngleRad = shouldInvertAxisRotation(facing) ? -shaftBaseAngleRad : shaftBaseAngleRad;
        Float sableTopAngleDegrees = be.computeSableTopVisualAngleDegrees(partialTicks);

        float topAngleRad;
        if (sableTopAngleDegrees != null) {
            topAngleRad = (float) Math.toRadians(sableTopAngleDegrees);
            if (shouldInvertSableTopRotation(facing)) {
                topAngleRad = -topAngleRad;
            }
        } else {
            float topBaseAngleRad = level != null
                    ? getTopAngleWithoutPositionOffset(be, level)
                    : 0.0F;
            topAngleRad = shouldInvertTopRotation(facing) ? -topBaseAngleRad : topBaseAngleRad;
        }

        renderHalfShaft(be, state, facing, shaftAngleRad, ms, buffer, packedLight);
        renderTop(be, state, facing, topAngleRad, partialTicks, ms, buffer, packedLight);
    }

    private void renderHalfShaft(WindRotoBlockEntity be, BlockState state, Direction facing,
                                 float angleRad, PoseStack ms, MultiBufferSource buffer, int light) {

        SuperByteBuffer shaft = CachedBuffers.partialFacing(
                AllPartialModels.SHAFT_HALF,
                state,
                facing.getOpposite()
        );

        KineticBlockEntityRenderer.kineticRotationTransform(shaft, be, facing.getAxis(), angleRad, light);

        shaft.renderInto(ms, buffer.getBuffer(getRenderType(be, state)));
    }

    private void renderTop(WindRotoBlockEntity be, BlockState state, Direction facing, float angleRad, float partialTicks,
                           PoseStack ms, MultiBufferSource buffer, int light) {

        SuperByteBuffer top = CachedBuffers.partial(TwisterMillPartialModels.WIND_ROTO_TOP, state);

        rotateTopToFacing(top, facing);

        if (SableTopVisualTransform.renderTop(
                be,
                be.getActiveTopSubLevelIdForRender(),
                facing,
                partialTicks,
                ms,
                () -> top.light(light).renderInto(ms, buffer.getBuffer(RenderType.cutout()))
        )) {
            return;
        }

        top.rotateCentered(angleRad, Direction.Axis.Z);
        top.light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
    }

    @Override
    public AABB getRenderBoundingBox(WindRotoBlockEntity be) {
        return SableTopVisualTransform.renderBounds(be.getBlockPos(), be.getActiveTopSubLevelIdForRender());
    }

    private static float getShaftAngle(WindRotoBlockEntity be, Direction facing) {
        return KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), facing.getAxis());
    }

    private static float getTopAngleWithoutPositionOffset(WindRotoBlockEntity be, Level level) {
        float angleDeg = (AnimationTickHolder.getRenderTime(level) * be.getSpeed() * 3f / 10f) % 360f;
        return (float) Math.toRadians(angleDeg);
    }

    private static boolean shouldInvertAxisRotation(Direction facing) {
        return false;
    }

    private static boolean shouldInvertTopRotation(Direction facing) {
        return facing == Direction.UP
                || facing == Direction.SOUTH
                || facing == Direction.EAST;
    }

    private static boolean shouldInvertSableTopRotation(Direction facing) {
        return true;
    }

    private static void rotateTopToFacing(SuperByteBuffer buf, Direction facing) {
        float modelYawFixDeg = 0f;

        switch (facing) {
            case NORTH -> {
                buf.rotateCentered((float) Math.toRadians(0), Direction.UP);
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

        if (!Mth.equal(modelYawFixDeg, 0f)) {
            buf.rotateCentered((float) Math.toRadians(modelYawFixDeg), Direction.UP);
        }
    }
}
