package com.proventure.twistermill.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.block.custom.MetalTraverseWithGirderBlock;
import com.proventure.twistermill.event.MetalTraverseGirderPlacementHandler;
import net.createmod.catnip.client.render.model.BakedModelBufferer;
import net.createmod.catnip.client.render.model.ShadeSeparatedBufferSource;
import net.createmod.catnip.ghostblock.GhostBlockParams;
import net.createmod.catnip.ghostblock.GhostBlockRenderer;
import net.createmod.catnip.ghostblock.GhostBlocks;
import net.createmod.catnip.impl.client.render.ColoringVertexConsumer;
import net.createmod.catnip.placement.PlacementClient;
import net.createmod.catnip.render.SuperRenderTypeBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.IdentityHashMap;
import java.util.Map;

public final class MetalTraverseGirderGhostRenderer {

    private static final Object GHOST_KEY = new Object();
    private static Map<BlockState, BakedModel> models = Map.of();
    private static Map<BakedModel, BakedModel> traverseOnlyModels = new IdentityHashMap<>();

    private MetalTraverseGirderGhostRenderer() {
    }

    public static void install(Map<BlockState, BakedModel> girderModels) {
        models = Map.copyOf(girderModels);
        traverseOnlyModels = new IdentityHashMap<>();
        MetalTraverseGirderPlacementHandler.installClientPreview(
                MetalTraverseGirderGhostRenderer::showGirderPreview,
                MetalTraverseGirderGhostRenderer::showTraversePreview);
    }

    private static void showGirderPreview(MetalTraverseGirderPlacementHandler.GirderPreview preview) {
        BlockState ghostState = ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get().defaultBlockState()
                .setValue(MetalTraverseWithGirderBlock.GIRDER_AXIS, preview.axis());
        GhostBlockParams params = GhostBlockParams.of(ghostState)
                .at(preview.pos())
                .breathingAlpha();
        GhostBlocks.getInstance().showGhost(
                GHOST_KEY,
                new FullScaleGirderRenderer(preview.pos(), ghostState),
                params,
                2);
    }

    private static void showTraversePreview(MetalTraverseGirderPlacementHandler.TraversePreview preview) {
        GhostBlockParams params = GhostBlockParams.of(preview.compositeState())
                .at(preview.pos())
                .breathingAlpha();
        GhostBlocks.getInstance().showGhost(
                GHOST_KEY,
                new FullScaleTraverseRenderer(preview.pos(), preview.compositeState()),
                params,
                2);
    }

    private static final class FullScaleGirderRenderer extends GhostBlockRenderer {

        private final BlockPos pos;
        private final BlockState state;

        private FullScaleGirderRenderer(BlockPos pos, BlockState state) {
            this.pos = pos.immutable();
            this.state = state;
        }

        @Override
        public void render(PoseStack poseStack, SuperRenderTypeBuffer buffer, Vec3 camera,
                           GhostBlockParams params) {
            BlockAndTintGetter level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            CreateGirderVisualResolver.VisualData visual =
                    CreateGirderVisualResolver.resolve(level, pos, state, models);
            if (visual == null) {
                return;
            }

            float alpha = (float) GhostBlocks.getBreathingAlpha() * 0.75F * PlacementClient.getCurrentAlpha();
            VertexConsumer translucent = new ColoringVertexConsumer(
                    buffer.getEarlyBuffer(RenderType.translucent()), 1.0F, 1.0F, 1.0F, alpha);

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            BakedModelBufferer.bufferModel(
                    visual.model(),
                    pos,
                    CreateGirderVisualResolver.virtualWorld(level, pos, visual.state()),
                    visual.state(),
                    poseStack,
                    (ShadeSeparatedBufferSource) (renderType, shaded) -> translucent);
            poseStack.popPose();
        }
    }

    private static final class FullScaleTraverseRenderer extends GhostBlockRenderer {

        private final BlockPos pos;
        private final BlockState state;

        private FullScaleTraverseRenderer(BlockPos pos, BlockState state) {
            this.pos = pos.immutable();
            this.state = state;
        }

        @Override
        public void render(PoseStack poseStack, SuperRenderTypeBuffer buffer, Vec3 camera,
                           GhostBlockParams params) {
            BlockAndTintGetter level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }

            BakedModel compositeModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            if (!(compositeModel instanceof ConnectedMetalTraverseModel)) {
                return;
            }
            BakedModel traverseModel = traverseOnlyModels.computeIfAbsent(
                    compositeModel, TraverseOnlyModel::new);

            float alpha = (float) GhostBlocks.getBreathingAlpha() * 0.75F * PlacementClient.getCurrentAlpha();
            VertexConsumer translucent = new ColoringVertexConsumer(
                    buffer.getEarlyBuffer(RenderType.translucent()), 1.0F, 1.0F, 1.0F, alpha);

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            BakedModelBufferer.bufferModel(
                    traverseModel,
                    pos,
                    level,
                    state,
                    poseStack,
                    (ShadeSeparatedBufferSource) (renderType, shaded) -> translucent);
            poseStack.popPose();
        }
    }

    private static final class TraverseOnlyModel extends BakedModelWrapper<BakedModel> {

        private TraverseOnlyModel(BakedModel originalModel) {
            super(originalModel);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                      ModelData modelData) {
            return originalModel.getModelData(level, pos, state, modelData)
                    .derive()
                    .with(ConnectedMetalTraverseModel.SUPPRESS_GIRDER_VISUAL_PROPERTY, true)
                    .build();
        }
    }
}
