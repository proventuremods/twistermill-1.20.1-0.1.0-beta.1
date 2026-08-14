package com.proventure.twistermill.client.command;

import com.mojang.logging.LogUtils;
import com.proventure.twistermill.config.TwisterMillConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.slf4j.Logger;

public final class TwisterMillClientCommands {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String UPDATE_FAILED_MESSAGE = "Weather Sail force vectors could not be updated.";

    private TwisterMillClientCommands() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("twistermill")
                .then(Commands.literal("toggle_sails_render")
                        .executes(context -> toggleSailsRender(context.getSource()))));
    }

    private static int toggleSailsRender(CommandSourceStack source) {
        if (!TwisterMillConfig.COMMON_SPEC.isLoaded()) {
            LOGGER.error("Cannot toggle Weather Sail force vectors because the common config is not loaded.");
            source.sendFailure(Component.literal(UPDATE_FAILED_MESSAGE));
            return 0;
        }

        final boolean previousValue;
        try {
            previousValue = TwisterMillConfig.SHOW_SAIL_FORCE_VECTORS.getRaw();
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to read the Weather Sail force-vector rendering setting.", exception);
            source.sendFailure(Component.literal(UPDATE_FAILED_MESSAGE));
            return 0;
        }

        boolean newValue = !previousValue;
        try {
            TwisterMillConfig.SHOW_SAIL_FORCE_VECTORS.set(newValue);
            TwisterMillConfig.SHOW_SAIL_FORCE_VECTORS.save();
        } catch (RuntimeException exception) {
            try {
                if (TwisterMillConfig.COMMON_SPEC.isLoaded()) {
                    TwisterMillConfig.SHOW_SAIL_FORCE_VECTORS.set(previousValue);
                }
            } catch (RuntimeException rollbackException) {
                exception.addSuppressed(rollbackException);
            }
            LOGGER.error("Failed to persist the Weather Sail force-vector rendering setting.", exception);
            source.sendFailure(Component.literal(UPDATE_FAILED_MESSAGE));
            return 0;
        }

        source.sendSuccess(
                () -> Component.literal("Weather Sail force vectors: " + (newValue ? "ON" : "OFF")),
                false
        );
        return 1;
    }
}
