package com.proventure.twistermill.debug;

public record WindRotoDebugSnapshot(
        long sequence,
        long gameTime,
        long blockPos,
        int dimensionId,
        int flags,
        int generatedRpm,
        int targetRpm,
        int externalRedstone,
        int boundServoCount,
        int lastComparatorLevel,
        int contraptionBlockCount,
        int boundServoContraptionBlocks,
        int forcedChunkX,
        int forcedChunkZ,
        int lastSentRpm,
        int lastSentBoundServoCount,
        int lastSentBoundServoContraptionBlocks,
        float generatedSu,
        float rawWind,
        float smoothedWind,
        float averageServoAngle,
        float servoSuMultiplier,
        float generatedSpeed,
        float bearingAngle,
        float lastSentWindSpeed,
        float lastSentWindSmoothed,
        float lastSentGeneratedSu,
        float lastSentAverageServoAngle,
        float lastSentServoSuMultiplier,
        long nextOutsideCheckAt,
        long nextWindSampleAt,
        long nextRampAt,
        long nextServoSampleAt,
        long nextStuckOverstressRebuildAt
) {
    public boolean hasFlag(int flagMask) {
        return (flags & flagMask) != 0;
    }
}
