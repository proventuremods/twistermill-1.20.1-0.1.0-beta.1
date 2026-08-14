package com.proventure.twistermill.worldgen;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.worldgen.feature.SignalQuartzOreFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgenFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, TwisterMill.MOD_ID);

    @SuppressWarnings("unused")
    public static final DeferredHolder<Feature<?>, SignalQuartzOreFeature> SIGNAL_QUARTZ_ORE =
            FEATURES.register("signal_quartz_ore", () -> new SignalQuartzOreFeature(OreConfiguration.CODEC));

    private ModWorldgenFeatures() {
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}
