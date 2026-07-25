package com.proventure.twistermill.mixin.weather2;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weather2.PacketNBTFromServer;

@Mixin(value = PacketNBTFromServer.class, remap = false)
public abstract class PacketNBTFromServerMixin {

    @ModifyVariable(
            method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false
    )
    private static CompoundTag twistermill$copyCtorTag(CompoundTag tag) {
        return tag == null ? null : tag.copy();
    }

    @Inject(
            method = "nbt()Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void twistermill$copyNbtForCodec(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag original = cir.getReturnValue();
        cir.setReturnValue(original == null ? null : original.copy());
    }
}
