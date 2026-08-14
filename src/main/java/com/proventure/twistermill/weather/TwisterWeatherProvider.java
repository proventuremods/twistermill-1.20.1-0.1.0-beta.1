package com.proventure.twistermill.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public interface TwisterWeatherProvider {

    WindSample sample(Level level, BlockPos worldPos, Vec3 worldCenter);

    default WindSample sampleWrvbDirection(Level level, BlockPos worldPos, Vec3 worldCenter) {
        return sample(level, worldPos, worldCenter);
    }
}
