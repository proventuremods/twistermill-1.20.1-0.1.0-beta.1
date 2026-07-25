package com.proventure.twistermill.event;

import com.proventure.twistermill.block.custom.TwisterSailBlock;
import com.proventure.twistermill.util.TwisterSailSurfacePatternUtil;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
public final class TwisterSailFrameMaterialHandler {

    private TwisterSailFrameMaterialHandler() {
    }

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

        TwisterSailBlock clickedSail = (TwisterSailBlock) state.getBlock();
        boolean isFrameClicked = clickedSail.isFrame();

        if (!isFrameClicked && isConsumableMaterial && state.getValue(TwisterSailBlock.FRAME_MATERIAL) == material)
            return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (level.isClientSide)
            return;

        int changedCount = 0;
        if (!isFrameClicked) {
            if (state.getValue(TwisterSailBlock.FRAME_MATERIAL) != material) {
                level.setBlock(pos, state.setValue(TwisterSailBlock.FRAME_MATERIAL, material), 3);
                changedCount = 1;
            }
        } else {
            Direction facing = state.getValue(SailBlock.FACING);
            if (state.getValue(TwisterSailBlock.FRAME_MATERIAL) != material) {
                level.setBlock(pos, state.setValue(TwisterSailBlock.FRAME_MATERIAL, material), 3);
                changedCount = 1;
            } else {
                List<BlockPos> targets = TwisterSailSurfacePatternUtil.collectNextPerSide(
                        level,
                        pos,
                        player.getDirection(),
                        candidate -> TwisterSailSurfacePatternUtil.isSameFacingTwisterSail(candidate, facing)
                                && ((TwisterSailBlock) candidate.getBlock()).isFrame(),
                        candidate -> candidate.getValue(TwisterSailBlock.FRAME_MATERIAL) != material
                );

                for (BlockPos targetPos : targets) {
                    BlockState targetState = level.getBlockState(targetPos);
                    level.setBlock(targetPos, targetState.setValue(TwisterSailBlock.FRAME_MATERIAL, material), 3);
                    changedCount++;
                }
            }
        }

        if (isConsumableMaterial && changedCount > 0 && !player.getAbilities().instabuild) {
            stack.shrink(changedCount);
        }
    }
}

