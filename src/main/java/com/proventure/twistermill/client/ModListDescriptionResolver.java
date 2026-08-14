package com.proventure.twistermill.client;

import com.proventure.twistermill.weather.TwisterWeatherBackendValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class ModListDescriptionResolver {

    public static final String DESCRIPTION_LINE_MARKER = "\u0000twistermill:mod-list-description-line\u0000";
    public static final String STATUS_LINE_MARKER = "\u0000twistermill:mod-list-status-line\u0000";

    private static final String PREFIX = "Compatibility bridge between Sable and Create’s kinetic system for ";
    private static final String WEATHER2 = "Weather2's";
    private static final String PMWEATHER = "ProtoManly’s";
    private static final String SUFFIX = " Wind Simulation";
    private static final String WEATHER2_STATUS = "Weather, Storms & Tornadoes - detected & in use";
    private static final String PMWEATHER_STATUS = "ProtoManly's Weather - detected & in use";

    private ModListDescriptionResolver() {
    }

    public static boolean hasExactlyOneActiveBackend() {
        return TwisterWeatherBackendValidator.isWeather2Loaded()
                != TwisterWeatherBackendValidator.isPmweatherLoaded();
    }

    public static Component resolveDescriptionLine() {
        if (TwisterWeatherBackendValidator.isWeather2Loaded()) {
            return Component.literal(PREFIX + WEATHER2 + " or " + PMWEATHER + SUFFIX)
                    .withStyle(ChatFormatting.WHITE);
        }

        return Component.literal(PREFIX + PMWEATHER + " or " + WEATHER2 + SUFFIX)
                .withStyle(ChatFormatting.WHITE);
    }

    public static Component resolveStatusLine() {
        String status = TwisterWeatherBackendValidator.isWeather2Loaded()
                ? WEATHER2_STATUS
                : PMWEATHER_STATUS;
        return Component.literal(status).withStyle(ChatFormatting.GREEN);
    }
}
