package com.proventure.twistermill.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import weather2.util.WindReader;
import weather2.weathersystem.WeatherManager;

public class Weather2WindProvider implements TwisterWeatherProvider {

    private static final float MAX_WEATHER2_SPEED = 3.0F;

    @Override
    public WindSample sample(Level level, BlockPos worldPos, Vec3 worldCenter) {
        if (level == null || worldPos == null || worldCenter == null) {
            return WindSample.invalid("weather2");
        }

        WeatherManager weatherManager = WindReader.getWeatherManagerFor(level);
        if (weatherManager == null || weatherManager.getWindManager() == null) {
            return WindSample.invalid("weather2");
        }

        float rawSpeed = weatherManager.getWindManager().getWindSpeedPositional(worldPos, 2.0F, false);
        float weather2Speed = Mth.clamp(rawSpeed, 0.0F, MAX_WEATHER2_SPEED);

        float windAngle = WindReader.getWindAngle(level, worldCenter);
        if (!Float.isFinite(windAngle)) {
            windAngle = 0.0F;
        }

        return new WindSample(true, weather2Speed, wrap360(windAngle), rawSpeed, "weather2");
    }

    private static float wrap360(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }
}
