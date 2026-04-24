package com.proventure.twistermill.debug;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ServoTwisterDebugTraceBuffer {

    public static final int TRACE_WINDOW_TICKS = 200;
    public static final int TRACE_SAMPLE_INTERVAL_TICKS = 5;

    public static final int TYPE_SERVO = 0;
    public static final int TYPE_INV_SERVO = 1;

    public static final int FLAG_RUNNING = 1;
    public static final int FLAG_ASSEMBLE_QUEUED = 1 << 1;
    public static final int FLAG_ASSEMBLED = 1 << 2;
    public static final int FLAG_MANUAL_ENABLED = 1 << 3;
    public static final int FLAG_NEEDS_STATE_REFRESH = 1 << 4;
    public static final int FLAG_BOUND_TO_WIND_ROTO = 1 << 5;
    public static final int FLAG_VISUAL_RUNNING = 1 << 6;
    public static final int FLAG_HAS_NETWORK = 1 << 7;
    public static final int FLAG_SOURCE_PRESENT = 1 << 8;

    private static final int RING_CAPACITY = 4096;
    private static final Object LOCK = new Object();

    private static final long[] SEQUENCE = new long[RING_CAPACITY];
    private static final long[] GAME_TIME = new long[RING_CAPACITY];
    private static final long[] BLOCK_POS = new long[RING_CAPACITY];
    private static final int[] DIMENSION_ID = new int[RING_CAPACITY];
    private static final int[] ENTITY_TYPE = new int[RING_CAPACITY];
    private static final int[] FLAGS = new int[RING_CAPACITY];

    private static final int[] CONTRAPTION_BLOCK_COUNT = new int[RING_CAPACITY];
    private static final int[] LAST_WEST_SIGNAL = new int[RING_CAPACITY];
    private static final int[] LAST_EAST_SIGNAL = new int[RING_CAPACITY];
    private static final int[] LAST_SOUTH_SIGNAL = new int[RING_CAPACITY];
    private static final int[] PENDING_WEST_SIGNAL = new int[RING_CAPACITY];
    private static final int[] PENDING_EAST_SIGNAL = new int[RING_CAPACITY];
    private static final int[] PENDING_SOUTH_SIGNAL = new int[RING_CAPACITY];
    private static final int[] PENDING_WEST_TICKS = new int[RING_CAPACITY];
    private static final int[] PENDING_EAST_TICKS = new int[RING_CAPACITY];
    private static final int[] PENDING_SOUTH_TICKS = new int[RING_CAPACITY];
    private static final int[] DISPLAY_WEST_SIGNAL = new int[RING_CAPACITY];
    private static final int[] DISPLAY_EAST_SIGNAL = new int[RING_CAPACITY];
    private static final int[] DISPLAY_SOUTH_SIGNAL = new int[RING_CAPACITY];
    private static final int[] PENDING_DISPLAY_WEST_SIGNAL = new int[RING_CAPACITY];
    private static final int[] PENDING_DISPLAY_EAST_SIGNAL = new int[RING_CAPACITY];
    private static final int[] PENDING_DISPLAY_SOUTH_SIGNAL = new int[RING_CAPACITY];
    private static final int[] PENDING_DISPLAY_WEST_TICKS = new int[RING_CAPACITY];
    private static final int[] PENDING_DISPLAY_EAST_TICKS = new int[RING_CAPACITY];
    private static final int[] PENDING_DISPLAY_SOUTH_TICKS = new int[RING_CAPACITY];
    private static final int[] CONFIGURED_MAX_DEGREES = new int[RING_CAPACITY];
    private static final int[] EFFECTIVE_MAX_DEGREES = new int[RING_CAPACITY];
    private static final int[] MOVEMENT_MODE_VALUE = new int[RING_CAPACITY];
    private static final int[] FACING_ORDINAL = new int[RING_CAPACITY];
    private static final int[] WEST_INPUT_ORDINAL = new int[RING_CAPACITY];
    private static final int[] EAST_INPUT_ORDINAL = new int[RING_CAPACITY];
    private static final int[] SOUTH_INPUT_ORDINAL = new int[RING_CAPACITY];

    private static final long[] LAST_RUNTIME_DIRTY_GAME_TIME = new long[RING_CAPACITY];

    private static final float[] ANGLE = new float[RING_CAPACITY];
    private static final float[] TARGET_ANGLE = new float[RING_CAPACITY];
    private static final float[] BINDING_ANGLE = new float[RING_CAPACITY];
    private static final float[] GENERATED_SPEED = new float[RING_CAPACITY];

    private static int writeIndex = 0;
    private static int size = 0;
    private static long nextSequence = 1L;

    private static final Map<String, Integer> DIMENSION_TO_ID = new HashMap<>();
    private static final Map<Integer, String> ID_TO_DIMENSION = new HashMap<>();
    private static int nextDimensionId = 1;

    @Nullable
    private static volatile Path logsDirectory;

    private ServoTwisterDebugTraceBuffer() {
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
        synchronized (LOCK) {
            int i = writeIndex;

            SEQUENCE[i] = nextSequence++;
            GAME_TIME[i] = gameTime;
            BLOCK_POS[i] = blockPos;
            DIMENSION_ID[i] = dimensionId;
            ENTITY_TYPE[i] = entityType;
            FLAGS[i] = flags;

            CONTRAPTION_BLOCK_COUNT[i] = contraptionBlockCount;
            LAST_WEST_SIGNAL[i] = lastWestSignal;
            LAST_EAST_SIGNAL[i] = lastEastSignal;
            LAST_SOUTH_SIGNAL[i] = lastSouthSignal;
            PENDING_WEST_SIGNAL[i] = pendingWestSignal;
            PENDING_EAST_SIGNAL[i] = pendingEastSignal;
            PENDING_SOUTH_SIGNAL[i] = pendingSouthSignal;
            PENDING_WEST_TICKS[i] = pendingWestTicks;
            PENDING_EAST_TICKS[i] = pendingEastTicks;
            PENDING_SOUTH_TICKS[i] = pendingSouthTicks;
            DISPLAY_WEST_SIGNAL[i] = displayWestSignal;
            DISPLAY_EAST_SIGNAL[i] = displayEastSignal;
            DISPLAY_SOUTH_SIGNAL[i] = displaySouthSignal;
            PENDING_DISPLAY_WEST_SIGNAL[i] = pendingDisplayWestSignal;
            PENDING_DISPLAY_EAST_SIGNAL[i] = pendingDisplayEastSignal;
            PENDING_DISPLAY_SOUTH_SIGNAL[i] = pendingDisplaySouthSignal;
            PENDING_DISPLAY_WEST_TICKS[i] = pendingDisplayWestTicks;
            PENDING_DISPLAY_EAST_TICKS[i] = pendingDisplayEastTicks;
            PENDING_DISPLAY_SOUTH_TICKS[i] = pendingDisplaySouthTicks;
            CONFIGURED_MAX_DEGREES[i] = configuredMaxDegrees;
            EFFECTIVE_MAX_DEGREES[i] = effectiveMaxDegrees;
            MOVEMENT_MODE_VALUE[i] = movementModeValue;
            FACING_ORDINAL[i] = facingOrdinal;
            WEST_INPUT_ORDINAL[i] = westInputOrdinal;
            EAST_INPUT_ORDINAL[i] = eastInputOrdinal;
            SOUTH_INPUT_ORDINAL[i] = southInputOrdinal;

            LAST_RUNTIME_DIRTY_GAME_TIME[i] = lastRuntimeDirtyGameTime;

            ANGLE[i] = angle;
            TARGET_ANGLE[i] = targetAngle;
            BINDING_ANGLE[i] = bindingAngle;
            GENERATED_SPEED[i] = generatedSpeed;

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
            List<ServoTwisterDebugSnapshot> snapshots = new ArrayList<>(size);

            for (int n = 0; n < size; n++) {
                int i = (oldestIndex + n) % RING_CAPACITY;

                if (GAME_TIME[i] < minimumGameTime) {
                    continue;
                }

                snapshots.add(new ServoTwisterDebugSnapshot(
                        SEQUENCE[i],
                        GAME_TIME[i],
                        BLOCK_POS[i],
                        DIMENSION_ID[i],
                        ENTITY_TYPE[i],
                        FLAGS[i],
                        CONTRAPTION_BLOCK_COUNT[i],
                        LAST_WEST_SIGNAL[i],
                        LAST_EAST_SIGNAL[i],
                        LAST_SOUTH_SIGNAL[i],
                        PENDING_WEST_SIGNAL[i],
                        PENDING_EAST_SIGNAL[i],
                        PENDING_SOUTH_SIGNAL[i],
                        PENDING_WEST_TICKS[i],
                        PENDING_EAST_TICKS[i],
                        PENDING_SOUTH_TICKS[i],
                        DISPLAY_WEST_SIGNAL[i],
                        DISPLAY_EAST_SIGNAL[i],
                        DISPLAY_SOUTH_SIGNAL[i],
                        PENDING_DISPLAY_WEST_SIGNAL[i],
                        PENDING_DISPLAY_EAST_SIGNAL[i],
                        PENDING_DISPLAY_SOUTH_SIGNAL[i],
                        PENDING_DISPLAY_WEST_TICKS[i],
                        PENDING_DISPLAY_EAST_TICKS[i],
                        PENDING_DISPLAY_SOUTH_TICKS[i],
                        CONFIGURED_MAX_DEGREES[i],
                        EFFECTIVE_MAX_DEGREES[i],
                        MOVEMENT_MODE_VALUE[i],
                        FACING_ORDINAL[i],
                        WEST_INPUT_ORDINAL[i],
                        EAST_INPUT_ORDINAL[i],
                        SOUTH_INPUT_ORDINAL[i],
                        LAST_RUNTIME_DIRTY_GAME_TIME[i],
                        ANGLE[i],
                        TARGET_ANGLE[i],
                        BINDING_ANGLE[i],
                        GENERATED_SPEED[i]
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
            List<ServoTwisterDebugSnapshot> snapshots,
            Map<Integer, String> dimensionNames,
            long newestGameTime,
            long minimumIncludedGameTime,
            int configuredWindowTicks
    ) {
    }
}
