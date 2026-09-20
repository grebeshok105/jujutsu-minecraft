package jujutsu.mod.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jujutsu.mod.cursedspirit.ability.effects.RunnerEffect;

/**
 * Issue #119: a carried victim must not sneak-dismount the runner. Vanilla lets any player
 * passenger leave the seat by pressing shift ({@code Player.rideTick} →
 * {@code wantsToStopRiding} → {@code stopRiding}, server-side only), which would free the
 * victim while the carry set, the {@code GRIPPED} marker and the registry pair still say
 * "held". The release path owns dismounting; the gate lives here because no Fabric event
 * reaches this check.
 */
@Mixin(Player.class)
public abstract class RunnerVictimDismountMixin {
	@Inject(method = "wantsToStopRiding()Z", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$carriedVictimCannotDismount(CallbackInfoReturnable<Boolean> cir) {
		if (RunnerEffect.isRunnerVictim((Player) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
