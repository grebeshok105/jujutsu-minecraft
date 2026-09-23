package jujutsu.mod.character.nobara.projectjjk;

import java.util.UUID;

/**
 * Assertion-main coverage for owner-scoped nail marks: per-owner isolation, the 900-tick expiry
 * boundary, the shared-glow {@code anyMarks} rule, consumption and the per-target cap.
 */
public final class ProjectJjkNailMarksTest {
	private static final UUID OWNER_A = new UUID(0L, 1L);
	private static final UUID OWNER_B = new UUID(0L, 2L);
	private static final UUID TARGET = new UUID(0L, 3L);
	private static final UUID OTHER_TARGET = new UUID(0L, 4L);

	private ProjectJjkNailMarksTest() {}

	public static void main(String[] args) {
		marksAreOwnerIsolated();
		expiryBoundaryAtNineHundredTicks();
		anyMarksFollowsTheSharedGlowRule();
		consumeReturnsAndDropsTheStack();
		clearOwnerLeavesOtherOwners();
		perTargetCapHolds();
		clearTargetLeavesOtherTargets();
		System.out.println("ProjectJjkNailMarksTest passed");
	}

	private static void marksAreOwnerIsolated() {
		ProjectJjkNailMarks.clearAll();
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 100L);
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 100L);
		ProjectJjkNailMarks.apply(OWNER_B, TARGET, 100L);
		assert ProjectJjkNailMarks.marks(OWNER_A, TARGET, 100L) == 2;
		assert ProjectJjkNailMarks.marks(OWNER_B, TARGET, 100L) == 1;
		assert ProjectJjkNailMarks.marks(OWNER_A, OTHER_TARGET, 100L) == 0;
	}

	private static void expiryBoundaryAtNineHundredTicks() {
		ProjectJjkNailMarks.clearAll();
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 1_000L);
		long inside = 1_000L + ProjectJjkNobaraProfile.MARK_DURATION_TICKS;
		assert ProjectJjkNailMarks.marks(OWNER_A, TARGET, inside) == 1
				: "a mark exactly at the duration edge must still be active";
		assert ProjectJjkNailMarks.marks(OWNER_A, TARGET, inside + 1) == 0
				: "a mark one tick past the duration must expire";
	}

	private static void anyMarksFollowsTheSharedGlowRule() {
		ProjectJjkNailMarks.clearAll();
		assert !ProjectJjkNailMarks.anyMarks(TARGET, 0L);
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 0L);
		assert ProjectJjkNailMarks.anyMarks(TARGET, 0L)
				: "the glow is target-scoped: one owner's mark lights the target for everyone";
		ProjectJjkNailMarks.clearOwner(OWNER_A);
		assert !ProjectJjkNailMarks.anyMarks(TARGET, 0L);
		assert !ProjectJjkNailMarks.anyMarks(null, 0L);
	}

	private static void consumeReturnsAndDropsTheStack() {
		ProjectJjkNailMarks.clearAll();
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 0L);
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 0L);
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 0L);
		assert ProjectJjkNailMarks.consume(OWNER_A, TARGET) == 3;
		assert ProjectJjkNailMarks.marks(OWNER_A, TARGET, 0L) == 0;
		assert ProjectJjkNailMarks.consume(OWNER_A, TARGET) == 0;
	}

	private static void clearOwnerLeavesOtherOwners() {
		ProjectJjkNailMarks.clearAll();
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 0L);
		ProjectJjkNailMarks.apply(OWNER_B, TARGET, 0L);
		ProjectJjkNailMarks.clearOwner(OWNER_A);
		assert ProjectJjkNailMarks.marks(OWNER_A, TARGET, 0L) == 0;
		assert ProjectJjkNailMarks.marks(OWNER_B, TARGET, 0L) == 1;
	}

	private static void perTargetCapHolds() {
		ProjectJjkNailMarks.clearAll();
		for (int i = 0; i < ProjectJjkNobaraProfile.MARK_MAX_PER_TARGET + 3; i++) {
			ProjectJjkNailMarks.apply(OWNER_A, TARGET, 0L);
		}
		assert ProjectJjkNailMarks.marks(OWNER_A, TARGET, 0L) == ProjectJjkNobaraProfile.MARK_MAX_PER_TARGET;
	}

	private static void clearTargetLeavesOtherTargets() {
		ProjectJjkNailMarks.clearAll();
		ProjectJjkNailMarks.apply(OWNER_A, TARGET, 0L);
		ProjectJjkNailMarks.apply(OWNER_A, OTHER_TARGET, 0L);
		ProjectJjkNailMarks.clear(TARGET);
		assert ProjectJjkNailMarks.marks(OWNER_A, TARGET, 0L) == 0;
		assert ProjectJjkNailMarks.marks(OWNER_A, OTHER_TARGET, 0L) == 1;
		ProjectJjkNailMarks.clearAll();
	}
}
