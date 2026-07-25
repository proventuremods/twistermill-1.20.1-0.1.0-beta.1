package com.proventure.twistermill.compat.framedblocks.client;

import com.proventure.twistermill.compat.framedblocks.TwisterMillFlatCamoType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

public final class TwisterMillFramedBlocksClientModels {

    private TwisterMillFramedBlocksClientModels() {
    }

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (TwisterMillFlatCamoType type : TwisterMillFlatCamoType.values()) {
            event.register(ModelResourceLocation.standalone(type.modelLocation()));
        }
    }
}
