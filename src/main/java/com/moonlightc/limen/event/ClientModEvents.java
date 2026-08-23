package com.moonlightc.limen.event;

import com.moonlightc.limen.entity.client.AutonomousSpiderRenderer;
import com.moonlightc.limen.entity.client.ChargedCoilArrowRenderer;
import com.moonlightc.limen.entity.client.CoilFieldRenderer;
import com.moonlightc.limen.entity.client.PamaDetectorRenderer;
import com.moonlightc.limen.entity.client.SkeletonChippedRenderer;
import com.moonlightc.limen.entity.client.ZombieChippedRenderer;
import com.moonlightc.limen.item.LivingShieldItem;
import com.moonlightc.limen.registry.ModEntities;
import com.moonlightc.limen.registry.ModItems;
import com.moonlightc.limen.registry.ModBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = com.moonlightc.limen.Limen.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CHARGED_COIL_ARROW.get(), ChargedCoilArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.COIL_FIELD.get(), CoilFieldRenderer::new);
        event.registerEntityRenderer(ModEntities.PAMA_DETECTOR.get(), PamaDetectorRenderer::new);
        event.registerEntityRenderer(ModEntities.AUTONOMOUS_SPIDER.get(), AutonomousSpiderRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETON_CHIPPED.get(), SkeletonChippedRenderer::new);
        event.registerEntityRenderer(ModEntities.ZOMBIE_CHIPPED.get(), ZombieChippedRenderer::new);
    }

    @SubscribeEvent
    @SuppressWarnings("removal")
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.STRUCTURE_VOID_BLOCK.get(), RenderType.cutout());
            ItemProperties.register(ModItems.ELECTROMAGNETIC_CROSSBOW.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "pull"),
                    (stack, level, entity, seed) -> getPull(stack, entity));
            ItemProperties.register(ModItems.ELECTROMAGNETIC_CROSSBOW.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.ELECTROMAGNETIC_CROSSBOW.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "charged"),
                    (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.LIVING_SHIELD.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "blocking"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.LIVING_SHIELD.get(), com.moonlightc.limen.Limen.id("charged"),
                    (stack, level, entity, seed) -> {
                        long gameTime = 0L;
                        if (level != null) {
                            gameTime = level.getGameTime();
                        } else if (entity != null) {
                            gameTime = entity.level().getGameTime();
                        } else if (net.minecraft.client.Minecraft.getInstance().level != null) {
                            gameTime = net.minecraft.client.Minecraft.getInstance().level.getGameTime();
                        }
                        return LivingShieldItem.isRoarReady(stack, gameTime) ? 1.0F : 0.0F;
                    });
            ItemProperties.register(ModItems.DEFLECTION_SHIELD.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "blocking"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        });
    }

    private static float getPull(ItemStack stack, LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }

        return entity.getUseItem() != stack || CrossbowItem.isCharged(stack)
                ? 0.0F
                : (float) (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / (float) CrossbowItem.getChargeDuration(stack);
    }
}
