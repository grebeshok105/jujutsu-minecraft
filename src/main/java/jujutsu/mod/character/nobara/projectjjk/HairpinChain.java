package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class HairpinChain {
	private final List<UUID> nailIds;
	private final List<UUID> pending;
	private final int cadenceTicks;
	private final List<UUID> skippedTemporary = new ArrayList<>();
	private long nextDueGameTime;
	private UUID lastSuccessful;

	private HairpinChain(List<UUID> nailIds, long nextDueGameTime, int cadenceTicks) {
		this.nailIds = List.copyOf(nailIds);
		this.pending = new ArrayList<>(nailIds);
		this.nextDueGameTime = nextDueGameTime;
		if (cadenceTicks < 1) throw new IllegalArgumentException("cadenceTicks must be positive");
		this.cadenceTicks = cadenceTicks;
	}

	public static HairpinChain start(List<UUID> nailIds, long firstDueGameTime, int cadenceTicks) {
		return new HairpinChain(nailIds, firstDueGameTime, cadenceTicks);
	}

	public Step poll(long gameTime, Function<UUID, Resolution> resolver) {
		if (gameTime < nextDueGameTime) return Step.waiting();
		int attempts = pending.size();
		while (attempts-- > 0 && !pending.isEmpty()) {
			UUID nailId = pending.removeFirst();
			Resolution resolution = Objects.requireNonNull(resolver.apply(nailId), "resolution");
			if (resolution == Resolution.TEMPORARILY_UNAVAILABLE) {
				if (!skippedTemporary.contains(nailId)) skippedTemporary.add(nailId);
				pending.add(nailId);
				continue;
			}
			if (resolution == Resolution.CONFIRMED_REMOVED || resolution == Resolution.INVALID) continue;
			lastSuccessful = nailId;
			nextDueGameTime = gameTime + cadenceTicks;
			return Step.explode(nailId, false);
		}
		if (!pending.isEmpty()) {
			nextDueGameTime = gameTime + cadenceTicks;
			return Step.waiting();
		}
		return Step.complete(lastSuccessful);
	}

	public List<UUID> nailIds() { return nailIds; }
	public List<UUID> skippedTemporary() { return List.copyOf(skippedTemporary); }

	public enum Resolution { RESOLVED, TEMPORARILY_UNAVAILABLE, CONFIRMED_REMOVED, INVALID }
	public enum StepKind { WAIT, EXPLODE, COMPLETE }

	public record Step(StepKind kind, UUID nailId, boolean finale) {
		private static Step waiting() { return new Step(StepKind.WAIT, null, false); }
		private static Step explode(UUID nailId, boolean finale) { return new Step(StepKind.EXPLODE, nailId, finale); }
		private static Step complete(UUID lastSuccessful) { return new Step(StepKind.COMPLETE, lastSuccessful, lastSuccessful != null); }
	}
}
