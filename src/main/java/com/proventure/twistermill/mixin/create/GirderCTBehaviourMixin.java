package com.proventure.twistermill.mixin.create;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.compat.create.MetalTraverseGirderStateResolver;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.girder.GirderCTBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GirderCTBehaviour.class)
public abstract class GirderCTBehaviourMixin {

    @Inject(
            method = "connectsTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void twistermill$connectToEmbeddedGirder(BlockState state, BlockState other,
                                                     BlockAndTintGetter reader, BlockPos pos, BlockPos otherPos,
                                                     Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()
                || state.getBlock() != AllBlocks.METAL_GIRDER.get()
                || other.getBlock() != ModBlocks.METAL_TRAVERSE_WITH_GIRDER.get()) {
            return;
        }
        BlockState embeddedState = MetalTraverseGirderStateResolver.resolveEmbeddedGirderState(
                reader, otherPos, other);
        if (embeddedState != null
                && !embeddedState.getValue(GirderBlock.X)
                && !embeddedState.getValue(GirderBlock.Z)) {
            cir.setReturnValue(true);
        }
    }
}
