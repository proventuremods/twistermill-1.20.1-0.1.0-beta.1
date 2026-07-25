package com.proventure.twistermill.mixin.create;

import com.simibubi.create.content.redstone.link.LinkRenderer;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LinkRenderer.class)
public class LinkRendererMixin {

    @Redirect(
            method = "renderOnBlockEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D"
            ),
            remap = false
    )
    private static double twistermill$distanceToSqrSubLevelAware(Vec3 instance, Vec3 pVec) {
        return Sable.HELPER.distanceSquaredWithSubLevels(Minecraft.getInstance().level, instance, pVec);
    }
}
