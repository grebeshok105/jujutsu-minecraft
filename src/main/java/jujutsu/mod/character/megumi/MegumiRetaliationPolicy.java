package jujutsu.mod.character.megumi;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pure picker behind the packs' retaliation (issue #76): which body, if any, the shikigami should
 * answer for their owner this tick.
 *
 * <p>Two signals, in priority order — the owner's last attacker while it is still fresh, then the
 * nearest body already aggroed on the owner (a mob whose {@code getTarget()} is the owner). The
 * manual sic never passes through here: it writes the same target field and outranks this pass.
 */
final class MegumiRetaliationPolicy {
	private MegumiRetaliationPolicy() {
	}

	/**
	 * @param owner            the vessel's player; its last-hurt source and timestamp are read here
	 * @param gameTime         current game time
	 * @param windowTicks      how long an attacker stays worth answering
	 * @param ownerAggressors  supplies bodies already targeting the owner, any order; only asked for
	 *                         when the owner's own last attacker is stale, so the tick that answers a
	 *                         fresh hit never pays for the scan
	 * @return the body to answer, or null when neither signal holds
	 */
	static LivingEntity pickAggressor(LivingEntity owner, long gameTime, long windowTicks,
			Supplier<List<LivingEntity>> ownerAggressors) {
		LivingEntity attacker = owner.getLastHurtByMob();
		if (isUsable(attacker) && attackerFresh(gameTime, owner.getLastHurtByMobTimestamp(), windowTicks)) {
			return attacker;
		}
		return nearestAggressor(owner, ownerAggressors.get());
	}

	/** An attacker stays worth answering for {@code windowTicks} after the hit that named it. */
	static boolean attackerFresh(long gameTime, long lastHurtTimestamp, long windowTicks) {
		return gameTime - lastHurtTimestamp <= windowTicks;
	}

	/** Closest usable body among the owner's aggressors; null when there is none. */
	static LivingEntity nearestAggressor(LivingEntity owner, List<LivingEntity> ownerAggressors) {
		LivingEntity nearest = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity candidate : ownerAggressors) {
			if (!isUsable(candidate)) {
				continue;
			}
			double distance = candidate.distanceToSqr(owner);
			if (distance < best) {
				best = distance;
				nearest = candidate;
			}
		}
		return nearest;
	}

	static boolean isUsable(LivingEntity candidate) {
		return candidate != null && candidate.isAlive() && !candidate.isRemoved();
	}

	/**
	 * The reach bound every signal answers to (issue #96): an aggressor past {@code radius} cannot
	 * be the pack's answer, however fresh the hit that named it. Applied to the picked aggressor so
	 * the owner's last attacker obeys the same line the aggro scan already draws.
	 */
	static boolean withinRadius(LivingEntity owner, LivingEntity candidate, double radius) {
		return candidate != null && candidate.distanceToSqr(owner) <= radius * radius;
	}

	/**
	 * Issue #96/#107: a mark the pack placed for itself — the retaliation answer — exists only
	 * while an aggressor answers for the owner; the tick none does, it expires. The owner's own
	 * sic ({@code MANUAL}) is an order and outlives the window, and the coordinator's
	 * {@code AUTONOMOUS} mark belongs to the coordination pass that placed it, not to this one.
	 */
	static boolean markExpiresWithoutAggressor(MegumiMarkKind kind) {
		return kind == MegumiMarkKind.RETALIATION;
	}
}
