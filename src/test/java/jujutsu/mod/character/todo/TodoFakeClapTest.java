package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.todo.TodoSwapGates.ClapGate;

/** The feint's product contract: same gates, same performance, no swap, its own cooldown. */
public final class TodoFakeClapTest {
	private static final Path TODO = Path.of("src/main/java/jujutsu/mod/character/todo");

	private TodoFakeClapTest() {}

	public static void main(String[] args) throws Exception {
		assertClapGateTruthTable();
		assertFeintCarriesNoSwapMachinery();
		assertFeintReusesTheRealClapPerformance();
		assertTheSharedPerformanceIsClapOnly();
		assertCooldownsAreIndependent();
		assertSlotRoutingIsExhaustive();
		System.out.println("TodoFakeClapTest passed");
	}

	private static void assertClapGateTruthTable() {
		assert TodoSwapGates.evaluate(false, true, false, false, true) == ClapGate.ALLOWED
				: "A healthy standing Todo with empty hands may clap";
		assert TodoSwapGates.evaluate(false, true, false, false, false) == ClapGate.HANDS_FULL
				: "A held item must refuse the clap with a message";
		assert TodoSwapGates.evaluate(true, true, false, false, true) == ClapGate.UNAVAILABLE
				: "A spectator must be refused silently";
		assert TodoSwapGates.evaluate(false, false, false, false, true) == ClapGate.UNAVAILABLE
				: "A dead Todo must be refused silently";
		assert TodoSwapGates.evaluate(false, true, true, false, true) == ClapGate.UNAVAILABLE
				: "Riding or being ridden must be refused silently";
		assert TodoSwapGates.evaluate(false, true, false, true, true) == ClapGate.UNAVAILABLE
				: "A staggered Todo must be refused silently";
		// State beats hands: an observer must not learn the hands were full from a cast that could not
		// have happened anyway.
		assert TodoSwapGates.evaluate(true, true, false, false, false) == ClapGate.UNAVAILABLE
				: "Unavailable state must outrank the hands-full message";
	}

	private static void assertFeintCarriesNoSwapMachinery() throws Exception {
		String feint = Files.readString(TODO.resolve("TodoFakeClapRuntime.java"));
		// The server must know the cast is hollow from the start; there is no swap to cancel.
		// SwapMomentum belongs here for two reasons: a mechanical payload is a tell, and the feint's own
		// cooldown is a third of the swap's, so a feint that granted it would be the cheap way to buy the
		// window and would make the real swap pointless as an opener.
		for (String forbidden : new String[] {"teleportTo", "TodoSwapPlan", "findSafeDestination", "TargetResolver",
				"SWAP_ENDPOINT", "SWAP_ARRIVAL", "SWAP_AFTERIMAGE", "MOMENTUM_STRIKE", "SwapMomentum", "emitSwapImpact"}) {
			assert !feint.contains(forbidden)
					: "The feint must not reach for " + forbidden + "; it never starts a swap";
		}
	}

	/**
	 * The other half of the deception, and the half the list above cannot reach.
	 *
	 * <p>Scanning the feint's own file proves it adds nothing. It does not prove the feint stays hollow,
	 * because the feint calls {@code emitClapPerformance} and inherits whatever that method does. An arrival
	 * cue added <em>there</em> would hand the feint a camera kick and six ticks of world silence — the two
	 * loudest things a completed swap owns — while every assertion above stayed green. So the shared method
	 * is scanned too, and by body rather than by file: those cues legitimately appear elsewhere in the class.
	 */
	private static void assertTheSharedPerformanceIsClapOnly() throws Exception {
		String swap = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		String body = methodBody(swap, "static void emitClapPerformance(");
		for (String forbidden : new String[] {"SWAP_ARRIVAL", "SWAP_AFTERIMAGE", "SWAP_ENDPOINT", "MOMENTUM_STRIKE",
				"emitSwapImpact", "scheduleDisplacementWhoosh", "scheduleLandingReport"}) {
			assert !body.contains(forbidden)
					: "emitClapPerformance is shared with the feint, so " + forbidden
							+ " in it would be given to a cast that moved nobody";
		}
		// The converse, so the method cannot be hollowed out instead of overfilled: it must still be the clap.
		assert body.contains("TodoVfxIds.BOOGIE_WOOGIE") && body.contains("JujutsuSounds.PROJECTJJK_CLAP")
				: "the shared performance must still carry the cue and the clap sound";
	}

	/**
	 * The source text of one method, brace-counted from its signature.
	 *
	 * <p>This is a grep and says so: it cannot see a swap cue reached through a helper the method calls,
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

	private static void assertFeintReusesTheRealClapPerformance() throws Exception {
		String feint = Files.readString(TODO.resolve("TodoFakeClapRuntime.java"));
		String swap = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		assert swap.contains("static void emitClapPerformance(")
				: "The clap performance must be a single extracted method, not two copies to keep in step";
		assert feint.contains("TodoBoogieWoogieRuntime.emitClapPerformance(")
				: "The feint must emit the real clap performance rather than its own lookalike";
		// One cue id for both casts: a separate id would let the two presentations drift apart.
		assert !feint.contains("TodoVfxIds.BOOGIE_WOOGIE")
				: "The feint must inherit the swap's cue from the shared method, not re-emit it";
		assert swap.contains("TodoVfxIds.BOOGIE_WOOGIE") && swap.contains("JujutsuSounds.PROJECTJJK_CLAP")
				: "The shared performance owns both the cue and the clap sound";
		assert feint.contains("TodoVfxIds.FEINT_TELL") && feint.contains("JujutsuNetworking.sendVfxCue(todo,")
				: "The caster-only tell must be a single-player send, never a broadcast";
		assert !feint.contains("broadcastVfxCue")
				: "Nothing about the feint being hollow may be broadcast";
	}

	private static void assertCooldownsAreIndependent() throws Exception {
		String feint = Files.readString(TODO.resolve("TodoFakeClapRuntime.java"));
		String swap = Files.readString(TODO.resolve("TodoBoogieWoogieRuntime.java"));
		assert feint.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY_SNEAK, TodoProfile.FAKE_CLAP_COOLDOWN_TICKS)")
				: "The feint must start its own slot's cooldown";
		// PRIMARY as a whole token, so the PRIMARY_SNEAK the feint legitimately uses does not match.
		assert !Pattern.compile("CharacterAbility\\.PRIMARY(?![_A-Z])").matcher(feint).find()
				: "The feint must never touch the real swap's cooldown slot";
		assert swap.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.PRIMARY, TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS)")
				: "The real swap must keep its own cooldown slot";
		int fake = TodoProfile.FAKE_CLAP_COOLDOWN_TICKS;
		int real = TodoProfile.BOOGIE_WOOGIE_COOLDOWN_TICKS;
		assert fake * 100 >= real * 25 && fake * 100 <= real * 40
				: "The feint cooldown must stay between a quarter and two fifths of the real one, got " + fake + " of " + real;
	}

	private static void assertSlotRoutingIsExhaustive() throws Exception {
		String router = Files.readString(TODO.resolve("TodoAbilityRouter.java"));
		for (CharacterAbility slot : CharacterAbility.values()) {
			assert router.contains(slot.name())
					: "Every input slot must be answered explicitly by Todo's router, missing: " + slot;
		}
		assert !Pattern.compile("default\\s*->").matcher(router).find()
				: "The slot switch must stay exhaustive so a new ability cannot fall into the swap";
		String definition = Files.readString(TODO.resolve("TodoDefinition.java"));
		assert definition.contains("TodoAbilityRouter.tryCast(player, slot, notify)")
				: "Todo's definition must send casts to his slot router";
		String executor = Files.readString(Path.of("src/main/java/jujutsu/mod/character/CharacterAbilityExecutor.java"));
		assert !executor.contains("Todo")
				: "The shared gate reaches Todo through the registry and must not name him";
		String keybinds = Files.readString(Path.of("src/client/java/jujutsu/mod/client/input/JujutsuKeybinds.java"));
		assert keybinds.contains("CharacterAbility.PRIMARY_SNEAK") && keybinds.contains("isShiftKeyDown()")
				: "The feint must ride the existing technique key with a shift modifier, not a new keybind";
		// Both technique keys go through the same translation, so the input layer picks slots by input
		// position only. Selecting by meaning is what forced every new vessel to edit this file.
		assert keybinds.contains("slot(client, CharacterAbility.PRIMARY, CharacterAbility.PRIMARY_SNEAK)")
				: "The technique key must name its slot from the sneak state, not from what Todo does with it";
		// Since the Megumi shadow kit the second key owns a sneak-hold gesture, so its slot choice is a
		// small state machine rather than the one-line slot(...) helper. The contract this pins is the
		// same: a plain press is the instant SECONDARY cast, and a sneaking tap still reaches the
		// SECONDARY_SNEAK slot (Todo's triple cycle) — now on release, inside the hold threshold.
		assert keybinds.contains("sendCharacterAbility(client, CharacterAbility.SECONDARY)")
				: "A plain second-key press must stay the instant SECONDARY cast";
		assert keybinds.contains("? CharacterAbility.SECONDARY_SNEAK_RELEASE")
				&& keybinds.contains(": CharacterAbility.SECONDARY_SNEAK")
				: "A sneaking tap must still send the SECONDARY_SNEAK slot when released before the hold threshold";
		assert !keybinds.contains("Todo")
				: "No vessel's runtime, profile or ability name may be visible from the input layer";
	}
}
