package com.proventure.twistermill.binaryredstone;

import com.proventure.twistermill.binaryredstone.message.ControlTableActionMessage;
import com.proventure.twistermill.binaryredstone.message.ControlTableConfigMessage;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModBinaryRedstoneMessages {

    private static final String NETWORK_VERSION = "1";

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToServer(ControlTableConfigMessage.TYPE, ControlTableConfigMessage.STREAM_CODEC, ControlTableConfigMessage::handle);
        registrar.playToServer(ControlTableActionMessage.TYPE, ControlTableActionMessage.STREAM_CODEC, ControlTableActionMessage::handle);
    }
}
