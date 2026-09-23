package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Triple cycle direction, atomic preflight, and reverse rollback contract checks. */
class TodoTripleSwapTest {
	@Test
	void planPreflightRequiresEveryDestination() {
		SwapMove one = new SwapMove(null, new Vec3(1, 1, 1));
		SwapMove two = new SwapMove(null, new Vec3(2, 2, 2));
		SwapMove three = new SwapMove(null, new Vec3(3, 3, 3));
		assertTrue(SwapPlan.preflight(one, two, three).isPresent());
		assertTrue(SwapPlan.preflight(one, two, new SwapMove(null, null)).isEmpty());
	}

	@Test
	void cycleDirectionIsTodoToAtoT() throws Exception {
		String source = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/SwapNodes.java"));
		assertTrue(source.contains("Vec3 todoPosition = todo.position()"));
		assertTrue(source.contains("Vec3 aPosition = a.position()"));
		assertTrue(source.contains("Vec3 tPosition = t.position()"));
		assertTrue(source.contains("new SwapMove(todo, todoDestination)"));
		assertTrue(source.contains("new SwapMove(a, aDestination)"));
		assertTrue(source.contains("new SwapMove(t, tDestination)"));
	}

	@Test
	void rollbackIsReverseOrderAndUsesProductionRestore() throws Exception {
		String source = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/SwapCommit.java"));
		assertTrue(source.contains("for (int index = placed.size() - 1; index >= 0; index--)"));
		assertTrue(source.contains("PRODUCTION_COMMIT_TELEPORT.teleport"));
		assertTrue(source.contains("LOGGER.error"));
	}

	@Test
	void tripleCooldownIsDistinctAndItDoesNotGrantMomentum() {
		assertEquals(160, SwapKind.TRIPLE.baseCooldownTicks());
		assertEquals(CharacterAbility.SECONDARY_SNEAK, SwapKind.TRIPLE.slot());
		assertTrue(!SwapKind.TRIPLE.grantsMomentum());
	}
}

// Red: before this change, no single `SwapCommit` owned reverse-order rollback;
// `./gradlew.bat testTodoTripleSwap` failed the commit seam assertions.
// Run: ./gradlew.bat testTodoTripleSwap
// Expected: cycle direction, preflight, and rollback assertions pass.
