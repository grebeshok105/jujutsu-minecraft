package jujutsu.mod.character;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The vessel registry's fail-closed contract.
 *
 * <p>Two guarantees, and they are not the same one. The exhaustive {@code switch} in
 * {@link JujutsuCharacters} is a <b>compile-time</b> guarantee: a new {@link JujutsuCharacter} constant
 * stops the build there until it is bound. This program is the <b>build-time</b> half, covering what a
 * switch cannot express — that the definition returned for a constant actually claims to be that
 * constant, and that nothing client-only leaked into the shared interface.
 */
public final class CharacterDefinitionRegistryTest {
	private static final Path MAIN = Path.of("src/main/java");

	private CharacterDefinitionRegistryTest() {}

	public static void main(String[] args) throws Exception {
		assertEveryVesselResolves();
		assertTheSweepMatchesTheSwitch();
		assertTheSwitchCannotFallThrough();
		assertNothingClientOnlyLeakedIn();
		assertEveryVesselRuntimeIsRegistered();
		System.out.println("CharacterDefinitionRegistryTest passed");
	}

	/**
	 * Mod init used to hand-list twelve per-vessel registrations; they moved into the definitions. The
	 * risk in that move is dropping one, which would silently disable a whole runtime with no error at
	 * any point. So the expected list is read off the source tree rather than written down here: every
	 * class under a vessel's package that exposes {@code register()} must be called by its definition.
	 */
	private static void assertEveryVesselRuntimeIsRegistered() throws Exception {
		String[][] vessels = {
				{"jujutsu/mod/character/nobara", "jujutsu/mod/character/nobara/NobaraDefinition.java"},
				{"jujutsu/mod/character/todo", "jujutsu/mod/character/todo/TodoDefinition.java"},
		};
		for (String[] vessel : vessels) {
			String definition = Files.readString(MAIN.resolve(vessel[1]));
			try (Stream<Path> tree = Files.walk(MAIN.resolve(vessel[0]))) {
				for (Path source : tree.filter(path -> path.toString().endsWith(".java")).toList()) {
					String body = Files.readString(source);
					if (!body.contains("public static void register()")) {
						continue;
					}
					String owner = source.getFileName().toString().replace(".java", "");
					assert definition.contains(owner + ".register()")
							: owner + " exposes register() but no vessel definition calls it, so its listeners never install";
				}
			}
		}
		String init = Files.readString(Path.of("src/main/java/jujutsu/mod/JujutsuMod.java"));
		assert init.contains("definition.registerServerHooks()")
				: "Mod init must install vessel listeners through the registry";
		for (String vesselPackage : new String[] {"character.nobara", "character.todo"}) {
			assert !init.contains(vesselPackage)
					: "Mod init must not reach into " + vesselPackage + "; that is what the hook replaced";
		}
	}

	/**
	 * The switch cannot catch a definition wired to the wrong arm — {@code case TODO -> NOBARA} compiles
	 * and type-checks. Asking each definition who it thinks it is closes exactly that gap.
	 */
	private static void assertEveryVesselResolves() {
		for (JujutsuCharacter character : JujutsuCharacter.values()) {
			CharacterDefinition definition = JujutsuCharacters.definition(character);
			assert definition != null : "Every vessel must resolve to a definition, missing: " + character;
			assert definition.id() == character
					: "The registry bound " + character + " to a definition that claims to be " + definition.id();
		}
	}

	private static void assertTheSweepMatchesTheSwitch() {
		CharacterDefinition[] all = JujutsuCharacters.all();
		assert all.length == JujutsuCharacter.values().length
				: "The sweep must cover every vessel, got " + all.length + " of " + JujutsuCharacter.values().length;
		Set<JujutsuCharacter> seen = new HashSet<>();
		for (CharacterDefinition definition : all) {
			assert seen.add(definition.id())
					: "A vessel must appear once in the sweep; clearing attributes twice would hide a bug: " + definition.id();
		}
	}

	private static void assertTheSwitchCannotFallThrough() throws Exception {
		String registry = Files.readString(MAIN.resolve("jujutsu/mod/character/JujutsuCharacters.java"));
		assert !Pattern.compile("default\\s*->|default\\s*:").matcher(registry).find()
				: "The registry switch must have no catch-all, or a new vessel would silently inherit one";
		for (JujutsuCharacter character : JujutsuCharacter.values()) {
			assert Pattern.compile("case\\s+" + character.name() + "\\s*->").matcher(registry).find()
					: "Every vessel needs its own arm in the registry, missing: " + character;
		}
		// Derived from the enum rather than listed by hand: a second hand-kept list is the one thing that
		// could disagree with the switch without failing compilation.
		assert registry.contains("JujutsuCharacter[] characters = JujutsuCharacter.values()")
				: "The sweep must be derived from the enum, not written out beside the switch";
	}

	/**
	 * A dedicated server loads this interface and every implementation of it. One client import here
	 * would drag client classes onto a machine that has none, and the crash would happen at load, far
	 * from the line that caused it.
	 */
	private static void assertNothingClientOnlyLeakedIn() throws Exception {
		String[] shared = {
				"jujutsu/mod/character/CharacterDefinition.java",
				"jujutsu/mod/character/JujutsuCharacters.java",
				"jujutsu/mod/character/NoneDefinition.java",
				"jujutsu/mod/character/nobara/NobaraDefinition.java",
				"jujutsu/mod/character/todo/TodoDefinition.java",
		};
		// Split so this file's own assertion text cannot match the pattern it is searching for.
		String minecraftClient = "net.minecraft." + "client";
		String modClient = "jujutsu.mod." + "client";
		for (String file : shared) {
			String source = Files.readString(MAIN.resolve(file));
			assert !source.contains(minecraftClient) && !source.contains(modClient)
					: file + " must not reference a client-only type; it is loaded on a dedicated server";
		}
	}
}
