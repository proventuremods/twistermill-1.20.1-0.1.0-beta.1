package com.proventure.twistermill.config;

import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

public class TwisterMillConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final String CONTENT_SHOW_BLADE_ARM_BLOCK = "showBladeArmBlock";
    public static final String CONTENT_SHOW_BLADE_ARM_EASTFACE_BLOCK = "showBladeArmEastfaceBlock";
    public static final String CONTENT_SHOW_BLADE_ARM_WESTFACE_BLOCK = "showBladeArmWestfaceBlock";
    public static final String CONTENT_SHOW_METAL_TRAVERSE = "showAluminumTruss";
    public static final String CONTENT_SHOW_NOSTALGIC_GRASS_BLOCK = "showNostalgicGrassBlock";
    private static final int DEFAULT_WIND_ROTO_VERTICAL_PULSE_MAX_TICKS = 25;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_PULSE_COOLDOWN_TICKS = 60;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_SU_PER_RPM = 12.8D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_SERVO_STIFFNESS_PER_INERTIA = 1600.0D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_SERVO_DAMPING_PER_INERTIA = 40.0D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_MIN_EFFECTIVE_INERTIA = 10.0D;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_MAX_YAW_RPM = 6;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_YAW_DEADZONE_DEG = 3.6D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_YAW_TARGET_OFFSET_DEG = 0.0D;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_PULSE_MIN_TICKS = 2;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_WIND_ANGLE_UPDATE_TICKS = 60;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_DISASSEMBLED = 30;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_ASSEMBLED = 60;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_YAW_CONTROLLER_GAIN = 0.24D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_MAX_YAW_ACCEL_DEG_PER_TICK2 = 0.08D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_YAW_MIN_TRACKING_SPEED_DEG_PER_TICK = 0.045D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_YAW_STOP_VELOCITY_DEG_PER_TICK = 0.025D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_PARK_ZERO_SNAP_EPSILON_DEG = 0.0001D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_DISASSEMBLE_RETURN_DEGREES_PER_TICK = 1.0D;
    private static final double DEFAULT_WIND_ROTO_VERTICAL_DISASSEMBLE_ZERO_EPSILON_DEG = 0.5D;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_DISASSEMBLE_STABLE_TICKS = 2;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_SABLE_LOAD_RECOVERY_TICKS = 100;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_GRACE_TICKS = 1200;
    private static final int DEFAULT_WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_LOG_INTERVAL_TICKS = 200;

    public static final ModConfigSpec.DoubleValue SU_FACTOR;
    public static final ModConfigSpec.IntValue SU_PER_BLOCK;
    public static final ModConfigSpec.IntValue SERVO_ANGLE_SU_MAX_MULTIPLIER;
    public static final ModConfigSpec.ConfigValue<Integer> RPM_RAMP_TICKS;
    public static final ModConfigSpec.ConfigValue<Integer> WIND_UPDATE_TICKS;
    public static final ModConfigSpec.IntValue RPM_RAMP_STEP;
    public static final ModConfigSpec.IntValue WEATHER2_MAX_RPM;
    public static final ModConfigSpec.IntValue PMWEATHER_MAX_RPM;
    public static final ModConfigSpec.BooleanValue INVERT_WIND_ROTO_COMPARATOR;
    public static final ModConfigSpec.IntValue ALLOWED_BLOCKS_ABOVE_FOR_OUTSIDE;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVO_TWISTER_BINARY_INPUT;
    public static final ModConfigSpec.BooleanValue ENABLE_INV_SERVO_TWISTER_BINARY_INPUT;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVO_SPEED_ZERO_MOVEMENT;
    public static final ModConfigSpec.BooleanValue ENABLE_INV_SERVO_SPEED_ZERO_MOVEMENT;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVO_SLOT_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_WIND_ROTO_BLOCK_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_WIND_ROTO_VERTICAL_BLOCK_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVO_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue ENABLE_INV_SERVO_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue AUTO_RESEAT_WIND_ROTO_VERTICAL_BLOCK_ON_LOAD;
    public static final ModConfigSpec.BooleanValue AUTO_RESEAT_WIND_ROTO_BLOCK_ON_LOAD;
    public static final ModConfigSpec.BooleanValue AUTO_RESEAT_SERVO_ON_LOAD;
    public static final ModConfigSpec.BooleanValue AUTO_RESEAT_INV_SERVO_ON_LOAD;
    public static final ModConfigSpec.BooleanValue SHOW_METAL_TRAVERSE_DEBUG_OVERLAY;
    public static final ModConfigSpec.BooleanValue GENERATE_ORES_IN_WORLD;
    public static final ModConfigSpec.BooleanValue SHOW_BLADE_ARM_BLOCK;
    public static final ModConfigSpec.BooleanValue SHOW_BLADE_ARM_EASTFACE_BLOCK;
    public static final ModConfigSpec.BooleanValue SHOW_BLADE_ARM_WESTFACE_BLOCK;
    public static final ModConfigSpec.BooleanValue SHOW_METAL_TRAVERSE;
    public static final ModConfigSpec.BooleanValue SHOW_NOSTALGIC_GRASS_BLOCK;
    public static final ModConfigSpec.BooleanValue ENABLE_NETHERITE_ADVANCEMENT_DROP;
    public static final ModConfigSpec.ConfigValue<String> ADVANCEMENT_DROP_ITEM;
    public static final ModConfigSpec.IntValue ADVANCEMENT_DROP_COUNT;
    public static final ModConfigSpec.BooleanValue SHOW_MASS_TOOLTIP;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_SU_PER_RPM;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_SERVO_STIFFNESS_PER_INERTIA;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_SERVO_DAMPING_PER_INERTIA;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_MIN_EFFECTIVE_INERTIA;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_MAX_YAW_RPM;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_YAW_DEADZONE_DEG;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_YAW_TARGET_OFFSET_DEG;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_PULSE_MIN_TICKS;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_PULSE_MAX_TICKS;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_PULSE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_WIND_ANGLE_UPDATE_TICKS;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_DISASSEMBLED;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_ASSEMBLED;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_YAW_CONTROLLER_GAIN;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_MAX_YAW_ACCEL_DEG_PER_TICK2;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_YAW_MIN_TRACKING_SPEED_DEG_PER_TICK;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_YAW_STOP_VELOCITY_DEG_PER_TICK;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_PARK_ZERO_SNAP_EPSILON_DEG;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_DISASSEMBLE_RETURN_DEGREES_PER_TICK;
    public static final ModConfigSpec.DoubleValue WIND_ROTO_VERTICAL_DISASSEMBLE_ZERO_EPSILON_DEG;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_DISASSEMBLE_STABLE_TICKS;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_SABLE_LOAD_RECOVERY_TICKS;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_GRACE_TICKS;
    public static final ModConfigSpec.IntValue WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_LOG_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SAIL_PLACEMENT_ASSIST_RANGE;
    public static final ModConfigSpec.BooleanValue ENABLE_SAIL_WIND_FORCE;
    private static final ModConfigSpec.BooleanValue SMOOTH_SAIL_FORCE_UPDATES;
    private static final ModConfigSpec.DoubleValue SAIL_FORCE_SMOOTHING_STRENGTH;
    public static final ModConfigSpec.BooleanValue SHOW_SAIL_FORCE_VECTORS;
    public static final ModConfigSpec.BooleanValue ENABLE_SAIL_WIND_DIAGNOSTICS;
    public static final ModConfigSpec.IntValue PEAK_EFFICIENCY_ROTOR_BLADES;
    public static final ModConfigSpec.IntValue SAIL_PEAK_EFFICIENCY_PITCH_DEGREES;
    public static final ModConfigSpec.IntValue SAIL_WIND_DIAGNOSTIC_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue SAIL_WIND_FORCE_COEFFICIENT;
    public static final ModConfigSpec.DoubleValue SAIL_WIND_MIN_EXPOSURE;
    public static final ModConfigSpec.DoubleValue SAIL_WIND_MAX_FORCE_PER_BLOCK;
    public static final ModConfigSpec.DoubleValue SAIL_WIND_MAX_FORCE_PER_OBJECT;
    public static final ModConfigSpec.BooleanValue ENABLE_WIND_ROTO_CONTRAPTION_MEMORY;
    public static final ModConfigSpec.BooleanValue ENABLE_WIND_ROTO_VERTICAL_CONTRAPTION_MEMORY;
    public static final ModConfigSpec.BooleanValue ENABLE_SERVO_TWISTER_CONTRAPTION_MEMORY;
    public static final ModConfigSpec.BooleanValue ENABLE_INV_SERVO_TWISTER_CONTRAPTION_MEMORY;
    private static final ModConfigSpec.DoubleValue SERVO_STIFFNESS_PER_INERTIA;
    private static final ModConfigSpec.DoubleValue SERVO_DAMPING_PER_INERTIA;
    private static final ModConfigSpec.DoubleValue MODE_7_DISASSEMBLY_RETURN_MOTOR_STRENGTH_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER_MODES_1_TO_3;
    private static final ModConfigSpec.DoubleValue SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER_MODES_4_TO_6;
    private static final ModConfigSpec.DoubleValue PROPELLER_SLOT_SERVO_STIFFNESS_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue PROPELLER_SLOT_SERVO_DAMPING_MULTIPLIER;
    private static final ModConfigSpec.DoubleValue FREE_BEARING_DAMPING_PER_INERTIA;
    private static final ModConfigSpec.DoubleValue SERVO_MIN_EFFECTIVE_INERTIA;
    private static final ModConfigSpec.IntValue BLADE_ARM_BLOCK_MASS;
    private static final ModConfigSpec.IntValue BLADE_ARM_EASTFACE_BLOCK_MASS;
    private static final ModConfigSpec.IntValue BLADE_ARM_WESTFACE_BLOCK_MASS;

    private static boolean isStep10Ticks(Object v) {
        if (!(v instanceof Integer i)) return false;
        return i >= 10 && i <= 1000 && (i % 10 == 0);
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("TwisterMill settings").push("twistermill");

        b.comment("Twistermill Bearing settings").push("twistermill_bearing");

        SU_FACTOR = b
                .comment("SU multiplier for Twistermill Bearing output. Range: 0.1 - 100.0. Default: 1.0")
                .defineInRange("suFactor", 1.0, 0.1, 100.0);

        SU_PER_BLOCK = b
                .comment("Static extra SU added per assembled contraption block. Range: 1 - 1024. Default: 8")
                .defineInRange("suPerBlock", 8, 1, 1024);

        SERVO_ANGLE_SU_MAX_MULTIPLIER = b
                .comment("Maximum Twistermill Bearing SU multiplier reached at average bound servo angle 45°. Range: 1 - 4. Default: 1")
                .defineInRange("servoAngleSuMaxMultiplier", 1, 1, 4);

        RPM_RAMP_TICKS = b
                .comment("How often Twistermill Bearing ramps RPM toward target. Range: 10 - 1000 ticks in steps of 10. Default: 200")
                .define("rpmRampTicks", 20, TwisterMillConfig::isStep10Ticks);

        WIND_UPDATE_TICKS = b
                .comment("How often Twistermill Bearing samples wind speed. Range: 10 - 1000 ticks in steps of 10. Default: 40")
                .define("windUpdateTicks", 10, TwisterMillConfig::isStep10Ticks);

        RPM_RAMP_STEP = b
                .comment("Maximum RPM change per ramp update for Twistermill Bearing. Range: 1 - 64. Default: 4")
                .defineInRange("rpmRampStep", 8, 1, 64);

        WEATHER2_MAX_RPM = b
                .comment("Maximum RPM for Twistermill Bearing when Weather2 is the active weather backend. Range: 10 - 256. Default: 64")
                .defineInRange("weather2MaxRPM", 64, 10, 256);

        PMWEATHER_MAX_RPM = b
                .comment("Maximum RPM for Twistermill Bearing when PMWeather is the active weather backend. Range: 10 - 256. Default: 32")
                .defineInRange("pmweatherMaxRPM", 32, 10, 256);

        INVERT_WIND_ROTO_COMPARATOR = b
                .comment("Invert Twistermill Bearing redstone comparator output (0..15) after normal calculation. Default: false")
                .define("invertTwistermillBearingComparator", false);

        ALLOWED_BLOCKS_ABOVE_FOR_OUTSIDE = b
                .comment("Maximum number of blocking blocks above a Twistermill Bearing that still count as outside. 0 disables the Weather Bearing sky obstruction check. Range: 0 - 10. Default: 3")
                .defineInRange("allowedBlocksAboveForOutside", 3, 0, 10);

        b.pop();

        b.comment("Weather Sail settings").push("sails");

        SAIL_PLACEMENT_ASSIST_RANGE = b
                .comment("Maximum number of blocks Weather Sail placement assist may search in the selected direction. Range: 1 - 10. Default: 3")
                .defineInRange("sail_placement_assist_range", 3, 1, 10);

        ENABLE_SAIL_WIND_FORCE = b
                .comment("Enable Weather Sail wind force contribution to Sable physics. Default: true.")
                .define("enable_sail_wind_force", true);

        SMOOTH_SAIL_FORCE_UPDATES = b
                .comment("Smooth TwisterMill Weather Sail force changes over Sable physics substeps. Default: false.")
                .define("smooth_sail_force_updates", false);

        SAIL_FORCE_SMOOTHING_STRENGTH = b
                .comment("Weather Sail force smoothing strength. Lower values react faster; higher values smooth more strongly. Range: 0.1 - 10.0. Default: 1.0.")
                .defineInRange("sail_force_smoothing_strength", 1.0D, 0.1D, 10.0D);

        SHOW_SAIL_FORCE_VECTORS = b
                .comment("Show the red incoming-wind force vector and green resulting pitch-force vector for Weather Sails when Sail wind force is enabled. This controls rendering only and does not change physics. Default: false.")
                .define("enable_sail_wind_force_vectors", false);

        ENABLE_SAIL_WIND_DIAGNOSTICS = b
                .comment("Enable periodic diagnostics for Weather Sail wind sampling and computed force. Default: false.")
                .define("enable_sail_wind_diagnostics", false);

        PEAK_EFFICIENCY_ROTOR_BLADES = b
                .comment("Maximum Sail force conversion from incoming wind into tangential rotor-driving force (Peak-Efficiency / Force Redirection). This is the peak green/blue force ratio and is fully configurable. Range: 10–200%. Default: 80%.")
                .defineInRange("peak_efficiency_rotor_blades", 80, 10, 200);

        SAIL_PEAK_EFFICIENCY_PITCH_DEGREES = b
                .comment("Pitch angle in degrees at which the maximal Sail peak-efficiency is reached. Range: 1–89. Default: 50.")
                .defineInRange("sail_peak_efficiency_pitch_degrees", 50, 1, 89);

        SAIL_WIND_DIAGNOSTIC_INTERVAL_TICKS = b
                .comment("Diagnostic log interval for Twister Sail wind diagnostics. Range: 10 - 1200 ticks. Default: 100.")
                .defineInRange("sail_wind_diagnostic_interval_ticks", 100, 10, 1200);

        SAIL_WIND_FORCE_COEFFICIENT = b
                .comment("Wind force coefficient for Weather Sail physics. Range: 0.0 - 10.0. Default: 1.0.")
                .defineInRange("sail_wind_force_coefficient", 1.0D, 0.0D, 10.0D);

        SAIL_WIND_MIN_EXPOSURE = b
                .comment("Minimum exposure threshold below which force is ignored. Range: 0.0 - 1.0. Default: 0.1.")
                .defineInRange("sail_wind_min_exposure", 0.1D, 0.0D, 1.0D);

        SAIL_WIND_MAX_FORCE_PER_BLOCK = b
                .comment("Maximum force magnitude per Weather Sail block contribution. Range: 0.0 - 1000.0. Default: 5.0.")
                .defineInRange("sail_wind_max_force_per_block", 5.0D, 0.0D, 1000.0D);

        SAIL_WIND_MAX_FORCE_PER_OBJECT = b
                .comment("Maximum total force magnitude per physics object from Weather Sail contributions. Range: 0.0 - 5000.0. Default: 60.0.")
                .defineInRange("sail_wind_max_force_per_object", 60.0D, 0.0D, 5000.0D);

        b.pop();

        b.comment("Ship Contraptions settings").push("ship_contraptions");

        ENABLE_WIND_ROTO_CONTRAPTION_MEMORY = b
                .comment("Enable remembered contraption memory for Weather Bearings. Default: true.")
                .define("enable_weather_bearing_contraption_memory", true);

        ENABLE_WIND_ROTO_VERTICAL_CONTRAPTION_MEMORY = b
                .comment("Enable remembered contraption memory for Windvane Bearings. Default: true.")
                .define("enable_windvane_bearing_contraption_memory", true);

        ENABLE_SERVO_TWISTER_CONTRAPTION_MEMORY = b
                .comment("Enable remembered contraption memory for Servo Bearings. Default: true.")
                .define("enable_servo_bearing_contraption_memory", true);

        ENABLE_INV_SERVO_TWISTER_CONTRAPTION_MEMORY = b
                .comment("Enable remembered contraption memory for Inverted Servo Bearings. Default: true.")
                .define("enable_inverted_servo_bearing_contraption_memory", true);

        BLADE_ARM_BLOCK_MASS = b
                .comment("Sable mass of each Blade Arm block. Requires a world/server restart and reassembly. Range: 1 - 200. Default: 33.")
                .worldRestart()
                .defineInRange("blade_arm_block_mass", 33, 1, 200);

        BLADE_ARM_EASTFACE_BLOCK_MASS = b
                .comment("Sable mass of each Blade Arm East Face block. Requires a world/server restart and reassembly. Range: 1 - 200. Default: 33.")
                .worldRestart()
                .defineInRange("blade_arm_eastface_block_mass", 33, 1, 200);

        BLADE_ARM_WESTFACE_BLOCK_MASS = b
                .comment("Sable mass of each Blade Arm West Face block. Requires a world/server restart and reassembly. Range: 1 - 200. Default: 33.")
                .worldRestart()
                .defineInRange("blade_arm_westface_block_mass", 33, 1, 200);

        AUTO_RESEAT_WIND_ROTO_VERTICAL_BLOCK_ON_LOAD = b
                .comment("Automatically reseat loaded Windvane Bearing Sable attachments after server/world load and chunk load. Default: false.")
                .define("auto_reseat_windvane_bearing_on_load", false);

        AUTO_RESEAT_WIND_ROTO_BLOCK_ON_LOAD = b
                .comment("Automatically reseat loaded Weather Bearing Sable attachments after server/world load and chunk load. Default: false.")
                .define("auto_reseat_weather_bearing_on_load", false);

        AUTO_RESEAT_SERVO_ON_LOAD = b
                .comment("Automatically reseat loaded Servo Bearing Sable attachments after server/world load and chunk load. Default: false.")
                .define("auto_reseat_servo_on_load", false);

        AUTO_RESEAT_INV_SERVO_ON_LOAD = b
                .comment("Automatically reseat loaded Inverted Servo Bearing Sable attachments after server/world load and chunk load. Default: false.")
                .define("auto_reseat_inverted_servo_on_load", false);

        b.pop();

        b.comment("Worldgen settings").push("worldgen");

        GENERATE_ORES_IN_WORLD = b
                .comment("Enable Signal Quartz Ore generation for this world. Once enabled in-world, it stays enabled for that world.")
                .translation("twistermill.configgui.generateOresInWorld")
                .define("generate_ores_in_world", false);

        b.pop();

        b.comment("Experimental content visibility and recipe availability. Registry entries always remain available for existing worlds.").push("experimental content");

        SHOW_BLADE_ARM_BLOCK = b
                .comment("Show the Blade Arm block in the TwisterMill creative tab and enable matching recipes when present. Default: false")
                .translation("twistermill.configgui.showBladeArmBlock")
                .define(CONTENT_SHOW_BLADE_ARM_BLOCK, false);

        SHOW_BLADE_ARM_EASTFACE_BLOCK = b
                .comment("Show the Blade Arm East Face block in the TwisterMill creative tab and enable matching recipes when present. Default: false")
                .translation("twistermill.configgui.showBladeArmEastfaceBlock")
                .define(CONTENT_SHOW_BLADE_ARM_EASTFACE_BLOCK, false);

        SHOW_BLADE_ARM_WESTFACE_BLOCK = b
                .comment("Show the Blade Arm West Face block in the TwisterMill creative tab and enable matching recipes when present. Default: false")
                .translation("twistermill.configgui.showBladeArmWestfaceBlock")
                .define(CONTENT_SHOW_BLADE_ARM_WESTFACE_BLOCK, false);

        SHOW_METAL_TRAVERSE = b
                .comment("Show Aluminum Truss in the TwisterMill creative tab and enable its recipes. Default: true")
                .translation("twistermill.configgui.showAluminumTruss")
                .define(CONTENT_SHOW_METAL_TRAVERSE, true);

        SHOW_NOSTALGIC_GRASS_BLOCK = b
                .comment("Show the Nostalgic Grass Block in the TwisterMill creative tab and enable its recipes. Default: false")
                .translation("twistermill.configgui.showNostalgicGrassBlock")
                .define(CONTENT_SHOW_NOSTALGIC_GRASS_BLOCK, false);

        b.pop();

        b.comment("Drop and reward settings").push("drops");

        ENABLE_NETHERITE_ADVANCEMENT_DROP = b
                .comment("Give the configured reward when the Binary Code Transmitter advancement is completed. Default: false")
                .define("enable_netherite_advancement_drop", false);

        ADVANCEMENT_DROP_ITEM = b
                .comment("Registered item ID used for the Binary Code Transmitter advancement reward. Default: minecraft:ancient_debris")
                .define("advancement_drop_item", "minecraft:ancient_debris");

        ADVANCEMENT_DROP_COUNT = b
                .comment("Item count used for the Binary Code Transmitter advancement reward. Runtime range: 1 - 64. Default: 6")
                .defineInRange("advancement_drop_count", 6, Integer.MIN_VALUE, Integer.MAX_VALUE);

        b.pop();

        b.comment("Tooltip settings").push("tooltips");

        SHOW_MASS_TOOLTIP = b
                .comment("Is item mass tooltip active? Default: true")
                .define("showMassTooltip", true);

        b.pop();

        b.comment("Servo Bearings settings").push("servo_bearings");

        ENABLE_SERVO_TWISTER_BINARY_INPUT = b
                .comment("Enable binary frame input decoding on Servo Bearing opposite-facing side. Default: true")
                .translation("twistermill.configgui.enableServoBearingBinaryInput")
                .define("enable_servo_bearing_binary_input", true);

        ENABLE_INV_SERVO_TWISTER_BINARY_INPUT = b
                .comment("Enable binary frame input decoding on Inverted Servo Bearing opposite-facing side. Default: true")
                .translation("twistermill.configgui.enableInvertedServoBearingBinaryInput")
                .define("enable_inverted_servo_bearing_binary_input", true);

        ENABLE_SERVO_SPEED_ZERO_MOVEMENT = b
                .comment("When enabled, Servo Bearing speed signal 0 maps to 0.1 degrees/tick. When disabled, speed signal 0 maps to 0 degrees/tick. Default: false")
                .define("enable_servo_speed_zero_movement", false);

        ENABLE_INV_SERVO_SPEED_ZERO_MOVEMENT = b
                .comment("When enabled, Inverted Servo Bearing speed signal 0 maps to 0.1 degrees/tick. When disabled, speed signal 0 maps to 0 degrees/tick. Default: false")
                .define("enable_inv_servo_speed_zero_movement", false);

        ENABLE_SERVO_SLOT_DIAGNOSTICS = b
                .comment("Enable extra diagnostics/logging/troubleshooting for Servo/Inverted Servo Bearing Sable and Mode 7 blade-anchor slots. Keep disabled during normal gameplay. Default: false.")
                .define("enable_servo_slot_diagnostics", false);

        SERVO_STIFFNESS_PER_INERTIA = b
                .comment("Sable motor stiffness per effective inertia for Servo and Inverted Servo Bearings. Requires a world/server restart. Range: 0.0 - 10000000.0. Default: 1600.0.")
                .worldRestart()
                .defineInRange("servo_stiffness_per_inertia", 1600.0D, 0.0D, 10000000.0D);

        SERVO_DAMPING_PER_INERTIA = b
                .comment("Sable motor damping per effective inertia for Servo and Inverted Servo Bearings. Requires a world/server restart. Range: 0.0 - 1000000.0. Default: 40.0.")
                .worldRestart()
                .defineInRange("servo_damping_per_inertia", 40.0D, 0.0D, 1000000.0D);

        MODE_7_DISASSEMBLY_RETURN_MOTOR_STRENGTH_MULTIPLIER = b
                .comment("Multiplier for Servo Bearing GUI mode 7 motor stiffness during requested disassembly return-to-zero and physical zero confirmation for all block facings; damping is multiplied by the square root of this value. Requires a world/server restart. Range: 1.0 - 100.0. Default: 4.0.")
                .worldRestart()
                .defineInRange("mode_7_disassembly_return_motor_strength_multiplier", 4.0D, 1.0D, 100.0D);

        SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER = b
                .comment("Multiplier for the commanded Servo Bearing return-to-zero speed during requested disassembly in GUI mode 7. Applies to the full return curve, including its minimum step, and does not change motor or physical safety thresholds. Requires a world/server restart. Range: 0.05 - 10.0. Default: 5.0.")
                .worldRestart()
                .defineInRange("disassembly_return_speed_multiplier", 5.0D, 0.05D, 10.0D);

        SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER_MODES_1_TO_3 = b
                .comment("Multiplier for the commanded Servo and Inverted Servo Bearing return-to-zero speed during requested disassembly in GUI modes 1 to 3. Applies to the full return curve, including its minimum step, and does not change motor or physical safety thresholds. Requires a world/server restart. Range: 0.05 - 10.0. Default: 5.0.")
                .worldRestart()
                .defineInRange("disassembly_return_speed_multiplier_modes_1_to_3", 5.0D, 0.05D, 10.0D);

        SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER_MODES_4_TO_6 = b
                .comment("Multiplier for the commanded Servo and Inverted Servo Bearing return-to-zero speed during requested disassembly in GUI modes 4 to 6. Applies to the full return curve, including its minimum step, and does not change motor or physical safety thresholds. Requires a world/server restart. Range: 0.05 - 10.0. Default: 5.0.")
                .worldRestart()
                .defineInRange("disassembly_return_speed_multiplier_modes_4_to_6", 5.0D, 0.05D, 10.0D);

        PROPELLER_SLOT_SERVO_STIFFNESS_MULTIPLIER = b
                .comment("Multiplier applied to Servo Bearing motor stiffness while mounted on an existing propeller-slot sublevel. Requires a world/server restart. Range: 0.0 - 100.0. Default: 4.0.")
                .worldRestart()
                .defineInRange("propeller_slot_servo_stiffness_multiplier", 4.0D, 0.0D, 100.0D);

        PROPELLER_SLOT_SERVO_DAMPING_MULTIPLIER = b
                .comment("Multiplier applied to Servo Bearing motor damping while mounted on an existing propeller-slot sublevel. Requires a world/server restart. Range: 0.0 - 100.0. Default: 4.0.")
                .worldRestart()
                .defineInRange("propeller_slot_servo_damping_multiplier", 4.0D, 0.0D, 100.0D);

        FREE_BEARING_DAMPING_PER_INERTIA = b
                .comment("Sable motor damping per effective inertia in Free Bearing mode for Servo and Inverted Servo Bearings; stiffness remains 0.0. Requires a world/server restart. Range: 0.0 - 1000.0. Default: 0.03.")
                .worldRestart()
                .defineInRange("free_bearing_damping_per_inertia", 0.03D, 0.0D, 1000.0D);

        SERVO_MIN_EFFECTIVE_INERTIA = b
                .comment("Minimum effective inertia used when scaling Servo and Inverted Servo Bearing Sable motors. Requires a world/server restart. Range: 0.0001 - 1000000.0. Default: 10.0.")
                .worldRestart()
                .defineInRange("min_effective_inertia", 10.0D, 0.0001D, 1000000.0D);

        b.pop();

        b.comment("Diagnostics logging settings. Keep disabled during normal gameplay. Runtime commands can temporarily enable each category without changing this file.").push("diagnostics");

        ENABLE_WIND_ROTO_BLOCK_DIAGNOSTICS = b
                .comment("Enable diagnostics logging for Weather Bearing. Default: false.")
                .define("enable_weather_bearing_diagnostics", false);

        ENABLE_WIND_ROTO_VERTICAL_BLOCK_DIAGNOSTICS = b
                .comment("Enable diagnostics logging for Windvane Bearing. Default: false.")
                .define("enable_windvane_bearing_diagnostics", false);

        ENABLE_SERVO_DIAGNOSTICS = b
                .comment("Enable diagnostics logging for Servo Bearing. Default: false.")
                .define("enable_servo_diagnostics", false);

        ENABLE_INV_SERVO_DIAGNOSTICS = b
                .comment("Enable diagnostics logging for Inverted Servo Bearing. Default: false.")
                .define("enable_inverted_servo_diagnostics", false);

        SHOW_METAL_TRAVERSE_DEBUG_OVERLAY = b
                .comment("Show detailed Aluminum Truss model, blockstate, and tag lines in the F3 Targeted Block debug overlay. Default: false.")
                .define("show_aluminum_truss_debug_overlay", false);

        b.pop();

        b.comment("Windvane Bearing settings").push("windvane_bearing");

        WIND_ROTO_VERTICAL_SU_PER_RPM = b
                .comment("Stress capacity generated per Windvane Bearing RPM before the global SU factor is applied. Range: 0.0 - 100000.0. Default: 12.8")
                .defineInRange("su_per_rpm", DEFAULT_WIND_ROTO_VERTICAL_SU_PER_RPM, 0.0D, 100000.0D);

        WIND_ROTO_VERTICAL_SERVO_STIFFNESS_PER_INERTIA = b
                .comment("Sable motor stiffness multiplier per effective inertia for Windvane Bearing top rotation. Range: 0.0 - 10000000.0. Default: 1600.0")
                .defineInRange("servo_stiffness_per_inertia", DEFAULT_WIND_ROTO_VERTICAL_SERVO_STIFFNESS_PER_INERTIA, 0.0D, 10000000.0D);

        WIND_ROTO_VERTICAL_SERVO_DAMPING_PER_INERTIA = b
                .comment("Sable motor damping multiplier per effective inertia for Windvane Bearing top rotation. Range: 0.0 - 1000000.0. Default: 40.0")
                .defineInRange("servo_damping_per_inertia", DEFAULT_WIND_ROTO_VERTICAL_SERVO_DAMPING_PER_INERTIA, 0.0D, 1000000.0D);

        WIND_ROTO_VERTICAL_MIN_EFFECTIVE_INERTIA = b
                .comment("Minimum effective inertia used when scaling the Windvane Bearing Sable motor. Range: 0.0001 - 1000000.0. Default: 10.0")
                .defineInRange("min_effective_inertia", DEFAULT_WIND_ROTO_VERTICAL_MIN_EFFECTIVE_INERTIA, 0.0001D, 1000000.0D);

        WIND_ROTO_VERTICAL_MAX_YAW_RPM = b
                .comment("Maximum yaw RPM commanded by Windvane Bearing wind tracking. 0 disables commanded yaw motion. Range: 0 - 256. Default: 6")
                .defineInRange("max_yaw_rpm", DEFAULT_WIND_ROTO_VERTICAL_MAX_YAW_RPM, 0, 256);

        WIND_ROTO_VERTICAL_YAW_DEADZONE_DEG = b
                .comment("Yaw target deadzone in degrees for Windvane Bearing tracking. Range: 0.0 - 180.0. Default: 3.6")
                .defineInRange("yaw_deadzone_deg", DEFAULT_WIND_ROTO_VERTICAL_YAW_DEADZONE_DEG, 0.0D, 180.0D);

        WIND_ROTO_VERTICAL_YAW_TARGET_OFFSET_DEG = b
                .comment("Offset applied to the Windvane Bearing wind target in local degrees. Range: -180.0 - 180.0. Default: 0.0")
                .defineInRange("yaw_target_offset_deg", DEFAULT_WIND_ROTO_VERTICAL_YAW_TARGET_OFFSET_DEG, -180.0D, 180.0D);

        WIND_ROTO_VERTICAL_PULSE_MIN_TICKS = b
                .comment("Minimum tick length for an all-sides Windvane Bearing pulse sequence 0 -> 15 -> 0 to toggle park mode. Range: 1 - 200 ticks. Default: 2")
                .defineInRange("pulse_min_ticks", DEFAULT_WIND_ROTO_VERTICAL_PULSE_MIN_TICKS, 1, 200);

        WIND_ROTO_VERTICAL_PULSE_MAX_TICKS = b
                .comment("Maximum tick window for an all-sides Windvane Bearing pulse sequence 0 -> 15 -> 0 to toggle park mode. Range: 10 - 60 ticks. Default: 25")
                .defineInRange("windvane_bearing_pulse_max_ticks", DEFAULT_WIND_ROTO_VERTICAL_PULSE_MAX_TICKS, 10, 60);

        WIND_ROTO_VERTICAL_PULSE_COOLDOWN_TICKS = b
                .comment("Cooldown after a Windvane Bearing pulse action. Effective minimum is windvane_bearing_pulse_max_ticks + 10 ticks. Range: 20 - 200 ticks. Default: 60")
                .defineInRange("windvane_bearing_pulse_cooldown_ticks", DEFAULT_WIND_ROTO_VERTICAL_PULSE_COOLDOWN_TICKS, 20, 200);

        WIND_ROTO_VERTICAL_WIND_ANGLE_UPDATE_TICKS = b
                .comment("How often Windvane Bearing samples the world wind angle. Range: 1 - 12000 ticks. Default: 60")
                .defineInRange("wind_angle_update_ticks", DEFAULT_WIND_ROTO_VERTICAL_WIND_ANGLE_UPDATE_TICKS, 1, 12000);

        WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_DISASSEMBLED = b
                .comment("How often a disassembled Windvane Bearing refreshes placement marker status. Range: 1 - 12000 ticks. Default: 30")
                .defineInRange("placement_status_refresh_ticks_disassembled", DEFAULT_WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_DISASSEMBLED, 1, 12000);

        WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_ASSEMBLED = b
                .comment("How often an assembled Windvane Bearing refreshes placement marker status. Range: 1 - 12000 ticks. Default: 60")
                .defineInRange("placement_status_refresh_ticks_assembled", DEFAULT_WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_ASSEMBLED, 1, 12000);

        WIND_ROTO_VERTICAL_YAW_CONTROLLER_GAIN = b
                .comment("Proportional yaw controller gain for Windvane Bearing tracking. Range: 0.0 - 10.0. Default: 0.24")
                .defineInRange("yaw_controller_gain", DEFAULT_WIND_ROTO_VERTICAL_YAW_CONTROLLER_GAIN, 0.0D, 10.0D);

        WIND_ROTO_VERTICAL_MAX_YAW_ACCEL_DEG_PER_TICK2 = b
                .comment("Maximum yaw acceleration in degrees per tick squared. Range: 0.0 - 360.0. Default: 0.08")
                .defineInRange("max_yaw_accel_deg_per_tick2", DEFAULT_WIND_ROTO_VERTICAL_MAX_YAW_ACCEL_DEG_PER_TICK2, 0.0D, 360.0D);

        WIND_ROTO_VERTICAL_YAW_MIN_TRACKING_SPEED_DEG_PER_TICK = b
                .comment("Minimum non-zero yaw tracking speed in degrees per tick. Range: 0.0 - 360.0. Default: 0.045")
                .defineInRange("yaw_min_tracking_speed_deg_per_tick", DEFAULT_WIND_ROTO_VERTICAL_YAW_MIN_TRACKING_SPEED_DEG_PER_TICK, 0.0D, 360.0D);

        WIND_ROTO_VERTICAL_YAW_STOP_VELOCITY_DEG_PER_TICK = b
                .comment("Yaw velocity threshold below which Windvane Bearing counts as stopped. Range: 0.0 - 360.0. Default: 0.025")
                .defineInRange("yaw_stop_velocity_deg_per_tick", DEFAULT_WIND_ROTO_VERTICAL_YAW_STOP_VELOCITY_DEG_PER_TICK, 0.0D, 360.0D);

        WIND_ROTO_VERTICAL_PARK_ZERO_SNAP_EPSILON_DEG = b
                .comment("Park-mode zero snap epsilon in degrees. Range: 0.0 - 5.0. Default: 0.0001")
                .defineInRange("park_zero_snap_epsilon_deg", DEFAULT_WIND_ROTO_VERTICAL_PARK_ZERO_SNAP_EPSILON_DEG, 0.0D, 5.0D);

        WIND_ROTO_VERTICAL_DISASSEMBLE_RETURN_DEGREES_PER_TICK = b
                .comment("Return-to-zero speed in degrees per tick before Windvane Bearing disassembles. Range: 0.0001 - 360.0. Default: 1.0")
                .defineInRange("disassemble_return_degrees_per_tick", DEFAULT_WIND_ROTO_VERTICAL_DISASSEMBLE_RETURN_DEGREES_PER_TICK, 0.0001D, 360.0D);

        WIND_ROTO_VERTICAL_DISASSEMBLE_ZERO_EPSILON_DEG = b
                .comment("Zero-angle epsilon in degrees for Windvane Bearing return-to-zero disassembly. Range: 0.0 - 180.0. Default: 0.5")
                .defineInRange("disassemble_zero_epsilon_deg", DEFAULT_WIND_ROTO_VERTICAL_DISASSEMBLE_ZERO_EPSILON_DEG, 0.0D, 180.0D);

        WIND_ROTO_VERTICAL_DISASSEMBLE_STABLE_TICKS = b
                .comment("Ticks Windvane Bearing must remain at zero before disassembly proceeds. Range: 1 - 200 ticks. Default: 2")
                .defineInRange("disassemble_stable_ticks", DEFAULT_WIND_ROTO_VERTICAL_DISASSEMBLE_STABLE_TICKS, 1, 200);

        WIND_ROTO_VERTICAL_SABLE_LOAD_RECOVERY_TICKS = b
                .comment("Legacy load recovery retry window for Windvane Bearing Sable refresh. Range: 1 - 12000 ticks. Default: 100")
                .defineInRange("sable_load_recovery_ticks", DEFAULT_WIND_ROTO_VERTICAL_SABLE_LOAD_RECOVERY_TICKS, 1, 12000);

        WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_GRACE_TICKS = b
                .comment("Reload reattach grace window during which retryable Windvane Bearing Sable failures keep the persistent link. Range: 1 - 72000 ticks. Default: 1200")
                .defineInRange("sable_reload_reattach_grace_ticks", DEFAULT_WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_GRACE_TICKS, 1, 72000);

        WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_LOG_INTERVAL_TICKS = b
                .comment("Minimum diagnostic log interval for retained Windvane Bearing Sable refresh failures. Range: 1 - 12000 ticks. Default: 200")
                .defineInRange("sable_reload_reattach_log_interval_ticks", DEFAULT_WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_LOG_INTERVAL_TICKS, 1, 12000);

        b.pop();
        b.pop();

        COMMON_SPEC = b.build();
    }

    public static int getResolvedSailPlacementAssistRange() {
        return Mth.clamp(SAIL_PLACEMENT_ASSIST_RANGE.get(), 1, 10);
    }

    public static double getRotorBladePeakEfficiencyFraction() {
        return Mth.clamp(PEAK_EFFICIENCY_ROTOR_BLADES.get(), 10, 200) / 100.0D;
    }

    public static int getSailPeakEfficiencyPitchDegrees() {
        return Mth.clamp(SAIL_PEAK_EFFICIENCY_PITCH_DEGREES.get(), 1, 89);
    }

    public static boolean isSailForceVectorsShown() {
        return SHOW_SAIL_FORCE_VECTORS.get();
    }

    public static boolean isSailForceSmoothingEnabled() {
        return SMOOTH_SAIL_FORCE_UPDATES.get();
    }

    public static double getSailForceSmoothingStrength() {
        return Mth.clamp(SAIL_FORCE_SMOOTHING_STRENGTH.get(), 0.1D, 10.0D);
    }

    public static int getAllowedBlocksAboveForOutside() {
        return Mth.clamp(ALLOWED_BLOCKS_ABOVE_FOR_OUTSIDE.get(), 0, 10);
    }

    public static boolean isServoTwisterBinaryInputEnabled() {
        return ENABLE_SERVO_TWISTER_BINARY_INPUT.get();
    }

    public static boolean isInvServoTwisterBinaryInputEnabled() {
        return ENABLE_INV_SERVO_TWISTER_BINARY_INPUT.get();
    }

    public static boolean isServoSpeedZeroMovementEnabled() {
        return ENABLE_SERVO_SPEED_ZERO_MOVEMENT.get();
    }

    public static boolean isInvServoSpeedZeroMovementEnabled() {
        return ENABLE_INV_SERVO_SPEED_ZERO_MOVEMENT.get();
    }

    public static boolean isServoSlotDiagnosticsEnabled() {
        return ENABLE_SERVO_SLOT_DIAGNOSTICS.get();
    }

    public static boolean isWindRotoBlockDiagnosticsEnabled() {
        return ENABLE_WIND_ROTO_BLOCK_DIAGNOSTICS.get();
    }

    public static boolean isWindRotoVerticalBlockDiagnosticsEnabled() {
        return ENABLE_WIND_ROTO_VERTICAL_BLOCK_DIAGNOSTICS.get();
    }

    public static boolean isServoDiagnosticsEnabled() {
        return ENABLE_SERVO_DIAGNOSTICS.get();
    }

    public static boolean isInvServoDiagnosticsEnabled() {
        return ENABLE_INV_SERVO_DIAGNOSTICS.get();
    }

    public static boolean isAutoReseatWrvbOnLoadEnabled() {
        return AUTO_RESEAT_WIND_ROTO_VERTICAL_BLOCK_ON_LOAD.get();
    }

    public static boolean isAutoReseatWrbOnLoadEnabled() {
        return AUTO_RESEAT_WIND_ROTO_BLOCK_ON_LOAD.get();
    }

    public static boolean isAutoReseatServoOnLoadEnabled() {
        return AUTO_RESEAT_SERVO_ON_LOAD.get();
    }

    public static boolean isAutoReseatInvServoOnLoadEnabled() {
        return AUTO_RESEAT_INV_SERVO_ON_LOAD.get();
    }

    public static boolean isMetalTraverseDebugOverlayShown() {
        return SHOW_METAL_TRAVERSE_DEBUG_OVERLAY.get();
    }

    public static boolean isGenerateOresInWorldEnabled() {
        return GENERATE_ORES_IN_WORLD.get();
    }

    public static boolean isBladeArmBlockShown() {
        return SHOW_BLADE_ARM_BLOCK.get();
    }

    public static boolean isBladeArmEastfaceBlockShown() {
        return SHOW_BLADE_ARM_EASTFACE_BLOCK.get();
    }

    public static boolean isBladeArmWestfaceBlockShown() {
        return SHOW_BLADE_ARM_WESTFACE_BLOCK.get();
    }

    public static boolean isMetalTraverseShown() {
        return SHOW_METAL_TRAVERSE.get();
    }

    public static boolean isNostalgicGrassBlockShown() {
        return SHOW_NOSTALGIC_GRASS_BLOCK.get();
    }

    public static boolean isNetheriteAdvancementDropEnabled() {
        return ENABLE_NETHERITE_ADVANCEMENT_DROP.get();
    }

    public static String getAdvancementDropItem() {
        return ADVANCEMENT_DROP_ITEM.get();
    }

    public static int getAdvancementDropCount() {
        return ADVANCEMENT_DROP_COUNT.get();
    }

    public static boolean isContentEnabled(String key) {
        return switch (key) {
            case CONTENT_SHOW_BLADE_ARM_BLOCK -> isBladeArmBlockShown();
            case CONTENT_SHOW_BLADE_ARM_EASTFACE_BLOCK -> isBladeArmEastfaceBlockShown();
            case CONTENT_SHOW_BLADE_ARM_WESTFACE_BLOCK -> isBladeArmWestfaceBlockShown();
            case CONTENT_SHOW_METAL_TRAVERSE -> isMetalTraverseShown();
            case CONTENT_SHOW_NOSTALGIC_GRASS_BLOCK -> isNostalgicGrassBlockShown();
            default -> false;
        };
    }

    public static boolean isMassTooltipShown() {
        return SHOW_MASS_TOOLTIP.get();
    }

    public static int getWeather2MaxRpm() {
        return Mth.clamp(WEATHER2_MAX_RPM.get(), 10, 256);
    }

    public static int getPmweatherMaxRpm() {
        return Mth.clamp(PMWEATHER_MAX_RPM.get(), 10, 256);
    }

    public static boolean isWindRotoContraptionMemoryEnabled() {
        return ENABLE_WIND_ROTO_CONTRAPTION_MEMORY.get();
    }

    public static boolean isWindRotoVerticalContraptionMemoryEnabled() {
        return ENABLE_WIND_ROTO_VERTICAL_CONTRAPTION_MEMORY.get();
    }

    public static boolean isServoTwisterContraptionMemoryEnabled() {
        return ENABLE_SERVO_TWISTER_CONTRAPTION_MEMORY.get();
    }

    public static boolean isInvServoTwisterContraptionMemoryEnabled() {
        return ENABLE_INV_SERVO_TWISTER_CONTRAPTION_MEMORY.get();
    }

    public static double getServoStiffnessPerInertia() {
        return Mth.clamp(SERVO_STIFFNESS_PER_INERTIA.get(), 0.0D, 10000000.0D);
    }

    public static double getServoDampingPerInertia() {
        return Mth.clamp(SERVO_DAMPING_PER_INERTIA.get(), 0.0D, 1000000.0D);
    }

    public static double getMode7DisassemblyReturnMotorStrengthMultiplier() {
        return Mth.clamp(
                MODE_7_DISASSEMBLY_RETURN_MOTOR_STRENGTH_MULTIPLIER.get(),
                1.0D,
                100.0D
        );
    }

    public static float getServoDisassemblyReturnSpeedMultiplier() {
        return (float) Mth.clamp(SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER.get(), 0.05D, 10.0D);
    }

    public static float getServoDisassemblyReturnSpeedMultiplierModes1To3() {
        return (float) Mth.clamp(
                SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER_MODES_1_TO_3.get(),
                0.05D,
                10.0D
        );
    }

    public static float getServoDisassemblyReturnSpeedMultiplierModes4To6() {
        return (float) Mth.clamp(
                SERVO_DISASSEMBLY_RETURN_SPEED_MULTIPLIER_MODES_4_TO_6.get(),
                0.05D,
                10.0D
        );
    }

    public static double getPropellerSlotServoStiffnessMultiplier() {
        return Mth.clamp(PROPELLER_SLOT_SERVO_STIFFNESS_MULTIPLIER.get(), 0.0D, 100.0D);
    }

    public static double getPropellerSlotServoDampingMultiplier() {
        return Mth.clamp(PROPELLER_SLOT_SERVO_DAMPING_MULTIPLIER.get(), 0.0D, 100.0D);
    }

    public static double getFreeBearingDampingPerInertia() {
        return Mth.clamp(FREE_BEARING_DAMPING_PER_INERTIA.get(), 0.0D, 1000.0D);
    }

    public static double getServoMinEffectiveInertia() {
        return Mth.clamp(SERVO_MIN_EFFECTIVE_INERTIA.get(), 0.0001D, 1000000.0D);
    }

    public static double getBladeArmBlockMass() {
        return Mth.clamp(BLADE_ARM_BLOCK_MASS.get(), 1, 200);
    }

    public static double getBladeArmEastfaceBlockMass() {
        return Mth.clamp(BLADE_ARM_EASTFACE_BLOCK_MASS.get(), 1, 200);
    }

    public static double getBladeArmWestfaceBlockMass() {
        return Mth.clamp(BLADE_ARM_WESTFACE_BLOCK_MASS.get(), 1, 200);
    }

    public static float getWindRotoVerticalSuPerRpm() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_SU_PER_RPM.get(), 0.0D, 100000.0D);
    }

    public static double getWindRotoVerticalServoStiffnessPerInertia() {
        return Mth.clamp(WIND_ROTO_VERTICAL_SERVO_STIFFNESS_PER_INERTIA.get(), 0.0D, 10000000.0D);
    }

    public static double getWindRotoVerticalServoDampingPerInertia() {
        return Mth.clamp(WIND_ROTO_VERTICAL_SERVO_DAMPING_PER_INERTIA.get(), 0.0D, 1000000.0D);
    }

    public static double getWindRotoVerticalMinEffectiveInertia() {
        return Mth.clamp(WIND_ROTO_VERTICAL_MIN_EFFECTIVE_INERTIA.get(), 0.0001D, 1000000.0D);
    }

    public static int getWindRotoVerticalMaxYawRpm() {
        return Mth.clamp(WIND_ROTO_VERTICAL_MAX_YAW_RPM.get(), 0, 256);
    }

    public static float getWindRotoVerticalYawDeadzoneDeg() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_YAW_DEADZONE_DEG.get(), 0.0D, 180.0D);
    }

    public static float getWindRotoVerticalYawTargetOffsetDeg() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_YAW_TARGET_OFFSET_DEG.get(), -180.0D, 180.0D);
    }

    public static int getWindRotoVerticalPulseMinTicks() {
        return Mth.clamp(WIND_ROTO_VERTICAL_PULSE_MIN_TICKS.get(), 1, 200);
    }

    public static int getWindRotoVerticalWindAngleUpdateTicks() {
        return Mth.clamp(WIND_ROTO_VERTICAL_WIND_ANGLE_UPDATE_TICKS.get(), 1, 12000);
    }

    public static int getWindRotoVerticalPlacementStatusRefreshTicksDisassembled() {
        return Mth.clamp(WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_DISASSEMBLED.get(), 1, 12000);
    }

    public static int getWindRotoVerticalPlacementStatusRefreshTicksAssembled() {
        return Mth.clamp(WIND_ROTO_VERTICAL_PLACEMENT_STATUS_REFRESH_TICKS_ASSEMBLED.get(), 1, 12000);
    }

    public static float getWindRotoVerticalYawControllerGain() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_YAW_CONTROLLER_GAIN.get(), 0.0D, 10.0D);
    }

    public static float getWindRotoVerticalMaxYawAccelDegPerTick2() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_MAX_YAW_ACCEL_DEG_PER_TICK2.get(), 0.0D, 360.0D);
    }

    public static float getWindRotoVerticalYawMinTrackingSpeedDegPerTick() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_YAW_MIN_TRACKING_SPEED_DEG_PER_TICK.get(), 0.0D, 360.0D);
    }

    public static float getWindRotoVerticalYawStopVelocityDegPerTick() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_YAW_STOP_VELOCITY_DEG_PER_TICK.get(), 0.0D, 360.0D);
    }

    public static float getWindRotoVerticalParkZeroSnapEpsilonDeg() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_PARK_ZERO_SNAP_EPSILON_DEG.get(), 0.0D, 5.0D);
    }

    public static float getWindRotoVerticalDisassembleReturnDegreesPerTick() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_DISASSEMBLE_RETURN_DEGREES_PER_TICK.get(), 0.0001D, 360.0D);
    }

    public static float getWindRotoVerticalDisassembleZeroEpsilonDeg() {
        return (float) Mth.clamp(WIND_ROTO_VERTICAL_DISASSEMBLE_ZERO_EPSILON_DEG.get(), 0.0D, 180.0D);
    }

    public static int getWindRotoVerticalDisassembleStableTicks() {
        return Mth.clamp(WIND_ROTO_VERTICAL_DISASSEMBLE_STABLE_TICKS.get(), 1, 200);
    }

    public static int getWindRotoVerticalSableLoadRecoveryTicks() {
        return Mth.clamp(WIND_ROTO_VERTICAL_SABLE_LOAD_RECOVERY_TICKS.get(), 1, 12000);
    }

    public static int getWindRotoVerticalSableReloadReattachGraceTicks() {
        return Mth.clamp(WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_GRACE_TICKS.get(), 1, 72000);
    }

    public static int getWindRotoVerticalSableReloadReattachLogIntervalTicks() {
        return Mth.clamp(WIND_ROTO_VERTICAL_SABLE_RELOAD_REATTACH_LOG_INTERVAL_TICKS.get(), 1, 12000);
    }
}
