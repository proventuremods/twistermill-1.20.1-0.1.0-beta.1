package com.proventure.twistermill.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.proventure.twistermill.blockentity.InternalServoRedstoneLinkOwner;
import com.proventure.twistermill.blockentity.InternalServoRedstoneLinkSlots;
import com.proventure.twistermill.blockentity.SecondaryServoRedstoneLinkBehaviour;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

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

        if (owner.shouldRenderInternalRedstoneLinkSlots()) {
            LinkBehaviour behaviour = be.getBehaviour(LinkBehaviour.TYPE);
            if (behaviour != null) {
                renderSlots(be, inverted, false, behaviour.getNetworkKey().getFirst().getStack(),
                        behaviour.getNetworkKey().getSecond().getStack(),
                        owner.getInternalRedstoneLinkSide(), ms, buffer, light, overlay);
            }
        }

        if (owner.shouldRenderSecondaryInternalRedstoneLinkSlots()) {
            SecondaryServoRedstoneLinkBehaviour secondary =
                    be.getBehaviour(SecondaryServoRedstoneLinkBehaviour.TYPE);
            if (secondary != null) {
                renderSlots(be, inverted, true, secondary.getFrequencyStack(true),
                        secondary.getFrequencyStack(false), owner.getSecondaryInternalRedstoneLinkSide(),
                        ms, buffer, light, overlay);
            }
        }
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.level instanceof ClientLevel level)
                || !(mc.hitResult instanceof BlockHitResult hit)) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SmartBlockEntity smart)
                || !(smart instanceof InternalServoRedstoneLinkOwner owner)
                || !owner.shouldRenderSecondaryInternalRedstoneLinkSlots()) {
            return;
        }

        SecondaryServoRedstoneLinkBehaviour behaviour =
                smart.getBehaviour(SecondaryServoRedstoneLinkBehaviour.TYPE);
        if (behaviour == null) {
            return;
        }

        Vec3 localHit = getLocalHitOrOriginal(level, pos, hit);
        Component frequency1 = CreateLang.translateDirect("logistics.firstFrequency");
        Component frequency2 = CreateLang.translateDirect("logistics.secondFrequency");

        for (boolean first : Iterate.trueAndFalse) {
            Component label = first ? frequency1 : frequency2;
            boolean targeted = behaviour.testHit(first, localHit);
            boolean empty = behaviour.getFrequencyStack(first).isEmpty();
            ValueBox box = new ValueBox(label, new AABB(Vec3.ZERO, Vec3.ZERO).inflate(.25F), pos)
                    .passive(!targeted);
            if (!empty) {
                box.wideOutline();
            }

            Outliner.getInstance()
                    .showOutline(com.mojang.datafixers.util.Pair.of(
                            "twistermill_secondary_servo_link_" + first, pos), box.transform(behaviour.getSlotTransform(first)))
                    .highlightFace(resolveLocalHitFace(level, pos, hit));

            if (targeted) {
                List<MutableComponent> tooltip = new ArrayList<>();
                tooltip.add(label.copy());
                tooltip.add(CreateLang.translateDirect(
                        empty ? "logistics.filter.click_to_set" : "logistics.filter.click_to_replace"));
                CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(tooltip);
            }
        }
    }

    private static void renderSlots(
            SmartBlockEntity be,
            boolean inverted,
            boolean secondary,
            ItemStack firstStack,
            ItemStack secondStack,
            Direction side,
            PoseStack ms,
            MultiBufferSource buffer,
            int light,
            int overlay
    ) {
        boolean renderSlotMarkers = isTargetingSide(be, side);
        for (boolean first : Iterate.trueAndFalse) {
            ValueBoxTransform transform = InternalServoRedstoneLinkSlots.createSlot(first, inverted, secondary);
            ItemStack stack = first ? firstStack : secondStack;
            ms.pushPose();
            transform.transform(be.getLevel(), be.getBlockPos(), be.getBlockState(), ms);
            if (renderSlotMarkers) {
                renderSlotMarker(stack, first, ms, buffer);
            }
            ValueBoxRenderer.renderItemIntoValueBox(stack, ms, buffer, light, overlay);
            ms.popPose();
        }
    }

    private static boolean isTargetingSide(SmartBlockEntity be, Direction side) {
        Level level = be.getLevel();
        if (level == null)
            return false;

        if (!(Minecraft.getInstance().hitResult instanceof BlockHitResult hit))
            return false;

        BlockPos pos = be.getBlockPos();
        if (!isTargetingBlock(level, pos, hit))
            return false;

        return resolveLocalHitFace(level, pos, hit) == side;
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
