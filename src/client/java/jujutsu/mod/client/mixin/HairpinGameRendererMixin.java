package jujutsu.mod.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jujutsu.mod.client.fx.HairpinCinematicCamera;

@Mixin(GameRenderer.class)
public abstract class HairpinGameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void jujutsumod$applyHairpinFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		float fovOffset = HairpinCinematicCamera.fovOffset();
		if (Math.abs(fovOffset) > 0.001f) {
			cir.setReturnValue(cir.getReturnValueF() + fovOffset);
		}
	}
}
