package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.WindRotoBlockEntity;
import com.proventure.twistermill.client.TwisterMillPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class WindRotoRenderer extends KineticBlockEntityRenderer<WindRotoBlockEntity> {

    private static final float ROTATION_EPSILON = 0.0001f;

    public WindRotoRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context context) {
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

        renderHalfShaft(be, state, facing, ms, buffer, packedLight);
        renderTop(be, state, facing, partialTicks, ms, buffer, packedLight);
    }

    private void renderHalfShaft(WindRotoBlockEntity be, BlockState state, Direction facing,
                                 PoseStack ms, MultiBufferSource buffer, int light) {

        SuperByteBuffer shaft = CachedBuffers.partialFacing(
                AllPartialModels.SHAFT_HALF,
                state,
                facing.getOpposite()
        );

        float generatedSpeed = be.getGeneratedSpeed();
        boolean shouldRotateShaft = be.isRunning() && Math.abs(generatedSpeed) > ROTATION_EPSILON;

        if (shouldRotateShaft) {
            float shaftAngle = KineticBlockEntityRenderer.getAngleForBe(be, be.getBlockPos(), facing.getAxis());
            KineticBlockEntityRenderer.kineticRotationTransform(shaft, be, facing.getAxis(), shaftAngle, light);
        } else {
            shaft.light(light);
        }

        shaft.renderInto(ms, buffer.getBuffer(getRenderType(be, state)));
    }

    private void renderTop(WindRotoBlockEntity be, BlockState state, Direction facing, float partialTicks,
                           PoseStack ms, MultiBufferSource buffer, int light) {

        SuperByteBuffer top = CachedBuffers.partial(TwisterMillPartialModels.WIND_ROTO_TOP, state);

        rotateTopToFacing(top, facing);

        float angleDeg = be.getInterpolatedAngle(partialTicks - 1);

        float sign = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1f : -1f;
        angleDeg *= -sign;

        top.rotateCentered((float) Math.toRadians(angleDeg), Direction.Axis.Z);
        top.light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutout()));
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
