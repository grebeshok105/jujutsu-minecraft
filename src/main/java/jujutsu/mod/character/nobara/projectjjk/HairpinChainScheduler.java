package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public final class HairpinChainScheduler<C> {
	private final List<Scheduled<C>> chains = new ArrayList<>();

	public void schedule(C context, HairpinChain chain) {
		chains.add(new Scheduled<>(context, chain));
	}

	public void tick(long gameTime, Resolver<C> resolver, Exploder<C> exploder, Finalizer<C> finalizer) {
		for (Iterator<Scheduled<C>> iterator = chains.iterator(); iterator.hasNext();) {
			Scheduled<C> scheduled = iterator.next();
			HairpinChain.Step step = scheduled.chain().poll(gameTime, id -> resolver.resolve(scheduled.context(), id));
			if (step.kind() == HairpinChain.StepKind.EXPLODE) {
				exploder.explode(scheduled.context(), scheduled.chain().mode(), step.nailId(), step.finale(), gameTime);
			} else if (step.kind() == HairpinChain.StepKind.COMPLETE) {
				if (step.nailId() != null) finalizer.finish(scheduled.context(), scheduled.chain().mode(), step.nailId(), gameTime);
				iterator.remove();
			}
		}
	}

	public void clear() { chains.clear(); }
	public int size() { return chains.size(); }

	@FunctionalInterface public interface Resolver<C> { HairpinChain.Resolution resolve(C context, UUID nailId); }
	@FunctionalInterface public interface Exploder<C> { void explode(C context, HairpinChain.Mode mode, UUID nailId, boolean finale, long gameTime); }
	@FunctionalInterface public interface Finalizer<C> { void finish(C context, HairpinChain.Mode mode, UUID nailId, long gameTime); }
	private record Scheduled<C>(C context, HairpinChain chain) {}
}
