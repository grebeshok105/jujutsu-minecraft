package jujutsu.mod.cursedspirit;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.cursedspirit.CursedSpiritAttackPolicy.Phase;
import jujutsu.mod.cursedspirit.CursedSpiritAttackPolicy.StrikePlan;

/**
 * Melee brain for all three tiers: APPROACH the target, WINDUP, STRIKE (direct damage plus the
 * greater tier's AoE slam and the common tier's lunging step), RECOVER, repeat. Every number comes
 * from the tier's {@link CursedSpiritTierStats} row via {@link CursedSpiritAttackPolicy}.
 */
public class CursedSpiritAttackGoal extends Goal {
	private final CursedSpiritEntity mob;
	private final CursedSpiritTierStats stats;
	private Phase phase = Phase.APPROACH;
	private int ticksInPhase;

	public CursedSpiritAttackGoal(CursedSpiritEntity mob) {
		this.mob = mob;
		this.stats = CursedSpiritProfile.of(mob.tier());
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.currentVictim();
		return target != null && target.isAlive() && mob.hasLineOfSight(target);
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = mob.currentVictim();
		return target != null && target.isAlive();
	}

	@Override
	public void start() {
		phase = Phase.APPROACH;
		ticksInPhase = 0;
	}

	@Override
	public void stop() {
		// A lost target mid-swing must not freeze the attack clip on: close the animation.
		if (phase != Phase.APPROACH) {
			mob.endAttackAnim();
			broadcast(CursedSpiritEntity.ATTACK_END);
		}
		phase = Phase.APPROACH;
		ticksInPhase = 0;
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		LivingEntity target = mob.currentVictim();
		if (target == null || !target.isAlive()) {
			return;
		}
		// Stagger pauses the swing clock: the windup neither advances nor restarts, it waits.
		if (mob.level() instanceof ServerLevel level
				&& CombatStagger.GLOBAL.isStaggered(mob.getUUID(), level.getGameTime())) {
			return;
		}
		boolean inReach = CursedSpiritAttackPolicy.inReach(
				mob.distanceTo(target), mob.getBbWidth(), target.getBbWidth(), stats);
		// Increment-then-transition: WINDUP lasts exactly attackWindupTicks ticks before STRIKE,
		// and RECOVER exactly attackCooldownTicks before the next WINDUP.
		ticksInPhase++;
		Phase next = CursedSpiritAttackPolicy.advance(phase, ticksInPhase, inReach, stats);
		if (next == phase) {
			if (phase == Phase.APPROACH) {
				mob.getNavigation().moveTo(target, 1.0);
			} else if (phase == Phase.WINDUP) {
				mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
			}
			return;
		}
		enter(target, next);
	}

	private void enter(LivingEntity target, Phase next) {
		phase = next;
		ticksInPhase = 0;
		switch (next) {
			case WINDUP -> {
				mob.getNavigation().stop();
				mob.beginAttackAnim();
				broadcast(CursedSpiritEntity.ATTACK_START);
				mob.swing(InteractionHand.MAIN_HAND);
			}
			case STRIKE -> strike(target);
			case RECOVER -> {
				// The attack clip spans WINDUP through RECOVER; it closes when the cooldown ends.
			}
			case APPROACH -> {
				mob.endAttackAnim();
				broadcast(CursedSpiritEntity.ATTACK_END);
			}
		}
	}

	private void strike(LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		double step = stats.strikeStep();
		if (step > 0.0) {
			Vec3 forward = mob.getLookAngle().multiply(1.0, 0.0, 1.0);
			if (forward.lengthSqr() > 1.0E-6) {
				forward = forward.normalize();
				mob.setDeltaMovement(mob.getDeltaMovement().add(forward.scale(step)));
				mob.hurtMarked = true;
			}
		}
		target.hurtServer(level, level.damageSources().mobAttack(mob),
				CursedSpiritAttackPolicy.primaryDamage(stats));
		knock(target, stats.attackKnockback());
		if (stats.aoeRadius() > 0.0) {
			List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
					mob.getBoundingBox().inflate(stats.aoeRadius()),
					candidate -> candidate != mob && candidate.isAlive());
			StrikePlan plan = CursedSpiritAttackPolicy.strikeTargets(target, nearby,
					mob.getX(), mob.getY(), mob.getZ(), stats);
			for (LivingEntity splash : plan.aoe()) {
				splash.hurtServer(level, level.damageSources().mobAttack(mob),
						CursedSpiritAttackPolicy.aoeDamage(stats));
				knock(splash, stats.aoeKnockback());
			}
		}
		// STRIKE lasts exactly one tick: the policy advances to RECOVER on the next tick.
		phase = Phase.RECOVER;
		ticksInPhase = 0;
	}

	private void knock(LivingEntity victim, double strength) {
		if (strength <= 0.0) {
			return;
		}
		Vec3 push = victim.position().subtract(mob.position());
		double horizontal = Math.sqrt(push.x * push.x + push.z * push.z);
		if (horizontal < 1.0E-6) {
			push = new Vec3(mob.getLookAngle().x, 0.0, mob.getLookAngle().z);
			horizontal = Math.sqrt(push.x * push.x + push.z * push.z);
			if (horizontal < 1.0E-6) {
				push = new Vec3(0.0, 0.0, 1.0);
				horizontal = 1.0;
			}
		}
		victim.knockback(strength, -push.x / horizontal, -push.z / horizontal);
	}

	private void broadcast(byte id) {
		mob.level().broadcastEntityEvent(mob, id);
	}
}
