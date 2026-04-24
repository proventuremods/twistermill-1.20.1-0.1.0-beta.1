package com.proventure.twistermill.debug;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WindRotoDebugTraceBuffer {

    public static final int TRACE_WINDOW_TICKS = 200;
    public static final int TRACE_SAMPLE_INTERVAL_TICKS = 5;

    public static final int FLAG_RUNNING = 1;
    public static final int FLAG_ASSEMBLE_QUEUED = 1 << 1;
    public static final int FLAG_ASSEMBLED = 1 << 2;
    public static final int FLAG_OUTSIDE = 1 << 3;
    public static final int FLAG_STOPPED_BY_REDSTONE = 1 << 4;
    public static final int FLAG_OVERSTRESSED = 1 << 5;
    public static final int FLAG_NEEDS_INIT = 1 << 6;
    public static final int FLAG_CHUNK_FORCED = 1 << 7;
    public static final int FLAG_VISUAL_RUNNING = 1 << 8;
    public static final int FLAG_HAS_NETWORK = 1 << 9;
    public static final int FLAG_SOURCE_PRESENT = 1 << 10;
    public static final int FLAG_LAST_SENT_OUTSIDE = 1 << 11;
    public static final int FLAG_LAST_SENT_VISUAL_RUNNING = 1 << 12;

    private static final int RING_CAPACITY = 4096;
    private static final Object LOCK = new Object();

    private static final long[] SEQUENCE = new long[RING_CAPACITY];
    private static final long[] GAME_TIME = new long[RING_CAPACITY];
    private static final long[] BLOCK_POS = new long[RING_CAPACITY];
    private static final int[] DIMENSION_ID = new int[RING_CAPACITY];
    private static final int[] FLAGS = new int[RING_CAPACITY];

    private static final int[] GENERATED_RPM = new int[RING_CAPACITY];
    private static final int[] TARGET_RPM = new int[RING_CAPACITY];
    private static final int[] EXTERNAL_REDSTONE = new int[RING_CAPACITY];
    private static final int[] BOUND_SERVO_COUNT = new int[RING_CAPACITY];
    private static final int[] LAST_COMPARATOR_LEVEL = new int[RING_CAPACITY];
    private static final int[] CONTRAPTION_BLOCK_COUNT = new int[RING_CAPACITY];
    private static final int[] BOUND_SERVO_CONTRAPTION_BLOCKS = new int[RING_CAPACITY];
    private static final int[] FORCED_CHUNK_X = new int[RING_CAPACITY];
    private static final int[] FORCED_CHUNK_Z = new int[RING_CAPACITY];
    private static final int[] LAST_SENT_RPM = new int[RING_CAPACITY];
    private static final int[] LAST_SENT_BOUND_SERVO_COUNT = new int[RING_CAPACITY];
    private static final int[] LAST_SENT_BOUND_SERVO_CONTRAPTION_BLOCKS = new int[RING_CAPACITY];

    private static final float[] GENERATED_SU = new float[RING_CAPACITY];
    private static final float[] RAW_WIND = new float[RING_CAPACITY];
    private static final float[] SMOOTHED_WIND = new float[RING_CAPACITY];
    private static final float[] AVERAGE_SERVO_ANGLE = new float[RING_CAPACITY];
    private static final float[] SERVO_SU_MULTIPLIER = new float[RING_CAPACITY];
    private static final float[] GENERATED_SPEED = new float[RING_CAPACITY];
    private static final float[] BEARING_ANGLE = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_WIND_SPEED = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_WIND_SMOOTHED = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_GENERATED_SU = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_AVERAGE_SERVO_ANGLE = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_SERVO_SU_MULTIPLIER = new float[RING_CAPACITY];

    private static final long[] NEXT_OUTSIDE_CHECK_AT = new long[RING_CAPACITY];
    private static final long[] NEXT_WIND_SAMPLE_AT = new long[RING_CAPACITY];
    private static final long[] NEXT_RAMP_AT = new long[RING_CAPACITY];
    private static final long[] NEXT_SERVO_SAMPLE_AT = new long[RING_CAPACITY];
    private static final long[] NEXT_STUCK_OVERSTRESS_REBUILD_AT = new long[RING_CAPACITY];

    private static int writeIndex = 0;
    private static int size = 0;
    private static long nextSequence = 1L;

    private static final Map<String, Integer> DIMENSION_TO_ID = new HashMap<>();
    private static final Map<Integer, String> ID_TO_DIMENSION = new HashMap<>();
    private static int nextDimensionId = 1;

    @Nullable
    private static volatile Path logsDirectory;

    private WindRotoDebugTraceBuffer() {
    }

    public static int getOrCreateDimensionId(ResourceLocation dimensionLocation) {
        String key = dimensionLocation.toString();

        synchronized (LOCK) {
            Integer existing = DIMENSION_TO_ID.get(key);
            if (existing != null) {
                return existing;
            }

            int newId = nextDimensionId++;
            DIMENSION_TO_ID.put(key, newId);
            ID_TO_DIMENSION.put(newId, key);
            return newId;
        }
    }

    public static void setLogsDirectory(Path path) {
        logsDirectory = path;
    }

    @Nullable
    public static Path getLogsDirectory() {
        return logsDirectory;
    }

    public static void record(
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
        synchronized (LOCK) {
            int i = writeIndex;

            SEQUENCE[i] = nextSequence++;
            GAME_TIME[i] = gameTime;
            BLOCK_POS[i] = blockPos;
            DIMENSION_ID[i] = dimensionId;
            FLAGS[i] = flags;

            GENERATED_RPM[i] = generatedRpm;
            TARGET_RPM[i] = targetRpm;
            EXTERNAL_REDSTONE[i] = externalRedstone;
            BOUND_SERVO_COUNT[i] = boundServoCount;
            LAST_COMPARATOR_LEVEL[i] = lastComparatorLevel;
            CONTRAPTION_BLOCK_COUNT[i] = contraptionBlockCount;
            BOUND_SERVO_CONTRAPTION_BLOCKS[i] = boundServoContraptionBlocks;
            FORCED_CHUNK_X[i] = forcedChunkX;
            FORCED_CHUNK_Z[i] = forcedChunkZ;
            LAST_SENT_RPM[i] = lastSentRpm;
            LAST_SENT_BOUND_SERVO_COUNT[i] = lastSentBoundServoCount;
            LAST_SENT_BOUND_SERVO_CONTRAPTION_BLOCKS[i] = lastSentBoundServoContraptionBlocks;

            GENERATED_SU[i] = generatedSu;
            RAW_WIND[i] = rawWind;
            SMOOTHED_WIND[i] = smoothedWind;
            AVERAGE_SERVO_ANGLE[i] = averageServoAngle;
            SERVO_SU_MULTIPLIER[i] = servoSuMultiplier;
            GENERATED_SPEED[i] = generatedSpeed;
            BEARING_ANGLE[i] = bearingAngle;
            LAST_SENT_WIND_SPEED[i] = lastSentWindSpeed;
            LAST_SENT_WIND_SMOOTHED[i] = lastSentWindSmoothed;
            LAST_SENT_GENERATED_SU[i] = lastSentGeneratedSu;
            LAST_SENT_AVERAGE_SERVO_ANGLE[i] = lastSentAverageServoAngle;
            LAST_SENT_SERVO_SU_MULTIPLIER[i] = lastSentServoSuMultiplier;

            NEXT_OUTSIDE_CHECK_AT[i] = nextOutsideCheckAt;
            NEXT_WIND_SAMPLE_AT[i] = nextWindSampleAt;
            NEXT_RAMP_AT[i] = nextRampAt;
            NEXT_SERVO_SAMPLE_AT[i] = nextServoSampleAt;
            NEXT_STUCK_OVERSTRESS_REBUILD_AT[i] = nextStuckOverstressRebuildAt;

            writeIndex = (writeIndex + 1) % RING_CAPACITY;
            if (size < RING_CAPACITY) {
                size++;
            }
        }
    }

    public static TraceWindow captureWindow() {
        synchronized (LOCK) {
            if (size <= 0) {
                return new TraceWindow(List.of(), Map.copyOf(ID_TO_DIMENSION), -1L, -1L, TRACE_WINDOW_TICKS);
            }

            int newestIndex = (writeIndex - 1 + RING_CAPACITY) % RING_CAPACITY;
            long newestGameTime = GAME_TIME[newestIndex];
            long minimumGameTime = Math.max(0L, newestGameTime - TRACE_WINDOW_TICKS);

            int oldestIndex = (writeIndex - size + RING_CAPACITY) % RING_CAPACITY;
            List<WindRotoDebugSnapshot> snapshots = new ArrayList<>(size);

            for (int n = 0; n < size; n++) {
                int i = (oldestIndex + n) % RING_CAPACITY;

                if (GAME_TIME[i] < minimumGameTime) {
                    continue;
                }

                snapshots.add(new WindRotoDebugSnapshot(
                        SEQUENCE[i],
                        GAME_TIME[i],
                        BLOCK_POS[i],
                        DIMENSION_ID[i],
                        FLAGS[i],
                        GENERATED_RPM[i],
                        TARGET_RPM[i],
                        EXTERNAL_REDSTONE[i],
                        BOUND_SERVO_COUNT[i],
                        LAST_COMPARATOR_LEVEL[i],
                        CONTRAPTION_BLOCK_COUNT[i],
                        BOUND_SERVO_CONTRAPTION_BLOCKS[i],
                        FORCED_CHUNK_X[i],
                        FORCED_CHUNK_Z[i],
                        LAST_SENT_RPM[i],
                        LAST_SENT_BOUND_SERVO_COUNT[i],
                        LAST_SENT_BOUND_SERVO_CONTRAPTION_BLOCKS[i],
                        GENERATED_SU[i],
                        RAW_WIND[i],
                        SMOOTHED_WIND[i],
                        AVERAGE_SERVO_ANGLE[i],
                        SERVO_SU_MULTIPLIER[i],
                        GENERATED_SPEED[i],
                        BEARING_ANGLE[i],
                        LAST_SENT_WIND_SPEED[i],
                        LAST_SENT_WIND_SMOOTHED[i],
                        LAST_SENT_GENERATED_SU[i],
                        LAST_SENT_AVERAGE_SERVO_ANGLE[i],
                        LAST_SENT_SERVO_SU_MULTIPLIER[i],
                        NEXT_OUTSIDE_CHECK_AT[i],
                        NEXT_WIND_SAMPLE_AT[i],
                        NEXT_RAMP_AT[i],
                        NEXT_SERVO_SAMPLE_AT[i],
                        NEXT_STUCK_OVERSTRESS_REBUILD_AT[i]
                ));
            }

            return new TraceWindow(
                    List.copyOf(snapshots),
                    Map.copyOf(ID_TO_DIMENSION),
                    newestGameTime,
                    minimumGameTime,
                    TRACE_WINDOW_TICKS
            );
        }
    }

    public record TraceWindow(
            List<WindRotoDebugSnapshot> snapshots,
            Map<Integer, String> dimensionNames,
            long newestGameTime,
            long minimumIncludedGameTime,
            int configuredWindowTicks
    ) {
    }
}
