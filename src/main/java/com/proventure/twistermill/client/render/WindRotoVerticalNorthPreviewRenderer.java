package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.block.custom.WindRotoVerticalBlock;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

public final class WindRotoVerticalNorthPreviewRenderer {

    private static final float GREEN_RED = 0.20F;
    private static final float GREEN_GREEN = 1.00F;
    private static final float GREEN_BLUE = 0.20F;

    private static final float GREEN_FILL_ALPHA = 0.22F;
    private static final float GREEN_LINE_ALPHA = 0.85F;

    private static final double MIN = 0.002D;
    private static final double MAX = 0.998D;

    private static final float MARKER_SCALE_MAX = 15.0F / 16.0F;
    private static final float MARKER_SCALE_MIN = 13.0F / 16.0F;
    private static final float MARKER_SCALE_CENTER = (MARKER_SCALE_MAX + MARKER_SCALE_MIN) * 0.5F;
    private static final float MARKER_SCALE_AMPLITUDE = (MARKER_SCALE_MAX - MARKER_SCALE_MIN) * 0.5F;
    private static final float TICKS_FROM_BIG_TO_SMALL = 15.0F;

    private WindRotoVerticalNorthPreviewRenderer() {
    }

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

        if (level.getBlockState(previewPos).is(Blocks.SMOOTH_STONE_SLAB)) {
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float markerScale = getMarkerScale(player, partialTick);

        renderPreviewBlock(event.getPoseStack(), minecraft, level, previewPos, markerScale);
    }

    private static boolean shouldShowPreview(LocalPlayer player, HitResult hitResult) {
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        if (!player.getItemInHand(InteractionHand.OFF_HAND).is(Items.COMPASS)) {
            return false;
        }

        if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(Blocks.SMOOTH_STONE_SLAB.asItem())) {
            return false;
        }

        return GogglesItem.isWearingGoggles(player);
    }

    private static float getMarkerScale(LocalPlayer player, float partialTick) {
        double ticks = player.tickCount + partialTick;
        double angle = (ticks / TICKS_FROM_BIG_TO_SMALL) * Math.PI;
        return MARKER_SCALE_CENTER + (float) Math.cos(angle) * MARKER_SCALE_AMPLITUDE;
    }

    private static void renderPreviewBlock(
            PoseStack poseStack,
            Minecraft minecraft,
            ClientLevel level,
            BlockPos previewPos,
            float markerScale
    ) {
        poseStack.pushPose();
        poseStack.translate(
                previewPos.getX() - minecraft.gameRenderer.getMainCamera().getPosition().x,
                previewPos.getY() - minecraft.gameRenderer.getMainCamera().getPosition().y,
                previewPos.getZ() - minecraft.gameRenderer.getMainCamera().getPosition().z
        );

        renderMarkerPreview(poseStack, minecraft, level, previewPos, markerScale);
        renderGreenHologram(poseStack, minecraft);

        poseStack.popPose();
    }

    private static void renderMarkerPreview(
            PoseStack poseStack,
            Minecraft minecraft,
            ClientLevel level,
            BlockPos previewPos,
            float markerScale
    ) {
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();
        BlockState previewState = Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        int packedLight = LevelRenderer.getLightColor(level, previewPos);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.scale(markerScale, markerScale, markerScale);
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
        renderFilledCube(poseStack, minecraft);

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

    private static void renderFilledCube(PoseStack poseStack, Minecraft minecraft) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                bufferSource.getBuffer(RenderType.debugFilledBox()),
                MIN, MIN, MIN,
                MAX, MAX, MAX,
                GREEN_RED, GREEN_GREEN, GREEN_BLUE, GREEN_FILL_ALPHA
        );
        bufferSource.endBatch(RenderType.debugFilledBox());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
