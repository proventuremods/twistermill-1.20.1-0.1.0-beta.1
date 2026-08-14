package com.proventure.twistermill.client.model;

import com.proventure.twistermill.compat.create.MetalTraverseGirderStateResolver;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CreateGirderVisualResolver {

    private CreateGirderVisualResolver() {
    }

    static @Nullable VisualData resolve(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                        Map<BlockState, BakedModel> girderModels) {
        BlockState girderState = resolveVisualState(world, pos, state);
        if (girderState == null) {
            return null;
        }
        BakedModel model = girderModels.get(girderState);
        if (model == null) {
            return null;
        }
        BlockAndTintGetter virtualWorld = MetalTraverseGirderStateResolver.virtualWorld(world, pos, girderState);
        ModelData modelData = model.getModelData(virtualWorld, pos, girderState, ModelData.EMPTY);
        return new VisualData(girderState, model, modelData);
    }

    static @Nullable BlockState resolveVisualState(BlockAndTintGetter world, BlockPos pos, BlockState state) {
        return MetalTraverseGirderStateResolver.resolveEmbeddedGirderState(world, pos, state);
    }

    static List<String> describeVisibleJsonModels(BlockAndTintGetter world, BlockPos pos, BlockState state) {
        BlockState girderState = resolveVisualState(world, pos, state);
        if (girderState == null) {
            return List.of();
        }
        return describeVisibleJsonModels(girderState);
    }

    static List<String> describeVisibleJsonModels(BlockState girderState) {
        if (girderState.getBlock() != AllBlocks.METAL_GIRDER.get()) {
            return List.of();
        }
        boolean x = girderState.getValue(GirderBlock.X);
        boolean z = girderState.getValue(GirderBlock.Z);
        boolean top = girderState.getValue(GirderBlock.TOP);
        boolean bottom = girderState.getValue(GirderBlock.BOTTOM);
        List<String> models = new ArrayList<>();
        if (!x && !z) {
            models.add("create/metal_girder/block_pole.json");
        }
        if (x) {
            models.add("create/metal_girder/block_x.json");
        }
        if (z) {
            models.add("create/metal_girder/block_z.json");
        }
        if (top && x && !z) {
            models.add("create/metal_girder/block_top.json");
        }
        if (bottom && x && !z) {
            models.add("create/metal_girder/block_bottom.json");
        }
        if (top && !x && z) {
            models.add("create/metal_girder/block_top.json");
        }
        if (bottom && !x && z) {
            models.add("create/metal_girder/block_bottom.json");
        }
        if (x && z) {
            models.add("create/metal_girder/block_cross.json");
        }
        return models;
    }

    static boolean hidesTraverseSide(BlockState girderState, Direction side) {
        if (girderState.getBlock() != AllBlocks.METAL_GIRDER.get()) {
            return false;
        }
        return switch (side) {
            case WEST, EAST -> girderState.getValue(GirderBlock.X);
            case NORTH, SOUTH -> girderState.getValue(GirderBlock.Z);
            case UP -> girderState.getValue(GirderBlock.TOP);
            case DOWN -> girderState.getValue(GirderBlock.BOTTOM);
        };
    }

    static BlockAndTintGetter virtualWorld(BlockAndTintGetter world, BlockPos pos, BlockState girderState) {
        return MetalTraverseGirderStateResolver.virtualWorld(world, pos, girderState);
    }

    record VisualData(BlockState state, BakedModel model, ModelData modelData) {
        List<BakedQuad> getQuads(@Nullable Direction side, RandomSource rand, ModelData extraData,
                                 @Nullable RenderType renderType) {
            return model.getQuads(state, side, rand, modelData, renderType);
        }
    }
}
