package com.proventure.twistermill.debug;

public record ServoTwisterDebugSnapshot(
        long sequence,
        long gameTime,
        long blockPos,
        int dimensionId,
        int entityType,
        int flags,
        int contraptionBlockCount,
        int lastWestSignal,
        int lastEastSignal,
        int lastSouthSignal,
        int pendingWestSignal,
        int pendingEastSignal,
        int pendingSouthSignal,
        int pendingWestTicks,
        int pendingEastTicks,
        int pendingSouthTicks,
        int displayWestSignal,
        int displayEastSignal,
        int displaySouthSignal,
        int pendingDisplayWestSignal,
        int pendingDisplayEastSignal,
        int pendingDisplaySouthSignal,
        int pendingDisplayWestTicks,
        int pendingDisplayEastTicks,
        int pendingDisplaySouthTicks,
        int configuredMaxDegrees,
        int effectiveMaxDegrees,
        int movementModeValue,
        int facingOrdinal,
        int westInputOrdinal,
        int eastInputOrdinal,
        int southInputOrdinal,
        long lastRuntimeDirtyGameTime,
        float angle,
        float targetAngle,
        float bindingAngle,
        float generatedSpeed
) {
    public boolean hasFlag(int flagMask) {
        return (flags & flagMask) != 0;
    }
}
