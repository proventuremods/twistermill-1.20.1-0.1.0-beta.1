package com.proventure.twistermill.item;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(com.proventure.twistermill.TwisterMill.MOD_ID);

    public static final DeferredItem<Item> BINDING_STICK =
            ITEMS.register("binding_stick", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> SIGNAL_QUARTZ =
            ITEMS.register("signal_quartz", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> POLISHED_SIGNAL_QUARTZ =
            ITEMS.register("polished_signal_quartz", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> SIGNAL_QUARTZ_DUST =
            ITEMS.register("signal_quartz_dust", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> SIGNAL_STEEL_INGOT =
            ITEMS.register("signal_steel_ingot", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> SIGNAL_STEEL_SHEET =
            ITEMS.register("signal_steel_sheet", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> SIGNAL_STEEL_ROD =
            ITEMS.register("signal_steel_rod", () -> new Item(new Item.Properties()));


    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
