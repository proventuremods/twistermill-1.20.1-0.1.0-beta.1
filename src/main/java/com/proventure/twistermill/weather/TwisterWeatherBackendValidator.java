package com.proventure.twistermill.weather;

import net.neoforged.fml.ModList;

public final class TwisterWeatherBackendValidator {

    private static final String WEATHER2_MOD_ID = "weather2";
    private static final String PMWEATHER_MOD_ID = "pmweather";

    private TwisterWeatherBackendValidator() {
    }

    public static boolean isWeather2Loaded() {
        return ModList.get().isLoaded(WEATHER2_MOD_ID);
    }

    public static boolean isPmweatherLoaded() {
        return ModList.get().isLoaded(PMWEATHER_MOD_ID);
    }

    public static void validateExactlyOneBackendOrThrow() {
        boolean weather2Loaded = isWeather2Loaded();
        boolean pmweatherLoaded = isPmweatherLoaded();

        if (weather2Loaded == pmweatherLoaded) {
            throw new IllegalStateException(buildExactlyOneBackendError(weather2Loaded, pmweatherLoaded));
        }
    }

    public static String buildExactlyOneBackendError(boolean weather2Loaded, boolean pmweatherLoaded) {
        if (weather2Loaded && pmweatherLoaded) {
            return "TwisterMill requires exactly one weather backend, but both weather2 and pmweather are loaded.";
        }
        return "TwisterMill requires exactly one weather backend, but neither weather2 nor pmweather is loaded.";
    }
}
