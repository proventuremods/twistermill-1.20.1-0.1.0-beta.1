package com.proventure.twistermill.mixin.neoforge;

import com.proventure.twistermill.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(CommonHooks.class)
public abstract class CommonHooksLadderMixin {

    private static final double LADDER_SEARCH_MARGIN = 4.0 / 16.0;

    @Inject(
            method = "isLivingOnLadder(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void twistermill$findExternalTraverseLadder(
            BlockState state, Level level, BlockPos pos, LivingEntity entity,
            CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (cir.getReturnValue().isPresent()
                || entity == null
                || entity instanceof Player player && player.isSpectator()) {
            return;
        }

        AABB bounds = entity.getBoundingBox();
        int minX = Mth.floor(bounds.minX - LADDER_SEARCH_MARGIN);
        int minY = Mth.floor(bounds.minY);
        int minZ = Mth.floor(bounds.minZ - LADDER_SEARCH_MARGIN);
        int maxX = Mth.floor(bounds.maxX + LADDER_SEARCH_MARGIN);
        int maxY = Mth.floor(Math.nextDown(bounds.maxY));
        int maxZ = Mth.floor(bounds.maxZ + LADDER_SEARCH_MARGIN);

        for (BlockPos candidatePos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState candidateState = level.getBlockState(candidatePos);
            if (candidateState.getBlock() != ModBlocks.METAL_TRAVERSE.get()
                    && candidateState.getBlock() != ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
                continue;
            }
            if (candidateState.isLadder(level, candidatePos, entity)) {
                cir.setReturnValue(Optional.of(candidatePos.immutable()));
                return;
            }
        }
    }
}
