package com.proventure.twistermill.util;

import com.proventure.twistermill.block.custom.ServoTwisterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class ServoTwisterVentStateHelper {

    private ServoTwisterVentStateHelper() {
    }

    public static ServoTwisterBlock.VentState getVentState(int westSignal, int eastSignal, int southSignal) {
        boolean speedActive = westSignal > 0;
        boolean angleActive = eastSignal > 0;
        boolean modeActive = southSignal > 0;

        if (speedActive && modeActive && angleActive)
            return ServoTwisterBlock.VentState.RIGHT_UP_LEFT;

        if (speedActive && modeActive && !angleActive)
            return ServoTwisterBlock.VentState.LEFT_UP;

        if (modeActive && angleActive && !speedActive)
            return ServoTwisterBlock.VentState.RIGHT_UP;

        if (speedActive && angleActive && !modeActive)
            return ServoTwisterBlock.VentState.RIGHT_LEFT;

        if (speedActive && !modeActive && !angleActive)
            return ServoTwisterBlock.VentState.LEFT;

        if (modeActive && !speedActive && !angleActive)
            return ServoTwisterBlock.VentState.UP;

        if (angleActive && !speedActive && !modeActive)
            return ServoTwisterBlock.VentState.RIGHT;

        return ServoTwisterBlock.VentState.NONE;
    }

    public static void syncVentState(Level level, BlockPos pos, BlockState state, int westSignal, int eastSignal, int southSignal) {
        if (level == null || level.isClientSide)
            return;

        if (!(state.getBlock() instanceof ServoTwisterBlock))
            return;

        if (!state.hasProperty(ServoTwisterBlock.VENT_STATE))
            return;

        ServoTwisterBlock.VentState newVentState = getVentState(westSignal, eastSignal, southSignal);
        ServoTwisterBlock.VentState currentVentState = state.getValue(ServoTwisterBlock.VENT_STATE);

        if (currentVentState == newVentState)
            return;

        level.setBlock(pos, state.setValue(ServoTwisterBlock.VENT_STATE, newVentState), 3);
    }
}