package com.proventure.twistermill.binaryredstone.message;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.blockentity.ControlTableBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ControlTableConfigMessage(
        BlockPos blockPos,
        int sequenceLengthTicks,
        int pulseLengthTicks,
        int pauseLengthTicks,
        int repeatIntervalTicks,
        int sendControl
) implements CustomPacketPayload {

    public static final Type<ControlTableConfigMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "control_table_config"));

    public static final StreamCodec<ByteBuf, ControlTableConfigMessage> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControlTableConfigMessage::blockPos,
            ByteBufCodecs.VAR_INT, ControlTableConfigMessage::sequenceLengthTicks,
            ByteBufCodecs.VAR_INT, ControlTableConfigMessage::pulseLengthTicks,
            ByteBufCodecs.VAR_INT, ControlTableConfigMessage::pauseLengthTicks,
            ByteBufCodecs.VAR_INT, ControlTableConfigMessage::repeatIntervalTicks,
            ByteBufCodecs.VAR_INT, ControlTableConfigMessage::sendControl,
            ControlTableConfigMessage::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ControlTableConfigMessage packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player contextPlayer = context.player();
            if (!(contextPlayer instanceof ServerPlayer player)) {
                return;
            }
            if (!player.level().isLoaded(packet.blockPos())) {
                return;
            }
            if (!player.canInteractWithBlock(packet.blockPos(), 8.0)) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.blockPos()) instanceof ControlTableBlockEntity controlTableBlockEntity)) {
                return;
            }

            controlTableBlockEntity.applyConfig(
                    packet.sequenceLengthTicks(),
                    packet.pulseLengthTicks(),
                    packet.pauseLengthTicks(),
                    packet.repeatIntervalTicks(),
                    packet.sendControl() & 0x3
            );
        });
    }
}
