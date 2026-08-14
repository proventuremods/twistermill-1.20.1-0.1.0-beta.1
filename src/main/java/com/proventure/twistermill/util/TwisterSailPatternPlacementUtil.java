package com.proventure.twistermill.util;

import com.proventure.twistermill.block.custom.TwisterSailBlock;
import com.proventure.twistermill.config.TwisterMillConfig;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TwisterSailPatternPlacementUtil {

    private static final String ROOT_TAG = "TwisterMillSailPattern";
    private static final String WIDTH_TAG = "Width";
    private static final int[] CYCLE = new int[]{3, 5, 7, 9, 1};

    private TwisterSailPatternPlacementUtil() {
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isPatternSetup(Player player) {
        return isTwisterSailItem(player.getMainHandItem()) && AllItems.EXTENDO_GRIP.isIn(player.getOffhandItem());
    }

    public static ItemInteractionResult placeWithPattern(
            Player player,
            Level level,
            BlockPos sourcePos,
            BlockState sourceState,
            PlacementOffset anchorOffset,
            BlockItem blockItem,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (!anchorOffset.isSuccessful()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!isPatternSetup(player)) {
            return anchorOffset.placeInWorld(level, blockItem, player, hand, hit);
        }

        int width = getEffectiveWidth(player);
        if (width <= 1) {
            return anchorOffset.placeInWorld(level, blockItem, player, hand, hit);
        }

        BlockPos anchorPos = anchorOffset.getBlockPos();
        Direction lengthDirection = resolveLengthDirection(sourcePos, anchorPos);
        if (lengthDirection == null) {
            return anchorOffset.placeInWorld(level, blockItem, player, hand, hit);
        }

        List<BlockPos> positions = buildPatternPositions(anchorPos, sourceState.getValue(SailBlock.FACING), lengthDirection, width);
        if (positions.size() <= 1) {
            return anchorOffset.placeInWorld(level, blockItem, player, hand, hit);
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!player.isCreative() && held.getCount() < positions.size()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        for (BlockPos patternPos : positions) {
            if (!level.mayInteract(player, patternPos)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.getBlockState(patternPos).canBeReplaced()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
        }

        boolean placedAny = false;
        for (BlockPos patternPos : positions) {
            PlacementOffset perBlockOffset = PlacementOffset.success(
                    patternPos,
                    placedState -> placedState.setValue(SailBlock.FACING, sourceState.getValue(SailBlock.FACING))
            );
            ItemInteractionResult placed = perBlockOffset.placeInWorld(level, blockItem, player, hand, hit);
            if (placed != ItemInteractionResult.SUCCESS) {
                return placedAny ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            placedAny = true;
        }

        return placedAny ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static void cyclePatternWidthAndNotify(Player player) {
        int current = getEffectiveWidth(player);
        int next = nextWidth(current);
        setStoredWidth(player, next);
        int range = TwisterMillConfig.getResolvedSailPlacementAssistRange();
        player.displayClientMessage(
                Component.literal("pattern size: " + next + "x" + range).withStyle(ChatFormatting.GREEN),
                true
        );
    }

    public static boolean isTwisterSailItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        return blockItem.getBlock() instanceof TwisterSailBlock;
    }

    private static int getEffectiveWidth(Player player) {
        CompoundTag root = getRootTag(player, false);
        if (root == null || !root.contains(WIDTH_TAG)) {
            return 3;
        }
        return normalizeWidth(root.getInt(WIDTH_TAG));
    }

    private static void setStoredWidth(Player player, int width) {
        CompoundTag root = getRootTag(player, true);
        root.putInt(WIDTH_TAG, normalizeWidth(width));
        saveRootTag(player, root);
    }

    private static int nextWidth(int current) {
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                return CYCLE[(i + 1) % CYCLE.length];
            }
        }
        return 3;
    }

    private static int normalizeWidth(int width) {
        for (int allowed : CYCLE) {
            if (allowed == width) {
                return width;
            }
        }
        return 3;
    }

    private static List<BlockPos> buildPatternPositions(BlockPos anchorPos, Direction sailFacing, Direction lengthDirection, int width) {
        Direction.Axis facingAxis = sailFacing.getAxis();
        Direction.Axis lengthAxis = lengthDirection.getAxis();
        Direction.Axis widthAxis = findRemainingAxis(facingAxis, lengthAxis);
        if (widthAxis == null) {
            List<BlockPos> fallback = new ArrayList<>(1);
            fallback.add(anchorPos);
            return fallback;
        }

        Direction widthPositive = Direction.fromAxisAndDirection(widthAxis, Direction.AxisDirection.POSITIVE);
        int half = (width - 1) / 2;

        List<BlockPos> positions = new ArrayList<>(width);
        for (int offset = -half; offset <= half; offset++) {
            positions.add(anchorPos.relative(widthPositive, offset));
        }
        return positions;
    }

    private static Direction.Axis findRemainingAxis(Direction.Axis facingAxis, Direction.Axis lengthAxis) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != facingAxis && axis != lengthAxis) {
                return axis;
            }
        }
        return null;
    }

    private static Direction resolveLengthDirection(BlockPos sourcePos, BlockPos anchorPos) {
        int dx = Integer.compare(anchorPos.getX(), sourcePos.getX());
        int dy = Integer.compare(anchorPos.getY(), sourcePos.getY());
        int dz = Integer.compare(anchorPos.getZ(), sourcePos.getZ());

        int nonZero = (dx != 0 ? 1 : 0) + (dy != 0 ? 1 : 0) + (dz != 0 ? 1 : 0);
        if (nonZero != 1) {
            return null;
        }

        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dy > 0) return Direction.UP;
        if (dy < 0) return Direction.DOWN;
        if (dz > 0) return Direction.SOUTH;
        return Direction.NORTH;
    }

    @Contract("_, true -> !null")
    private static @Nullable CompoundTag getRootTag(Player player, boolean createIfMissing) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(Player.PERSISTED_NBT_TAG)) {
            if (!createIfMissing) {
                return null;
            }
            persistent.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }

        CompoundTag persisted = persistent.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(ROOT_TAG)) {
            if (!createIfMissing) {
                return null;
            }
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
}
