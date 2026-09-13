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
import jujutsu.mod.cursedspirit.perception.CursePerception;
/**
 * Melee brain for all three tiers: APPROACH the target, WINDUP, STRIKE (direct damage plus the
 * greater tier's AoE slam and the common tier's lunging step), RECOVER, repeat. Every number comes
 * from the tier's {@link CursedSpiritTierStats} row via {@link CursedSpiritAttackPolicy}.
 */
public class CursedSpiritAttackGoal extends Goal {
	private final CursedSpiritEntity mob;
	private Phase phase = Phase.APPROACH;
	private int ticksInPhase;

	public CursedSpiritAttackGoal(CursedSpiritEntity mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}
	// Read live, never cached: the goal is constructed in registerGoals(), which the
	// superclass constructor runs BEFORE the entity's tier field is assigned — touching the
	// profile in the constructor NPEs on every spawn (registerGoals-then-field order).
	private CursedSpiritTierStats stats() {
		return CursedSpiritProfile.of(mob.tier());
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.currentVictim();
		return target != null && target.isAlive() && mob.hasLineOfSight(target);
	}

	/**
	 * Deliberately looser than {@link #canUse()}: a momentary line-of-sight blip mid-swing must
	 * not abort the strike (the default would delegate to {@code canUse()} and {@link #stop()}
	 * would cut the attack clip). Only a dead or gone target ends the swing.
	 */
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
				mob.distanceTo(target), mob.getBbWidth(), target.getBbWidth(), stats());
		// Block 3 (#86): ability movement owns navigation. While any takesMovement window is
		// open the goal neither approaches nor retreats; retreat (regen) outranks the melee
		// approach but never a movement owner.
		long now = mob.level().getGameTime();
		boolean moveOwned = mob.abilityBrain().movementOwned(now);
		boolean retreat = !moveOwned && mob.abilityBrain().shouldRetreat(now, true);
		// Increment-then-transition: WINDUP lasts exactly attackWindupTicks ticks before STRIKE,
		// and RECOVER exactly attackCooldownTicks before the next WINDUP.
		ticksInPhase++;
		Phase next = CursedSpiritAttackPolicy.advance(phase, ticksInPhase, inReach, stats());
		if (next == phase) {
			if (phase == Phase.APPROACH) {
				if (moveOwned) {
					mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
				} else if (retreat) {
					retreatFrom(target);
				} else {
					mob.getNavigation().moveTo(target, 1.0);
				}
				mob.abilityBrain().decideInCombat(mob, mob.grade(), target, now);
			} else if (phase == Phase.WINDUP) {
				mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
			} else if (phase == Phase.RECOVER) {
				mob.abilityBrain().decideInCombat(mob, mob.grade(), target, now);
			}
			return;
		}
		enter(target, next, now);
	}
	private void enter(LivingEntity target, Phase next, long now) {
		// Block 3 (#86): no new WINDUP while an ability owns the attack clip.
		if (next == Phase.WINDUP && mob.abilityBrain().attackClipOccupied(now)) {
			phase = Phase.APPROACH;
			ticksInPhase = 0;
			return;
		}
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

	/**
	 * Regen retreat (Block 3, #86): walk away from the target while the HoT runs. A plain
	 * navigation move inside the existing goal — never a second goal.
	 */
	private void retreatFrom(LivingEntity target) {
		Vec3 away = mob.position().subtract(target.position());
		away = new Vec3(away.x, 0.0, away.z);
		if (away.lengthSqr() < 1.0E-6) {
			away = new Vec3(mob.getLookAngle().x, 0.0, mob.getLookAngle().z);
		}
		if (away.lengthSqr() < 1.0E-6) {
			return;
		}
		away = away.normalize();
		Vec3 spot = mob.position().add(away.scale(8.0));
		mob.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
		mob.getLookControl().setLookAt(target, 30.0f, 30.0f);
	}

	private void strike(LivingEntity target) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		CursedSpiritTierStats row = stats();
		// Strike-time reach re-check: the windup clock alone enters STRIKE, so a victim that
		// sprints out mid-swing must take no direct damage and no knockback. Deliberately reach
		// only, no line-of-sight re-check (see canContinueToUse): a momentary sight blip must not
		// void the swing, but distance does. A whiff still runs out the swing clock through
		// RECOVER, and the slam below still lands — the swing is never empty (issue #85).
		if (CursedSpiritAttackPolicy.inReach(
				mob.distanceTo(target), mob.getBbWidth(), target.getBbWidth(), row)) {
			double step = row.strikeStep();
			if (step > 0.0) {
				Vec3 forward = mob.getLookAngle().multiply(1.0, 0.0, 1.0);
				if (forward.lengthSqr() > 1.0E-6) {
					forward = forward.normalize();
					mob.setDeltaMovement(mob.getDeltaMovement().add(forward.scale(step)));
					mob.hurtMarked = true;
				}
			}
		target.hurtServer(level, level.damageSources().mobAttack(mob),
				CursedSpiritAttackPolicy.primaryDamage(mob.gradeStats()));
			knock(target, row.attackKnockback());
		}
		// The slam is a ground impact centred on the body, not a targeted hit: whoever stands in
		// the crater takes the shockwave even when the tracked victim escaped reach (issue #85).
		// Tiers 1-2 carry radius 0, so their whiff behaviour is unchanged.
		if (row.aoeRadius() > 0.0) {
			// Issue #80: the shockwave spares non-perceiving players standing in the crater.
			// Curse↔curse and curse↔mob pairs always pass mayTouch, so the second spirit in
			// the blast still takes its damage (pinned by GameTest).
			List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
					mob.getBoundingBox().inflate(row.aoeRadius()),
					candidate -> candidate != mob && candidate.isAlive()
							&& CursePerception.mayTouch(mob, candidate));
			StrikePlan plan = CursedSpiritAttackPolicy.strikeTargets(target, nearby,
					mob.getX(), mob.getY(), mob.getZ(), row);
			for (LivingEntity splash : plan.aoe()) {
				splash.hurtServer(level, level.damageSources().mobAttack(mob),
						CursedSpiritAttackPolicy.aoeDamage(mob.gradeStats(), row));
				knock(splash, row.aoeKnockback());
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
