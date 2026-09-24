package jujutsu.mod.character.megumi;

/**
 * Pure decisions for the Tiger Funeral (authorial kit): the committed three-beat combo's resolve
 * timing, reach/arc connect tests, damage/stagger/knockback tables and the no-retarget pin. No
 * Minecraft dependencies — the brain feeds it facts.
 */
final class MegumiTigerPolicy {
	private MegumiTigerPolicy() {}

	/** The three beats of the committed combo, in order. */
	enum Strike {
		STRIKE_1,
		STRIKE_2,
		FINISHER;

		/** The 1-based beat number the {@code TIGER_STRIKE} cue carries in its intensity. */
		int beat() {
			return ordinal() + 1;
		}

		/** The beat after this one, or null once the finisher has swung — the combo is spent. */
		Strike next() {
			return switch (this) {
				case STRIKE_1 -> STRIKE_2;
				case STRIKE_2 -> FINISHER;
				case FINISHER -> null;
			};
		}
	}

	/** What the brain does once a beat resolves. */
	enum ComboAction {
		/** Move to the next beat of the sequence. */
		NEXT_BEAT,
		/** Collapse the sequence into recovery. */
		RECOVER
	}

	/**
	 * What the brain knows about the locked target at the moment a beat resolves:
	 * {@code horizontalDistance} is the feet-to-feet horizontal gap and {@code yawDeltaDeg} the
	 * wrapped degrees between the tiger's frozen facing and the bearing to the target.
	 */
	record ComboFacts(Strike strike, boolean targetAlive, boolean sameLevel,
			double horizontalDistance, double yawDeltaDeg) {}

	/** A beat connects only on a live, same-level target still inside the frozen facing arc. */
	static boolean strikeConnects(ComboFacts facts) {
		return facts.targetAlive() && facts.sameLevel()
				&& facts.horizontalDistance() <= rangeFor(facts.strike())
				&& inArc(facts.strike(), facts.yawDeltaDeg());
	}

	/**
	 * After a resolved beat: a connect advances the sequence, a miss on a target still present
	 * keeps the committed swings coming (the next beat may still land), a miss on a gone target
	 * collapses to recovery, and the finisher always ends the combo either way.
	 */
	static ComboAction afterBeat(Strike strike, boolean connected, boolean targetPresent) {
		if (strike.next() == null) {
			return ComboAction.RECOVER;
		}
		if (connected) {
			return ComboAction.NEXT_BEAT;
		}
		return targetPresent ? ComboAction.NEXT_BEAT : ComboAction.RECOVER;
	}

	/** The yaw window a beat covers: |facing−bearing| must stay inside half of it. */
	static boolean inArc(Strike strike, double yawDeltaDeg) {
		return Math.abs(yawDeltaDeg) <= arcDegFor(strike) * 0.5;
	}

	static int windupTicksFor() {
		return MegumiShikigamiProfile.TIGER_COMBO_WINDUP_TICKS;
	}

	static int recoveryTicks() {
		return MegumiShikigamiProfile.TIGER_RECOVERY_TICKS;
	}

	static int comboCooldownTicks() {
		return MegumiShikigamiProfile.TIGER_COMBO_COOLDOWN_TICKS;
	}

	static int resolveTicksFor(Strike strike) {
		return switch (strike) {
			case STRIKE_1 -> MegumiShikigamiProfile.TIGER_STRIKE1_RESOLVE_TICKS;
			case STRIKE_2 -> MegumiShikigamiProfile.TIGER_STRIKE2_RESOLVE_TICKS;
			case FINISHER -> MegumiShikigamiProfile.TIGER_FINISHER_RESOLVE_TICKS;
		};
	}

	static double rangeFor(Strike strike) {
		return switch (strike) {
			case STRIKE_1 -> MegumiShikigamiProfile.TIGER_STRIKE1_RANGE;
			case STRIKE_2 -> MegumiShikigamiProfile.TIGER_STRIKE2_RANGE;
			case FINISHER -> MegumiShikigamiProfile.TIGER_FINISHER_RANGE;
		};
	}

	static double arcDegFor(Strike strike) {
		return switch (strike) {
			case STRIKE_1 -> MegumiShikigamiProfile.TIGER_STRIKE1_ARC_DEG;
			case STRIKE_2 -> MegumiShikigamiProfile.TIGER_STRIKE2_ARC_DEG;
			case FINISHER -> MegumiShikigamiProfile.TIGER_FINISHER_ARC_DEG;
		};
	}

	static double damageFor(Strike strike) {
		return switch (strike) {
			case STRIKE_1 -> MegumiShikigamiProfile.TIGER_STRIKE1_DAMAGE;
			case STRIKE_2 -> MegumiShikigamiProfile.TIGER_STRIKE2_DAMAGE;
			case FINISHER -> MegumiShikigamiProfile.TIGER_FINISHER_DAMAGE;
		};
	}

	static int staggerTicksFor(Strike strike) {
		return switch (strike) {
			case STRIKE_1 -> MegumiShikigamiProfile.TIGER_STRIKE1_STAGGER_TICKS;
			case STRIKE_2 -> MegumiShikigamiProfile.TIGER_STRIKE2_STAGGER_TICKS;
			case FINISHER -> MegumiShikigamiProfile.TIGER_FINISHER_STAGGER_TICKS;
		};
	}

	/**
	 * Horizontal shove behind a connected beat: a nudge on the quick hits, the plan's named
	 * knockback on the finisher.
	 */
	static double knockbackFor(Strike strike) {
		return switch (strike) {
			case STRIKE_1 -> 0.3;
			case STRIKE_2 -> 0.5;
			case FINISHER -> MegumiShikigamiProfile.TIGER_FINISHER_KNOCKBACK;
		};
	}

	/** Vertical velocity a connected beat adds — only the finisher launches. */
	static double liftFor(Strike strike) {
		return strike == Strike.FINISHER ? MegumiShikigamiProfile.TIGER_FINISHER_LIFT : 0.0;
	}

	/**
	 * Pinned (§C): the locked target identity never changes inside a sequence. A mutation that
	 * retargets mid-combo must fail this before any GameTest loads.
	 */
	static boolean retargetDuringCombo() {
		return false;
	}
}
