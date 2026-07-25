package com.proventure.twistermill.display.source;

import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ServoTwisterDisplaySource extends NumericSingleLineDisplaySource {

    private static final String CONFIG_MODE = "Mode";

    private static final int MODE_ANGLE = 0;
    private static final int MODE_ANGLE_SETTING = 1;
    private static final int MODE_BOUND_TO_WIND_ROTO = 2;
    private static final int MODE_RS_SPEED = 3;
    private static final int MODE_RS_ANGLE = 4;
    private static final int MODE_RS_MODE = 5;
    private static final int MODE_CONTRAPTION_BLOCKS = 6;
    private static final int MODE_SUMMARY_1 = 7;
    private static final int MODE_SUMMARY_2 = 8;
    private static final int MODE_SUMMARY_3 = 9;
    private static final int MODE_MAX = MODE_SUMMARY_3;

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        ServoValues values = ServoValues.from(context);
        int mode = getMode(context);

        if (mode == MODE_SUMMARY_1) {
            List<MutableComponent> lines = new ArrayList<>(2);
            lines.add(Component.literal("Angle: " + formatFloat(values.angle()) + "°"));
            lines.add(Component.literal("Bound to WindRoto: " + booleanText(values.boundToWindRoto())));
            return lines;
        }

        if (mode == MODE_SUMMARY_2) {
            List<MutableComponent> lines = new ArrayList<>(3);
            lines.add(Component.literal("RS input (speed): " + formatSpeedMapping(values)));
            lines.add(Component.literal("RS input (angle): " + formatAngleMapping(values)));
            lines.add(Component.literal("RS input (mode): " + values.rsMode()));
            return lines;
        }

        if (mode == MODE_SUMMARY_3) {
            List<MutableComponent> lines = new ArrayList<>(7);
            lines.add(Component.literal("Angle: " + formatFloat(values.angle()) + "°"));
            lines.add(Component.literal("Angle setting: " + formatFloat(values.angleSetting()) + "°"));
            lines.add(Component.literal("Bound to WindRoto: " + booleanText(values.boundToWindRoto())));
            lines.add(Component.literal("RS input (speed): " + formatSpeedMapping(values)));
            lines.add(Component.literal("RS input (angle): " + formatAngleMapping(values)));
            lines.add(Component.literal("RS input (mode): " + values.rsMode()));
            lines.add(Component.literal("Contraption blocks: " + values.contraptionBlocks()));
            return lines;
        }

        return super.provideText(context, stats);
    }

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        ServoValues values = ServoValues.from(context);

        return switch (getMode(context)) {
            case MODE_ANGLE -> Component.literal(formatFloat(values.angle()))
                    .append(Component.literal("°"));
            case MODE_ANGLE_SETTING -> Component.literal(formatFloat(values.angleSetting()))
                    .append(Component.literal("°"));
            case MODE_BOUND_TO_WIND_ROTO -> Component.literal(booleanText(values.boundToWindRoto()));
            case MODE_RS_SPEED -> Component.literal(formatSpeedMapping(values));
            case MODE_RS_ANGLE -> Component.literal(formatAngleMapping(values));
            case MODE_RS_MODE -> Component.literal(Integer.toString(values.rsMode()));
            case MODE_CONTRAPTION_BLOCKS -> Component.literal(Integer.toString(values.contraptionBlocks()));
            default -> ZERO.copy();
        };
    }

    @Override
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (isFirstLine) {
            return;
        }

        builder.addSelectionScrollInput(0, 143,
                (selectionScrollInput, label) -> selectionScrollInput
                        .forOptions(List.of(
                                Component.literal("Angle"),
                                Component.literal("Angle setting"),
                                Component.literal("Bound to WindRoto (true/false)"),
                                Component.literal("RS input (speed mapped)"),
                                Component.literal("RS input (angle mapped)"),
                                Component.literal("RS input (mode)"),
                                Component.literal("Contraption blocks"),
                                Component.literal("Summary 1"),
                                Component.literal("Summary 2"),
                                Component.literal("Summary 3")
                        ))
                        .titled(Component.literal("Displayed Value")),
                CONFIG_MODE);
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        int mode = getMode(context);
        return mode != MODE_SUMMARY_1 && mode != MODE_SUMMARY_2 && mode != MODE_SUMMARY_3;
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
        if (mode < MODE_ANGLE || mode > MODE_MAX) {
            return MODE_ANGLE;
        }
        return mode;
    }

    private static boolean isTextMode(int mode) {
        return mode == MODE_BOUND_TO_WIND_ROTO || mode == MODE_SUMMARY_1 || mode == MODE_SUMMARY_2 || mode == MODE_SUMMARY_3;
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

    private static String formatFloat(float value) {
        if (!Float.isFinite(value)) {
            return "0";
        }

        if (Math.abs(value - Math.round(value)) < 0.0001F) {
            return Integer.toString(Math.round(value));
        }

        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String booleanText(boolean value) {
        return value ? "true" : "false";
    }

    private static String formatSpeedMapping(ServoValues values) {
        return values.rsSpeed() + " => " + formatFloat(values.mappedSpeedDegPerTick()) + " deg/t";
    }

    private static String formatAngleMapping(ServoValues values) {
        String multiplierPart = values.angleMultiplier() > 1 ? " x" + values.angleMultiplier() : "";
        return values.rsAngle() + " => " + formatFloat(values.mappedAngleDegrees()) + " deg ("
                + values.configuredMaxDegrees() + " deg" + multiplierPart + ")";
    }

    private record ServoValues(float angle, float angleSetting, boolean boundToWindRoto, int rsSpeed, int rsAngle,
                               int rsMode, int contraptionBlocks, float mappedSpeedDegPerTick, float mappedAngleDegrees,
                               int configuredMaxDegrees, int angleMultiplier) {
        private static ServoValues from(DisplayLinkContext context) {
            if (context.getSourceBlockEntity() instanceof ServoTwisterBlockEntity servoTwister) {
                return new ServoValues(
                        servoTwister.getWindRotoBindingAngleDegrees(),
                        servoTwister.getMappedAngleDegreesForDisplay(),
                        servoTwister.isBoundToWindRoto(),
                        clampRedstoneValue(servoTwister.getLastWestSignal()),
                        clampRedstoneValue(servoTwister.getLastEastSignal()),
                        clampRedstoneValue(servoTwister.getLastOppositeTopSignal()),
                        servoTwister.getBoundContraptionBlockCount(),
                        servoTwister.getMappedSpeedDegreesPerTickForDisplay(),
                        servoTwister.getMappedAngleDegreesForDisplay(),
                        servoTwister.getConfiguredMaxDegreesForDisplay(),
                        servoTwister.getActiveAngleMultiplierForDisplay()
                );
            }

            if (context.getSourceBlockEntity() instanceof InvServoTwisterBlockEntity invServoTwister) {
                return new ServoValues(
                        invServoTwister.getWindRotoBindingAngleDegrees(),
                        invServoTwister.getMappedAngleDegreesForDisplay(),
                        invServoTwister.isBoundToWindRoto(),
                        clampRedstoneValue(invServoTwister.getLastWestSignal()),
                        clampRedstoneValue(invServoTwister.getLastEastSignal()),
                        clampRedstoneValue(invServoTwister.getLastOppositeTopSignal()),
                        invServoTwister.getBoundContraptionBlockCount(),
                        invServoTwister.getMappedSpeedDegreesPerTickForDisplay(),
                        invServoTwister.getMappedAngleDegreesForDisplay(),
                        invServoTwister.getConfiguredMaxDegreesForDisplay(),
                        invServoTwister.getActiveAngleMultiplierForDisplay()
                );
            }

            return new ServoValues(0.0F, 0.0F, false, 0, 0, 0, 0, 0.0F, 0.0F, 60, 1);
        }
    }
}

