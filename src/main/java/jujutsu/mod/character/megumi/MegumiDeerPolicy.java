package jujutsu.mod.character.megumi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/** Pure priority, eligibility, timing and effect policy for Round Deer. */
public final class MegumiDeerPolicy {
	private static final int NO_PRIORITY = 0;
	private static final int LESSER_WOUND_PRIORITY = 1;
	private static final int HEAVY_SHIKIGAMI_PRIORITY = 2;
	private static final int HEAVY_OWNER_PRIORITY = 3;

	private MegumiDeerPolicy() {}

	/** Facts collected from the bounded owner/pack list; no world query belongs in this policy. */
	public record RecipientFacts(
			UUID uuid,
			boolean alive,
			boolean removed,
			boolean sameLevel,
			boolean ownedByOwner,
			boolean owner,
			boolean shikigami,
			float health,
			float maxHealth,
			double distance
	) {}

	public static boolean scanDue(long gameTime, long nextScanGameTime) {
		return gameTime >= nextScanGameTime;
	}

	/** Only wounded, in-range entities from the owner's own level and owned list can receive support. */
	public static boolean eligibleRecipient(RecipientFacts facts) {
		return facts != null
				&& facts.uuid() != null
				&& facts.alive()
				&& !facts.removed()
				&& facts.sameLevel()
				&& facts.ownedByOwner()
				&& (facts.owner() || facts.shikigami())
				&& facts.maxHealth() > 0.0f
				&& Float.isFinite(facts.health())
				&& Float.isFinite(facts.maxHealth())
				&& facts.health() >= 0.0f
				&& facts.health() < facts.maxHealth()
				&& Double.isFinite(facts.distance())
				&& facts.distance() <= MegumiShikigamiProfile.DEER_SUPPORT_RADIUS;
	}

	/** Higher values win: heavily wounded owner, heavily wounded shikigami, then all lesser wounds. */
	public static int priority(RecipientFacts facts) {
		if (!eligibleRecipient(facts)) {
			return NO_PRIORITY;
		}
		boolean heavilyWounded = missingHealthFraction(facts)
				>= MegumiShikigamiProfile.DEER_HEAVY_WOUND_FRACTION;
		if (heavilyWounded && facts.owner()) {
			return HEAVY_OWNER_PRIORITY;
		}
		if (heavilyWounded && facts.shikigami()) {
			return HEAVY_SHIKIGAMI_PRIORITY;
		}
		return LESSER_WOUND_PRIORITY;
	}

	/** Selects exactly one recipient with deterministic wound, distance and UUID tie-breaks. */
	public static Optional<RecipientFacts> choose(List<RecipientFacts> candidates) {
		if (candidates == null || candidates.isEmpty()) {
			return Optional.empty();
		}
		RecipientFacts best = null;
		for (RecipientFacts candidate : candidates) {
			if (priority(candidate) == NO_PRIORITY) {
				continue;
			}
			if (best == null || compare(candidate, best) < 0) {
				best = candidate;
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Explicit allowlist only: unlisted vanilla and every mod effect stay untouched. Slowness is
	 * deliberately absent because Toad refreshes it at amplifier 100 as a hold mechanic.
	 */
	public static boolean cleanseable(Holder<MobEffect> effect, boolean cursedFearActive) {
		if (effect == null) {
			return false;
		}
		return effect.equals(MobEffects.BLINDNESS)
				|| effect.equals(MobEffects.POISON)
				|| effect.equals(MobEffects.WITHER)
				|| effect.equals(MobEffects.WEAKNESS)
				|| effect.equals(MobEffects.MINING_FATIGUE)
				|| effect.equals(MobEffects.HUNGER)
				|| effect.equals(MobEffects.LEVITATION)
				|| effect.equals(MobEffects.UNLUCK)
				|| effect.equals(MobEffects.BAD_OMEN)
				|| (!cursedFearActive
						&& (effect.equals(MobEffects.DARKNESS) || effect.equals(MobEffects.NAUSEA)));
	}

	/** A pulse can never heal more than the recipient's missing health. */
	public static float healAmount(float health, float maxHealth, float configuredPulse) {
		if (!Float.isFinite(health) || !Float.isFinite(maxHealth)
				|| !Float.isFinite(configuredPulse) || maxHealth <= 0.0f || configuredPulse <= 0.0f) {
			return 0.0f;
		}
		return Math.max(0.0f, Math.min(configuredPulse, maxHealth - health));
	}

	private static int compare(RecipientFacts left, RecipientFacts right) {
		int priorityOrder = Integer.compare(priority(right), priority(left));
		if (priorityOrder != 0) {
			return priorityOrder;
		}
		int woundOrder = Float.compare(missingHealthFraction(right), missingHealthFraction(left));
		if (woundOrder != 0) {
			return woundOrder;
		}
		int distanceOrder = Double.compare(left.distance(), right.distance());
		return distanceOrder != 0 ? distanceOrder : left.uuid().compareTo(right.uuid());
	}

	private static float missingHealthFraction(RecipientFacts facts) {
		return (facts.maxHealth() - facts.health()) / facts.maxHealth();
	}
}
