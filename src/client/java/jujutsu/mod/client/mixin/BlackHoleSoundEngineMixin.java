package jujutsu.mod.client.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.blackhole.BlackHoleSoundInstance;

/**
 * Suppresses ordinary Minecraft audio while a black hole is active: every sound that is not a
 * {@link BlackHoleSoundInstance} has its computed volume scaled by {@code 1 - duckAmount}. Ducking
 * at the volume stage (rather than pausing) covers sounds that start mid-effect and restores the
 * world instantly when the window ends.
 */
@Mixin(SoundEngine.class)
public abstract class BlackHoleSoundEngineMixin {
	@Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
			at = @At("RETURN"), cancellable = true)
	private void jujutsumod$blackHoleDuck(SoundInstance instance, CallbackInfoReturnable<Float> cir) {
		if (instance instanceof BlackHoleSoundInstance) {
			return;
		}
		float duck = VfxDirector.blackHole().duckAmount();
		if (duck > 0.0f) {
			cir.setReturnValue(cir.getReturnValueF() * (1.0f - Math.min(1.0f, duck)));
		}
	}
}
