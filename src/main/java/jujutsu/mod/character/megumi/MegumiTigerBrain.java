package jujutsu.mod.character.megumi;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;

/**
 * Tiger Funeral behaviour: STALK → COMBO_WINDUP → STRIKE_1 → STRIKE_2 → FINISHER → RECOVERY →
 * STALK. The combo is committed: once the windup starts the locked {@code comboTargetUuid} and the
 * facing frozen at windup end decide every beat — the body never re-aims between beats, never
 * teleports, and never retargets; a miss is a miss. A mark landing mid-combo (manual sic,
 * retaliation, coordinator) is recorded by the runtime but ignored until recovery ends, then the
 * latest {@code sicTargetUuid} wins naturally.
 *
 * <p>All damage is brain-driven — the entity registers no {@code MeleeAttackGoal} — so every hit
 * is a resolved beat, never a free contact swing.
 */
final class MegumiTigerBrain {
	private static final String ACTION_KEY = "tiger_combo";

	/** Action-layer indices on DATA_COMBO_BEAT, mirroring the frozen client contract. */
	private static final int ACTION_NONE = 0;
	private static final int ACTION_WINDUP = 1;
	private static final int ACTION_RECOVER = 5;

	private MegumiTigerBrain() {}

	static void tick(ServerLevel level, ServerPlayer owner, MegumiShikigamiPack pack,
			MegumiTigerEntity tiger, long gameTime) {
		tiger.advanceStateTicks();
		switch (tiger.state()) {
			case STALK -> tickStalk(level, tiger, gameTime);
			case COMBO_WINDUP -> tickWindup(level, tiger, gameTime);
			case STRIKE_1 -> tickBeat(level, owner, tiger, gameTime, MegumiTigerPolicy.Strike.STRIKE_1);
			case STRIKE_2 -> tickBeat(level, owner, tiger, gameTime, MegumiTigerPolicy.Strike.STRIKE_2);
			case FINISHER -> tickBeat(level, owner, tiger, gameTime, MegumiTigerPolicy.Strike.FINISHER);
			case RECOVERY -> tickRecovery(tiger, gameTime);
		}
	}

	/**
	 * Stalk/approach: while a mark lives inside the engage range the brain drives the move
	 * control straight at it — no navigation, no melee goal. In reach and off cooldown, the
	 * windup commits. Out of range or cooling down, the body simply holds its ground and the
	 * follow goals own the feet.
	 */
	private static void tickStalk(ServerLevel level, MegumiTigerEntity tiger, long gameTime) {
		LivingEntity target = resolve(level, tiger.sicTargetUuid());
		if (target == null) {
			return;
		}
		double distance = target.position().subtract(tiger.position()).horizontalDistance();
		if (distance > MegumiShikigamiProfile.TIGER_APPROACH_RANGE) {
			return;
		}
		faceTarget(tiger, target);
		if (distance > MegumiShikigamiProfile.TIGER_APPROACH_STOP) {
			// Navigation stays stopped so the follow goal's occasional re-path cannot steal a tick
			// of the stalk: this write is the only wanted position the move control sees.
			tiger.getNavigation().stop();
			tiger.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.0);
			return;
		}
		if (gameTime < tiger.nextComboGameTime()) {
			tiger.getNavigation().stop();
			return;
		}
		enterWindup(tiger, target, gameTime);
	}

	/** Windup entry: the lock lands here — target identity for the whole sequence. */
	private static void enterWindup(MegumiTigerEntity tiger, LivingEntity target, long gameTime) {
		tiger.lockComboTarget(target.getUUID());
		tiger.enterComboState(MegumiTigerEntity.TigerState.COMBO_WINDUP, ACTION_WINDUP);
		tiger.setResolveGameTime(gameTime + MegumiTigerPolicy.windupTicksFor());
		// The combo owns the body until recovery ends: vanilla targeting, navigation and the goal
		// set all drop so the beats resolve against the lock, not whoever wandered into view.
		tiger.setTarget(null);
		tiger.getNavigation().stop();
		tiger.setNoAi(true);
	}

	/** The telegraph: still aimable — the facing only freezes when the first strike launches. */
	private static void tickWindup(ServerLevel level, MegumiTigerEntity tiger, long gameTime) {
		if (gameTime < tiger.resolveGameTime()) {
			LivingEntity target = resolve(level, tiger.comboTargetUuid());
			if (target != null) {
				faceTarget(tiger, target);
			}
			return;
		}
		// Windup end freezes the facing every beat resolves against.
		tiger.setComboYawDeg(tiger.getYRot());
		enterStrike(tiger, gameTime, MegumiTigerPolicy.Strike.STRIKE_1);
	}

	private static void enterStrike(MegumiTigerEntity tiger, long gameTime, MegumiTigerPolicy.Strike strike) {
		tiger.enterComboState(stateFor(strike), beatActionIndex(strike));
		tiger.setResolveGameTime(gameTime + MegumiTigerPolicy.resolveTicksFor(strike));
	}

	/**
	 * One beat per resolveGameTime: the hit test runs against the frozen facing and the locked
	 * target — alive, same level, in range, in arc — and the policy decides whether a miss keeps
	 * the swings coming (target present) or drops the combo (target gone).
	 */
	private static void tickBeat(ServerLevel level, ServerPlayer owner, MegumiTigerEntity tiger,
			long gameTime, MegumiTigerPolicy.Strike strike) {
		if (gameTime < tiger.resolveGameTime()) {
			return;
		}
		LivingEntity target = resolve(level, tiger.comboTargetUuid());
		boolean targetPresent = target != null;
		double distance = 0.0;
		double yawDeltaDeg = 0.0;
		if (targetPresent) {
			Vec3 delta = target.position().subtract(tiger.position());
			distance = delta.horizontalDistance();
			yawDeltaDeg = Mth.wrapDegrees(bearingDeg(delta) - tiger.comboYawDeg());
		}
		boolean connects = MegumiTigerPolicy.strikeConnects(new MegumiTigerPolicy.ComboFacts(
				strike, targetPresent, targetPresent, distance, yawDeltaDeg));
		if (connects) {
			connect(level, owner, tiger, target, strike, gameTime);
		} else {
			miss(level, owner, tiger, strike, gameTime);
		}
		if (MegumiTigerPolicy.afterBeat(strike, connects, targetPresent) == MegumiTigerPolicy.ComboAction.NEXT_BEAT) {
			enterStrike(tiger, gameTime, strike.next());
		} else {
			enterRecovery(level, owner, tiger, gameTime);
		}
	}

	private static void connect(ServerLevel level, ServerPlayer owner, MegumiTigerEntity tiger,
			LivingEntity target, MegumiTigerPolicy.Strike strike, long gameTime) {
		DamageSource source = owner != null
				? level.damageSources().playerAttack(owner)
				: level.damageSources().mobAttack(tiger);
		target.hurtServer(level, source, (float) MegumiTigerPolicy.damageFor(strike));
		CombatStagger.GLOBAL.apply(target, gameTime, MegumiTigerPolicy.staggerTicksFor(strike));
		Vec3 facing = facingVector(tiger.comboYawDeg());
		target.knockback(MegumiTigerPolicy.knockbackFor(strike), -facing.x, -facing.z);
		double lift = MegumiTigerPolicy.liftFor(strike);
		if (lift > 0.0) {
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, lift, 0.0));
			target.hurtMarked = true;
		}
		MegumiShikigamiRuntime.broadcastCue(level, owner,
				strikeCue(target.position(), tiger.getId(), tiger.position(), strike.beat(),
						gameTime, level.getRandom().nextLong(), facing));
		level.playSound(null, tiger.getX(), tiger.getY(), tiger.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
				SoundSource.NEUTRAL, 0.8f, 1.0f);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PHANTOM_BITE,
				SoundSource.NEUTRAL, 0.75f, 1.05f);
		if (strike == MegumiTigerPolicy.Strike.FINISHER) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE,
					SoundSource.NEUTRAL, 0.45f, 1.35f);
			level.playSound(null, tiger.getX(), tiger.getY(), tiger.getZ(), SoundEvents.WITHER_SHOOT,
					SoundSource.NEUTRAL, 0.55f, 1.1f);
		}
	}

	private static void miss(ServerLevel level, ServerPlayer owner, MegumiTigerEntity tiger,
			MegumiTigerPolicy.Strike strike, long gameTime) {
		tiger.markComboWhiffed();
		Vec3 reach = tiger.position()
				.add(facingVector(tiger.comboYawDeg()).scale(MegumiTigerPolicy.rangeFor(strike)));
		MegumiShikigamiRuntime.broadcastCue(level, owner,
				MegumiShikigamiRuntime.directedCue(MegumiVfxIds.TIGER_MISS, reach, tiger.getId(),
						tiger.position(), 1, gameTime, level.getRandom().nextLong(),
						facingVector(tiger.comboYawDeg())));
		level.playSound(null, tiger.getX(), tiger.getY(), tiger.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
				SoundSource.NEUTRAL, 0.45f, 0.9f);
	}

	/** Recovery entry: the commit's price — a cooldown, a cue, and the whiff on the record. */
	private static void enterRecovery(ServerLevel level, ServerPlayer owner, MegumiTigerEntity tiger,
			long gameTime) {
		tiger.enterComboState(MegumiTigerEntity.TigerState.RECOVERY, ACTION_RECOVER);
		tiger.setResolveGameTime(gameTime + MegumiTigerPolicy.recoveryTicks());
		tiger.setNextComboGameTime(gameTime + MegumiTigerPolicy.comboCooldownTicks());
		if (tiger.comboWhiffed()) {
			MegumiFailureMemory.recordFailure(tiger.getUUID(), ACTION_KEY, gameTime);
		}
		MegumiShikigamiRuntime.broadcastCue(level, owner, MegumiVfxIds.TIGER_RECOVER,
				tiger.position(), tiger.getId(), Vec3.ZERO);
		level.playSound(null, tiger.getX(), tiger.getY(), tiger.getZ(), SoundEvents.POLAR_BEAR_WARNING,
				SoundSource.NEUTRAL, 0.35f, 0.85f);
	}

	private static void tickRecovery(MegumiTigerEntity tiger, long gameTime) {
		if (gameTime < tiger.resolveGameTime()) {
			return;
		}
		tiger.enterComboState(MegumiTigerEntity.TigerState.STALK, ACTION_NONE);
		tiger.clearComboTarget();
		// Goals resume exactly as Nue's dive ends — NoAI clears only on an ACTIVE body — and the
		// vanilla target re-arms from whatever mark the runtime last wrote (a mid-combo sic is a
		// pending order now, not a stale one).
		tiger.setNoAi(!tiger.combatEnabled());
		ServerLevel level = (ServerLevel) tiger.level();
		LivingEntity standing = resolve(level, tiger.sicTargetUuid());
		if (standing != null) {
			tiger.setTarget(standing);
		}
	}

	private static MegumiTigerEntity.TigerState stateFor(MegumiTigerPolicy.Strike strike) {
		return switch (strike) {
			case STRIKE_1 -> MegumiTigerEntity.TigerState.STRIKE_1;
			case STRIKE_2 -> MegumiTigerEntity.TigerState.STRIKE_2;
			case FINISHER -> MegumiTigerEntity.TigerState.FINISHER;
		};
	}

	/** The action-layer index a beat holds for the client: strike_1 = 2, strike_2 = 3, finisher = 4. */
	private static int beatActionIndex(MegumiTigerPolicy.Strike strike) {
		return strike.ordinal() + 2;
	}

	/**
	 * The production strike payload factory — same seam shape as {@code MegumiNueBrain.shockCue}:
	 * the impact stays immutable in {@code origin}, the tiger id is the live endpoint, and the
	 * beat index rides in {@code intensity} so the client sizes the slash by beat.
	 */
	static VfxCue strikeCue(Vec3 targetPos, int tigerId, Vec3 tigerPos, int beat,
			long gameTime, long seed, Vec3 facing) {
		return MegumiShikigamiRuntime.directedCue(MegumiVfxIds.TIGER_STRIKE, targetPos, tigerId,
				tigerPos, beat, gameTime, seed, facing);
	}

	private static void faceTarget(MegumiTigerEntity tiger, LivingEntity target) {
		Vec3 delta = target.getEyePosition().subtract(tiger.getEyePosition());
		if (delta.horizontalDistanceSqr() < 1.0E-8) {
			return;
		}
		float yaw = (float) bearingDeg(delta);
		tiger.setYRot(yaw);
		tiger.yBodyRot = yaw;
		tiger.yHeadRot = yaw;
	}

	/** Yaw convention shared with {@code faceTarget}: atan2(-dx, dz) in degrees. */
	static double bearingDeg(Vec3 delta) {
		return Math.toDegrees(Math.atan2(-delta.x, delta.z));
	}

	/** Unit vector along a yaw in the same convention: yaw 0 faces +z, yaw -90 faces +x. */
	private static Vec3 facingVector(float yawDeg) {
		double radians = Math.toRadians(yawDeg);
		return new Vec3(-Math.sin(radians), 0.0, Math.cos(radians));
	}

	private static LivingEntity resolve(ServerLevel level, UUID id) {
		return id != null && level.getEntity(id) instanceof LivingEntity living
				&& living.isAlive() && !living.isRemoved() && living.level() == level
				? living : null;
	}
}
