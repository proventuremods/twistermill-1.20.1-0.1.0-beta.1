package com.proventure.twistermill.event;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.ModBlocks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SignalQuartzOreAdvancementHandler {

    private static final ResourceLocation SIGNAL_QUARTZ_ORE_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "signal_quartz_ore");
    private static final String HIT_SIGNAL_QUARTZ_ORE_CRITERION = "hit_signal_quartz_ore";

    private SignalQuartzOreAdvancementHandler() {
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START)
            return;
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
            return;
        if (!event.getItemStack().is(ItemTags.PICKAXES))
            return;
        if (!event.getLevel().getBlockState(event.getPos()).is(ModBlocks.SIGNAL_QUARTZ_ORE_BLOCK.get()))
            return;

        AdvancementHolder advancement = serverPlayer.server.getAdvancements().get(SIGNAL_QUARTZ_ORE_ADVANCEMENT);
        if (advancement == null)
            return;
        if (serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone())
            return;

        serverPlayer.getAdvancements().award(advancement, HIT_SIGNAL_QUARTZ_ORE_CRITERION);
    }
}
