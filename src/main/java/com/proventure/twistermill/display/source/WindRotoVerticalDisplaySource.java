package com.proventure.twistermill.display.source;

import com.proventure.twistermill.blockentity.WindRotoVerticalBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.display.FlapDisplaySection;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WindRotoVerticalDisplaySource extends NumericSingleLineDisplaySource {

    private static final String CONFIG_MODE = "Mode";

    private static final int MODE_VERTICAL_FACING = 0;
    private static final int MODE_OBSIDIAN_NORTH_MARKER = 1;
    private static final int MODE_YAW = 2;
    private static final int MODE_RPM = 3;
    private static final int MODE_TARGET_YAW = 4;
    private static final int MODE_WORLD_WIND_ANGLE = 5;
    private static final int MODE_LOCAL_TARGET_YAW = 6;
    private static final int MODE_YAW_SPEED = 7;
    private static final int MODE_YAW_MOVEMENT = 8;
    private static final int MODE_PARK_MODE = 9;
    private static final int MODE_REDSTONE_PULSE_COOLDOWN = 10;
    private static final int MODE_SUMMARY_1 = 11;
    private static final int MODE_SUMMARY_2 = 12;
    private static final int MODE_SUMMARY_3 = 13;
    private static final int MODE_SUMMARY_4 = 14;
    private static final int MODE_SUMMARY_5 = 15;
    private static final int MODE_SUMMARY_6 = 16;
    private static final int MODE_SUMMARY_7 = 17;
    private static final int MODE_MAX = MODE_SUMMARY_7;

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        VerticalValues values = VerticalValues.from(context);
        int mode = getMode(context);

        if (mode == MODE_SUMMARY_1) {
            List<MutableComponent> lines = new ArrayList<>(2);
            lines.add(Component.literal("Yaw: " + formatFloat(values.yaw()) + "°"));
            lines.add(Component.literal("Target yaw: " + formatFloat(values.targetYaw()) + "°"));
            return lines;
        }

        if (mode == MODE_SUMMARY_2) {
            List<MutableComponent> lines = new ArrayList<>(4);
            lines.add(Component.literal("Vertical facing: " + booleanText(values.verticalFacing())));
            lines.add(Component.literal("Obsidian north marker: " + booleanText(values.obsidianMarkerPresent())));
            lines.add(Component.literal("World wind angle: " + formatFloat(values.worldWindAngle()) + "°"));
            lines.add(Component.literal("Local target yaw: " + formatFloat(values.localTargetYaw()) + "°"));
            return lines;
        }

        if (mode == MODE_SUMMARY_3) {
            List<MutableComponent> lines = new ArrayList<>(5);
            lines.add(Component.literal("Yaw: " + formatFloat(values.yaw()) + "°"));
            lines.add(Component.literal("Target yaw: " + formatFloat(values.targetYaw()) + "°"));
            lines.add(Component.literal("World wind angle: " + formatFloat(values.worldWindAngle()) + "°"));
            lines.add(Component.literal("Local target yaw: " + formatFloat(values.localTargetYaw()) + "°"));
            lines.add(Component.literal("Yaw speed: " + formatFloat(values.yawSpeed()) + "°/t"));
            return lines;
        }

        if (mode == MODE_SUMMARY_4) {
            List<MutableComponent> lines = new ArrayList<>(2);
            lines.add(Component.literal("Park mode: " + parkModeText(values.parkModeActive())));
            lines.add(Component.literal("Redstone pulse cooldown: " + values.redstonePulseCooldownTicks()));
            return lines;
        }

        if (mode == MODE_SUMMARY_5) {
            List<MutableComponent> lines = new ArrayList<>(7);
            lines.add(Component.literal("Vertical facing: " + booleanText(values.verticalFacing())));
            lines.add(Component.literal("Obsidian north marker: " + booleanText(values.obsidianMarkerPresent())));
            lines.add(Component.literal("Yaw: " + formatFloat(values.yaw()) + "°"));
            lines.add(Component.literal("Target yaw: " + formatFloat(values.targetYaw()) + "°"));
            lines.add(Component.literal("World wind angle: " + formatFloat(values.worldWindAngle()) + "°"));
            lines.add(Component.literal("Local target yaw: " + formatFloat(values.localTargetYaw()) + "°"));
            lines.add(Component.literal("Yaw speed: " + formatFloat(values.yawSpeed()) + "°/t"));
            return lines;
        }

        if (mode == MODE_SUMMARY_6) {
            List<MutableComponent> lines = new ArrayList<>(8);
            lines.add(Component.literal("Vertical facing: " + booleanText(values.verticalFacing())));
            lines.add(Component.literal("Obsidian north marker: " + booleanText(values.obsidianMarkerPresent())));
            lines.add(Component.literal("Yaw: " + formatFloat(values.yaw()) + "°"));
            lines.add(Component.literal("RPM: " + values.rpm()));
            lines.add(Component.literal("Target yaw: " + formatFloat(values.targetYaw()) + "°"));
            lines.add(Component.literal("World wind angle: " + formatFloat(values.worldWindAngle()) + "°"));
            lines.add(Component.literal("Local target yaw: " + formatFloat(values.localTargetYaw()) + "°"));
            lines.add(Component.literal("Yaw speed: " + formatFloat(values.yawSpeed()) + "°/t"));
            return lines;
        }

        if (mode == MODE_SUMMARY_7) {
            List<MutableComponent> lines = new ArrayList<>(11);
            lines.add(Component.literal("Vertical facing: " + booleanText(values.verticalFacing())));
            lines.add(Component.literal("Obsidian north marker: " + booleanText(values.obsidianMarkerPresent())));
            lines.add(Component.literal("Yaw: " + formatFloat(values.yaw()) + "°"));
            lines.add(Component.literal("RPM: " + values.rpm()));
            lines.add(Component.literal("Target yaw: " + formatFloat(values.targetYaw()) + "°"));
            lines.add(Component.literal("World wind angle: " + formatFloat(values.worldWindAngle()) + "°"));
            lines.add(Component.literal("Local target yaw: " + formatFloat(values.localTargetYaw()) + "°"));
            lines.add(Component.literal("Yaw speed: " + formatFloat(values.yawSpeed()) + "°/t"));
            lines.add(Component.literal("Yaw movement: " + booleanText(values.yawMoving())));
            lines.add(Component.literal("Park mode: " + parkModeText(values.parkModeActive())));
            lines.add(Component.literal("Redstone pulse cooldown: " + values.redstonePulseCooldownTicks()));
            return lines;
        }

        return super.provideText(context, stats);
    }

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        VerticalValues values = VerticalValues.from(context);

        return switch (getMode(context)) {
            case MODE_VERTICAL_FACING -> Component.literal(booleanText(values.verticalFacing()));
            case MODE_OBSIDIAN_NORTH_MARKER -> Component.literal(booleanText(values.obsidianMarkerPresent()));
            case MODE_YAW -> Component.literal(formatFloat(values.yaw()))
                    .append(Component.literal("°"));
            case MODE_RPM -> Component.literal(Integer.toString(values.rpm()))
                    .append(Component.literal(" "))
                    .append(Component.translatable("create.generic.unit.rpm"));
            case MODE_TARGET_YAW -> Component.literal(formatFloat(values.targetYaw()))
                    .append(Component.literal("°"));
            case MODE_WORLD_WIND_ANGLE -> Component.literal(formatFloat(values.worldWindAngle()))
                    .append(Component.literal("°"));
            case MODE_LOCAL_TARGET_YAW -> Component.literal(formatFloat(values.localTargetYaw()))
                    .append(Component.literal("°"));
            case MODE_YAW_SPEED -> Component.literal(formatFloat(values.yawSpeed()))
                    .append(Component.literal("°/t"));
            case MODE_YAW_MOVEMENT -> Component.literal(booleanText(values.yawMoving()));
            case MODE_PARK_MODE -> Component.literal(parkModeText(values.parkModeActive()));
            case MODE_REDSTONE_PULSE_COOLDOWN -> Component.literal(Long.toString(values.redstonePulseCooldownTicks()));
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
                                Component.literal("Vertical facing (true/false)"),
                                Component.literal("Obsidian north marker present"),
                                Component.literal("Yaw"),
                                Component.literal("RPM"),
                                Component.literal("Target yaw"),
                                Component.literal("World wind angle"),
                                Component.literal("Local target yaw"),
                                Component.literal("Yaw speed"),
                                Component.literal("Yaw movement"),
                                Component.literal("Park mode status"),
                                Component.literal("Redstone pulse cooldown"),
                                Component.literal("Summary 1"),
                                Component.literal("Summary 2"),
                                Component.literal("Summary 3"),
                                Component.literal("Summary 4"),
                                Component.literal("Summary 5"),
                                Component.literal("Summary 6"),
                                Component.literal("Summary 7")
                        ))
                        .titled(Component.literal("Displayed Value")),
                CONFIG_MODE);
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        int mode = getMode(context);
        return mode < MODE_SUMMARY_1 || mode > MODE_SUMMARY_7;
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
        if (mode < MODE_VERTICAL_FACING || mode > MODE_MAX) {
            return MODE_VERTICAL_FACING;
        }
        return mode;
    }

    private static boolean isTextMode(int mode) {
        return mode == MODE_VERTICAL_FACING
                || mode == MODE_OBSIDIAN_NORTH_MARKER
                || mode == MODE_YAW_MOVEMENT
                || mode == MODE_PARK_MODE
                || (mode >= MODE_SUMMARY_1 && mode <= MODE_SUMMARY_7);
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

    private static String parkModeText(boolean active) {
        return active ? "active" : "not active";
    }

    private static boolean isVerticalFacing(BlockState state) {
        if (!state.hasProperty(BearingBlock.FACING)) {
            return false;
        }

        Direction facing = state.getValue(BearingBlock.FACING);
        return facing == Direction.UP || facing == Direction.DOWN;
    }

    private static float wrap360(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        if (wrapped >= 360.0F) {
            wrapped -= 360.0F;
        }
        return wrapped;
    }

    private record VerticalValues(boolean verticalFacing, boolean obsidianMarkerPresent, float yaw, int rpm,
                                  float targetYaw, float worldWindAngle, float localTargetYaw, float yawSpeed,
                                  boolean yawMoving, boolean parkModeActive, long redstonePulseCooldownTicks) {
        private static VerticalValues from(DisplayLinkContext context) {
            if (!(context.getSourceBlockEntity() instanceof WindRotoVerticalBlockEntity windRotoVertical)) {
                return new VerticalValues(false, false, 0.0F, 0, 0.0F,
                        0.0F, 0.0F, 0.0F, false, false, 0L);
            }

            CompoundTag tag = new CompoundTag();
            windRotoVertical.write(tag, false);

            boolean verticalFacing = isVerticalFacing(windRotoVertical.getBlockState());
            boolean obsidianMarker = tag.getBoolean("PlacementNorthValid");
            float yaw = wrap360(tag.getFloat("VerticalCurrentYawDeg"));
            int rpm = Math.abs(tag.getInt("GenRpm"));
            float targetYaw = wrap360(tag.getFloat("VerticalTargetYawDeg"));
            float worldWindAngle = wrap360(tag.getFloat("LastWorldWindAngleDeg"));
            float localTargetYaw = wrap360(tag.getFloat("LastLocalTargetYawDeg"));
            float yawSpeed = tag.getFloat("VerticalYawVelocityDegPerTick");
            boolean yawMoving = tag.getBoolean("VerticalYawMoving");
            boolean parkMode = tag.getBoolean("VerticalParkedMode");

            long cooldownUntil = tag.getLong("VerticalPulseCooldownUntil");
            Level level = windRotoVertical.getLevel();
            long cooldownTicks = level == null ? 0L : Math.max(0L, cooldownUntil - level.getGameTime());

            return new VerticalValues(verticalFacing, obsidianMarker, yaw, rpm, targetYaw, worldWindAngle,
                    localTargetYaw, yawSpeed, yawMoving, parkMode, cooldownTicks);
        }
    }
}
