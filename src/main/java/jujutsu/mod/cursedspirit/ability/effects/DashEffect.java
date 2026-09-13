package jujutsu.mod.cursedspirit.ability.effects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityBrain;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityParams;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Fixed-direction dash (Block 3, Step 3): one impulse along the frozen line to the target at
 * start, a flight window from game time, collision damage, a telegraph cue. The direction is
 * never re-aimed mid-flight — a victim that leaves the line takes nothing (R52).
 */
public final class DashEffect {
	private DashEffect() {
	}

	public static boolean start(CursedSpiritEntity spirit, LivingEntity target, long now,
			CursedSpiritAbilityParams params, CursedSpiritAbilityBrain brain) {
		Vec3 direction = target.position().subtract(spirit.position());
		direction = new Vec3(direction.x, 0.0, direction.z);
		if (direction.lengthSqr() < 1.0E-6) {
			direction = new Vec3(spirit.getLookAngle().x, 0.0, spirit.getLookAngle().z);
		}
		if (direction.lengthSqr() < 1.0E-6) {
			return false;
		}
		direction = direction.normalize();
		if (!brain.tryStart(CursedSpiritAbilityId.DASH, now + params.durationTicks(), params,
				target.getUUID(), now)) {
			return false;
		}
		spirit.setDeltaMovement(direction.scale(params.speed()));
		spirit.hurtMarked = true;
		ServerLevel level = (ServerLevel) spirit.level();
		level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_WINDUP);
		JujutsuNetworking.broadcastVfxCue(level, spirit.position(),
				CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixedDirected(CursedSpiritVfxIds.DASH, spirit.position(), 1, now,
						spirit.getRandom().nextLong(), direction),
				CursePerception::perceives);
		return true;
	}

	/** Collision check against the frozen target only; the window ends on hit or timeout. */
	public static void tick(CursedSpiritEntity spirit, ServerLevel level,
			CursedSpiritAbilityBrain brain, CursedSpiritAbilityBrain.EffectState state, long now) {
		if (!(level.getEntity(state.targetUuid()) instanceof LivingEntity target) || !target.isAlive()) {
			return;
		}
		double hitRadius = state.params().radius() + target.getBbWidth() * 0.5;
		if (spirit.distanceTo(target) > hitRadius) {
			return;
		}
		if (!CursePerception.mayTouch(spirit, target)) {
			brain.forceEnd(CursedSpiritAbilityId.DASH);
			return;
		}
		target.hurtServer(level, level.damageSources().mobAttack(spirit),
				(float) state.params().damage());
		Vec3 push = target.position().subtract(spirit.position());
		double horizontal = Math.sqrt(push.x * push.x + push.z * push.z);
		if (horizontal > 1.0E-6) {
			target.push(push.x / horizontal * state.params().strength(), 0.1,
					push.z / horizontal * state.params().strength());
		}
		level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_RELEASE);
		brain.forceEnd(CursedSpiritAbilityId.DASH);
	}
}
