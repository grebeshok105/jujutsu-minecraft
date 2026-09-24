package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import jujutsu.mod.character.megumi.MegumiSerpentPolicy.AmbushFacts;
import jujutsu.mod.character.megumi.MegumiSerpentPolicy.BindFacts;
import jujutsu.mod.character.megumi.MegumiSerpentPolicy.SerpentAction;

/**
 * The serpent kit's pure half: ambush eligibility, emerge geometry, bind length, release
 * triggers and the toss. Every assertion here is about a number rule, never about a caller —
 * the brain's half is covered by the in-world game tests. Literal ticks/distance expectations
 * are deliberate (the §I literal-pin convention): a mutated profile constant must turn a row
 * red, not silently follow.
 */
class MegumiSerpentPolicyTest {
	private static final double EPS = 1.0E-9;
	/** A zombie's real measurements: 20 max-HP and a 0.6×1.95 hitbox (volume ≈ 0.702). */
	private static final double ZOMBIE_HP = 20.0;
	private static final double ZOMBIE_VOLUME = 0.6 * 0.6 * 1.95;
	/** A silverfish's: 8 max-HP, 0.4×0.3 hitbox — lands the bind inside the plan's ~[110,130] band. */
	private static final double SILVERFISH_HP = 8.0;
	private static final double SILVERFISH_VOLUME = 0.4 * 0.4 * 0.3;

	private static AmbushFacts clear(double distance) {
		return new AmbushFacts(true, false, false, true, false, false, false, false, distance);
	}

	@Test
	void canAmbushRefusesEverySingleBadFact() {
		double inside = MegumiShikigamiProfile.SERPENT_AMBUSH_RANGE - 1.0;
		assertTrue(MegumiSerpentPolicy.canAmbush(clear(inside)));
		// Each row flips exactly one fact — a refusal must come from that fact alone.
		assertFalse(MegumiSerpentPolicy.canAmbush(new AmbushFacts(
				false, false, false, true, false, false, false, false, inside)), "non-hostile");
		assertFalse(MegumiSerpentPolicy.canAmbush(new AmbushFacts(
				true, true, false, true, false, false, false, false, inside)), "own side");
		assertFalse(MegumiSerpentPolicy.canAmbush(new AmbushFacts(
				true, false, true, true, false, false, false, false, inside)), "protected");
		assertFalse(MegumiSerpentPolicy.canAmbush(new AmbushFacts(
				true, false, false, false, false, false, false, false, inside)), "ineligible");
		assertFalse(MegumiSerpentPolicy.canAmbush(new AmbushFacts(
				true, false, false, true, true, false, false, false, inside)), "already held");
		assertFalse(MegumiSerpentPolicy.canAmbush(new AmbushFacts(
				true, false, false, true, false, true, false, false, inside)), "ungrabbable");
		assertFalse(MegumiSerpentPolicy.canAmbush(new AmbushFacts(
				true, false, false, true, false, false, true, false, inside)), "mounted");
		assertFalse(MegumiSerpentPolicy.canAmbush(new AmbushFacts(
				true, false, false, true, false, false, false, true, inside)), "airborne");
	}

	@Test
	void canAmbushOnlyInsideRange() {
		assertTrue(MegumiSerpentPolicy.canAmbush(clear(MegumiShikigamiProfile.SERPENT_AMBUSH_RANGE)));
		assertFalse(MegumiSerpentPolicy.canAmbush(
				clear(MegumiShikigamiProfile.SERPENT_AMBUSH_RANGE + 0.01)));
	}

	@Test
	void ambushStillHoldsToleratesTheSlackBandOnly() {
		double range = MegumiShikigamiProfile.SERPENT_AMBUSH_RANGE;
		assertTrue(MegumiSerpentPolicy.ambushStillHolds(clear(range + 2.0)), "edge of the slack");
		assertFalse(MegumiSerpentPolicy.ambushStillHolds(clear(range + 2.01)), "past the slack");
		// The slack buys distance, not forgiveness — the refusal facts still kill a pending ambush.
		assertFalse(MegumiSerpentPolicy.ambushStillHolds(new AmbushFacts(
				true, false, false, true, true, false, false, false, range)),
				"a victim grabbed mid-dive is still lost");
		assertFalse(MegumiSerpentPolicy.ambushStillHolds(new AmbushFacts(
				true, false, false, true, false, false, false, true, range)),
				"a victim that left the ground is still lost");
	}

	@Test
	void ambushDueTriggersAtTheScanTick() {
		assertFalse(MegumiSerpentPolicy.ambushDue(39, 40));
		assertTrue(MegumiSerpentPolicy.ambushDue(40, 40));
		assertTrue(MegumiSerpentPolicy.ambushDue(41, 40));
	}

	@Test
	void emergeCandidatesWalkTheRearArcInContractOrder() {
		Vec3 target = new Vec3(3.0, 64.0, -2.0);
		List<Vec3> points = MegumiSerpentPolicy.emergeCandidates(
				target, 0.0f, MegumiShikigamiProfile.SERPENT_EMERGE_REAR_OFFSET);
		assertEquals(MegumiShadowMovePolicy.REAR_ARC_DEGREES.length, points.size());
		// Yaw 0 bodies face +z, so the straight-behind point (arc 0) sits at -z.
		assertEquals(target.x, points.get(0).x, EPS);
		assertEquals(target.z - MegumiShikigamiProfile.SERPENT_EMERGE_REAR_OFFSET,
				points.get(0).z, 1.0E-6);
		// The alternating pairs stay symmetric: ±25 first, then ±50, then ±75.
		for (int i = 1; i < points.size(); i += 2) {
			assertEquals(-points.get(i).x + 2 * target.x, points.get(i + 1).x, 1.0E-3,
					"left/right pair " + i + " must mirror around the target");
			assertEquals(points.get(i).z, points.get(i + 1).z, 1.0E-3);
		}
		// Wider arcs end further off the straight-behind axis; the whole fan stays behind.
		for (Vec3 point : points) {
			assertTrue(point.z < target.z, "every candidate must sit in the rear half");
			assertEquals(target.y, point.y, EPS);
		}
	}

	@Test
	void mouthAnchorStandsOneBlockAheadAtFootLevel() {
		Vec3 body = new Vec3(1.0, 63.0, 1.0);
		Vec3 anchor = MegumiSerpentPolicy.mouthAnchor(body, new Vec3(0.6, 0.7, 0.6));
		assertEquals(1.0 + 0.6 / Math.sqrt(0.72), anchor.x, 1.0E-6);
		assertEquals(body.y, anchor.y, EPS);
		assertEquals(1.0 + 0.6 / Math.sqrt(0.72), anchor.z, 1.0E-6);
		assertEquals(MegumiSerpentPolicy.SERPENT_MOUTH_OFFSET, anchor.distanceTo(body), 1.0E-6);
	}

	@Test
	void mouthAnchorWithAGroundedLookFallsBackToTheBody() {
		Vec3 body = new Vec3(1.0, 63.0, 1.0);
		assertEquals(body, MegumiSerpentPolicy.mouthAnchor(body, new Vec3(0.0, 1.0, 0.0)));
	}

	@Test
	void bindTicksForShavesHealthAndBulkThenClamps() {
		// A zombie: 120 − 20·1.0 − 0.702·8.0 ≈ 94.4 → 94.
		assertEquals(94, MegumiSerpentPolicy.bindTicksFor(ZOMBIE_HP, ZOMBIE_VOLUME, false));
		// A silverfish: 120 − 8 − 0.048·8 ≈ 111.6 → 112, inside the plan's ~[110,130] band.
		assertEquals(112, MegumiSerpentPolicy.bindTicksFor(SILVERFISH_HP, SILVERFISH_VOLUME, false));
		// A player pays health but never size: 120 − 20 = 100 regardless of hitbox.
		assertEquals(100, MegumiSerpentPolicy.bindTicksFor(20.0, 99.0, true));
		// The clamps hold both ends: a heavy brute bottoms out, a fragile fly caps at the top.
		assertEquals(80, MegumiSerpentPolicy.bindTicksFor(200.0, 3.0, false));
		assertEquals(120, MegumiSerpentPolicy.bindTicksFor(0.0, 0.0, false));
		assertEquals(160, MegumiSerpentPolicy.bindTicksFor(-100.0, 0.0, false));
	}

	@Test
	void bindBrokenBreaksPastTheRangeOnly() {
		assertFalse(MegumiSerpentPolicy.bindBroken(16.0, 16.0));
		assertTrue(MegumiSerpentPolicy.bindBroken(16.01, 16.0));
	}

	@Test
	void bindActionOrdersAbortBeforeReleaseBeforeHold() {
		BindFacts nominal = new BindFacts(true, true, false, true, false, 3.0, 1.5);
		assertEquals(SerpentAction.HOLD, MegumiSerpentPolicy.bindAction(nominal));
		// The four silent exits — each one alone, and even an expired timer stays an abort.
		assertEquals(SerpentAction.ABORT, MegumiSerpentPolicy.bindAction(
				new BindFacts(false, true, false, true, false, 3.0, 1.5)), "victim gone");
		assertEquals(SerpentAction.ABORT, MegumiSerpentPolicy.bindAction(
				new BindFacts(true, false, false, true, false, 3.0, 1.5)), "victim dead");
		assertEquals(SerpentAction.ABORT, MegumiSerpentPolicy.bindAction(
				new BindFacts(true, true, true, true, false, 3.0, 1.5)), "victim disconnected");
		assertEquals(SerpentAction.ABORT, MegumiSerpentPolicy.bindAction(
				new BindFacts(true, true, false, false, false, 3.0, 1.5)), "dimension split");
		assertEquals(SerpentAction.ABORT, MegumiSerpentPolicy.bindAction(
				new BindFacts(false, false, false, false, true, 3.0, 1.5)),
				"dead AND expired is still quiet");
		// The live exits.
		assertEquals(SerpentAction.RELEASE, MegumiSerpentPolicy.bindAction(
				new BindFacts(true, true, false, true, true, 3.0, 1.5)), "timer expired");
		assertEquals(SerpentAction.RELEASE, MegumiSerpentPolicy.bindAction(
				new BindFacts(true, true, false, true, false,
						MegumiShikigamiProfile.SERPENT_BIND_BREAK_RANGE + 0.5, 1.5)),
				"owner leash snapped");
		assertEquals(SerpentAction.RELEASE, MegumiSerpentPolicy.bindAction(
				new BindFacts(true, true, false, true, false, 3.0,
						MegumiShikigamiProfile.SERPENT_BIND_BREAK_RANGE + 0.5)),
				"victim dragged out");
		assertEquals(SerpentAction.HOLD, MegumiSerpentPolicy.bindAction(
				new BindFacts(true, true, false, true, false,
						MegumiShikigamiProfile.SERPENT_BIND_BREAK_RANGE,
						MegumiShikigamiProfile.SERPENT_BIND_BREAK_RANGE)),
				"both leashes at the exact edge still hold");
	}

	@Test
	void tossVelocityFlingsAwayFromTheOwnerAtThePinnedSpeed() {
		Vec3 owner = new Vec3(0.0, 64.0, 0.0);
		Vec3 body = new Vec3(3.0, 64.0, 4.0);
		Vec3 toss = MegumiSerpentPolicy.tossVelocity(owner, body);
		double horizontal = Math.hypot(toss.x, toss.z);
		assertEquals(MegumiShikigamiProfile.SERPENT_TOSS_SPEED, horizontal, 1.0E-6);
		assertEquals(MegumiShikigamiProfile.SERPENT_TOSS_LIFT, toss.y, EPS);
		assertTrue(toss.x > 0 && toss.z > 0, "the toss points along owner→body, away from the owner");
		// Standing on top of the owner drops the shove but keeps the pop.
		assertEquals(new Vec3(0.0, MegumiShikigamiProfile.SERPENT_TOSS_LIFT, 0.0),
				MegumiSerpentPolicy.tossVelocity(owner, owner));
	}
}
