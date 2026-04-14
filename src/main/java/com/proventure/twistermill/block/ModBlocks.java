package com.proventure.twistermill.block;

import com.proventure.twistermill.block.custom.InvServoTwisterBlock;
import com.proventure.twistermill.block.custom.ServoTwisterBlock;
import com.proventure.twistermill.block.custom.WindRotoBlock;
import com.proventure.twistermill.block.custom.WindRotoVerticalBlock;
import com.proventure.twistermill.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, com.proventure.twistermill.TwisterMill.MOD_ID);

    public static final RegistryObject<Block> WIND_ROTO_BLOCK = registerBlock("wind_roto_block",
            () -> new WindRotoBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            ));

    public static final RegistryObject<Block> SERVO_TWISTER_BLOCK = registerBlock("servo_twister_block",
            () -> new ServoTwisterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            ));

    public static final RegistryObject<Block> INV_SERVO_TWISTER_BLOCK = registerBlock("inv_servo_twister_block",
            () -> new InvServoTwisterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            ));

    public static final RegistryObject<Block> WIND_ROTO_VERTICAL_BLOCK = registerBlock("wind_roto_vertical_block",
            () -> new WindRotoVerticalBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
            ));


    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}