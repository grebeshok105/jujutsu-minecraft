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

	@Test
	void everyLiveClapRouteUsesTheCasterAnchor() throws Exception {
		String clap = Files.readString(CLAP_RUNTIME);
		String performance = methodBody(clap, "emitClapPerformance");
		assertTrue(performance.contains("VfxCues.anchoredDirected"));
		assertTrue(performance.contains("todo.getId()"));
		assertTrue(Files.readString(FAKE_CLAP_RUNTIME).contains("TodoBoogieWoogieRuntime.emitClapPerformance"),
				"fake clap must share the anchored route");
		assertTrue(Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoPairSwapRuntime.java"))
				.contains("TodoBoogieWoogieRuntime.emitSwapImpact"));
		assertTrue(Files.readString(Path.of("src/main/java/jujutsu/mod/character/todo/TodoMarkerSwapRuntime.java"))
				.contains("TodoBoogieWoogieRuntime.emitSwapImpact"));
	}

	@Test
	void noAnchorClapDoesNotSelectANearbyLocalPlayer() throws Exception {
		String hooks = Files.readString(HOOKS);
		assertTrue(hooks.contains("cue.anchorEntityId() == VfxCue.NO_ANCHOR"));
		assertFalse(hooks.contains("distanceToSqr"));
		assertFalse(hooks.contains("client.player"));
		assertFalse(hooks.contains("legacy NO_ANCHOR"));
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
