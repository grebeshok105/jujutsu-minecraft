package jujutsu.mod.character.nobara.projectjjk;

public record NobaraActionTimeline(
		int impactTick,
		int recoveryTicks,
		int blackFlashStartTick,
		int blackFlashEndTick
) {
	public static final NobaraActionTimeline HORIZONTAL = aroundImpact(
			ProjectJjkNobaraProfile.HORIZONTAL_IMPACT_TICK,
			ProjectJjkNobaraProfile.HORIZONTAL_RECOVERY_TICKS
	);
	public static final NobaraActionTimeline OVERHEAD = aroundImpact(
			ProjectJjkNobaraProfile.OVERHEAD_IMPACT_TICK,
			ProjectJjkNobaraProfile.OVERHEAD_RECOVERY_TICKS
	);

	public NobaraActionTimeline {
		if (impactTick < 0 || recoveryTicks < impactTick || blackFlashStartTick > blackFlashEndTick) {
			throw new IllegalArgumentException("Invalid Nobara action timeline");
		}
	}

	public boolean acceptsBlackFlashInput(int actionTick) {
		return actionTick >= blackFlashStartTick && actionTick <= blackFlashEndTick;
	}

	private static NobaraActionTimeline aroundImpact(int impactTick, int recoveryTicks) {
		return new NobaraActionTimeline(
				impactTick,
				recoveryTicks,
				impactTick + ProjectJjkNobaraProfile.BLACK_FLASH_WINDOW_EARLY_TICKS,
				impactTick + ProjectJjkNobaraProfile.BLACK_FLASH_WINDOW_LATE_TICKS
		);
	}
}
