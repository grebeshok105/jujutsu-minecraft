package jujutsu.mod.vfx;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VfxFactoryMigrationContractTest {
	private static final List<Path> MIGRATED_RUNTIME_FILES = List.of(
			Path.of("src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrapRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/SelfResonanceRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/todo/TodoBlackFlashRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/todo/TodoEntityMarkRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/todo/TodoFakeClapRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/todo/TodoPairSwapRuntime.java"),
			Path.of("src/main/java/jujutsu/mod/character/todo/TodoSwapMomentumRuntime.java"));

	@Test
	void migratedRuntimesUseTransportFactories() throws Exception {
		for (Path file : MIGRATED_RUNTIME_FILES) {
			String source = withoutCommentsAndLiterals(Files.readString(file));
			assertTrue(source.contains("VfxCues."), "missing VfxCues call in " + file);
			if (file.getFileName().toString().equals("TodoBoogieWoogieRuntime.java")) {
				assertFalse(genericMethods(source).contains("new VfxCue"),
						"Todo clap and endpoint paths must use factories; only overloaded readers may construct locally");
			} else {
				assertFalse(source.contains("new VfxCue"), "direct generic construction remains in " + file);
			}
		}
	}

	@Test
	void overloadedTodoPayloadsRemainExplicitAndLocal() throws Exception {
		String source = withoutCommentsAndLiterals(Files.readString(
			Path.of("src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java")));
		assertTrue(methodBody(source, "broadcastAfterimage").contains("new VfxCue"));
		assertTrue(methodBody(source, "broadcastArrival").contains("new VfxCue"));
		assertFalse(methodBody(source, "emitClapPerformance").contains("new VfxCue"));
		assertFalse(methodBody(source, "broadcastSwapEndpoint").contains("new VfxCue"));
	}

	private static String genericMethods(String source) {
		return source
				.replace(methodBody(source, "broadcastAfterimage"), "")
				.replace(methodBody(source, "broadcastArrival"), "");
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

	private static String withoutCommentsAndLiterals(String source) {
		StringBuilder result = new StringBuilder(source.length());
		for (int index = 0; index < source.length();) {
			char current = source.charAt(index);
			if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
				while (index < source.length() && source.charAt(index) != '\n') index++;
			} else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
				index += 2;
				while (index + 1 < source.length() && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) index++;
				index = Math.min(source.length(), index + 2);
			} else if (current == '"') {
				index++;
				while (index < source.length() && source.charAt(index) != '"') index += source.charAt(index) == '\\' ? 2 : 1;
				index++;
				result.append("\"\"");
			} else {
				result.append(current);
				index++;
			}
		}
		return result.toString();
	}
}
