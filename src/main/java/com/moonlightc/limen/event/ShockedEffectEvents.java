package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.registry.ModMobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class ShockedEffectEvents {
    private ShockedEffectEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || !player.hasEffect(ModMobEffects.SHOCKED.get())) {
            return;
        }

        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(0.0D, movement.y, 0.0D);
        player.setSprinting(false);
    }
}
