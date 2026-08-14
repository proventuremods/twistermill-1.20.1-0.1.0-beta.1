package com.proventure.twistermill.weather;

import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class WeatherSailForceMath {

    private static final double EPSILON = 1.0E-9D;
    private static final double PEAK_SIDE_ANGLE_DEGREES = 55.0D;
    private static final double EDGE_ANGLE_RANGE_DEGREES = 35.0D;
    private static final double PEAK_SIDE_DIRECTION_SHAPE_FACTOR = 0.45D;
    private static final double EDGE_DIRECTION_SHAPE_FACTOR = 0.10D;
    private static final double PITCH_EFFICIENCY_AT_FEATHERED_THRESHOLD = 0.10D;
    private static final double MIN_PEAK_PITCH_EFFICIENCY = 0.10D;
    private static final double MAX_PEAK_PITCH_EFFICIENCY = 2.00D;
    private static final double FEATHERED_THRESHOLD_RATE = 5.0D / 6.0D;

    private WeatherSailForceMath() {
    }

    public static boolean compute(
            Vector3dc windDirection,
            Vector3dc sailNormal,
            double windSpeed,
            double coefficient,
            double minimumExposure,
            double maximumForcePerBlock,
            double peakPitchEfficiency,
            double peakPitchAngleDegrees,
            Result result
    ) {
        result.clear();

        if (!isFinite(windDirection)
                || !isFinite(sailNormal)
                || !Double.isFinite(windSpeed)
                || !Double.isFinite(coefficient)
                || !Double.isFinite(minimumExposure)
                || !Double.isFinite(maximumForcePerBlock)
                || !Double.isFinite(peakPitchEfficiency)
                || !Double.isFinite(peakPitchAngleDegrees)
                || windSpeed <= 0.0D
                || coefficient <= 0.0D
                || maximumForcePerBlock <= 0.0D
                || peakPitchEfficiency < MIN_PEAK_PITCH_EFFICIENCY
                || peakPitchEfficiency > MAX_PEAK_PITCH_EFFICIENCY
                || peakPitchAngleDegrees <= 0.0D
                || peakPitchAngleDegrees >= 90.0D) {
            return false;
        }

        double windLengthSquared = windDirection.lengthSquared();
        double normalLengthSquared = sailNormal.lengthSquared();
        if (windLengthSquared <= EPSILON || normalLengthSquared <= EPSILON) {
            return false;
        }

        result.windDirection.set(windDirection).div(Math.sqrt(windLengthSquared));
        result.canonicalNormal.set(sailNormal).div(Math.sqrt(normalLengthSquared));

        double facingDot = result.windDirection.dot(result.canonicalNormal);
        if (facingDot < 0.0D) {
            result.canonicalNormal.negate();
            facingDot = -facingDot;
        }
        facingDot = clamp(facingDot, 0.0D, 1.0D);

        result.pitchDegrees = Math.toDegrees(Math.acos(facingDot));
        computeDirectionalFactors(result.pitchDegrees, result);

        result.sideDirection.set(result.canonicalNormal)
                .fma(-facingDot, result.windDirection);
        double sideLengthSquared = result.sideDirection.lengthSquared();
        if (sideLengthSquared > EPSILON) {
            result.sideDirection.div(Math.sqrt(sideLengthSquared));
        } else {
            result.sideDirection.zero();
            result.sideFactor = 0.0D;
        }

        result.effectiveExposure = Math.hypot(result.forwardFactor, result.sideFactor);
        double clampedMinimumExposure = clamp(minimumExposure, 0.0D, 1.0D);
        if (!Double.isFinite(result.effectiveExposure)
                || result.effectiveExposure <= EPSILON
                || result.effectiveExposure + EPSILON < clampedMinimumExposure) {
            return false;
        }

        double baseForce = windSpeed * windSpeed * coefficient;
        if (!Double.isFinite(baseForce) || baseForce <= 0.0D) {
            return false;
        }

        double pitchEfficiency = computePitchEfficiency(
                result.pitchDegrees,
                peakPitchEfficiency,
                peakPitchAngleDegrees
        );
        if (!Double.isFinite(pitchEfficiency) || pitchEfficiency <= 0.0D) {
            return false;
        }

        result.forceWorld.set(result.sideDirection);
        double directionMagnitude = result.forceWorld.length();
        if (!Double.isFinite(directionMagnitude) || directionMagnitude <= EPSILON) {
            result.forceWorld.zero();
            return false;
        }
        result.forceWorld.div(directionMagnitude);

        double referenceForceMagnitude = Math.min(baseForce, maximumForcePerBlock);
        double forceMagnitude = referenceForceMagnitude * pitchEfficiency;
        if (!Double.isFinite(forceMagnitude) || forceMagnitude <= EPSILON) {
            result.forceWorld.zero();
            return false;
        }

        result.forceWorld.mul(forceMagnitude);
        result.referenceForceMagnitude = referenceForceMagnitude;
        result.forceMagnitude = forceMagnitude;
        return true;
    }

    private static void computeDirectionalFactors(double pitchDegrees, Result result) {
        double clampedPitch = clamp(pitchDegrees, 0.0D, 90.0D);
        if (clampedPitch <= PEAK_SIDE_ANGLE_DEGREES) {
            double progress = smoothstep(clampedPitch / PEAK_SIDE_ANGLE_DEGREES);
            result.forwardFactor = 1.0D - progress;
            result.sideFactor = PEAK_SIDE_DIRECTION_SHAPE_FACTOR * progress;
            return;
        }

        double progress = smoothstep((clampedPitch - PEAK_SIDE_ANGLE_DEGREES) / EDGE_ANGLE_RANGE_DEGREES);
        double magnitude = PEAK_SIDE_DIRECTION_SHAPE_FACTOR
                - (PEAK_SIDE_DIRECTION_SHAPE_FACTOR - EDGE_DIRECTION_SHAPE_FACTOR) * progress;
        double deflectionRadians = Math.toRadians(90.0D * (1.0D - progress));
        result.forwardFactor = magnitude * Math.cos(deflectionRadians);
        result.sideFactor = magnitude * Math.sin(deflectionRadians);
    }

    private static double computePitchEfficiency(
            double pitchDegrees,
            double peakPitchEfficiency,
            double peakPitchAngleDegrees
    ) {
        if (!Double.isFinite(pitchDegrees) || pitchDegrees <= 0.0D) {
            return 0.0D;
        }

        double peakDegrees = clamp(peakPitchAngleDegrees, 1.0D, 89.0D);
        if (!Double.isFinite(peakDegrees) || peakDegrees <= 0.0D || peakDegrees >= 90.0D) {
            return 0.0D;
        }

        double featheredThreshold = peakDegrees + (90.0D - peakDegrees) * FEATHERED_THRESHOLD_RATE;
        if (!Double.isFinite(featheredThreshold) || featheredThreshold <= peakDegrees) {
            return 0.0D;
        }

        if (pitchDegrees <= peakDegrees) {
            return peakPitchEfficiency * (pitchDegrees / peakDegrees);
        }

        if (pitchDegrees <= featheredThreshold) {
            double featheredRange = featheredThreshold - peakDegrees;
            return peakPitchEfficiency
                    - (peakPitchEfficiency - PITCH_EFFICIENCY_AT_FEATHERED_THRESHOLD)
                    * ((pitchDegrees - peakDegrees) / featheredRange);
        }

        return 0.0D;
    }

    private static double smoothstep(double value) {
        double clamped = clamp(value, 0.0D, 1.0D);
        return clamped * clamped * (3.0D - 2.0D * clamped);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean isFinite(Vector3dc vector) {
        return Double.isFinite(vector.x())
                && Double.isFinite(vector.y())
                && Double.isFinite(vector.z());
    }

    public static final class Result {
        private final Vector3d windDirection = new Vector3d();
        private final Vector3d canonicalNormal = new Vector3d();
        private final Vector3d sideDirection = new Vector3d();
        private final Vector3d forceWorld = new Vector3d();
        private double pitchDegrees;
        private double forwardFactor;
        private double sideFactor;
        private double effectiveExposure;
        private double referenceForceMagnitude;
        private double forceMagnitude;

        public Vector3dc forceWorld() {
            return forceWorld;
        }

        public double pitchDegrees() {
            return pitchDegrees;
        }

        public double forwardFactor() {
            return forwardFactor;
        }

        public double sideFactor() {
            return sideFactor;
        }

        public double effectiveExposure() {
            return effectiveExposure;
        }

        public double referenceForceMagnitude() {
            return referenceForceMagnitude;
        }

        public double forceMagnitude() {
            return forceMagnitude;
        }

        private void clear() {
            windDirection.zero();
            canonicalNormal.zero();
            sideDirection.zero();
            forceWorld.zero();
            pitchDegrees = 0.0D;
            forwardFactor = 0.0D;
            sideFactor = 0.0D;
            effectiveExposure = 0.0D;
            referenceForceMagnitude = 0.0D;
            forceMagnitude = 0.0D;
        }
    }
}
