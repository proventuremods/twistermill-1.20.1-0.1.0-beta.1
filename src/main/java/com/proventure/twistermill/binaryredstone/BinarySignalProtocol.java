package com.proventure.twistermill.binaryredstone;

public final class BinarySignalProtocol {

    private BinarySignalProtocol() {
    }

    public static final int FRAME_HALF_PHASE_TICKS = 3;
    public static final int FRAME_START_MIN_TICKS = 2;
    public static final int FRAME_START_MAX_TICKS = 4;
    public static final int FRAME_BITS = 12;

    public static final int CONTROL_MARKER_HIGH_PHASES = 2;
    public static final int CONTROL_MARKER_HIGH_TICKS = 9;
    public static final int CONTROL_MARKER_LOW_TICKS = 9;
    public static final int CONTROL_MARKER_MIN_HIGH_TICKS = 8;
    public static final int CONTROL_MARKER_MAX_HIGH_TICKS = 10;
    public static final int CONTROL_MARKER_MIN_LOW_GAP_TICKS = 8;
    public static final int CONTROL_MARKER_MAX_LOW_GAP_TICKS = 10;
    public static final int CONTROL_MARKER_RECEIVE_TIMEOUT_TICKS = 40;
    public static final int CONTROL_MARKER_TOTAL_TICKS =
            CONTROL_MARKER_HIGH_TICKS * CONTROL_MARKER_HIGH_PHASES + CONTROL_MARKER_LOW_TICKS;

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "ConstantValue"})
    public static boolean isControlMarkerConfigurationSafe() {
        return CONTROL_MARKER_MIN_HIGH_TICKS > FRAME_START_MAX_TICKS
                && CONTROL_MARKER_MIN_HIGH_TICKS > FRAME_HALF_PHASE_TICKS
                && CONTROL_MARKER_TOTAL_TICKS <= CONTROL_MARKER_RECEIVE_TIMEOUT_TICKS;
    }

    public static boolean isControlMarkerHighTicksInRange(int ticks) {
        return ticks >= CONTROL_MARKER_MIN_HIGH_TICKS && ticks <= CONTROL_MARKER_MAX_HIGH_TICKS;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isControlMarkerLowGapTicksInRange(int ticks) {
        return ticks >= CONTROL_MARKER_MIN_LOW_GAP_TICKS && ticks <= CONTROL_MARKER_MAX_LOW_GAP_TICKS;
    }
}
