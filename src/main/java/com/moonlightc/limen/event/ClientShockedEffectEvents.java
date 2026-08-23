package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.config.LimenCommonConfig;
import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class ClientShockedEffectEvents {
    private ClientShockedEffectEvents() {
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!event.getEntity().hasEffect(ModMobEffects.SHOCKED.get())) {
            return;
        }

        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.jumping = false;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (event.getPlayer().hasEffect(ModMobEffects.SHOCKED.get())) {
            event.setNewFovModifier((float) (event.getNewFovModifier() * LimenCommonConfig.SHOCKED_FOV_SCALE.get()));
        }
    }
}
