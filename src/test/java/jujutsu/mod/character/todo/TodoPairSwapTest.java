package jujutsu.mod.character.todo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import jujutsu.mod.character.CharacterAbility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pair-selection behavior plus source pins for the shared node transaction. */
class TodoPairSwapTest {
	private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
			ResourceLocation.parse("minecraft:overworld"));
	private static final ResourceKey<Level> NETHER = ResourceKey.create(Registries.DIMENSION,
			ResourceLocation.parse("minecraft:the_nether"));

	@Test
	void selectionExpiresOnItsOwnClock() {
		TodoPendingSelection selection = new TodoPendingSelection(OVERWORLD, UUID.randomUUID(), 42, 100);
		assertFalse(selection.isExpired(99));
		assertTrue(selection.isExpired(100));
		assertTrue(selection.isIn(OVERWORLD));
		assertFalse(selection.isIn(NETHER));
	}

	@Test
	void identityRequiresBothEntityUuidAndDimension() {
		UUID uuid = UUID.randomUUID();
		TodoPendingSelection selection = new TodoPendingSelection(OVERWORLD, uuid, 7, 100);
		assertTrue(selection.identifies(uuid));
		assertFalse(selection.identifies(UUID.randomUUID()));
		assertTrue(selection.isIn(OVERWORLD));
		assertFalse(selection.isIn(NETHER));
	}

	@Test
	void pairRuntimeUsesSharedNodePlanAndCommit() throws Exception {
		String source = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoPairSwapRuntime.java"));
		assertTrue(source.contains("SwapNodes.planExchange"));
		assertTrue(source.contains("SwapCommit.commit"));
		assertTrue(source.contains("TodoSwapHooks.fireAfterCommit(todo, SwapKind.PAIR"));
		assertTrue(source.contains("TodoCooldownPolicy.arm"));
	}

	@Test
	void tripleUsesTheDistinctSneakSlotAndKeepsTheMarkUntilSuccess() throws Exception {
		String source = Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoPairSwapRuntime.java"));
		assertTrue(source.contains("SwapNodes.planCycle"));
		assertTrue(source.contains("SwapKind.TRIPLE"));
		assertTrue(source.contains("TodoTransientState.clearPairSelection(todo.getUUID())"));
		assertEquals(CharacterAbility.SECONDARY_SNEAK, SwapKind.TRIPLE.slot());
	}
}

// Red: before the unified pipeline, pair/triple used independent plan records and direct placement;
// `./gradlew.bat testTodoPairSwap` failed the shared-node source contract.
// Run: ./gradlew.bat testTodoPairSwap
// Expected: all pair-selection and pipeline assertions pass.
