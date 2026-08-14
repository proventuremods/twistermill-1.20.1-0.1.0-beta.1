package com.proventure.twistermill.item;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.ModBlocks;
import com.proventure.twistermill.config.TwisterMillConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeModTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TwisterMill.MOD_ID);

    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> Twistermill =
            CREATIVE_MODE_TABS.register("create_twistermill_tab",
                    () -> CreativeModeTab.builder()
                            .icon(() -> new ItemStack(ModBlocks.WIND_ROTO_BLOCK.get()))
                            .title(Component.translatable("creativetab.twistermill_tab"))
                            .displayItems((parameters, output) -> {
                                output.accept(ModBlocks.WIND_ROTO_BLOCK.get());
                                output.accept(ModBlocks.SERVO_TWISTER_BLOCK.get());
                                output.accept(ModBlocks.INV_SERVO_TWISTER_BLOCK.get());
                                output.accept(ModBlocks.WIND_ROTO_VERTICAL_BLOCK.get());
                                output.accept(ModBlocks.CONTROL_TABLE_BLOCK.get());
                                output.accept(ModBlocks.DIGITAL_SIGNAL_TX_BLOCK.get());
                                output.accept(ModBlocks.REDSTONE_IN_BIT_OUT_BLOCK.get());
                                output.accept(ModBlocks.TWISTER_SAIL_FRAME_BLOCK.get());
                                output.accept(ModBlocks.TWISTER_SAIL_BLOCK.get());
                                if (TwisterMillConfig.isNostalgicGrassBlockShown()) {
                                    output.accept(ModBlocks.NOSTALGIC_GRASS_BLOCK.get());
                                }
                                output.accept(ModBlocks.SIGNAL_QUARTZ_ORE_BLOCK.get());
                                if (TwisterMillConfig.isMetalTraverseShown()) {
                                    output.accept(ModBlocks.METAL_TRAVERSE.get());
                                }
                                if (TwisterMillConfig.isBladeArmBlockShown()) {
                                    output.accept(ModBlocks.BLADE_ARM_BLOCK.get());
                                }
                                if (TwisterMillConfig.isBladeArmEastfaceBlockShown()) {
                                    output.accept(ModBlocks.BLADE_ARM_EASTFACE_BLOCK.get());
                                }
                                if (TwisterMillConfig.isBladeArmWestfaceBlockShown()) {
                                    output.accept(ModBlocks.BLADE_ARM_WESTFACE_BLOCK.get());
                                }

                                output.accept(ModItems.BINDING_STICK.get());
                                output.accept(ModItems.SIGNAL_QUARTZ.get());
                                output.accept(ModItems.POLISHED_SIGNAL_QUARTZ.get());
                                output.accept(ModItems.SIGNAL_QUARTZ_DUST.get());
                                output.accept(ModBlocks.SIGNAL_STEEL_BLOCK.get());
                                output.accept(ModItems.SIGNAL_STEEL_INGOT.get());
                                output.accept(ModItems.SIGNAL_STEEL_SHEET.get());
                                output.accept(ModItems.SIGNAL_STEEL_ROD.get());

                            })
                            .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }

}
