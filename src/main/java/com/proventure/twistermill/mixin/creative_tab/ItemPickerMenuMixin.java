package com.proventure.twistermill.mixin.creative_tab;

import com.proventure.twistermill.client.gui.TwisterMillCreativeTabHeader;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.ItemPickerMenu.class)
public abstract class ItemPickerMenuMixin {
    @Shadow
    protected abstract int getRowIndexForScroll(float scrollOffs);

    @Inject(method = "scrollTo", at = @At("HEAD"))
    private void twistermill$trackCreativeTabHeaderRow(float pos, CallbackInfo ci) {
        TwisterMillCreativeTabHeader.setCurrentRow(this.getRowIndexForScroll(pos));
    }
}
