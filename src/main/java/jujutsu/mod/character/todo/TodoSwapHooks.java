package jujutsu.mod.character.todo;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.JujutsuMod;

/** Todo-owned event seam for successful, committed swaps. */
public final class TodoSwapHooks {
	@FunctionalInterface
	public interface CommitListener {
		void afterCommit(ServerPlayer player, SwapKind kind, SwapOutcome outcome, boolean automatic);
	}

	private static final CopyOnWriteArrayList<CommitListener> COMMIT_LISTENERS = new CopyOnWriteArrayList<>();
	private static volatile ToIntFunction<ServerPlayer> beatProvider = player -> 0;

	private TodoSwapHooks() {}

	public static void registerCommitListener(CommitListener listener) {
		COMMIT_LISTENERS.add(Objects.requireNonNull(listener, "listener"));
	}

	public static void registerBeatProvider(ToIntFunction<ServerPlayer> provider) {
		beatProvider = Objects.requireNonNull(provider, "provider");
	}

	/** Returns Beat 0 until the rhythm runtime installs its provider. */
	public static int beatOf(ServerPlayer player) {
		if (player == null) {
			return 0;
		}
		try {
			return Math.max(0, beatProvider.applyAsInt(player));
		} catch (RuntimeException exception) {
			JujutsuMod.LOGGER.warn("Todo beat provider failed; falling back to Beat 0", exception);
			return 0;
		}
	}

	/** Notifies listeners only after a successful atomic commit. */
	public static void fireAfterCommit(ServerPlayer player, SwapKind kind, SwapOutcome outcome, boolean automatic) {
		if (player == null || kind == null || outcome == null || !outcome.success()) {
			return;
		}
		for (CommitListener listener : COMMIT_LISTENERS) {
			try {
				listener.afterCommit(player, kind, outcome, automatic);
			} catch (RuntimeException exception) {
				// A passive listener must not turn a committed swap into a gameplay failure.
				JujutsuMod.LOGGER.error("Todo swap commit listener failed for {}", kind, exception);
			}
		}
	}
}
