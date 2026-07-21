package jujutsu.mod.character.nobara.projectjjk;

/**
 * Briefly lowers the authoritative server tick rate and restores the previous value safely.
 * Used for Resonance hit-stop (server-side time slow) after a successful cast.
 */
public final class ServerTimeDilation {
	private static final float RATE_EPSILON = 0.001f;
	private float restoreRate;
	private float appliedRate;
	private int remainingTicks;

	public void trigger(TickRateAccess access, float targetRate, int durationTicks) {
		if (durationTicks <= 0) {
			return;
		}
		float requestedRate = Math.max(1.0f, targetRate);
		if (remainingTicks <= 0) {
			restoreRate = access.tickRate();
			appliedRate = requestedRate;
		} else {
			// Overlap: keep the slower of the two rates, extend the window.
			appliedRate = Math.min(appliedRate, requestedRate);
		}
		remainingTicks = Math.max(remainingTicks, durationTicks);
		access.setTickRate(appliedRate);
	}

	public void tick(TickRateAccess access) {
		if (remainingTicks <= 0 || --remainingTicks > 0) {
			return;
		}
		restore(access);
	}

	public void clear(TickRateAccess access) {
		if (remainingTicks > 0) {
			restore(access);
		}
	}

	private void restore(TickRateAccess access) {
		// Only restore if nobody else changed the rate while we held it.
		if (Math.abs(access.tickRate() - appliedRate) <= RATE_EPSILON) {
			access.setTickRate(restoreRate);
		}
		remainingTicks = 0;
		appliedRate = 0.0f;
		restoreRate = 0.0f;
	}

	public interface TickRateAccess {
		float tickRate();

		void setTickRate(float tickRate);
	}
}
