
package com.proventure.twistermill.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
public final class WindRotoReflectionHelper {

    private WindRotoReflectionHelper() {
    }

    public static BlockPos getWorldBlockPos(@SuppressWarnings("unused") Level level, BlockPos worldPosition) {
        return worldPosition;
    }
}
