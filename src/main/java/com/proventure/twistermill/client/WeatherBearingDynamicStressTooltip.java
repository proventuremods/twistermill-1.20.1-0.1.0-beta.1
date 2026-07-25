package com.proventure.twistermill.client;

import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class WeatherBearingDynamicStressTooltip implements TooltipModifier {

    private static final int TICKS_PER_PHASE = 18;
    private static final ChatFormatting[] PHASE_COLORS = {
            ChatFormatting.GREEN,
            ChatFormatting.YELLOW,
            ChatFormatting.GOLD,
            ChatFormatting.RED
    };
    private static final int[] PHASE_FILLED_LENGTHS = {3, 2, 1, 0};

    @Override
    public void modify(ItemTooltipEvent context) {
        int phase = currentPhase();
        ChatFormatting color = PHASE_COLORS[phase];

        context.getToolTip().add(Component.empty());
        context.getToolTip().add(Component.translatable("create.tooltip.capacityProvided")
                .withStyle(ChatFormatting.GRAY));
        context.getToolTip().add(Component.literal(TooltipHelper.makeProgressBar(3, PHASE_FILLED_LENGTHS[phase]))
                .withStyle(color)
                .append(Component.literal("Dynamic SU").withStyle(color)));
    }

    private static int currentPhase() {
        return Math.floorMod(AnimationTickHolder.getTicks() / TICKS_PER_PHASE, PHASE_COLORS.length);
    }
}
