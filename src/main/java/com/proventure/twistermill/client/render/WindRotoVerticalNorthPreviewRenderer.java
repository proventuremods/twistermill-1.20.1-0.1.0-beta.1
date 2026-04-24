package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.proventure.twistermill.block.custom.WindRotoVerticalBlock;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = com.proventure.twistermill.TwisterMill.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WindRotoVerticalNorthPreviewRenderer {

    private static final float GREEN_RED = 0.20F;
    private static final float GREEN_GREEN = 1.00F;
    private static final float GREEN_BLUE = 0.20F;

    private static final float GREEN_FILL_ALPHA = 0.22F;
    private static final float GREEN_LINE_ALPHA = 0.85F;

    private static final double MIN = 0.002D;
    private static final double MAX = 0.998D;

    private static final float OBSIDIAN_SCALE_MAX = 15.0F / 16.0F;
    private static final float OBSIDIAN_SCALE_MIN = 13.0F / 16.0F;
    private static final float OBSIDIAN_SCALE_CENTER = (OBSIDIAN_SCALE_MAX + OBSIDIAN_SCALE_MIN) * 0.5F;
    private static final float OBSIDIAN_SCALE_AMPLITUDE = (OBSIDIAN_SCALE_MAX - OBSIDIAN_SCALE_MIN) * 0.5F;
    private static final float TICKS_FROM_BIG_TO_SMALL = 15.0F;

    private WindRotoVerticalNorthPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null) {
            return;
        }

        if (!shouldShowPreview(player, minecraft.hitResult)) {
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) minecraft.hitResult;
        BlockPos windRotoVerticalPos = blockHit.getBlockPos();
        BlockState lookedAtState = level.getBlockState(windRotoVerticalPos);

        if (!(lookedAtState.getBlock() instanceof WindRotoVerticalBlock)) {
            return;
        }

        if (!lookedAtState.hasProperty(WindRotoVerticalBlock.FACING)) {
            return;
        }

        Direction facing = lookedAtState.getValue(WindRotoVerticalBlock.FACING);
        if (facing != Direction.UP && facing != Direction.DOWN) {
            return;
        }

        BlockPos previewPos = windRotoVerticalPos.north();
        if (!level.isLoaded(previewPos)) {
            return;
        }

        if (level.getBlockState(previewPos).is(Blocks.OBSIDIAN)) {
            return;
        }

        float partialTick = minecraft.getFrameTime();
        float obsidianScale = getObsidianScale(player, partialTick);

        renderPreviewBlock(event.getPoseStack(), minecraft, level, previewPos, obsidianScale);
    }

    private static boolean shouldShowPreview(LocalPlayer player, HitResult hitResult) {
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        if (!player.getItemInHand(InteractionHand.OFF_HAND).is(Items.COMPASS)) {
            return false;
        }

        if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.OBSIDIAN)) {
            return false;
        }

        if (!GogglesItem.isWearingGoggles(player)) {
            return false;
        }

        return true;
    }

    private static float getObsidianScale(LocalPlayer player, float partialTick) {
        double ticks = player.tickCount + partialTick;
        double angle = (ticks / TICKS_FROM_BIG_TO_SMALL) * Math.PI;
        return OBSIDIAN_SCALE_CENTER + (float) Math.cos(angle) * OBSIDIAN_SCALE_AMPLITUDE;
    }

    private static void renderPreviewBlock(
            PoseStack poseStack,
            Minecraft minecraft,
            ClientLevel level,
            BlockPos previewPos,
            float obsidianScale
    ) {
        poseStack.pushPose();
        poseStack.translate(
                previewPos.getX() - minecraft.gameRenderer.getMainCamera().getPosition().x,
                previewPos.getY() - minecraft.gameRenderer.getMainCamera().getPosition().y,
                previewPos.getZ() - minecraft.gameRenderer.getMainCamera().getPosition().z
        );

        renderObsidianPreview(poseStack, minecraft, level, previewPos, obsidianScale);
        renderGreenHologram(poseStack, minecraft);

        poseStack.popPose();
    }

    private static void renderObsidianPreview(
            PoseStack poseStack,
            Minecraft minecraft,
            ClientLevel level,
            BlockPos previewPos,
            float obsidianScale
    ) {
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        BlockState previewState = Blocks.OBSIDIAN.defaultBlockState();
        int packedLight = LevelRenderer.getLightColor(level, previewPos);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(obsidianScale, obsidianScale, obsidianScale);
        poseStack.translate(-0.5F, -0.5F, -0.5F);

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        blockRenderer.renderSingleBlock(
                previewState,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY,
                RenderType.solid()
        );

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        bufferSource.endBatch(RenderType.solid());
        poseStack.popPose();
    }

    private static void renderGreenHologram(PoseStack poseStack, Minecraft minecraft) {
        renderFilledCube(poseStack, GREEN_FILL_ALPHA);

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.lines()),
                MIN, MIN, MIN,
                MAX, MAX, MAX,
                GREEN_RED, GREEN_GREEN, GREEN_BLUE, GREEN_LINE_ALPHA
        );
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderFilledCube(PoseStack poseStack, float alpha) {
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        addQuad(buffer, matrix, MIN, MIN, MIN, MAX, MIN, MIN, MAX, MAX, MIN, MIN, MAX, MIN, alpha);
        addQuad(buffer, matrix, MAX, MIN, MAX, MIN, MIN, MAX, MIN, MAX, MAX, MAX, MAX, MAX, alpha);
        addQuad(buffer, matrix, MIN, MIN, MAX, MIN, MIN, MIN, MIN, MAX, MIN, MIN, MAX, MAX, alpha);
        addQuad(buffer, matrix, MAX, MIN, MIN, MAX, MIN, MAX, MAX, MAX, MAX, MAX, MAX, MIN, alpha);
        addQuad(buffer, matrix, MIN, MAX, MIN, MAX, MAX, MIN, MAX, MAX, MAX, MIN, MAX, MAX, alpha);
        addQuad(buffer, matrix, MIN, MIN, MAX, MAX, MIN, MAX, MAX, MIN, MIN, MIN, MIN, MIN, alpha);

        BufferUploader.drawWithShader(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void addQuad(
            BufferBuilder buffer,
            Matrix4f matrix,
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            double x3, double y3, double z3,
            double x4, double y4, double z4,
            float alpha
    ) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(GREEN_RED, GREEN_GREEN, GREEN_BLUE, alpha).endVertex();
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(GREEN_RED, GREEN_GREEN, GREEN_BLUE, alpha).endVertex();
        buffer.vertex(matrix, (float) x3, (float) y3, (float) z3).color(GREEN_RED, GREEN_GREEN, GREEN_BLUE, alpha).endVertex();
        buffer.vertex(matrix, (float) x4, (float) y4, (float) z4).color(GREEN_RED, GREEN_GREEN, GREEN_BLUE, alpha).endVertex();
    }
}