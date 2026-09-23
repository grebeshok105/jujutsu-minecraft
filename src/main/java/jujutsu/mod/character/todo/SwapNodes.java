package jujutsu.mod.character.todo;

import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.TargetResolver;

/** Resolution, full revalidation, and plan construction for Todo swap nodes. */
public final class SwapNodes {
	private SwapNodes() {}

	/** Resolves the current crosshair target, then repeats every cheap target check on the fresh entity. */
	public static Optional<BodyNode> aimed(ServerPlayer todo, double range) {
		if (todo == null || todo.level() == null || !Double.isFinite(range) || range <= 0.0) {
			return Optional.empty();
		}
		ServerLevel level = todo.level();
		TargetResolver.Result result = TargetResolver.resolve(level, todo, range,
				candidate -> TodoBoogieWoogieRuntime.isEligibleTarget(todo, candidate));
		if (result.mode() != TargetResolver.Mode.ENTITY || result.entityId().isEmpty()) {
			return Optional.empty();
		}
		Entity entity = level.getEntity(result.entityId().get());
		if (!(entity instanceof LivingEntity target)
				|| !TodoBoogieWoogieRuntime.isEligibleTarget(todo, target)
				|| !todo.hasLineOfSight(target)
				|| todo.distanceToSqr(target) > range * range) {
			return Optional.empty();
		}
		return Optional.of(new BodyNode(target, TodoBoogieWoogieRuntime.Strictness.STRICT));
	}

	/** Resolves the marked participant by dimension, entity id, and UUID identity. */
	public static Optional<BodyNode> marked(ServerPlayer todo) {
		if (todo == null) {
			return Optional.empty();
		}
		TodoPendingSelection pending = TodoTransientState.pairSelection(todo.getUUID()).orElse(null);
		ServerLevel level = todo.level();
		if (pending == null || pending.isExpired(level.getGameTime()) || !pending.isIn(level.dimension())) {
			if (pending != null) {
				TodoTransientState.clearPairSelection(todo.getUUID());
			}
			return Optional.empty();
		}
		Entity entity = level.getEntity(pending.targetEntityId());
		if (!(entity instanceof LivingEntity target)
				|| !pending.identifies(target.getUUID())
				|| !TodoBoogieWoogieRuntime.isEligibleTarget(todo, target)
				|| !todo.hasLineOfSight(target)
				|| !within(todo, target, TodoProfile.BOOGIE_WOOGIE_RANGE)) {
			return Optional.empty();
		}
		return Optional.of(new BodyNode(target, TodoBoogieWoogieRuntime.Strictness.STRICT));
	}

	/** Resolves the owner's live stone from its UUID and recorded dimension. */
	public static Optional<StoneNode> stone(ServerPlayer todo) {
		if (todo == null) {
			return Optional.empty();
		}
		TodoStoneRef ref = TodoTransientState.stone(todo.getUUID()).orElse(null);
		if (ref == null || !ref.dimension().equals(todo.level().dimension())) {
			return Optional.empty();
		}
		Entity entity = todo.level().getEntity(ref.entityUuid());
		if (!(entity instanceof TodoStoneEntity stone)
				|| stone.isRemoved()
				|| !stoneEligible(todo, stone)) {
			return Optional.empty();
		}
		return Optional.of(new StoneNode(stone));
	}

	/** Resolves Todo himself; his own arrival is the only SOFT destination in the manual swap kit. */
	public static BodyNode self(ServerPlayer todo) {
		return new BodyNode(todo, TodoBoogieWoogieRuntime.Strictness.SOFT);
	}

	/** Rechecks the complete transport/eligibility/LOS/range policy immediately before commit. */
	public static boolean revalidate(SwapNode node, ServerPlayer caster) {
		if (node == null || caster == null || !node.stillValid(caster)) {
			return false;
		}
		if (node instanceof BodyNode body) {
			LivingEntity entity = body.body();
			if (entity == caster) {
				return !TodoSwapGates.casterStateBlocked(caster);
			}
			return TodoBoogieWoogieRuntime.isEligibleTarget(caster, entity)
					&& caster.hasLineOfSight(entity)
					&& within(caster, entity, TodoProfile.BOOGIE_WOOGIE_RANGE);
		}
		if (node instanceof StoneNode stone) {
			return stoneEligible(caster, stone.stone());
		}
		return false;
	}

	/** Builds a two-node exchange; body destinations use their node's policy, stones snap verbatim. */
	public static Optional<SwapPlan> planExchange(SwapNode a, SwapNode b) {
		if (a == null || b == null || a.level() != b.level()) {
			return Optional.empty();
		}
		Vec3 aPosition = a.position();
		Vec3 bPosition = b.position();
		Vec3 aDestination = destinationFor(a, bPosition);
		Vec3 bDestination = destinationFor(b, aPosition);
		return SwapPlan.preflight(new SwapMove(a, aDestination), new SwapMove(b, bDestination));
	}

	/** Builds the fixed Todo→A→T→Todo cycle, with STRICT placement for all three bodies. */
	public static Optional<SwapPlan> planCycle(BodyNode todo, BodyNode a, BodyNode t) {
		if (todo == null || a == null || t == null || todo.level() != a.level() || todo.level() != t.level()) {
			return Optional.empty();
		}
		Vec3 todoPosition = todo.position();
		Vec3 aPosition = a.position();
		Vec3 tPosition = t.position();
		Vec3 todoDestination = TodoBoogieWoogieRuntime.findSafeDestination(todo.level(), todo.body(), aPosition,
				TodoBoogieWoogieRuntime.Strictness.STRICT);
		Vec3 aDestination = TodoBoogieWoogieRuntime.findSafeDestination(a.level(), a.body(), tPosition,
				TodoBoogieWoogieRuntime.Strictness.STRICT);
		Vec3 tDestination = TodoBoogieWoogieRuntime.findSafeDestination(t.level(), t.body(), todoPosition,
				TodoBoogieWoogieRuntime.Strictness.STRICT);
		return SwapPlan.preflight(List.of(
				new SwapMove(todo, todoDestination),
				new SwapMove(a, aDestination),
				new SwapMove(t, tDestination)));
	}

	private static Vec3 destinationFor(SwapNode moving, Vec3 partnerPosition) {
		if (moving instanceof StoneNode) {
			return partnerPosition;
		}
		BodyNode body = (BodyNode) moving;
		return TodoBoogieWoogieRuntime.findSafeDestination(body.level(), body.body(), partnerPosition, body.strictness());
	}

	private static boolean stoneEligible(ServerPlayer todo, TodoStoneEntity stone) {
		if (stone == null || stone.isRemoved() || stone.level() != todo.level()
				|| !within(todo, stone, TodoProfile.STONE_SWAP_RANGE)) {
			return false;
		}
		return TodoTransientState.stone(todo.getUUID()).map(ref -> ref.entityUuid().equals(stone.getUUID())
				&& ref.dimension().equals(todo.level().dimension())).orElse(false);
	}

	private static boolean within(ServerPlayer todo, LivingEntity body, double range) {
		return todo.distanceToSqr(body) <= range * range;
	}

	private static boolean within(ServerPlayer todo, TodoStoneEntity stone, double range) {
		return todo.distanceToSqr(stone) <= range * range;
	}
}
