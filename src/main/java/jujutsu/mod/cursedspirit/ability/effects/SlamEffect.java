package jujutsu.mod.cursedspirit.ability.effects;

import java.util.List;
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
 * Ground slam (Block 3, Step 4): a jump impulse plus the landing shockwave on the existing
 * melee pattern. A member of the {@code takesMovement} group (jump impulse, then landing),
 * so it never overlaps dash/runner windows and melee navigation holds still while it runs.
 * The shockwave is body-centred: whoever stands in the crater takes it, including other
 * spirits; non-perceiving players take nothing (C1 gate).
 */
public final class SlamEffect {
	private SlamEffect() {
	}

	public static boolean start(CursedSpiritEntity spirit, LivingEntity target, long now,
			CursedSpiritAbilityParams params, CursedSpiritAbilityBrain brain) {
		Vec3 toward = target.position().subtract(spirit.position());
		toward = new Vec3(toward.x, 0.0, toward.z);
		if (toward.lengthSqr() > 1.0E-6) {
			toward = toward.normalize();
		} else {
			toward = Vec3.ZERO;
		}
		if (!brain.tryStart(CursedSpiritAbilityId.GROUND_SLAM, now + params.durationTicks(), params,
				target.getUUID(), now)) {
			return false;
		}
		spirit.setDeltaMovement(toward.scale(0.25).add(0.0, params.speed(), 0.0));
		spirit.hurtMarked = true;
		ServerLevel level = (ServerLevel) spirit.level();
		level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_WINDUP);
		JujutsuNetworking.broadcastVfxCue(level, spirit.position(),
				CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(CursedSpiritVfxIds.SLAM, spirit.position(), 1, now,
						spirit.getRandom().nextLong()),
				CursePerception::perceives);
		return true;
	}

	/**
	 * Detonates once the body is back on the ground past the launch ticks; the window is a
	 * timeout, not a fuse. Never writes attribution beyond the damage source: a shared
	 * velocity vector carries no source tag in any contract.
	 */
	public static void tick(CursedSpiritEntity spirit, ServerLevel level,
			CursedSpiritAbilityBrain brain, CursedSpiritAbilityBrain.EffectState state, long now) {
		if (!spirit.onGround() || now - state.startedGameTime() < 3) {
			return;
		}
		double radius = state.params().radius();
		List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
				spirit.getBoundingBox().inflate(radius), candidate -> {
					if (candidate == spirit || !candidate.isAlive()
							|| !CursePerception.mayTouch(spirit, candidate)) {
						return false;
					}
					double hitRadius = radius + candidate.getBbWidth() * 0.5;
					double dx = candidate.getX() - spirit.getX();
					double dz = candidate.getZ() - spirit.getZ();
					return dx * dx + dz * dz <= hitRadius * hitRadius;
				});
		for (LivingEntity victim : nearby) {
			victim.hurtServer(level, level.damageSources().mobAttack(spirit),
					(float) state.params().damage());
			Vec3 push = victim.position().subtract(spirit.position());
			double horizontal = Math.sqrt(push.x * push.x + push.z * push.z);
			if (horizontal > 1.0E-6) {
				victim.push(push.x / horizontal * state.params().strength(), 0.4,
						push.z / horizontal * state.params().strength());
			} else {
				victim.push(0.0, 0.4, 0.0);
			}
			// hurtMarked marks the velocity for sync: a ServerPlayer's movement is
			// client-authoritative, so a bare push() silently no-ops on the exact
			// victims the slam is aimed at (mobs were fine; players were not).
			victim.hurtMarked = true;
		}
		level.broadcastEntityEvent(spirit, CursedSpiritEntity.ABILITY_RELEASE);
		JujutsuNetworking.broadcastVfxCue(level, spirit.position(),
				CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(CursedSpiritVfxIds.SLAM, spirit.position(), 2, now,
						spirit.getRandom().nextLong()),
				CursePerception::perceives);
		brain.forceEnd(CursedSpiritAbilityId.GROUND_SLAM);
	}
}
