package jujutsu.mod.cursedspirit.perception;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Issue #80, damage gates in both directions (Step 4) and the melee-swing gate (Step 5).
 *
 * <p>Both listeners identify the curse side through {@link CursePerception#isSubject} (marker or
 * tag), never through a compile dependency on a body class — future curse projectiles and zones
 * join the rule without touching this file. Non-player pairs are never filtered.
 */
public final class CursedSpiritInteractionGates {
	private CursedSpiritInteractionGates() {}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CursedSpiritInteractionGates::allowDamage);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (entity != null && CursePerception.isSubject(entity)
					&& !CursePerception.canInteract(player)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		// The accountable entity: for direct blows this IS getEntity (a melee attacker, or the
		// shooter vanilla stores as causing entity on arrow/trident sources); when the causing
		// entity is absent or is itself a projectile/owned body, the owner chain recovers the
		// player behind it — a non-interactor's arrow, trident or TNT cannot reach the curse
		// through the indirection.
		Entity responsible = CursePerception.responsibleParty(
				attacker != null ? attacker : source.getDirectEntity());
		// Curse → player without the interaction right: the blow does not exist for the victim.
		// The subject check covers both a direct curse attacker and a curse-owned body
		// (projectile, summon) resolved through the owner chain.
		if (victim instanceof Player && !CursePerception.canInteract(victim)
				&& (CursePerception.isSubject(attacker) || CursePerception.isSubject(responsible))) {
			return false;
		}
		// Player without the interaction right → curse: blind swings and owned projectiles
		// alike cannot connect. A projectile with no player owner (dispenser, world trap)
		// resolves to null and stays an honest world hazard.
		if (CursePerception.isSubject(victim)
				&& responsible instanceof Player && !CursePerception.canInteract(responsible)) {
			return false;
		}
		return true;
	}
}
