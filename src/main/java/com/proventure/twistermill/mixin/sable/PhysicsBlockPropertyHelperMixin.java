package com.proventure.twistermill.mixin.sable;

import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.config.TwisterMillConfig;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PhysicsBlockPropertyHelper.class, remap = false)
public abstract class PhysicsBlockPropertyHelperMixin {

    @Inject(
            method = "getMass(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)D",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private static void twistermill$useConfiguredBladeArmMass(BlockGetter level, BlockPos pos, BlockState state,
                                                               CallbackInfoReturnable<Double> cir) {
        double originalMass = cir.getReturnValue();
        if (originalMass == 0.0D) {
            return;
        }

        Block block = state.getBlock();
        if (block == ModBlocks.BLADE_ARM_BLOCK.get()) {
            cir.setReturnValue(TwisterMillConfig.getBladeArmBlockMass());
        } else if (block == ModBlocks.BLADE_ARM_EASTFACE_BLOCK.get()) {
            cir.setReturnValue(TwisterMillConfig.getBladeArmEastfaceBlockMass());
        } else if (block == ModBlocks.BLADE_ARM_WESTFACE_BLOCK.get()) {
            cir.setReturnValue(TwisterMillConfig.getBladeArmWestfaceBlockMass());
        }
    }
}
