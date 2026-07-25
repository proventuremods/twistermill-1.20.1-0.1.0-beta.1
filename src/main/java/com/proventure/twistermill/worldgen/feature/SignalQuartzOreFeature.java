package com.proventure.twistermill.worldgen.feature;

import com.mojang.serialization.Codec;
import com.proventure.twistermill.worldgen.TwisterMillWorldGenSavedData;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class SignalQuartzOreFeature extends Feature<OreConfiguration> {

    public SignalQuartzOreFeature(Codec<OreConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        var serverLevel = context.level().getLevel();
        if (serverLevel.dimension() != Level.OVERWORLD) {
            return false;
        }

        TwisterMillWorldGenSavedData worldGenState = TwisterMillWorldGenSavedData.get(serverLevel.getServer());
        if (!worldGenState.isGenerateOresEnabled()) {
            return false;
        }

        OreConfiguration baseConfig = context.config();
        int sampledClusterSize = Mth.nextInt(context.random(), 2, 9);
        OreConfiguration dynamicConfig = new OreConfiguration(
                baseConfig.targetStates,
                sampledClusterSize,
                baseConfig.discardChanceOnAirExposure
        );

        return Feature.ORE.place(dynamicConfig, context.level(), context.chunkGenerator(), context.random(), context.origin());
    }
}
