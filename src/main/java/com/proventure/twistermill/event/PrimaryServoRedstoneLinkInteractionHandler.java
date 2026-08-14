package com.proventure.twistermill.event;

import com.proventure.twistermill.block.custom.ServoTwisterBlock;
import com.proventure.twistermill.blockentity.InternalServoRedstoneLinkOwner;
import com.proventure.twistermill.blockentity.InternalServoRedstoneLinkSlots;
import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
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

public final class PrimaryServoRedstoneLinkInteractionHandler {

    private PrimaryServoRedstoneLinkInteractionHandler() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SmartBlockEntity smart)
                || !(smart instanceof InternalServoRedstoneLinkOwner owner)
                || !owner.isInternalRedstoneLinkMode()) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(ServoTwisterBlock.PITCH_CLEARANCE)
                || !state.getValue(ServoTwisterBlock.PITCH_CLEARANCE)) {
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

        LinkBehaviour behaviour = smart.getBehaviour(LinkBehaviour.TYPE);
        if (behaviour == null) {
            return;
        }

        BlockHitResult hitResult = event.getHitVec();
        Vec3 hit = SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, pos, hitResult);

        boolean fakePlayer = player instanceof FakePlayer;
        boolean fakePlayerChoice = false;
        if (fakePlayer) {
            Vec3 localHit = hit.subtract(Vec3.atLowerCornerOf(pos))
                    .add(Vec3.atLowerCornerOf(hitResult.getDirection().getNormal()).scale(.25F));
            boolean inverted = smart instanceof InvServoTwisterBlockEntity;
            ValueBoxTransform firstTransform = InternalServoRedstoneLinkSlots.createSlot(true, inverted);
            ValueBoxTransform secondTransform = InternalServoRedstoneLinkSlots.createSlot(false, inverted);
            Vec3 firstOffset = firstTransform.getLocalOffset(level, pos, state);
            Vec3 secondOffset = secondTransform.getLocalOffset(level, pos, state);
            if (firstOffset == null || secondOffset == null) {
                return;
            }
            fakePlayerChoice = localHit.distanceToSqr(firstOffset) <= localHit.distanceToSqr(secondOffset);
        }

        for (boolean first : Iterate.trueAndFalse) {
            if (!behaviour.testHit(first, hit) && (!fakePlayer || fakePlayerChoice != first)) {
                continue;
            }
            if (event.getSide() != LogicalSide.CLIENT) {
                ItemStack previousFrequency = behaviour.getNetworkKey().get(first).getStack();
                behaviour.setFrequency(first, heldItem);
                ItemStack updatedFrequency = behaviour.getNetworkKey().get(first).getStack();
                if (!ItemStack.isSameItemSameComponents(previousFrequency, updatedFrequency)) {
                    smart.setChanged();
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, .25F, .1F);
            return;
        }
    }
}
