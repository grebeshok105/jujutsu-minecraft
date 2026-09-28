package jujutsu.mod.character.todo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;

/** Atomic forward commit for every Todo node plan. */
public final class SwapCommit {
	public static final SwapCommitTeleport PRODUCTION_COMMIT_TELEPORT =
			(body, level, destination, yaw, pitch) -> body.teleportTo(level, destination.x(), destination.y(), destination.z(),
					java.util.Set.of(), yaw, pitch, false);

	private static volatile SwapCommitTeleport commitTeleport = PRODUCTION_COMMIT_TELEPORT;

	private SwapCommit() {}

	public static SwapCommitTeleport commitTeleport() {
		return commitTeleport;
	}

	/** Test seam for manufacturing a deterministic mid-commit failure. */
	public static void overrideCommitTeleport(SwapCommitTeleport replacement) {
		commitTeleport = Objects.requireNonNull(replacement, "replacement");
		JujutsuMod.LOGGER.warn("Todo swap commit teleport OVERRIDDEN (test seam) — restore after the test");
	}

	public static void restoreProductionCommitTeleport() {
		commitTeleport = PRODUCTION_COMMIT_TELEPORT;
	}

	/**
	 * Revalidates every node, places bodies in plan order, snaps stones only after all bodies succeed, and
	 * restores already-placed bodies in reverse order on any failure.
	 */
	public static SwapOutcome commit(ServerPlayer caster, SwapPlan plan) {
		if (caster == null || plan == null || plan.moves().isEmpty()) {
			return SwapOutcome.refused();
		}
		for (SwapMove move : plan.moves()) {
			if (!SwapNodes.revalidate(move.node(), caster)) {
				return SwapOutcome.refused();
			}
		}

		List<PlacedBody> placed = new ArrayList<>();
		try {
			for (SwapMove move : plan.moves()) {
				if (!(move.node() instanceof BodyNode bodyNode)) {
					continue;
				}
				LivingEntity body = bodyNode.body();
				TodoBoogieWoogieRuntime.Snapshot snapshot = bodyNode.snapshotOrNull();
				if (snapshot == null || !commitTeleport.teleport(body, bodyNode.level(), move.destination(),
						snapshot.yaw(), snapshot.pitch())) {
					rollback(caster, placed, "forward teleport returned false");
					return SwapOutcome.refused();
				}
				placed.add(new PlacedBody(body, snapshot, move.destination()));
			}

			for (SwapMove move : plan.moves()) {
				if (move.node() instanceof StoneNode stoneNode) {
					stoneNode.stone().snapTo(stoneNode.level(), move.destination());
				}
			}
		} catch (RuntimeException exception) {
			rollback(caster, placed, "commit threw " + exception.getClass().getSimpleName());
			return SwapOutcome.refused();
		}

		List<TodoBoogieWoogieRuntime.MovedBody> moved = new ArrayList<>(placed.size());
		for (PlacedBody body : placed) {
			TodoBoogieWoogieRuntime.restoreMotionAndRotation(body.entity(), body.snapshot());
			moved.add(new TodoBoogieWoogieRuntime.MovedBody(body.snapshot(), body.destination()));
		}
		return new SwapOutcome(true, moved);
	}

	private static void rollback(ServerPlayer caster, List<PlacedBody> placed, String reason) {
		boolean complete = true;
		for (int index = placed.size() - 1; index >= 0; index--) {
			PlacedBody body = placed.get(index);
			boolean restored = restore(body.entity(), body.snapshot());
			complete &= restored;
			if (!restored) {
				JujutsuMod.LOGGER.error("Todo swap rollback incomplete caster={} body={} snapshot={}",
						caster.getGameProfile().getName(), body.entity().getName().getString(), body.snapshot().position());
			}
		}
		JujutsuMod.LOGGER.error("Todo swap commit failed caster={} moved={} reason={} rollbackComplete={}",
				caster.getGameProfile().getName(), placed.size(), reason, complete);
	}

	private static boolean restore(LivingEntity body, TodoBoogieWoogieRuntime.Snapshot snapshot) {
		if (body.isRemoved() || body.level() != snapshot.level()) {
			return false;
		}
		boolean restored = PRODUCTION_COMMIT_TELEPORT.teleport(body, snapshot.level(), snapshot.position(),
				snapshot.yaw(), snapshot.pitch());
		if (restored) {
			TodoBoogieWoogieRuntime.restoreMotionAndRotation(body, snapshot);
		}
		return restored;
	}

	private record PlacedBody(LivingEntity entity, TodoBoogieWoogieRuntime.Snapshot snapshot, Vec3 destination) {}
}
