package com.proventure.twistermill.weather;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModWeatherSailMessages {

    private static final String NETWORK_VERSION = "2";

    private ModWeatherSailMessages() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                WeatherSailForceSnapshotPayload.TYPE,
                WeatherSailForceSnapshotPayload.STREAM_CODEC,
                WeatherSailForceSnapshotPayload::handle
        );
        registrar.playToServer(
                WeatherSailForceSubscriptionPayload.TYPE,
                WeatherSailForceSubscriptionPayload.STREAM_CODEC,
                WeatherSailForceSubscriptionPayload::handle
        );
    }
}
