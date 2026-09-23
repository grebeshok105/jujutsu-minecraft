package jujutsu.mod.client.vfx.todo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class TodoAnimationHooksContractTest {
	private static final Path HOOKS = Path.of(
			"src/client/java/jujutsu/mod/client/vfx/todo/TodoAnimationHooks.java");
	private static final Path CLAP_RUNTIME = Path.of(
			"src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java");
	private static final Path FAKE_CLAP_RUNTIME = Path.of(
			"src/main/java/jujutsu/mod/character/todo/TodoFakeClapRuntime.java");
	private static final Path TODO_BLACK_FLASH = Path.of(
			"src/main/java/jujutsu/mod/character/todo/TodoBlackFlashRuntime.java");
	private static final Path NOBARA_HAMMER = Path.of(
			"src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java");
	private static final Path RECIPES = Path.of(
			"src/client/java/jujutsu/mod/client/vfx/todo/TodoVfxRecipes.java");
	private static final Path SHARED_BLACK_FLASH = Path.of(
			"src/client/java/jujutsu/mod/client/vfx/shared/SharedVfxRecipes.java");
	@Test
	void everyLiveClapRouteUsesTheCasterAnchor() throws Exception {
		String clap = Files.readString(CLAP_RUNTIME);
		String performance = methodBody(clap, "emitClapPerformance");
		assertTrue(performance.contains(
				"VfxCues.anchoredDirected(TodoVfxIds.BOOGIE_WOOGIE, origin, todo.getId(), origin,"),
				"the clap anchor position must be the cue origin, preserving zero offset after a swap");
		assertTrue(Files.readString(FAKE_CLAP_RUNTIME).contains("TodoBoogieWoogieRuntime.emitClapPerformance"),
				"fake clap must share the anchored route");
		assertTrue(Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoPairSwapRuntime.java"))
				.contains("TodoBoogieWoogieRuntime.emitSwapImpact"));
	}

	@Test
	void noAnchorClapDoesNotSelectANearbyLocalPlayer() throws Exception {
		String hooks = Files.readString(HOOKS);
		assertTrue(hooks.contains("cue.anchorEntityId() == VfxCue.NO_ANCHOR"));
		assertFalse(hooks.contains("distanceToSqr"));
		assertFalse(hooks.contains("legacy NO_ANCHOR"));
	}

	@Test
	void everyTodoAnimationHookUsesTheCueContract() throws Exception {
		String hooks = Files.readString(HOOKS);
		for (String name : new String[] {
				"triggerBoogieWoogie", "triggerFakeClap", "triggerStoneThrow",
				"triggerMomentumStrike", "triggerPeak", "triggerRevised"}) {
			assertTrue(hooks.contains("void " + name + "(VfxCue cue)"), "missing VfxCue hook: " + name);
		}
		assertTrue(Files.readString(RECIPES).contains("TodoAnimationHooks.triggerBoogieWoogie(cue)"));
		assertTrue(Files.readString(RECIPES).contains("TodoAnimationHooks.triggerFakeClap(cue)"));
		assertTrue(Files.readString(RECIPES).contains("TodoAnimationHooks.triggerStoneThrow(cue)"));
		assertTrue(Files.readString(RECIPES).contains("TodoAnimationHooks.triggerMomentumStrike(cue)"));
		assertTrue(Files.readString(RECIPES).contains("TodoAnimationHooks.triggerPeak(cue)"));
		assertTrue(Files.readString(RECIPES).contains("TodoAnimationHooks.triggerRevised(cue)"));
	}

	@Test
	void blackFlashUsesTheSharedIdAndNobaraDispatchStaysInTheSharedRecipe() throws Exception {
		assertTrue(Files.readString(TODO_BLACK_FLASH).contains("SharedVfxIds.BLACK_FLASH"));
		assertTrue(Files.readString(NOBARA_HAMMER).contains("SharedVfxIds.BLACK_FLASH"));
		String sharedRecipe = Files.readString(SHARED_BLACK_FLASH);
		assertTrue(sharedRecipe.contains("instanceof NobaraPlayerGeoAnimatable"));
		assertTrue(sharedRecipe.contains("VfxWorldChannel.ImpactStyle.BLACK_FLASH"));
	}

	@Test
	void feintAndRealClapShareTheBeatFormulaAndOnlySwapTheAnimationHook() throws Exception {
		String recipes = Files.readString(RECIPES);
		assertTrue(recipes.contains("TodoVfxIds.FEINT_CLAP"));
		assertTrue(recipes.contains("intensity(cue) - 1"));
		assertTrue(recipes.contains("TodoAnimationHooks.triggerFakeClap(cue)"));
		assertTrue(recipes.contains("TodoAnimationHooks.triggerBoogieWoogie(cue)"));
	}

	private static String methodBody(String source, String methodName) {
		int start = source.indexOf("void " + methodName + "(");
		assertTrue(start >= 0, "missing method " + methodName);
		int open = source.indexOf('{', start);
		int depth = 0;
		for (int index = open; index < source.length(); index++) {
			char current = source.charAt(index);
			if (current == '{') depth++;
			if (current == '}' && --depth == 0) return source.substring(start, index + 1);
		}
		throw new AssertionError("unterminated method " + methodName);
	}
}
