package jujutsu.mod.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jujutsu.mod.cursedspirit.perception.CursePerception;

/**
 * Issue #80, projectile pass-through gate (post-merge review): a projectile owned by a
 * player without the curse-interaction right flies THROUGH a curse subject — no hit, no
 * stick, no deflection-off-thin-air leaking the invisible body's position.
 *
 * <p>Why a mixin: the pair check needs the projectile, and vanilla's target-side hooks
 * carry none — {@code Entity.canBeHitByProjectile()} and {@code isPickable()} are no-arg
 * self-state (javap-verified on 1.21.8), so an override on the spirit cannot see who
 * fired. {@code Projectile.canHitEntity(Entity)} is the single funnel every projectile
 * (arrow, trident, fireball, spit) consults before selecting a hit target; returning
 * {@code false} removes the spirit from the candidate set entirely, before
 * {@code hitTargetOrDeflectSelf} can ever ask for its deflection — so the "bounce off
 * thin air" leak dies with the damage path (which {@code ALLOW_DAMAGE} already refuses;
 * this additionally spares the visual stick).
 *
 * <p>Unowned projectiles (dispenser arrows, world traps) resolve to {@code null} through
 * {@link CursePerception#responsibleParty} and stay honest world hazards — no player is
 * responsible for them.
 *
 * <p>Method descriptor (javap-verified on 1.21.8):
 * {@code canHitEntity(Lnet/minecraft/world/entity/Entity;)Z}.
 */
@Mixin(Projectile.class)
public abstract class CursedSpiritProjectileMixin {
	@Inject(method = "canHitEntity(Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("HEAD"), cancellable = true)
	private void jujutsumod$unownedPerceptionProjectileMisses(Entity target,
			CallbackInfoReturnable<Boolean> cir) {
		if (!CursePerception.isSubject(target)) {
			return;
		}
		if (CursePerception.responsibleParty((Entity) (Object) this) instanceof Player owner
				&& !CursePerception.canInteract(owner)) {
			cir.setReturnValue(false);
		}
	}
}
