package com.proventure.twistermill;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.advancement.ModCriteriaTriggers;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.blockentity.ServoPropellerSlotManager;
import com.proventure.twistermill.client.TwisterMillClient;
import com.proventure.twistermill.client.TwisterMillItemDescriptions;
import com.proventure.twistermill.command.TwisterMillDiagnosticsCommands;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.condition.ModRecipeConditions;
import com.proventure.twistermill.compat.framedblocks.TwisterMillFramedBlocksCompat;
import com.proventure.twistermill.diagnostics.TwisterMillAutoReseatEvents;
import com.proventure.twistermill.display.ModDisplaySources;
import com.proventure.twistermill.event.SignalQuartzOreAdvancementHandler;
import com.proventure.twistermill.event.StickServoBindingHandler;
import com.proventure.twistermill.event.TwisterSailFrameMaterialHandler;
import com.proventure.twistermill.event.TwisterSailPatternModeHandler;
import com.proventure.twistermill.event.TwisterSailPlacementHandler;
import com.proventure.twistermill.item.ModCreativeModTabs;
import com.proventure.twistermill.item.ModItems;
import com.proventure.twistermill.menu.ModMenuTypes;
import com.proventure.twistermill.binaryredstone.ModBinaryRedstoneMessages;
import com.proventure.twistermill.weather.TwisterWeatherBackendValidator;
import com.proventure.twistermill.worldgen.ModWorldgenFeatures;
import com.proventure.twistermill.worldgen.TwisterMillWorldGenEvents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

@Mod(TwisterMill.MOD_ID)
public class TwisterMill {

    public static final String MOD_ID = "twistermill";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TwisterMill(IEventBus modEventBus, @NotNull ModContainer modContainer) {

        modContainer.registerConfig(ModConfig.Type.COMMON, TwisterMillConfig.COMMON_SPEC);

        ModCreativeModTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModDisplaySources.register(modEventBus);
        ModWorldgenFeatures.register(modEventBus);
        ModRecipeConditions.register(modEventBus);
        if (ModList.get().isLoaded("framedblocks")) {
            TwisterMillFramedBlocksCompat.register(modEventBus);
        }
        modEventBus.addListener(ModCriteriaTriggers::register);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreative);

        modEventBus.addListener(TwisterMillClient::registerRenderers);
        modEventBus.addListener(TwisterMillClient::registerAdditionalModels);
        modEventBus.addListener(TwisterMillClient::registerModelBakeModifiers);
        modEventBus.addListener(TwisterMillClient::registerMenuScreens);
        modEventBus.addListener(TwisterMillClient::registerGuiLayers);
        modEventBus.addListener(TwisterMillClient::registerClientSetup);
        modEventBus.addListener(this::registerPayloads);
        TwisterMillItemDescriptions.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, StickServoBindingHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, TwisterSailFrameMaterialHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, TwisterSailPlacementHandler::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, TwisterSailPatternModeHandler::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(SignalQuartzOreAdvancementHandler::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(TwisterMillWorldGenEvents::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(ServoPropellerSlotManager::onPrePhysicsTick);
        NeoForge.EVENT_BUS.addListener(ServoPropellerSlotManager::onPostPhysicsTick);
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> ServoPropellerSlotManager.onBlockPlace(event));
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> ServoPropellerSlotManager.onBlockBreak(event));
        NeoForge.EVENT_BUS.addListener(TwisterMillDiagnosticsCommands::register);
        NeoForge.EVENT_BUS.addListener(TwisterMillAutoReseatEvents::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(TwisterMillAutoReseatEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(TwisterMillAutoReseatEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(TwisterMillAutoReseatEvents::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(TwisterMillAutoReseatEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(TwisterMillAutoReseatEvents::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(TwisterMillAutoReseatEvents::onServerTickPost);

        LOGGER.info("{} loaded", MOD_ID);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        TwisterWeatherBackendValidator.validateExactlyOneBackendOrThrow();
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        ModBinaryRedstoneMessages.register(event);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }
}
