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
	private static final Path NOBARA_DEFINITION = Path.of(
			"src/client/java/jujutsu/mod/client/character/nobara/NobaraClientDefinition.java");
	@Test
	void everyLiveClapRouteUsesTheCasterAnchor() throws Exception {
		String clap = Files.readString(CLAP_RUNTIME);
		assertTrue(clap.contains(
				"VfxCues.anchoredDirected(cueId, origin, todo.getId(), origin,"),
				"the clap anchor position must be the cue origin, preserving zero offset after a swap");
		assertTrue(Files.readString(FAKE_CLAP_RUNTIME).contains("TodoBoogieWoogieRuntime.emitClapPerformance"),
				"fake clap must share the anchored route");
		assertTrue(Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoPairSwapRuntime.java"))
				.contains("TodoBoogieWoogieRuntime.emitSwapFeedback"),
				"pair swap must share the single swap-feedback emission point");
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
	void blackFlashUsesTheSharedIdAndBodyDispatchGoesThroughTheDefinition() throws Exception {
		assertTrue(Files.readString(TODO_BLACK_FLASH).contains("SharedVfxIds.BLACK_FLASH"));
		assertTrue(Files.readString(NOBARA_HAMMER).contains("SharedVfxIds.BLACK_FLASH"));
		String sharedRecipe = Files.readString(SHARED_BLACK_FLASH);
		assertFalse(sharedRecipe.contains("NobaraPlayerGeoAnimatable"),
				"shared code asks the definition, never which character the player is");
		assertTrue(sharedRecipe.contains("triggerActionAnimation"));
		assertTrue(sharedRecipe.contains("VfxWorldChannel.ImpactStyle.BLACK_FLASH"));
		assertTrue(Files.readString(NOBARA_DEFINITION).contains("NobaraPlayerGeoAnimatable.INSTANCE.triggerAction"),
				"the Nobara body clip lives behind the vessel's own definition");
	}

	@Test
	void feintAndRealClapShareTheBeatFormulaAndOnlySwapTheAnimationHook() throws Exception {
		String recipes = Files.readString(RECIPES);
		assertTrue(recipes.contains("TodoVfxIds.FEINT_CLAP"));
		assertTrue(recipes.contains("intensity(cue) - 1"));
		assertTrue(recipes.contains("TodoAnimationHooks.triggerFakeClap(cue)"));
		assertTrue(recipes.contains("TodoAnimationHooks.triggerBoogieWoogie(cue)"));
	}

}
