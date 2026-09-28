package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Deterministic cadence and resolution policy for one scheduled Hairpin chain. */
public final class HairpinChain {
	private final List<UUID> nailIds;
	private final ArrayDeque<UUID> pending;
	private final int cadenceTicks;
	private final List<UUID> skippedTemporary = new ArrayList<>();
	private final Map<UUID, Long> temporarySince = new HashMap<>();
	private long nextDueGameTime;
	private UUID lastSuccessful;
	private int successfulCount;

	private HairpinChain(List<UUID> nailIds, long nextDueGameTime, int cadenceTicks) {
		Objects.requireNonNull(nailIds, "nailIds");
		if (cadenceTicks < 1) throw new IllegalArgumentException("cadenceTicks must be positive");
		this.nailIds = List.copyOf(nailIds);
		this.pending = new ArrayDeque<>(nailIds);
		this.nextDueGameTime = nextDueGameTime;
		this.cadenceTicks = cadenceTicks;
	}

	public static HairpinChain start(List<UUID> nailIds, long firstDueGameTime, int cadenceTicks) {
		return new HairpinChain(nailIds, firstDueGameTime, cadenceTicks);
	}

	/**
	 * Polls at most one successful detonation per cadence. Resolved entries detonate immediately;
	 * confirmed removals and invalid entries are dropped immediately; temporary entries rotate behind
	 * the rest of the chain until their first-temporary deadline reaches exactly 20 ticks.
	 */
	public Step poll(long gameTime, Function<UUID, Resolution> resolver) {
		Objects.requireNonNull(resolver, "resolver");
		if (gameTime < nextDueGameTime) return Step.waiting();

		int attempts = pending.size();
		while (attempts-- > 0 && !pending.isEmpty()) {
			UUID nailId = pending.removeFirst();
			Long firstUnavailable = temporarySince.get(nailId);
			if (firstUnavailable != null
					&& gameTime - firstUnavailable >= ProjectJjkNobaraProfile.TEMP_RETRY_DEADLINE_TICKS) {
				temporarySince.remove(nailId);
				continue;
			}
			Resolution resolution = Objects.requireNonNull(resolver.apply(nailId), "resolution");
			switch (resolution) {
				case RESOLVED -> {
					temporarySince.remove(nailId);
					lastSuccessful = nailId;
					successfulCount++;
					nextDueGameTime = gameTime + cadenceTicks;
					return Step.explode(nailId, false);
				}
				case TEMPORARILY_UNAVAILABLE -> {
					temporarySince.computeIfAbsent(nailId, ignored -> {
						skippedTemporary.add(nailId);
						return gameTime;
					});
					pending.addLast(nailId);
				}
				case CONFIRMED_REMOVED, INVALID -> {
					temporarySince.remove(nailId);
				}
			}
		}

		if (!pending.isEmpty()) {
			nextDueGameTime = gameTime + cadenceTicks;
			return Step.waiting();
		}
		return Step.complete(lastSuccessful);
	}

	public List<UUID> nailIds() { return nailIds; }
	public List<UUID> skippedTemporary() { return List.copyOf(skippedTemporary); }
	public int successfulCount() { return successfulCount; }

	public enum Resolution { RESOLVED, TEMPORARILY_UNAVAILABLE, CONFIRMED_REMOVED, INVALID }
	public enum StepKind { WAIT, EXPLODE, COMPLETE }

	public record Step(StepKind kind, UUID nailId, boolean finale) {
		private static Step waiting() { return new Step(StepKind.WAIT, null, false); }
		private static Step explode(UUID nailId, boolean finale) { return new Step(StepKind.EXPLODE, nailId, finale); }
		private static Step complete(UUID lastSuccessful) { return new Step(StepKind.COMPLETE, lastSuccessful, lastSuccessful != null); }
	}
}
