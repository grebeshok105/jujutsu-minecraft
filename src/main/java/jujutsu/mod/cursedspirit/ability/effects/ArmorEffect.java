package jujutsu.mod.cursedspirit.ability.effects;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritGrade;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Armor (Block 3, Step 8): passive flat absorption applied <em>before</em>
 * {@code super.hurtServer}, floored at zero. Always on while in the pool — a state, never
 * a start. A fully absorbed hit is not accepted damage, so the hurt scream stays silent.
 */
public final class ArmorEffect {
	private ArmorEffect() {
	}

	/**
	 * Source-aware absorption (review #90): the shell absorbs <em>attacks</em>, not
	 * hazards — sources in {@link DamageTypeTags#BYPASSES_ARMOR} (fire, fall, drowning,
	 * cactus, magic…) pass through at full amount, matching what vanilla armor would
	 * do. A {@code null} source keeps the legacy absorb-everything behaviour.
	 */
	public static float absorb(CursedSpiritGrade grade, List<CursedSpiritAbilityId> pool,
			float amount, DamageSource source) {
		if (source != null && source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return amount;
		}
		return absorb(grade, pool, amount);
	}

	/** Incoming damage after absorption; 0 means the hit never reaches the body. */
	public static float absorb(CursedSpiritGrade grade, List<CursedSpiritAbilityId> pool, float amount) {
		if (!pool.contains(CursedSpiritAbilityId.ARMOR)) {
			return amount;
		}
		float left = amount - (float) CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.ARMOR, grade)
				.strength();
		return Math.max(0.0f, left);
	}

	public static void emitBlocked(CursedSpiritEntity spirit, long now) {
		if (spirit.level() instanceof ServerLevel level) {
			JujutsuNetworking.broadcastVfxCue(level, spirit.position(),
					CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
					VfxCues.anchored(CursedSpiritVfxIds.ARMOR, spirit.position(), spirit.getId(),
							spirit.position(), 1, now, spirit.getRandom().nextLong()),
					CursePerception::perceives);
		}
	}
}
