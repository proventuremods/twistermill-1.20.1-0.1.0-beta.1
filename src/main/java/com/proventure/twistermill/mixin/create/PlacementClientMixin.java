package com.proventure.twistermill.mixin.create;

import com.proventure.twistermill.compat.sable.SableDisplayLinkPositionHelper;
import net.createmod.catnip.placement.PlacementClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PlacementClient.class)
public abstract class PlacementClientMixin {

    @ModifyArg(
            method = "checkHelpers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/createmod/catnip/placement/PlacementClient;setTarget(Lnet/minecraft/core/BlockPos;)V"
            ),
            remap = false
    )
    private static BlockPos twistermill$normalizePlacementTarget(BlockPos target) {
        Level level = Minecraft.getInstance().level;
        return SableDisplayLinkPositionHelper.toWorldBlockPos(level, target);
    }
}
