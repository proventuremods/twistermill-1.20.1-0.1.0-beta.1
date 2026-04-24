package com.proventure.twistermill.display;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.display.source.ServoTwisterDisplaySource;
import com.proventure.twistermill.display.source.WindRotoDisplaySource;
import com.proventure.twistermill.display.source.WindRotoVerticalDisplaySource;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModDisplaySources {

    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, TwisterMill.MOD_ID);

    public static final RegistryObject<DisplaySource> WIND_ROTO_STATS =
            DISPLAY_SOURCES.register("wind_roto_stats", WindRotoDisplaySource::new);
    public static final RegistryObject<DisplaySource> SERVO_TWISTER_STATS =
            DISPLAY_SOURCES.register("servo_twister_stats", ServoTwisterDisplaySource::new);
    public static final RegistryObject<DisplaySource> WIND_ROTO_VERTICAL_STATS =
            DISPLAY_SOURCES.register("wind_roto_vertical_stats", WindRotoVerticalDisplaySource::new);

    private ModDisplaySources() {
    }

    public static void register(IEventBus eventBus) {
        DISPLAY_SOURCES.register(eventBus);
        eventBus.addListener(ModDisplaySources::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DisplaySource.BY_BLOCK.add(ModBlocks.WIND_ROTO_BLOCK.get(), WIND_ROTO_STATS.get());
            DisplaySource.BY_BLOCK.add(ModBlocks.SERVO_TWISTER_BLOCK.get(), SERVO_TWISTER_STATS.get());
            DisplaySource.BY_BLOCK.add(ModBlocks.INV_SERVO_TWISTER_BLOCK.get(), SERVO_TWISTER_STATS.get());
            DisplaySource.BY_BLOCK.add(ModBlocks.WIND_ROTO_VERTICAL_BLOCK.get(), WIND_ROTO_VERTICAL_STATS.get());
        });
    }
}
