package com.proventure.twistermill.client;

import com.proventure.twistermill.blockentity.ModBlockEntities;
import com.proventure.twistermill.client.render.InvServoTwisterRenderer;
import com.proventure.twistermill.client.render.ServoTwisterRenderer;
import com.proventure.twistermill.client.render.WindRotoRenderer;
import com.proventure.twistermill.client.render.WindRotoVerticalRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.proventure.twistermill.TwisterMill.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TwisterMillClient {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
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

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(TwisterMillPartialModels.WIND_ROTO_TOP_LOCATION);
        event.register(TwisterMillPartialModels.WIND_ROTO_VERTICAL_TOP_LOCATION);
        event.register(TwisterMillPartialModels.SERVO_TWISTER_TOP_LOCATION);
        event.register(TwisterMillPartialModels.INV_SERVO_TWISTER_TOP_LOCATION);
    }
}