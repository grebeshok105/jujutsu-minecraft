package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import jujutsu.mod.character.CharacterAbility;

/** Pair-swap selection lifecycle, the Shift+B boundary, and the safety rules that apply when bystanders are moved. */
public final class TodoPairSwapTest {
	private static final Path TODO = Path.of("src/main/java/jujutsu/mod/character/todo");
	private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
	private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:the_nether"));

	private TodoPairSwapTest() {}

	public static void main(String[] args) throws Exception {
		assertSelectionExpiresOnItsOwnClock();
		assertSelectionIsBoundToDimensionAndIdentity();
		assertBystandersGetStrictPlacement();
		assertOnlyTodoHimselfMayReachTheFallback();
		assertDistanceIsMeasuredFromTodoOnly();
		assertCanonicalSlotIsGoneFromTheDefinitionSeam();
		assertShiftBCastsTheTripleAndNeverDegradesIntoB();
		assertTheSelectionSurvivesTheTransitionFromBToShiftB();
		assertMarkingIsFreeAndOnlyTheSwapCosts();
		System.out.println("TodoPairSwapTest passed");
	}

	private static void assertSelectionExpiresOnItsOwnClock() {
		UUID target = UUID.nameUUIDFromBytes(new byte[] {1});
		long expiry = 500L + TodoProfile.PAIR_SELECTION_TTL_TICKS;
		TodoPendingSelection selection = new TodoPendingSelection(OVERWORLD, target, 42, expiry);
		assert !selection.isExpired(expiry - 1) : "A selection must survive until its expiry tick";
		assert selection.isExpired(expiry) : "A selection must expire on its expiry tick, not after it";
		assert selection.isExpired(expiry + 1000) : "An expired selection must stay expired";
	}

	private static void assertSelectionIsBoundToDimensionAndIdentity() {
		UUID target = UUID.nameUUIDFromBytes(new byte[] {2});
		UUID other = UUID.nameUUIDFromBytes(new byte[] {3});
		TodoPendingSelection selection = new TodoPendingSelection(OVERWORLD, target, 42, 100L);
		assert selection.isIn(OVERWORLD) : "A selection must recognize its own dimension";
		assert !selection.isIn(NETHER) : "A selection must not survive into another dimension";
		assert selection.identifies(target) : "A selection must recognize its marked entity";
		// The network id can be recycled onto a different entity; the UUID is what makes that detectable.
		assert !selection.identifies(other) : "A recycled entity id must not pass as the marked entity";
	}

	private static void assertBystandersGetStrictPlacement() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		assert pair.contains("Strictness.STRICT")
				: "Moving bystanders must not use the last-resort fallback that exists for Todo's own feel";
		assert !pair.contains("Strictness.SOFT")
				: "The pair swap must never fall back to the exact requested point";
		// The scan lives in shared SafeBodyPlacement since the Megumi shadow kit; the property is the
		// same, split across the seam: only the SOFT policy carries the exact-point fallback, and the
		// shared gate hands the requested point back only when the policy opted in.
		String swap = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		assert swap.contains("strictness == Strictness.SOFT ? SOFT_PLACEMENT : STRICT_PLACEMENT")
				: "Only SOFT may select the policy that keeps the exact-point fallback";
		String placement = Files.readString(
				Path.of("src/main/java/jujutsu/mod/combat/SafeBodyPlacement.java"));
		assert placement.contains("policy.exactRequestedFallback() && isInWorld")
				: "The fallback must stay gated on the policy flag so a strict scan genuinely cancels";
		assert pair.contains("TodoSwapPlan.preflight")
				: "The pair swap must use the same atomic two-destination rule as the self swap";
		assert pair.contains("TodoBoogieWoogieRuntime.rollback(\"pair swap\"")
				: "A failed pair placement must report the incomplete restore through the shared helper";
	}

	/**
	 * No body but Todo's own may reach the unchecked fallback.
	 *
	 * <p>The pair swap and the triple cycle always pass {@code STRICT} explicitly — the triple even for
	 * Todo himself, because a three-body commitment is not the place for his private mid-air luxury. The
	 * aimed swap did not pass anything: a defaulting overload supplied {@code SOFT} for <em>both</em>
	 * destinations, so the target — the one participant who did not ask to be moved — could be placed at
	 * the exact requested point with {@code noBlockCollision} skipped. The overload is deleted rather
	 * than merely bypassed, so the unsafe choice cannot be made by omission again.
	 *
	 * <p>The properties this protects — a wall refuses the placement, a large body is judged by its own
	 * bounding box, the world border is enforced, and open air is still a legal destination — live inside
	 * {@code isPlaceableDestination} and need a real {@code ServerLevel} to exercise. Nothing here can do
	 * that; they are in-game checks. What is checkable is that a third party reaches that predicate at all,
	 * which is exactly what the defect removed.
	 */
	private static void assertOnlyTodoHimselfMayReachTheFallback() throws Exception {
		String swap = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		assert !swap.contains("findSafeDestination(ServerLevel level, LivingEntity entity, Vec3 requested) {")
				: "the defaulting overload must stay deleted; it is how SOFT was applied without anyone choosing it";
		assert swap.contains("findSafeDestination(level, todo, targetSnapshot.position(), Strictness.SOFT)")
				: "Todo's own arrival keeps the fallback: the risk is his and it is what makes a mid-air swap feel right";
		assert swap.contains("findSafeDestination(level, target, todoSnapshot.position(), Strictness.STRICT)")
				: "the aimed target must be placed only where noBlockCollision passed, or the cast must cancel";
		// One SOFT site in the whole package, and it is the line above.
		int soft = swap.split(java.util.regex.Pattern.quote("Strictness.SOFT"), -1).length - 1;
		assert soft == 2
				: "SOFT must appear exactly twice in this file: the one call site, and the gate inside the search";
		for (String path : new String[] {"TodoPairSwapRuntime.java"}) {
			assert !Files.readString(TODO.resolve(path)).contains("Strictness.SOFT")
					: path + " moves bodies that are not Todo and must never reach the fallback";
		}
	}

	private static void assertDistanceIsMeasuredFromTodoOnly() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		assert pair.contains("todo.distanceToSqr(participant)")
				: "Reach must be measured from Todo to each participant";
		// The pair may legitimately be 40 blocks apart; that spread is the whole value of the technique.
		assert !pair.contains("first.distanceToSqr(aimed)") && !pair.contains("aimed.distanceToSqr(first)")
				: "The distance between the two participants must never be limited";
		assert pair.contains("TodoProfile.BOOGIE_WOOGIE_RANGE")
				: "Reach must reuse the swap range rather than introduce a second number";
	}

	/**
	 * The canonicalSlot seam is gone entirely: Shift+B is no longer folded onto B anywhere, not in the
	 * interface, not in the executor, not in Todo's definition. The executor hands the raw slot to the
	 * vessel, so SECONDARY_SNEAK reaches Todo's router as itself and answers with the triple cycle.
	 */
	private static void assertCanonicalSlotIsGoneFromTheDefinitionSeam() throws Exception {
		String definition = Files.readString(TODO.resolve("TodoDefinition.java"));
		String interfaceFile = Files.readString(Path.of("src/main/java/jujutsu/mod/character/CharacterDefinition.java"));
		String executor = Files.readString(Path.of("src/main/java/jujutsu/mod/character/CharacterAbilityExecutor.java"));
		assert !definition.contains("canonicalSlot")
				: "Todo must not fold Shift+B onto B anymore; the triple cycle owns that slot";
		assert !interfaceFile.contains("canonicalSlot")
				: "The definition seam must not keep a dead fold hook now that no vessel uses it";
		assert !executor.contains("canonicalSlot")
				: "The executor must hand the raw input slot to the vessel and never fold it";
		assert executor.contains("CharacterAbilityCooldowns.isReady(player, ability)")
				&& executor.contains("return definition.tryCast(player, ability, notify)")
				&& executor.contains("return AbilityResult.UNHANDLED_FAILURE;")
				: "Both the cooldown check and the cast must use the raw input slot, each slot cooling itself; the gate's own refusals must come back UNHANDLED_FAILURE so no fallback double-announces them";
	}

	/**
	 * Shift+B is the triple cycle and never a second pair press: with no live selection it refuses with
	 * {@code triple.no_first} instead of falling back to B's mark. The selection is what tells the two
	 * casts apart, so the boundary between them has to be the selection itself.
	 */
	private static void assertShiftBCastsTheTripleAndNeverDegradesIntoB() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		String router = Files.readString(TODO.resolve("TodoAbilityRouter.java"));
		assert router.contains("case SECONDARY, SECONDARY_SNEAK -> TodoPairSwapRuntime.tryCast(todo, ability, notify)")
				: "Shift+B must reach the same runtime as B, not a false arm";
		String triple = methodBody(pair, "private static boolean tripleCast(");
		assert triple.contains("message.jujutsumod.todo.triple.no_first")
				: "Shift+B without a live selection must refuse with the triple-specific message";
		assert !triple.contains("mark(todo, level,")
				: "Shift+B must never degrade into a pair mark when the selection is missing";
		// Marking stays the pair path's private gesture: the triple cannot create its own first participant.
		String mark = methodBody(pair, "private static boolean mark(");
		assert !mark.contains("SECONDARY_SNEAK")
				: "Marking must remain the B press's gesture and never be reachable from Shift+B";
		// The pair path keeps its own refusal: a second B press with no selection still marks, unchanged.
		assert pair.contains("if (pending == null) {\n\t\t\treturn mark(todo, level, aimed, notify);")
				: "B must keep marking when there is no selection; only Shift+B is strict about it";
	}

	/**
	 * The first press's selection is shared state, not per-cast: B writes it and Shift+B reads it from
	 * the same {@link TodoTransientState} slot, and the triple consumes it only on success — every
	 * refusal leaves it for the next cast or the TTL sweep.
	 */
	private static void assertTheSelectionSurvivesTheTransitionFromBToShiftB() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		assert pair.contains("TodoTransientState.setPairSelection(todo.getUUID(), new TodoPendingSelection(")
				: "The B press must store the selection in the shared transient state";
		String triple = methodBody(pair, "private static boolean tripleCast(");
		// Exactly three clears, each the end of the selection's usefulness: the marked body is gone, the
		// bodies are not in one level, or the cycle committed. Every refusal keeps the selection alive.
		int clears = triple.split("TodoTransientState.clearPairSelection", -1).length - 1;
		assert clears == 3
				: "The triple must clear the selection only on lost-marked, cross-level and success, found " + clears;
		assert triple.contains("if (aimed == null)") && triple.contains("no aimed third participant")
				: "A triple miss must refuse and keep the selection";
		assert triple.contains("message.jujutsumod.todo.triple.unsafe")
				: "A failed triple preflight must refuse and keep the selection";
	}

	private static void assertMarkingIsFreeAndOnlyTheSwapCosts() throws Exception {
		String pair = Files.readString(TODO.resolve("TodoPairSwapRuntime.java"));
		assert pair.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.SECONDARY, TodoProfile.PAIR_SWAP_COOLDOWN_TICKS)")
				: "A committed pair swap must take its own cooldown slot";
		assert pair.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.SECONDARY_SNEAK, TodoProfile.TRIPLE_SWAP_COOLDOWN_TICKS)")
				: "A committed triple cycle must take the sneak slot's own cooldown";
		// Marking, cancelling and every rejection must be free: no cooldown call inside the pair path
		// except the committed swap's, and none inside the triple path except the committed cycle's.
		String pairBody = methodBody(pair, "private static boolean pairCast(");
		assert !pairBody.contains("CharacterAbilityCooldowns.start")
				: "The pair path must not start a cooldown before the commit";
		String mark = methodBody(pair, "private static boolean mark(");
		assert !mark.contains("CharacterAbilityCooldowns.start")
				: "Marking must stay free";
		assert CharacterAbility.SECONDARY.networkId() == 2 : "The pair swap must sit on the second technique key";
		assert CharacterAbility.byNetworkId(2) == CharacterAbility.SECONDARY : "Network id 2 must resolve to SECONDARY";
		assert TodoProfile.PAIR_SWAP_COOLDOWN_TICKS > TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS
				: "Swapping bystanders carries no personal risk, so it must cost more than Todo's own swap";
		String router = Files.readString(TODO.resolve("TodoAbilityRouter.java"));
		assert router.contains("case SECONDARY, SECONDARY_SNEAK ->") : "The pair swap must be routed from Todo's slot map";
	}

	/**
	 * The source text of one method, brace-counted from its signature.
	 *
	 * <p>This is a grep and says so: it cannot see behaviour reached through a helper the method calls,
	 * only one written into the method itself.
	 */
	private static String methodBody(String source, String signature) {
		int at = source.indexOf(signature);
		assert at >= 0 : "method not found, so the assertions over it would pass over nothing: " + signature;
		int open = source.indexOf('{', at);
		assert open >= 0 : "no method body found for " + signature;
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
