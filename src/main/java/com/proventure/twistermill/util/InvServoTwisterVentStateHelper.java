package com.proventure.twistermill.util;

import com.proventure.twistermill.block.custom.InvServoTwisterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class InvServoTwisterVentStateHelper {

    private InvServoTwisterVentStateHelper() {
    }

    public static InvServoTwisterBlock.VentState getVentState(int westSignal, int eastSignal, int southSignal) {
        boolean speedActive = westSignal > 0;
        boolean angleActive = eastSignal > 0;
        boolean modeActive = southSignal > 0;

        if (speedActive && modeActive && angleActive)
            return InvServoTwisterBlock.VentState.RIGHT_UP_LEFT;

        if (speedActive && modeActive && !angleActive)
            return InvServoTwisterBlock.VentState.LEFT_UP;

        if (modeActive && angleActive && !speedActive)
            return InvServoTwisterBlock.VentState.RIGHT_UP;

        if (speedActive && angleActive && !modeActive)
            return InvServoTwisterBlock.VentState.RIGHT_LEFT;

        if (speedActive && !modeActive && !angleActive)
            return InvServoTwisterBlock.VentState.LEFT;

        if (modeActive && !speedActive && !angleActive)
            return InvServoTwisterBlock.VentState.UP;

        if (angleActive && !speedActive && !modeActive)
            return InvServoTwisterBlock.VentState.RIGHT;

        return InvServoTwisterBlock.VentState.NONE;
    }

    public static void syncVentState(Level level, BlockPos pos, BlockState state, int westSignal, int eastSignal, int southSignal) {
        if (level == null || level.isClientSide)
            return;

        if (!(state.getBlock() instanceof InvServoTwisterBlock))
            return;

        if (!state.hasProperty(InvServoTwisterBlock.VENT_STATE))
            return;

        InvServoTwisterBlock.VentState newVentState = getVentState(westSignal, eastSignal, southSignal);
        InvServoTwisterBlock.VentState currentVentState = state.getValue(InvServoTwisterBlock.VENT_STATE);

        if (currentVentState == newVentState)
            return;

        level.setBlock(pos, state.setValue(InvServoTwisterBlock.VENT_STATE, newVentState), 3);
    }
}