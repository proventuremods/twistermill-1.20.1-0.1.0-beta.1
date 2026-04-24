package com.proventure.twistermill.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class TwisterMillConfig {

    public static final ForgeConfigSpec COMMON_SPEC;

    public static final ForgeConfigSpec.DoubleValue SU_FACTOR;
    public static final ForgeConfigSpec.DoubleValue SU_PER_RPM;
    public static final ForgeConfigSpec.IntValue SU_PER_BLOCK;
    public static final ForgeConfigSpec.ConfigValue<Integer> RPM_RAMP_TICKS;
    public static final ForgeConfigSpec.ConfigValue<Integer> WIND_UPDATE_TICKS;
    public static final ForgeConfigSpec.IntValue RPM_RAMP_STEP;
    public static final ForgeConfigSpec.IntValue MAX_RPM;
    public static final ForgeConfigSpec.BooleanValue CHUNK_LOADING_ENABLED;
    public static final ForgeConfigSpec.BooleanValue COMPARATOR_OUTPUT_INVERTED;

    public static final ForgeConfigSpec.ConfigValue<Integer> VERTICAL_WIND_ANGLE_UPDATE_TICKS;
    public static final ForgeConfigSpec.IntValue VERTICAL_MAX_YAW_RPM;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_DEGREES_PER_TICK_AT_1_RPM;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_MAX_YAW_ACCEL_DEG_PER_TICK2;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_YAW_DEADZONE_DEG;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_YAW_STOP_VELOCITY_DEG_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_YAW_MIN_TRACKING_SPEED_DEG_PER_TICK;
    public static final ForgeConfigSpec.DoubleValue VERTICAL_YAW_CONTROLLER_GAIN;

    private static boolean isStep10Ticks(Object v) {
        if (!(v instanceof Integer i)) return false;
        return i >= 10 && i <= 1000 && (i % 10 == 0);
    }

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("TwisterMill settings").push("twistermill");

        b.comment("WindRotoBlockEntity settings").push("Twistermill_bearing");

        SU_FACTOR = b
                .comment("SU multiplier for Twistermill bearing output. Range: 1.0 - 1000.0. Default: 8.0")
                .defineInRange("suFactor", 1.0, 1.0, 1000.0);

        SU_PER_RPM = b
                .comment("Base SU generated per RPM. Range: 2.0 - 128.0. Default: 25.4")
                .defineInRange("suPerRpm", 25.4, 2.0, 128.0);

        SU_PER_BLOCK = b
                .comment("Static extra SU added per assembled contraption block. Range: 1 - 1024. Default: 64")
                .defineInRange("suPerBlock", 64, 1, 1024);

        RPM_RAMP_TICKS = b
                .comment("How often Wind Direction bearing ramps RPM toward target. Range: 10 - 1000 ticks in steps of 10. Default: 20")
                .define("rpmRampTicks", 20, TwisterMillConfig::isStep10Ticks);

        WIND_UPDATE_TICKS = b
                .comment("How often Wind Direction bearing samples wind speed. Range: 10 - 1000 ticks in steps of 10. Default: 40")
                .define("windUpdateTicks", 40, TwisterMillConfig::isStep10Ticks);

        RPM_RAMP_STEP = b
                .comment("Maximum RPM change per ramp update for Wind Direction bearing. Range: 1 - 64. Default: 4")
                .defineInRange("rpmRampStep", 4, 1, 64);

        MAX_RPM = b
                .comment("Maximum RPM for Wind Direction bearing. Range: 10 - 256. Default: 128")
                .defineInRange("maxRPM", 128, 10, 256);

        CHUNK_LOADING_ENABLED = b
                .comment("Enables chunkloading/forceload for Twistermill block entities. Default: false")
                .define("chunkLoadingEnabled", false);

        COMPARATOR_OUTPUT_INVERTED = b
                .comment("Inverts WindRoto comparator output mapping (high RPM -> low signal, low RPM -> high signal). Default: false")
                .define("comparatorOutputInverted", false);

        b.pop();

        b.comment("WindRotoVerticalBlockEntity settings").push("Wind Direction bearing");

        VERTICAL_WIND_ANGLE_UPDATE_TICKS = b
                .comment("How often WindRotoVerticalBlockEntity samples world wind angle. Range: 10 - 1000 ticks in steps of 10. Default: 20")
                .define("verticalWindAngleUpdateTicks", 20, TwisterMillConfig::isStep10Ticks);

        VERTICAL_MAX_YAW_RPM = b
                .comment("Maximum yaw RPM for WindRotoVerticalBlockEntity. Range: 1 - 256. Default: 10")
                .defineInRange("verticalMaxYawRPM", 10, 1, 256);

        VERTICAL_DEGREES_PER_TICK_AT_1_RPM = b
                .comment("Yaw degrees per tick at 1 RPM for WindRotoVerticalBlockEntity. Range: 0.001 - 10.0. Default: 0.1")
                .defineInRange("verticalDegreesPerTickAt1RPM", 0.1, 0.001, 10.0);

        VERTICAL_MAX_YAW_ACCEL_DEG_PER_TICK2 = b
                .comment("Maximum yaw acceleration in degrees per tick squared. Range: 0.0001 - 10.0. Default: 0.04")
                .defineInRange("verticalMaxYawAccelDegPerTick2", 0.04, 0.0001, 10.0);

        VERTICAL_YAW_DEADZONE_DEG = b
                .comment("Yaw deadzone in degrees around the target. Range: 0.0 - 180.0. Default: 3.0")
                .defineInRange("verticalYawDeadzoneDeg", 3.0, 0.0, 180.0);

        VERTICAL_YAW_STOP_VELOCITY_DEG_PER_TICK = b
                .comment("Yaw stop velocity threshold in degrees per tick. Range: 0.0001 - 10.0. Default: 0.025")
                .defineInRange("verticalYawStopVelocityDegPerTick", 0.025, 0.0001, 10.0);

        VERTICAL_YAW_MIN_TRACKING_SPEED_DEG_PER_TICK = b
                .comment("Minimum yaw tracking speed in degrees per tick. Range: 0.0 - 10.0. Default: 0.06")
                .defineInRange("verticalYawMinTrackingSpeedDegPerTick", 0.06, 0.0, 10.0);

        VERTICAL_YAW_CONTROLLER_GAIN = b
                .comment("Yaw controller gain. Range: 0.0 - 10.0. Default: 0.3")
                .defineInRange("verticalYawControllerGain", 0.3, 0.0, 10.0);

        b.pop();
        b.pop();

        COMMON_SPEC = b.build();
    }
}
