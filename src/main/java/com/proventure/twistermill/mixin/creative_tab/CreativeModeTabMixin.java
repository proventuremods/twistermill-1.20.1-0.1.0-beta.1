package com.proventure.twistermill.mixin.creative_tab;

import com.proventure.twistermill.client.gui.TwisterMillCreativeTabHeader;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void twistermill$prependCreativeTabHeaderSlots(CreativeModeTab.ItemDisplayParameters parameters,
                                                          CallbackInfo ci) {
        this.displayItems = TwisterMillCreativeTabHeader.withHeaderSlots((CreativeModeTab) (Object) this,
                this.displayItems);
    }
}
