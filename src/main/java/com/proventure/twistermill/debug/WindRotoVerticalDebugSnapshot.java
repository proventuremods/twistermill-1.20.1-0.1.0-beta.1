package com.proventure.twistermill.debug;

public record WindRotoVerticalDebugSnapshot(
        long sequence,
        long gameTime,
        long blockPos,
        int dimensionId,
        int flags,
        int externalRedstone,
        int placementNorthDirData,
        int obsidianCheckTimer,
        int assemblyModeValue,
        int generatedRpm,
        int lastSentGeneratedRpm,
        int lastSentPlacementNorthDirData,
        int forcedChunkX,
        int forcedChunkZ,
        float generatedSpeedRpm,
        float generatedSu,
        float currentYawDeg,
        float targetYawDeg,
        float yawVelocityDegPerTick,
        float worldWindAngleDeg,
        float localTargetYawDeg,
        float lastSentGeneratedSpeedRpm,
        float lastSentGeneratedSu,
        float lastSentCurrentYawDeg,
        float lastSentTargetYawDeg,
        float lastSentYawVelocityDegPerTick,
        float lastSentWorldWindAngleDeg,
        float lastSentLocalTargetYawDeg,
        float bearingAngle,
        long nextWindAngleSampleAt,
        long verticalPulseStartTick,
        long verticalPulseCooldownUntil,
        long pulseCooldownRemaining,
        long lastRuntimeDirtyGameTime
) {
    public boolean hasFlag(int flagMask) {
        return (flags & flagMask) != 0;
    }
}
