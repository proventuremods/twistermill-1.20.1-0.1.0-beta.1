package com.proventure.twistermill.event;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.blockentity.InvServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.ServoTwisterBlockEntity;
import com.proventure.twistermill.blockentity.WindRotoBlockEntity;
import com.proventure.twistermill.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TwisterMill.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StickServoBindingHandler {

    private static final String ROOT_TAG = "TwisterMillStickBinding";
    private static final String PENDING_DIM_TAG = "PendingWindRotoDim";
    private static final String PENDING_POS_TAG = "PendingWindRotoPos";
    private static final ResourceLocation DYNAMIC_SU_ADVANCEMENT =
            ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "twistermill_dynamic_su");

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        Player player = event.getEntity();
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.BINDING_STICK.get()))
            return;

        Level level = event.getLevel();
        BlockPos clickedPos = event.getPos();
        BlockEntity clickedBlockEntity = level.getBlockEntity(clickedPos);

        boolean isRelevantTarget = clickedBlockEntity instanceof WindRotoBlockEntity
                || clickedBlockEntity instanceof ServoTwisterBlockEntity
                || clickedBlockEntity instanceof InvServoTwisterBlockEntity;

        if (!player.isShiftKeyDown() && !isRelevantTarget)
            return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (level.isClientSide)
            return;

        if (player.isShiftKeyDown()) {
            clearBindingsAndPending(player, level, clickedBlockEntity);
            return;
        }

        if (clickedBlockEntity instanceof WindRotoBlockEntity) {
            setPendingWindRoto(player, level, clickedPos);
            player.displayClientMessage(
                    Component.literal("TwisterMill binding mode active. Right-click a servo with the stick.")
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
            return;
        }

        PendingWindRoto pending = getPendingWindRoto(player);
        if (pending == null) {
            player.displayClientMessage(
                    Component.literal("No WindRoto selected for binding mode.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        if (!pending.dimension.equals(level.dimension().location().toString())) {
            clearPendingWindRoto(player);
            player.displayClientMessage(
                    Component.literal("Stored WindRoto is in another dimension. Binding mode has been reset.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        BlockEntity pendingTarget = level.getBlockEntity(pending.pos);
        if (!(pendingTarget instanceof WindRotoBlockEntity windRoto)) {
            clearPendingWindRoto(player);
            player.displayClientMessage(
                    Component.literal("Stored WindRoto not found. Binding mode has been reset.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        if (clickedBlockEntity instanceof ServoTwisterBlockEntity) {
            boolean changed = windRoto.addBoundServo(clickedPos, false);
            player.displayClientMessage(
                    Component.literal(changed
                                    ? "Servo linked to Twistermill bearing."
                                    : "Servo was already linked.")
                            .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                    true
            );
            checkAndGrantDynamicSuAdvancement(player, windRoto);
            return;
        }

        if (clickedBlockEntity instanceof InvServoTwisterBlockEntity) {
            boolean changed = windRoto.addBoundServo(clickedPos, true);
            player.displayClientMessage(
                    Component.literal(changed
                                    ? "Inverted servo linked to Twistermill bearing."
                                    : "Inverted servo was already linked.")
                            .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.YELLOW),
                    true
            );
            checkAndGrantDynamicSuAdvancement(player, windRoto);
        }
    }

    private static void clearBindingsAndPending(Player player, Level level, BlockEntity clickedBlockEntity) {
        PendingWindRoto pending = getPendingWindRoto(player);
        clearPendingWindRoto(player);

        WindRotoBlockEntity targetWindRoto = null;

        if (clickedBlockEntity instanceof WindRotoBlockEntity windRoto) {
            targetWindRoto = windRoto;
        } else if (pending != null && pending.dimension.equals(level.dimension().location().toString())) {
            BlockEntity be = level.getBlockEntity(pending.pos);
            if (be instanceof WindRotoBlockEntity windRoto) {
                targetWindRoto = windRoto;
            }
        }

        if (targetWindRoto != null) {
            targetWindRoto.clearBoundServos();
            player.displayClientMessage(
                    Component.literal("Binding mode and all Twistermill bearing / Servo links were cleared.")
                            .withStyle(ChatFormatting.YELLOW),
                    true
            );
            return;
        }

        player.displayClientMessage(
                Component.literal("Binding mode has been reset.")
                        .withStyle(ChatFormatting.YELLOW),
                true
        );
    }

    private static void setPendingWindRoto(Player player, Level level, BlockPos pos) {
        CompoundTag root = getOrCreateRootTag(player);
        root.putString(PENDING_DIM_TAG, level.dimension().location().toString());
        root.putLong(PENDING_POS_TAG, pos.asLong());
        saveRootTag(player, root);
    }

    private static void clearPendingWindRoto(Player player) {
        CompoundTag root = getRootTag(player, false);
        if (root == null)
            return;

        if (!root.contains(PENDING_DIM_TAG) && !root.contains(PENDING_POS_TAG))
            return;

        root.remove(PENDING_DIM_TAG);
        root.remove(PENDING_POS_TAG);
        saveRootTag(player, root);
    }

    private static PendingWindRoto getPendingWindRoto(Player player) {
        CompoundTag root = getRootTag(player, false);
        if (root == null)
            return null;

        if (!root.contains(PENDING_DIM_TAG) || !root.contains(PENDING_POS_TAG))
            return null;

        return new PendingWindRoto(
                root.getString(PENDING_DIM_TAG),
                BlockPos.of(root.getLong(PENDING_POS_TAG))
        );
    }

    private static CompoundTag getOrCreateRootTag(Player player) {
        return getRootTag(player, true);
    }

    private static CompoundTag getRootTag(Player player, boolean createIfMissing) {
        CompoundTag persistent = player.getPersistentData();

        if (!persistent.contains(Player.PERSISTED_NBT_TAG)) {
            if (!createIfMissing)
                return null;
            persistent.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }

        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ROOT_TAG)) {
            if (!createIfMissing)
                return null;
            persisted.put(ROOT_TAG, new CompoundTag());
            persistent.put(Player.PERSISTED_NBT_TAG, persisted);
        }

        return persisted.getCompound(ROOT_TAG);
    }

    private static void saveRootTag(Player player, CompoundTag rootTag) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(Player.PERSISTED_NBT_TAG)) {
            persistent.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }

        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(ROOT_TAG, rootTag);
        persistent.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static void checkAndGrantDynamicSuAdvancement(Player player, WindRotoBlockEntity windRoto) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        if (!windRoto.hasBothServoTypes())
            return;

        Advancement advancement = serverPlayer.server.getAdvancements().getAdvancement(DYNAMIC_SU_ADVANCEMENT);
        if (advancement == null)
            return;
        if (serverPlayer.getAdvancements().getOrStartProgress(advancement).isDone())
            return;

        serverPlayer.getAdvancements().award(advancement, "binding_complete");
    }

    private record PendingWindRoto(String dimension, BlockPos pos) {
    }
}