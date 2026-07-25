package com.proventure.twistermill.mixin.weather2;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import weather2.WeatherNetworkingv2;

@Mixin(value = WeatherNetworkingv2.class, remap = false)
public abstract class WeatherNetworkingv2Mixin {

    @ModifyArg(
            method = {
                    "serverSendToClientAll(Lnet/minecraft/nbt/CompoundTag;)V",
                    "serverSendToClientPlayer(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/entity/player/Player;)V",
                    "serverSendToClientNear(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/phys/Vec3;DLnet/minecraft/world/level/Level;)V",
                    "serverSendToClientsInDimension(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/world/level/Level;)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lweather2/PacketNBTFromServer;<init>(Lnet/minecraft/nbt/CompoundTag;)V"
            ),
            index = 0,
            remap = false
    )
    private CompoundTag twistermill$copyTagForPacket(CompoundTag tag) {
        return tag == null ? null : tag.copy();
    }
}
