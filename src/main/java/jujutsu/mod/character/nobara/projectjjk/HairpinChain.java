package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class HairpinChain {
	private final Mode mode;
	private final List<UUID> nailIds;
	private final int cadenceTicks;
	private final List<UUID> skippedTemporary = new ArrayList<>();
	private long nextDueGameTime;
	private int cursor;

	private HairpinChain(Mode mode, List<UUID> nailIds, long nextDueGameTime, int cadenceTicks) {
		this.mode = Objects.requireNonNull(mode, "mode");
		this.nailIds = List.copyOf(nailIds);
		this.nextDueGameTime = nextDueGameTime;
		if (cadenceTicks < 1) throw new IllegalArgumentException("cadenceTicks must be positive");
		this.cadenceTicks = cadenceTicks;
	}

	public static HairpinChain start(Mode mode, List<UUID> nailIds, long firstDueGameTime, int cadenceTicks) {
		return new HairpinChain(mode, nailIds, firstDueGameTime, cadenceTicks);
	}

	public Step poll(long gameTime, Function<UUID, Resolution> resolver) {
		if (gameTime < nextDueGameTime) return Step.waiting();
		while (cursor < nailIds.size()) {
			UUID nailId = nailIds.get(cursor++);
			Resolution resolution = Objects.requireNonNull(resolver.apply(nailId), "resolution");
			if (resolution == Resolution.TEMPORARILY_UNAVAILABLE) {
				skippedTemporary.add(nailId);
				continue;
			}
			if (resolution == Resolution.CONFIRMED_REMOVED || resolution == Resolution.INVALID) continue;
			boolean finale = !hasFutureResolvable(resolver);
			nextDueGameTime = gameTime + cadenceTicks;
			return Step.explode(nailId, finale);
		}
		return Step.complete();
	}

	private boolean hasFutureResolvable(Function<UUID, Resolution> resolver) {
		for (int index = cursor; index < nailIds.size(); index++) {
			if (resolver.apply(nailIds.get(index)) == Resolution.RESOLVED) return true;
		}
		return false;
	}

	public Mode mode() { return mode; }
	public List<UUID> nailIds() { return nailIds; }
	public List<UUID> skippedTemporary() { return List.copyOf(skippedTemporary); }

	public enum Mode { DIRECTED, MASS }
	public enum Resolution { RESOLVED, TEMPORARILY_UNAVAILABLE, CONFIRMED_REMOVED, INVALID }
	public enum StepKind { WAIT, EXPLODE, COMPLETE }

	public record Step(StepKind kind, UUID nailId, boolean finale) {
		private static Step waiting() { return new Step(StepKind.WAIT, null, false); }
		private static Step explode(UUID nailId, boolean finale) { return new Step(StepKind.EXPLODE, nailId, finale); }
		private static Step complete() { return new Step(StepKind.COMPLETE, null, false); }
	}
}
