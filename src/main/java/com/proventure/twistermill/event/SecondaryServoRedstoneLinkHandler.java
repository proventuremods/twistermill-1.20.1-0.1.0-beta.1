package com.proventure.twistermill.event;

import com.proventure.twistermill.blockentity.InternalServoRedstoneLinkOwner;
import com.proventure.twistermill.blockentity.SecondaryServoRedstoneLinkBehaviour;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SecondaryServoRedstoneLinkHandler {

    private SecondaryServoRedstoneLinkHandler() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SmartBlockEntity smart)
                || !(smart instanceof InternalServoRedstoneLinkOwner owner)
                || !owner.isSecondaryInternalRedstoneLinkEligible()) {
            return;
        }

        SecondaryServoRedstoneLinkBehaviour behaviour =
                smart.getBehaviour(SecondaryServoRedstoneLinkBehaviour.TYPE);
        if (behaviour == null) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        if (player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }

        ItemStack heldItem = player.getItemInHand(hand);
        if (AllItems.LINKED_CONTROLLER.isIn(heldItem) || AllItems.WRENCH.isIn(heldItem)) {
            return;
        }

        BlockHitResult hitResult = event.getHitVec();
        Vec3 hit = SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, pos, hitResult);

        boolean fakePlayer = player instanceof FakePlayer;
        boolean fakePlayerChoice = false;
        if (fakePlayer) {
            BlockState state = level.getBlockState(pos);
            Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(pos))
                    .add(Vec3.atLowerCornerOf(hitResult.getDirection().getNormal()).scale(.25F));
            Vec3 firstOffset = behaviour.getSlotTransform(true).getLocalOffset(level, pos, state);
            Vec3 secondOffset = behaviour.getSlotTransform(false).getLocalOffset(level, pos, state);
            if (firstOffset == null || secondOffset == null) {
                return;
            }
            fakePlayerChoice = localHit.distanceToSqr(firstOffset) > localHit.distanceToSqr(secondOffset);
        }

        for (boolean first : Iterate.trueAndFalse) {
            if (!behaviour.testHit(first, hit) && (!fakePlayer || fakePlayerChoice != first)) {
                continue;
            }
            if (event.getSide() != LogicalSide.CLIENT) {
                behaviour.setFrequency(first, heldItem);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25F, .1F);
            return;
        }
    }
}
