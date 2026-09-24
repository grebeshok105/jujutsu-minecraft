package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MegumiSerpentPolicyTest {
	private static final double EPS = 1.0E-9;
	private static final MegumiSerpentPolicy.BindFacts BINDABLE = new MegumiSerpentPolicy.BindFacts(
			true, false, true, false, false, false, true, true);

	@Test
	void ambushRequiresAnEligibleMarkedTargetInsideRangeAndSight() {
		assertTrue(MegumiSerpentPolicy.canStart(new MegumiSerpentPolicy.AmbushFacts(
				MegumiSerpentPolicy.MarkKind.MANUAL, true, true, true, true)));
		assertTrue(MegumiSerpentPolicy.canStart(new MegumiSerpentPolicy.AmbushFacts(
				MegumiSerpentPolicy.MarkKind.AUTONOMOUS, true, true, true, false)),
				"an autonomous mark depends on the serpent's senses, not the owner's sightline");
		assertTrue(MegumiSerpentPolicy.canStart(new MegumiSerpentPolicy.AmbushFacts(
				MegumiSerpentPolicy.MarkKind.RETALIATION, true, true, true, false)));
		assertFalse(MegumiSerpentPolicy.canStart(new MegumiSerpentPolicy.AmbushFacts(
				MegumiSerpentPolicy.MarkKind.NONE, true, true, true, true)));
		assertFalse(MegumiSerpentPolicy.canStart(new MegumiSerpentPolicy.AmbushFacts(
				MegumiSerpentPolicy.MarkKind.MANUAL, false, true, true, true)), "ineligible targets are refused");
		assertFalse(MegumiSerpentPolicy.canStart(new MegumiSerpentPolicy.AmbushFacts(
				MegumiSerpentPolicy.MarkKind.MANUAL, true, false, true, true)), "out-of-range marks are refused");
		assertFalse(MegumiSerpentPolicy.canStart(new MegumiSerpentPolicy.AmbushFacts(
				MegumiSerpentPolicy.MarkKind.MANUAL, true, true, false, true)), "the serpent needs line of sight");
		assertFalse(MegumiSerpentPolicy.canStart(new MegumiSerpentPolicy.AmbushFacts(
				MegumiSerpentPolicy.MarkKind.MANUAL, true, true, true, false)), "manual sic retains owner sight gating");
		assertFalse(MegumiSerpentPolicy.canStart(null));
	}

	@Test
	void bindRechecksEveryEligibilityGateAtCommit() {
		assertTrue(MegumiSerpentPolicy.canBind(BINDABLE));
		assertFalse(MegumiSerpentPolicy.canBind(new MegumiSerpentPolicy.BindFacts(
				false, false, true, false, false, false, true, true)), "dead targets cannot be bound");
		assertFalse(MegumiSerpentPolicy.canBind(new MegumiSerpentPolicy.BindFacts(
				true, true, true, false, false, false, true, true)), "removed targets cannot be bound");
		assertFalse(MegumiSerpentPolicy.canBind(new MegumiSerpentPolicy.BindFacts(
				true, false, false, false, false, false, true, true)), "owner-ineligible targets cannot be bound");
		assertFalse(MegumiSerpentPolicy.canBind(new MegumiSerpentPolicy.BindFacts(
				true, false, true, true, false, false, true, true)), "passengers cannot be bound");
		assertFalse(MegumiSerpentPolicy.canBind(new MegumiSerpentPolicy.BindFacts(
				true, false, true, false, true, false, true, true)), "a victim already held by another source is refused");
		assertFalse(MegumiSerpentPolicy.canBind(new MegumiSerpentPolicy.BindFacts(
				true, false, true, false, false, true, true, true)), "ungrabbable targets are refused");
		assertFalse(MegumiSerpentPolicy.canBind(new MegumiSerpentPolicy.BindFacts(
				true, false, true, false, false, false, false, true)), "out-of-range targets are refused");
		assertFalse(MegumiSerpentPolicy.canBind(new MegumiSerpentPolicy.BindFacts(
				true, false, true, false, false, false, true, false)), "occluded targets are refused");
		assertFalse(MegumiSerpentPolicy.canBind(null));
	}

	@Test
	void theLeashHoldsOnTheBoundaryAndBreaksBeyondIt() {
		assertFalse(MegumiSerpentPolicy.bindBroken(20.0, 20.0));
		assertFalse(MegumiSerpentPolicy.bindBroken(0.0, 20.0));
		assertTrue(MegumiSerpentPolicy.bindBroken(20.001, 20.0));
		assertTrue(MegumiSerpentPolicy.bindBroken(Double.NaN, 20.0), "invalid measurements fail safe");
		assertTrue(MegumiSerpentPolicy.bindBroken(1.0, Double.NaN), "a non-finite leash cannot retain a hold");
		assertTrue(MegumiSerpentPolicy.bindBroken(1.0, -1.0), "a negative leash cannot retain a hold");
	}

	@Test
	void emergeCandidatesFormAnOrderedHorizontalRingOnTheApproachSide() {
		Vec3 target = new Vec3(10.0, 4.0, -2.0);
		List<Vec3> candidates = MegumiSerpentPolicy.emergeCandidates(target,
				new Vec3(0.0, 8.0, 2.0), 2.0);
		assertEquals(8, candidates.size());
		assertEquals(new Vec3(10.0, 4.0, 0.0), candidates.get(0), "the first candidate is toward the approach");
		assertEquals(8, new HashSet<>(candidates).size(), "the ring candidates are distinct");
		for (Vec3 candidate : candidates) {
			assertEquals(target.y, candidate.y, EPS, "emerge candidates keep the target's floor level");
			assertEquals(2.0, Math.hypot(candidate.x - target.x, candidate.z - target.z), EPS);
		}
	}

	@Test
	void aDegenerateApproachHasDeterministicFallbackAndInvalidRadiiHaveNoCandidates() {
		List<Vec3> candidates = MegumiSerpentPolicy.emergeCandidates(
				new Vec3(1.0, 2.0, 3.0), Vec3.ZERO, 1.5);
		assertEquals(new Vec3(1.0, 2.0, 4.5), candidates.get(0));
		assertEquals(List.of(new Vec3(1.0, 2.0, 3.0)), MegumiSerpentPolicy.emergeCandidates(
				new Vec3(1.0, 2.0, 3.0), Vec3.ZERO, 0.0));
		assertTrue(MegumiSerpentPolicy.emergeCandidates(Vec3.ZERO, Vec3.ZERO, Double.NaN).isEmpty());
		assertTrue(MegumiSerpentPolicy.emergeCandidates(Vec3.ZERO, Vec3.ZERO, -1.0).isEmpty());
		assertTrue(MegumiSerpentPolicy.emergeCandidates(Vec3.ZERO, null, 1.0).isEmpty());
		assertTrue(MegumiSerpentPolicy.emergeCandidates(null, Vec3.ZERO, 1.0).isEmpty());
	}

	@Test
	void safeEmergeRequiresFiniteLoadedInWorldBorderCollisionFreePlacement() {
		MegumiSerpentPolicy.SafetyFacts safe = new MegumiSerpentPolicy.SafetyFacts(true, true, true, true, true);
		assertTrue(MegumiSerpentPolicy.isSafeEmerge(safe));
		assertFalse(MegumiSerpentPolicy.isSafeEmerge(new MegumiSerpentPolicy.SafetyFacts(false, true, true, true, true)));
		assertFalse(MegumiSerpentPolicy.isSafeEmerge(new MegumiSerpentPolicy.SafetyFacts(true, false, true, true, true)));
		assertFalse(MegumiSerpentPolicy.isSafeEmerge(new MegumiSerpentPolicy.SafetyFacts(true, true, false, true, true)));
		assertFalse(MegumiSerpentPolicy.isSafeEmerge(new MegumiSerpentPolicy.SafetyFacts(true, true, true, false, true)));
		assertFalse(MegumiSerpentPolicy.isSafeEmerge(new MegumiSerpentPolicy.SafetyFacts(true, true, true, true, false)));
		assertFalse(MegumiSerpentPolicy.isSafeEmerge(null));
	}

	@Test
	void stateMachineFollowsTheAmbushLifecycleAndRejectsInvalidBranches() {
		MegumiSerpentPolicy.State state = MegumiSerpentPolicy.State.FOLLOW_READY;
		state = MegumiSerpentPolicy.nextState(state, MegumiSerpentPolicy.Event.MARK_READY);
		assertEquals(MegumiSerpentPolicy.State.PREPARE_AMBUSH, state);
		state = MegumiSerpentPolicy.nextState(state, MegumiSerpentPolicy.Event.PREPARE_COMPLETE);
		assertEquals(MegumiSerpentPolicy.State.SUBMERGED, state);
		state = MegumiSerpentPolicy.nextState(state, MegumiSerpentPolicy.Event.SUBMERGE_COMPLETE);
		assertEquals(MegumiSerpentPolicy.State.EMERGE, state);
		state = MegumiSerpentPolicy.nextState(state, MegumiSerpentPolicy.Event.EMERGE_BINDABLE);
		assertEquals(MegumiSerpentPolicy.State.BIND, state);
		state = MegumiSerpentPolicy.nextState(state, MegumiSerpentPolicy.Event.BIND_TIMER);
		assertEquals(MegumiSerpentPolicy.State.RELEASE, state);
		state = MegumiSerpentPolicy.nextState(state, MegumiSerpentPolicy.Event.RELEASE_COMPLETE);
		assertEquals(MegumiSerpentPolicy.State.RECOVERY, state);
		assertEquals(MegumiSerpentPolicy.State.FOLLOW_READY,
				MegumiSerpentPolicy.nextState(state, MegumiSerpentPolicy.Event.RECOVERY_COMPLETE));
		assertEquals(MegumiSerpentPolicy.State.RECOVERY, MegumiSerpentPolicy.nextState(
				MegumiSerpentPolicy.State.SUBMERGED, MegumiSerpentPolicy.Event.NO_SAFE_EMERGE));
		assertEquals(MegumiSerpentPolicy.State.RECOVERY, MegumiSerpentPolicy.nextState(
				MegumiSerpentPolicy.State.EMERGE, MegumiSerpentPolicy.Event.EMERGE_INVALID));
	}

	@Test
	void everyBoundLifecycleExitUsesReleaseBeforeRecovery() {
		Set<MegumiSerpentPolicy.Event> releaseEvents = Set.of(
				MegumiSerpentPolicy.Event.BIND_TIMER,
				MegumiSerpentPolicy.Event.BIND_LEASH,
				MegumiSerpentPolicy.Event.MANUAL_RECALL,
				MegumiSerpentPolicy.Event.SERPENT_DEATH,
				MegumiSerpentPolicy.Event.SERPENT_REMOVED,
				MegumiSerpentPolicy.Event.SERPENT_TEARDOWN,
				MegumiSerpentPolicy.Event.OWNER_DEATH,
				MegumiSerpentPolicy.Event.OWNER_DISCONNECT,
				MegumiSerpentPolicy.Event.OWNER_RESPAWN,
				MegumiSerpentPolicy.Event.OWNER_DIMENSION_CHANGE,
				MegumiSerpentPolicy.Event.VICTIM_DEATH,
				MegumiSerpentPolicy.Event.VICTIM_DISCONNECT,
				MegumiSerpentPolicy.Event.VICTIM_DIMENSION_CHANGE,
				MegumiSerpentPolicy.Event.VICTIM_UNLOAD,
				MegumiSerpentPolicy.Event.VICTIM_INELIGIBLE,
				MegumiSerpentPolicy.Event.HOLD_OWNERSHIP_MISMATCH,
				MegumiSerpentPolicy.Event.SERVER_TEARDOWN,
				MegumiSerpentPolicy.Event.DESELECTED,
				MegumiSerpentPolicy.Event.FIXTURE_RESET);
		for (MegumiSerpentPolicy.Event event : releaseEvents) {
			assertEquals(MegumiSerpentPolicy.State.RELEASE,
					MegumiSerpentPolicy.nextState(MegumiSerpentPolicy.State.BIND, event), event.toString());
		}
		assertEquals(MegumiSerpentPolicy.State.BIND, MegumiSerpentPolicy.nextState(
				MegumiSerpentPolicy.State.BIND, MegumiSerpentPolicy.Event.RELEASE_COMPLETE));
	}
}
