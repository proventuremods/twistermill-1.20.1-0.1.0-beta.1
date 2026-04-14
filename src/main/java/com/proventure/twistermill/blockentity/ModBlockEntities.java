package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("DataFlowIssue")
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(
                    ForgeRegistries.BLOCK_ENTITY_TYPES,
                    com.proventure.twistermill.TwisterMill.MOD_ID
            );

    public static final RegistryObject<BlockEntityType<WindRotoBlockEntity>> WIND_ROTO_BE =
            BLOCK_ENTITIES.register("wind_roto_block_entity",
                    () -> BlockEntityType.Builder.of(
                            WindRotoBlockEntity::new,
                            ModBlocks.WIND_ROTO_BLOCK.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<ServoTwisterBlockEntity>> SERVO_TWISTER_BE =
            BLOCK_ENTITIES.register("servo_twister_block_entity",
                    () -> BlockEntityType.Builder.of(
                            ServoTwisterBlockEntity::new,
                            ModBlocks.SERVO_TWISTER_BLOCK.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<InvServoTwisterBlockEntity>> INV_SERVO_TWISTER_BE =
            BLOCK_ENTITIES.register("inv_servo_twister_block_entity",
                    () -> BlockEntityType.Builder.of(
                            InvServoTwisterBlockEntity::new,
                            ModBlocks.INV_SERVO_TWISTER_BLOCK.get()
                    ).build(null));

    public static final RegistryObject<BlockEntityType<WindRotoVerticalBlockEntity>> WIND_ROTO_VERTICAL_BE =
            BLOCK_ENTITIES.register("wind_roto_vertical_block_entity",
                    () -> BlockEntityType.Builder.of(
                            WindRotoVerticalBlockEntity::new,
                            ModBlocks.WIND_ROTO_VERTICAL_BLOCK.get()
                    ).build(null));


    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}