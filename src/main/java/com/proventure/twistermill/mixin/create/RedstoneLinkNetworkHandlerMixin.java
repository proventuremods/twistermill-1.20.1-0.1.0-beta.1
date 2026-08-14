package com.proventure.twistermill.mixin.create;

import com.proventure.twistermill.util.SableLevelWrapper;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RedstoneLinkNetworkHandler.class)
public abstract class RedstoneLinkNetworkHandlerMixin {

    @ModifyVariable(
            method = {
                    "onLoadWorld",
                    "onUnloadWorld",
                    "getNetworkOf",
                    "addToNetwork",
                    "removeFromNetwork",
                    "updateNetworkOf",
                    "networksIn"
            },
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false
    )
    private LevelAccessor twistermill$useRootLevelForNetwork(LevelAccessor world) {
        return twistermill$normalizeWorld(world);
    }

    @Redirect(
            method = "updateNetworkOf",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/redstone/link/RedstoneLinkNetworkHandler;withinRange(Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;Lcom/simibubi/create/content/redstone/link/IRedstoneLinkable;)Z"
            ),
            remap = false
    )
    private boolean twistermill$checkSubLevelAwareRange(
            IRedstoneLinkable from,
            IRedstoneLinkable to,
            LevelAccessor world,
            IRedstoneLinkable ignoredActor
    ) {
        if (from == to) {
            return true;
        }

        if (!(world instanceof Level level)) {
            return RedstoneLinkNetworkHandler.withinRange(from, to);
        }

        Vector3d fromWorld = SableLevelWrapper.toWorldCenter(level, from.getLocation());
        Vector3d toWorld = SableLevelWrapper.toWorldCenter(level, to.getLocation());
        int linkRange = AllConfigs.server().logistics.linkRange.get();
        return fromWorld.distanceSquared(toWorld) < (double) linkRange * (double) linkRange;
    }

    @Unique
    private static LevelAccessor twistermill$normalizeWorld(LevelAccessor world) {
        if (world instanceof Level level) {
            return SableLevelWrapper.getRootLevel(level);
        }
        return world;
    }
}
