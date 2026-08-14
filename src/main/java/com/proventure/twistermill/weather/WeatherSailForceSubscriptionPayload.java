package com.proventure.twistermill.weather;

import com.proventure.twistermill.TwisterMill;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record WeatherSailForceSubscriptionPayload(
        boolean enabled,
        long subscriptionEpoch
) implements CustomPacketPayload {

    public static final Type<WeatherSailForceSubscriptionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "weather_sail_force_subscription")
    );

    public static final StreamCodec<ByteBuf, WeatherSailForceSubscriptionPayload> STREAM_CODEC =
            StreamCodec.of(WeatherSailForceSubscriptionPayload::encode, WeatherSailForceSubscriptionPayload::decode);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(WeatherSailForceSubscriptionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WeatherSailForceSnapshotServer.updateSubscription(
                        player,
                        payload.enabled,
                        payload.subscriptionEpoch
                );
            }
        });
    }

    private static void encode(ByteBuf buffer, WeatherSailForceSubscriptionPayload payload) {
        buffer.writeBoolean(payload.enabled);
        buffer.writeLong(payload.subscriptionEpoch);
    }

    private static WeatherSailForceSubscriptionPayload decode(ByteBuf buffer) {
        return new WeatherSailForceSubscriptionPayload(buffer.readBoolean(), buffer.readLong());
    }
}
