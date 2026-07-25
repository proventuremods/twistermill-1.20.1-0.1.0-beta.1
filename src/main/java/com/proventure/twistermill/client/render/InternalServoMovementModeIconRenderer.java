package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.util.SableLevelWrapper;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import com.simibubi.create.content.contraptions.DirectionalExtenderScrollOptionSlot;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.gui.AllIcons;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public final class InternalServoMovementModeIconRenderer {

    private InternalServoMovementModeIconRenderer() {
    }

    public static void renderOnBlockEntity(SmartBlockEntity be, int configuredMaxDegrees,
                                           PoseStack ms, MultiBufferSource buffer) {
        if (be == null || be.isRemoved())
            return;

        Level level = be.getLevel();
        if (level == null || !isSableSubLevel(level, be.getBlockPos()))
            return;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit))
            return;

        BlockPos pos = be.getBlockPos();
        if (!isTargetingBlock(level, pos, hit))
            return;

        BlockState state = be.getBlockState();
        ValueBoxTransform transform = createMaxAngleSlot();
        Direction face = resolveLocalHitFace(level, pos, hit);
        if (transform instanceof ValueBoxTransform.Sided sided) {
            sided.fromSide(face);
        }

        if (!transform.shouldRender(level, pos, state))
            return;

        Vec3 localHit = SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, pos, hit)
                .subtract(Vec3.atLowerCornerOf(pos));
        boolean highlight = transform.testHit(level, pos, state, localHit);

        ms.pushPose();
        transform.transform(level, pos, state, ms);
        if (highlight)
            renderWideOutline(ms, buffer);
        renderIcon(iconFor(configuredMaxDegrees), transform, ms, buffer);
        ms.popPose();
    }

    private static boolean isSableSubLevel(Level level, BlockPos pos) {
        return SableLevelWrapper.isSubLevel(level) || Sable.HELPER.getContaining(level, pos) instanceof SubLevel;
    }

    private static boolean isTargetingBlock(Level level, BlockPos pos, BlockHitResult hit) {
        if (hit.getBlockPos().equals(pos))
            return true;

        Vec3 localHit = SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, pos, hit);
        return SablePlacementHitHelper.isHitLocationConsistentWithPos(pos, localHit);
    }

    private static Direction resolveLocalHitFace(Level level, BlockPos pos, BlockHitResult hit) {
        Direction face = hit.getDirection();
        Vec3 originalHit = hit.getLocation();
        Vec3 localHit = SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, pos, hit);
        boolean transformedToLocal = !SablePlacementHitHelper.isHitLocationConsistentWithPos(pos, originalHit)
                && SablePlacementHitHelper.isHitLocationConsistentWithPos(pos, localHit);

        if (!transformedToLocal || !(Sable.HELPER.getContaining(level, pos) instanceof SubLevel containing))
            return face;

        Vector3d localNormal = containing.logicalPose()
                .transformNormalInverse(new Vector3d(face.getStepX(), face.getStepY(), face.getStepZ()), new Vector3d());
        if (localNormal.lengthSquared() <= 1.0E-9D)
            return face;

        return Direction.getNearest(localNormal.x, localNormal.y, localNormal.z);
    }

    private static ValueBoxTransform createMaxAngleSlot() {
        return new DirectionalExtenderScrollOptionSlot((state, direction) -> {
            Direction facing = state.getValue(BearingBlock.FACING);

            if (facing == Direction.DOWN && (direction == Direction.WEST || direction == Direction.EAST))
                return false;

            if (facing.getAxis().isHorizontal())
                return direction == Direction.UP || direction == Direction.DOWN;

            if (facing == Direction.UP && (direction == Direction.WEST || direction == Direction.EAST))
                return false;

            return facing.getAxis() != direction.getAxis();
        });
    }

    private static AllIcons iconFor(int configuredMaxDegrees) {
        return switch (configuredMaxDegrees) {
            case 120 -> AllIcons.I_ROTATE_PLACE;
            case 240 -> AllIcons.I_ROTATE_PLACE_RETURNED;
            default -> AllIcons.I_ROTATE_NEVER_PLACE;
        };
    }

    private static void renderWideOutline(PoseStack ms, MultiBufferSource buffer) {
        ms.pushPose();
        ms.scale(-2.01f, -2.01f, 2.01f);
        ms.translate(-8 / 16.0D, -8 / 16.0D, -0.5D / 16.0D);
        AllIcons.VALUE_BOX_HOVER_6PX.render(ms, buffer, 0xFFFFFF);
        ms.popPose();
    }

    private static void renderIcon(AllIcons icon, ValueBoxTransform transform, PoseStack ms, MultiBufferSource buffer) {
        ms.pushPose();
        float fontScale = -transform.getFontScale();
        ms.scale(fontScale, fontScale, fontScale);
        ms.scale(2 * 16, 2 * 16, 2 * 16);
        ms.translate(-0.5F, -0.5F, 5 / 32.0F);
        icon.render(ms, buffer, 0xFFFFFF);
        ms.popPose();
    }
}
