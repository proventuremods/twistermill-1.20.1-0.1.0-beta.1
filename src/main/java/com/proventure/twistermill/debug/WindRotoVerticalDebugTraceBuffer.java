package com.proventure.twistermill.debug;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WindRotoVerticalDebugTraceBuffer {

    public static final int TRACE_WINDOW_TICKS = 200;
    public static final int TRACE_SAMPLE_INTERVAL_TICKS = 5;

    public static final int FLAG_RUNNING = 1;
    public static final int FLAG_ASSEMBLE_QUEUED = 1 << 1;
    public static final int FLAG_ASSEMBLED = 1 << 2;
    public static final int FLAG_VERTICAL_MANUAL_ENABLED = 1 << 3;
    public static final int FLAG_RESTORE_FREE_MODE_AFTER_MANUAL_DISASSEMBLY = 1 << 4;
    public static final int FLAG_PLACEMENT_NORTH_VALID = 1 << 5;
    public static final int FLAG_VERTICAL_YAW_MOVING = 1 << 6;
    public static final int FLAG_VERTICAL_PARKED_MODE = 1 << 7;
    public static final int FLAG_VERTICAL_PULSE_POWERED = 1 << 8;
    public static final int FLAG_CHUNK_FORCED = 1 << 9;
    public static final int FLAG_VISUAL_RUNNING = 1 << 10;
    public static final int FLAG_MOVE_NEVER_PLACE_MODE_SELECTED = 1 << 11;
    public static final int FLAG_VERTICAL_CONTROL_ENABLED = 1 << 12;
    public static final int FLAG_VERTICAL_FACING_VALID = 1 << 13;
    public static final int FLAG_OVERSTRESSED = 1 << 14;
    public static final int FLAG_LAST_SENT_VISUAL_RUNNING = 1 << 15;
    public static final int FLAG_LAST_SENT_PLACEMENT_NORTH_VALID = 1 << 16;
    public static final int FLAG_LAST_SENT_VERTICAL_YAW_MOVING = 1 << 17;
    public static final int FLAG_LAST_SENT_VERTICAL_PARKED_MODE = 1 << 18;

    private static final int RING_CAPACITY = 4096;
    private static final Object LOCK = new Object();

    private static final long[] SEQUENCE = new long[RING_CAPACITY];
    private static final long[] GAME_TIME = new long[RING_CAPACITY];
    private static final long[] BLOCK_POS = new long[RING_CAPACITY];
    private static final int[] DIMENSION_ID = new int[RING_CAPACITY];
    private static final int[] FLAGS = new int[RING_CAPACITY];

    private static final int[] EXTERNAL_REDSTONE = new int[RING_CAPACITY];
    private static final int[] PLACEMENT_NORTH_DIR_DATA = new int[RING_CAPACITY];
    private static final int[] OBSIDIAN_CHECK_TIMER = new int[RING_CAPACITY];
    private static final int[] ASSEMBLY_MODE_VALUE = new int[RING_CAPACITY];
    private static final int[] GENERATED_RPM = new int[RING_CAPACITY];
    private static final int[] LAST_SENT_GENERATED_RPM = new int[RING_CAPACITY];
    private static final int[] LAST_SENT_PLACEMENT_NORTH_DIR_DATA = new int[RING_CAPACITY];
    private static final int[] FORCED_CHUNK_X = new int[RING_CAPACITY];
    private static final int[] FORCED_CHUNK_Z = new int[RING_CAPACITY];

    private static final float[] GENERATED_SPEED_RPM = new float[RING_CAPACITY];
    private static final float[] GENERATED_SU = new float[RING_CAPACITY];
    private static final float[] CURRENT_YAW_DEG = new float[RING_CAPACITY];
    private static final float[] TARGET_YAW_DEG = new float[RING_CAPACITY];
    private static final float[] YAW_VELOCITY_DEG_PER_TICK = new float[RING_CAPACITY];
    private static final float[] WORLD_WIND_ANGLE_DEG = new float[RING_CAPACITY];
    private static final float[] LOCAL_TARGET_YAW_DEG = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_GENERATED_SPEED_RPM = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_GENERATED_SU = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_CURRENT_YAW_DEG = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_TARGET_YAW_DEG = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_YAW_VELOCITY_DEG_PER_TICK = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_WORLD_WIND_ANGLE_DEG = new float[RING_CAPACITY];
    private static final float[] LAST_SENT_LOCAL_TARGET_YAW_DEG = new float[RING_CAPACITY];
    private static final float[] BEARING_ANGLE = new float[RING_CAPACITY];

    private static final long[] NEXT_WIND_ANGLE_SAMPLE_AT = new long[RING_CAPACITY];
    private static final long[] VERTICAL_PULSE_START_TICK = new long[RING_CAPACITY];
    private static final long[] VERTICAL_PULSE_COOLDOWN_UNTIL = new long[RING_CAPACITY];
    private static final long[] PULSE_COOLDOWN_REMAINING = new long[RING_CAPACITY];
    private static final long[] LAST_RUNTIME_DIRTY_GAME_TIME = new long[RING_CAPACITY];

    private static int writeIndex = 0;
    private static int size = 0;
    private static long nextSequence = 1L;

    private static final Map<String, Integer> DIMENSION_TO_ID = new HashMap<>();
    private static final Map<Integer, String> ID_TO_DIMENSION = new HashMap<>();
    private static int nextDimensionId = 1;

    @Nullable
    private static volatile Path logsDirectory;

    private WindRotoVerticalDebugTraceBuffer() {
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
        synchronized (LOCK) {
            int i = writeIndex;

            SEQUENCE[i] = nextSequence++;
            GAME_TIME[i] = gameTime;
            BLOCK_POS[i] = blockPos;
            DIMENSION_ID[i] = dimensionId;
            FLAGS[i] = flags;

            EXTERNAL_REDSTONE[i] = externalRedstone;
            PLACEMENT_NORTH_DIR_DATA[i] = placementNorthDirData;
            OBSIDIAN_CHECK_TIMER[i] = obsidianCheckTimer;
            ASSEMBLY_MODE_VALUE[i] = assemblyModeValue;
            GENERATED_RPM[i] = generatedRpm;
            LAST_SENT_GENERATED_RPM[i] = lastSentGeneratedRpm;
            LAST_SENT_PLACEMENT_NORTH_DIR_DATA[i] = lastSentPlacementNorthDirData;
            FORCED_CHUNK_X[i] = forcedChunkX;
            FORCED_CHUNK_Z[i] = forcedChunkZ;

            GENERATED_SPEED_RPM[i] = generatedSpeedRpm;
            GENERATED_SU[i] = generatedSu;
            CURRENT_YAW_DEG[i] = currentYawDeg;
            TARGET_YAW_DEG[i] = targetYawDeg;
            YAW_VELOCITY_DEG_PER_TICK[i] = yawVelocityDegPerTick;
            WORLD_WIND_ANGLE_DEG[i] = worldWindAngleDeg;
            LOCAL_TARGET_YAW_DEG[i] = localTargetYawDeg;
            LAST_SENT_GENERATED_SPEED_RPM[i] = lastSentGeneratedSpeedRpm;
            LAST_SENT_GENERATED_SU[i] = lastSentGeneratedSu;
            LAST_SENT_CURRENT_YAW_DEG[i] = lastSentCurrentYawDeg;
            LAST_SENT_TARGET_YAW_DEG[i] = lastSentTargetYawDeg;
            LAST_SENT_YAW_VELOCITY_DEG_PER_TICK[i] = lastSentYawVelocityDegPerTick;
            LAST_SENT_WORLD_WIND_ANGLE_DEG[i] = lastSentWorldWindAngleDeg;
            LAST_SENT_LOCAL_TARGET_YAW_DEG[i] = lastSentLocalTargetYawDeg;
            BEARING_ANGLE[i] = bearingAngle;

            NEXT_WIND_ANGLE_SAMPLE_AT[i] = nextWindAngleSampleAt;
            VERTICAL_PULSE_START_TICK[i] = verticalPulseStartTick;
            VERTICAL_PULSE_COOLDOWN_UNTIL[i] = verticalPulseCooldownUntil;
            PULSE_COOLDOWN_REMAINING[i] = pulseCooldownRemaining;
            LAST_RUNTIME_DIRTY_GAME_TIME[i] = lastRuntimeDirtyGameTime;

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
            List<WindRotoVerticalDebugSnapshot> snapshots = new ArrayList<>(size);

            for (int n = 0; n < size; n++) {
                int i = (oldestIndex + n) % RING_CAPACITY;

                if (GAME_TIME[i] < minimumGameTime) {
                    continue;
                }

                snapshots.add(new WindRotoVerticalDebugSnapshot(
                        SEQUENCE[i],
                        GAME_TIME[i],
                        BLOCK_POS[i],
                        DIMENSION_ID[i],
                        FLAGS[i],
                        EXTERNAL_REDSTONE[i],
                        PLACEMENT_NORTH_DIR_DATA[i],
                        OBSIDIAN_CHECK_TIMER[i],
                        ASSEMBLY_MODE_VALUE[i],
                        GENERATED_RPM[i],
                        LAST_SENT_GENERATED_RPM[i],
                        LAST_SENT_PLACEMENT_NORTH_DIR_DATA[i],
                        FORCED_CHUNK_X[i],
                        FORCED_CHUNK_Z[i],
                        GENERATED_SPEED_RPM[i],
                        GENERATED_SU[i],
                        CURRENT_YAW_DEG[i],
                        TARGET_YAW_DEG[i],
                        YAW_VELOCITY_DEG_PER_TICK[i],
                        WORLD_WIND_ANGLE_DEG[i],
                        LOCAL_TARGET_YAW_DEG[i],
                        LAST_SENT_GENERATED_SPEED_RPM[i],
                        LAST_SENT_GENERATED_SU[i],
                        LAST_SENT_CURRENT_YAW_DEG[i],
                        LAST_SENT_TARGET_YAW_DEG[i],
                        LAST_SENT_YAW_VELOCITY_DEG_PER_TICK[i],
                        LAST_SENT_WORLD_WIND_ANGLE_DEG[i],
                        LAST_SENT_LOCAL_TARGET_YAW_DEG[i],
                        BEARING_ANGLE[i],
                        NEXT_WIND_ANGLE_SAMPLE_AT[i],
                        VERTICAL_PULSE_START_TICK[i],
                        VERTICAL_PULSE_COOLDOWN_UNTIL[i],
                        PULSE_COOLDOWN_REMAINING[i],
                        LAST_RUNTIME_DIRTY_GAME_TIME[i]
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
            List<WindRotoVerticalDebugSnapshot> snapshots,
            Map<Integer, String> dimensionNames,
            long newestGameTime,
            long minimumIncludedGameTime,
            int configuredWindowTicks
    ) {
    }
}
