package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.vfx.MegumiVfxIds;

/** Server-authoritative, locked-target combo for Tiger Funeral's authorial grounded melee kit. */
final class MegumiTigerBrain {
	private MegumiTigerBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiTigerEntity tiger, long gameTime) {
		if (!tiger.combatEnabled()) {
			return;
		}
		if (tiger.comboState() == MegumiTigerPolicy.State.RECOVERY) {
			tickRecovery(tiger, gameTime);
			return;
		}
		if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level) {
			if (isCommitted(tiger.comboState())) {
				enterRecovery(tiger, gameTime, MegumiTigerPolicy.Event.TARGET_INVALID);
			} else {
				tiger.getNavigation().stop();
			}
			return;
		}
		if (tiger.comboState() == MegumiTigerPolicy.State.STALK_APPROACH) {
			tickApproach(level, owner, tiger, gameTime);
			return;
		}
		tickCommitted(level, owner, tiger, gameTime);
	}

	private static void tickApproach(ServerLevel level, ServerPlayer owner,
			MegumiTigerEntity tiger, long gameTime) {
		UUID markedUuid = tiger.sicTargetUuid();
		LivingEntity target = resolve(level, markedUuid);
		if (!isValidTarget(owner, level, tiger, target)) {
			tiger.getNavigation().stop();
			return;
		}
		tiger.setXRot(0.0f);
		tiger.setYRot(MegumiShikigamiEntity.yawTowards(tiger.position(), target.position()));
		tiger.setYHeadRot(tiger.getYRot());
		boolean withinApproachRange = tiger.distanceToSqr(target)
				<= MegumiShikigamiProfile.TIGER_APPROACH_RANGE * MegumiShikigamiProfile.TIGER_APPROACH_RANGE;
		boolean inStrikeArc = inStrikeArc(tiger, target);
		MegumiTigerPolicy.ComboStartFacts facts = new MegumiTigerPolicy.ComboStartFacts(
				tiger.combatEnabled(), tiger.attackReady(gameTime), target.isAlive(), target.isRemoved(),
				MegumiSummonRuntime.isEligibleTarget(owner, target), withinApproachRange, inStrikeArc);
		if (!withinApproachRange) {
			tiger.getNavigation().moveTo(target, 1.0);
			return;
		}
		if (!MegumiTigerPolicy.canStart(facts)) {
			tiger.getNavigation().moveTo(target, 1.0);
			return;
		}
		tiger.beginCombo(target.getUUID(), gameTime);
		playSound(level, tiger, 0.72f, 0.82f);
	}

	private static void tickCommitted(ServerLevel level, ServerPlayer owner,
			MegumiTigerEntity tiger, long gameTime) {
		MegumiTigerPolicy.State state = tiger.comboState();
		if (!isCommitted(state)) {
			return;
		}
		if (gameTime > tiger.comboDeadlineGameTime()) {
			enterRecovery(tiger, gameTime, MegumiTigerPolicy.Event.TARGET_INVALID);
			return;
		}
		LivingEntity target = resolve(level, tiger.comboTargetUuid());
		if (!isValidTarget(owner, level, tiger, target)) {
			enterRecovery(tiger, gameTime, MegumiTigerPolicy.Event.TARGET_INVALID);
			return;
		}
		tiger.setDeltaMovement(Vec3.ZERO);

		if (state == MegumiTigerPolicy.State.COMBO_WINDUP) {
			if (MegumiTigerPolicy.hitWindowReached(state, tiger.actionTicks())) {
				tiger.enterComboState(MegumiTigerPolicy.nextState(state,
						MegumiTigerPolicy.Event.WINDUP_COMPLETE), gameTime);
			}
			return;
		}
		if (!MegumiTigerPolicy.hitWindowReached(state, tiger.actionTicks())) {
			return;
		}

		int expectedStrike = switch (state) {
			case STRIKE_1 -> 0;
			case STRIKE_2 -> 1;
			case FINISHER -> 2;
			default -> -1;
		};
		if (expectedStrike < 0 || tiger.strikeIndex() != expectedStrike) {
			enterRecovery(tiger, gameTime, MegumiTigerPolicy.Event.TARGET_INVALID);
			return;
		}

		MegumiTigerPolicy.StrikeOutcome outcome = MegumiTigerPolicy.strikeOutcome(
				new MegumiTigerPolicy.StrikeFacts(true, target.isAlive(), target.isRemoved(),
						MegumiSummonRuntime.isEligibleTarget(owner, target), inStrikeArc(tiger, target)));
		if (outcome == MegumiTigerPolicy.StrikeOutcome.CANCEL) {
			enterRecovery(tiger, gameTime, MegumiTigerPolicy.Event.TARGET_INVALID);
			return;
		}
		if (outcome == MegumiTigerPolicy.StrikeOutcome.MISS) {
			// A miss spends this authored beat: sound only, no movement, effects, or substitution.
			playSound(level, tiger, 0.38f, 1.28f);
		} else {
			applyStrike(level, owner, tiger, target, state, gameTime);
		}
		tiger.consumeStrike();
		MegumiTigerPolicy.Event nextEvent = state == MegumiTigerPolicy.State.FINISHER
				? MegumiTigerPolicy.Event.FINISHER_COMPLETE
				: MegumiTigerPolicy.Event.STRIKE_COMPLETE;
		tiger.enterComboState(MegumiTigerPolicy.nextState(state, nextEvent), gameTime);
	}

	private static void tickRecovery(MegumiTigerEntity tiger, long gameTime) {
		tiger.setDeltaMovement(Vec3.ZERO);
		if (tiger.actionTicks() <= 0) {
			tiger.enterComboState(MegumiTigerPolicy.nextState(MegumiTigerPolicy.State.RECOVERY,
					MegumiTigerPolicy.Event.RECOVERY_COMPLETE), gameTime);
		}
	}

	private static void applyStrike(ServerLevel level, ServerPlayer owner, MegumiTigerEntity tiger,
			LivingEntity target, MegumiTigerPolicy.State state, long gameTime) {
		float damage;
		int stagger;
		boolean finisher = state == MegumiTigerPolicy.State.FINISHER;
		switch (state) {
			case STRIKE_1 -> {
				damage = MegumiShikigamiProfile.TIGER_STRIKE1_DAMAGE;
				stagger = MegumiShikigamiProfile.TIGER_STRIKE1_STAGGER;
			}
			case STRIKE_2 -> {
				damage = MegumiShikigamiProfile.TIGER_STRIKE2_DAMAGE;
				stagger = MegumiShikigamiProfile.TIGER_STRIKE2_STAGGER;
			}
			case FINISHER -> {
				damage = MegumiShikigamiProfile.TIGER_FINISHER_DAMAGE;
				stagger = MegumiShikigamiProfile.TIGER_STRIKE2_STAGGER;
			}
			default -> throw new IllegalArgumentException("not a Tiger strike: " + state);
		}
		playSound(level, tiger, finisher ? 1.0f : 0.72f, finisher ? 0.64f : 0.92f);
		if (!target.hurtServer(level, level.damageSources().mobAttack(tiger), damage)) {
			return;
		}
		CombatStagger.GLOBAL.apply(target, gameTime, stagger);
		if (finisher) {
			applyFinisherKnockback(tiger, target);
		}
		MegumiShikigamiRuntime.broadcastCue(level, owner,
				finisher ? MegumiVfxIds.TIGER_FINISHER : MegumiVfxIds.TIGER_STRIKE,
				target.position(), target.getId(), new Vec3(0.0, target.getBbHeight() * 0.5, 0.0));
	}

	private static void applyFinisherKnockback(MegumiTigerEntity tiger, LivingEntity target) {
		Vec3 direction = target.position().subtract(tiger.position()).multiply(1.0, 0.0, 1.0);
		if (direction.lengthSqr() < 1.0E-8) {
			direction = tiger.getLookAngle().multiply(1.0, 0.0, 1.0);
		}
		if (direction.lengthSqr() < 1.0E-8) {
			return;
		}
		direction = direction.normalize();
		target.knockback(MegumiShikigamiProfile.TIGER_FINISHER_KNOCKBACK, -direction.x, -direction.z);
		Vec3 velocity = target.getDeltaMovement();
		target.setDeltaMovement(velocity.x, Math.max(velocity.y, 0.35), velocity.z);
		target.hasImpulse = true;
	}

	private static boolean inStrikeArc(MegumiTigerEntity tiger, LivingEntity target) {
		return MegumiTigerPolicy.inStrikeArc(tiger.position(), tiger.getLookAngle(), target.getBoundingBox(),
				MegumiShikigamiProfile.TIGER_STRIKE_RANGE,
				MegumiShikigamiProfile.TIGER_STRIKE_MIN_DOT,
				MegumiShikigamiProfile.TIGER_STRIKE_VERTICAL);
	}

	private static boolean isValidTarget(ServerPlayer owner, ServerLevel level,
			MegumiTigerEntity tiger, LivingEntity target) {
		return owner != null && target != null && target != tiger
				&& target.isAlive() && !target.isRemoved() && target.level() == level
				&& MegumiSummonRuntime.isEligibleTarget(owner, target);
	}

	private static LivingEntity resolve(ServerLevel level, UUID targetUuid) {
		if (targetUuid == null) {
			return null;
		}
		Entity entity = level.getEntity(targetUuid);
		return entity instanceof LivingEntity living ? living : null;
	}

	private static boolean isCommitted(MegumiTigerPolicy.State state) {
		return state == MegumiTigerPolicy.State.COMBO_WINDUP
				|| state == MegumiTigerPolicy.State.STRIKE_1
				|| state == MegumiTigerPolicy.State.STRIKE_2
				|| state == MegumiTigerPolicy.State.FINISHER;
	}

	private static void enterRecovery(MegumiTigerEntity tiger, long gameTime,
			MegumiTigerPolicy.Event event) {
		MegumiTigerPolicy.State next = MegumiTigerPolicy.nextState(tiger.comboState(), event);
		if (next == MegumiTigerPolicy.State.RECOVERY) {
			tiger.enterComboState(next, gameTime);
		}
	}

	private static void playSound(ServerLevel level, MegumiTigerEntity tiger, float volume, float pitch) {
		level.playSound(null, tiger.getX(), tiger.getY(), tiger.getZ(), SoundEvents.RAVAGER_ATTACK,
				SoundSource.NEUTRAL, volume, pitch);
	}
}
