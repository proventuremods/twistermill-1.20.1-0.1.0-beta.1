package com.proventure.twistermill.mixin.creative_tab;

import com.proventure.twistermill.client.gui.TwisterMillCreativeTabHeader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    @Shadow
    private static CreativeModeTab selectedTab;

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void twistermill$resetCreativeTabHeaderRow(CreativeModeTab tab, CallbackInfo ci) {
        TwisterMillCreativeTabHeader.resetCurrentRow();
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;renderTooltip(Lnet/minecraft/client/gui/GuiGraphics;II)V"
            )
    )
    private void twistermill$renderCreativeTabHeader(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                                     float partialTick, CallbackInfo ci) {
        TwisterMillCreativeTabHeader.render((CreativeModeInventoryScreen) (Object) this, selectedTab, guiGraphics);
    }
}
