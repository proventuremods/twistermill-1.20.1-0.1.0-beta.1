package com.proventure.twistermill.compat.framedblocks.client;

import com.proventure.twistermill.compat.framedblocks.TwisterMillFlatCamoContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
import xfacthd.framedblocks.api.camo.CamoClientHandler;
import xfacthd.framedblocks.api.model.util.ModelUtils;

public final class TwisterMillFlatCamoClientHandler extends CamoClientHandler<TwisterMillFlatCamoContent> {

    public static final CamoClientHandler<TwisterMillFlatCamoContent> INSTANCE = new TwisterMillFlatCamoClientHandler();

    private TwisterMillFlatCamoClientHandler() {
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(TwisterMillFlatCamoContent content, RandomSource random, ModelData modelData) {
        return ModelUtils.SOLID;
    }

    @Override
    public BakedModel getOrCreateModel(TwisterMillFlatCamoContent content) {
        return Minecraft.getInstance()
                .getModelManager()
                .getModel(ModelResourceLocation.standalone(content.type().modelLocation()));
    }

    @Override
    public Particle makeHitDestroyParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            TwisterMillFlatCamoContent content,
            BlockPos pos
    ) {
        return new TerrainParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, content.getAppearanceState(), pos);
    }
}
