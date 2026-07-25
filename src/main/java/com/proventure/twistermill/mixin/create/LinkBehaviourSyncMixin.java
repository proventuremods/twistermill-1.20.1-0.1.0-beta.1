package com.proventure.twistermill.mixin.create;

import com.proventure.twistermill.util.SableLevelWrapper;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LinkBehaviour.class)
public abstract class LinkBehaviourSyncMixin {

    @Inject(
            method = "setFrequency",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;sendData()V"
            ),
            remap = false
    )
    private void twistermill$syncFrequencyUpdate(boolean first, ItemStack stack, CallbackInfo ci) {
        BlockEntityBehaviour behaviour = (BlockEntityBehaviour) (Object) this;
        if (behaviour.blockEntity == null) {
            return;
        }

        SmartBlockEntity blockEntity = behaviour.blockEntity;
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide || !SableLevelWrapper.isSubLevel(level)) {
            return;
        }

        blockEntity.setChanged();

        BlockPos pos = blockEntity.getBlockPos();
        BlockState state = blockEntity.getBlockState();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        level.blockUpdated(pos, state.getBlock());
    }
}
