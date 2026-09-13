package jujutsu.mod.cursedspirit.ability.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritGrade;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Berserk (Block 3, Step 8): once damage drops the body at or below the profile threshold,
 * the latch closes permanently — transient attack/speed modifiers plus a flare cue. The
 * latch is NBT-persisted; the modifiers are transient by design and re-applied from the
 * latch on load ({@link #restoreIfLatched}), otherwise a reload silently disarms berserk.
 */
public final class BerserkEffect {
	private static final ResourceLocation DAMAGE_ID = JujutsuMod.id("curse_berserk_damage");
	private static final ResourceLocation SPEED_ID = JujutsuMod.id("curse_berserk_speed");

	private BerserkEffect() {
	}

	/** Post-damage latch check: call after {@code super.hurtServer} accepted the hit. */
	public static void maybeLatch(CursedSpiritEntity spirit, CursedSpiritGrade grade, long now) {
		if (spirit.berserkLatched()
				|| !spirit.abilityBrain().pool().contains(CursedSpiritAbilityId.BERSERK)) {
			return;
		}
		if (spirit.hpFraction() > CursedSpiritAbilityProfile.berserkThreshold()) {
			return;
		}
		spirit.setBerserkLatched(true);
		applyModifiers(spirit, grade);
		if (spirit.level() instanceof ServerLevel level) {
			level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_RELEASE);
			JujutsuNetworking.broadcastVfxCue(level, spirit.position(),
					CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
					VfxCues.anchored(CursedSpiritVfxIds.BERSERK, spirit.position(), spirit.getId(),
							spirit.position(), 2, now, spirit.getRandom().nextLong()),
					CursePerception::perceives);
		}
	}

	public static void applyModifiers(CursedSpiritEntity spirit, CursedSpiritGrade grade) {
		var params = CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.BERSERK, grade);
		AttributeInstance damage = spirit.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null && !damage.hasModifier(DAMAGE_ID)) {
			damage.addTransientModifier(new AttributeModifier(DAMAGE_ID, params.strength(),
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		AttributeInstance speed = spirit.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && !speed.hasModifier(SPEED_ID)) {
			speed.addTransientModifier(new AttributeModifier(SPEED_ID, params.speed(),
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	/** Re-applies transient modifiers from a persisted latch. No latch, no work. */
	public static void restoreIfLatched(CursedSpiritEntity spirit, CursedSpiritGrade grade) {
		if (spirit.berserkLatched()) {
			applyModifiers(spirit, grade);
		}
	}

	/** Test hook: modifier ids. */
	public static ResourceLocation damageModifierId() {
		return DAMAGE_ID;
	}

	public static ResourceLocation speedModifierId() {
		return SPEED_ID;
	}
}
