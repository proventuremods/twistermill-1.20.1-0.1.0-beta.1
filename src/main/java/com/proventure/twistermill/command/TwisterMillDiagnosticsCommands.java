package com.proventure.twistermill.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics;
import com.proventure.twistermill.diagnostics.TwisterMillDiagnostics.Target;
import com.proventure.twistermill.diagnostics.TwisterMillReseatService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

public final class TwisterMillDiagnosticsCommands {

    private TwisterMillDiagnosticsCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("twistermill")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("diagnostics")
                        .then(Commands.literal("logging")
                                .then(target("windrotoblock", Target.WRB, "WindRotoBlock"))
                                .then(target("windvaneblock", Target.WRVB, "Wind Vane Block"))
                                .then(target("servo", Target.SERVO, "Servo"))
                                .then(target("invservo", Target.INV_SERVO, "InvServo"))))
                .then(Commands.literal("reseat")
                        .then(Commands.literal("now")
                                .executes(context -> reseatNow(context.getSource())))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> target(String literal,
                                                                                                  Target target,
                                                                                                  String label) {
        return Commands.literal(literal)
                .then(Commands.literal("start")
                        .executes(context -> setRuntime(context.getSource(), target, label, true)))
                .then(Commands.literal("stop")
                        .executes(context -> setRuntime(context.getSource(), target, label, false)))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource(), target, label)));
    }

    private static int setRuntime(CommandSourceStack source, Target target, String label, boolean enabled) {
        TwisterMillDiagnostics.setRuntimeEnabled(target, enabled);
        boolean config = TwisterMillDiagnostics.isConfigEnabled(target);
        boolean legacy = TwisterMillDiagnostics.isLegacyConfigEnabled(target);
        boolean effective = TwisterMillDiagnostics.isLoggingEnabled(target);

        String action = enabled ? "started" : "stopped";
        String suffix = !enabled && effective
                ? " Effective logging is still ON because config is enabled."
                : "";
        source.sendSuccess(() -> Component.literal("TwisterMill diagnostics " + action + " for " + label
                + ". runtime=" + onOff(enabled)
                + ", config=" + onOff(config)
                + legacyText(target, legacy)
                + ", effective=" + onOff(effective)
                + "." + suffix), false);
        return 1;
    }

    private static int status(CommandSourceStack source, Target target, String label) {
        boolean runtime = TwisterMillDiagnostics.isRuntimeEnabled(target);
        boolean config = TwisterMillDiagnostics.isConfigEnabled(target);
        boolean legacy = TwisterMillDiagnostics.isLegacyConfigEnabled(target);
        boolean effective = TwisterMillDiagnostics.isLoggingEnabled(target);
        source.sendSuccess(() -> Component.literal("TwisterMill diagnostics status for " + label
                + ": runtime=" + onOff(runtime)
                + ", config=" + onOff(config)
                + legacyText(target, legacy)
                + ", effective=" + onOff(effective)
                + "."), false);
        return 1;
    }

    private static String legacyText(Target target, boolean legacy) {
        return target == Target.SERVO ? ", legacy=" + onOff(legacy) : "";
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static int reseatNow(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(20.0D, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("TwisterMill reseat failed: look at a WRVB, WRB, Servo, or Inverted Servo."));
            return 0;
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        TwisterMillReseatService.ReseatResult result =
                TwisterMillReseatService.reseatTargeted(player.serverLevel(), pos);
        if (result == null) {
            source.sendFailure(Component.literal("TwisterMill reseat failed: targeted block is not a WRVB, WRB, Servo, or Inverted Servo."));
            return 0;
        }

        if (!result.applied()) {
            source.sendFailure(Component.literal("TwisterMill " + result.targetType().shortLabel()
                    + " reseat failed at " + TwisterMillReseatService.formatBlockPos(pos)
                    + ": " + TwisterMillReseatService.failureText(result.action()) + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "TwisterMill %s reseat applied at %s: visual %s -> %s, anchor %s -> %s, normal %s -> %s.",
                result.targetType().shortLabel(),
                TwisterMillReseatService.formatBlockPos(pos),
                TwisterMillReseatService.formatFloat(result.visualAngleBefore()),
                TwisterMillReseatService.formatFloat(result.visualAngleAfter()),
                TwisterMillReseatService.formatDouble(result.anchorWorldErrorBefore()),
                TwisterMillReseatService.formatDouble(result.anchorWorldErrorAfter()),
                TwisterMillReseatService.formatDouble(result.normalWorldErrorBefore()),
                TwisterMillReseatService.formatDouble(result.normalWorldErrorAfter()))), false);
        return 1;
    }
}
