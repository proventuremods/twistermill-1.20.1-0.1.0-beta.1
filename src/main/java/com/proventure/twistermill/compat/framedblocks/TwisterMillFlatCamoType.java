package com.proventure.twistermill.compat.framedblocks;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Supplier;

public enum TwisterMillFlatCamoType implements StringRepresentable {
    NOSTALGIC_GRASS(
            "nostalgic_grass",
            ModBlocks.NOSTALGIC_GRASS_BLOCK,
            "block/framedblocks/nostalgic_grass"
    ),
    SIGNAL_QUARTZ_ORE(
            "signal_quartz_ore",
            ModBlocks.SIGNAL_QUARTZ_ORE_BLOCK,
            "block/framedblocks/signal_quartz_ore"
    ),
    TWISTER_SAIL_CANVAS(
            "twister_sail_canvas",
            ModBlocks.TWISTER_SAIL_BLOCK,
            "block/framedblocks/twister_sail_canvas"
    );

    private final String serializedName;
    private final Supplier<? extends Block> block;
    private final ResourceLocation modelLocation;

    TwisterMillFlatCamoType(String serializedName, DeferredBlock<Block> block, String modelPath) {
        this.serializedName = serializedName;
        this.block = block;
        this.modelLocation = ResourceLocation.fromNamespaceAndPath(TwisterMill.MOD_ID, modelPath);
    }

    @Override
    public @NotNull String getSerializedName() {
        return serializedName;
    }

    public ResourceLocation modelLocation() {
        return modelLocation;
    }

    public BlockState appearanceState() {
        return block.get().defaultBlockState();
    }

    public Item item() {
        return block.get().asItem();
    }

    public String camoId() {
        return TwisterMill.MOD_ID + ":" + serializedName;
    }

    public static TwisterMillFlatCamoType byName(String name) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown TwisterMill FramedBlocks camo type: " + name));
    }

    @Nullable
    public static TwisterMillFlatCamoType byItem(Item item) {
        return Arrays.stream(values())
                .filter(type -> type.item() == item)
                .findFirst()
                .orElse(null);
    }
}
