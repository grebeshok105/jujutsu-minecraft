package jujutsu.mod.character.todo;

import jujutsu.mod.character.todo.TodoSwapMomentum.Spend;

/**
 * When a landed swap's window is spent. Every row here is a way a player could feel cheated: a buff eaten
 * by a miss, by a shielded hit, or by the same swing seen twice through Black Flash's re-entrant bonus.
 */
public final class TodoSwapMomentumTest {
	private TodoSwapMomentumTest() {}

	public static void main(String[] args) {
		assertOnlyAConfirmedMeleeHitSpendsIt();
		assertTheBlackFlashBonusCannotEatIt();
		assertTheWindowClosesBeforeAnotherSwapCanOpenOne();
		assertTheBonusIsAnOpeningRatherThanAKill();
		System.out.println("TodoSwapMomentumTest passed");
	}

	private static void assertOnlyAConfirmedMeleeHitSpendsIt() {
		assert decide(true) == Spend.SPEND : "an ordinary confirmed melee hit must spend the window";

		assert TodoSwapMomentum.decide(false, 4.0f, false, true, true, true, false) == Spend.IGNORE
				: "with no window open there is nothing to spend";
		// A miss never reaches the damage event at all, so the interesting near-misses are these two:
		assert TodoSwapMomentum.decide(true, 4.0f, false, true, true, true, true) == Spend.IGNORE
				: "a shielded hit is not the hit the swap bought";
		assert TodoSwapMomentum.decide(false, 0.0f, false, true, true, true, true) == Spend.IGNORE
				: "a hit that dealt nothing must not close the window";
		assert TodoSwapMomentum.decide(false, -1.0f, false, true, true, true, true) == Spend.IGNORE
				: "negative damage is not a hit either";

		assert TodoSwapMomentum.decide(false, 4.0f, false, false, true, true, true) == Spend.IGNORE
				: "an arrow he fired is not the melee this window is for";
		assert TodoSwapMomentum.decide(false, 4.0f, false, true, false, true, true) == Spend.IGNORE
				: "a player who is no longer Todo must not spend a Todo window";
		assert TodoSwapMomentum.decide(false, 4.0f, false, true, true, false, true) == Spend.IGNORE
				: "a dead or spectating attacker must not spend it";
	}

	private static void assertTheBlackFlashBonusCannotEatIt() {
		// Black Flash applies its extra damage by calling hurtServer again from inside the damage event, so
		// the listener sees the same swing twice. Spending on the nested pass would tie the stagger and the
		// cue to whichever pass a hidden ten-percent roll produced -- not a crash, just nondeterminism the
		// player would experience as "sometimes my buff does nothing".
		assert TodoSwapMomentum.decide(false, 4.0f, true, true, true, true, true) == Spend.IGNORE
				: "the re-entrant bonus hit is the same swing and must not eat the window";
		assert decide(true) == Spend.SPEND
				: "the base pass of that same swing must still spend it";
	}

	private static void assertTheWindowClosesBeforeAnotherSwapCanOpenOne() {
		// This is what makes "refreshes rather than stacks" structurally unreachable instead of merely
		// unlikely: the swap cannot come off cooldown while a window is still live, so two grants never
		// overlap. Lower the cooldown or raise the window and this fails loudly rather than quietly
		// becoming a stacking bug.
		assert TodoProfile.SWAP_MOMENTUM_WINDOW_TICKS < TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS
				: "the window must close before the swap is castable again";
		assert TodoProfile.SWAP_MOMENTUM_WINDOW_TICKS < TodoProfile.MARKER_SWAP_COOLDOWN_TICKS
				: "the mark route must not be a way around that";
		assert TodoProfile.SWAP_MOMENTUM_WINDOW_TICKS >= 20 && TodoProfile.SWAP_MOMENTUM_WINDOW_TICKS <= 30
				: "the window is a beat, not a stance";
	}

	private static void assertTheBonusIsAnOpeningRatherThanAKill() {
		assert TodoProfile.SWAP_MOMENTUM_DAMAGE_MULTIPLIER == 1.25 : "the damage bonus must stay centralized";
		assert TodoProfile.SWAP_MOMENTUM_STAGGER_TICKS == 8 : "the stagger must stay centralized";
		assert TodoProfile.SWAP_MOMENTUM_STAGGER_TICKS > 0
				: "a window with no stagger would be worth almost nothing on the bare fists the clap requires";
		assert TodoProfile.SWAP_MOMENTUM_STAGGER_TICKS < TodoProfile.BLACK_FLASH_STAGGER_TICKS
				: "an earned opening must still read as smaller than a Black Flash";
		assert TodoProfile.SWAP_MOMENTUM_DAMAGE_MULTIPLIER < TodoProfile.BLACK_FLASH_DAMAGE_MULTIPLIER
				: "and must not out-hit one either";
	}

	private static Spend decide(boolean hasMomentum) {
		return TodoSwapMomentum.decide(false, 4.0f, false, true, true, true, hasMomentum);
	}
}
