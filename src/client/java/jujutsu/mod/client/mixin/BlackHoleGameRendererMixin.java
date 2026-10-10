package jujutsu.mod.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.vfx.VfxDirector;

/**
 * Runs the black hole composite at the very end of {@code GameRenderer.renderLevel} — after the
 * world, the hand and the screen effects are all in the framebuffer, so the warp bends everything
 * the player sees, not just the terrain.
 */
@Mixin(GameRenderer.class)
public abstract class BlackHoleGameRendererMixin {
	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void jujutsumod$blackHolePostLevel(DeltaTracker tickCounter, CallbackInfo ci) {
		WorldRenderContext context = VfxDirector.lastWorldContext();
		if (context != null) {
			VfxDirector.blackHole().render(Minecraft.getInstance(), context);
		}
	}
}
