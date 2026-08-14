package com.proventure.twistermill.worldgen;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

public class TwisterMillWorldGenSavedData extends SavedData {

    private static final String DATA_NAME = "twistermill_worldgen";
    private static final String TAG_GENERATE_ORES_ENABLED = "GenerateOresEnabled";
    private static final SavedData.Factory<TwisterMillWorldGenSavedData> FACTORY =
            new SavedData.Factory<>(TwisterMillWorldGenSavedData::new, TwisterMillWorldGenSavedData::load);

    private boolean generateOresEnabled;

    public TwisterMillWorldGenSavedData() {
        this(false);
    }

    private TwisterMillWorldGenSavedData(boolean generateOresEnabled) {
        this.generateOresEnabled = generateOresEnabled;
    }

    public static TwisterMillWorldGenSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static TwisterMillWorldGenSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        boolean enabled = tag.getBoolean(TAG_GENERATE_ORES_ENABLED);
        return new TwisterMillWorldGenSavedData(enabled);
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registries) {
        tag.putBoolean(TAG_GENERATE_ORES_ENABLED, this.generateOresEnabled);
        return tag;
    }

    public boolean isGenerateOresEnabled() {
        return this.generateOresEnabled;
    }

    public void enableOreGeneration() {
        if (this.generateOresEnabled) {
            return;
        }

        this.generateOresEnabled = true;
        setDirty();
    }
}
