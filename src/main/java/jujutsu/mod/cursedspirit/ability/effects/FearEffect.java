package jujutsu.mod.cursedspirit.ability.effects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityBrain;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityParams;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Fear (Block 3, Step 7), server half: {@code DARKNESS} + {@code NAUSEA} + the
 * {@code CURSED_FEAR} marker, all for the grade duration from the profile (never
 * {@code * grade}). The client half (input inversion) keys off the marker only.
 *
 * <p>Applied debuffs run their vanilla course even if the victim stops perceiving
 * mid-duration (vessel switch): expiry, not a tick hook, clears them — accepted, the
 * window itself never opens for a non-perceiver.
 */
public final class FearEffect {
	private FearEffect() {
	}

	public static boolean applyTo(CursedSpiritEntity spirit, LivingEntity target, long now,
			CursedSpiritAbilityParams params, CursedSpiritAbilityBrain brain) {
		if (!(spirit.level() instanceof ServerLevel level)) {
			return false;
		}
		// DARKNESS/NAUSEA/CURSED_FEAR are inert on non-players: casting on a mob
		// attacker spends the window for nothing (same gate shape as GRAB_RUNNER).
		if (!(target instanceof Player)) {
			return false;
		}
		// Issue #80: fear is control — never lands on a non-perceiving player.
		if (!CursePerception.mayTouch(spirit, target)) {
			return false;
		}
		// Cast range is the profile radius (FEAR.radius), read here as well as in the
		// decider so a direct call cannot snipe across the biome.
		if (spirit.distanceTo(target) > params.radius()) {
			return false;
		}
		if (!brain.tryStart(CursedSpiritAbilityId.FEAR, now + CursedSpiritAbilityProfile.FEAR_CAST_TICKS,
				params, target.getUUID(), now)) {
			return false;
		}
		int duration = params.durationTicks();
		target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, duration, 0, false, true, true),
				spirit);
		target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, duration, 0, false, true, true),
				spirit);
		target.addEffect(new MobEffectInstance(JujutsuEffects.CURSED_FEAR, duration, 0, false, false,
				false), spirit);
		level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_WINDUP);
		JujutsuNetworking.broadcastVfxCue(level, target.position(),
				CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.anchored(CursedSpiritVfxIds.FEAR, target.position(), target.getId(),
						target.position(), 1, now, spirit.getRandom().nextLong()),
				CursePerception::perceives);
		return true;
	}
}
