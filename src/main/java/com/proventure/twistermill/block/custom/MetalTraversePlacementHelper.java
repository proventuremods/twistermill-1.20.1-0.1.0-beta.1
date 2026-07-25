package com.proventure.twistermill.block.custom;

import com.proventure.twistermill.util.SablePlacementHitHelper;
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

import java.util.List;
import java.util.function.Predicate;

public class MetalTraversePlacementHelper implements IPlacementHelper {

    @Override
    public Predicate<ItemStack> getItemPredicate() {
        return stack -> stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof MetalTraverseBlock;
    }

    @Override
    public Predicate<BlockState> getStatePredicate() {
        return MetalFrameConnectionHelper::isMetalFrameConnector;
    }

    private boolean canExtendToward(BlockState state, Direction side) {
        return MetalFrameConnectionHelper.canExtendMetalFrameToward(state, side);
    }

    private int attachedPoles(Level world, BlockPos pos, Direction direction) {
        BlockPos checkPos = pos.relative(direction);
        BlockState state = world.getBlockState(checkPos);
        int count = 0;
        while (canExtendToward(state, direction)) {
            count++;
            checkPos = checkPos.relative(direction);
            state = world.getBlockState(checkPos);
        }
        return count;
    }

    private BlockState applyAimedPlacementState(BlockState placedState, Level world, BlockPos newPos, BlockState sourceState,
                                                Direction extensionDirection, Direction clickedFace, boolean yRotated) {
        return MetalTraverseBlock.computePlacementHelperState(
                world, newPos, placedState, sourceState, extensionDirection, clickedFace, yRotated);
    }

    @Override
    public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
        Direction clickedFace = ray.getDirection();
        Vec3 hitLocation = SablePlacementHitHelper.ensureHitLocationInSameSpaceAsPos(world, pos, ray);
        List<Direction> directions =
                IPlacementHelper.orderedByDistance(pos, hitLocation, dir -> canExtendToward(state, dir));

        for (int directionIndex = 0; directionIndex < directions.size(); directionIndex++) {
            Direction direction = directions.get(directionIndex);
            int range = AllConfigs.server().equipment.placementAssistRange.get();
            if (player != null) {
                AttributeInstance reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
                if (reach != null && reach.hasModifier(ExtendoGripItem.singleRangeAttributeModifier.id())) {
                    range += 4;
                }
            }

            int poles = attachedPoles(world, pos, direction);
            if (poles >= range) {
                continue;
            }

            BlockPos tailPos = pos.relative(direction, poles);
            BlockState tailState = world.getBlockState(tailPos);
            boolean nextYRotated = tailState.getBlock() instanceof MetalTraverseBlock
                    && !tailState.getValue(MetalTraverseBlock.Y_ROTATED);

            BlockPos newPos = pos.relative(direction, poles + 1);
            BlockState newState = world.getBlockState(newPos);
            if (!newState.canBeReplaced()) {
                if (directionIndex == 0) {
                    return PlacementOffset.fail();
                }
                continue;
            }

            return PlacementOffset.success(newPos,
                    placedState -> applyAimedPlacementState(placedState, world, newPos, state, direction, clickedFace, nextYRotated));
        }

        return PlacementOffset.fail();
    }
}
