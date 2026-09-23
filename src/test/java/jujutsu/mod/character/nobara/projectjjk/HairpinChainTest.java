package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Assertion-main coverage for Hairpin cadence and resolution policy. */
public final class HairpinChainTest {
	private static final UUID A = new UUID(0L, 1L);
	private static final UUID B = new UUID(0L, 2L);
	private static final UUID C = new UUID(0L, 3L);

	private HairpinChainTest() {}

	public static void main(String[] args) {
		directedUsesExactCadence();
		temporaryEntryRotatesAndRetriesAfterOtherEntries();
		temporaryDeadlineDropsAtExactlyTwentyTicks();
		confirmedRemovedDropsImmediately();
		invalidDropsWithoutRetry();
		finalizerRunsOnlyAfterSuccessfulAnchor();
	}

	private static void directedUsesExactCadence() {
		HairpinChain chain = HairpinChain.start(List.of(A, B), 10L, 2);
		assert chain.poll(9L, id -> HairpinChain.Resolution.RESOLVED).kind() == HairpinChain.StepKind.WAIT;
		assert chain.poll(10L, id -> HairpinChain.Resolution.RESOLVED).nailId().equals(A);
		assert chain.poll(11L, id -> HairpinChain.Resolution.RESOLVED).kind() == HairpinChain.StepKind.WAIT;
		assert chain.poll(12L, id -> HairpinChain.Resolution.RESOLVED).nailId().equals(B);
	}

	private static void temporaryEntryRotatesAndRetriesAfterOtherEntries() {
		HairpinChain chain = HairpinChain.start(List.of(A, B), 0L, 3);
		Map<UUID, HairpinChain.Resolution> unavailable = Map.of(
				A, HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE,
				B, HairpinChain.Resolution.RESOLVED);
		assert chain.poll(0L, unavailable::get).nailId().equals(B);
		Map<UUID, HairpinChain.Resolution> restored = Map.of(
				A, HairpinChain.Resolution.RESOLVED,
				B, HairpinChain.Resolution.CONFIRMED_REMOVED);
		assert chain.poll(3L, restored::get).nailId().equals(A);
		assert chain.skippedTemporary().equals(List.of(A));
	}

	private static void temporaryDeadlineDropsAtExactlyTwentyTicks() {
		HairpinChain chain = HairpinChain.start(List.of(A), 0L, 2);
		for (long tick = 0; tick < ProjectJjkNobaraProfile.TEMP_RETRY_DEADLINE_TICKS; tick += 2) {
			assert chain.poll(tick, id -> HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE).kind()
					== HairpinChain.StepKind.WAIT : "TEMP should remain retryable before deadline at " + tick;
		}
		HairpinChain.Step atDeadline = chain.poll(ProjectJjkNobaraProfile.TEMP_RETRY_DEADLINE_TICKS,
				id -> HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE);
		assert atDeadline.kind() == HairpinChain.StepKind.COMPLETE;
		assert atDeadline.nailId() == null;
		assert chain.skippedTemporary().equals(List.of(A));
	}

	private static void confirmedRemovedDropsImmediately() {
		HairpinChain chain = HairpinChain.start(List.of(A, B), 0L, 2);
		HairpinChain.Step step = chain.poll(0L, id -> id.equals(A)
				? HairpinChain.Resolution.CONFIRMED_REMOVED : HairpinChain.Resolution.RESOLVED);
		assert step.kind() == HairpinChain.StepKind.EXPLODE && step.nailId().equals(B);
	}

	private static void invalidDropsWithoutRetry() {
		HairpinChain chain = HairpinChain.start(List.of(A, B), 0L, 2);
		List<UUID> calls = new ArrayList<>();
		HairpinChain.Step step = chain.poll(0L, id -> {
			calls.add(id);
			return id.equals(A) ? HairpinChain.Resolution.INVALID : HairpinChain.Resolution.RESOLVED;
		});
		assert step.kind() == HairpinChain.StepKind.EXPLODE && step.nailId().equals(B);
		assert calls.equals(List.of(A, B));
		HairpinChain.Step complete = chain.poll(2L, id -> {
			calls.add(id);
			return HairpinChain.Resolution.INVALID;
		});
		assert complete.kind() == HairpinChain.StepKind.COMPLETE;
		assert calls.equals(List.of(A, B)) : "invalid entry was retried: " + calls;
	}

	private static void finalizerRunsOnlyAfterSuccessfulAnchor() {
		HairpinChainScheduler<String> scheduler = new HairpinChainScheduler<>();
		List<String> finalized = new ArrayList<>();
		scheduler.schedule("invalid", HairpinChain.start(List.of(A), 0L, 2));
		scheduler.tick(0L, (context, id) -> HairpinChain.Resolution.INVALID,
				(context, id, finale, time) -> { throw new AssertionError("invalid entry detonated"); },
				(context, id, time) -> finalized.add(context));
		assert finalized.isEmpty();
		scheduler.schedule("success", HairpinChain.start(List.of(B), 0L, 2));
		scheduler.tick(0L, (context, id) -> HairpinChain.Resolution.RESOLVED,
				(context, id, finale, time) -> {},
				(context, id, time) -> finalized.add(context));
		scheduler.tick(2L, (context, id) -> HairpinChain.Resolution.RESOLVED,
				(context, id, finale, time) -> {},
				(context, id, time) -> finalized.add(context));
		assert finalized.equals(List.of("success"));
	}
}
