package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.InternalServoRedstoneLinkOwner;
import com.proventure.twistermill.blockentity.InternalServoRedstoneLinkSlots;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class InternalServoRedstoneLinkRenderer {
    private static final int FIRST_FREQUENCY_COLOR = 0xFF334D;
    private static final int SECOND_FREQUENCY_COLOR = 0x337DFF;

    private InternalServoRedstoneLinkRenderer() {
    }

    public static void renderOnBlockEntity(SmartBlockEntity be, boolean inverted, PoseStack ms,
                                           MultiBufferSource buffer, int light, int overlay) {
        if (be == null || be.isRemoved())
            return;

        if (!(be instanceof InternalServoRedstoneLinkOwner owner) || !owner.shouldRenderInternalRedstoneLinkSlots())
            return;

        LinkBehaviour behaviour = be.getBehaviour(LinkBehaviour.TYPE);
        if (behaviour == null)
            return;

        boolean renderSlotMarkers = isTargetingInternalLinkSide(be, owner);
        for (boolean first : Iterate.trueAndFalse) {
            ValueBoxTransform transform = InternalServoRedstoneLinkSlots.createSlot(first, inverted);
            ItemStack stack = behaviour.getNetworkKey()
                    .get(first)
                    .getStack();

            ms.pushPose();
            transform.transform(be.getLevel(), be.getBlockPos(), be.getBlockState(), ms);
            if (renderSlotMarkers) {
                renderSlotMarker(stack, first, ms, buffer);
            }
            ValueBoxRenderer.renderItemIntoValueBox(stack, ms, buffer, light, overlay);
            ms.popPose();
        }
    }

    private static boolean isTargetingInternalLinkSide(SmartBlockEntity be, InternalServoRedstoneLinkOwner owner) {
        Level level = be.getLevel();
        if (level == null)
            return false;

        if (!(Minecraft.getInstance().hitResult instanceof BlockHitResult hit))
            return false;

        BlockPos pos = be.getBlockPos();
        if (!isTargetingBlock(level, pos, hit))
            return false;

        return resolveLocalHitFace(level, pos, hit) == owner.getInternalRedstoneLinkSide();
    }

    private static boolean isTargetingBlock(Level level, BlockPos pos, BlockHitResult hit) {
        if (hit.getBlockPos().equals(pos))
            return true;

        Vec3 localHit = getLocalHitOrOriginal(level, pos, hit);
        return SablePlacementHitHelper.isHitLocationConsistentWithPos(pos, localHit);
    }

    private static Direction resolveLocalHitFace(Level level, BlockPos pos, BlockHitResult hit) {
        Direction face = hit.getDirection();
        Vec3 originalHit = hit.getLocation();
        Vec3 localHit = getLocalHitOrOriginal(level, pos, hit);
        boolean transformedToLocal = !SablePlacementHitHelper.isHitLocationConsistentWithPos(pos, originalHit)
                && SablePlacementHitHelper.isHitLocationConsistentWithPos(pos, localHit);

        if (!transformedToLocal)
            return face;

        SubLevel containing = getContainingSubLevel(level, pos);
        if (containing == null)
            return face;

        Vector3d localNormal = containing.logicalPose()
                .transformNormalInverse(new Vector3d(face.getStepX(), face.getStepY(), face.getStepZ()), new Vector3d());
        if (localNormal.lengthSquared() <= 1.0E-9D)
            return face;

        return Direction.getNearest(localNormal.x, localNormal.y, localNormal.z);
    }

    private static Vec3 getLocalHitOrOriginal(Level level, BlockPos pos, BlockHitResult hit) {
        try {
            return SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, pos, hit);
        } catch (RuntimeException ignored) {
            return hit.getLocation();
        }
    }

    private static SubLevel getContainingSubLevel(Level level, BlockPos pos) {
        try {
            return Sable.HELPER.getContaining(level, pos) instanceof SubLevel containing ? containing : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void renderSlotMarker(ItemStack stack, boolean first, PoseStack ms, MultiBufferSource buffer) {
        ms.pushPose();
        ms.scale(-2.01f, -2.01f, 2.01f);
        ms.translate(-8 / 16.0D, -8 / 16.0D, -0.5D / 16.0D);
        (stack.isEmpty() ? AllIcons.VALUE_BOX_HOVER_4PX : AllIcons.VALUE_BOX_HOVER_6PX)
                .render(ms, buffer, first ? FIRST_FREQUENCY_COLOR : SECOND_FREQUENCY_COLOR);
        ms.popPose();
    }
}
