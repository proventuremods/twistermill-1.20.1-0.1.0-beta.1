package com.proventure.twistermill.block.custom;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class TwisterSailFrameBlock extends TwisterSailBlock {

    public TwisterSailFrameBlock(Properties properties) {
        super(properties, true);
    }

    @Override
    protected boolean twistermill$isWindSailPhysicsEnabledForThisBlock() {
        return false;
    }
}
