package com.moonlightc.limen.client;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.hotbar.ArtisanHotbars;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keybinds for the artisan hotbar slots. Unbound by default, as requested: the player assigns them in
 * Options → Controls, under the Limen category. Scrolling reaches the slots without any keybind.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArtisanHotbarKeys {
    public static final String CATEGORY = "key.categories.limen";

    /** Direct jump to a specific artisan slot, indexed 0-2. */
    public static final KeyMapping[] SELECT_SLOT = new KeyMapping[ArtisanHotbars.SLOT_COUNT];

    static {
        for (int i = 0; i < SELECT_SLOT.length; i++) {
            SELECT_SLOT[i] = new KeyMapping(
                    "key.limen.artisan_hotbar_" + (i + 1), KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
        }
    }

    private ArtisanHotbarKeys() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : SELECT_SLOT) {
            event.register(mapping);
        }
    }
}
