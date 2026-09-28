package com.moonlightc.limen.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.moonlightc.limen.Limen;
import com.moonlightc.limen.client.PamaTridentImpactWaves;
import com.moonlightc.limen.entity.projectile.PamaTridentEntity;
import com.moonlightc.limen.registry.ModBlocks;
import com.moonlightc.limen.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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

import java.util.List;

@Mod.EventBusSubscriber(modid = Limen.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientForgeEvents {
    private static final int VISIBILITY_RANGE = 8;
    // Impact wave colour #da0205.
    private static final float WAVE_RED = 218.0F / 255.0F;
    private static final float WAVE_GREEN = 2.0F / 255.0F;
    private static final float WAVE_BLUE = 5.0F / 255.0F;

    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void onClientLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PamaTridentImpactWaves.clear();
        }
    }

    @SubscribeEvent
    public static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }

        if (!player.isUsingItem()
                || !(player.getUseItem().is(ModItems.ROYAL_POLEARM.get())
                        || player.getUseItem().is(ModItems.POWERED_POLEARM.get()))) {
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

        Vec3 camera = event.getCamera().getPosition();
        renderStructureVoids(minecraft, player, event.getPoseStack(), camera);
        renderPamaTridentWaves(minecraft, event.getPoseStack(), camera, event.getPartialTick());
    }

    private static void renderStructureVoids(Minecraft minecraft, LocalPlayer player, PoseStack poseStack, Vec3 camera) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!mainHand.is(ModItems.STRUCTURE_VOID_BLOCK.get())
                && !offHand.is(ModItems.STRUCTURE_VOID_BLOCK.get())
                && !head.is(ModItems.STRUCTURE_VOID_BLOCK.get())) {
            return;
        }

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

    private static void renderPamaTridentWaves(Minecraft minecraft, PoseStack poseStack, Vec3 camera, float partialTick) {
        long gameTime = minecraft.level.getGameTime();
        List<PamaTridentImpactWaves.Wave> waves = PamaTridentImpactWaves.active(gameTime);
        if (waves.isEmpty()) {
            return;
        }

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();

        for (PamaTridentImpactWaves.Wave wave : waves) {
            float age = (gameTime - wave.startTick()) + partialTick;
            float progress = Mth.clamp(age / PamaTridentImpactWaves.DURATION_TICKS, 0.0F, 1.0F);
            float radius = (float) (PamaTridentEntity.IMPACT_RADIUS * progress);
            float alpha = 1.0F - progress;

            Vec3 c = wave.center();
            double cx = c.x - camera.x;
            double cy = c.y - camera.y;
            double cz = c.z - camera.z;
            renderOctahedron(pose, consumer, cx, cy, cz, radius, alpha);
        }
        bufferSource.endBatch(RenderType.lines());
    }

    /** Draws the twelve edges of an L1 (Manhattan) octahedron of the given radius in {@code #da0205}. */
    private static void renderOctahedron(PoseStack.Pose pose, VertexConsumer consumer,
                                         double cx, double cy, double cz, float radius, float alpha) {
        double px = cx + radius;
        double nx = cx - radius;
        double py = cy + radius;
        double ny = cy - radius;
        double pz = cz + radius;
        double nz = cz - radius;

        // Four equatorial vertices (in the XZ plane) connected into a diamond.
        line(pose, consumer, px, cy, cz, cx, cy, pz, alpha);
        line(pose, consumer, cx, cy, pz, nx, cy, cz, alpha);
        line(pose, consumer, nx, cy, cz, cx, cy, nz, alpha);
        line(pose, consumer, cx, cy, nz, px, cy, cz, alpha);

        // Top and bottom poles connected to each equatorial vertex.
        line(pose, consumer, cx, py, cz, px, cy, cz, alpha);
        line(pose, consumer, cx, py, cz, nx, cy, cz, alpha);
        line(pose, consumer, cx, py, cz, cx, cy, pz, alpha);
        line(pose, consumer, cx, py, cz, cx, cy, nz, alpha);
        line(pose, consumer, cx, ny, cz, px, cy, cz, alpha);
        line(pose, consumer, cx, ny, cz, nx, cy, cz, alpha);
        line(pose, consumer, cx, ny, cz, cx, cy, pz, alpha);
        line(pose, consumer, cx, ny, cz, cx, cy, nz, alpha);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer consumer,
                             double x1, double y1, double z1, double x2, double y2, double z2, float alpha) {
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-5F) {
            return;
        }
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;
        consumer.vertex(pose.pose(), (float) x1, (float) y1, (float) z1)
                .color(WAVE_RED, WAVE_GREEN, WAVE_BLUE, alpha)
                .normal(pose.normal(), nx, ny, nz).endVertex();
        consumer.vertex(pose.pose(), (float) x2, (float) y2, (float) z2)
                .color(WAVE_RED, WAVE_GREEN, WAVE_BLUE, alpha)
                .normal(pose.normal(), nx, ny, nz).endVertex();
    }
}
