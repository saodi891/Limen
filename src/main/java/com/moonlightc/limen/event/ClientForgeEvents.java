package com.moonlightc.limen.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.moonlightc.limen.Limen;
import com.moonlightc.limen.registry.ModBlocks;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {
    private static final int VISIBILITY_RANGE = 8;

    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }

        if (!player.isUsingItem() || !player.getUseItem().is(ModItems.ROYAL_POLEARM.get())) {
            return;
        }

        event.getInput().forwardImpulse *= 5.0F;
        event.getInput().leftImpulse *= 5.0F;

        Minecraft minecraft = Minecraft.getInstance();
        boolean shouldSprint = minecraft.options.keySprint.isDown() && event.getInput().forwardImpulse > 0.0F;
        player.setSprinting(shouldSprint);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!mainHand.is(ModItems.STRUCTURE_VOID_BLOCK.get())
                && !offHand.is(ModItems.STRUCTURE_VOID_BLOCK.get())
                && !head.is(ModItems.STRUCTURE_VOID_BLOCK.get())) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher blockRenderer = minecraft.getBlockRenderer();

        BlockPos center = player.blockPosition();
        BlockPos min = center.offset(-VISIBILITY_RANGE, -VISIBILITY_RANGE, -VISIBILITY_RANGE);
        BlockPos max = center.offset(VISIBILITY_RANGE, VISIBILITY_RANGE, VISIBILITY_RANGE);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = minecraft.level.getBlockState(pos);
            if (!state.is(ModBlocks.STRUCTURE_VOID_BLOCK.get())) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            blockRenderer.getModelRenderer().tesselateBlock(
                    minecraft.level,
                    blockRenderer.getBlockModel(state),
                    state,
                    pos,
                    poseStack,
                    bufferSource.getBuffer(RenderType.cutout()),
                    false,
                    RandomSource.create(state.getSeed(pos)),
                    state.getSeed(pos),
                    OverlayTexture.NO_OVERLAY
            );
            poseStack.popPose();
        }
        bufferSource.endBatch();
    }
}
