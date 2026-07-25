package com.proventure.twistermill.compat.framedblocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import xfacthd.framedblocks.api.camo.CamoContainerFactory;
import xfacthd.framedblocks.api.camo.TriggerRegistrar;
import xfacthd.framedblocks.api.util.ConfigView;
import xfacthd.framedblocks.api.util.Utils;

public final class TwisterMillFlatCamoFactory extends CamoContainerFactory<TwisterMillFlatCamoContainer> {

    private static final String TAG_FLAT_TYPE = "flat_type";
    private static final Codec<TwisterMillFlatCamoType> TYPE_CODEC = Codec.STRING.xmap(
            TwisterMillFlatCamoType::byName,
            TwisterMillFlatCamoType::getSerializedName
    );
    private static final MapCodec<TwisterMillFlatCamoContainer> CODEC = TYPE_CODEC
            .fieldOf(TAG_FLAT_TYPE)
            .xmap(TwisterMillFlatCamoContainer::new, TwisterMillFlatCamoContainer::type);
    private static final StreamCodec<? super RegistryFriendlyByteBuf, TwisterMillFlatCamoContainer> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(
                    name -> new TwisterMillFlatCamoContainer(TwisterMillFlatCamoType.byName(name)),
                    container -> container.type().getSerializedName()
            );

    @Override
    public TwisterMillFlatCamoContainer applyCamo(Level level, BlockPos pos, Player player, ItemStack stack) {
        TwisterMillFlatCamoType type = TwisterMillFlatCamoType.byItem(stack.getItem());
        if (type == null) {
            return null;
        }
        if (!level.isClientSide() && !player.isCreative() && ConfigView.Server.INSTANCE.shouldConsumeCamoItem()) {
            stack.shrink(1);
            player.getInventory().setChanged();
        }
        return new TwisterMillFlatCamoContainer(type);
    }

    @Override
    public boolean removeCamo(Level level, BlockPos pos, Player player, ItemStack stack, TwisterMillFlatCamoContainer camo) {
        if (!level.isClientSide()) {
            Utils.giveToPlayer(player, dropCamo(camo), ConfigView.Server.INSTANCE.shouldConsumeCamoItem());
        }
        return true;
    }

    @Override
    public boolean canTriviallyConvertToItemStack() {
        return true;
    }

    @Override
    public ItemStack dropCamo(TwisterMillFlatCamoContainer camo) {
        return new ItemStack(camo.type().item());
    }

    @Override
    public boolean validateCamo(TwisterMillFlatCamoContainer camo) {
        return camo != null && TwisterMillFlatCamoType.byItem(camo.type().item()) == camo.type();
    }

    @Override
    protected void writeToNetwork(CompoundTag tag, TwisterMillFlatCamoContainer camo) {
        tag.putString(TAG_FLAT_TYPE, camo.type().getSerializedName());
    }

    @Override
    protected TwisterMillFlatCamoContainer readFromNetwork(CompoundTag tag) {
        return new TwisterMillFlatCamoContainer(TwisterMillFlatCamoType.byName(tag.getString(TAG_FLAT_TYPE)));
    }

    @Override
    public boolean canApplyInCraftingRecipe(ItemStack stack) {
        return TwisterMillFlatCamoType.byItem(stack.getItem()) != null;
    }

    @Override
    public TwisterMillFlatCamoContainer applyCamoInCraftingRecipe(ItemStack stack) {
        TwisterMillFlatCamoType type = TwisterMillFlatCamoType.byItem(stack.getItem());
        if (type == null) {
            throw new IllegalStateException("applyCamoInCraftingRecipe() called without canApplyInCraftingRecipe() check");
        }
        return new TwisterMillFlatCamoContainer(type);
    }

    @Override
    public ItemStack getCraftingRemainder(ItemStack stack) {
        if (!ConfigView.Server.INSTANCE.shouldConsumeCamoItem()) {
            return stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public MapCodec<TwisterMillFlatCamoContainer> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, TwisterMillFlatCamoContainer> streamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public void registerTriggerItems(TriggerRegistrar registrar) {
        for (TwisterMillFlatCamoType type : TwisterMillFlatCamoType.values()) {
            registrar.registerApplicationItem(type.item());
        }
        registrar.registerRemovalPredicate(TriggerRegistrar.DEFAULT_REMOVAL);
    }
}
