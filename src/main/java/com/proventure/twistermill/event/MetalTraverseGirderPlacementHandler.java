package com.proventure.twistermill.event;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.block.custom.MetalTraverseBlock;
import com.proventure.twistermill.block.custom.MetalTraversePlacementHelper;
import com.proventure.twistermill.block.custom.MetalTraverseWithGirderBlock;
import com.proventure.twistermill.blockentity.WrenchSideCycleBlockEntity;
import com.proventure.twistermill.util.SablePlacementHitHelper;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.equipment.extendoGrip.ExtendoGripItem;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public final class MetalTraverseGirderPlacementHandler {

    private static final PlacementHelper PLACEMENT_HELPER = new PlacementHelper();
    @SuppressWarnings("unused")
    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(PLACEMENT_HELPER);
    private static Consumer<GirderPreview> clientGirderPreview = preview -> {
    };
    private static Consumer<TraversePreview> clientTraversePreview = preview -> {
    };

    private MetalTraverseGirderPlacementHandler() {
    }

    public static void register() {
        // Forces static placement-helper registration during mod construction.
    }

    public static void installClientPreview(Consumer<GirderPreview> girderPreview,
                                            Consumer<TraversePreview> traversePreview) {
        clientGirderPreview = girderPreview;
        clientTraversePreview = traversePreview;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()) {
            return;
        }

        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos sourcePos = event.getPos();
        BlockState sourceState = level.getBlockState(sourcePos);
        ItemStack stack = player.getItemInHand(event.getHand());

        if (event.getHand() == InteractionHand.MAIN_HAND) {
            DirectConversion directConversion = resolveDirectConversion(player, sourceState, stack, event.getHitVec());
            if (directConversion != null) {
                event.setCanceled(true);
                if (!player.mayBuild()) {
                    event.setCancellationResult(InteractionResult.FAIL);
                    return;
                }
                if (level.isClientSide) {
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }

                ItemInteractionResult result = convertToComposite(
                        level, player, event.getHand(), event.getHitVec(), sourcePos,
                        directConversion.sourceKind(), directConversion.axis(), null);
                event.setCancellationResult(result == ItemInteractionResult.SUCCESS
                        ? InteractionResult.SUCCESS
                        : InteractionResult.FAIL);
                return;
            }

            PlacementPlan priorityGirderPlan = resolvePriorityTraversePlan(
                    player, level, sourceState, sourcePos, event.getHitVec(), stack);
            if (priorityGirderPlan != null) {
                event.setCanceled(true);
                if (level.isClientSide) {
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    return;
                }

                ItemInteractionResult result = convertToComposite(
                        level, player, event.getHand(), event.getHitVec(), priorityGirderPlan.targetPos(),
                        ConversionSource.TRAVERSE, priorityGirderPlan.axis(), null);
                event.setCancellationResult(result == ItemInteractionResult.SUCCESS
                        ? InteractionResult.SUCCESS
                        : InteractionResult.FAIL);
                return;
            }

            if (!player.isSecondaryUseActive()
                    && sourceState.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()
                    && stack.is(ModBlocks.METAL_TRAVERSE.get().asItem())) {
                ReversePlacementPlan reversePlan = resolveReversePlacement(
                        player, level, sourceState, sourcePos, event.getHitVec());
                if (reversePlan != null) {
                    event.setCanceled(true);
                    if (!player.mayBuild()) {
                        event.setCancellationResult(InteractionResult.FAIL);
                        return;
                    }
                    if (level.isClientSide) {
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        return;
                    }

                    ItemInteractionResult result = convertToComposite(
                            level, player, event.getHand(), event.getHitVec(), reversePlan.targetPos(),
                            ConversionSource.GIRDER, reversePlan.girderAxis(), reversePlan.compositeState());
                    event.setCancellationResult(result == ItemInteractionResult.SUCCESS
                            ? InteractionResult.SUCCESS
                            : InteractionResult.FAIL);
                    return;
                }
            }

            if (!player.isSecondaryUseActive()
                    && sourceState.getBlock() == ModBlocks.METAL_TRAVERSE.get()
                    && stack.is(ModBlocks.METAL_TRAVERSE.get().asItem())) {
                MetalTraversePlacementHelper.PlacementResolution resolution =
                        MetalTraversePlacementHelper.resolvePlacement(
                                player, level, sourceState, sourcePos, event.getHitVec(), true);
                MetalTraversePlacementHelper.GirderTransition transition = resolution.transition();
                if (transition != null) {
                    event.setCanceled(true);
                    if (level.isClientSide) {
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        return;
                    }

                    ItemInteractionResult result = convertToComposite(
                            level, player, event.getHand(), event.getHitVec(), transition.targetPos(),
                            ConversionSource.GIRDER, transition.axis(), transition.compositeState());
                    event.setCancellationResult(result == ItemInteractionResult.SUCCESS
                            ? InteractionResult.SUCCESS
                            : InteractionResult.FAIL);
                    return;
                }
            }
        }

        if (player.isShiftKeyDown() || !player.mayBuild()) {
            return;
        }

        if (!AllBlocks.METAL_GIRDER.isIn(stack) || !(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        if (!PLACEMENT_HELPER.matchesState(sourceState)) {
            return;
        }

        PlacementPlan plan = resolvePlan(player, level, sourceState, sourcePos, event.getHitVec());
        if (plan == null || !plan.requiresTwisterMillHandling()) {
            return;
        }

        event.setCanceled(true);
        if (level.isClientSide) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        ItemInteractionResult result = plan.kind() == PlacementKind.CONVERT_TRAVERSE
                ? convertToComposite(level, player, event.getHand(), event.getHitVec(), plan.targetPos(),
                ConversionSource.TRAVERSE, plan.axis(), null)
                : plan.offset().placeInWorld(level, blockItem, player, event.getHand(), event.getHitVec());
        event.setCancellationResult(result == ItemInteractionResult.SUCCESS
                ? InteractionResult.SUCCESS
                : InteractionResult.FAIL);
    }

    private static DirectConversion resolveDirectConversion(Player player, BlockState sourceState, ItemStack stack,
                                                            BlockHitResult hitResult) {
        if (sourceState.getBlock() == ModBlocks.METAL_TRAVERSE.get()
                && AllBlocks.METAL_GIRDER.isIn(stack)) {
            return player.isSecondaryUseActive()
                    ? null
                    : new DirectConversion(ConversionSource.TRAVERSE, hitResult.getDirection().getAxis());
        }
        if (sourceState.getBlock() == AllBlocks.METAL_GIRDER.get()
                && stack.is(ModBlocks.METAL_TRAVERSE.get().asItem())) {
            return new DirectConversion(
                    ConversionSource.GIRDER, sourceState.getValue(GirderBlock.AXIS));
        }
        return null;
    }

    private static ItemInteractionResult convertToComposite(Level level, Player player, InteractionHand hand,
                                                             BlockHitResult hitResult, BlockPos targetPos,
                                                             ConversionSource sourceKind,
                                                             Direction.Axis requestedAxis,
                                                             @Nullable BlockState requestedCompositeState) {
        if (!player.mayBuild() || !level.mayInteract(player, targetPos)) {
            return ItemInteractionResult.FAIL;
        }

        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.isEmpty() || !sourceKind.matchesHeldItem(heldStack)) {
            return ItemInteractionResult.FAIL;
        }

        BlockState oldState = level.getBlockState(targetPos);
        if (!sourceKind.matchesSourceState(oldState)) {
            return ItemInteractionResult.FAIL;
        }

        BlockEntity oldBlockEntity = level.getBlockEntity(targetPos);
        Direction.Axis girderAxis;
        BlockState compositeState;
        if (sourceKind == ConversionSource.TRAVERSE) {
            if (!(oldBlockEntity instanceof WrenchSideCycleBlockEntity)) {
                return ItemInteractionResult.FAIL;
            }
            girderAxis = requestedAxis;
            compositeState = MetalTraverseWithGirderBlock.fromTraverseState(oldState, girderAxis);
        } else {
            if (oldBlockEntity != null) {
                return ItemInteractionResult.FAIL;
            }
            girderAxis = oldState.getValue(GirderBlock.AXIS);
            if (requestedCompositeState == null) {
                BlockState traverseState = MetalTraverseBlock.getStateForDirectPlacement(
                        level, targetPos, hitResult.getDirection());
                compositeState = MetalTraverseWithGirderBlock.fromTraverseState(traverseState, girderAxis);
            } else {
                if (requestedCompositeState.getBlock() != ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()
                        || requestedCompositeState.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS) != girderAxis) {
                    return ItemInteractionResult.FAIL;
                }
                compositeState = requestedCompositeState;
            }
        }

        BlockSnapshot rollbackSnapshot = BlockSnapshot.create(
                level.dimension(), level, targetPos, Block.UPDATE_ALL);
        ItemStack placedStack = heldStack.copy();
        if (CatnipServices.HOOKS.playerPlaceSingleBlock(player, level, targetPos, compositeState)) {
            rollbackSnapshot.restore(Block.UPDATE_ALL);
            return ItemInteractionResult.FAIL;
        }

        BlockState placedState = level.getBlockState(targetPos);
        BlockEntity placedBlockEntity = level.getBlockEntity(targetPos);
        boolean validBlockEntity = placedBlockEntity instanceof WrenchSideCycleBlockEntity sideCycle
                && (sourceKind == ConversionSource.TRAVERSE
                ? placedBlockEntity == oldBlockEntity
                : hasDefaultSideCycle(sideCycle));
        if (placedState.getBlock() != ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()
                || placedState.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS) != girderAxis
                || !validBlockEntity) {
            rollbackSnapshot.restore(Block.UPDATE_ALL);
            return ItemInteractionResult.FAIL;
        }

        WrenchSideCycleBlockEntity sideCycle = (WrenchSideCycleBlockEntity) placedBlockEntity;
        sideCycle.setTraverseAddedToGirder(sourceKind == ConversionSource.GIRDER);
        sideCycle.markChangedAndSync();

        var soundType = placedState.getSoundType(level, targetPos, player);
        level.playSound(null, targetPos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1.0F) / 2.0F, soundType.getPitch() * 0.8F);
        level.gameEvent(GameEvent.BLOCK_PLACE, targetPos, GameEvent.Context.of(player, placedState));
        player.awardStat(Stats.ITEM_USED.get(heldStack.getItem()));
        placedState.getBlock().setPlacedBy(level, targetPos, placedState, player, placedStack);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, targetPos, placedStack);
        }
        heldStack.consume(1, player);
        return ItemInteractionResult.SUCCESS;
    }

    private static boolean hasDefaultSideCycle(WrenchSideCycleBlockEntity sideCycle) {
        for (Direction direction : Direction.values()) {
            if (sideCycle.getStage(direction) != WrenchSideCycleBlockEntity.STAGE_AUTO_A) {
                return false;
            }
        }
        return true;
    }

    public static @Nullable PlacementOffset getReversePlacementOffset(Player player, Level level,
                                                                       BlockState sourceState, BlockPos sourcePos,
                                                                       BlockHitResult hitResult, ItemStack heldStack) {
        if (!player.mayBuild()
                || player.isSecondaryUseActive()
                || !heldStack.is(ModBlocks.METAL_TRAVERSE.get().asItem())
                || sourceState.getBlock() != ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
            return null;
        }
        ReversePlacementPlan plan = resolveReversePlacement(player, level, sourceState, sourcePos, hitResult);
        return plan == null ? null : plan.offset();
    }

    public static boolean displayReverseGhost(PlacementOffset offset) {
        if (!offset.hasGhostState()) {
            return false;
        }
        BlockState ghostState = offset.getTransform().apply(offset.getGhostState());
        if (ghostState.getBlock() != ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
            return false;
        }
        clientTraversePreview.accept(new TraversePreview(offset.getBlockPos(), ghostState));
        return true;
    }

    public static @Nullable BlockState createTransitionCompositeState(Level level, BlockPos targetPos,
                                                                       Direction.Axis lineAxis) {
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.getBlock() != AllBlocks.METAL_GIRDER.get()) {
            return null;
        }
        Direction.Axis girderAxis = targetState.getValue(GirderBlock.AXIS);

        YRotationResolution rotation = resolveHelperYRotation(
                level,
                targetPos,
                lineAxis,
                neighbourState -> isTransitionRotationNeighbour(neighbourState, lineAxis));
        if (!rotation.valid()) {
            return null;
        }

        BlockState traverseState = MetalTraverseBlock.computeGirderHelperState(
                level,
                targetPos,
                lineAxis,
                rotation.yRotated(),
                targetState.getValue(WATERLOGGED));
        return MetalTraverseWithGirderBlock.fromTraverseState(traverseState, girderAxis);
    }

    public static @Nullable BlockPos displayPriorityGirderGhost(Player player, Level level,
                                                                 BlockState sourceState, BlockPos sourcePos,
                                                                 BlockHitResult hitResult, ItemStack heldStack) {
        PlacementPlan plan = resolvePriorityTraversePlan(
                player, level, sourceState, sourcePos, hitResult, heldStack);
        if (plan == null) {
            return null;
        }
        clientGirderPreview.accept(new GirderPreview(plan.targetPos(), plan.axis()));
        return plan.targetPos();
    }

    private static @Nullable ReversePlacementPlan resolveReversePlacement(Player player, Level level,
                                                                           BlockState sourceState,
                                                                           BlockPos sourcePos,
                                                                           BlockHitResult hitResult) {
        if (sourceState.getBlock() != ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
            return null;
        }

        Direction.Axis girderAxis = sourceState.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS);
        List<Direction> directions = IPlacementHelper.orderedByDistance(
                sourcePos,
                SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, sourcePos, hitResult),
                direction -> direction.getAxis() == girderAxis);
        int range = getPlacementRange(player);

        for (Direction direction : directions) {
            for (int distance = 1; distance <= range; distance++) {
                BlockPos candidatePos = sourcePos.relative(direction, distance);
                BlockState candidateState = level.getBlockState(candidatePos);
                if (candidateState.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
                    if (candidateState.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS) != girderAxis) {
                        break;
                    }
                    continue;
                }
                if (candidateState.getBlock() != AllBlocks.METAL_GIRDER.get()
                        || candidateState.getValue(GirderBlock.AXIS) != girderAxis) {
                    break;
                }

                YRotationResolution rotation = resolveHelperYRotation(
                        level,
                        candidatePos,
                        girderAxis,
                        neighbourState -> neighbourState.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()
                                && neighbourState.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS) == girderAxis);
                if (!rotation.valid()) {
                    break;
                }
                BlockState traverseState = MetalTraverseBlock.computeGirderHelperState(
                        level,
                        candidatePos,
                        girderAxis,
                        rotation.yRotated(),
                        candidateState.getValue(WATERLOGGED));
                BlockState compositeState = MetalTraverseWithGirderBlock.fromTraverseState(
                        traverseState, girderAxis);
                PlacementOffset offset = PlacementOffset.success(candidatePos).withGhostState(compositeState);
                return new ReversePlacementPlan(candidatePos, girderAxis, compositeState, offset);
            }
        }
        return null;
    }

    private static YRotationResolution resolveHelperYRotation(Level level, BlockPos pos,
                                                               Direction.Axis axis,
                                                               Predicate<BlockState> compatibleNeighbour) {
        Boolean requiredValue = null;
        for (Direction.AxisDirection axisDirection : Direction.AxisDirection.values()) {
            Direction direction = Direction.fromAxisAndDirection(axis, axisDirection);
            BlockState neighbourState = level.getBlockState(pos.relative(direction));
            if (!compatibleNeighbour.test(neighbourState)) {
                continue;
            }
            boolean candidateValue = !neighbourState.getValue(MetalTraverseBlock.Y_ROTATED);
            if (requiredValue != null && requiredValue != candidateValue) {
                return new YRotationResolution(false, false);
            }
            requiredValue = candidateValue;
        }
        return new YRotationResolution(true, requiredValue != null && requiredValue);
    }

    private static boolean isTransitionRotationNeighbour(BlockState state, Direction.Axis lineAxis) {
        return (state.getBlock() == ModBlocks.METAL_TRAVERSE.get()
                || state.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get())
                && state.getValue(MetalTraverseBlock.AXIS) == lineAxis;
    }

    private static PlacementPlan resolvePlan(Player player, Level level, BlockState sourceState, BlockPos sourcePos,
                                             BlockHitResult hitResult) {
        List<Direction> directions = IPlacementHelper.orderedByDistance(
                sourcePos,
                SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, sourcePos, hitResult),
                direction -> canExtendToward(sourceState, direction));
        int range = getPlacementRange(player);

        for (Direction direction : directions) {
            Direction.Axis axis = direction.getAxis();
            boolean crossedComposite = sourceState.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get();
            for (int distance = 1; distance <= range; distance++) {
                BlockPos candidatePos = sourcePos.relative(direction, distance);
                BlockState candidateState = level.getBlockState(candidatePos);

                if (isCompatiblePlainGirder(candidateState, direction)) {
                    continue;
                }
                if (candidateState.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
                    if (candidateState.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS) != axis) {
                        break;
                    }
                    crossedComposite = true;
                    continue;
                }
                if (candidateState.getBlock() == ModBlocks.METAL_TRAVERSE.get()) {
                    BlockState ghostState = MetalTraverseWithGirderBlock.fromTraverseState(candidateState, axis);
                    PlacementOffset offset = PlacementOffset.success(candidatePos).withGhostState(ghostState);
                    return new PlacementPlan(PlacementKind.CONVERT_TRAVERSE, candidatePos, axis, offset, true);
                }
                if (candidateState.canBeReplaced()) {
                    if (!crossedComposite) {
                        break;
                    }
                    PlacementOffset offset = PlacementOffset.success(candidatePos,
                                    state -> Block.updateFromNeighbourShapes(withAxis(state, axis), level, candidatePos))
                            .withGhostState(AllBlocks.METAL_GIRDER.getDefaultState());
                    return new PlacementPlan(PlacementKind.PLACE_FREE, candidatePos, axis, offset, true);
                }
                break;
            }
        }
        return null;
    }

    private static @Nullable PlacementPlan resolvePriorityTraversePlan(Player player, Level level,
                                                                        BlockState sourceState, BlockPos sourcePos,
                                                                        BlockHitResult hitResult,
                                                                        ItemStack heldStack) {
        if (!player.mayBuild()
                || player.isSecondaryUseActive()
                || heldStack != player.getMainHandItem()
                || !AllBlocks.METAL_GIRDER.isIn(heldStack)
                || sourceState.getBlock() != AllBlocks.METAL_GIRDER.get()) {
            return null;
        }

        List<Direction> directions = IPlacementHelper.orderedByDistance(
                sourcePos,
                SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(level, sourcePos, hitResult),
                direction -> true);
        for (Direction direction : directions) {
            BlockPos candidatePos = sourcePos.relative(direction);
            BlockState candidateState = level.getBlockState(candidatePos);
            Direction.Axis axis = direction.getAxis();
            if (candidateState.getBlock() != ModBlocks.METAL_TRAVERSE.get()
                    || candidateState.getValue(MetalTraverseBlock.AXIS) != axis
                    || !level.mayInteract(player, candidatePos)) {
                continue;
            }
            BlockState ghostState = MetalTraverseWithGirderBlock.fromTraverseState(candidateState, axis);
            PlacementOffset offset = PlacementOffset.success(candidatePos).withGhostState(ghostState);
            return new PlacementPlan(PlacementKind.CONVERT_TRAVERSE, candidatePos, axis, offset, true);
        }
        return null;
    }

    private static int getPlacementRange(Player player) {
        int range = AllConfigs.server().equipment.placementAssistRange.get();
        AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id())) {
            range += 4;
        }
        return range;
    }

    private static boolean canExtendToward(BlockState state, Direction direction) {
        Direction.Axis axis = direction.getAxis();
        if (state.getBlock() == AllBlocks.METAL_GIRDER.get()) {
            boolean x = state.getValue(GirderBlock.X);
            boolean z = state.getValue(GirderBlock.Z);
            if (!x && !z) {
                return axis == Direction.Axis.Y;
            }
            return x && z || axis == (x ? Direction.Axis.X : Direction.Axis.Z);
        }
        return state.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()
                && state.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS) == axis;
    }

    private static boolean isCompatiblePlainGirder(BlockState state, Direction direction) {
        return state.getBlock() == AllBlocks.METAL_GIRDER.get() && canExtendToward(state, direction);
    }

    private static BlockState withAxis(BlockState state, Direction.Axis axis) {
        return state.setValue(GirderBlock.X, axis == Direction.Axis.X)
                .setValue(GirderBlock.Z, axis == Direction.Axis.Z)
                .setValue(GirderBlock.AXIS, axis);
    }

    public record GirderPreview(BlockPos pos, Direction.Axis axis) {
    }

    public record TraversePreview(BlockPos pos, BlockState compositeState) {
    }

    private enum PlacementKind {
        CONVERT_TRAVERSE,
        PLACE_FREE
    }

    private enum ConversionSource {
        TRAVERSE,
        GIRDER;

        private boolean matchesHeldItem(ItemStack stack) {
            return this == TRAVERSE
                    ? AllBlocks.METAL_GIRDER.isIn(stack)
                    : stack.is(ModBlocks.METAL_TRAVERSE.get().asItem());
        }

        private boolean matchesSourceState(BlockState state) {
            return state.getBlock() == (this == TRAVERSE
                    ? ModBlocks.METAL_TRAVERSE.get()
                    : AllBlocks.METAL_GIRDER.get());
        }
    }

    private record DirectConversion(ConversionSource sourceKind, Direction.Axis axis) {
    }

    private record PlacementPlan(PlacementKind kind, BlockPos targetPos, Direction.Axis axis,
                                 PlacementOffset offset, boolean requiresTwisterMillHandling) {
    }

    private record ReversePlacementPlan(BlockPos targetPos, Direction.Axis girderAxis,
                                        BlockState compositeState, PlacementOffset offset) {
    }

    private record YRotationResolution(boolean valid, boolean yRotated) {
    }

    private static final class PlacementHelper implements IPlacementHelper {

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return AllBlocks.METAL_GIRDER::isIn;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() == AllBlocks.METAL_GIRDER.get()
                    || state.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get();
        }

        @Override
        public PlacementOffset getOffset(@NotNull Player player, @NotNull Level level, @NotNull BlockState state,
                                         @NotNull BlockPos pos, @NotNull BlockHitResult hitResult) {
            PlacementPlan plan = resolvePlan(player, level, state, pos, hitResult);
            return plan == null || !plan.requiresTwisterMillHandling() ? PlacementOffset.fail() : plan.offset();
        }

        @Override
        public void displayGhost(PlacementOffset offset) {
            if (offset.hasGhostState()) {
                BlockState ghostState = offset.getTransform().apply(offset.getGhostState());
                if (ghostState.getBlock() == ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
                    clientGirderPreview.accept(new GirderPreview(
                            offset.getBlockPos(), ghostState.getValue(MetalTraverseWithGirderBlock.GIRDER_AXIS)));
                    return;
                }
            }
            IPlacementHelper.super.displayGhost(offset);
        }
    }
}
