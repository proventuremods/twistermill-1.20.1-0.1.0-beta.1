package com.proventure.twistermill.diagnostics;

import com.proventure.twistermill.config.TwisterMillConfig;

import java.util.EnumSet;

public final class TwisterMillDiagnostics {

    private static final EnumSet<Target> RUNTIME_ENABLED = EnumSet.noneOf(Target.class);

    private TwisterMillDiagnostics() {
    }

    public enum Target {
        WRB,
        WRVB,
        SERVO,
        INV_SERVO
    }

    public static synchronized void setRuntimeEnabled(Target target, boolean enabled) {
        if (enabled) {
            RUNTIME_ENABLED.add(target);
        } else {
            RUNTIME_ENABLED.remove(target);
        }
    }

    public static synchronized boolean isRuntimeEnabled(Target target) {
        return RUNTIME_ENABLED.contains(target);
    }

    public static boolean isConfigEnabled(Target target) {
        return switch (target) {
            case WRB -> TwisterMillConfig.isWindRotoBlockDiagnosticsEnabled();
            case WRVB -> TwisterMillConfig.isWindRotoVerticalBlockDiagnosticsEnabled();
            case SERVO -> TwisterMillConfig.isServoDiagnosticsEnabled();
            case INV_SERVO -> TwisterMillConfig.isInvServoDiagnosticsEnabled();
        };
    }

    public static boolean isLegacyConfigEnabled(Target target) {
        return target == Target.SERVO && TwisterMillConfig.isServoSlotDiagnosticsEnabled();
    }

    public static boolean isLoggingEnabled(Target target) {
        return isRuntimeEnabled(target) || isConfigEnabled(target) || isLegacyConfigEnabled(target);
    }

    public static boolean isWrbLoggingEnabled() {
        return isLoggingEnabled(Target.WRB);
    }

    public static boolean isWrvbLoggingEnabled() {
        return isLoggingEnabled(Target.WRVB);
    }

    public static boolean isServoLoggingEnabled() {
        return isLoggingEnabled(Target.SERVO);
    }

    public static boolean isInvServoLoggingEnabled() {
        return isLoggingEnabled(Target.INV_SERVO);
    }
}
