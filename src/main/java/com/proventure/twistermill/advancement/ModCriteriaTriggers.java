package com.proventure.twistermill.advancement;

import com.proventure.twistermill.TwisterMill;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class ModCriteriaTriggers {

    public static final TwisterMillSimpleTrigger CTB_SYSTEM_COMPLETE =
            new TwisterMillSimpleTrigger();
    public static final TwisterMillSimpleTrigger BINARY_CODE_TRANSMITTER =
            new TwisterMillSimpleTrigger();

    private ModCriteriaTriggers() {
    }

    public static void register(RegisterEvent event) {
        event.register(Registries.TRIGGER_TYPE, helper -> {
            helper.register(
                    ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "ctb_system_complete"),
                    CTB_SYSTEM_COMPLETE
            );
            helper.register(
                    ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, "binary_code_transmitter"),
                    BINARY_CODE_TRANSMITTER
            );
        });
    }
}
