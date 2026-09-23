package jujutsu.mod.character.todo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCues;

/** Server-authoritative Boogie Rhythm state machine and Revised auto-swap scheduler. */
public final class TodoRhythmRuntime {
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	private TodoRhythmRuntime() {}

	/** Installs the successful-commit listener and the beat provider once. */
	public static void register() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		TodoSwapHooks.registerBeatProvider(TodoRhythmRuntime::beatOf);
		TodoSwapHooks.registerCommitListener(TodoRhythmRuntime::afterCommit);
	}

	/** Decays inactive rhythm, expires Revised, and runs due quota-reserved auto-swaps. */
	public static void serverTick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		for (UUID owner : TodoTransientState.owners()) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player == null || !player.isAlive()) {
				continue;
			}
			long now = player.level().getGameTime();
			TodoRhythmState state = TodoTransientState.rhythm(owner).orElse(null);
			if (state == null) {
				continue;
			}

			if (state.revisedUntilGameTime() > 0 && now >= state.revisedUntilGameTime()) {
				TodoTransientState.setRhythm(owner, null);
				emitRhythmState(player, TodoRhythmState.ZERO, now);
				continue;
			}

			TodoRhythmState current = state;
			if (!current.revisedAt(now) && !current.peakArmed()
					&& shouldDecay(current, now)) {
				int beforeBeat = current.beat();
				current = current.withPoints(current.points() - 1);
				TodoTransientState.setRhythm(owner, current);
				if (current.beat() != beforeBeat) {
					emitRhythmState(player, current, now);
				}
			}
			if (current.revisedAt(now) && !current.pending().isEmpty()) {
				processPending(server, player, current, now);
			}
		}
	}

	/** Records one successful swap's variety contribution. */
	public static void recordSwap(ServerPlayer player, SwapKind kind, long gameTime) {
		if (player == null || kind == null) {
			return;
		}
		TodoRhythmState before = stateOf(player.getUUID());
		TodoRhythmState after = advance(before, kind, gameTime);
		TodoTransientState.setRhythm(player.getUUID(), after);

		if (before.beatAt(gameTime) < 4 && after.beatAt(gameTime) >= 4) {
			JujutsuNetworking.broadcastVfxCue(player.level(), player.position(), TodoProfile.VFX_DELIVERY_RADIUS,
					VfxCues.anchored(TodoVfxIds.RHYTHM_PEAK, player.position(), player.getId(), player.position(),
							1, gameTime, player.getRandom().nextLong()));
		}
		if (before.beatAt(gameTime) != after.beatAt(gameTime)) {
			emitRhythmState(player, after, gameTime);
		}
	}

	/** Beat provider exposed to the swap pipeline; absent owners are Beat 0. */
	public static int beatOf(ServerPlayer player) {
		if (player == null) {
			return 0;
		}
		TodoRhythmState state = stateOf(player.getUUID());
		return state.beatAt(player.level().getGameTime());
	}

	/** Pure state overload used by unit tests and by the server provider. */
	public static int beatOf(TodoRhythmState state) {
		return state == null ? 0 : state.beat();
	}

	public static boolean isRevised(ServerPlayer player) {
		if (player == null) {
			return false;
		}
		return revisedRemainingTicks(player) > 0;
	}

	public static int revisedRemainingTicks(ServerPlayer player) {
		if (player == null) {
			return 0;
		}
		long now = player.level().getGameTime();
		long remaining = stateOf(player.getUUID()).revisedUntilGameTime() - now;
		return remaining <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, remaining);
	}

	/** Returns an immutable all-zero record when no owner entry exists. */
	public static TodoRhythmState stateOf(UUID owner) {
		if (owner == null) {
			return TodoRhythmState.ZERO;
		}
		return TodoTransientState.rhythm(owner).orElse(TodoRhythmState.ZERO);
	}

	/** Pure one-swap transition; the commit listener owns Peak consumption separately. */
	static TodoRhythmState advance(TodoRhythmState before, SwapKind kind, long gameTime) {
		TodoRhythmState state = before == null ? TodoRhythmState.ZERO : before;
		int gain = (kind != state.lastKind() ? 1 : 0)
				+ (state.recent().contains(kind) ? 0 : 1);
		List<SwapKind> recent = new ArrayList<>(state.recent().size() + 1);
		recent.add(kind);
		recent.addAll(state.recent());
		if (recent.size() > TodoProfile.RHYTHM_RECENT_KINDS) {
			recent = new ArrayList<>(recent.subList(0, TodoProfile.RHYTHM_RECENT_KINDS));
		}
		int points = state.points() + gain;
		boolean peakArmed = state.peakArmed()
				|| (state.beat() < 4 && points >= TodoProfile.RHYTHM_PEAK_POINTS);
		return state.withSwap(kind, recent, gameTime, points, peakArmed);
	}

	/** Pure decay schedule: grace is strict, the first tick is grace+interval, and armed Peak or an open
	 * Revised window freezes the rhythm entirely — the caller checks the same gates before ticking. */
	static boolean shouldDecay(TodoRhythmState state, long now) {
		long elapsed = now - state.lastSwapGameTime();
		return state.points() > 0
				&& !state.peakArmed()
				&& !state.revisedAt(now)
				&& elapsed > TodoProfile.RHYTHM_DECAY_DELAY_TICKS
				&& (elapsed - TodoProfile.RHYTHM_DECAY_DELAY_TICKS) % TodoProfile.RHYTHM_DECAY_INTERVAL_TICKS == 0;
	}

	/** Pure auto-swap ground predicate used by tests and the live plan gate. */
	public static boolean hasGroundWithin(boolean[] solidBelow) {
		if (solidBelow == null) {
			return false;
		}
		for (boolean solid : solidBelow) {
			if (solid) {
				return true;
			}
		}
		return false;
	}

	private static void afterCommit(ServerPlayer player, SwapKind kind, SwapOutcome outcome, boolean automatic) {
		if (player == null || kind == null || outcome == null || !outcome.success()) {
			return;
		}
		long now = player.level().getGameTime();
		TodoRhythmState before = stateOf(player.getUUID());
		recordSwap(player, kind, now);
		TodoRhythmState current = stateOf(player.getUUID());

		// Peak is consumed by the next successful swap, regardless of the automatic flag. Revised auto-swaps
		// are only scheduled while Revised is already open, so they can never mint another Revised window.
		if (before.peakArmed()) {
			current = current.withRevised(now + TodoProfile.REVISED_DURATION_TICKS, 0, List.of());
			TodoTransientState.setRhythm(player.getUUID(), current);
			JujutsuNetworking.broadcastVfxCue(player.level(), player.position(), TodoProfile.VFX_DELIVERY_RADIUS,
					VfxCues.anchored(TodoVfxIds.REVISED_START, player.position(), player.getId(), player.position(),
							1, now, player.getRandom().nextLong()));
			emitRhythmState(player, current, now);
		}

		current = stateOf(player.getUUID());
		if (current.revisedAt(now) && !automatic
				&& current.autoSwapsUsed() < TodoProfile.REVISED_AUTO_SWAPS_MAX) {
			List<PendingAutoSwap> pending = new ArrayList<>(current.pending());
			pending.add(new PendingAutoSwap(null, now + TodoProfile.REVISED_AUTO_SWAP_DELAY_TICKS));
			TodoTransientState.setRhythm(player.getUUID(), current.withAutoSwaps(current.autoSwapsUsed() + 1, pending));
		}
	}

	private static void processPending(MinecraftServer server, ServerPlayer player, TodoRhythmState state, long now) {
		List<PendingAutoSwap> due = new ArrayList<>();
		List<PendingAutoSwap> remaining = new ArrayList<>();
		for (PendingAutoSwap pending : state.pending()) {
			if (pending.executeAtGameTime() <= now) {
				due.add(pending);
			} else {
				remaining.add(pending);
			}
		}
		if (due.isEmpty()) {
			return;
		}
		TodoTransientState.setRhythm(player.getUUID(), state.withAutoSwaps(state.autoSwapsUsed(), remaining));
		for (PendingAutoSwap ignored : due) {
			executePending(server, player, now);
		}
	}

	private static void executePending(MinecraftServer server, ServerPlayer player, long now) {
		if (!player.isAlive() || player.isSpectator()
				|| CharacterSelectionManager.selected(player) != JujutsuCharacter.TODO
				|| !isRevised(player)) {
			return;
		}
		Optional<BodyNode> target = SwapNodes.aimed(player, TodoProfile.BOOGIE_WOOGIE_RANGE)
				.or(() -> SwapNodes.marked(player));
		if (target.isEmpty()) {
			return;
		}
		// Auto-swaps are deliberately strict for both bodies. The manual self swap's SOFT fallback is not
		// safe when a delayed action may fire after the player has moved or the target has changed.
		BodyNode self = new BodyNode(player, TodoBoogieWoogieRuntime.Strictness.STRICT);
		Optional<SwapPlan> plan = SwapNodes.planExchange(self, target.get());
		if (plan.isEmpty() || !groundSafe(player.level(), plan.get())) {
			return;
		}
		SwapOutcome outcome = SwapCommit.commit(player, plan.get());
		if (!outcome.success()) {
			return;
		}
		TodoSwapHooks.fireAfterCommit(player, SwapKind.AIMED, outcome, true);
		TodoCooldownPolicy.arm(player, SwapKind.AIMED.slot(), SwapKind.AIMED.baseCooldownTicks());
		TodoSwapMomentumRuntime.grant(player);
	}

	private static boolean groundSafe(ServerLevel level, SwapPlan plan) {
		if (level == null || plan == null || plan.moves().size() < 2) {
			return false;
		}
		for (SwapMove move : plan.moves()) {
			if (move == null || move.destination() == null
					|| !groundAt(level, move.destination(), TodoProfile.REVISED_AUTO_SWAP_GROUND_SCAN)) {
				return false;
			}
		}
		return true;
	}

	private static boolean groundAt(ServerLevel level, Vec3 destination, int scan) {
		if (scan <= 0 || destination == null
				|| !Double.isFinite(destination.x) || !Double.isFinite(destination.y) || !Double.isFinite(destination.z)) {
			return false;
		}
		BlockPos feet = BlockPos.containing(destination);
		for (int offset = 1; offset <= scan; offset++) {
			BlockState state = level.getBlockState(feet.below(offset));
			if (state.isSolid()) {
				return true;
			}
		}
		return false;
	}

	private static void emitRhythmState(ServerPlayer player, TodoRhythmState state, long gameTime) {
		int beat = state == null ? 0 : state.beat();
		int revised = state == null || state.revisedUntilGameTime() <= gameTime
				? 0
				: (int) Math.min(Integer.MAX_VALUE, state.revisedUntilGameTime() - gameTime);
		JujutsuNetworking.sendVfxCue(player,
				VfxCues.anchoredWithOffset(TodoVfxIds.RHYTHM_STATE, player.position(), player.getId(),
						new Vec3(beat, revised, 0), 1, gameTime, player.getRandom().nextLong()));
	}
}
