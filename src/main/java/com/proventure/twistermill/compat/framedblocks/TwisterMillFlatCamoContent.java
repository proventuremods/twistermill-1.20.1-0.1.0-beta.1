package com.proventure.twistermill.compat.framedblocks;

import com.proventure.twistermill.compat.framedblocks.client.TwisterMillFlatCamoClientHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.util.TriState;
import xfacthd.framedblocks.api.camo.CamoClientHandler;
import xfacthd.framedblocks.api.camo.CamoContent;
import xfacthd.framedblocks.api.camo.block.BlockCamoContent;

public final class TwisterMillFlatCamoContent extends CamoContent<TwisterMillFlatCamoContent> {

    private final TwisterMillFlatCamoType type;
    private final BlockCamoContent delegate;

    public TwisterMillFlatCamoContent(TwisterMillFlatCamoType type) {
        this.type = type;
        this.delegate = new BlockCamoContent(type.appearanceState());
    }

    public TwisterMillFlatCamoType type() {
        return type;
    }

    @Override
    public boolean propagatesSkylightDown(BlockGetter level, BlockPos pos) {
        return delegate.propagatesSkylightDown(level, pos);
    }

    @Override
    public float getExplosionResistance(BlockGetter level, BlockPos pos, Explosion explosion) {
        return delegate.getExplosionResistance(level, pos, explosion);
    }

    @Override
    public boolean isFlammable(BlockGetter level, BlockPos pos, Direction direction) {
        return delegate.isFlammable(level, pos, direction);
    }

    @Override
    public int getFlammability(BlockGetter level, BlockPos pos, Direction direction) {
        return delegate.getFlammability(level, pos, direction);
    }

    @Override
    public int getFireSpreadSpeed(BlockGetter level, BlockPos pos, Direction direction) {
        return delegate.getFireSpreadSpeed(level, pos, direction);
    }

    @Override
    public float getShadeBrightness(BlockGetter level, BlockPos pos, float shade) {
        return delegate.getShadeBrightness(level, pos, shade);
    }

    @Override
    public int getLightEmission() {
        return delegate.getLightEmission();
    }

    @Override
    public boolean isEmissive() {
        return delegate.isEmissive();
    }

    @Override
    public SoundType getSoundType() {
        return delegate.getSoundType();
    }

    @Override
    public boolean shouldDisplayFluidOverlay(BlockAndTintGetter level, BlockPos pos, FluidState fluidState) {
        return delegate.shouldDisplayFluidOverlay(level, pos, fluidState);
    }

    @Override
    public float getFriction(LevelReader level, BlockPos pos, Entity entity, float fallback) {
        return delegate.getFriction(level, pos, entity, fallback);
    }

    @Override
    public TriState canSustainPlant(BlockGetter level, BlockPos pos, Direction direction, BlockState plant) {
        return delegate.canSustainPlant(level, pos, direction, plant);
    }

    @Override
    public boolean canEntityDestroy(BlockGetter level, BlockPos pos, Entity entity) {
        return delegate.canEntityDestroy(level, pos, entity);
    }

    @Override
    public MapColor getMapColor(BlockGetter level, BlockPos pos) {
        return delegate.getMapColor(level, pos);
    }

    @Override
    public int getTintColor(BlockAndTintGetter level, BlockPos pos, int tintIndex) {
        return delegate.getTintColor(level, pos, tintIndex);
    }

    @Override
    public Integer getBeaconColorMultiplier(LevelReader level, BlockPos pos, BlockPos beaconPos) {
        return delegate.getBeaconColorMultiplier(level, pos, beaconPos);
    }

    @Override
    public boolean isSolid(BlockGetter level, BlockPos pos) {
        return delegate.isSolid(level, pos);
    }

    @Override
    public boolean canOcclude() {
        return delegate.canOcclude();
    }

    @Override
    public BlockState getAsBlockState() {
        return delegate.getAsBlockState();
    }

    @Override
    public BlockState getAppearanceState() {
        return delegate.getAppearanceState();
    }

    @Override
    public boolean isOccludedBy(BlockState state, BlockGetter level, BlockPos pos, BlockPos adjPos) {
        return delegate.isOccludedBy(state, level, pos, adjPos);
    }

    @Override
    public boolean isOccludedBy(CamoContent<?> camo, BlockGetter level, BlockPos pos, BlockPos adjPos) {
        return delegate.isOccludedBy(camo, level, pos, adjPos);
    }

    @Override
    public boolean occludes(BlockState state, BlockGetter level, BlockPos pos, BlockPos adjPos) {
        return delegate.occludes(state, level, pos, adjPos);
    }

    @Override
    public ParticleOptions makeRunningLandingParticles(BlockPos pos) {
        return delegate.makeRunningLandingParticles(pos);
    }

    @Override
    public String getCamoId() {
        return type.camoId();
    }

    @Override
    public MutableComponent getCamoName() {
        return delegate.getCamoName();
    }

    @Override
    public CamoClientHandler<TwisterMillFlatCamoContent> getClientHandler() {
        return TwisterMillFlatCamoClientHandler.INSTANCE;
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TwisterMillFlatCamoContent other && other.type == type;
    }

    @Override
    public String toString() {
        return "TwisterMillFlatCamoContent[type=" + type.getSerializedName() + "]";
    }
}
