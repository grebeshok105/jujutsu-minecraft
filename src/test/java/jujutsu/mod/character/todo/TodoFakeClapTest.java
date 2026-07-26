package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.todo.TodoSwapGates.ClapGate;

/** The feint's product contract: same gates, same performance, no swap, its own cooldown. */
public final class TodoFakeClapTest {
	private static final Path TODO = Path.of("src/main/java/jujutsu/mod/character/todo");

	private TodoFakeClapTest() {}

	public static void main(String[] args) throws Exception {
		assertClapGateTruthTable();
		assertAbilitySlotsAreWireStable();
		assertFeintCarriesNoSwapMachinery();
		assertFeintReusesTheRealClapPerformance();
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

	private static void assertAbilitySlotsAreWireStable() {
		assert CharacterAbility.PRIMARY.networkId() == 0 : "PRIMARY must keep network id 0";
		assert CharacterAbility.SECONDARY.networkId() == 1 : "SECONDARY must append rather than renumber";
		assert CharacterAbility.byNetworkId(0) == CharacterAbility.PRIMARY : "Network id 0 must resolve to PRIMARY";
		assert CharacterAbility.byNetworkId(1) == CharacterAbility.SECONDARY : "Network id 1 must resolve to SECONDARY";
		assert CharacterAbility.byNetworkId(99) == null : "An unknown slot id must resolve to null, not a default";
	}

	private static void assertFeintCarriesNoSwapMachinery() throws Exception {
		String feint = Files.readString(TODO.resolve("TodoFakeClapRuntime.java"));
		// The server must know the cast is hollow from the start; there is no swap to cancel.
		for (String forbidden : new String[] {"teleportTo", "TodoSwapPlan", "findSafeDestination", "TargetResolver", "SWAP_ENDPOINT"}) {
			assert !feint.contains(forbidden)
					: "The feint must not reach for " + forbidden + "; it never starts a swap";
		}
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
		assert feint.contains("CharacterAbilityCooldowns.start(todo, CharacterAbility.SECONDARY, TodoProfile.FAKE_CLAP_COOLDOWN_TICKS)")
				: "The feint must start its own slot's cooldown";
		assert !feint.contains("CharacterAbility.PRIMARY")
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
		assert router.contains("case PRIMARY ->") && router.contains("case SECONDARY ->")
				: "Every Todo slot must be routed explicitly";
		assert !router.contains("default ->")
				: "The slot switch must stay exhaustive so a new ability cannot fall into the swap";
		String executor = Files.readString(Path.of("src/main/java/jujutsu/mod/character/CharacterAbilityExecutor.java"));
		assert executor.contains("TodoAbilityRouter.tryCast(player, ability, notify)")
				: "The executor must dispatch Todo through the slot router";
		String keybinds = Files.readString(Path.of("src/client/java/jujutsu/mod/client/input/JujutsuKeybinds.java"));
		assert keybinds.contains("CharacterAbility.SECONDARY") && keybinds.contains("isShiftKeyDown()")
				: "The feint must ride the existing technique key with a shift modifier, not a new keybind";
	}
}
