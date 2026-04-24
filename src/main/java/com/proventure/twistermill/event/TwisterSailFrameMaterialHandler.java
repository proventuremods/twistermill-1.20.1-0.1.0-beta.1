package com.proventure.twistermill.event;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.custom.TwisterSailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TwisterMill.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TwisterSailFrameMaterialHandler {

    private TwisterSailFrameMaterialHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        Player player = event.getEntity();

        if (player.isShiftKeyDown())
            return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof TwisterSailBlock))
            return;

        ItemStack stack = player.getItemInHand(event.getHand());

        if (!(stack.getItem() instanceof BlockItem blockItem))
            return;

        TwisterSailBlock.FrameMaterial material =
                TwisterSailBlock.FrameMaterial.fromBlock(blockItem.getBlock());

        if (material == null)
            return;

        if (!state.hasProperty(TwisterSailBlock.FRAME_MATERIAL))
            return;

        String materialName = material.getSerializedName();
        boolean isWoolMaterial = materialName.endsWith("_wool");
        boolean isLogMaterial = materialName.endsWith("_log");
        boolean isStemMaterial = materialName.endsWith("_stem");
        boolean isConsumableMaterial = isWoolMaterial || isLogMaterial || isStemMaterial;

        if (isConsumableMaterial && state.getValue(TwisterSailBlock.FRAME_MATERIAL) == material)
            return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (level.isClientSide)
            return;

        level.setBlock(pos, state.setValue(TwisterSailBlock.FRAME_MATERIAL, material), 3);

        if (isConsumableMaterial && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }
}
