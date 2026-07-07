package jujutsu.mod.character.nobara;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NobaraCombatStateManager {
	public static final int MAX_PREPARED_NAILS = 4;
	public static final int PREPARED_TTL_TICKS = 100;
	public static final int WINDUP_TICKS = 8;
	public static final int FLIGHT_TICKS = 10;
	public static final int RECOVERY_TICKS = 14;
	public static final int COOLDOWN_TICKS = 70;

	private final Map<UUID, State> states = new HashMap<>();

	public enum Phase {
		IDLE,
		WINDUP,
		FLIGHT,
		RECOVERY,
		COOLDOWN
	}

	public enum RejectReason {
		NONE,
		NO_PREPARED_NAILS,
		COOLDOWN
	}

	public record PrepareResult(int preparedCount, int consumedCount) {}

	public record LaunchResult(boolean accepted, RejectReason reason, int nailCount, long windupEndsAt, long impactAt) {
		public static LaunchResult rejected(RejectReason reason) {
			return new LaunchResult(false, reason, 0, -1L, -1L);
		}
	}

	public PrepareResult prepareNails(UUID playerId, String dimension, long gameTime, int availableNails, boolean creative) {
		State state = state(playerId);
		state.clearIfDimensionChanged(dimension);
		int preparedCount = Math.min(MAX_PREPARED_NAILS, Math.max(0, availableNails));
		state.preparedCount = preparedCount;
		state.preparedAt = gameTime;
		state.dimension = dimension;
		return new PrepareResult(preparedCount, creative ? 0 : preparedCount);
	}

	public LaunchResult startHairpin(UUID playerId, String dimension, long gameTime) {
		State state = state(playerId);
		state.clearIfDimensionChanged(dimension);
		if (state.phaseAt(gameTime) != Phase.IDLE) {
			return LaunchResult.rejected(RejectReason.COOLDOWN);
		}
		int nailCount = state.preparedCount(gameTime);
		if (nailCount <= 0) {
			state.clearPrepared();
			return LaunchResult.rejected(RejectReason.NO_PREPARED_NAILS);
		}

		long windupEndsAt = gameTime + WINDUP_TICKS;
		long impactAt = windupEndsAt + FLIGHT_TICKS;
		state.preparedCount = 0;
		state.windupStartedAt = gameTime;
		state.windupEndsAt = windupEndsAt;
		state.impactAt = impactAt;
		state.recoveryEndsAt = impactAt + RECOVERY_TICKS;
		state.cooldownEndsAt = impactAt + COOLDOWN_TICKS;
		return new LaunchResult(true, RejectReason.NONE, nailCount, windupEndsAt, impactAt);
	}

	public void markImpactResolved(UUID playerId, long gameTime) {
		state(playerId).impactResolvedAt = gameTime;
	}

	public State state(UUID playerId) {
		return states.computeIfAbsent(playerId, ignored -> new State());
	}

	public void clear(UUID playerId) {
		states.remove(playerId);
	}

	public static final class State {
		private String dimension;
		private int preparedCount;
		private long preparedAt = -1L;
		private long windupStartedAt = -1L;
		private long windupEndsAt = -1L;
		private long impactAt = -1L;
		private long recoveryEndsAt = -1L;
		private long cooldownEndsAt = -1L;
		private long impactResolvedAt = -1L;

		public int preparedCount(long gameTime) {
			if (preparedCount <= 0) {
				return 0;
			}
			if (gameTime - preparedAt > PREPARED_TTL_TICKS) {
				clearPrepared();
				return 0;
			}
			return preparedCount;
		}

		public Phase phaseAt(long gameTime) {
			if (windupStartedAt >= 0L && gameTime < windupEndsAt) {
				return Phase.WINDUP;
			}
			if (windupEndsAt >= 0L && gameTime < impactAt) {
				return Phase.FLIGHT;
			}
			if (impactAt >= 0L && gameTime < recoveryEndsAt) {
				return Phase.RECOVERY;
			}
			if (recoveryEndsAt >= 0L && gameTime < cooldownEndsAt) {
				return Phase.COOLDOWN;
			}
			return Phase.IDLE;
		}

		public long cooldownEndsAt() {
			return cooldownEndsAt;
		}

		public long impactAt() {
			return impactAt;
		}

		public long impactResolvedAt() {
			return impactResolvedAt;
		}

		public String dimension() {
			return dimension == null ? "" : dimension;
		}

		private void clearIfDimensionChanged(String currentDimension) {
			if (dimension != null && !dimension.equals(currentDimension)) {
				clearPrepared();
			}
			dimension = currentDimension;
		}

		private void clearPrepared() {
			preparedCount = 0;
			preparedAt = -1L;
		}
	}
}
