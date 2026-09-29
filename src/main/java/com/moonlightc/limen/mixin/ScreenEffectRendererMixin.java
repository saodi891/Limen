package com.moonlightc.limen.mixin;

import com.moonlightc.limen.client.CampfireCameraController;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's in-wall overlay is keyed to the player's eye, so it never fires while the campfire peace scene
 * drives the overhead camera. In the rare case where that camera still ends up inside a solid block, this
 * makes {@code getViewBlockingState} answer for the camera position instead, so vanilla paints the block's
 * texture over the screen exactly as it does when a player's head is buried in terrain — no see-through.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
    private static void limen$campfireCameraInWall(Player player, CallbackInfoReturnable<BlockState> cir) {
        if (!CampfireCameraController.isActive()) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition();
        BlockPos pos = BlockPos.containing(eye.x, eye.y, eye.z);
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        if (state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(level, pos)) {
            cir.setReturnValue(state);
        }
    }
}
