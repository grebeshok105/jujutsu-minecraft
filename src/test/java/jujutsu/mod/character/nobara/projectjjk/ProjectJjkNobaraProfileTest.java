package jujutsu.mod.character.nobara.projectjjk;

public final class ProjectJjkNobaraProfileTest {
	private ProjectJjkNobaraProfileTest() {}

	public static void main(String[] args) {
		assertTapPreparesOneNail();
		assertShortHoldPreparesTripleNails();
		assertLongHoldPreparesBarrageNails();
		assertWorldNailsPersistLikeProjectJjkReference();
		assertNailsLaunchWithTwoTickStagger();
		assertCursedEnergyEconomy();
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

	private static void assertCursedEnergyEconomy() {
		assert ProjectJjkNobaraProfile.CE_MAX == 100.0f : "cursed energy pool should cap at 100";
		assert ProjectJjkNobaraProfile.detonateDamage(0) == ProjectJjkNobaraProfile.DETONATE_DAMAGE_BASE : "zero marks detonate for the base amount";
		assert ProjectJjkNobaraProfile.detonateDamage(4) > ProjectJjkNobaraProfile.detonateDamage(1) : "more marks must detonate harder";
		assert ProjectJjkNobaraProfile.resonanceDamage(4) > ProjectJjkNobaraProfile.resonanceDamage(0) : "resonance must scale with marks";
	}
}
