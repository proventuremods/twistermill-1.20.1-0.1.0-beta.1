package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.blockentity.ServoPropellerSlotManager;
import com.proventure.twistermill.event.MetalTraverseGirderPlacementHandler;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class MetalTraversePlacementHelper implements IPlacementHelper {

    @Override
    public @NotNull Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof MetalTraverseBlock;
    }

    @Override
    public @NotNull Predicate<BlockState> getStatePredicate() {
        return MetalFrameConnectionHelper::isMetalFrameConnector;
    }

    private static boolean canExtendToward(BlockState state, Direction side) {
        return MetalFrameConnectionHelper.canExtendMetalFrameToward(state, side);
    }

    private static LineScan scanAttachedPoles(Level world, BlockPos pos, Direction direction,
                                              Direction.Axis lineAxis) {
        BlockPos checkPos = pos.relative(direction);
        BlockState state = world.getBlockState(checkPos);
        int count = 0;
        boolean transitionCompatible = true;
        while (canExtendToward(state, direction)) {
            count++;
            transitionCompatible &= state.getBlock() == ModBlocks.METAL_TRAVERSE.get()
                    && state.getValue(MetalTraverseBlock.AXIS) == lineAxis;
            checkPos = checkPos.relative(direction);
            state = world.getBlockState(checkPos);
        }
        return new LineScan(count, transitionCompatible);
    }

    private static BlockState applyAimedPlacementState(BlockState placedState, Level world, BlockPos newPos,
                                                       BlockState sourceState, Direction extensionDirection,
                                                       Direction clickedFace, boolean yRotated) {
        return MetalTraverseBlock.computePlacementHelperState(
                world, newPos, placedState, sourceState, extensionDirection, clickedFace, yRotated);
    }

    @Override
    public @NotNull PlacementOffset getOffset(
            @NotNull Player player,
            @NotNull Level world,
            @NotNull BlockState state,
            @NotNull BlockPos pos,
            @NotNull BlockHitResult ray
    ) {
        return resolvePlacement(player, world, state, pos, ray, false).offset();
    }

    public static PlacementResolution resolvePlacement(@NotNull Player player, @NotNull Level world,
                                                       @NotNull BlockState state, @NotNull BlockPos pos,
                                                       @NotNull BlockHitResult ray, boolean allowGirderTransition) {
        Direction clickedFace = ray.getDirection();
        Vec3 hitLocation = SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(world, pos, ray);
        Direction slotOutward =
                ServoPropellerSlotManager.getMode7SlotPlacementHelperOutward(world, pos, state);
        List<Direction> directions =
                IPlacementHelper.orderedByDistance(
                        pos, hitLocation, dir -> canExtendToward(state, dir) || dir == slotOutward);
        Direction.Axis lineAxis = state.getValue(MetalTraverseBlock.AXIS);

        for (int directionIndex = 0; directionIndex < directions.size(); directionIndex++) {
            Direction direction = directions.get(directionIndex);
            int range = AllConfigs.server().equipment.placementAssistRange.get();
            AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
            if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id())) {
                range += 4;
            }

            LineScan lineScan = scanAttachedPoles(world, pos, direction, lineAxis);
            if (lineScan.poles() >= range) {
                continue;
            }

            BlockPos tailPos = pos.relative(direction, lineScan.poles());
            BlockState tailState = world.getBlockState(tailPos);
            boolean nextYRotated = tailState.getBlock() instanceof MetalTraverseBlock
                    && !tailState.getValue(MetalTraverseBlock.Y_ROTATED);

            BlockPos newPos = pos.relative(direction, lineScan.poles() + 1);
            BlockState newState = world.getBlockState(newPos);
            if (allowGirderTransition
                    && state.getBlock() == ModBlocks.METAL_TRAVERSE.get()
                    && state.getValue(MetalTraverseBlock.AXIS) == direction.getAxis()
                    && lineScan.transitionCompatible()
                    && newState.getBlock() == AllBlocks.METAL_GIRDER.get()
                    && player.mayBuild()
                    && world.mayInteract(player, newPos)) {
                BlockState compositeState = MetalTraverseGirderPlacementHandler.createTransitionCompositeState(
                        world, newPos, lineAxis);
                if (compositeState != null) {
                    PlacementOffset offset = PlacementOffset.success(newPos).withGhostState(compositeState);
                    return new PlacementResolution(offset,
                            new GirderTransition(
                                    newPos, newState.getValue(GirderBlock.AXIS), compositeState));
                }
            }

            if (!newState.canBeReplaced()) {
                if (directionIndex == 0) {
                    return PlacementResolution.fail();
                }
                continue;
            }

            PlacementOffset offset = PlacementOffset.success(newPos,
                    placedState -> applyAimedPlacementState(
                            placedState, world, newPos, state, direction, clickedFace, nextYRotated));
            return new PlacementResolution(offset, null);
        }

        return PlacementResolution.fail();
    }

    @Override
    public @NotNull PlacementOffset getOffset(@NotNull Player player, @NotNull Level world,
                                               @NotNull BlockState state, @NotNull BlockPos pos,
                                               @NotNull BlockHitResult ray, @NotNull ItemStack heldItem) {
        PlacementOffset reverseOffset = MetalTraverseGirderPlacementHandler.getReversePlacementOffset(
                player, world, state, pos, ray, heldItem);
        if (reverseOffset != null) {
            return reverseOffset;
        }

        boolean allowGirderTransition = heldItem == player.getMainHandItem()
                && heldItem.is(ModBlocks.METAL_TRAVERSE.get().asItem())
                && !player.isSecondaryUseActive();
        PlacementResolution resolution = resolvePlacement(
                player, world, state, pos, ray, allowGirderTransition);
        if (resolution.transition() == null && heldItem.getItem() instanceof BlockItem blockItem) {
            resolution.offset().withGhostState(blockItem.getBlock().defaultBlockState());
        }
        return resolution.offset();
    }

    @Override
    public void renderAt(
            @NotNull BlockPos pos,
            @NotNull BlockState state,
            @NotNull BlockHitResult ray,
            @NotNull PlacementOffset offset
    ) {
        if (MetalTraverseGirderPlacementHandler.displayReverseGhost(offset)) {
            return;
        }
        IPlacementHelper.super.renderAt(pos, state, ray, offset);
    }

    private record LineScan(int poles, boolean transitionCompatible) {
    }

    public record GirderTransition(BlockPos targetPos, Direction.Axis axis, BlockState compositeState) {
    }

    public record PlacementResolution(PlacementOffset offset, @Nullable GirderTransition transition) {

        private static PlacementResolution fail() {
            return new PlacementResolution(PlacementOffset.fail(), null);
        }
    }
}
