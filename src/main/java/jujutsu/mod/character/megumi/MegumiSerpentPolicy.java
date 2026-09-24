package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Pure Great Serpent numbers: who may be ambushed, where the body surfaces, how long a bind lasts
 * and when it snaps. The serpent has no swing — its whole attack is the bind, so every rule the
 * brain runs sits here in plain JUnit form (mirrors {@link MegumiToadPolicy}'s split).
 */
public final class MegumiSerpentPolicy {
	private MegumiSerpentPolicy() {}

	/**
	 * Consecutive SUBMERGED ticks with an unplaceable emerge point before the ambush is called
	 * (plan deviation 4.7 — the worker-owned constant): brief blockers (a closing door, a thrown
	 * block) should not cancel an ambush that is about to clear, a solid wall should.
	 */
	static final int SERPENT_EMERGE_ABORT_TICKS = 10;

	/**
	 * Extra reach past {@code SERPENT_AMBUSH_RANGE} a marked target may drift before the pending
	 * ambush is called (plan deviation 4.7's "range+slack"): a target that stepped one block out
	 * of range between the pick and the strike should not waste the whole sequence.
	 */
	static final double SERPENT_AMBUSH_ABORT_SLACK = 2.0;

	/** Ticks the sunken coil waits under its victim before it commits to the rise. */
	static final int SERPENT_SUBMERGED_DWELL_TICKS = 20;

	/** How long the release clip reads before the body settles into recovery. */
	static final int SERPENT_RELEASE_TICKS = 8;

	/** Post-release posture before normal following resumes. */
	static final int SERPENT_RECOVERY_TICKS = 20;

	/**
	 * Where the victim hangs while bound: this far in front of the body along its horizontal
	 * look, at the body's own feet level — the coil's mouth, mirroring the toad's grip offset.
	 */
	static final double SERPENT_MOUTH_OFFSET = 1.0;

	/**
	 * Who the body is allowed to ambush. {@code guarded} is the friendly-fire shield
	 * ({@link MegumiShikigamiFriendlyFire#isProtected}: owner, allies, own bodies, spectators);
	 * {@code ownSide} is the narrower same-team row that stays explicit so the refusal order —
	 * friendly and own-pack bodies first — is visible in the facts themselves.
	 */
	public record AmbushFacts(
			boolean hostile,
			boolean ownSide,
			boolean guarded,
			boolean eligible,
			boolean held,
			boolean ungrabbable,
			boolean mounted,
			boolean airborne,
			double distance) {}

	/**
	 * The ambush gate (plan §C): hostile AND not-own-side AND not-protected AND eligible AND not
	 * held AND not ungrabbable AND not mounted AND not airborne AND inside the ambush range.
	 * A non-hostile mark (a sic'd neutral) refuses — the serpent only works combat targets.
	 */
	public static boolean canAmbush(AmbushFacts facts) {
		return facts.hostile()
				&& !facts.ownSide()
				&& !facts.guarded()
				&& facts.eligible()
				&& !facts.held()
				&& !facts.ungrabbable()
				&& !facts.mounted()
				&& !facts.airborne()
				&& facts.distance() <= MegumiShikigamiProfile.SERPENT_AMBUSH_RANGE;
	}

	/**
	 * The same gate read against the abort slack instead of the pick range — a committed ambush
	 * tolerates a target that drifted slightly out of range, nothing more (deviation 4.7).
	 */
	public static boolean ambushStillHolds(AmbushFacts facts) {
		return facts.hostile()
				&& !facts.ownSide()
				&& !facts.guarded()
				&& facts.eligible()
				&& !facts.held()
				&& !facts.ungrabbable()
				&& !facts.mounted()
				&& !facts.airborne()
				&& facts.distance() <= MegumiShikigamiProfile.SERPENT_AMBUSH_RANGE
						+ SERPENT_AMBUSH_ABORT_SLACK;
	}

	/**
	 * The idle pick cadence: an unmarked serpent only reconsiders an ambush when
	 * {@code nextAmbushScanGameTime} has been reached — the scan walks the candidate pool, so it
	 * must not run every tick (SERPENT_AMBUSH_SCAN_TICKS is the reschedule).
	 */
	public static boolean ambushDue(long gameTime, long nextAmbushScanGameTime) {
		return gameTime >= nextAmbushScanGameTime;
	}

	/**
	 * Emerge points in contract order: the target's rear arc, straight behind first, then
	 * alternating left/right from narrow to wide ({@link MegumiShadowMovePolicy#REAR_ARC_DEGREES}
	 * try order is the contract — a point square behind the victim always beats a flank). Each
	 * entry is a <em>requested</em> point for {@link jujutsu.mod.combat.SafeBodyPlacement#find};
	 * the first that resolves a placeable point wins.
	 */
	public static List<Vec3> emergeCandidates(Vec3 targetPosition, float targetBodyYawDegrees,
			double rearOffset) {
		List<Vec3> points = new ArrayList<>(MegumiShadowMovePolicy.REAR_ARC_DEGREES.length);
		for (float arcOffset : MegumiShadowMovePolicy.REAR_ARC_DEGREES) {
			points.add(MegumiShadowMovePolicy.behindPoint(
					targetPosition, targetBodyYawDegrees, arcOffset, rearOffset));
		}
		return List.copyOf(points);
	}

	/**
	 * Where the bound victim is pinned: {@code SERPENT_MOUTH_OFFSET} blocks in front of the body
	 * along its horizontal look, at the body's own feet level. A zero-length look (impossible in
	 * play, reachable in a test) falls back to the body's position instead of a NaN direction.
	 */
	public static Vec3 mouthAnchor(Vec3 bodyPos, Vec3 bodyLook) {
		double dx = bodyLook.x;
		double dz = bodyLook.z;
		double lengthSqr = dx * dx + dz * dz;
		if (lengthSqr < 1.0E-8) {
			return bodyPos;
		}
		double length = Math.sqrt(lengthSqr);
		return new Vec3(bodyPos.x + dx / length * SERPENT_MOUTH_OFFSET,
				bodyPos.y, bodyPos.z + dz / length * SERPENT_MOUTH_OFFSET);
	}

	/**
	 * How long the bind holds, in ticks: heavier and bulkier victims are held shorter, the
	 * victim's hitbox volume only counts for non-players (a player should not lose bind time for
	 * being player-sized) and the result is clamped into the hard band so no input can produce a
	 * bind that is instantly over or endless.
	 */
	public static int bindTicksFor(double maxHealth, double volume, boolean isPlayer) {
		double ticks = MegumiShikigamiProfile.SERPENT_BIND_BASE_TICKS
				- maxHealth * MegumiShikigamiProfile.SERPENT_BIND_HP_PENALTY;
		if (!isPlayer) {
			ticks -= volume * MegumiShikigamiProfile.SERPENT_BIND_SIZE_PENALTY;
		}
		return clamp((int) Math.round(ticks),
				MegumiShikigamiProfile.SERPENT_BIND_MIN_TICKS,
				MegumiShikigamiProfile.SERPENT_BIND_MAX_TICKS);
	}

	/** A distance past the bind's break range snaps the coil (inclusive inside boundary). */
	public static boolean bindBroken(double distance, double range) {
		return distance > range;
	}

	/** What a bound tick resolves to, in release-matrix order. */
	public enum SerpentAction {
		/** Victim gone, dead, disconnected or across dimensions: drop the bind, no toss. */
		ABORT,
		/** Live exit — the timer ran out or a leash broke the coil: release with the toss. */
		RELEASE,
		/** Keep pinning. */
		HOLD
	}

	/** The facts one bound tick needs. */
	public record BindFacts(
			boolean victimPresent,
			boolean victimAlive,
			boolean victimDisconnected,
			boolean sameDimension,
			boolean expired,
			double ownerLeash,
			double victimDistance) {}

	/**
	 * The bound-tick decision: an absent/dead/disconnected/cross-dimension victim is released
	 * silently (the toss is for a live exit), an expired timer or a broken leash — owner-side
	 * (the coil is anchored to its caster) or victim-side (the victim was dragged or teleported
	 * out of the coil's reach) — releases with the toss. Anything else keeps pinning.
	 */
	public static SerpentAction bindAction(BindFacts facts) {
		if (!facts.victimPresent() || !facts.sameDimension()
				|| !facts.victimAlive() || facts.victimDisconnected()) {
			return SerpentAction.ABORT;
		}
		if (facts.expired()
				|| bindBroken(facts.ownerLeash(), MegumiShikigamiProfile.SERPENT_BIND_BREAK_RANGE)
				|| bindBroken(facts.victimDistance(), MegumiShikigamiProfile.SERPENT_BIND_BREAK_RANGE)) {
			return SerpentAction.RELEASE;
		}
		return SerpentAction.HOLD;
	}

	/**
	 * Toss direction on release: away from the owner, along the owner→serpent line, at exactly
	 * {@code SERPENT_TOSS_SPEED}, plus the lift upward. With the body on top of the owner the
	 * horizontal part is dropped (only the lift remains) rather than becoming a NaN.
	 */
	public static Vec3 tossVelocity(Vec3 ownerPos, Vec3 bodyPos) {
		double dx = bodyPos.x - ownerPos.x;
		double dz = bodyPos.z - ownerPos.z;
		double lengthSqr = dx * dx + dz * dz;
		if (lengthSqr < 1.0E-8) {
			return new Vec3(0.0, MegumiShikigamiProfile.SERPENT_TOSS_LIFT, 0.0);
		}
		double length = Math.sqrt(lengthSqr);
		return new Vec3(dx / length * MegumiShikigamiProfile.SERPENT_TOSS_SPEED,
				MegumiShikigamiProfile.SERPENT_TOSS_LIFT,
				dz / length * MegumiShikigamiProfile.SERPENT_TOSS_SPEED);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
