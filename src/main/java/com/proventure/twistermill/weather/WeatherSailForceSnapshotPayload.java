package com.proventure.twistermill.weather;

import com.proventure.twistermill.TwisterMill;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public record WeatherSailForceSnapshotPayload(
        long subscriptionEpoch,
        ResourceLocation dimension,
        long generation,
        int partIndex,
        int partCount,
        float maximumForcePerBlock,
        List<Entry> entries
) implements CustomPacketPayload {

    public static final int MAX_ENTRIES_PER_PART = 64;
    public static final int MAX_PARTS = 2;

    public static final Type<WeatherSailForceSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "weather_sail_force_snapshot"));

    public static final StreamCodec<ByteBuf, WeatherSailForceSnapshotPayload> STREAM_CODEC =
            StreamCodec.of(WeatherSailForceSnapshotPayload::encode, WeatherSailForceSnapshotPayload::decode);

    private static volatile Consumer<WeatherSailForceSnapshotPayload> clientHandler = payload -> {
    };

    public WeatherSailForceSnapshotPayload {
        Objects.requireNonNull(dimension, "dimension");
        entries = List.copyOf(entries);
        if (partCount < 1 || partCount > MAX_PARTS) {
            throw new IllegalArgumentException("Invalid Weather Sail snapshot part count: " + partCount);
        }
        if (partIndex < 0 || partIndex >= partCount) {
            throw new IllegalArgumentException("Invalid Weather Sail snapshot part index: " + partIndex);
        }
        if (entries.size() > MAX_ENTRIES_PER_PART) {
            throw new IllegalArgumentException("Too many Weather Sail snapshot entries: " + entries.size());
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void installClientHandler(Consumer<WeatherSailForceSnapshotPayload> handler) {
        clientHandler = Objects.requireNonNull(handler, "handler");
    }

    public static void handle(WeatherSailForceSnapshotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientHandler.accept(payload));
    }

    private static void encode(ByteBuf buffer, WeatherSailForceSnapshotPayload payload) {
        buffer.writeLong(payload.subscriptionEpoch);
        ResourceLocation.STREAM_CODEC.encode(buffer, payload.dimension);
        buffer.writeLong(payload.generation);
        buffer.writeByte(payload.partIndex);
        buffer.writeByte(payload.partCount);
        buffer.writeFloat(payload.maximumForcePerBlock);
        buffer.writeByte(payload.entries.size());
        for (Entry entry : payload.entries) {
            buffer.writeLong(entry.subLevelId.getMostSignificantBits());
            buffer.writeLong(entry.subLevelId.getLeastSignificantBits());
            buffer.writeLong(entry.sailId);
            buffer.writeDouble(entry.localCenterX);
            buffer.writeDouble(entry.localCenterY);
            buffer.writeDouble(entry.localCenterZ);
            buffer.writeFloat(entry.localThicknessAxisX);
            buffer.writeFloat(entry.localThicknessAxisY);
            buffer.writeFloat(entry.localThicknessAxisZ);
            buffer.writeFloat(entry.incomingWindX);
            buffer.writeFloat(entry.incomingWindY);
            buffer.writeFloat(entry.incomingWindZ);
            buffer.writeFloat(entry.appliedForceX);
            buffer.writeFloat(entry.appliedForceY);
            buffer.writeFloat(entry.appliedForceZ);
        }
    }

    private static WeatherSailForceSnapshotPayload decode(ByteBuf buffer) {
        long subscriptionEpoch = buffer.readLong();
        ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buffer);
        long generation = buffer.readLong();
        int partIndex = buffer.readUnsignedByte();
        int partCount = buffer.readUnsignedByte();
        float maximumForcePerBlock = buffer.readFloat();
        int entryCount = buffer.readUnsignedByte();

        if (partCount < 1 || partCount > MAX_PARTS
                || partIndex >= partCount
                || entryCount > MAX_ENTRIES_PER_PART) {
            throw new IllegalArgumentException("Invalid Weather Sail force snapshot payload");
        }

        List<Entry> entries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            UUID subLevelId = new UUID(buffer.readLong(), buffer.readLong());
            entries.add(new Entry(
                    subLevelId,
                    buffer.readLong(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat(),
                    buffer.readFloat()
            ));
        }

        return new WeatherSailForceSnapshotPayload(
                subscriptionEpoch,
                dimension,
                generation,
                partIndex,
                partCount,
                maximumForcePerBlock,
                entries
        );
    }

    public record Entry(
            UUID subLevelId,
            long sailId,
            double localCenterX,
            double localCenterY,
            double localCenterZ,
            float localThicknessAxisX,
            float localThicknessAxisY,
            float localThicknessAxisZ,
            float incomingWindX,
            float incomingWindY,
            float incomingWindZ,
            float appliedForceX,
            float appliedForceY,
            float appliedForceZ
    ) {
        public Entry {
            Objects.requireNonNull(subLevelId, "subLevelId");
        }
    }
}
