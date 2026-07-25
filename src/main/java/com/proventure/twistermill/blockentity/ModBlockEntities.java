package com.proventure.twistermill.blockentity;

import com.proventure.twistermill.TwisterMill;
import com.proventure.twistermill.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TwisterMill.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    private static <T extends BlockEntity> BlockEntityType<T> buildWithoutDataFixer(BlockEntityType.Builder<T> builder) {
        return builder.build(null);
    }

    public static final Supplier<BlockEntityType<WindRotoBlockEntity>> WIND_ROTO_BE =
            BLOCK_ENTITIES.register("wind_roto_block_entity",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            WindRotoBlockEntity::new,
                            ModBlocks.WIND_ROTO_BLOCK.get()
                    )));

    public static final Supplier<BlockEntityType<ServoTwisterBlockEntity>> SERVO_TWISTER_BE =
            BLOCK_ENTITIES.register("servo_twister_block_entity",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ServoTwisterBlockEntity::new,
                            ModBlocks.SERVO_TWISTER_BLOCK.get()
                    )));

    public static final Supplier<BlockEntityType<InvServoTwisterBlockEntity>> INV_SERVO_TWISTER_BE =
            BLOCK_ENTITIES.register("inv_servo_twister_block_entity",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            InvServoTwisterBlockEntity::new,
                            ModBlocks.INV_SERVO_TWISTER_BLOCK.get()
                    )));

    public static final Supplier<BlockEntityType<WindRotoVerticalBlockEntity>> WIND_ROTO_VERTICAL_BE =
            BLOCK_ENTITIES.register("wind_roto_vertical_block_entity",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            WindRotoVerticalBlockEntity::new,
                            ModBlocks.WIND_ROTO_VERTICAL_BLOCK.get()
                    )));

    public static final Supplier<BlockEntityType<ControlTableBlockEntity>> CONTROL_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("control_table_block_entity",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            ControlTableBlockEntity::new,
                            ModBlocks.CONTROL_TABLE_BLOCK.get()
                    )));

    public static final Supplier<BlockEntityType<RedstoneInBitOutBlockEntity>> REDSTONE_IN_BIT_OUT_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("redstone_in_bit_out_block_entity",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            RedstoneInBitOutBlockEntity::new,
                            ModBlocks.REDSTONE_IN_BIT_OUT_BLOCK.get()
                    )));

    public static final Supplier<BlockEntityType<DigitalSignalTxBlockEntity>> DIGITAL_SIGNAL_TX_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("digital_signal_tx_block_entity",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            DigitalSignalTxBlockEntity::new,
                            ModBlocks.DIGITAL_SIGNAL_TX_BLOCK.get()
                    )));

    public static final Supplier<BlockEntityType<WrenchSideCycleBlockEntity>> METAL_SIDE_CYCLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("metal_side_cycle_block_entity",
                    () -> buildWithoutDataFixer(BlockEntityType.Builder.of(
                            WrenchSideCycleBlockEntity::new,
                            ModBlocks.METAL_TRAVERSE.get()
                    )));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
