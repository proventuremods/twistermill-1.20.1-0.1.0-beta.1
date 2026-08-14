package com.proventure.twistermill.util;

import net.minecraft.core.Direction;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

public final class ServoTwoAxisRotationMath {

    public static final float MAX_AXIS_DEGREES = 90.0F;
    private static final double MAX_HALF_VECTOR_LENGTH = Math.sin(Math.toRadians(MAX_AXIS_DEGREES) * 0.5D);

    private static final float[] AXIS_1_X = new float[Direction.values().length];
    private static final float[] AXIS_1_Y = new float[Direction.values().length];
    private static final float[] AXIS_1_Z = new float[Direction.values().length];
    private static final float[] AXIS_2_X = new float[Direction.values().length];
    private static final float[] AXIS_2_Y = new float[Direction.values().length];
    private static final float[] AXIS_2_Z = new float[Direction.values().length];
    private static final Quaterniond[] CANONICAL_FRAME = new Quaterniond[Direction.values().length];

    static {
        defineBasis(Direction.UP, 1, 0, 0, 0, 1, 0, 0, 0, 1);
        defineBasis(Direction.DOWN, 1, 0, 0, 0, -1, 0, 0, 0, -1);
        defineBasis(Direction.NORTH, -1, 0, 0, 0, 0, -1, 0, -1, 0);
        defineBasis(Direction.SOUTH, 1, 0, 0, 0, 0, 1, 0, -1, 0);
        defineBasis(Direction.EAST, 0, 0, -1, 1, 0, 0, 0, -1, 0);
        defineBasis(Direction.WEST, 0, 0, 1, -1, 0, 0, 0, -1, 0);
    }

    private ServoTwoAxisRotationMath() {
    }

    public static float magnitudeFromSignal(int signal) {
        return Math.min(ServoRedstoneMappings.clampSignal(signal), 9) * 10.0F;
    }

    public static double motorTargetRadiansX(float rawAxis1Degrees, float rawAxis2Degrees) {
        double halfX = halfSine(rawAxis1Degrees);
        return 2.0D * Math.asin(clampUnit(halfX * projectionScale(halfX, halfSine(rawAxis2Degrees))));
    }

    public static double motorTargetRadiansZ(float rawAxis1Degrees, float rawAxis2Degrees) {
        double halfZ = halfSine(rawAxis2Degrees);
        return 2.0D * Math.asin(clampUnit(halfZ * projectionScale(halfSine(rawAxis1Degrees), halfZ)));
    }

    public static Quaterniond setTargetQuaternion(
            float rawAxis1Degrees,
            float rawAxis2Degrees,
            Quaterniond destination
    ) {
        double halfX = halfSine(rawAxis1Degrees);
        double halfZ = halfSine(rawAxis2Degrees);
        double scale = projectionScale(halfX, halfZ);
        double x = halfX * scale;
        double z = halfZ * scale;
        double w = Math.sqrt(Math.max(0.0D, 1.0D - x * x - z * z));
        return destination.set(x, 0.0D, z, w).normalize();
    }

    public static Quaternionf setBlockMovementQuaternion(
            Direction facing,
            float rawAxis1Degrees,
            float rawAxis2Degrees,
            Quaternionf destination
    ) {
        double halfX = halfSine(rawAxis1Degrees);
        double halfZ = halfSine(rawAxis2Degrees);
        double scale = projectionScale(halfX, halfZ);
        double x = halfX * scale;
        double z = halfZ * scale;
        double w = Math.sqrt(Math.max(0.0D, 1.0D - x * x - z * z));
        int index = facing.ordinal();
        return destination.set(
                (float) (AXIS_1_X[index] * x + AXIS_2_X[index] * z),
                (float) (AXIS_1_Y[index] * x + AXIS_2_Y[index] * z),
                (float) (AXIS_1_Z[index] * x + AXIS_2_Z[index] * z),
                (float) w
        ).normalize();
    }

    public static Quaterniond setCanonicalFrame(Direction facing, Quaterniond destination) {
        return destination.set(CANONICAL_FRAME[facing.ordinal()]);
    }

    public static Vector3d setAxis1(Direction facing, Vector3d destination) {
        int index = facing.ordinal();
        return destination.set(AXIS_1_X[index], AXIS_1_Y[index], AXIS_1_Z[index]);
    }

    public static Vector3d setAxis2(Direction facing, Vector3d destination) {
        int index = facing.ordinal();
        return destination.set(AXIS_2_X[index], AXIS_2_Y[index], AXIS_2_Z[index]);
    }

    private static void defineBasis(
            Direction facing,
            double axis1X,
            double axis1Y,
            double axis1Z,
            double forwardX,
            double forwardY,
            double forwardZ,
            double axis2X,
            double axis2Y,
            double axis2Z
    ) {
        int index = facing.ordinal();
        AXIS_1_X[index] = (float) axis1X;
        AXIS_1_Y[index] = (float) axis1Y;
        AXIS_1_Z[index] = (float) axis1Z;
        AXIS_2_X[index] = (float) axis2X;
        AXIS_2_Y[index] = (float) axis2Y;
        AXIS_2_Z[index] = (float) axis2Z;

        Matrix3d frame = new Matrix3d();
        frame.setColumn(0, axis1X, axis1Y, axis1Z);
        frame.setColumn(1, forwardX, forwardY, forwardZ);
        frame.setColumn(2, axis2X, axis2Y, axis2Z);
        CANONICAL_FRAME[index] = new Quaterniond().setFromNormalized(frame).normalize();
    }

    private static double halfSine(float rawDegrees) {
        float finiteDegrees = Float.isFinite(rawDegrees) ? rawDegrees : 0.0F;
        double clampedDegrees = Math.clamp(finiteDegrees, -MAX_AXIS_DEGREES, MAX_AXIS_DEGREES);
        return Math.sin(Math.toRadians(clampedDegrees) * 0.5D);
    }

    private static double projectionScale(double halfX, double halfZ) {
        double length = Math.hypot(halfX, halfZ);
        return length > MAX_HALF_VECTOR_LENGTH && length > 0.0D
                ? MAX_HALF_VECTOR_LENGTH / length
                : 1.0D;
    }

    private static double clampUnit(double value) {
        return Math.clamp(value, -1.0D, 1.0D);
    }
}
