package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ProjectJjkNobaraProfileTest {
	private ProjectJjkNobaraProfileTest() {}

	public static void main(String[] args) {
		assertTapPreparesOneNail();
		assertHoldAddsOneNailEveryHalfSecond();
		assertWorldNailsPersistLikeProjectJjkReference();
		assertNailsLaunchWithTwoTickStagger();
		assertPreparedLaunchRequiresCloseNails();
		assertPreparedLaunchRangeUsesPlayerBounds();
		assertGroundImpactsStayTighterThanDirectHits();
		assertEmbeddedNailsDoNotExpireByAge();
		assertTargetMarksFollowCurseMarkDuration();
		assertBodyEmbedPointStaysInsideBodyHeight();
		assertBodyEmbedPointPiercesIntoBody();
		assertHairpinExplosionCanSeeCloseAnchors();
		assertLoadoutTopsUpNails();
		assertDamageScaling();
		assertCombatExpansionBalanceIsCentralized();
		assertActionTimelinesExposeImpactAndBlackFlashWindow();
		System.out.println("ProjectJjkNobaraProfileTest passed");
	}

	private static void assertTapPreparesOneNail() {
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(0) == 1 : "tap should prepare one nail";
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(9) == 1 : "less than 0.5s should still be one nail";
	}

	private static void assertHoldAddsOneNailEveryHalfSecond() {
		assert ProjectJjkNobaraProfile.EXTRA_NAIL_HOLD_TICKS == 10 : "one extra nail should require 0.5s / 10 ticks of hold";
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(10) == 2 : "0.5s should add the second nail";
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(20) == 3 : "1.0s should prepare three nails";
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(70) == ProjectJjkNobaraProfile.BARRAGE_NAILS : "3.5s should reach the barrage cap";
		assert ProjectJjkNobaraProfile.nailCountForUseTicks(120) == ProjectJjkNobaraProfile.BARRAGE_NAILS : "long holds should clamp to barrage nails";
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

	private static void assertEmbeddedNailsDoNotExpireByAge() {
		assert ProjectJjkNobaraProfile.EMBEDDED_NAIL_AGE_TICKS == 0 : "persistent nails must not disappear because their anchor was unloaded for a long time";
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

	private static void assertBodyEmbedPointPiercesIntoBody() {
		Vec3 targetPosition = new Vec3(0.0, 64.0, 0.0);
		Vec3 direction = new Vec3(0.0, 0.0, 1.0);
		Vec3 hitPoint = targetPosition.add(0.0, 1.0, -0.3);
		Vec3 embedPoint = ProjectJjkNailEmbedding.bodyEmbedPoint(targetPosition, 0.6, 1.8, hitPoint, direction, 16);
		assert embedPoint.z > 0.0 : "embedded nails should pierce visibly into the target body instead of sitting in front of it: " + embedPoint;
		assert embedPoint.z < 0.24 : "embedded nails should not cross through the whole target body: " + embedPoint;
	}

	private static void assertHairpinExplosionCanSeeCloseAnchors() {
		assert ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_DETECT_FORWARD_OFFSET == 0.0 : "Hairpin Explosion search must start at the caster eye so close nails are not cut off";
		assert ProjectJjkNobaraProfile.HAIRPIN_EXPLOSION_DETECT_RANGE >= 12.0 : "Hairpin Explosion needs enough forward reach after removing the old 4-block offset";
	}

	private static void assertLoadoutTopsUpNails() {
		assert ProjectJjkNobaraLoadout.missingNails(0) == 16 : "Nobara selection should provide a starter nail stack";
		assert ProjectJjkNobaraLoadout.missingNails(9) == 7 : "Nobara selection should top up partial nail stacks";
		assert ProjectJjkNobaraLoadout.missingNails(16) == 0 : "Nobara selection should not duplicate full starter nails";
		assert ProjectJjkNobaraLoadout.missingNails(30) == 0 : "Nobara selection should not add nails when player already has enough";
	}

	private static void assertDamageScaling() {
		assert ProjectJjkNobaraProfile.detonateDamage(0) == ProjectJjkNobaraProfile.DETONATE_DAMAGE_BASE : "zero marks detonate for the base amount";
		assert ProjectJjkNobaraProfile.detonateDamage(4) == ProjectJjkNobaraProfile.detonateDamage(1) : "Hairpin Explosion damage stays fixed per detonation";
		assert ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_DAMAGE == 4.0f : "Hairpin Enlarge damage is applied independently per nail";
		assert ProjectJjkNobaraProfile.DETONATE_DAMAGE_BASE == 3.0f : "Hairpin Explosion damage is applied independently per nail";
		assert ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE == 20.0 : "ProjectJJK Hairpin Enlarge player range is 20 blocks";
		assert ProjectJjkNobaraProfile.RESONANCE_DAMAGE == 28.0f : "physical-remnant Resonance uses the initial heavy balance value";
	}

	private static void assertCombatExpansionBalanceIsCentralized() {
		assert ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_DAMAGE_PER_NAIL == 4.0f;
		assert ProjectJjkNobaraProfile.HAIRPIN_BOOM_DAMAGE_PER_NAIL == 3.0f;
		assert ProjectJjkNobaraProfile.RESONANCE_DAMAGE == 28.0f;
		assert ProjectJjkNobaraProfile.SELF_RESONANCE_SELF_DAMAGE == 6.0f;
		assert ProjectJjkNobaraProfile.SELF_RESONANCE_LINKED_DAMAGE == 18.0f;
		assert ProjectJjkNobaraProfile.BLACK_FLASH_DAMAGE_MULTIPLIER == 1.75f;
		assert ProjectJjkNobaraProfile.HAMMER_HORIZONTAL_DAMAGE == 5.0f;
		assert ProjectJjkNobaraProfile.HAMMER_OVERHEAD_DAMAGE == 8.0f;
		assert ProjectJjkNobaraProfile.EMBEDDED_NAIL_DRIVE_DAMAGE == 4.0f;
	}

	private static void assertActionTimelinesExposeImpactAndBlackFlashWindow() {
		NobaraActionTimeline overhead = NobaraActionTimeline.OVERHEAD;
		assert overhead.impactTick() > 0;
		assert overhead.recoveryTicks() > overhead.impactTick();
		assert overhead.blackFlashStartTick() <= overhead.blackFlashEndTick();
		assert overhead.acceptsBlackFlashInput(overhead.blackFlashStartTick());
		assert overhead.acceptsBlackFlashInput(overhead.blackFlashEndTick());
		assert !overhead.acceptsBlackFlashInput(overhead.blackFlashStartTick() - 1);
	}
}
