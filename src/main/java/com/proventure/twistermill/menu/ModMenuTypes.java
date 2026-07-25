package com.proventure.twistermill.menu;

import com.proventure.twistermill.TwisterMill;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TwisterMill.MOD_ID);

    public static final Supplier<MenuType<ControlTableMenu>> CONTROL_TABLE_MENU =
            MENUS.register("control_table_menu", () -> IMenuTypeExtension.create(ControlTableMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
