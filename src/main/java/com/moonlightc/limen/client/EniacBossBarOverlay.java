package com.moonlightc.limen.client;

import com.moonlightc.limen.Limen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Replaces the vanilla boss-bar rendering for ENIAC with a custom texture. Other bosses (ender dragon,
 * wither, other mods) keep their vanilla bars; only the bar whose name is ENIAC's entity translation key
 * is taken over here.
 *
 * <p>Texture layout in {@code eniac_bossbar.png} (256x64): empty bar at v=0, full bar at v=24, each 182x20.
 */
@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT)
public final class EniacBossBarOverlay {
    private static final ResourceLocation TEXTURE = Limen.id("textures/gui/eniac_bossbar.png");
    private static final int TEX_W = 256, TEX_H = 64;
    private static final int BAR_W = 182, BAR_H = 20;
    private static final int BG_V = 0;
    private static final int FG_V = 24;

    private EniacBossBarOverlay() {
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        BossEvent boss = event.getBossEvent();
        if (!isEniac(boss)) {
            return;
        }
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = screenW / 2 - BAR_W / 2;
        int y = event.getY();

        graphics.blit(TEXTURE, x, y, 0.0F, (float) BG_V, BAR_W, BAR_H, TEX_W, TEX_H);
        int fill = Mth.ceil(boss.getProgress() * BAR_W);
        if (fill > 0) {
            graphics.blit(TEXTURE, x, y, 0.0F, (float) FG_V, fill, BAR_H, TEX_W, TEX_H);
        }

        Component name = boss.getName();
        int nameW = mc.font.width(name);
        graphics.drawString(mc.font, name, screenW / 2 - nameW / 2, y - 9, 0xFFFFFF);

        event.setIncrement(BAR_H + mc.font.lineHeight + 4);
    }

    private static boolean isEniac(BossEvent boss) {
        return boss.getName().getContents() instanceof TranslatableContents contents
                && "entity.limen.eniac".equals(contents.getKey());
    }
}
