package com.proventure.twistermill.mixin.create;

import com.proventure.twistermill.compat.sable.SableDisplayLinkPositionHelper;
import net.createmod.catnip.ghostblock.GhostBlockParams;
import net.createmod.catnip.ghostblock.GhostBlocks;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(IPlacementHelper.class)
public interface IPlacementHelperMixin {

    @Redirect(
            method = "displayGhost",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/ghostblock/GhostBlocks;showGhostState(Ljava/lang/Object;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/createmod/catnip/ghostblock/GhostBlockParams;"
            ),
            remap = false
    )
    private static GhostBlockParams twistermill$showWorldFacingGhost(
            GhostBlocks ghostBlocks,
            Object slot,
            BlockState state,
            PlacementOffset offset
    ) {
        Level level = Minecraft.getInstance().level;
        BlockPos localPos = offset.getBlockPos();
        BlockState normalized = SableDisplayLinkPositionHelper.toWorldBlockState(level, localPos, state);
        return ghostBlocks.showGhostState(slot, normalized);
    }

    @ModifyArg(
            method = "displayGhost",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/ghostblock/GhostBlockParams;at(Lnet/minecraft/core/BlockPos;)Lnet/createmod/catnip/ghostblock/GhostBlockParams;"
            ),
            index = 0,
            remap = false
    )
    private static BlockPos twistermill$normalizeGhostTarget(BlockPos target) {
        Level level = Minecraft.getInstance().level;
        return SableDisplayLinkPositionHelper.toWorldBlockPos(level, target);
    }
}
