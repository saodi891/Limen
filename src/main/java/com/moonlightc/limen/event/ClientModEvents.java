package com.moonlightc.limen.event;

import com.moonlightc.limen.entity.client.AutonomousSpiderRenderer;
import com.moonlightc.limen.entity.client.ChargedCoilArrowRenderer;
import com.moonlightc.limen.entity.client.CoilFieldRenderer;
import com.moonlightc.limen.entity.client.EniacRenderer;
import com.moonlightc.limen.entity.client.MechanicTrappedModel;
import com.moonlightc.limen.entity.client.PamaDetectorRenderer;
import com.moonlightc.limen.entity.client.PamaRenderer;
import com.moonlightc.limen.entity.client.SilkBabyRenderer;
import com.moonlightc.limen.entity.client.SkeletonChippedRenderer;
import com.moonlightc.limen.entity.client.ZombieChippedRenderer;
import com.moonlightc.limen.entity.client.TowerGuardRenderer;
import com.moonlightc.limen.entity.client.TowerGhostRenderer;
import com.moonlightc.limen.entity.client.TowerGhostBlueFlameRenderer;
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
    public static void registerModelLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MechanicTrappedModel.LAYER_LOCATION, MechanicTrappedModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CHARGED_COIL_ARROW.get(), ChargedCoilArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.PAMA_TRIDENT.get(), com.moonlightc.limen.entity.client.PamaTridentRenderer::new);
        event.registerEntityRenderer(ModEntities.PAMA_ENDER_PEARL.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.COIL_FIELD.get(), CoilFieldRenderer::new);
        event.registerEntityRenderer(ModEntities.PAMA_DETECTOR.get(), PamaDetectorRenderer::new);
        event.registerEntityRenderer(ModEntities.PAMA.get(), PamaRenderer::new);
        event.registerEntityRenderer(ModEntities.AUTONOMOUS_SPIDER.get(), AutonomousSpiderRenderer::new);
        event.registerEntityRenderer(ModEntities.SILK_BABY.get(), SilkBabyRenderer::new);
        event.registerEntityRenderer(ModEntities.ENIAC.get(), EniacRenderer::new);
        event.registerEntityRenderer(ModEntities.TOWER_GUARD.get(), TowerGuardRenderer::new);
        event.registerEntityRenderer(ModEntities.TOWER_GHOST.get(), TowerGhostRenderer::new);
        event.registerEntityRenderer(ModEntities.SOUL_FLAME.get(), TowerGhostBlueFlameRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETON_CHIPPED.get(), SkeletonChippedRenderer::new);
        event.registerEntityRenderer(ModEntities.ZOMBIE_CHIPPED.get(), ZombieChippedRenderer::new);
        event.registerEntityRenderer(ModEntities.TOTEM_OF_RELINK.get(),
                com.moonlightc.limen.entity.client.TotemOfRelinkRenderer::new);
        event.registerBlockEntityRenderer(com.moonlightc.limen.registry.ModBlockEntities.ARTISAN_BOX.get(),
                context -> new com.moonlightc.limen.entity.client.ArtisanBoxRenderer());
    }

    @SubscribeEvent
    @SuppressWarnings("removal")
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.moonlightc.limen.registry.ModMenuTypes.ARTISAN_BOX.get(),
                    com.moonlightc.limen.client.gui.ArtisanBoxScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.moonlightc.limen.registry.ModMenuTypes.CAMPFIRE.get(),
                    com.moonlightc.limen.client.gui.CampfireScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.moonlightc.limen.registry.ModMenuTypes.CAMPFIRE_HUB.get(),
                    com.moonlightc.limen.client.gui.CampfireHubScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.moonlightc.limen.registry.ModMenuTypes.CAMPFIRE_ENCHANT.get(),
                    com.moonlightc.limen.client.gui.CampfireEnchantScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.moonlightc.limen.registry.ModMenuTypes.CAMPFIRE_BREW.get(),
                    com.moonlightc.limen.client.gui.CampfireBrewScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.moonlightc.limen.registry.ModMenuTypes.CAMPFIRE_MEND.get(),
                    com.moonlightc.limen.client.gui.CampfireMendScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(
                    com.moonlightc.limen.registry.ModMenuTypes.TABLE_SAW.get(),
                    com.moonlightc.limen.client.gui.TableSawScreen::new);
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.STRUCTURE_VOID_BLOCK.get(), RenderType.cutout());
            ItemProperties.register(ModItems.ELECTROMAGNETIC_CROSSBOW.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "pull"),
                    (stack, level, entity, seed) -> getPull(stack, level, entity));
            ItemProperties.register(ModItems.ELECTROMAGNETIC_CROSSBOW.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "pulling"),
                    (stack, level, entity, seed) -> isPulling(stack, entity) ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.ELECTROMAGNETIC_CROSSBOW.get(), ResourceLocation.fromNamespaceAndPath("minecraft", "charged"),
                    (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
            ItemProperties.register(ModItems.POWERED_EXOSKELETON.get(), com.moonlightc.limen.Limen.id("broken"),
                    (stack, level, entity, seed) -> com.moonlightc.limen.item.PoweredExoskeletonItem.isWornOut(stack)
                            ? 1.0F : 0.0F);
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
            // Swaps the chainsword to its static overheated model while the vanilla item cooldown
            // (started when the weapon overheats) is running.
            ItemProperties.register(ModItems.CHAINSWORD.get(), com.moonlightc.limen.Limen.id("overheated"),
                    (stack, level, entity, seed) -> {
                        if (entity instanceof net.minecraft.world.entity.player.Player player) {
                            return player.getCooldowns().isOnCooldown(stack.getItem()) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });
        });
    }

    private static float getPull(ItemStack stack, net.minecraft.world.level.Level level, LivingEntity entity) {
        if (CrossbowItem.isCharged(stack)) {
            return 0.0F;
        }

        // Auto-reload (overclock) drives the pull animation from the stored start tick so the
        // string draws back exactly like a manual reload, even though nobody is "using" the item.
        Float autoPull = getAutoReloadPull(stack, level, entity);
        if (autoPull != null) {
            return autoPull;
        }

        if (entity == null || entity.getUseItem() != stack) {
            return 0.0F;
        }
        return (float) (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / (float) CrossbowItem.getChargeDuration(stack);
    }

    private static boolean isPulling(ItemStack stack, LivingEntity entity) {
        if (CrossbowItem.isCharged(stack)) {
            return false;
        }
        if (entity != null && entity.isUsingItem() && entity.getUseItem() == stack) {
            return true;
        }
        return stack.getOrCreateTag().contains(com.moonlightc.limen.item.ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG);
    }

    @org.jetbrains.annotations.Nullable
    private static Float getAutoReloadPull(ItemStack stack, net.minecraft.world.level.Level level, LivingEntity entity) {
        net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(com.moonlightc.limen.item.ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG)) {
            return null;
        }

        long gameTime;
        if (level != null) {
            gameTime = level.getGameTime();
        } else if (entity != null) {
            gameTime = entity.level().getGameTime();
        } else if (net.minecraft.client.Minecraft.getInstance().level != null) {
            gameTime = net.minecraft.client.Minecraft.getInstance().level.getGameTime();
        } else {
            return 0.0F;
        }

        long elapsed = gameTime - tag.getLong(com.moonlightc.limen.item.ElectromagneticCrossbowItem.AUTO_RELOAD_START_TAG);
        return net.minecraft.util.Mth.clamp((float) elapsed / (float) CrossbowItem.getChargeDuration(stack), 0.0F, 1.0F);
    }
}
