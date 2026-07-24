package jujutsu.mod.character.todo;

/** Locks the first Todo slice values into one readable profile. */
public final class TodoProfileTest {
	private TodoProfileTest() {}

	public static void main(String[] args) {
		assert TodoProfile.MELEE_DAMAGE_MULTIPLIER == 1.50 : "Todo melee identity must stay centralized";
		assert TodoProfile.ATTACK_SPEED_MULTIPLIER == 0.85 : "Todo attack speed must be slower but usable";
		assert TodoProfile.STAGGER_DURATION_MULTIPLIER == 0.50 : "Todo stagger resistance must reduce rather than remove stagger";
		assert TodoProfile.BOOGIE_WOOGIE_RANGE == 24.0 : "Boogie Woogie range must match the approved design";
		assert TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS == 60 : "Boogie Woogie cooldown must be three seconds";
		assert TodoProfile.SAFE_POSITION_UPWARD_BLOCKS == 3;
		assert TodoProfile.SAFE_POSITION_HORIZONTAL_RADIUS == 1.0;
		System.out.println("TodoProfileTest passed");
	}
}
