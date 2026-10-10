package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.todo.TodoSwapGates.ClapGate;

/** Fake Clap remains a deception-only, FEINT_CLAP presentation path. */
public final class TodoFakeClapTest {
	private TodoFakeClapTest() {}

	public static void main(String[] args) throws Exception {
		assert TodoSwapGates.evaluate(false, true, false, false, true) == ClapGate.ALLOWED;
		assert TodoSwapGates.evaluate(false, true, false, false, false) == ClapGate.HANDS_FULL;
		assert TodoSwapGates.evaluate(true, true, false, false, true) == ClapGate.UNAVAILABLE;
		String fake = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoFakeClapRuntime.java"));
		assert fake.contains("TodoVfxIds.FEINT_CLAP");
		assert !fake.contains("TodoVfxIds.BOOGIE_WOOGIE");
		for (String forbidden : new String[] {"teleportTo", "SwapPlan", "findSafeDestination", "TargetResolver",
				"SWAP_ENDPOINT", "SWAP_ARRIVAL", "SWAP_AFTERIMAGE", "MOMENTUM_STRIKE", "SwapMomentum", "emitSwapImpact"}) {
			assert !fake.contains(forbidden) : "Fake Clap must not use " + forbidden;
		}
		String router = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoAbilityRouter.java"));
		assert router.contains("case PRIMARY_SNEAK -> TodoFakeClapRuntime.tryCast");
		assert CharacterAbility.PRIMARY_SNEAK != CharacterAbility.PRIMARY;
		System.out.println("TodoFakeClapTest passed");
	}
}

// Red: before the rework, Fake Clap emitted the real BOOGIE_WOOGIE cue;
// `./gradlew.bat testTodoFakeClap` failed the FEINT_CLAP-only source contract.
// Run: ./gradlew.bat testTodoFakeClap
// Expected: TodoFakeClapTest passed.
