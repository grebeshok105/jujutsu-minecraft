package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ProjectJjkNobaraProfileTest {
	private ProjectJjkNobaraProfileTest() {}

	public static void main(String[] args) {
		assertTapPreparesOneNail();
		assertShortHoldPreparesTripleNails();
		assertLongHoldPreparesBarrageNails();
		assertWorldNailsPersistLikeProjectJjkReference();
		assertNailsLaunchWithTwoTickStagger();
		assertPreparedLaunchRequiresCloseNails();
		assertPreparedLaunchRangeUsesPlayerBounds();
		assertGroundImpactsStayTighterThanDirectHits();
		assertEmbeddedNailsLastAsLongAsMarks();
		assertTargetMarksFollowCurseMarkDuration();
		assertBodyEmbedPointStaysInsideBodyHeight();
		assertLoadoutTopsUpNails();
		assertDamageScaling();
		System.out.println("ProjectJjkNobaraProfileTest passed");
	}

	private static void assertTapPreparesOneNail() {
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(0) == 1 : "tap should prepare one nail";
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(5) == 1 : "250 ms should still be one nail";
	}

	private static void assertShortHoldPreparesTripleNails() {
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(6) == 3 : "300 ms should prepare three nails";
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(15) == 3 : "750 ms should stay at triple nails";
	}

	private static void assertLongHoldPreparesBarrageNails() {
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(16) == ProjectJjkNobaraProfile.BARRAGE_NAILS : "800 ms should prepare barrage nails";
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(60) == ProjectJjkNobaraProfile.BARRAGE_NAILS : "long holds should clamp to barrage nails";
	}

	private static void assertWorldNailsPersistLikeProjectJjkReference() {
		assert ProjectJjkNobaraProfile.MAX_NAIL_AGE_TICKS == 1200 : "ProjectJJK reference nails persist for 1200 ticks";
		assert ProjectJjkNobaraProfile.LAUNCH_SPEED_BLOCKS_PER_TICK >= 2.8 : "comparison nails should launch sharply";
		assert ProjectJjkNobaraProfile.PREPARED_FORWARD_OFFSET > 1.0 : "prepared nails should spawn in front of the caster";
	}

	private static void assertNailsLaunchWithTwoTickStagger() {
		assert ProjectJjkNobaraProfile.PREPARED_LAUNCH_DELAY_TICKS == 4 : "0.2 second nail stagger should be four ticks";
		assert ProjectJjkNobaraProfile.launchDelayForIndex(0) == 0 : "first nail should launch immediately";
		assert ProjectJjkNobaraProfile.launchDelayForIndex(1) == 4 : "second nail should launch after 0.2s";
		assert ProjectJjkNobaraProfile.launchDelayForIndex(7) == 28 : "barrage should keep staggering every nail";
	}

	private static void assertPreparedLaunchRequiresCloseNails() {
		assert ProjectJjkNobaraProfile.PREPARED_LAUNCH_RANGE == 2.0 : "prepared nails should only launch within two blocks";
		assert ProjectJjkNobaraProfile.PREPARED_LAUNCH_RANGE < ProjectJjkNobaraProfile.HAIRPIN_SEARCH_RANGE : "launch range must be tighter than legacy search range";
	}

	private static void assertPreparedLaunchRangeUsesPlayerBounds() {
		AABB playerBounds = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);
		assert ProjectJjkNobaraRuntime.isPreparedNailWithinLaunchRange(playerBounds, new Vec3(0.0, 0.0, 2.25)) : "nail two blocks from player body should launch";
		assert !ProjectJjkNobaraRuntime.isPreparedNailWithinLaunchRange(playerBounds, new Vec3(0.0, 0.0, 2.31)) : "nail beyond two blocks from player body should not launch";
	}

	private static void assertGroundImpactsStayTighterThanDirectHits() {
		assert ProjectJjkNobaraProfile.GROUND_IMPACT_RADIUS < ProjectJjkNobaraProfile.IMPACT_RADIUS : "ground impact should be smaller than direct hit bloom";
		assert ProjectJjkNobaraProfile.GROUND_IMPACT_RADIUS >= 2.0 : "ground impact should still read visually";
	}

	private static void assertEmbeddedNailsLastAsLongAsMarks() {
		assert ProjectJjkNobaraProfile.EMBEDDED_NAIL_AGE_TICKS == ProjectJjkNobaraProfile.MARK_DURATION_TICKS : "visible stuck nails should match cursed mark duration";
	}

	private static void assertTargetMarksFollowCurseMarkDuration() {
		assert ProjectJjkNobaraProfile.TARGET_MARK_RENDER_TICKS == ProjectJjkNobaraProfile.MARK_DURATION_TICKS : "ProjectJJK-style target marks should stay with the cursed mark";
	}

	private static void assertBodyEmbedPointStaysInsideBodyHeight() {
		Vec3 targetPosition = new Vec3(10.0, 64.0, 10.0);
		Vec3 hitPoint = targetPosition.add(0.0, 1.72, 0.0);
		Vec3 embedPoint = ProjectJjkNailEmbedding.bodyEmbedPoint(targetPosition, 0.6, 1.8, hitPoint, new Vec3(0.0, -1.0, 0.0), 3);
		double relativeY = embedPoint.y - targetPosition.y;
		assert relativeY >= 1.8 * 0.28 - 1.0E-6 && relativeY <= 1.8 * 0.86 + 1.0E-6 : "embedded nails should stay within the victim body height";
	}

	private static void assertLoadoutTopsUpNails() {
		assert ProjectJjkNobaraLoadout.missingNails(0) == 16 : "Nobara selection should provide a starter nail stack";
		assert ProjectJjkNobaraLoadout.missingNails(9) == 7 : "Nobara selection should top up partial nail stacks";
		assert ProjectJjkNobaraLoadout.missingNails(16) == 0 : "Nobara selection should not duplicate full starter nails";
		assert ProjectJjkNobaraLoadout.missingNails(30) == 0 : "Nobara selection should not add nails when player already has enough";
	}

	private static void assertDamageScaling() {
		assert ProjectJjkNobaraProfile.detonateDamage(0) == ProjectJjkNobaraProfile.DETONATE_DAMAGE_BASE : "zero marks detonate for the base amount";
		assert ProjectJjkNobaraProfile.detonateDamage(4) > ProjectJjkNobaraProfile.detonateDamage(1) : "more marks must detonate harder";
		assert ProjectJjkNobaraProfile.resonanceDamage(4) > ProjectJjkNobaraProfile.resonanceDamage(0) : "resonance must scale with marks";
	}
}
