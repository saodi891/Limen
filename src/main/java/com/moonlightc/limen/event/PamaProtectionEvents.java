package com.moonlightc.limen.event;

import com.moonlightc.limen.Limen;
import com.moonlightc.limen.entity.monster.PamaEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Limen.MODID)
public final class PamaProtectionEvents {
    private PamaProtectionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerBreak(BlockEvent.BreakEvent event) {
        if (!event.getPlayer().isCreative() && isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getIndirectSourceEntity();
        Entity exploder = event.getExplosion().getExploder();
        if ((source instanceof Player sourcePlayer && sourcePlayer.isCreative())
                || (exploder instanceof Player exploderPlayer && exploderPlayer.isCreative())) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> isProtected(event.getLevel(), pos));
    }

    @SubscribeEvent
    public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
        if (isProtected(event.getEntity().level(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPistonMove(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level)) {
            return;
        }
        if (isProtected(level, event.getPos()) || isProtected(level, event.getFaceOffsetPos())) {
            event.setCanceled(true);
            return;
        }

        var resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        boolean touchesProtectedArea = resolver.getToPush().stream().anyMatch(pos ->
                isProtected(level, pos) || isProtected(level, pos.relative(resolver.getPushDirection())));
        if (!touchesProtectedArea) {
            touchesProtectedArea = resolver.getToDestroy().stream().anyMatch(pos -> isProtected(level, pos));
        }
        if (touchesProtectedArea) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFluidSpread(BlockEvent.FluidPlaceBlockEvent event) {
        if (isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static boolean isProtected(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        return level instanceof Level world && PamaEntity.isBlockProtected(world, pos);
    }
}
