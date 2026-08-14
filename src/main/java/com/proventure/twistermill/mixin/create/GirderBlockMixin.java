package com.proventure.twistermill.mixin.create;

import com.proventure.twistermill.compat.create.MetalTraverseGirderStateResolver;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GirderBlock.class)
public abstract class GirderBlockMixin {

    @Inject(
            method = "updateState(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void twistermill$connectToEmbeddedGirder(LevelAccessor level, BlockPos pos, BlockState state,
                                                            Direction direction,
                                                            CallbackInfoReturnable<BlockState> cir) {
        cir.setReturnValue(MetalTraverseGirderStateResolver.connectToCompositeNeighbour(
                level, pos, cir.getReturnValue(), direction));
    }
}
