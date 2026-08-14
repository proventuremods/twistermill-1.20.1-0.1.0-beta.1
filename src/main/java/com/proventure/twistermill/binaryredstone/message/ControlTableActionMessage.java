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
import org.jetbrains.annotations.NotNull;

public record ControlTableActionMessage(
        BlockPos blockPos,
        int action
) implements CustomPacketPayload {

    public static final int ACTION_TOGGLE_SERVO_ASSEMBLY = 0;

    public static final Type<ControlTableActionMessage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "control_table_action"));

    public static final StreamCodec<ByteBuf, ControlTableActionMessage> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ControlTableActionMessage::blockPos,
            ByteBufCodecs.VAR_INT, ControlTableActionMessage::action,
            ControlTableActionMessage::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("resource")
    public static void handle(ControlTableActionMessage packet, IPayloadContext context) {
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
            if (packet.action() != ACTION_TOGGLE_SERVO_ASSEMBLY) {
                return;
            }

            controlTableBlockEntity.requestDisassembleAssembleToggle();
        });
    }
}
