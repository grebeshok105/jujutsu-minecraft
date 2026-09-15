package jujutsu.mod.character.megumi;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Who Max Elephant's presence is allowed to <em>hurt</em> (issue #79). Deliberately not
 * {@link MegumiSummonRuntime#isEligibleTarget}: that one answers "may I attack this", which excludes
 * allies and neutrals alike — while the presence must still push an ally, just without damage.
 *
 * <p>Three signals, any of which is enough: a hostile archetype, a body currently aiming at the
 * owner, or a fresh aggressor (the same window the packs use to answer for their owner).
 */
public final class MegumiHostilityPolicy {
	private MegumiHostilityPolicy() {}

	/** The decision on plain booleans — the part worth testing without a level. */
	public static boolean isHostile(boolean enemyArchetype, boolean aimedAtOwner, boolean freshAggressor) {
		return enemyArchetype || aimedAtOwner || freshAggressor;
	}

	/**
	 * Reads the three signals off live entities.
	 *
	 * <p>The freshness window is measured on the OWNER's own clock: vanilla stamps
	 * {@code lastHurtByMobTimestamp} with the victim's {@code tickCount}, so comparing it against
	 * {@code level().getGameTime()} silently expires every hit in a world that has been ticking for
	 * longer than the player has existed — which is every world after a rejoin (issue #91, the
	 * same trap {@link MegumiSummonRuntime#retaliationTarget} documents). The overload therefore
	 * takes no clock at all: a caller-passed time is exactly how the wrong one crept in.
	 */
	public static boolean isHostile(LivingEntity owner, LivingEntity candidate) {
		if (candidate == null || candidate == owner) {
			return false;
		}
		boolean enemy = candidate instanceof Enemy;
		boolean aimed = owner != null && candidate instanceof Mob mob && mob.getTarget() == owner;
		boolean fresh = owner != null
				&& candidate == owner.getLastHurtByMob()
				&& aggressorFresh(owner.tickCount, owner.getLastHurtByMobTimestamp());
		return isHostile(enemy, aimed, fresh);
	}

	/**
	 * An attacker stays worth answering for the window after the hit that named it.
	 * {@code nowOnOwnerClock} must be the owner's {@code tickCount} — never the level's game time.
	 */
	public static boolean aggressorFresh(long nowOnOwnerClock, long lastHurtTimestamp) {
		return nowOnOwnerClock - lastHurtTimestamp
				<= MegumiShikigamiProfile.ELEPHANT_PRESENCE_AGGRESSION_WINDOW_TICKS;
	}
}
