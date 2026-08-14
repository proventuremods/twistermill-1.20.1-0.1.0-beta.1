package com.proventure.twistermill.binaryredstone;

public final class BinarySignalFrameReceiver {

    public static final int NO_FRAME = -1;

    private static final int HALF_PHASE_TICKS = BinarySignalProtocol.FRAME_HALF_PHASE_TICKS;
    private static final int START_MIN_TICKS = BinarySignalProtocol.FRAME_START_MIN_TICKS;
    private static final int START_MAX_TICKS = BinarySignalProtocol.FRAME_START_MAX_TICKS;
    private static final int FRAME_BITS = BinarySignalProtocol.FRAME_BITS;
    private static final int BIT_CELL_TICKS = HALF_PHASE_TICKS * 2;
    private static final int BIT_SAMPLE_TICK = 1;
    private static final int SECOND_HALF_START_TICK = HALF_PHASE_TICKS;
    private static final int RECEIVE_TIMEOUT_TICKS = 90;

    private enum Phase {
        IDLE,
        START_ON,
        START_OFF,
        BIT_CELL
    }

    private Phase phase = Phase.IDLE;
    private int workingBits;
    private int bitsRead;
    private int receiveTicks;
    private int startOnTicks;
    private int startOffTicks;
    private int bitCellTick;
    private boolean startBitCellNextTick;
    private boolean lastInputHigh;

    public int tick(boolean currentInputHigh) {
        if (startBitCellNextTick) {
            startBitCellNextTick = false;
            phase = Phase.BIT_CELL;
            bitCellTick = 0;
        }

        int completedFrame = NO_FRAME;
        switch (phase) {
            case IDLE -> {
                if (!lastInputHigh && currentInputHigh) {
                    phase = Phase.START_ON;
                    startOnTicks = 1;
                    receiveTicks = 1;
                }
            }
            case START_ON -> {
                receiveTicks++;
                if (currentInputHigh) {
                    startOnTicks++;
                    if (startOnTicks > START_MAX_TICKS) {
                        reset(currentInputHigh);
                    }
                } else if (startOnTicks < START_MIN_TICKS || startOnTicks > START_MAX_TICKS) {
                    reset(currentInputHigh);
                } else {
                    phase = Phase.START_OFF;
                    startOffTicks = 1;
                }
            }
            case START_OFF -> {
                receiveTicks++;
                if (currentInputHigh && !lastInputHigh) {
                    if (startOffTicks >= START_MIN_TICKS && startOffTicks <= START_MAX_TICKS) {
                        startBitCellNextTick = true;
                    } else {
                        reset(currentInputHigh);
                    }
                } else if (!currentInputHigh) {
                    startOffTicks++;
                    if (startOffTicks > START_MAX_TICKS) {
                        reset(currentInputHigh);
                    } else if (startOffTicks == HALF_PHASE_TICKS) {
                        startBitCellNextTick = true;
                    }
                } else {
                    reset(currentInputHigh);
                }
            }
            case BIT_CELL -> {
                receiveTicks++;

                if (bitCellTick == BIT_SAMPLE_TICK) {
                    workingBits = (workingBits << 1) | (currentInputHigh ? 1 : 0);
                }

                if (bitCellTick >= SECOND_HALF_START_TICK && currentInputHigh) {
                    reset(currentInputHigh);
                    break;
                }

                if (bitCellTick >= BIT_CELL_TICKS - 1) {
                    bitsRead++;
                    if (bitsRead == FRAME_BITS) {
                        completedFrame = workingBits;
                        reset(currentInputHigh);
                    } else {
                        bitCellTick = 0;
                    }
                } else {
                    bitCellTick++;
                }
            }
        }

        if (phase != Phase.IDLE && receiveTicks > RECEIVE_TIMEOUT_TICKS) {
            reset(currentInputHigh);
        }

        lastInputHigh = currentInputHigh;
        return completedFrame;
    }

    public void synchronizeInput(boolean currentInputHigh) {
        lastInputHigh = currentInputHigh;
    }

    public void reset(boolean currentInputHigh) {
        phase = Phase.IDLE;
        workingBits = 0;
        bitsRead = 0;
        receiveTicks = 0;
        startOnTicks = 0;
        startOffTicks = 0;
        bitCellTick = 0;
        startBitCellNextTick = false;
        lastInputHigh = currentInputHigh;
    }
}
