package com.proventure.twistermill.item;

import com.proventure.twistermill.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, com.proventure.twistermill.TwisterMill.MOD_ID);

    private static final ResourceLocation CREATE_BASE_TAB =
            ResourceLocation.fromNamespaceAndPath("create", "base");

    @SuppressWarnings("unused")
    public static final RegistryObject<CreativeModeTab> TUTORIAL_TAB = CREATIVE_MODE_TABS.register("tutorial_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.WIND_ROTO_BLOCK.get()))
                    .title(Component.translatable("creativetab.twistermill_tab"))
                    .withTabsBefore(CREATE_BASE_TAB)
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModBlocks.WIND_ROTO_BLOCK.get());
                        pOutput.accept(ModBlocks.WIND_ROTO_VERTICAL_BLOCK.get());
                        pOutput.accept(ModBlocks.INV_SERVO_TWISTER_BLOCK.get());
                        pOutput.accept(ModBlocks.SERVO_TWISTER_BLOCK.get());
                        pOutput.accept(ModItems.BINDING_STICK.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}