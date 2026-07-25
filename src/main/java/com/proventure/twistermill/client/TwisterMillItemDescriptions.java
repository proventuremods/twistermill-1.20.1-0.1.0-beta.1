package com.proventure.twistermill.client;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.item.ModItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class TwisterMillItemDescriptions {

    private TwisterMillItemDescriptions() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(TwisterMillItemDescriptions::onClientSetup);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork((Runnable) TwisterMillItemDescriptions::register);
    }

    private static void register() {
        TooltipModifier.REGISTRY.register(
                ModItems.BINDING_STICK.get(),
                new ItemDescription.Modifier(
                        ModItems.BINDING_STICK.get(),
                        FontHelper.Palette.STANDARD_CREATE
                )
        );

        TooltipModifier.REGISTRY.register(
                ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get().asItem(),
                new ItemDescription.Modifier(
                        ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get().asItem(),
                        FontHelper.Palette.STANDARD_CREATE
                )
        );

        TooltipModifier.REGISTRY.register(
                ModBlocks.TWISTER_SAIL_BLOCK.get().asItem(),
                new ItemDescription.Modifier(
                        ModBlocks.TWISTER_SAIL_BLOCK.get().asItem(),
                        FontHelper.Palette.STANDARD_CREATE
                )
        );

        TooltipModifier.REGISTRY.register(
                ModBlocks.METAL_TRAVERSE.get().asItem(),
                new ItemDescription.Modifier(
                        ModBlocks.METAL_TRAVERSE.get().asItem(),
                        FontHelper.Palette.STANDARD_CREATE
                )
        );

        TooltipModifier.REGISTRY.register(
                ModBlocks.WIND_ROTO_BLOCK.get().asItem(),
                new ItemDescription.Modifier(
                        ModBlocks.WIND_ROTO_BLOCK.get().asItem(),
                        FontHelper.Palette.STANDARD_CREATE
                ).andThen(new WeatherBearingDynamicStressTooltip())
        );
    }
}
