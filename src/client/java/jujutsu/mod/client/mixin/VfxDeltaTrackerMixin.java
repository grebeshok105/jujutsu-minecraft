package jujutsu.mod.client.mixin;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jujutsu.mod.client.vfx.VfxDirector;

@Mixin(DeltaTracker.Timer.class)
public abstract class VfxDeltaTrackerMixin {
	@Inject(method = "getGameTimeDeltaTicks", at = @At("RETURN"), cancellable = true)
	private void jujutsumod$applyVfxTimeScale(CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(cir.getReturnValueF() * VfxDirector.timeScale());
	}

	@Inject(method = "getGameTimeDeltaPartialTick", at = @At("RETURN"), cancellable = true)
	private void jujutsumod$applyVfxPartialTimeScale(boolean includePaused, CallbackInfoReturnable<Float> cir) {
		cir.setReturnValue(cir.getReturnValueF() * VfxDirector.timeScale());
	}
}
