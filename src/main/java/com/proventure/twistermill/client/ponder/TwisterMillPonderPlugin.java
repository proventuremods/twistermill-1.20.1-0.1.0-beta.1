package com.proventure.twistermill.client.ponder;

import com.proventure.twistermill.TwisterMill;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class TwisterMillPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return TwisterMill.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        TwisterMillPonderScenes.register(helper);
    }
}