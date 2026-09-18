package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * Source-level boundary checks for issue #110's command and agent surface.
 * The surface must enter through IncidentControl: implementation/runtime classes are not a second
 * API. This deliberately reads source because a forbidden dependency can disappear from bytecode
 * through constant folding or dynamic loading.
 */
final class IncidentSurfaceTripwireTest {

	private static final Path COMMANDS = Path.of("src/main/java/jujutsu/mod/command/JujutsuCommands.java");
	private static final Path TOOL_ROOT = Path.of("src/mcpdev/java/jujutsu/mcpdev");
	private static final List<Map.Entry<String, String>> OPERATIONS = List.of(
			Map.entry("spawn", "Spawn"),
			Map.entry("object_spawn", "ObjectSpawn"),
			Map.entry("inspect", "Inspect"),
			Map.entry("list", "List"),
			Map.entry("set_stage", "SetStage"),
			Map.entry("advance", "Advance"),
			Map.entry("escalate", "Escalate"),
			Map.entry("seal", "Seal"),
			Map.entry("unseal", "Unseal"),
			Map.entry("damage_seal", "DamageSeal"),
			Map.entry("relocate", "Relocate"),
			Map.entry("secondary", "Secondary"),
			Map.entry("identify", "Identify"),
			Map.entry("cleanup", "Cleanup"),
			Map.entry("seed", "Seed"));
	private static final Set<String> FORBIDDEN_IMPLEMENTATIONS = Set.of(
			"IncidentRuntime", "InfectionSink", "IncidentSavedData");
	private static final Pattern COMMENTS_AND_LITERALS = Pattern.compile(
			"(?s)/\\*.*?\\*/|//[^\\r\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'");

	@Test
	void commandsAndToolsUseOnlyTheControlFacade() {
		String commandSource = withoutCommentsAndLiterals(read(COMMANDS));
		assertTrue(commandSource.contains("IncidentControl"), "incident commands must reference IncidentControl");
		assertFalse(containsForbidden(commandSource), "commands must not bypass the control facade");

		for (Map.Entry<String, String> operation : OPERATIONS) {
			String toolSource = withoutCommentsAndLiterals(read(toolPath(operation.getValue())));
			assertTrue(toolSource.contains("IncidentControl"),
					() -> operation.getKey() + " tool must reference IncidentControl");
			assertFalse(containsForbidden(toolSource),
					() -> operation.getKey() + " tool must not reference implementation state directly");
		}
	}

	@Test
	void everyDevOperationHasACommandAndRegisteredToolSource() {
		String commandSource = read(COMMANDS);
		for (Map.Entry<String, String> operation : OPERATIONS) {
			String literal = "literal(\"" + operation.getKey() + "\")";
			assertTrue(commandSource.contains(literal), () -> "missing /jujutsu incident " + operation.getKey());
			String toolSource = read(toolPath(operation.getValue()));
			assertTrue(toolSource.contains("name = \"jujutsu_incident_" + operation.getKey() + "\""),
					() -> "missing MCP name for " + operation.getKey());
		}
	}

	@Test
	void noArbitraryEvaluationOrScriptToolCanEnterTheSurface() {
		Set<String> expected = OPERATIONS.stream()
				.map(entry -> "JujutsuIncident" + entry.getValue() + "Tool.java")
				.collect(Collectors.toCollection(TreeSet::new));
		Set<String> actual = listIncidentTools();
		assertEquals(expected, actual, "incident tool set must stay a fixed controlled operation list");
		assertTrue(actual.stream().noneMatch(name -> {
			String lower = name.toLowerCase(java.util.Locale.ROOT);
			return lower.contains("eval") || lower.contains("exec") || lower.contains("script");
		}), "eval/exec/script tools are forbidden");
	}

	private static Path toolPath(String suffix) {
		return TOOL_ROOT.resolve("JujutsuIncident" + suffix + "Tool.java");
	}

	private static Set<String> listIncidentTools() {
		try (var paths = Files.list(TOOL_ROOT)) {
			return paths
					.map(path -> path.getFileName().toString())
					.filter(name -> name.startsWith("JujutsuIncident") && name.endsWith("Tool.java"))
					.collect(Collectors.toCollection(TreeSet::new));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean containsForbidden(String source) {
		return FORBIDDEN_IMPLEMENTATIONS.stream().anyMatch(source::contains);
	}

	private static String withoutCommentsAndLiterals(String source) {
		return COMMENTS_AND_LITERALS.matcher(source).replaceAll(" ");
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot read " + path, e);
		}
	}
}
