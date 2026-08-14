package com.proventure.twistermill.mixin.create;

import com.proventure.twistermill.event.MetalTraverseGirderPlacementHandler;
import com.proventure.twistermill.compat.sable.SableDisplayLinkPositionHelper;
import net.createmod.catnip.placement.PlacementClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlacementClient.class)
public abstract class PlacementClientMixin {

    @Shadow(remap = false)
    private static void setTarget(BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "checkHelpers()V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void twistermill$showCrossAxisGirderPlacement(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        BlockPos sourcePos = SableDisplayLinkPositionHelper.toWorldBlockPos(
                minecraft.level, blockHitResult.getBlockPos());
        BlockPos targetPos = MetalTraverseGirderPlacementHandler.displayPriorityGirderGhost(
                minecraft.player,
                minecraft.level,
                minecraft.level.getBlockState(sourcePos),
                sourcePos,
                blockHitResult,
                minecraft.player.getMainHandItem());
        if (targetPos == null) {
            return;
        }

        setTarget(SableDisplayLinkPositionHelper.toWorldBlockPos(minecraft.level, targetPos));
        ci.cancel();
    }

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
