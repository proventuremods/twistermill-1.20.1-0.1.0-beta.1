package com.proventure.twistermill.client;

import com.proventure.twistermill.TwisterMill;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

public class TwisterMillPartialModels {

    public static final ResourceLocation WIND_ROTO_TOP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/wind_roto_block_top");

    public static final PartialModel WIND_ROTO_TOP =
            PartialModel.of(WIND_ROTO_TOP_LOCATION);

    public static final ResourceLocation SERVO_TWISTER_TOP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/servo_twister_block_top");

    public static final PartialModel SERVO_TWISTER_TOP =
            PartialModel.of(SERVO_TWISTER_TOP_LOCATION);

    public static final ResourceLocation SERVO_TWISTER_ANTENNA_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/servo_twister_block_antenna");

    public static final PartialModel SERVO_TWISTER_ANTENNA =
            PartialModel.of(SERVO_TWISTER_ANTENNA_LOCATION);

    public static final ResourceLocation INV_SERVO_TWISTER_TOP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/inv_servo_twister_block_top");

    public static final PartialModel INV_SERVO_TWISTER_TOP =
            PartialModel.of(INV_SERVO_TWISTER_TOP_LOCATION);

    public static final ResourceLocation INV_SERVO_TWISTER_ANTENNA_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/inv_servo_twister_block_antenna");

    public static final PartialModel INV_SERVO_TWISTER_ANTENNA =
            PartialModel.of(INV_SERVO_TWISTER_ANTENNA_LOCATION);

    public static final ResourceLocation WIND_ROTO_VERTICAL_TOP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/wind_roto_vertical_block_top");

    public static final PartialModel WIND_ROTO_VERTICAL_TOP =
            PartialModel.of(WIND_ROTO_VERTICAL_TOP_LOCATION);

    public static final ResourceLocation METAL_TRAVERSE_BRACKET_NORTH_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/metal_traverse/bracket_north");

    public static final ResourceLocation METAL_TRAVERSE_BRACKET_SOUTH_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/metal_traverse/bracket_south");

    public static final ResourceLocation METAL_TRAVERSE_BRACKET_EAST_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/metal_traverse/bracket_east");

    public static final ResourceLocation METAL_TRAVERSE_BRACKET_WEST_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/metal_traverse/bracket_west");

    public static final ResourceLocation METAL_TRAVERSE_BRACKET_UP_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/metal_traverse/bracket_up");

    public static final ResourceLocation METAL_TRAVERSE_BRACKET_DOWN_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "block/metal_traverse/bracket_down");

    public static final ResourceLocation METAL_TRAVERSE_POLE_HIDE_WS_CORNER_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID,
                    "block/metal_traverse/block_pole_hide_ws_corner");

    public static final ResourceLocation METAL_TRAVERSE_POLE_HIDE_EN_CORNER_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID,
                    "block/metal_traverse/block_pole_hide_en_corner");

    public static final ResourceLocation METAL_TRAVERSE_POLE_Y90_HIDE_WN_CORNER_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID,
                    "block/metal_traverse/block_pole_y90_hide_wn_corner");

    public static final ResourceLocation METAL_TRAVERSE_POLE_Y90_HIDE_ES_CORNER_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID,
                    "block/metal_traverse/block_pole_y90_hide_es_corner");

    public static final ResourceLocation METAL_TRAVERSE_POLE_Y180_HIDE_WS_CORNER_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID,
                    "block/metal_traverse/block_pole_y180_hide_ws_corner");

    public static final ResourceLocation METAL_TRAVERSE_POLE_Y180_HIDE_EN_CORNER_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID,
                    "block/metal_traverse/block_pole_y180_hide_en_corner");

    public static final ResourceLocation METAL_TRAVERSE_POLE_Y270_HIDE_WN_CORNER_LOCATION =
            METAL_TRAVERSE_POLE_Y90_HIDE_WN_CORNER_LOCATION;

    public static final ResourceLocation METAL_TRAVERSE_POLE_Y270_HIDE_ES_CORNER_LOCATION =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID,
                    "block/metal_traverse/block_pole_y270_hide_es_corner");

    public static ResourceLocation getMetalTraverseBracketLocation(Direction direction) {
        return switch (direction) {
            case NORTH -> METAL_TRAVERSE_BRACKET_NORTH_LOCATION;
            case SOUTH -> METAL_TRAVERSE_BRACKET_SOUTH_LOCATION;
            case EAST -> METAL_TRAVERSE_BRACKET_EAST_LOCATION;
            case WEST -> METAL_TRAVERSE_BRACKET_WEST_LOCATION;
            case UP -> METAL_TRAVERSE_BRACKET_UP_LOCATION;
            case DOWN -> METAL_TRAVERSE_BRACKET_DOWN_LOCATION;
        };
    }

    private TwisterMillPartialModels() {}
}
