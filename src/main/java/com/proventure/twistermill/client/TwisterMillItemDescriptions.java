package com.proventure.twistermill.client;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.item.ModItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TwisterMill.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TwisterMillItemDescriptions {

    private TwisterMillItemDescriptions() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(TwisterMillItemDescriptions::register);
    }

    private static void register() {
        TooltipModifier.REGISTRY.register(
                ModItems.BINDING_STICK.get(),
                new ItemDescription.Modifier(
                        ModItems.BINDING_STICK.get(),
                        FontHelper.Palette.GRAY_AND_WHITE
                )
        );

        TooltipModifier.REGISTRY.register(
                ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get().asItem(),
                new ItemDescription.Modifier(
                        ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get().asItem(),
                        FontHelper.Palette.GRAY_AND_WHITE
                )
        );

        TooltipModifier.REGISTRY.register(
                ModBlocks.TWISTER_SAIL_BLOCK.get().asItem(),
                new ItemDescription.Modifier(
                        ModBlocks.TWISTER_SAIL_BLOCK.get().asItem(),
                        FontHelper.Palette.GRAY_AND_WHITE
                )
        );
    }
}
