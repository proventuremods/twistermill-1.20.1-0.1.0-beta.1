package com.proventure.twistermill.compat.framedblocks;

import com.proventure.twistermill.TwisterMill;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import xfacthd.framedblocks.api.FramedBlocksAPI;
import xfacthd.framedblocks.api.camo.CamoContainerFactory;

public final class TwisterMillFramedBlocksCompat {

    private static final DeferredRegister<CamoContainerFactory<?>> CAMO_FACTORIES =
            DeferredRegister.create(FramedBlocksAPI.INSTANCE.getCamoContainerFactoryRegistry(), TwisterMill.MOD_ID);

    private static final DeferredHolder<CamoContainerFactory<?>, TwisterMillFlatCamoFactory> FLAT_CAMO =
            CAMO_FACTORIES.register("flat_texture_block_camo", TwisterMillFlatCamoFactory::new);

    private TwisterMillFramedBlocksCompat() {
    }

    public static void register(IEventBus modEventBus) {
        CAMO_FACTORIES.register(modEventBus);
    }

    static TwisterMillFlatCamoFactory flatCamoFactory() {
        return FLAT_CAMO.get();
    }
}
