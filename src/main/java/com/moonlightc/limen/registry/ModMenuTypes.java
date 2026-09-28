package com.moonlightc.limen.registry;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.inventory.ArtisanBoxMenu;
import com.moonlightc.limen.inventory.CampfireMenu;
import com.moonlightc.limen.inventory.TableSawMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    private static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, Limen.MODID);

    public static final RegistryObject<MenuType<ArtisanBoxMenu>> ARTISAN_BOX = MENU_TYPES.register(
            "artisan_box",
            () -> IForgeMenuType.create((containerId, inventory, data) -> new ArtisanBoxMenu(containerId, inventory))
    );

    public static final RegistryObject<MenuType<CampfireMenu>> CAMPFIRE = MENU_TYPES.register(
            "campfire",
            () -> IForgeMenuType.create(CampfireMenu::new)
    );

    public static final RegistryObject<MenuType<TableSawMenu>> TABLE_SAW = MENU_TYPES.register(
            "table_saw",
            () -> IForgeMenuType.create(TableSawMenu::new)
    );

    private ModMenuTypes() {
    }

    public static void register(IEventBus modBus) {
        MENU_TYPES.register(modBus);
    }
}
