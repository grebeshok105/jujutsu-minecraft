package jujutsu.mod.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Character-neutral Black Flash proc sequence shared by current combat kits.
 * Covers the roll, the bonus hit, stagger, knockback and focus; cue emission and
 * vessel-specific decorations stay with the caller.
 */
public final class BlackFlashStrike {
	private static final Set<UUID> APPLYING_BONUS = new HashSet<>();

	private BlackFlashStrike() {}

	public static boolean rolls(ServerPlayer player, float chance) {
		return ForcedBlackFlash.isEnabled(player) || player.getRandom().nextFloat() < chance;
	}

	public static boolean isApplyingBonus(LivingEntity target) {
		return APPLYING_BONUS.contains(target.getUUID());
	}

	public static void forgetBonusHit(UUID entityId) {
		APPLYING_BONUS.remove(entityId);
	}

	public static void clearBonusHits() {
		APPLYING_BONUS.clear();
	}

	public static Vec3 impactOrigin(LivingEntity target) {
		return target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
	}

	public static void resolve(
			ServerPlayer caster,
			LivingEntity target,
			float baseDamage,
			float damageMultiplier,
			DamageSource bonusSource,
			boolean pierceInvulnerability,
			int staggerTicks,
			double knockbackStrength
	) {
		float bonus = baseDamage * Math.max(0.0f, damageMultiplier - 1.0f);
		if (bonus > 0.0f) {
			applyBonus(caster, target, bonusSource, bonus, pierceInvulnerability);
		}
		CombatStagger.GLOBAL.apply(target, caster.level().getGameTime(), staggerTicks);
		Vec3 look = caster.getLookAngle();
		target.knockback(knockbackStrength, -look.x, -look.z);
		BlackFlashFocus.grant(caster);
	}

	private static void applyBonus(
			ServerPlayer caster,
			LivingEntity target,
			DamageSource bonusSource,
			float bonus,
			boolean pierceInvulnerability
	) {
		if (!pierceInvulnerability) {
			target.hurtServer(caster.level(), bonusSource, bonus);
			return;
		}
		APPLYING_BONUS.add(target.getUUID());
		// AFTER_DAMAGE runs after vanilla sets invulnerableTime; clear it only for the bonus hit.
		int previousInvulnerable = target.invulnerableTime;
		target.invulnerableTime = 0;
		try {
			target.hurtServer(caster.level(), bonusSource, bonus);
		} finally {
			target.invulnerableTime = Math.max(target.invulnerableTime, previousInvulnerable);
			APPLYING_BONUS.remove(target.getUUID());
		}
	}
}
