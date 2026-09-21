package jujutsu.mod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.vfx.VfxDirector;

/**
 * Cancels view bobbing while a black hole is on screen. The warp field is anchored to the hole's
 * projected position; head bob makes that anchor swim relative to the world, which reads as the
 * shader "shaking". The spec asks for a stable frame — the effect must come from space, not the
 * camera.
 */
@Mixin(GameRenderer.class)
public abstract class BlackHoleBobViewMixin {
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$blackHoleSteadyView(PoseStack poseStack, float partialTicks, CallbackInfo ci) {
		if (VfxDirector.blackHole().hudWarpActive() > 0.0f) {
			ci.cancel();
		}
	}
}
