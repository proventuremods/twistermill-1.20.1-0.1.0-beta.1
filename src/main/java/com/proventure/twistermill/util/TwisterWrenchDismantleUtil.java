package com.proventure.twistermill.util;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class TwisterWrenchDismantleUtil {

    private TwisterWrenchDismantleUtil() {
    }

    public static InteractionResult handleSneakDismantle(BlockState state, UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.SUCCESS;
        }

        boolean removed = level.destroyBlock(pos, false);
        if (!removed) {
            return InteractionResult.PASS;
        }

        ItemStack blockStack = new ItemStack(state.getBlock().asItem(), 1);
        if (!blockStack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(blockStack);
        }

        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }
}
