package com.proventure.twistermill.diagnostics;

import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.WindRotoBlockEntity;
import com.proventure.twistermill.blockentity.WindRotoVerticalBlockEntity;
import com.proventure.twistermill.config.TwisterMillConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Locale;

public final class TwisterMillReseatService {

    private TwisterMillReseatService() {
    }

    public enum TargetType {
        WRVB("WRVB", "Wind Roto Vertical Block"),
        WRB("WRB", "Wind Roto Block"),
        SERVO("Servo", "Servo"),
        INV_SERVO("InvServo", "Inverted Servo");

        private final String shortLabel;
        private final String displayLabel;

        TargetType(String shortLabel, String displayLabel) {
            this.shortLabel = shortLabel;
            this.displayLabel = displayLabel;
        }

        public String shortLabel() {
            return shortLabel;
        }

        public String displayLabel() {
            return displayLabel;
        }
    }

    public enum Trigger {
        MANUAL_COMMAND("manual-command", false),
        AUTO_LEVEL_LOAD("auto-reseat", true),
        AUTO_SERVER_STARTED("auto-reseat", true),
        AUTO_PLAYER_JOIN("auto-reseat", true),
        AUTO_CHUNK_LOAD("auto-reseat", true);

        private final String actionPrefix;
        private final boolean automatic;

        Trigger(String actionPrefix, boolean automatic) {
            this.actionPrefix = actionPrefix;
            this.automatic = automatic;
        }

        public String actionPrefix() {
            return actionPrefix;
        }

        public boolean automatic() {
            return automatic;
        }
    }

    public record ReseatResult(
            TargetType targetType,
            BlockPos pos,
            boolean applied,
            String action,
            Float visualAngleBefore,
            Float visualAngleAfter,
            double anchorWorldErrorBefore,
            double normalWorldErrorBefore,
            double anchorWorldErrorAfter,
            double normalWorldErrorAfter
    ) {
        public static ReseatResult failed(TargetType targetType, BlockPos pos, String action) {
            return new ReseatResult(targetType, pos, false, action, null, null,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
    }

    public static TargetType identify(BlockEntity blockEntity) {
        if (blockEntity instanceof WindRotoVerticalBlockEntity) {
            return TargetType.WRVB;
        }
        if (blockEntity instanceof WindRotoBlockEntity) {
            return TargetType.WRB;
        }
        if (blockEntity instanceof ServoTwisterBlockEntity) {
            return TargetType.SERVO;
        }
        if (blockEntity instanceof InvServoTwisterBlockEntity) {
            return TargetType.INV_SERVO;
        }
        return null;
    }

    public static boolean anyAutoReseatOnLoadEnabled() {
        return TwisterMillConfig.isAutoReseatWrvbOnLoadEnabled()
                || TwisterMillConfig.isAutoReseatWrbOnLoadEnabled()
                || TwisterMillConfig.isAutoReseatServoOnLoadEnabled()
                || TwisterMillConfig.isAutoReseatInvServoOnLoadEnabled();
    }

    public static boolean isAutoEnabled(TargetType targetType) {
        return switch (targetType) {
            case WRVB -> TwisterMillConfig.isAutoReseatWrvbOnLoadEnabled();
            case WRB -> TwisterMillConfig.isAutoReseatWrbOnLoadEnabled();
            case SERVO -> TwisterMillConfig.isAutoReseatServoOnLoadEnabled();
            case INV_SERVO -> TwisterMillConfig.isAutoReseatInvServoOnLoadEnabled();
        };
    }

    public static ReseatResult reseatTargeted(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        return reseat(blockEntity, Trigger.MANUAL_COMMAND, true);
    }

    public static ReseatResult reseatAuto(BlockEntity blockEntity, Trigger trigger) {
        return reseat(blockEntity, trigger, false);
    }

    private static ReseatResult reseat(BlockEntity blockEntity, Trigger trigger, boolean bypassAutoConfig) {
        TargetType targetType = identify(blockEntity);
        if (targetType == null) {
            return null;
        }
        if (trigger.automatic() && !bypassAutoConfig && !isAutoEnabled(targetType)) {
            return ReseatResult.failed(targetType, blockEntity.getBlockPos(), "auto-disabled");
        }

        if (blockEntity instanceof WindRotoVerticalBlockEntity wrvb) {
            return wrvb.reseatFromDiagnostics(trigger);
        }
        if (blockEntity instanceof WindRotoBlockEntity wrb) {
            return wrb.reseatFromDiagnostics(trigger);
        }
        if (blockEntity instanceof ServoTwisterBlockEntity servo) {
            return servo.reseatFromDiagnostics(trigger);
        }
        if (blockEntity instanceof InvServoTwisterBlockEntity invServo) {
            return invServo.reseatFromDiagnostics(trigger);
        }
        return ReseatResult.failed(targetType, blockEntity.getBlockPos(), "unsupported-target");
    }

    public static String failureText(String action) {
        if (action == null || action.isBlank()) {
            return "unknown failure";
        }
        return switch (action) {
            case "inactive" -> "no active Sable sublevel";
            case "not-server-level" -> "target is not on a server level";
            case "container-unavailable" -> "Sable container unavailable";
            case "constraint-invalid" -> "constraint handle is missing or invalid";
            default -> failureTextWithPrefix(action);
        };
    }

    private static String failureTextWithPrefix(String action) {
        String normalized = action;
        if (normalized.startsWith("manual-command-")) {
            normalized = normalized.substring("manual-command-".length());
        } else if (normalized.startsWith("auto-reseat-")) {
            normalized = normalized.substring("auto-reseat-".length());
        }

        return switch (normalized) {
            case "frame-unavailable" -> "anchor frame unavailable";
            case "frame-nonfinite" -> "anchor frame contains invalid values";
            case "anchor-error-too-large" -> "anchor error is outside the command safety limit";
            case "normal-error-too-large" -> "normal error is outside the command safety limit";
            case "constraint-reattach-failed" -> "constraint could not be reattached after pose reseat";
            case "zero-pose-reseat" -> "pose reseat was applied";
            default -> normalized.startsWith("resolve-")
                    ? "active sublevel could not be resolved (" + normalized.substring("resolve-".length()) + ")"
                    : action;
        };
    }

    public static String formatBlockPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    public static String formatDouble(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.5f", value) : "n/a";
    }

    public static String formatFloat(Float value) {
        return value == null || !Float.isFinite(value) ? "n/a" : String.format(Locale.ROOT, "%.2f", value);
    }
}
