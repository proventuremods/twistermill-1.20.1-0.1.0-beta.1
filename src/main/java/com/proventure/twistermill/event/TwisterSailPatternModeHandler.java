package com.proventure.twistermill.event;

import com.proventure.twistermill.util.TwisterSailPatternPlacementUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class TwisterSailPatternModeHandler {

    private TwisterSailPatternModeHandler() {
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }

        if (!TwisterSailPatternPlacementUtil.isPatternSetup(player)) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        Level level = event.getLevel();
        if (level.isClientSide) {
            return;
        }

        TwisterSailPatternPlacementUtil.cyclePatternWidthAndNotify(player);
    }
}
