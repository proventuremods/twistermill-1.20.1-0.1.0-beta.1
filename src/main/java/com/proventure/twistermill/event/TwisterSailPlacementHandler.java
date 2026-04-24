package com.proventure.twistermill.event;

import com.proventure.twistermill.TwisterMill;
import com.simibubi.create.content.contraptions.bearing.SailBlock;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = TwisterMill.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TwisterSailPlacementHandler {

    private static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new PlacementHelper());

    private TwisterSailPlacementHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND)
            return;

        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = player.getItemInHand(event.getHand());

        if (player.isShiftKeyDown())
            return;

        if (!player.mayBuild())
            return;

        if (!(state.getBlock() instanceof SailBlock))
            return;

        if (!(stack.getItem() instanceof BlockItem blockItem))
            return;

        if (!(blockItem.getBlock() instanceof SailBlock))
            return;

        IPlacementHelper placementHelper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        PlacementOffset offset = placementHelper.getOffset(player, level, state, pos, event.getHitVec());

        if (!offset.isSuccessful())
            return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        offset.placeInWorld(level, blockItem, player, event.getHand(), event.getHitVec());
    }

    @MethodsReturnNonnullByDefault
    private static class PlacementHelper implements IPlacementHelper {

        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem blockItem
                    && blockItem.getBlock() instanceof SailBlock;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof SailBlock;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(
                    pos,
                    ray.getLocation(),
                    state.getValue(SailBlock.FACING).getAxis(),
                    direction -> world.getBlockState(pos.relative(direction)).canBeReplaced()
            );

            if (directions.isEmpty())
                return PlacementOffset.fail();

            return PlacementOffset.success(
                    pos.relative(directions.get(0)),
                    placedState -> placedState.setValue(SailBlock.FACING, state.getValue(SailBlock.FACING))
            );
        }
    }
}