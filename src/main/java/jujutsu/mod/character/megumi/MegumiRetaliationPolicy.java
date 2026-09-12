package jujutsu.mod.character.megumi;

import java.util.List;
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
	 * @param ownerAggressors  bodies already targeting the owner, any order
	 * @return the body to answer, or null when neither signal holds
	 */
	static LivingEntity pickAggressor(LivingEntity owner, long gameTime, long windowTicks,
			List<LivingEntity> ownerAggressors) {
		LivingEntity attacker = owner.getLastHurtByMob();
		if (isUsable(attacker) && attackerFresh(gameTime, owner.getLastHurtByMobTimestamp(), windowTicks)) {
			return attacker;
		}
		return nearestAggressor(owner, ownerAggressors);
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
}
