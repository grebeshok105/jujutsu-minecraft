package jujutsu.mod.client.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * The client half of a hold (issue #79): while the local player carries {@code GRIPPED} — the Toad's
 * grab or a cursed spirit's runner — this client must not fight the server's pin with its own
 * movement input, or the victim rubber-bands out of the grip.
 *
 * <p><strong>Where, exactly.</strong> Inside {@code LocalPlayer.aiStep()} the order (javap-verified
 * on the mapped 1.21.8 jar) is: bytecode 0 (where a HEAD injection would run) → 158
 * {@code ClientInput.tick()} — which <em>recomputes</em> {@code keyPresses} from the raw keys — →
 * the movement reads at 362, 412, 523, … So a HEAD injection is silently overwritten before
 * anything consumes it. The injection therefore sits immediately AFTER that recompute: the
 * movement reads that follow all see {@link Input#EMPTY}.
 *
 * <p><strong>What is suppressed.</strong> Both movement channels, at their own seams:
 * {@code keyPresses} carries forward/back/left/right, jump, sneak and sprint, while the locomotion
 * impulse ({@code xxa}/{@code zza}) is copied from {@code input.getMoveVector()} inside
 * {@link LocalPlayer#applyInput()} — zeroing only the first left WASD walking and the server pin
 * rubber-banded the victim. {@code moveVector} itself is {@code protected} on {@code ClientInput},
 * so it cannot be shadowed from this mixin; the second injection cancels {@code applyInput()} while
 * held, which is the only place that reads it. Attack, item use, inventory and hotbar slots are
 * untouched — the design leaves them to the player while held.
 */
@Mixin(LocalPlayer.class)
public abstract class HoldInputMixin {
	@Shadow
	public ClientInput input;

	@Inject(
			method = "aiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/player/ClientInput;tick()V",
					shift = At.Shift.AFTER))
	private void jujutsumod$suppressMovementWhileHeld(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (!self.hasEffect(JujutsuEffects.GRIPPED)) {
			return;
		}
		this.input.keyPresses = Input.EMPTY;
	}

	@Inject(method = "applyInput", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$suppressLocomotionWhileHeld(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		if (self.hasEffect(JujutsuEffects.GRIPPED)) {
			ci.cancel();
		}
	}
}
