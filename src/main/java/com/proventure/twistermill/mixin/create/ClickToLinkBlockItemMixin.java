package com.proventure.twistermill.mixin.create;

import com.proventure.twistermill.compat.sable.SableDisplayLinkPositionHelper;
import com.simibubi.create.content.redstone.displayLink.ClickToLinkBlockItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClickToLinkBlockItem.class)
public abstract class ClickToLinkBlockItemMixin {

    @Redirect(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"
            ),
            remap = false
    )
    private boolean twistermill$checkDisplayLinkRangeInWorld(
            BlockPos selectedPos,
            Vec3i placedPos,
            double maxDistance,
            UseOnContext context
    ) {
        BlockPos placedBlockPos = placedPos instanceof BlockPos blockPos ? blockPos : new BlockPos(placedPos);
        Level level = context.getLevel();

        BlockPos selectedWorldPos = SableDisplayLinkPositionHelper.toWorldBlockPos(level, selectedPos);
        BlockPos placedWorldPos = SableDisplayLinkPositionHelper.toWorldBlockPos(level, placedBlockPos);
        return selectedWorldPos.closerThan(placedWorldPos, maxDistance);
    }
}
