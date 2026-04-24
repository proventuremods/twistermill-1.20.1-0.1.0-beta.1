package com.proventure.twistermill.display.source;

import com.proventure.twistermill.blockentity.WindRotoBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WindRotoDisplaySource extends NumericSingleLineDisplaySource {

    private static final String CONFIG_MODE = "Mode";
    private static final TagKey<Block> CREATE_WINDMILL_SAILS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("create", "windmill_sails"));

    private static final int MODE_RPM = 0;
    private static final int MODE_SU_CAPACITY = 1;
    private static final int MODE_RAW_WIND_SPEED = 2;
    private static final int MODE_SMOOTHED_WIND_SPEED = 3;
    private static final int MODE_OUTSIDE = 4;
    private static final int MODE_CONNECTED_SERVOS = 5;
    private static final int MODE_AVERAGE_SERVO = 6;
    private static final int MODE_SAIL_LIKE_BLOCKS = 7;
    private static final int MODE_CONTRAPTION_BLOCKS = 8;
    private static final int MODE_REDSTONE_INPUT = 9;
    private static final int MODE_REDSTONE_OUTPUT = 10;
    private static final int MODE_HOLD_STATE = 11;
    private static final int MODE_SUMMARY_8 = 12;
    private static final int MODE_SUMMARY_12 = 13;
    private static final int MODE_MAX = MODE_SUMMARY_12;

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (getMode(context) == MODE_SUMMARY_8) {
            WindRotoValues values = WindRotoValues.from(context);
            List<MutableComponent> lines = new ArrayList<>(8);
            lines.add(Component.literal("RPM: " + values.generatedRpm()));
            lines.add(Component.literal("SU capacity: " + formatFloat(values.generatedSu())));
            lines.add(Component.literal("raw wind speed: " + formatFloat(values.rawWindSpeed())));
            lines.add(Component.literal("smoothed wind speed: " + formatFloat(values.smoothedWindSpeed())));
            lines.add(Component.literal("outside: " + (values.outside() ? "true" : "false")));
            lines.add(Component.literal("connected servos: " + values.connectedServos()));
            lines.add(Component.literal("average servo: " + formatFloat(values.averageServo())));
            lines.add(Component.literal("sail-like blocks: " + values.sailLikeBlocks()));
            return lines;
        }

        if (getMode(context) == MODE_SUMMARY_12) {
            WindRotoValues values = WindRotoValues.from(context);
            List<MutableComponent> lines = new ArrayList<>(12);
            lines.add(Component.literal("RPM: " + values.generatedRpm()));
            lines.add(Component.literal("SU capacity: " + formatFloat(values.generatedSu())));
            lines.add(Component.literal("raw wind speed: " + formatFloat(values.rawWindSpeed())));
            lines.add(Component.literal("smoothed wind speed: " + formatFloat(values.smoothedWindSpeed())));
            lines.add(Component.literal("outside: " + (values.outside() ? "true" : "false")));
            lines.add(Component.literal("connected servos: " + values.connectedServos()));
            lines.add(Component.literal("average servo: " + formatFloat(values.averageServo())));
            lines.add(Component.literal("sail-like blocks: " + values.sailLikeBlocks()));
            lines.add(Component.literal("contraption blocks: " + values.contraptionBlocks()));
            lines.add(Component.literal("redstone input: " + values.redstoneInput()));
            lines.add(Component.literal("redstone output: " + values.redstoneOutput()));
            lines.add(Component.literal("hold mode status: " + (values.holdActive() ? "active" : "not active")));
            return lines;
        }

        return super.provideText(context, stats);
    }

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        WindRotoValues values = WindRotoValues.from(context);

        return switch (getMode(context)) {
            case MODE_RPM -> Component.literal(Integer.toString(values.generatedRpm()))
                    .append(Component.literal(" "))
                    .append(Component.translatable("create.generic.unit.rpm"));
            case MODE_SU_CAPACITY -> Component.literal(formatFloat(values.generatedSu()))
                    .append(Component.literal(" SU"));
            case MODE_RAW_WIND_SPEED -> Component.literal(formatFloat(values.rawWindSpeed()));
            case MODE_SMOOTHED_WIND_SPEED -> Component.literal(formatFloat(values.smoothedWindSpeed()));
            case MODE_OUTSIDE -> Component.literal(values.outside() ? "true" : "false");
            case MODE_CONNECTED_SERVOS -> Component.literal(Integer.toString(values.connectedServos()));
            case MODE_AVERAGE_SERVO -> Component.literal(formatFloat(values.averageServo()))
                    .append(Component.literal("°"));
            case MODE_SAIL_LIKE_BLOCKS -> Component.literal(Integer.toString(values.sailLikeBlocks()));
            case MODE_CONTRAPTION_BLOCKS -> Component.literal(Integer.toString(values.contraptionBlocks()));
            case MODE_REDSTONE_INPUT -> Component.literal(Integer.toString(values.redstoneInput()));
            case MODE_REDSTONE_OUTPUT -> Component.literal(Integer.toString(values.redstoneOutput()));
            case MODE_HOLD_STATE -> values.holdActive()
                    ? Component.translatable("create.tooltip.twistermill.vertical.active")
                    : Component.translatable("create.tooltip.twistermill.vertical.not_active");
            default -> ZERO.copy();
        };
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (isFirstLine) {
            return;
        }

        builder.addSelectionScrollInput(0, 143,
                (selectionScrollInput, label) -> selectionScrollInput
                        .forOptions(List.of(
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.rpm"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.su_capacity"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.raw_wind_speed"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.smoothed_wind_speed"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.outside"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.connected_servos"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.average_servo"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.sail_like_blocks"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.contraption_blocks"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.redstone_input"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.redstone_output"),
                                Component.translatable("twistermill.display_source.wind_roto_stats.option.hold_mode_state"),
                                Component.literal("Summary (8 values)"),
                                Component.literal("Summary (12 values)")
                        ))
                        .titled(Component.translatable("twistermill.display_source.wind_roto_stats.display")),
                CONFIG_MODE);
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        int mode = getMode(context);
        return mode != MODE_SUMMARY_8 && mode != MODE_SUMMARY_12;
    }

    @Override
    protected String getFlapDisplayLayoutName(DisplayLinkContext context) {
        int mode = getMode(context);
        if (isTextMode(mode)) {
            return "Default";
        }
        return super.getFlapDisplayLayoutName(context);
    }

    @Override
    protected FlapDisplaySection createSectionForValue(DisplayLinkContext context, int size) {
        int mode = getMode(context);
        if (isTextMode(mode)) {
            return new FlapDisplaySection(size * FlapDisplaySection.MONOSPACE, "alphabet", false, false);
        }
        return super.createSectionForValue(context, size);
    }

    private static int getMode(DisplayLinkContext context) {
        int mode = context.sourceConfig().getInt(CONFIG_MODE);
        if (mode < MODE_RPM || mode > MODE_MAX) {
            return MODE_RPM;
        }
        return mode;
    }

    private static boolean isTextMode(int mode) {
        return mode == MODE_OUTSIDE || mode == MODE_HOLD_STATE || mode == MODE_SUMMARY_8 || mode == MODE_SUMMARY_12;
    }

    private static String formatFloat(float value) {
        if (!Float.isFinite(value)) {
            return "0";
        }

        if (Math.abs(value - Math.round(value)) < 0.0001F) {
            return Integer.toString(Math.round(value));
        }

        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static int clampRedstoneValue(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 15) {
            return 15;
        }
        return value;
    }

    private static int countSailLikeBlocks(WindRotoBlockEntity windRoto) {
        var movedContraption = windRoto.getMovedContraption();
        if (movedContraption == null || movedContraption.getContraption() == null) {
            return 0;
        }

        var blocks = movedContraption.getContraption().getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            return 0;
        }

        int sailLikeCount = 0;
        for (var info : blocks.values()) {
            if (info != null && info.state().is(CREATE_WINDMILL_SAILS)) {
                sailLikeCount++;
            }
        }

        return sailLikeCount;
    }

    private record WindRotoValues(int generatedRpm, float generatedSu, float rawWindSpeed, float smoothedWindSpeed,
                                  boolean outside, int connectedServos, float averageServo, int sailLikeBlocks,
                                  int contraptionBlocks, int redstoneInput, int redstoneOutput, boolean holdActive) {
        private static WindRotoValues from(DisplayLinkContext context) {
            if (!(context.getSourceBlockEntity() instanceof WindRotoBlockEntity windRoto)) {
                return new WindRotoValues(0, 0.0F, 0.0F, 0.0F, false, 0, 0.0F,
                        0, 0, 0, 0, false);
            }

            CompoundTag tag = new CompoundTag();
            windRoto.write(tag, false);

            int rpm = tag.getInt("GenRpm");
            float su = tag.getFloat("GenSu");
            float raw = tag.getFloat("LastWind");
            float smooth = tag.contains("WindSmoothed") ? tag.getFloat("WindSmoothed") : raw;
            if (!Float.isFinite(smooth)) {
                smooth = raw;
            }

            boolean outside = tag.getBoolean("Outside");
            int redstoneInput = clampRedstoneValue(tag.getInt("ExtRS"));
            boolean holdActive = tag.getBoolean("StoppedByRS");

            int connectedServos = windRoto.getBoundServoCount();
            float averageServo = windRoto.getAverageBoundServoAngle();
            int sailLikeBlocks = countSailLikeBlocks(windRoto);
            int contraptionBlocks = windRoto.getTotalContraptionBlockCount();
            int redstoneOutput = clampRedstoneValue(windRoto.getComparatorOutputLevel());

            return new WindRotoValues(rpm, su, raw, smooth, outside, connectedServos, averageServo, sailLikeBlocks,
                    contraptionBlocks, redstoneInput, redstoneOutput, holdActive);
        }
    }
}
