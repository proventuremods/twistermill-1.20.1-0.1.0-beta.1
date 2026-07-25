package com.proventure.twistermill.block;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.custom.BladeArmBlock;
import com.proventure.twistermill.block.custom.ControlTableBlock;
import com.proventure.twistermill.block.custom.DigitalSignalTxBlock;
import com.proventure.twistermill.block.custom.InvServoTwisterBlock;
import com.proventure.twistermill.block.custom.MetalTraverseBlock;
import com.proventure.twistermill.block.custom.NostalgicGrassBlock;
import com.proventure.twistermill.block.custom.RedstoneInBitOutBlock;
import com.proventure.twistermill.block.custom.ServoTwisterBlock;
import com.proventure.twistermill.block.custom.TwisterSailBlock;
import com.proventure.twistermill.block.custom.TwisterSailFrameBlock;
import com.proventure.twistermill.block.custom.WindRotoBlock;
import com.proventure.twistermill.block.custom.WindRotoVerticalBlock;
import com.proventure.twistermill.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;
import java.util.function.Supplier;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(TwisterMill.MOD_ID);

    public static final DeferredBlock<Block> WIND_ROTO_BLOCK = registerBlock("wind_roto_block",
            () -> new WindRotoBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> SERVO_TWISTER_BLOCK = registerBlock("servo_twister_block",
            () -> new ServoTwisterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> INV_SERVO_TWISTER_BLOCK = registerBlock("inv_servo_twister_block",
            () -> new InvServoTwisterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> WIND_ROTO_VERTICAL_BLOCK = registerBlock("wind_roto_vertical_block",
            () -> new WindRotoVerticalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> CONTROL_TABLE_BLOCK = registerBlock("control_table_block",
            () -> new ControlTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)));

    public static final DeferredBlock<Block> REDSTONE_IN_BIT_OUT_BLOCK = registerBlock("redstone_in_bit_out_block",
            () -> new RedstoneInBitOutBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> DIGITAL_SIGNAL_TX_BLOCK = registerBlock("digital_signal_tx_block",
            () -> new DigitalSignalTxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(10.0F, 10.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));


    public static final DeferredBlock<Block> TWISTER_SAIL_FRAME_BLOCK = registerBlock("twister_sail_frame_block",
            () -> new TwisterSailFrameBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .strength(1.0F, 3.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.WOOD)));

    public static final DeferredBlock<Block> TWISTER_SAIL_BLOCK = registerBlock("twister_sail_block",
            () -> new TwisterSailBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL)
                    .strength(1.2F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.WOOL), false));

    public static final DeferredBlock<Block> NOSTALGIC_GRASS_BLOCK = registerBlock("nostalgic_grass_block",
            () -> new NostalgicGrassBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GRASS_BLOCK)
                    .strength(0.5F, 0.5F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.ROOTED_DIRT)));

    public static final DeferredBlock<Block> SIGNAL_QUARTZ_ORE_BLOCK = registerBlock("signal_quartz_ore_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK)
                    .strength(6.0F, 5.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)));

    public static final DeferredBlock<Block> SIGNAL_STEEL_BLOCK = registerBlock("signal_steel_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> METAL_TRAVERSE = registerBlock("metal_traverse",
            () -> new MetalTraverseBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> BLADE_ARM_BLOCK = registerBlock("blade_arm_block",
            () -> new BladeArmBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> BLADE_ARM_EASTFACE_BLOCK = registerBlock("blade_arm_eastface_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> BLADE_ARM_WESTFACE_BLOCK = registerBlock("blade_arm_westface_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .strength(3.5F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)));

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        return registerBlock(name, block, registeredBlock -> new BlockItem(registeredBlock.get(), new Item.Properties()));
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block,
                                                                    Function<DeferredBlock<T>, BlockItem> itemFactory) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn, itemFactory);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block,
                                                            Function<DeferredBlock<T>, BlockItem> itemFactory) {
        ModItems.ITEMS.register(name, () -> itemFactory.apply(block));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
