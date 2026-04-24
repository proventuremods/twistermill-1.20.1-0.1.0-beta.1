package com.proventure.twistermill.debug;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.CrashReportCallables;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WindRotoDebugDumpService {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Object DUMP_LOCK = new Object();
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final DateTimeFormatter HEADER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String[] WINDROTO_VERTICAL_COLUMN_KEYS = new String[] {
            "t",
            "dim",
            "pos",
            "running",
            "assembleQueued",
            "assembled",
            "manualEnabled",
            "restoreFreeMode",
            "placementNorthValid",
            "yawMoving",
            "parkedMode",
            "pulsePowered",
            "chunkForced",
            "visualRunning",
            "modeFreeRsOn",
            "controlEnabled",
            "verticalFacingValid",
            "overStressed",
            "sentVisualRunning",
            "sentPlacementValid",
            "sentYawMoving",
            "sentParkedMode",
            "redstone",
            "placementNorthDir",
            "obsidianCheckTimer",
            "assemblyModeValue",
            "generatedRpm",
            "generatedSpeed",
            "generatedSu",
            "currentYaw",
            "targetYaw",
            "yawVelocity",
            "worldWindAngle",
            "localTargetYaw",
            "bearingAngle",
            "nextWindSampleAt",
            "pulseStartTick",
            "pulseCooldownUntil",
            "pulseCooldownRemaining",
            "lastRuntimeDirty",
            "forcedChunk",
            "sentRpm",
            "sentPlacementNorthDir",
            "sentGeneratedSpeed",
            "sentGeneratedSu",
            "sentCurrentYaw",
            "sentTargetYaw",
            "sentYawVelocity",
            "sentWorldWindAngle",
            "sentLocalTargetYaw"
    };
    private static final AtomicBoolean CRASH_HOOKS_INSTALLED = new AtomicBoolean(false);
    private static final AtomicBoolean UNCAUGHT_DUMP_ACTIVE = new AtomicBoolean(false);

    @Nullable
    private static volatile Thread.UncaughtExceptionHandler previousDefaultUncaughtExceptionHandler;

    private WindRotoDebugDumpService() {
    }

    public static void initialize(@Nullable MinecraftServer server) {
        updateLogsDirectory(server);
        installCrashHooksIfNeeded();
    }

    public static void updateLogsDirectory(@Nullable MinecraftServer server) {
        if (server == null) {
            return;
        }

        Path logsDir = server.getFile("logs").toPath();
        WindRotoDebugTraceBuffer.setLogsDirectory(logsDir);
        WindRotoVerticalDebugTraceBuffer.setLogsDirectory(logsDir);
        ServoTwisterDebugTraceBuffer.setLogsDirectory(logsDir);
    }

    public static void installCrashHooksIfNeeded() {
        if (!CRASH_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        CrashReportCallables.registerCrashCallable("TwisterMill WindRoto Debug Dump", () -> {
            DumpResult result = dumpCombinedInternal(null, DumpTarget.CRASH_COMBINED, "forge_crash_report", null, false);
            if (result.success()) {
                return "Saved to " + result.relativePath();
            }
            return "Failed to save dump: " + result.errorMessage();
        });

        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        previousDefaultUncaughtExceptionHandler = previous;

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (UNCAUGHT_DUMP_ACTIVE.compareAndSet(false, true)) {
                try {
                    dumpCombinedInternal(null, DumpTarget.CRASH_COMBINED, "uncaught_" + sanitizeThreadName(thread.getName()), throwable, false);
                } catch (Exception ignored) {
                    // Keep crash path resilient: diagnostics must not block original crash handling.
                } finally {
                    UNCAUGHT_DUMP_ACTIVE.set(false);
                }
            }

            Thread.UncaughtExceptionHandler delegate = previousDefaultUncaughtExceptionHandler;
            if (delegate != null) {
                delegate.uncaughtException(thread, throwable);
                return;
            }

            throwable.printStackTrace();
        });
    }

    public static DumpResult dumpWindRotoForCommand(@Nullable MinecraftServer server) {
        return dumpSingleInternal(server, DumpTarget.WINDROTO, "command_windroto", true);
    }

    public static DumpResult dumpWindRotoVerticalForCommand(@Nullable MinecraftServer server) {
        return dumpSingleInternal(server, DumpTarget.WINDROTO_VERTICAL, "command_windvane", true);
    }

    public static DumpResult dumpServoForCommand(@Nullable MinecraftServer server) {
        return dumpSingleInternal(server, DumpTarget.SERVO, "command_servo", true);
    }

    public static DumpResult dumpInvServoForCommand(@Nullable MinecraftServer server) {
        return dumpSingleInternal(server, DumpTarget.INV_SERVO, "command_invservo", true);
    }

    private static DumpResult dumpSingleInternal(
            @Nullable MinecraftServer server,
            DumpTarget target,
            String trigger,
            boolean commandAnnouncement
    ) {
        synchronized (DUMP_LOCK) {
            Path logsDir = resolveLogsDirectory(server);

            try {
                Files.createDirectories(logsDir);
            } catch (IOException e) {
                return DumpResult.failure("Could not create logs directory: " + e.getMessage());
            }

            Path file = createUniqueDumpFile(logsDir, target.filePrefix());

            try (BufferedWriter writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            )) {
                writeCommonHeader(writer, trigger);

                switch (target) {
                    case WINDROTO -> {
                        WindRotoDebugTraceBuffer.TraceWindow window = WindRotoDebugTraceBuffer.captureWindow();
                        writeWindRotoSection(writer, window);
                    }
                    case WINDROTO_VERTICAL -> {
                        WindRotoVerticalDebugTraceBuffer.TraceWindow window = WindRotoVerticalDebugTraceBuffer.captureWindow();
                        writeWindRotoVerticalSection(writer, window);
                    }
                    case SERVO -> {
                        ServoTwisterDebugTraceBuffer.TraceWindow window = ServoTwisterDebugTraceBuffer.captureWindow();
                        writeServoSection(writer, window, ServoTwisterDebugTraceBuffer.TYPE_SERVO, "servo");
                    }
                    case INV_SERVO -> {
                        ServoTwisterDebugTraceBuffer.TraceWindow window = ServoTwisterDebugTraceBuffer.captureWindow();
                        writeServoSection(writer, window, ServoTwisterDebugTraceBuffer.TYPE_INV_SERVO, "invservo");
                    }
                    default -> {
                        return DumpResult.failure("Unsupported dump target: " + target.name());
                    }
                }
            } catch (IOException e) {
                return DumpResult.failure("Could not write debug dump: " + e.getMessage());
            }

            String relative = "logs/" + file.getFileName();
            if (commandAnnouncement) {
                LOGGER.info("[TwisterMill] Debug dump saved to {}", relative);
            } else {
                LOGGER.info("[TwisterMill] {} debug dump saved to {}", target.label(), relative);
            }

            return DumpResult.success(file, relative);
        }
    }

    private static DumpResult dumpCombinedInternal(
            @Nullable MinecraftServer server,
            DumpTarget target,
            String trigger,
            @Nullable Throwable throwable,
            boolean commandAnnouncement
    ) {
        synchronized (DUMP_LOCK) {
            WindRotoDebugTraceBuffer.TraceWindow windRotoWindow = WindRotoDebugTraceBuffer.captureWindow();
            WindRotoVerticalDebugTraceBuffer.TraceWindow verticalWindow = WindRotoVerticalDebugTraceBuffer.captureWindow();
            ServoTwisterDebugTraceBuffer.TraceWindow servoWindow = ServoTwisterDebugTraceBuffer.captureWindow();

            Path logsDir = resolveLogsDirectory(server);

            try {
                Files.createDirectories(logsDir);
            } catch (IOException e) {
                return DumpResult.failure("Could not create logs directory: " + e.getMessage());
            }

            Path file = createUniqueDumpFile(logsDir, target.filePrefix());

            try (BufferedWriter writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            )) {
                writeCommonHeader(writer, trigger);
                writeWindRotoSection(writer, windRotoWindow);
                writer.newLine();
                writeWindRotoVerticalSection(writer, verticalWindow);
                writer.newLine();
                writeServoSection(writer, servoWindow, ServoTwisterDebugTraceBuffer.TYPE_SERVO, "servo");
                writer.newLine();
                writeServoSection(writer, servoWindow, ServoTwisterDebugTraceBuffer.TYPE_INV_SERVO, "invservo");

                if (throwable != null) {
                    writer.newLine();
                    writer.write("--- exception ---");
                    writer.newLine();
                    writeThrowable(writer, throwable);
                }
            } catch (IOException e) {
                return DumpResult.failure("Could not write debug dump: " + e.getMessage());
            }

            String relative = "logs/" + file.getFileName();
            if (commandAnnouncement) {
                LOGGER.info("[TwisterMill] Debug dump saved to {}", relative);
            } else {
                LOGGER.info("[TwisterMill] {} debug dump saved to {}", target.label(), relative);
            }

            return DumpResult.success(file, relative);
        }
    }

    private static void writeCommonHeader(BufferedWriter writer, String trigger) throws IOException {
        writer.write("TwisterMill Debug Trace Dump");
        writer.newLine();
        writer.write("generatedAt=" + LocalDateTime.now().format(HEADER_TIMESTAMP));
        writer.newLine();
        writer.write("trigger=" + trigger);
        writer.newLine();
    }

    private static void writeWindRotoSection(BufferedWriter writer, WindRotoDebugTraceBuffer.TraceWindow window) throws IOException {
        writer.write("--- windroto ---");
        writer.newLine();
        writer.write("windowTicks=" + window.configuredWindowTicks()
                + " newestGameTime=" + window.newestGameTime()
                + " minimumIncludedGameTime=" + window.minimumIncludedGameTime());
        writer.newLine();
        writer.write("entries=" + window.snapshots().size());
        writer.newLine();

        Map<Integer, String> dimensionNames = window.dimensionNames();

        for (WindRotoDebugSnapshot snapshot : window.snapshots()) {
            writeWindRotoSnapshotLine(writer, snapshot, dimensionNames);
        }
    }

    private static void writeWindRotoVerticalSection(BufferedWriter writer, WindRotoVerticalDebugTraceBuffer.TraceWindow window) throws IOException {
        writer.write("--- windvane ---");
        writer.newLine();
        writer.write("windowTicks=" + window.configuredWindowTicks()
                + " newestGameTime=" + window.newestGameTime()
                + " minimumIncludedGameTime=" + window.minimumIncludedGameTime());
        writer.newLine();
        writer.write("entries=" + window.snapshots().size());
        writer.newLine();

        Map<Integer, String> dimensionNames = window.dimensionNames();
        List<VerticalDumpRow> rows = new ArrayList<>(window.snapshots().size());
        int[] columnWidths = new int[WINDROTO_VERTICAL_COLUMN_KEYS.length];

        for (WindRotoVerticalDebugSnapshot snapshot : window.snapshots()) {
            VerticalDumpRow row = toWindRotoVerticalDumpRow(snapshot, dimensionNames);
            rows.add(row);
            updateWindRotoVerticalColumnWidths(columnWidths, row.values());
        }

        for (VerticalDumpRow row : rows) {
            writer.write(formatWindRotoVerticalSnapshotLine(row, columnWidths));
            writer.newLine();
        }
    }
    private static void writeServoSection(
            BufferedWriter writer,
            ServoTwisterDebugTraceBuffer.TraceWindow window,
            int entityType,
            String sectionLabel
    ) throws IOException {
        writer.write("--- " + sectionLabel + " ---");
        writer.newLine();
        writer.write("windowTicks=" + window.configuredWindowTicks()
                + " newestGameTime=" + window.newestGameTime()
                + " minimumIncludedGameTime=" + window.minimumIncludedGameTime());
        writer.newLine();

        int entryCount = 0;
        for (ServoTwisterDebugSnapshot snapshot : window.snapshots()) {
            if (snapshot.entityType() == entityType) {
                entryCount++;
            }
        }

        writer.write("entries=" + entryCount);
        writer.newLine();

        Map<Integer, String> dimensionNames = window.dimensionNames();

        for (ServoTwisterDebugSnapshot snapshot : window.snapshots()) {
            if (snapshot.entityType() != entityType) {
                continue;
            }
            writeServoSnapshotLine(writer, snapshot, dimensionNames);
        }
    }

    private static void writeWindRotoSnapshotLine(
            BufferedWriter writer,
            WindRotoDebugSnapshot snapshot,
            Map<Integer, String> dimensionNames
    ) throws IOException {
        String dimension = dimensionNames.get(snapshot.dimensionId());
        if (dimension == null) {
            dimension = "unknown#" + snapshot.dimensionId();
        }

        BlockPos pos = BlockPos.of(snapshot.blockPos());

        StringBuilder line = new StringBuilder(960);
        line.append('#').append(snapshot.sequence())
                .append(" t=").append(snapshot.gameTime())
                .append(" dim=").append(dimension)
                .append(" pos=").append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ())
                .append(" running=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_RUNNING)))
                .append(" assembleQueued=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_ASSEMBLE_QUEUED)))
                .append(" assembled=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_ASSEMBLED)))
                .append(" outside=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_OUTSIDE)))
                .append(" stoppedByRS=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_STOPPED_BY_REDSTONE)))
                .append(" overStressed=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_OVERSTRESSED)))
                .append(" needsInit=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_NEEDS_INIT)))
                .append(" chunkForced=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_CHUNK_FORCED)))
                .append(" visualRunning=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_VISUAL_RUNNING)))
                .append(" hasNetwork=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_HAS_NETWORK)))
                .append(" sourcePresent=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_SOURCE_PRESENT)))
                .append(" sentOutside=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_LAST_SENT_OUTSIDE)))
                .append(" sentVisualRunning=").append(boolInt(snapshot.hasFlag(WindRotoDebugTraceBuffer.FLAG_LAST_SENT_VISUAL_RUNNING)))
                .append(" redstone=").append(snapshot.externalRedstone())
                .append(" generatedRpm=").append(snapshot.generatedRpm())
                .append(" targetRpm=").append(snapshot.targetRpm())
                .append(" generatedSpeed=").append(formatFloat(snapshot.generatedSpeed()))
                .append(" generatedSu=").append(formatFloat(snapshot.generatedSu()))
                .append(" rawWind=").append(formatFloat(snapshot.rawWind()))
                .append(" smoothedWind=").append(formatFloat(snapshot.smoothedWind()))
                .append(" bearingAngle=").append(formatFloat(snapshot.bearingAngle()))
                .append(" comparatorLevel=").append(snapshot.lastComparatorLevel())
                .append(" contraptionBlocks=").append(snapshot.contraptionBlockCount())
                .append(" boundServos=").append(snapshot.boundServoCount())
                .append(" avgServoAngle=").append(formatFloat(snapshot.averageServoAngle()))
                .append(" servoSuMultiplier=").append(formatFloat(snapshot.servoSuMultiplier()))
                .append(" servoContraptionBlocks=").append(snapshot.boundServoContraptionBlocks())
                .append(" forcedChunk=").append(snapshot.forcedChunkX()).append(',').append(snapshot.forcedChunkZ())
                .append(" nextOutsideCheckAt=").append(snapshot.nextOutsideCheckAt())
                .append(" nextWindSampleAt=").append(snapshot.nextWindSampleAt())
                .append(" nextRampAt=").append(snapshot.nextRampAt())
                .append(" nextServoSampleAt=").append(snapshot.nextServoSampleAt())
                .append(" nextStuckRebuildAt=").append(snapshot.nextStuckOverstressRebuildAt())
                .append(" sentRpm=").append(snapshot.lastSentRpm())
                .append(" sentBoundServos=").append(snapshot.lastSentBoundServoCount())
                .append(" sentServoBlocks=").append(snapshot.lastSentBoundServoContraptionBlocks())
                .append(" sentWindRaw=").append(formatFloat(snapshot.lastSentWindSpeed()))
                .append(" sentWindSmooth=").append(formatFloat(snapshot.lastSentWindSmoothed()))
                .append(" sentGeneratedSu=").append(formatFloat(snapshot.lastSentGeneratedSu()))
                .append(" sentAvgServoAngle=").append(formatFloat(snapshot.lastSentAverageServoAngle()))
                .append(" sentServoSuMultiplier=").append(formatFloat(snapshot.lastSentServoSuMultiplier()));

        writer.write(line.toString());
        writer.newLine();
    }

    private static VerticalDumpRow toWindRotoVerticalDumpRow(
            WindRotoVerticalDebugSnapshot snapshot,
            Map<Integer, String> dimensionNames
    ) {
        String dimension = dimensionNames.get(snapshot.dimensionId());
        if (dimension == null) {
            dimension = "unknown#" + snapshot.dimensionId();
        }

        BlockPos pos = BlockPos.of(snapshot.blockPos());

        String[] values = new String[] {
                Long.toString(snapshot.gameTime()),
                dimension,
                pos.getX() + "," + pos.getY() + "," + pos.getZ(),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_RUNNING))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_ASSEMBLE_QUEUED))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_ASSEMBLED))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_MANUAL_ENABLED))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_RESTORE_FREE_MODE_AFTER_MANUAL_DISASSEMBLY))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_PLACEMENT_NORTH_VALID))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_YAW_MOVING))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_PARKED_MODE))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_PULSE_POWERED))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_CHUNK_FORCED))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_VISUAL_RUNNING))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_MOVE_NEVER_PLACE_MODE_SELECTED))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_CONTROL_ENABLED))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_VERTICAL_FACING_VALID))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_OVERSTRESSED))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_LAST_SENT_VISUAL_RUNNING))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_LAST_SENT_PLACEMENT_NORTH_VALID))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_LAST_SENT_VERTICAL_YAW_MOVING))),
                Integer.toString(boolInt(snapshot.hasFlag(WindRotoVerticalDebugTraceBuffer.FLAG_LAST_SENT_VERTICAL_PARKED_MODE))),
                Integer.toString(snapshot.externalRedstone()),
                Integer.toString(snapshot.placementNorthDirData()),
                Integer.toString(snapshot.obsidianCheckTimer()),
                Integer.toString(snapshot.assemblyModeValue()),
                Integer.toString(snapshot.generatedRpm()),
                formatFloat(snapshot.generatedSpeedRpm()),
                formatFloat(snapshot.generatedSu()),
                formatFloat(snapshot.currentYawDeg()),
                formatFloat(snapshot.targetYawDeg()),
                formatFloat(snapshot.yawVelocityDegPerTick()),
                formatFloat(snapshot.worldWindAngleDeg()),
                formatFloat(snapshot.localTargetYawDeg()),
                formatFloat(snapshot.bearingAngle()),
                Long.toString(snapshot.nextWindAngleSampleAt()),
                Long.toString(snapshot.verticalPulseStartTick()),
                Long.toString(snapshot.verticalPulseCooldownUntil()),
                Long.toString(snapshot.pulseCooldownRemaining()),
                Long.toString(snapshot.lastRuntimeDirtyGameTime()),
                snapshot.forcedChunkX() + "," + snapshot.forcedChunkZ(),
                Integer.toString(snapshot.lastSentGeneratedRpm()),
                Integer.toString(snapshot.lastSentPlacementNorthDirData()),
                formatFloat(snapshot.lastSentGeneratedSpeedRpm()),
                formatFloat(snapshot.lastSentGeneratedSu()),
                formatFloat(snapshot.lastSentCurrentYawDeg()),
                formatFloat(snapshot.lastSentTargetYawDeg()),
                formatFloat(snapshot.lastSentYawVelocityDegPerTick()),
                formatFloat(snapshot.lastSentWorldWindAngleDeg()),
                formatFloat(snapshot.lastSentLocalTargetYawDeg())
        };

        return new VerticalDumpRow(snapshot.sequence(), values);
    }

    private static String formatWindRotoVerticalSnapshotLine(VerticalDumpRow row, int[] columnWidths) {
        StringBuilder line = new StringBuilder(1600);
        line.append('#').append(row.sequence());

        String[] values = row.values();
        for (int i = 0; i < values.length; i++) {
            String pair = WINDROTO_VERTICAL_COLUMN_KEYS[i] + "=" + values[i];
            line.append(' ').append(pair);
            if (i < values.length - 1) {
                int spaces = (columnWidths[i] - pair.length()) + 1;
                appendSpaces(line, spaces);
            }
        }

        return line.toString();
    }

    private static void updateWindRotoVerticalColumnWidths(int[] columnWidths, String[] values) {
        for (int i = 0; i < values.length; i++) {
            int pairLen = WINDROTO_VERTICAL_COLUMN_KEYS[i].length() + 1 + values[i].length();
            if (pairLen > columnWidths[i]) {
                columnWidths[i] = pairLen;
            }
        }
    }

    private static void appendSpaces(StringBuilder line, int count) {
        for (int i = 0; i < count; i++) {
            line.append(' ');
        }
    }

    private record VerticalDumpRow(long sequence, String[] values) {
    }
    private static void writeServoSnapshotLine(
            BufferedWriter writer,
            ServoTwisterDebugSnapshot snapshot,
            Map<Integer, String> dimensionNames
    ) throws IOException {
        String dimension = dimensionNames.get(snapshot.dimensionId());
        if (dimension == null) {
            dimension = "unknown#" + snapshot.dimensionId();
        }

        BlockPos pos = BlockPos.of(snapshot.blockPos());

        StringBuilder line = new StringBuilder(768);
        line.append('#').append(snapshot.sequence())
                .append(" t=").append(snapshot.gameTime())
                .append(" dim=").append(dimension)
                .append(" pos=").append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ())
                .append(" running=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_RUNNING)))
                .append(" assembleQueued=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_ASSEMBLE_QUEUED)))
                .append(" assembled=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_ASSEMBLED)))
                .append(" manualEnabled=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_MANUAL_ENABLED)))
                .append(" needsStateRefresh=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_NEEDS_STATE_REFRESH)))
                .append(" boundToWindRoto=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_BOUND_TO_WIND_ROTO)))
                .append(" visualRunning=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_VISUAL_RUNNING)))
                .append(" hasNetwork=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_HAS_NETWORK)))
                .append(" sourcePresent=").append(boolInt(snapshot.hasFlag(ServoTwisterDebugTraceBuffer.FLAG_SOURCE_PRESENT)))
                .append(" contraptionBlocks=").append(snapshot.contraptionBlockCount())
                .append(" lastWestSignal=").append(snapshot.lastWestSignal())
                .append(" lastEastSignal=").append(snapshot.lastEastSignal())
                .append(" lastSouthSignal=").append(snapshot.lastSouthSignal())
                .append(" pendingWestSignal=").append(snapshot.pendingWestSignal())
                .append(" pendingEastSignal=").append(snapshot.pendingEastSignal())
                .append(" pendingSouthSignal=").append(snapshot.pendingSouthSignal())
                .append(" pendingWestTicks=").append(snapshot.pendingWestTicks())
                .append(" pendingEastTicks=").append(snapshot.pendingEastTicks())
                .append(" pendingSouthTicks=").append(snapshot.pendingSouthTicks())
                .append(" displayWestSignal=").append(snapshot.displayWestSignal())
                .append(" displayEastSignal=").append(snapshot.displayEastSignal())
                .append(" displaySouthSignal=").append(snapshot.displaySouthSignal())
                .append(" pendingDisplayWestSignal=").append(snapshot.pendingDisplayWestSignal())
                .append(" pendingDisplayEastSignal=").append(snapshot.pendingDisplayEastSignal())
                .append(" pendingDisplaySouthSignal=").append(snapshot.pendingDisplaySouthSignal())
                .append(" pendingDisplayWestTicks=").append(snapshot.pendingDisplayWestTicks())
                .append(" pendingDisplayEastTicks=").append(snapshot.pendingDisplayEastTicks())
                .append(" pendingDisplaySouthTicks=").append(snapshot.pendingDisplaySouthTicks())
                .append(" configuredMaxDegrees=").append(snapshot.configuredMaxDegrees())
                .append(" effectiveMaxDegrees=").append(snapshot.effectiveMaxDegrees())
                .append(" movementModeValue=").append(snapshot.movementModeValue())
                .append(" facingOrdinal=").append(snapshot.facingOrdinal())
                .append(" westInputOrdinal=").append(snapshot.westInputOrdinal())
                .append(" eastInputOrdinal=").append(snapshot.eastInputOrdinal())
                .append(" southInputOrdinal=").append(snapshot.southInputOrdinal())
                .append(" lastRuntimeDirty=").append(snapshot.lastRuntimeDirtyGameTime())
                .append(" angle=").append(formatFloat(snapshot.angle()))
                .append(" targetAngle=").append(formatFloat(snapshot.targetAngle()))
                .append(" bindingAngle=").append(formatFloat(snapshot.bindingAngle()))
                .append(" generatedSpeed=").append(formatFloat(snapshot.generatedSpeed()));

        writer.write(line.toString());
        writer.newLine();
    }

    private static Path createUniqueDumpFile(Path logsDir, String filePrefix) {
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
        Path file = logsDir.resolve(filePrefix + timestamp + ".log");
        int suffix = 1;

        while (Files.exists(file)) {
            file = logsDir.resolve(filePrefix + timestamp + "_" + suffix + ".log");
            suffix++;
        }

        return file;
    }

    private static int boolInt(boolean value) {
        return value ? 1 : 0;
    }

    private static String formatFloat(float value) {
        if (Float.isNaN(value)) {
            return "NaN";
        }
        if (Float.isInfinite(value)) {
            return value > 0 ? "+Inf" : "-Inf";
        }
        return Float.toString(value);
    }

    private static void writeThrowable(BufferedWriter writer, Throwable throwable) throws IOException {
        Throwable cursor = throwable;

        while (cursor != null) {
            writer.write(cursor.toString());
            writer.newLine();

            for (StackTraceElement element : cursor.getStackTrace()) {
                writer.write("    at " + element);
                writer.newLine();
            }

            cursor = cursor.getCause();
            if (cursor != null) {
                writer.write("Caused by:");
                writer.newLine();
            }
        }
    }

    private static String sanitizeThreadName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "thread";
        }

        StringBuilder sanitized = new StringBuilder(name.length());

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }

        return sanitized.toString();
    }

    private static Path resolveLogsDirectory(@Nullable MinecraftServer server) {
        if (server != null) {
            return server.getFile("logs").toPath();
        }

        Path windRotoPath = WindRotoDebugTraceBuffer.getLogsDirectory();
        if (windRotoPath != null) {
            return windRotoPath;
        }

        Path verticalPath = WindRotoVerticalDebugTraceBuffer.getLogsDirectory();
        if (verticalPath != null) {
            return verticalPath;
        }

        Path servoPath = ServoTwisterDebugTraceBuffer.getLogsDirectory();
        if (servoPath != null) {
            return servoPath;
        }

        return Path.of("logs");
    }

    private enum DumpTarget {
        WINDROTO("twistermill_windroto_dump_", "WindRoto"),
        WINDROTO_VERTICAL("twistermill_windroto_vertical_dump_", "WindRotoVertical"),
        SERVO("twistermill_servo_dump_", "Servo"),
        INV_SERVO("twistermill_invservo_dump_", "InvServo"),
        CRASH_COMBINED("twistermill_windroto_crash_dump_", "WindRotoCrash");

        private final String filePrefix;
        private final String label;

        DumpTarget(String filePrefix, String label) {
            this.filePrefix = filePrefix;
            this.label = label;
        }

        public String filePrefix() {
            return filePrefix;
        }

        public String label() {
            return label;
        }
    }

    public record DumpResult(
            boolean success,
            @Nullable Path absolutePath,
            @Nullable String relativePath,
            @Nullable String errorMessage
    ) {
        public static DumpResult success(Path absolutePath, String relativePath) {
            return new DumpResult(true, absolutePath, relativePath, null);
        }

        public static DumpResult failure(String message) {
            return new DumpResult(false, null, null, message);
        }
    }
}




