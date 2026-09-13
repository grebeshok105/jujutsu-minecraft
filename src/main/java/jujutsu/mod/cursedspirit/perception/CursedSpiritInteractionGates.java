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
					&& !CursePerception.perceives(player)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		// Curse → non-perceiving player: the blow does not exist for the victim.
		if (attacker != null && CursePerception.isSubject(attacker)
				&& victim instanceof Player && !CursePerception.perceives(victim)) {
			return false;
		}
		// Non-perceiving player → curse: blind swings cannot connect.
		if (CursePerception.isSubject(victim)
				&& attacker instanceof Player && !CursePerception.perceives(attacker)) {
			return false;
		}
		return true;
	}
}
