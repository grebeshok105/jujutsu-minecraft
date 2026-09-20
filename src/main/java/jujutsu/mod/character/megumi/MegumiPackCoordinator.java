package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import jujutsu.mod.combat.HoldSupport;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * The autonomous half of Ten Shadows (issue #107): every {@code COORDINATION_SCAN_TICKS} ticks it
 * rebuilds one {@link MegumiCombatContext} per owner — a single entity scan shared by the whole
 * pack — then lets {@link MegumiCoordinationPolicy} pick each mark-holding body's target. Bodies
 * keep acting on their {@code sicTargetUuid} exactly as before; this class only writes the mark.
 *
 * <p>Registered AFTER the two pack runtimes so its END_SERVER_TICK runs once their reconcile and
 * retaliation passes have settled — the coordinator reads the marks those passes left, never the
 * other way around. A manual sic ({@link MegumiMarkKind#MANUAL}) is the owner's order and is never
 * reassigned here; a retaliation mark is the pack's own answer and is left to the retaliation pass.
 */
public final class MegumiPackCoordinator {
	private static final Map<UUID, MegumiCombatContext> CONTEXTS = new ConcurrentHashMap<>();

	private MegumiPackCoordinator() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MegumiPackCoordinator::tick);
		// Same stop-hook every sibling owner-keyed runtime carries: a context holds live entity
		// references, so it must not outlive the server in a shared JVM (issue #22 debt class).
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CONTEXTS.clear();
			MegumiFailureMemory.clearAll();
		});
	}

	/**
	 * The owner's combat context, at most {@code COORDINATION_SCAN_TICKS} ticks stale — brains read
	 * it for soft coordination (an elephant holds its jet off a target an ally is already working).
	 */
	public static MegumiCombatContext contextFor(ServerPlayer owner, ServerLevel level) {
		MegumiCombatContext cached = CONTEXTS.get(owner.getUUID());
		long gameTime = level.getGameTime();
		if (cached != null
				&& gameTime - cached.gameTime() < MegumiShikigamiProfile.COORDINATION_SCAN_TICKS) {
			return cached;
		}
		return rebuild(owner, level, gameTime);
	}

	private static void tick(MinecraftServer server) {
		Set<UUID> liveBodies = new HashSet<>();
		for (ServerPlayer owner : server.getPlayerList().getPlayers()) {
			List<LivingEntity> bodies = markHolders(server, owner.getUUID());
			if (bodies.isEmpty()) {
				CONTEXTS.remove(owner.getUUID());
				continue;
			}
			for (LivingEntity body : bodies) {
				liveBodies.add(body.getUUID());
			}
			ServerLevel level = owner.level();
			MegumiCombatContext context = contextFor(owner, level);
			assign(owner, level, context, bodies);
		}
		CONTEXTS.keySet().removeIf(ownerId -> server.getPlayerList().getPlayer(ownerId) == null);
		MegumiFailureMemory.retainOnly(liveBodies);
	}

	/** Every body that can carry a mark: the non-rabbit shikigami plus the Divine Dogs. */
	private static List<LivingEntity> markHolders(MinecraftServer server, UUID ownerId) {
		List<LivingEntity> bodies = new ArrayList<>();
		for (MegumiShikigamiEntity body : MegumiShikigamiRuntime.livingBodiesAll(server, ownerId)) {
			// Rabbit Escape carries no marks (D13): the swarm's chaos is its contribution.
			if (body.shikigamiType() != MegumiShikigami.RABBITS) {
				bodies.add(body);
			}
		}
		bodies.addAll(MegumiSummonRuntime.livingDogs(server, ownerId));
		return bodies;
	}

	private static MegumiCombatContext rebuild(ServerPlayer owner, ServerLevel level, long gameTime) {
		UUID ownerId = owner.getUUID();
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
				owner.getBoundingBox().inflate(MegumiShikigamiProfile.AUTONOMY_RADIUS),
				candidate -> candidate.isAlive()
						&& !candidate.isRemoved()
						&& MegumiSummonRuntime.isEligibleTarget(owner, candidate));
		List<LivingEntity> bodies = markHolders(level.getServer(), ownerId);
		Map<UUID, UUID> marks = new HashMap<>();
		Set<UUID> intents = new HashSet<>();
		Map<UUID, UUID> allyThreats = new HashMap<>();
		for (LivingEntity body : bodies) {
			UUID mark = markOf(body);
			if (mark != null) {
				marks.put(body.getUUID(), mark);
			}
			if (body instanceof MegumiToadEntity toad) {
				if (toad.grabIntentUuid() != null) {
					intents.add(toad.grabIntentUuid());
				}
				if (toad.grabbedUuid() != null) {
					intents.add(toad.grabbedUuid());
				}
			} else if (body instanceof MegumiNueEntity nue && nue.diving()) {
				intents.add(nue.diveTargetUuid());
			} else if (body instanceof MegumiElephantEntity elephant && elephant.jetActive()
					&& elephant.sicTargetUuid() != null) {
				intents.add(elephant.sicTargetUuid());
			}
			LivingEntity aggressor = body.getLastHurtByMob();
			if (MegumiRetaliationPolicy.isUsable(aggressor)
					&& MegumiRetaliationPolicy.attackerFresh(body.tickCount,
							body.getLastHurtByMobTimestamp(), MegumiProfile.RETALIATION_WINDOW_TICKS)
					&& MegumiSummonRuntime.isEligibleTarget(owner, aggressor)) {
				allyThreats.put(body.getUUID(), aggressor.getUUID());
			}
		}
		MegumiCombatContext context = new MegumiCombatContext(gameTime, ownerId, owner.position(),
				candidates, marks, intents, allyThreats);
		CONTEXTS.put(ownerId, context);
		return context;
	}

	private static void assign(ServerPlayer owner, ServerLevel level,
			MegumiCombatContext context, List<LivingEntity> bodies) {
		Set<UUID> claimed = context.occupiedOrClaimed();
		for (LivingEntity body : bodies) {
			MegumiMarkKind kind = markKindOf(body);
			if (kind == MegumiMarkKind.MANUAL || kind == MegumiMarkKind.RETALIATION) {
				continue;
			}
			double distToOwner = body.distanceTo(owner);
			MegumiCoordinationPolicy.BandAction band = MegumiCoordinationPolicy.bandAction(
					distToOwner, kind != null);
			if (band == MegumiCoordinationPolicy.BandAction.DROP) {
				clearMarkOf(body);
				continue;
			}
			if (band != MegumiCoordinationPolicy.BandAction.ASSIGNABLE) {
				continue;
			}
			if (!acceptsMark(body)) {
				// Still materializing: a mark written now would sit on a body whose setTarget is
				// gated off, and tickPounce would clear it as stale before it ever fired.
				continue;
			}
			UUID currentMark = markOf(body);
			LivingEntity current = currentMark == null ? null : resolve(level, currentMark);
			double currentScore = current == null ? 0.0
					: scoreFor(body, owner, context, current, claimed, currentMark);
			List<LivingEntity> candidates = context.candidates();
			double[] scores = new double[candidates.size()];
			for (int i = 0; i < candidates.size(); i++) {
				scores[i] = scoreFor(body, owner, context, candidates.get(i), claimed,
						currentMark);
			}
			int pick = MegumiCoordinationPolicy.pick(candidates.size(), i -> scores[i],
					i -> claimed.contains(candidates.get(i).getUUID())
							&& !candidates.get(i).getUUID().equals(currentMark),
					i -> context.intentTargets().contains(candidates.get(i).getUUID())
							|| context.allyThreats().containsValue(candidates.get(i).getUUID()));
			if (pick < 0) {
				continue;
			}
			LivingEntity chosen = candidates.get(pick);
			if (current != null && current == chosen) {
				continue;
			}
			// A threat mark overrides hysteresis the same way it overrides the spread rule in pick:
			boolean chosenIsThreat = context.intentTargets().contains(chosen.getUUID())
					|| context.allyThreats().containsValue(chosen.getUUID());
			if (current != null && !chosenIsThreat
					&& !MegumiCoordinationPolicy.beatsWithHysteresis(scores[pick], currentScore)) {
				continue;
			}
			assignAutonomous(body, chosen);
			claimed.add(chosen.getUUID());
		}
	}

	/** Whether the body can take a mark this tick — the same gate the sic command uses. */
	private static boolean acceptsMark(LivingEntity body) {
		if (body instanceof MegumiShikigamiEntity shikigami) {
			return shikigami.acceptsSicCommand();
		}
		if (body instanceof MegumiDivineDogEntity dog) {
			return dog.acceptsSicCommand();
		}
		return false;
	}

	private static double scoreFor(LivingEntity body, ServerPlayer owner, MegumiCombatContext context,
			LivingEntity candidate, Set<UUID> claimed, UUID currentMark) {
		UUID candidateId = candidate.getUUID();
		boolean occupied = claimed.contains(candidateId) && !candidateId.equals(currentMark);
		return MegumiCoordinationPolicy.score(new MegumiCoordinationPolicy.CandidateFacts(
				candidate.isAlive() && !candidate.isRemoved(),
				MegumiSummonRuntime.isEligibleTarget(owner, candidate),
				body.hasLineOfSight(candidate),
				candidate.distanceTo(owner),
				candidate.distanceTo(body),
				candidate.getMaxHealth(),
				candidate.hasEffect(JujutsuEffects.MEGUMI_SOAKED),
				HoldSupport.isHeld(candidate),
				context.intentTargets().contains(candidateId),
				context.allyThreats().containsValue(candidateId),
				occupied), body.getRandom());
	}

	private static UUID markOf(LivingEntity body) {
		if (body instanceof MegumiShikigamiEntity shikigami) {
			return shikigami.sicTargetUuid();
		}
		if (body instanceof MegumiDivineDogEntity dog) {
			return dog.sicTargetUuid();
		}
		return null;
	}

	private static MegumiMarkKind markKindOf(LivingEntity body) {
		if (body instanceof MegumiShikigamiEntity shikigami) {
			return shikigami.markKind();
		}
		if (body instanceof MegumiDivineDogEntity dog) {
			return dog.markKind();
		}
		return null;
	}

	private static void assignAutonomous(LivingEntity body, LivingEntity target) {
		if (body instanceof MegumiShikigamiEntity shikigami) {
			shikigami.assignAutonomousTarget(target);
		} else if (body instanceof MegumiDivineDogEntity dog) {
			dog.assignAutonomousTarget(target);
		}
	}

	private static void clearMarkOf(LivingEntity body) {
		if (body instanceof MegumiShikigamiEntity shikigami) {
			shikigami.clearSicCommand();
		} else if (body instanceof MegumiDivineDogEntity dog) {
			dog.clearSicCommand();
		}
	}

	private static LivingEntity resolve(ServerLevel level, UUID id) {
		return level.getEntity(id) instanceof LivingEntity living
				&& living.isAlive() && !living.isRemoved() ? living : null;
	}
}
