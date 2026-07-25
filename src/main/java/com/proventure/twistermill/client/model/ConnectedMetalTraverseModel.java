package com.proventure.twistermill.client.model;

import com.proventure.twistermill.block.custom.MetalTraverseBlock;
import com.proventure.twistermill.block.custom.MetalFrameConnectionHelper;
import com.proventure.twistermill.blockentity.WrenchSideCycleBlockEntity;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class ConnectedMetalTraverseModel extends BakedModelWrapperWithData {

    private static final ModelProperty<ConnectionData> CONNECTION_PROPERTY = new ModelProperty<>();
    private final Map<Direction, BakedModel> horizontalBracketModels;
    private final BakedModel upBracketModel;
    private final BakedModel downBracketModel;
    private final BakedModel poleHideWsModel;
    private final BakedModel poleHideEnModel;
    private final BakedModel poleY90HideWnModel;
    private final BakedModel poleY90HideEsModel;
    private final BakedModel poleY180HideWsModel;
    private final BakedModel poleY180HideEnModel;
    private final BakedModel poleY270HideWnModel;
    private final BakedModel poleY270HideEsModel;

    public ConnectedMetalTraverseModel(BakedModel originalModel, Map<Direction, BakedModel> horizontalBracketModels,
                                       BakedModel upBracketModel, BakedModel downBracketModel,
                                       BakedModel poleHideWsModel, BakedModel poleHideEnModel,
                                       BakedModel poleY90HideWnModel, BakedModel poleY90HideEsModel,
                                       BakedModel poleY180HideWsModel, BakedModel poleY180HideEnModel,
                                       BakedModel poleY270HideWnModel, BakedModel poleY270HideEsModel) {
        super(originalModel);
        this.horizontalBracketModels = new EnumMap<>(Direction.class);
        this.horizontalBracketModels.putAll(horizontalBracketModels);
        this.upBracketModel = upBracketModel;
        this.downBracketModel = downBracketModel;
        this.poleHideWsModel = poleHideWsModel;
        this.poleHideEnModel = poleHideEnModel;
        this.poleY90HideWnModel = poleY90HideWnModel;
        this.poleY90HideEsModel = poleY90HideEsModel;
        this.poleY180HideWsModel = poleY180HideWsModel;
        this.poleY180HideEnModel = poleY180HideEnModel;
        this.poleY270HideWnModel = poleY270HideWnModel;
        this.poleY270HideEsModel = poleY270HideEsModel;
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
                                                BlockState state, ModelData blockEntityData) {
        WrenchSideCycleBlockEntity.SideCycleSnapshot sideCycle = blockEntityData.has(WrenchSideCycleBlockEntity.SIDE_CYCLE_PROPERTY)
                ? blockEntityData.get(WrenchSideCycleBlockEntity.SIDE_CYCLE_PROPERTY)
                : null;
        return builder.with(CONNECTION_PROPERTY, collectConnectionData(world, pos, state, sideCycle));
    }

    private static ConnectionData collectConnectionData(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                                        WrenchSideCycleBlockEntity.SideCycleSnapshot sideCycle) {
        ConnectionData connectionData = new ConnectionData();
        for (Direction direction : Iterate.horizontalDirections) {
            byte stage = getStage(sideCycle, direction);
            connectionData.setStage(direction, stage);
            boolean hidden = WrenchSideCycleBlockEntity.isHiddenStage(stage);
            boolean manualBracket = !hidden && (sideCycle != null
                    ? WrenchSideCycleBlockEntity.isBracketStage(stage)
                    : MetalTraverseBlock.hasManualBracket(state, direction));
            boolean autoBracket = !hidden && MetalTraverseBlock.wouldAutoRenderBracket(world, pos, state, direction);
            boolean connected = !hidden && (autoBracket || manualBracket);
            connectionData.setConnected(direction, connected);
            connectionData.setStraightRow(direction, false);
        }

        byte upStage = getStage(sideCycle, Direction.UP);
        connectionData.setStage(Direction.UP, upStage);
        boolean upHidden = WrenchSideCycleBlockEntity.isHiddenStage(upStage);
        boolean manualUp = !upHidden && (sideCycle != null
                ? WrenchSideCycleBlockEntity.isBracketStage(upStage)
                : MetalTraverseBlock.hasManualBracket(state, Direction.UP));
        boolean upAutoBracket = !upHidden && MetalTraverseBlock.wouldAutoRenderBracket(world, pos, state, Direction.UP);
        boolean upConnected = !upHidden && (upAutoBracket || manualUp);
        connectionData.setUpConnected(upConnected);

        byte downStage = getStage(sideCycle, Direction.DOWN);
        connectionData.setStage(Direction.DOWN, downStage);
        boolean downHidden = WrenchSideCycleBlockEntity.isHiddenStage(downStage);
        boolean manualDown = !downHidden && (sideCycle != null
                ? WrenchSideCycleBlockEntity.isBracketStage(downStage)
                : MetalTraverseBlock.hasManualBracket(state, Direction.DOWN));
        boolean downAutoBracket = !downHidden && MetalTraverseBlock.wouldAutoRenderBracket(world, pos, state, Direction.DOWN);
        boolean downConnected = !downHidden && (downAutoBracket || manualDown);
        connectionData.setDownConnected(downConnected);
        connectionData.setCornerPoleHide(getCornerPoleHide(world, pos, state));
        return connectionData;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.union(
                super.getRenderTypes(state, rand, data),
                ChunkRenderTypeSet.of(RenderType.translucent()));
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
                                    RenderType renderType) {
        ConnectionData data = extraData.has(CONNECTION_PROPERTY) ? extraData.get(CONNECTION_PROPERTY) : null;
        if (isTranslucentPass(renderType)) {
            if (side != null || data == null || !data.isUpConnected() || upBracketModel == null) {
                return List.of();
            }
            return upBracketModel.getQuads(state, side, rand, extraData, renderType);
        }

        List<BakedQuad> baseQuads = getBaseQuads(state, side, rand, extraData, renderType, data);
        if (side != null || data == null) {
            return baseQuads;
        }

        List<BakedQuad> quads = new ArrayList<>(baseQuads);
        for (Direction direction : Iterate.horizontalDirections) {
            if (!data.isConnected(direction)) {
                continue;
            }
            if (data.isStraightRow(direction)) {
                continue;
            }

            BakedModel bracketModel = horizontalBracketModels.get(direction);
            if (bracketModel == null) {
                continue;
            }

            quads.addAll(bracketModel.getQuads(state, side, rand, extraData, renderType));
        }

        if (renderType == null && data.isUpConnected() && upBracketModel != null) {
            quads.addAll(upBracketModel.getQuads(state, side, rand, extraData, renderType));
        }

        if (data.isDownConnected() && downBracketModel != null) {
            quads.addAll(downBracketModel.getQuads(state, side, rand, extraData, renderType));
        }

        return quads;
    }

    public static List<String> describeVisibleJsonModels(BlockAndTintGetter world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MetalTraverseBlock)) {
            return List.of();
        }

        String folder = "metal_traverse";
        WrenchSideCycleBlockEntity.SideCycleSnapshot sideCycle = world.getBlockEntity(pos) instanceof WrenchSideCycleBlockEntity be
                ? be.snapshot()
                : null;
        ConnectionData data = collectConnectionData(world, pos, state, sideCycle);
        List<String> models = new ArrayList<>();

        CornerPoleHide cornerPoleHide = data.getCornerPoleHide();
        if (cornerPoleHide != CornerPoleHide.NONE) {
            models.add(folder + "/" + getCornerPoleHideModelName(cornerPoleHide) + ".json");
        } else {
            addBaseModelNames(models, folder, state);
        }

        for (Direction direction : Iterate.horizontalDirections) {
            if (data.isConnected(direction) && !data.isStraightRow(direction)) {
                models.add(folder + "/bracket_" + direction.getName() + ".json");
            }
        }
        if (data.isUpConnected()) {
            models.add(folder + "/bracket_up.json");
        }
        if (data.isDownConnected()) {
            models.add(folder + "/bracket_down.json");
        }
        return models;
    }

    private static void addBaseModelNames(List<String> models, String folder, BlockState state) {
        boolean x = state.getValue(MetalTraverseBlock.X);
        boolean z = state.getValue(MetalTraverseBlock.Z);
        boolean top = state.getValue(MetalTraverseBlock.TOP);
        boolean bottom = state.getValue(MetalTraverseBlock.BOTTOM);
        boolean yRotated = state.getValue(MetalTraverseBlock.Y_ROTATED);
        Direction.Axis axis = state.getValue(MetalTraverseBlock.AXIS);

        if (!x && !z) {
            models.add(folder + "/" + ((yRotated && axis == Direction.Axis.Z) ? "block_pole_z90" : "block_pole") + ".json");
            return;
        }

        boolean z90 = yRotated && axis == Direction.Axis.Z;
        if ((x && z) || (top && bottom)) {
            models.add(folder + "/" + (z90 ? "block_cross_z90" : "block_cross") + ".json");
            return;
        }

        if (x) {
            models.add(folder + "/" + (z90 ? "block_x_z90" : "block_x") + ".json");
        } else {
            models.add(folder + "/" + (z90 ? "block_z_z90" : "block_z") + ".json");
        }

        if (top && !bottom) {
            models.add(folder + "/" + (z90 ? "block_top_z90" : "block_top") + ".json");
        }
        if (bottom && !top) {
            models.add(folder + "/" + (z90 ? "block_bottom_z90" : "block_bottom") + ".json");
        }
    }

    private static String getCornerPoleHideModelName(CornerPoleHide cornerPoleHide) {
        return switch (cornerPoleHide) {
            case NONE -> "block_pole";
            case HIDE_WS -> "block_pole_hide_ws_corner";
            case HIDE_EN -> "block_pole_hide_en_corner";
            case HIDE_Y90_WN -> "block_pole_y90_hide_wn_corner";
            case HIDE_Y90_ES -> "block_pole_y90_hide_es_corner";
            case HIDE_Y180_WS -> "block_pole_y180_hide_ws_corner";
            case HIDE_Y180_EN -> "block_pole_y180_hide_en_corner";
            case HIDE_Y270_WN -> "block_pole_y90_hide_wn_corner";
            case HIDE_Y270_ES -> "block_pole_y270_hide_es_corner";
        };
    }

    private static boolean isTranslucentPass(RenderType renderType) {
        return renderType == RenderType.translucent();
    }

    private List<BakedQuad> getBaseQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
                                         RenderType renderType, ConnectionData data) {
        BakedModel model = data == null ? null : getCornerPoleHideModel(data.getCornerPoleHide());
        if (model == null) {
            return super.getQuads(state, side, rand, extraData, renderType);
        }
        return model.getQuads(state, side, rand, extraData, renderType);
    }

    private BakedModel getCornerPoleHideModel(CornerPoleHide cornerPoleHide) {
        return switch (cornerPoleHide) {
            case NONE -> null;
            case HIDE_WS -> poleHideWsModel;
            case HIDE_EN -> poleHideEnModel;
            case HIDE_Y90_WN -> poleY90HideWnModel;
            case HIDE_Y90_ES -> poleY90HideEsModel;
            case HIDE_Y180_WS -> poleY180HideWsModel;
            case HIDE_Y180_EN -> poleY180HideEnModel;
            case HIDE_Y270_WN -> poleY270HideWnModel;
            case HIDE_Y270_ES -> poleY270HideEsModel;
        };
    }

    private static byte getStage(WrenchSideCycleBlockEntity.SideCycleSnapshot snapshot, Direction side) {
        if (snapshot == null) {
            return WrenchSideCycleBlockEntity.STAGE_AUTO_A;
        }
        return snapshot.getStage(side);
    }

    private static boolean hasTraverseNeighbourOnAxis(BlockAndTintGetter world, BlockPos pos, Direction direction,
                                                      Direction.Axis axis) {
        BlockState neighbourState = world.getBlockState(pos.relative(direction));
        return neighbourState.getBlock() instanceof MetalTraverseBlock
                && MetalFrameConnectionHelper.hasMetalFrameAxis(neighbourState, axis);
    }

    private static boolean hasVerticalTraverseNeighbour(BlockAndTintGetter world, BlockPos pos, Direction direction) {
        BlockState neighbourState = world.getBlockState(pos.relative(direction));
        return neighbourState.getBlock() instanceof MetalTraverseBlock
                && MetalFrameConnectionHelper.isMetalFrameVertical(neighbourState);
    }

    private static CornerPoleHide getCornerPoleHide(BlockAndTintGetter world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof MetalTraverseBlock)
                || MetalTraverseBlock.suppressesCornerHide(state)
                || state.getValue(MetalTraverseBlock.AXIS) != Direction.Axis.Y
                || !state.getValue(MetalTraverseBlock.TOP)
                || !state.getValue(MetalTraverseBlock.BOTTOM)
                || !hasVerticalTraverseNeighbour(world, pos, Direction.DOWN)
                || hasVerticalTraverseNeighbour(world, pos, Direction.UP)) {
            return CornerPoleHide.NONE;
        }

        boolean eastX = hasTraverseNeighbourOnAxis(world, pos, Direction.EAST, Direction.Axis.X);
        boolean westX = hasTraverseNeighbourOnAxis(world, pos, Direction.WEST, Direction.Axis.X);
        boolean northZ = hasTraverseNeighbourOnAxis(world, pos, Direction.NORTH, Direction.Axis.Z);
        boolean southZ = hasTraverseNeighbourOnAxis(world, pos, Direction.SOUTH, Direction.Axis.Z);
        if (eastX == westX || northZ == southZ) {
            return CornerPoleHide.NONE;
        }

        boolean yRotated = state.getValue(MetalTraverseBlock.Y_ROTATED);
        if (eastX && northZ) {
            return yRotated ? CornerPoleHide.HIDE_Y180_WS : CornerPoleHide.HIDE_WS;
        }
        if (westX && southZ) {
            return yRotated ? CornerPoleHide.HIDE_Y180_EN : CornerPoleHide.HIDE_EN;
        }
        if (westX && northZ) {
            return yRotated ? CornerPoleHide.HIDE_Y90_ES : CornerPoleHide.HIDE_Y270_ES;
        }
        if (eastX && southZ) {
            return yRotated ? CornerPoleHide.HIDE_Y90_WN : CornerPoleHide.HIDE_Y270_WN;
        }
        return CornerPoleHide.NONE;
    }

    private enum CornerPoleHide {
        NONE,
        HIDE_WS,
        HIDE_EN,
        HIDE_Y90_WN,
        HIDE_Y90_ES,
        HIDE_Y180_WS,
        HIDE_Y180_EN,
        HIDE_Y270_WN,
        HIDE_Y270_ES
    }

    private static final class ConnectionData {
        private final boolean[] connectedFaces;
        private final boolean[] straightRowFaces;
        private final byte[] sideStages;
        private boolean upConnected;
        private boolean downConnected;
        private CornerPoleHide cornerPoleHide;

        private ConnectionData() {
            connectedFaces = new boolean[4];
            Arrays.fill(connectedFaces, false);
            straightRowFaces = new boolean[4];
            Arrays.fill(straightRowFaces, false);
            sideStages = new byte[6];
            Arrays.fill(sideStages, WrenchSideCycleBlockEntity.STAGE_AUTO_A);
            upConnected = false;
            downConnected = false;
            cornerPoleHide = CornerPoleHide.NONE;
        }

        private void setConnected(Direction face, boolean connected) {
            if (!face.getAxis().isHorizontal()) {
                return;
            }
            connectedFaces[face.get2DDataValue()] = connected;
        }

        private boolean isConnected(Direction face) {
            if (!face.getAxis().isHorizontal()) {
                return false;
            }
            return connectedFaces[face.get2DDataValue()];
        }

        private void setStraightRow(Direction face, boolean straightRow) {
            if (!face.getAxis().isHorizontal()) {
                return;
            }
            straightRowFaces[face.get2DDataValue()] = straightRow;
        }

        private boolean isStraightRow(Direction face) {
            if (!face.getAxis().isHorizontal()) {
                return false;
            }
            return straightRowFaces[face.get2DDataValue()];
        }

        private void setUpConnected(boolean connected) {
            upConnected = connected;
        }

        private void setDownConnected(boolean connected) {
            downConnected = connected;
        }

        private void setStage(Direction side, byte stage) {
            sideStages[indexOf(side)] = stage;
        }

        private byte getStage(Direction side) {
            return sideStages[indexOf(side)];
        }

        private boolean isUpConnected() {
            return upConnected;
        }

        private boolean isDownConnected() {
            return downConnected;
        }

        private void setCornerPoleHide(CornerPoleHide cornerPoleHide) {
            this.cornerPoleHide = cornerPoleHide;
        }

        private CornerPoleHide getCornerPoleHide() {
            return cornerPoleHide;
        }

        private static int indexOf(Direction side) {
            return switch (side) {
                case NORTH -> 0;
                case SOUTH -> 1;
                case EAST -> 2;
                case WEST -> 3;
                case UP -> 4;
                case DOWN -> 5;
            };
        }
    }
}
