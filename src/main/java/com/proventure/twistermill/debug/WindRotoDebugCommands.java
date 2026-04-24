package com.proventure.twistermill.debug;

import com.proventure.twistermill.TwisterMill;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.nio.file.Path;

@Mod.EventBusSubscriber(modid = TwisterMill.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WindRotoDebugCommands {

    private static final Component OPEN_FILE_HINT = Component.literal("Beim Anklicken wird die Datei direkt geoeffnet.");

    private WindRotoDebugCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        WindRotoDebugDumpService.installCrashHooksIfNeeded();

        event.getDispatcher().register(
                Commands.literal(TwisterMill.MOD_ID)
                        .then(Commands.literal("debug")
                                .then(Commands.literal("windroto")
                                        .then(Commands.literal("dump")
                                                .requires(source -> source.hasPermission(2))
                                                .executes(context -> executeWindRotoDump(context.getSource()))))
                                .then(Commands.literal("windvane")
                                        .then(Commands.literal("dump")
                                                .requires(source -> source.hasPermission(2))
                                                .executes(context -> executeWindRotoVerticalDump(context.getSource()))))
                                .then(Commands.literal("servo")
                                        .then(Commands.literal("dump")
                                                .requires(source -> source.hasPermission(2))
                                                .executes(context -> executeServoDump(context.getSource()))))
                                .then(Commands.literal("invservo")
                                        .then(Commands.literal("dump")
                                                .requires(source -> source.hasPermission(2))
                                                .executes(context -> executeInvServoDump(context.getSource())))))
        );
    }

    private static int executeWindRotoDump(CommandSourceStack source) {
        WindRotoDebugDumpService.DumpResult result = WindRotoDebugDumpService.dumpWindRotoForCommand(source.getServer());
        return completeCommand(source, result, "windroto");
    }

    private static int executeWindRotoVerticalDump(CommandSourceStack source) {
        WindRotoDebugDumpService.DumpResult result = WindRotoDebugDumpService.dumpWindRotoVerticalForCommand(source.getServer());
        return completeCommand(source, result, "windvane");
    }

    private static int executeServoDump(CommandSourceStack source) {
        WindRotoDebugDumpService.DumpResult result = WindRotoDebugDumpService.dumpServoForCommand(source.getServer());
        return completeCommand(source, result, "servo");
    }

    private static int executeInvServoDump(CommandSourceStack source) {
        WindRotoDebugDumpService.DumpResult result = WindRotoDebugDumpService.dumpInvServoForCommand(source.getServer());
        return completeCommand(source, result, "invservo");
    }

    private static int completeCommand(CommandSourceStack source, WindRotoDebugDumpService.DumpResult result, String typeLabel) {
        if (!result.success()) {
            String error = result.errorMessage() != null ? result.errorMessage() : "unknown error";
            source.sendFailure(Component.literal("[TwisterMill] Failed to save " + typeLabel + " debug dump: " + error));
            return 0;
        }

        source.sendSuccess(() -> buildSuccessMessage(result), false);
        return 1;
    }

    private static Component buildSuccessMessage(WindRotoDebugDumpService.DumpResult result) {
        String relativePath = result.relativePath() != null ? result.relativePath() : "logs/<unknown>";
        int slash = relativePath.lastIndexOf('/');

        String prefix;
        String filename;

        if (slash >= 0) {
            prefix = relativePath.substring(0, slash + 1);
            filename = relativePath.substring(slash + 1);
        } else {
            prefix = "";
            filename = relativePath;
        }

        MutableComponent message = Component.literal("[TwisterMill] debug saved to " + prefix);
        Path absolutePath = result.absolutePath();

        if (absolutePath == null || filename.isBlank()) {
            return message.append(filename);
        }

        return message.append(createClickableFilename(filename, absolutePath));
    }

    private static MutableComponent createClickableFilename(String filename, @Nullable Path absolutePath) {
        MutableComponent fileComponent = Component.literal(filename);

        if (absolutePath == null) {
            return fileComponent;
        }

        return fileComponent.withStyle(style -> style
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, absolutePath.toString()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, OPEN_FILE_HINT))
        );
    }
}
