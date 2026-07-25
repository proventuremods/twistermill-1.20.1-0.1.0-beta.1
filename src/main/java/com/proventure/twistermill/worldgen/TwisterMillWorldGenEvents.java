package com.proventure.twistermill.worldgen;

import com.proventure.twistermill.config.TwisterMillConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class TwisterMillWorldGenEvents {

    private TwisterMillWorldGenEvents() {
    }

    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }

        if (!TwisterMillConfig.isGenerateOresInWorldEnabled()) {
            return;
        }

        TwisterMillWorldGenSavedData.get(serverLevel.getServer()).enableOreGeneration();
    }
}
