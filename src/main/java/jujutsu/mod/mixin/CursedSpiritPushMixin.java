package jujutsu.mod.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.cursedspirit.perception.CursePerception;

/**
 * Issue #80, pair-physics gate (Step 6): no displacement between a curse subject and a
 * non-perceiving player, in either direction.
 *
 * <p>Why a mixin: the vanilla push loop consults no pair gate (probe 1, javap-verified) —
 * {@code EntitySelector.pushableBy} filters candidates by the no-arg {@code isPushable()} plus
 * team rules, {@code LivingEntity.doPush(other)} is a bare {@code other.push(this)}, and
 * {@code Entity.push} itself checks only vehicle occupancy and {@code noPhysics}. Overrides on
 * the spirit alone cannot stop the spirit's own {@code pushEntities} from shoving the player
 * ({@code player.push(spirit)} consults no spirit method). Cancelling the single shared
 * {@code push} sink covers both directions: spirit-moved-by-player and player-moved-by-spirit.
 *
 * <p>Hot-path cost is two {@code instanceof} checks for the overwhelmingly common unmarked
 * pair; the tag lookup inside {@code isSubject} only runs past a marker hit. Non-player pairs
 * always pass {@code interacts} and behave exactly as before. The gate reads the interaction
 * right ({@code canInteract}), not mere perception: displacement is contact, not sensation.
 *
 * <p>Method descriptor (probe 1, javap-verified on 1.21.8):
 * {@code push(Lnet/minecraft/world/entity/Entity;)V}.
 */
@Mixin(Entity.class)
public abstract class CursedSpiritPushMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$gateUnperceivedPush(Entity other, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (other != null && !CursePerception.interacts(self, other)) {
			ci.cancel();
		}
	}
}
