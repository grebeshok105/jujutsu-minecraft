package jujutsu.mod.character.nobara.projectjjk;

public record NobaraActionTimeline(
		int impactTick,
		int recoveryTicks,
		int blackFlashStartTick,
		int blackFlashEndTick
) {
	public static final int RESONANCE_SETUP_TICK = 0;
	public static final int RESONANCE_WINDUP_TICK = 10;
	public static final int RESONANCE_STRIKE_TICK = 24;
	public static final int RESONANCE_RELEASE_TICK = 30;

	public static final NobaraActionTimeline HORIZONTAL = aroundImpact(
			ProjectJjkNobaraProfile.HORIZONTAL_IMPACT_TICK,
			ProjectJjkNobaraProfile.HORIZONTAL_RECOVERY_TICKS
	);
	public static final NobaraActionTimeline OVERHEAD = aroundImpact(
			ProjectJjkNobaraProfile.OVERHEAD_IMPACT_TICK,
			ProjectJjkNobaraProfile.OVERHEAD_RECOVERY_TICKS
	);
	public static final NobaraActionTimeline NAIL_LAUNCH = aroundImpact(0, ProjectJjkNobaraProfile.NAIL_LAUNCH_RECOVERY_TICKS);
	public static final NobaraActionTimeline EMBEDDED_NAIL_DRIVE = HORIZONTAL;
	public static final NobaraActionTimeline DOLL_STRIKE = new NobaraActionTimeline(
			RESONANCE_STRIKE_TICK,
			ProjectJjkNobaraProfile.RESONANCE_TIMELINE_TICKS,
			RESONANCE_STRIKE_TICK,
			RESONANCE_RELEASE_TICK
	);
	public static final NobaraActionTimeline SELF_RESONANCE = aroundImpact(
			ProjectJjkNobaraProfile.SELF_RESONANCE_WINDUP_TICKS,
			ProjectJjkNobaraProfile.RITUAL_RECOVERY_TICKS
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
