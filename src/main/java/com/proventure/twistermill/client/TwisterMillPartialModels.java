package com.proventure.twistermill.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class TwisterMillPartialModels {




    public static final ResourceLocation WIND_ROTO_TOP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(com.proventure.twistermill.TwisterMill.MOD_ID, "block/wind_roto_block_top");

    public static final PartialModel WIND_ROTO_TOP =
            PartialModel.of(WIND_ROTO_TOP_LOCATION);


    public static final ResourceLocation SERVO_TWISTER_TOP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(com.proventure.twistermill.TwisterMill.MOD_ID, "block/servo_twister_block_top");

    public static final PartialModel SERVO_TWISTER_TOP =
            PartialModel.of(SERVO_TWISTER_TOP_LOCATION);



    public static final ResourceLocation INV_SERVO_TWISTER_TOP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(com.proventure.twistermill.TwisterMill.MOD_ID, "block/inv_servo_twister_block_top");

    public static final PartialModel INV_SERVO_TWISTER_TOP =
            PartialModel.of(INV_SERVO_TWISTER_TOP_LOCATION);



    public static final ResourceLocation WIND_ROTO_VERTICAL_TOP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(com.proventure.twistermill.TwisterMill.MOD_ID, "block/wind_roto_vertical_block_top");

    public static final PartialModel WIND_ROTO_VERTICAL_TOP =
            PartialModel.of(WIND_ROTO_VERTICAL_TOP_LOCATION);




    private TwisterMillPartialModels() {}
}
