package com.proventure.twistermill;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.proventure.twistermill.display.ModDisplaySources;
import com.proventure.twistermill.item.ModCreativeModTabs;
import com.proventure.twistermill.item.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(com.proventure.twistermill.TwisterMill.MOD_ID)
public class TwisterMill {

    public static final String MOD_ID = "twistermill";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TwisterMill(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        context.registerConfig(ModConfig.Type.COMMON, TwisterMillConfig.COMMON_SPEC);

        ModCreativeModTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModDisplaySources.register(modEventBus);

        LOGGER.info("{} loaded", MOD_ID);
    }
}
