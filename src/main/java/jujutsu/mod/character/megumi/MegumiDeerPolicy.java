package jujutsu.mod.character.megumi;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * The Round Deer's whole decision surface as pure functions (issue #107 pure-seam convention):
 * the brain resolves the world into {@link WoundedFacts} and asks this file whom to heal, what to
 * cleanse, where to stand, and how hard to shove. Nothing here touches a level, a registry, or a
 * clock — every test drives it on numbers alone.
 */
final class MegumiDeerPolicy {

	/** What kind of friendly a wounded candidate is — the strict priority lanes. */
	enum WoundedKind {
		OWNER,
		OWN_SHIKIGAMI,
		ALLY,
		SELF
	}

	/**
	 * One heal/cleanse candidate as a flat fact: identity, lane, wound depth, distance to the
	 * deer, and whether it carries at least one {@link #cleansable} effect (the cleanse scan
	 * picks among carriers, wounded or not — a held or feared friendly needs no wound).
	 */
	record WoundedFacts(
			UUID uuid,
			WoundedKind kind,
			double missingHealthFraction,
			double distanceSqr,
			boolean carriesCleansable) {}

	/** Lane first (owner > own shikigami > allies > self), then the deeper wound, then the nearer body. */
	private static final Comparator<WoundedFacts> PRIORITY = Comparator
			.comparingInt((WoundedFacts facts) -> kindRank(facts.kind()))
			.thenComparing(Comparator.comparingDouble(WoundedFacts::missingHealthFraction).reversed())
			.thenComparingDouble(WoundedFacts::distanceSqr)
			.thenComparing(WoundedFacts::uuid);

	private MegumiDeerPolicy() {}

	/** The scan clock: a pulse is wanted once the stored deadline has passed. */
	static boolean healDue(long gameTime, long nextHealScanGameTime) {
		return gameTime >= nextHealScanGameTime;
	}

	/** The cleanse clock, on its own slower scan. */
	static boolean cleanseDue(long gameTime, long nextCleanseScanGameTime) {
		return gameTime >= nextCleanseScanGameTime;
	}

	/** Best pulse recipient among actually-wounded candidates; null when nobody qualifies. */
	static WoundedFacts healPriority(List<WoundedFacts> candidates) {
		return candidates.stream()
				.filter(facts -> facts.missingHealthFraction() > 0.0)
				.min(PRIORITY)
				.orElse(null);
	}

	/** Best cleanse recipient among friendly carriers of a cleansable effect; null when none. */
	static WoundedFacts cleansePriority(List<WoundedFacts> candidates) {
		return candidates.stream()
				.filter(WoundedFacts::carriesCleansable)
				.min(PRIORITY)
				.orElse(null);
	}

	/**
	 * Pulse strength scaled by the missing-health fraction, between {@code DEER_HEAL_MIN} and
	 * {@code DEER_HEAL_MAX}; the self-heal runs at {@code DEER_SELF_HEAL_FACTOR} so the deer can
	 * never keep itself topped off at the rate it spends on others.
	 */
	static float healPulse(double missingHealthFraction, WoundedKind kind) {
		double fraction = Math.max(0.0, Math.min(1.0, missingHealthFraction));
		double amount = MegumiShikigamiProfile.DEER_HEAL_MIN
				+ fraction * (MegumiShikigamiProfile.DEER_HEAL_MAX - MegumiShikigamiProfile.DEER_HEAL_MIN);
		if (kind == WoundedKind.SELF) {
			amount *= MegumiShikigamiProfile.DEER_SELF_HEAL_FACTOR;
		}
		return (float) amount;
	}

	/**
	 * Whether an effect is safe for the deer to strip. Only real hostile debuffs are in scope —
	 * the mod's internal authority markers are denied by class: {@code GRIPPED} and
	 * {@code MEGUMI_TOAD_TONGUE} are hold channels the client mirrors (stripping them desyncs a
	 * held victim), {@code MEGUMI_SHADOW_GRIP} is futile (the trap re-applies it every tick), and
	 * {@code MEGUMI_NUE_WINGS} plus the momentum pair are beneficial internals. {@code SOAKED}
	 * and {@code CURSED_FEAR} are hostile debuffs victims carry — stripping them IS the cleanse.
	 */
	static boolean cleansable(MobEffect effect) {
		return effect.getCategory() == MobEffectCategory.HARMFUL
				&& !DeniedEffects.CLASSES.contains(effect.getClass());
	}

	/**
	 * The interpose anchor: {@code radius} blocks out from the owner toward the threat on the
	 * ground plane, clamped so a point-blank threat cannot push the anchor past the threat
	 * itself (the deer shields, it does not chase).
	 */
	static Vec3 interposePoint(Vec3 ownerPos, Vec3 threatPos, double radius) {
		double dx = threatPos.x - ownerPos.x;
		double dz = threatPos.z - ownerPos.z;
		double distance = Math.hypot(dx, dz);
		if (distance < 1.0E-6) {
			return ownerPos;
		}
		double step = Math.min(radius, distance);
		return new Vec3(
				ownerPos.x + dx / distance * step,
				ownerPos.y,
				ownerPos.z + dz / distance * step);
	}

	/**
	 * The (x, z) arguments for {@link LivingEntity#knockback}: that call pushes opposite the
	 * vector it is fed, so the deer feeds it deer-ward and the attacker flies away.
	 */
	static Vec3 antlerKnockback(Vec3 deerPos, Vec3 attackerPos) {
		return new Vec3(deerPos.x - attackerPos.x, 0.0, deerPos.z - attackerPos.z);
	}

	/** The antler cooldown read: the shove is free again once the stored deadline has passed. */
	static boolean shoveReady(long gameTime, long antlerCooldownUntil) {
		return gameTime >= antlerCooldownUntil;
	}

	private static int kindRank(WoundedKind kind) {
		return switch (kind) {
			case OWNER -> 0;
			case OWN_SHIKIGAMI -> 1;
			case ALLY -> 2;
			case SELF -> 3;
		};
	}

	/**
	 * The class denylist, lazily built so ordinary policy calls never class-load the effect
	 * registry (registration only runs while the game bootstrap permits it).
	 */
	private static final class DeniedEffects {
		private static final Set<Class<?>> CLASSES = Set.of(
				JujutsuEffects.GRIPPED.value().getClass(),
				JujutsuEffects.MEGUMI_SHADOW_GRIP.value().getClass(),
				JujutsuEffects.MEGUMI_TOAD_TONGUE.value().getClass(),
				JujutsuEffects.MEGUMI_NUE_WINGS.value().getClass(),
				JujutsuEffects.RESONANT_MOMENTUM.value().getClass(),
				JujutsuEffects.TODO_SWAP_MOMENTUM.value().getClass());
	}
}
