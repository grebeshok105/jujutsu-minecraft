package jujutsu.mod.cursedspirit;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pure attack math for the cursed-spirit strike. No level, no entity, no randomness — every number
 * comes from the passed {@link CursedSpiritTierStats} row, so tests can mutate a row and watch the
 * derived numbers move (R20 anti-duplication proof).
 */
public final class CursedSpiritAttackPolicy {
	private CursedSpiritAttackPolicy() {}

	public enum Phase {
		APPROACH,
		WINDUP,
		STRIKE,
		RECOVER
	}

	/** Bodies hit by one strike: the direct target plus the AoE splash around the attacker. */
	public record StrikePlan(LivingEntity primary, List<LivingEntity> aoe) {}

	/**
	 * Pure phase transition. {@code ticksInPhase} counts ticks already spent in {@code phase};
	 * {@code inReach} is true when the target is inside strike range this tick.
	 */
	public static Phase advance(Phase phase, int ticksInPhase, boolean inReach, CursedSpiritTierStats stats) {
		return switch (phase) {
			case APPROACH -> inReach ? Phase.WINDUP : Phase.APPROACH;
			case WINDUP -> windupDone(ticksInPhase, stats) ? Phase.STRIKE : Phase.WINDUP;
			case STRIKE -> Phase.RECOVER;
			case RECOVER -> ticksInPhase >= stats.attackCooldownTicks() ? Phase.APPROACH : Phase.RECOVER;
		};
	}

	/** True once the windup clock has run {@code attackWindupTicks} ticks. */
	public static boolean windupDone(int windupElapsed, CursedSpiritTierStats stats) {
		return windupElapsed >= stats.attackWindupTicks();
	}

	/**
	 * True when the centres are close enough to strike: the profile reach plus both bodies'
	 * half-widths, so the hitbox edge (R24) is the boundary.
	 */
	public static boolean inReach(double centreDistance, double attackerWidth, double victimWidth,
			CursedSpiritTierStats stats) {
		return centreDistance <= stats.attackReach() + attackerWidth / 2.0 + victimWidth / 2.0;
	}

	/** Plain positional snapshot so the AoE membership core stays entity-free (unit-testable). */
	public record Body(double x, double y, double z, boolean alive) {}

	/**
	 * Pure AoE membership core: indices of {@code bodies} (excluding {@code primaryIndex} and
	 * dead bodies) within {@code aoeRadius} of the attacker. An {@code aoeRadius} of 0 (tiers
	 * 1–2) splashes nobody.
	 */
	public static List<Integer> aoeMemberIndices(List<Body> bodies, int primaryIndex,
			double attackerX, double attackerY, double attackerZ, CursedSpiritTierStats stats) {
		List<Integer> members = new ArrayList<>();
		double radius = stats.aoeRadius();
		if (radius > 0.0) {
			double radiusSqr = radius * radius;
			for (int i = 0; i < bodies.size(); i++) {
				if (i == primaryIndex) {
					continue;
				}
				Body candidate = bodies.get(i);
				if (!candidate.alive()) {
					continue;
				}
				double dx = candidate.x() - attackerX;
				double dy = candidate.y() - attackerY;
				double dz = candidate.z() - attackerZ;
				if (dx * dx + dy * dy + dz * dz <= radiusSqr) {
					members.add(i);
				}
			}
		}
		return List.copyOf(members);
	}

	/**
	 * Splits nearby bodies into the direct victim (already resolved) and the AoE splash: every
	 * other living body within {@code aoeRadius} of the attacker. The splash is the attack's
	 * ground impact, so it does not depend on the primary still being in reach — a slam whose
	 * tracked victim escaped still hits the bodies standing in the crater (issue #85).
	 */
	public static StrikePlan strikeTargets(LivingEntity primary, List<LivingEntity> nearby,
			double attackerX, double attackerY, double attackerZ, CursedSpiritTierStats stats) {
		List<Body> bodies = new ArrayList<>(nearby.size());
		int primaryIndex = -1;
		for (int i = 0; i < nearby.size(); i++) {
			LivingEntity candidate = nearby.get(i);
			if (candidate == primary) {
				primaryIndex = i;
			}
			bodies.add(new Body(candidate.getX(), candidate.getY(), candidate.getZ(), candidate.isAlive()));
		}
		List<LivingEntity> aoe = new ArrayList<>();
		for (int index : aoeMemberIndices(bodies, primaryIndex, attackerX, attackerY, attackerZ, stats)) {
			aoe.add(nearby.get(index));
		}
		return new StrikePlan(primary, List.copyOf(aoe));
	}

	/** Direct-strike damage, straight from the profile row. */
	public static float primaryDamage(CursedSpiritTierStats stats) {
		return (float) stats.attackDamage();
	}

	/** AoE damage: the profile fraction of the direct strike. */
	public static float aoeDamage(CursedSpiritTierStats stats) {
		return (float) (stats.attackDamage() * stats.aoeDamageScale());
	}
	/** Ticks from one STRIKE to the next WINDUP, straight from the profile row. */
	public static int cooldownTicks(CursedSpiritTierStats stats) {
		return stats.attackCooldownTicks();
	}
}
