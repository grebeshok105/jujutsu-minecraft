package jujutsu.mod.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.tongue.TongueClientState;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Client half of the tongue pull (issue #108): while the server says an anchor is attached, the
 * local player's velocity for the coming tick comes from {@link TongueClientState#nextVelocity}
 * instead of being left to vanilla friction.
 *
 * <p><strong>Why the target is {@code Player} and the guard is {@code LocalPlayer}.</strong>
 * javap-verified on the mapped 1.21.8 jar: {@code travel(Lnet/minecraft/world/phys/Vec3;)V} is
 * declared on {@code Player} (offset 0 in that method's Code), while {@code LocalPlayer} declares
 * no {@code travel} at all — targeting {@code LocalPlayer} would therefore fail to find an
 * injection point and the mixin would not apply. {@code ServerPlayer} inherits the same method, so
 * the {@code instanceof LocalPlayer} guard is what keeps this client-authoritative law on the one
 * body that actually integrates its own physics. Server players move on packets, never on
 * {@code travel}.
 *
 * <p><strong>Where exactly.</strong> {@code @At("TAIL")} resolves to the method's <em>last</em>
 * {@code RETURN} — offset 171, the end of the non-passenger path (the swim branch and the
 * flying/not-flying split both converge there). The passenger branch returns at offset 12 and is
 * deliberately not injected: a player riding a boat or a mount has no velocity of its own to drive,
 * and the vanilla vehicle motion is not the tongue's business. Injecting after vanilla travel is
 * what makes the pull a law rather than an addition: gravity, the flight input and the ground
 * friction have all been applied to the velocity we read, and the policy replaces the result.
 */
@Mixin(Player.class)
public abstract class TonguePhysicsMixin {
	@Inject(method = "travel", at = @At("TAIL"))
	private void jujutsumod$pullTowardTongueAnchor(Vec3 travelVector, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player)) {
			return;
		}
		if (player.hasEffect(JujutsuEffects.GRIPPED)) {
			// A held player's movement belongs to the hold: the server re-teleports the victim
			// every tick, and a client-authoritative pull fighting it would rubber-band the body
			// out of the grip — the same fight HoldInputMixin exists to prevent.
			return;
		}
		Vec3 current = player.getDeltaMovement();
		Vec3 pulled = TongueClientState.nextVelocity(current, player.position(), player.input.keyPresses);
		if (pulled != current) {
			// Released ticks hand the same instance back; only an attached pull ever writes.
			player.setDeltaMovement(pulled);
		}
	}
}
