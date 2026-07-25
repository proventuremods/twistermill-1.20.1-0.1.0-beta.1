package com.proventure.twistermill.weather;

import com.proventure.twistermill.util.SableLevelWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class TwisterWeatherService {

    private static volatile TwisterWeatherProvider provider;

    private TwisterWeatherService() {
    }

    public static WindSample sampleAtBlock(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return WindSample.invalid("none");
        }

        Vector3d worldCenter = SableLevelWrapper.toWorldCenter(level, pos);
        Vec3 worldCenterVec = new Vec3(worldCenter.x, worldCenter.y, worldCenter.z);
        BlockPos worldPos = BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z);
        return sample(level, worldPos, worldCenterVec);
    }

    public static WindSample sampleAtWorldPosition(Level level, Vec3 worldCenter) {
        if (level == null || worldCenter == null) {
            return WindSample.invalid("none");
        }

        BlockPos worldPos = BlockPos.containing(worldCenter.x, worldCenter.y, worldCenter.z);
        return sample(level, worldPos, worldCenter);
    }

    private static WindSample sample(Level level, BlockPos worldPos, Vec3 worldCenter) {
        TwisterWeatherProvider activeProvider = getProvider();
        return activeProvider.sample(level, worldPos, worldCenter);
    }

    private static TwisterWeatherProvider getProvider() {
        TwisterWeatherProvider local = provider;
        if (local != null) {
            return local;
        }

        synchronized (TwisterWeatherService.class) {
            local = provider;
            if (local != null) {
                return local;
            }

            boolean weather2Loaded = TwisterWeatherBackendValidator.isWeather2Loaded();
            boolean pmweatherLoaded = TwisterWeatherBackendValidator.isPmweatherLoaded();

            if (weather2Loaded == pmweatherLoaded) {
                throw new IllegalStateException(
                        TwisterWeatherBackendValidator.buildExactlyOneBackendError(weather2Loaded, pmweatherLoaded)
                );
            }

            local = weather2Loaded ? new Weather2WindProvider() : new PmwWindProvider();
            provider = local;
            return local;
        }
    }
}
