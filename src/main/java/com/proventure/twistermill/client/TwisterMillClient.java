package com.proventure.twistermill.client;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.client.command.TwisterMillClientCommands;
import com.proventure.twistermill.client.model.ConnectedMetalTraverseModel;
import com.proventure.twistermill.client.model.MetalTraverseGirderGhostRenderer;
import com.proventure.twistermill.client.screen.ControlTableScreen;
import com.proventure.twistermill.client.screen.TwisterMillConfigScreen;
import com.proventure.twistermill.client.render.InvServoTwisterRenderer;
import com.proventure.twistermill.client.render.InternalServoRedstoneLinkRenderer;
import com.proventure.twistermill.client.render.ServoTwisterRenderer;
import com.proventure.twistermill.client.render.WeatherSailForceVectorRenderer;
import com.proventure.twistermill.client.render.WindRotoRenderer;
import com.proventure.twistermill.client.render.WindRotoVerticalNorthPreviewRenderer;
import com.proventure.twistermill.client.render.WindRotoVerticalRenderer;
import com.proventure.twistermill.compat.framedblocks.client.TwisterMillFramedBlocksClientModels;
import com.proventure.twistermill.menu.ModMenuTypes;
import com.proventure.twistermill.ponder.TwisterMillPonderPlugin;
import com.proventure.twistermill.weather.WeatherSailForceSnapshotPayload;
import com.simibubi.create.AllBlocks;
import net.createmod.catnip.data.Iterate;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class TwisterMillClient {

    public static void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
                IConfigScreenFactory.class,
                (minecraft, parent) -> TwisterMillConfigScreen.create(parent)
        );
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        NeoForge.EVENT_BUS.addListener(WindRotoVerticalNorthPreviewRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(WeatherSailForceVectorRenderer::onRenderLevelStage);
        event.registerBlockEntityRenderer(
                ModBlockEntities.WIND_ROTO_BE.get(),
                WindRotoRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.SERVO_TWISTER_BE.get(),
                ServoTwisterRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.INV_SERVO_TWISTER_BE.get(),
                InvServoTwisterRenderer::new
        );
        event.registerBlockEntityRenderer(
                ModBlockEntities.WIND_ROTO_VERTICAL_BE.get(),
                WindRotoVerticalRenderer::new
        );
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.WIND_ROTO_TOP_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.WIND_ROTO_VERTICAL_TOP_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.SERVO_TWISTER_TOP_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.SERVO_TWISTER_ANTENNA_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.INV_SERVO_TWISTER_TOP_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.INV_SERVO_TWISTER_ANTENNA_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_NORTH_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_SOUTH_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_EAST_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_WEST_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_NORTH_LADDER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_SOUTH_LADDER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_EAST_LADDER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_WEST_LADDER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_UP_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_BRACKET_DOWN_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_HIDE_WS_CORNER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_HIDE_EN_CORNER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y90_HIDE_WN_CORNER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y90_HIDE_ES_CORNER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y180_HIDE_EN_CORNER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y270_HIDE_ES_CORNER_LOCATION
        ));
        event.register(ModelResourceLocation.standalone(TwisterMillPartialModels.CREATE_METAL_GIRDER_X_LOCATION));
        event.register(ModelResourceLocation.standalone(TwisterMillPartialModels.CREATE_METAL_GIRDER_Y_LOCATION));
        event.register(ModelResourceLocation.standalone(TwisterMillPartialModels.CREATE_METAL_GIRDER_Z_LOCATION));
        TwisterMillFramedBlocksClientModels.registerAdditionalModels(event);
    }

    public static void registerModelBakeModifiers(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();

        Map<Direction, BakedModel> horizontalTraverseBracketModels = getMetalTraverseHorizontalBracketModels(models);
        Map<Direction, BakedModel> horizontalTraverseLadderBracketModels =
                getMetalTraverseHorizontalLadderBracketModels(models);
        BakedModel upTraverseBracketModel = getMetalTraverseBracketModel(models, Direction.UP);
        BakedModel downTraverseBracketModel = getMetalTraverseBracketModel(models, Direction.DOWN);
        BakedModel traversePoleHideWsModel = getStandaloneModel(models,
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_HIDE_WS_CORNER_LOCATION);
        BakedModel traversePoleHideEnModel = getStandaloneModel(models,
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_HIDE_EN_CORNER_LOCATION);
        BakedModel traversePoleY90HideWnModel = getStandaloneModel(models,
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y90_HIDE_WN_CORNER_LOCATION);
        BakedModel traversePoleY90HideEsModel = getStandaloneModel(models,
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y90_HIDE_ES_CORNER_LOCATION);
        BakedModel traversePoleY180HideEnModel = getStandaloneModel(models,
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y180_HIDE_EN_CORNER_LOCATION);
        BakedModel traversePoleY270HideWnModel = getStandaloneModel(models,
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y270_HIDE_WN_CORNER_LOCATION);
        BakedModel traversePoleY270HideEsModel = getStandaloneModel(models,
                TwisterMillPartialModels.METAL_TRAVERSE_POLE_Y270_HIDE_ES_CORNER_LOCATION);
        Map<BlockState, BakedModel> girderModels = getCreateMetalGirderModels(models);
        if (horizontalTraverseBracketModels != null && horizontalTraverseLadderBracketModels != null
                && upTraverseBracketModel != null && downTraverseBracketModel != null) {
            for (Block metalTraverse : new Block[]{
                    ModBlocks.METAL_TRAVERSE.get(),
                    ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()
            }) {
                for (BlockState state : metalTraverse.getStateDefinition().getPossibleStates()) {
                    ModelResourceLocation stateLocation = BlockModelShaper.stateToModelLocation(state);
                    BakedModel originalModel = models.get(stateLocation);
                    if (originalModel == null || originalModel instanceof ConnectedMetalTraverseModel) {
                        continue;
                    }
                    models.put(stateLocation,
                            new ConnectedMetalTraverseModel(
                                    originalModel,
                                    horizontalTraverseBracketModels,
                                    horizontalTraverseLadderBracketModels,
                                    upTraverseBracketModel,
                                    downTraverseBracketModel,
                                    traversePoleHideWsModel,
                                    traversePoleHideEnModel,
                                    traversePoleY90HideWnModel,
                                    traversePoleY90HideEsModel,
                                    traversePoleY180HideEnModel,
                                    traversePoleY270HideWnModel,
                                    traversePoleY270HideEsModel,
                                    girderModels));
                }
            }
            MetalTraverseGirderGhostRenderer.install(girderModels);
        }
    }

    private static Map<BlockState, BakedModel> getCreateMetalGirderModels(Map<ModelResourceLocation, BakedModel> models) {
        Map<BlockState, BakedModel> girderModels = new HashMap<>();
        for (BlockState state : AllBlocks.METAL_GIRDER.get().getStateDefinition().getPossibleStates()) {
            BakedModel model = models.get(BlockModelShaper.stateToModelLocation(state));
            if (model != null) {
                girderModels.put(state, model);
            }
        }
        return Map.copyOf(girderModels);
    }

    private static Map<Direction, BakedModel> getMetalTraverseHorizontalBracketModels(Map<ModelResourceLocation, BakedModel> models) {
        Map<Direction, BakedModel> bracketModels = new EnumMap<>(Direction.class);
        for (Direction direction : Iterate.horizontalDirections) {
            BakedModel bracketModel = getMetalTraverseBracketModel(models, direction);
            if (bracketModel == null) {
                return null;
            }
            bracketModels.put(direction, bracketModel);
        }
        return bracketModels;
    }

    private static Map<Direction, BakedModel> getMetalTraverseHorizontalLadderBracketModels(
            Map<ModelResourceLocation, BakedModel> models) {
        Map<Direction, BakedModel> bracketModels = new EnumMap<>(Direction.class);
        for (Direction direction : Iterate.horizontalDirections) {
            ModelResourceLocation location = ModelResourceLocation.standalone(
                    TwisterMillPartialModels.getMetalTraverseLadderBracketLocation(direction));
            BakedModel bracketModel = models.get(location);
            if (bracketModel == null) {
                return null;
            }
            bracketModels.put(direction, bracketModel);
        }
        return bracketModels;
    }

    private static BakedModel getMetalTraverseBracketModel(Map<ModelResourceLocation, BakedModel> models, Direction direction) {
        ModelResourceLocation location =
                ModelResourceLocation.standalone(TwisterMillPartialModels.getMetalTraverseBracketLocation(direction));
        return models.get(location);
    }

    private static BakedModel getStandaloneModel(Map<ModelResourceLocation, BakedModel> models, ResourceLocation location) {
        return models.get(ModelResourceLocation.standalone(location));
    }

    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.CONTROL_TABLE_MENU.get(), ControlTableScreen::new);
    }

    public static void registerGuiLayers(@SuppressWarnings("unused") RegisterGuiLayersEvent event) {
    }

    public static void registerClientSetup(@SuppressWarnings("unused") FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(TwisterMillClientCommands::register);
        NeoForge.EVENT_BUS.addListener(TwisterMillMassTooltip::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(MetalTraverseDebugOverlay::onDebugText);
        NeoForge.EVENT_BUS.addListener(InternalServoRedstoneLinkRenderer::onClientTick);
        NeoForge.EVENT_BUS.addListener(WeatherSailForceVectorRenderer::onClientTick);
        WeatherSailForceSnapshotPayload.installClientHandler(WeatherSailForceVectorRenderer::acceptSnapshot);
        PonderIndex.addPlugin(new TwisterMillPonderPlugin());
    }
}
