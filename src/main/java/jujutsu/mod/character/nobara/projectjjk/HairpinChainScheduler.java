package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.ToLongFunction;

public final class HairpinChainScheduler<C> {
	private final List<Scheduled<C>> chains = new ArrayList<>();

	public void schedule(C context, HairpinChain chain) {
		chains.add(new Scheduled<>(context, chain));
	}

	/** Clock is resolved per context so chains in different dimensions tick on their own level's game time. */
	public void tick(ToLongFunction<C> clock, Resolver<C> resolver, Exploder<C> exploder, Finalizer<C> finalizer) {
		for (Iterator<Scheduled<C>> iterator = chains.iterator(); iterator.hasNext();) {
			Scheduled<C> scheduled = iterator.next();
			long gameTime = clock.applyAsLong(scheduled.context());
			HairpinChain.Step step = scheduled.chain().poll(gameTime, id -> resolver.resolve(scheduled.context(), id));
			if (step.kind() == HairpinChain.StepKind.EXPLODE) {
				exploder.explode(scheduled.context(), step.nailId(), step.finale(), gameTime);
			} else if (step.kind() == HairpinChain.StepKind.COMPLETE) {
				if (step.nailId() != null) finalizer.finish(scheduled.context(), step.nailId(), gameTime);
				iterator.remove();
			}
		}
	}
	public void clear() { chains.clear(); }
	public void removeIf(java.util.function.Predicate<C> predicate) { chains.removeIf(scheduled -> predicate.test(scheduled.context())); }
	public int size() { return chains.size(); }

	@FunctionalInterface public interface Resolver<C> { HairpinChain.Resolution resolve(C context, UUID nailId); }
	@FunctionalInterface public interface Exploder<C> { void explode(C context, UUID nailId, boolean finale, long gameTime); }
	@FunctionalInterface public interface Finalizer<C> { void finish(C context, UUID nailId, long gameTime); }
	private record Scheduled<C>(C context, HairpinChain chain) {}
}
