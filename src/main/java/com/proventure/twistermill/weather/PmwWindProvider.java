package com.proventure.twistermill.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class PmwWindProvider implements TwisterWeatherProvider {

    private static final float PWM_MPH_FOR_WEATHER2_MAX = 65.0F;
    private static final float MIN_NON_ZERO_WEATHER2_SPEED = 0.1F;
    private static final float MAX_WEATHER2_SPEED = 3.0F;
    private static final float ZERO_THRESHOLD_MPH = 0.1F;
    private static final float NON_ZERO_EPSILON = 0.0001F;
    private static final int HOLD_TICKS = 40;
    private static final int MAX_CACHE_KEYS = 4096;

    private static final Method WIND_ENGINE_GET_WIND_WITH_FLAGS = resolveGetWindMethodWithFlags();
    private static final Method WIND_ENGINE_GET_WIND_LEGACY = resolveGetWindMethodLegacy();
    private static final Object CACHE_LOCK = new Object();
    private static final Map<CacheKey, CacheEntry> SAMPLE_CACHE = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, CacheEntry> eldest) {
            return size() > MAX_CACHE_KEYS;
        }
    };

    @Override
    public WindSample sample(Level level, BlockPos worldPos, Vec3 worldCenter) {
        if (level == null) {
            return WindSample.invalid("pmweather", "level-null");
        }

        CacheKey cacheKey = resolveCacheKey(level, worldPos, worldCenter);
        Vec3 queryPos = resolveQueryPosition(worldPos, worldCenter);
        if (cacheKey == null || queryPos == null) {
            return WindSample.invalid("pmweather", "query-position-missing");
        }

        long gameTime = level.getGameTime();

        synchronized (CACHE_LOCK) {
            CacheEntry cached = SAMPLE_CACHE.get(cacheKey);
            if (cached != null && cached.lastSampleTick == gameTime && cached.lastSample != null) {
                return cached.lastSample;
            }
        }

        WindSample sampled = sampleRaw(level, queryPos);

        synchronized (CACHE_LOCK) {
            CacheEntry cached = SAMPLE_CACHE.computeIfAbsent(cacheKey, ignored -> new CacheEntry());
            if (cached.lastSampleTick == gameTime && cached.lastSample != null) {
                return cached.lastSample;
            }

            WindSample result = sampled;
            if (shouldHoldPreviousSample(cached, sampled, gameTime)) {
                result = cached.lastValidNonZeroSample.withPmwDiagnosticsFrom(sampled, true);
            }

            if (isValidNonZeroSample(sampled)) {
                cached.lastValidNonZeroSample = sampled;
                cached.lastValidNonZeroTick = gameTime;
            }

            cached.lastSample = result;
            cached.lastSampleTick = gameTime;
            return result;
        }
    }

    private static CacheKey resolveCacheKey(Level level, BlockPos worldPos, Vec3 worldCenter) {
        BlockPos cachePos = worldPos != null ? worldPos : (worldCenter != null ? BlockPos.containing(worldCenter) : null);
        if (cachePos == null) {
            return null;
        }

        ResourceKey<Level> dimension = level.dimension();
        return new CacheKey(dimension, cachePos.asLong());
    }

    private static Vec3 resolveQueryPosition(BlockPos worldPos, Vec3 worldCenter) {
        if (worldCenter != null) {
            return worldCenter;
        }
        if (worldPos == null) {
            return null;
        }
        return Vec3.atCenterOf(worldPos);
    }

    private static WindSample sampleRaw(Level level, Vec3 queryPos) {
        try {
            Object raw;
            if (WIND_ENGINE_GET_WIND_WITH_FLAGS != null) {
                raw = WIND_ENGINE_GET_WIND_WITH_FLAGS.invoke(null, queryPos, level, false, false, true);
            } else if (WIND_ENGINE_GET_WIND_LEGACY != null) {
                raw = WIND_ENGINE_GET_WIND_LEGACY.invoke(null, queryPos, level);
            } else {
                return WindSample.invalid("pmweather", "wind-engine-method-missing");
            }

            if (!(raw instanceof Vec3 windVec)) {
                return WindSample.invalid("pmweather", "raw-not-vec3");
            }

            float mph = (float) windVec.length();
            if (!Float.isFinite(mph)) {
                return WindSample.invalid("pmweather", "raw-speed-non-finite");
            }

            float weather2Speed = mapMphToWeather2Speed(mph);
            float angleDeg = computeAngleDeg(windVec);
            String zeroReason = weather2Speed <= NON_ZERO_EPSILON ? "raw-speed-below-threshold" : "";
            return new WindSample(true, weather2Speed, angleDeg, mph, "pmweather",
                    true, windVec.x, windVec.y, windVec.z, windVec.length(), "", zeroReason, false);
        } catch (Throwable ignored) {
            return WindSample.invalid("pmweather", "wind-engine-exception");
        }
    }

    private static boolean shouldHoldPreviousSample(CacheEntry cached, WindSample sampled, long gameTime) {
        if (sampled == null || isValidNonZeroSample(sampled)) {
            return false;
        }
        if (cached.lastValidNonZeroSample == null || cached.lastValidNonZeroTick == Long.MIN_VALUE) {
            return false;
        }

        long sinceValid = gameTime - cached.lastValidNonZeroTick;
        return sinceValid >= 0 && sinceValid <= HOLD_TICKS;
    }

    private static boolean isValidNonZeroSample(WindSample sample) {
        return sample != null
                && sample.valid()
                && Float.isFinite(sample.weather2WindSpeed())
                && sample.weather2WindSpeed() > NON_ZERO_EPSILON;
    }

    private static Method resolveGetWindMethodWithFlags() {
        try {
            Class<?> windEngineClass = Class.forName("dev.protomanly.pmweather.weather.WindEngine");
            return windEngineClass.getMethod("getWind", Vec3.class, Level.class, boolean.class, boolean.class, boolean.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method resolveGetWindMethodLegacy() {
        try {
            Class<?> windEngineClass = Class.forName("dev.protomanly.pmweather.weather.WindEngine");
            return windEngineClass.getMethod("getWind", Vec3.class, Level.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float mapMphToWeather2Speed(float mph) {
        if (mph <= ZERO_THRESHOLD_MPH) {
            return 0.0F;
        }

        float normalized = Mth.clamp(mph / PWM_MPH_FOR_WEATHER2_MAX, 0.0F, 1.0F);
        float mapped = MIN_NON_ZERO_WEATHER2_SPEED + normalized * (MAX_WEATHER2_SPEED - MIN_NON_ZERO_WEATHER2_SPEED);
        return Mth.clamp(mapped, MIN_NON_ZERO_WEATHER2_SPEED, MAX_WEATHER2_SPEED);
    }

    private static float computeAngleDeg(Vec3 windVec) {
        double angle = Math.toDegrees(Math.atan2(-windVec.x, windVec.z));
        if (!Double.isFinite(angle)) {
            return 0.0F;
        }
        return wrap360((float) angle);
    }

    private static float wrap360(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    private record CacheKey(ResourceKey<Level> dimension, long blockPosLong) {
    }

    private static final class CacheEntry {
        private WindSample lastSample;
        private long lastSampleTick = Long.MIN_VALUE;
        private WindSample lastValidNonZeroSample;
        private long lastValidNonZeroTick = Long.MIN_VALUE;
    }
}
