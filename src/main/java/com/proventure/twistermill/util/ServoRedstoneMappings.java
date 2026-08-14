package com.proventure.twistermill.util;

import com.proventure.twistermill.config.TwisterMillConfig;
import net.minecraft.util.Mth;

public final class ServoRedstoneMappings {
    private static final float[] SPEED_DEGREES_PER_TICK = new float[]{
            0.10F, 0.25F, 0.50F, 1.125F,
            1.50F, 2.00F, 3.00F, 4.50F,
            6.00F, 7.50F, 9.00F, 10.00F,
            12.00F, 13.50F, 15.00F, 18.00F
    };

    private static final float[] ANGLE_60 = new float[]{
            0F, 1F, 2F, 4F,
            6F, 8F, 10F, 15F,
            20F, 25F, 30F, 35F,
            40F, 45F, 50F, 60F
    };

    private static final float[] ANGLE_120 = new float[]{
            0F, 64F, 68F, 72F,
            76F, 80F, 84F, 88F,
            92F, 96F, 100F, 104F,
            108F, 112F, 116F, 120F
    };

    private static final float[] ANGLE_240 = new float[]{
            0F, 128F, 136F, 144F,
            152F, 160F, 168F, 176F,
            184F, 192F, 200F, 208F,
            216F, 224F, 232F, 240F
    };

    private ServoRedstoneMappings() {
    }

    public static int clampSignal(int redstoneSignal) {
        return Mth.clamp(redstoneSignal, 0, 15);
    }

    @SuppressWarnings("unused")
    public static float speedDegreesPerTickFromSignal(int redstoneSignal) {
        return SPEED_DEGREES_PER_TICK[clampSignal(redstoneSignal)];
    }

    @SuppressWarnings("unused")
    public static float effectiveServoSpeedDegreesPerTickFromSignal(int redstoneSignal) {
        return effectiveSpeedDegreesPerTickFromSignal(redstoneSignal, TwisterMillConfig.isServoSpeedZeroMovementEnabled());
    }

    @SuppressWarnings("unused")
    public static float effectiveInvServoSpeedDegreesPerTickFromSignal(int redstoneSignal) {
        return effectiveSpeedDegreesPerTickFromSignal(redstoneSignal, TwisterMillConfig.isInvServoSpeedZeroMovementEnabled());
    }

    public static float effectiveSpeedDegreesPerTickFromSignal(int redstoneSignal, boolean speedZeroMoves) {
        int clampedSignal = clampSignal(redstoneSignal);
        if (clampedSignal == 0 && !speedZeroMoves) {
            return 0.0F;
        }
        return SPEED_DEGREES_PER_TICK[clampedSignal];
    }

    public static float baseAngleFromSignalAndConfiguredMax(int redstoneSignal, int configuredMaxDegrees) {
        int clampedSignal = clampSignal(redstoneSignal);
        if (configuredMaxDegrees <= 60) {
            return ANGLE_60[clampedSignal];
        }
        if (configuredMaxDegrees <= 120) {
            return ANGLE_120[clampedSignal];
        }
        return ANGLE_240[clampedSignal];
    }
}
