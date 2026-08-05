package jujutsu.mod.character.todo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * The triple cycle's contracts: fixed direction, atomic three-body preflight, reverse-order rollback,
 * a cooldown of its own, and no swap momentum. Direction and rollback are source pins — the suite has
 * no {@code ServerLevel} (see E1), so the commit order is read off the runtime rather than executed.
 */
class TodoTripleSwapTest {
	private static final Path TODO = Path.of("src/main/java/jujutsu/mod/character/todo");

	@Test
	void preflightRequiresAllThreeDestinations() {
		Vec3 d1 = new Vec3(1, 1, 1);
		Vec3 d2 = new Vec3(2, 2, 2);
		Vec3 d3 = new Vec3(3, 3, 3);
		assertTrue(TodoTripleSwapPlan.preflight(d1, d2, d3).isPresent(),
				"three safe destinations must produce a plan");
		assertTrue(TodoTripleSwapPlan.preflight(null, d2, d3).isEmpty(),
				"a null Todo destination must cancel the whole cast");
		assertTrue(TodoTripleSwapPlan.preflight(d1, null, d3).isEmpty(),
				"a null A destination must cancel the whole cast");
		assertTrue(TodoTripleSwapPlan.preflight(d1, d2, null).isEmpty(),
				"a null T destination must cancel the whole cast");
		assertTrue(TodoTripleSwapPlan.preflight(null, null, null).isEmpty(),
				"an all-null plan must cancel like any other");
		Optional<TodoTripleSwapPlan> plan = TodoTripleSwapPlan.preflight(d1, d2, d3);
		assertEquals(d1, plan.get().todoDestination());
		assertEquals(d2, plan.get().aDestination());
		assertEquals(d3, plan.get().tDestination());
	}

	/**
	 * The cycle direction is fixed: Todo → A's position, A → T's position, T → Todo's position. One
	 * destination per body, authored in that order and no other, so the mapping is visible in the commit
	 * path rather than hidden behind a helper that could be reordered.
	 */
	@Test
	void cycleDirectionIsFixedAndPinned() throws Exception {
		String triple = methodBody(Files.readString(TODO.resolve("TodoPairSwapRuntime.java")),
				"private static boolean tripleCast(");
		int todo = triple.indexOf("findSafeDestination(level, todo, firstSnapshot.position()");
		int a = triple.indexOf("findSafeDestination(level, first, thirdSnapshot.position()");
		int t = triple.indexOf("findSafeDestination(level, aimed, todoSnapshot.position()");
		assertTrue(todo >= 0 && a > todo && t > a,
				"the three destinations must be authored in the fixed cycle order Todo->A->T->Todo");
		assertEquals(3, triple.split("findSafeDestination", -1).length - 1,
				"the triple must preflight exactly three destinations, one per body");
	}

	/**
	 * The cycle reads in-world through three TRIPLE_SWAP edges — Todo→A, A→T, T→Todo — each world-fixed
	 * at its start, with the flow in the direction and the full displacement (whose length is the edge
	 * length) in {@code anchorOffset}.
	 */
	@Test
	void feedbackEmitsOneEdgePerCycleStep() throws Exception {
		String feedback = methodBody(Files.readString(TODO.resolve("TodoPairSwapRuntime.java")),
				"private static void emitTripleFeedback(");
		int edge1 = feedback.indexOf("emitCycleEdge(level, todo, todoSnapshot.position(), firstSnapshot.position()");
		int edge2 = feedback.indexOf("emitCycleEdge(level, todo, firstSnapshot.position(), thirdSnapshot.position()");
		int edge3 = feedback.indexOf("emitCycleEdge(level, todo, thirdSnapshot.position(), todoSnapshot.position()");
		assertTrue(edge1 >= 0 && edge2 > edge1 && edge3 > edge2,
				"one edge per cycle step, in the order Todo->A, A->T, T->Todo");
		String edge = methodBody(Files.readString(TODO.resolve("TodoPairSwapRuntime.java")),
				"private static void emitCycleEdge(");
		assertTrue(edge.contains("to.subtract(from)"),
				"the displacement must own the full edge vector (its length is the edge length)");
		assertTrue(edge.contains("VfxCues.worldFixedDisplacement(TodoVfxIds.TRIPLE_SWAP")
				&& edge.contains("broadcastVfxCue"),
				"the edge must ride the world-fixed displacement factory and be broadcast");
	}

	/**
	 * A mid-commit failure must not strand anyone on a destination the cycle never completed: every
	 * already-moved body is restored to its snapshot, last placed first, and the failure is reported.
	 */
	@Test
	void rollbackRestoresInReversePlacementOrderAndAlwaysLogs() throws Exception {
		String rollback = methodBody(Files.readString(TODO.resolve("TodoPairSwapRuntime.java")),
				"private static void rollbackTriple(");
		assertTrue(rollback.contains("for (int i = placed.size() - 1; i >= 0; i--)"),
				"the restore loop must walk the placed bodies in reverse placement order");
		assertTrue(rollback.contains("TodoBoogieWoogieRuntime.restore(body.entity(), body.snapshot())"),
				"each placed body must be restored to its snapshot");
		assertTrue(rollback.contains("LOGGER.error"), "a failed commit must always log; no silent partial cycle");
		assertTrue(rollback.contains("caster.getGameProfile().getName()") && rollback.contains("position()"),
				"the error must name the caster and the positions involved");
	}

	@Test
	void tripleHasItsOwnCooldownDistinctFromPair() throws Exception {
		assertNotEquals(TodoProfile.TRIPLE_SWAP_COOLDOWN_TICKS, TodoProfile.PAIR_SWAP_COOLDOWN_TICKS,
				"the triple and the pair must never share a cooldown duration");
		assertTrue(TodoProfile.TRIPLE_SWAP_COOLDOWN_TICKS > TodoProfile.PAIR_SWAP_COOLDOWN_TICKS,
				"a cycle that moves three bodies, Todo included, must cost more than the bystander pair");
		String triple = methodBody(Files.readString(TODO.resolve("TodoPairSwapRuntime.java")),
				"private static boolean tripleCast(");
		assertTrue(triple.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.SECONDARY_SNEAK, "
						+ "TodoProfile.TRIPLE_SWAP_COOLDOWN_TICKS)"),
				"the cycle must start the sneak slot's own cooldown");
		assertTrue(triple.contains("sendAbilityCooldown(todo, CharacterAbility.SECONDARY_SNEAK"),
				"the cooldown must be mirrored to the caster on the same slot");
	}

	@Test
	void tripleGrantsNoSwapMomentum() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		assertFalse(pair.contains("SwapMomentum"),
				"the triple cycle must never grant the momentum window; that reward belongs to swaps Todo makes with his own body (R, V)");
	}

	@Test
	void selectionPulseIsCasterOnlySilentAndPeriodic() throws Exception {
		String tick = methodBody(Files.readString(TODO.resolve("TodoPairSwapRuntime.java")),
				"public static void serverTick(");
		assertTrue(tick.contains("PAIR_MARK_PULSE_TICKS"), "the pulse must ride the profile period");
		assertTrue(tick.contains("sendVfxCue"), "the pulse must be caster-only like the mark, never broadcast");
		assertTrue(tick.contains("VfxCues.anchoredSilentRepeat(TodoVfxIds.PAIR_MARK"),
				"the pulse must ride the silent-repeat factory — the one intensity the clamp allows to be zero");
		assertTrue(tick.contains("pending.isExpired(now)"), "the sweep must still drop expired selections");
	}

	/** The source text of one method, brace-counted from its signature. */
	private static String methodBody(String source, String signature) {
		int at = source.indexOf(signature);
		assertTrue(at >= 0, "method not found, so the assertions over it would pass over nothing: " + signature);
		int open = source.indexOf('{', at);
		assertTrue(open >= 0, "no method body found for " + signature);
		int depth = 0;
		for (int i = open; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}' && --depth == 0) {
				return source.substring(open, i + 1);
			}
		}
		throw new AssertionError("unbalanced braces while reading " + signature);
	}
}
