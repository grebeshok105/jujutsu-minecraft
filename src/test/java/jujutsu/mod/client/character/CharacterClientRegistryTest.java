package jujutsu.mod.client.character;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jujutsu.mod.character.JujutsuCharacter;

/**
 * The client registry's fail-closed contract, and the seam's one hard rule.
 *
 * <p>Mirrors the server-side program, plus the check that matters most here: the shared server interface
 * must never reach a client type. A dedicated server loads {@code CharacterDefinition} and every
 * implementation of it, and a client class on that path fails at load rather than at the line that
 * caused it, so the failure would surface far from its cause.
 *
 * <p>Assertions are source-text rather than reflective on purpose. Instantiating a client definition
 * touches renderers and GUI types, which need a client bootstrap this harness does not have.
 */
public final class CharacterClientRegistryTest {
	private static final Path MAIN = Path.of("src/main/java");
	private static final Path CLIENT = Path.of("src/client/java");
	private static final Path REGISTRY = CLIENT.resolve("jujutsu/mod/client/character/JujutsuCharacterClients.java");

	private CharacterClientRegistryTest() {}

	public static void main(String[] args) throws Exception {
		assertEveryVesselIsBound();
		assertTheServerHalfStaysServerOnly();
		assertNoSharedClientFileNamesAVessel();
		assertEveryVesselDeclaresItsOwnCard();
		System.out.println("CharacterClientRegistryTest passed");
	}

	private static void assertEveryVesselIsBound() throws Exception {
		String registry = Files.readString(REGISTRY);
		assert !Pattern.compile("default\\s*->|default\\s*:").matcher(registry).find()
				: "The client registry must have no catch-all, or a new vessel would silently inherit one";
		for (JujutsuCharacter character : JujutsuCharacter.values()) {
			assert Pattern.compile("case\\s+" + character.name() + "\\s*->").matcher(registry).find()
					: "Every vessel needs its own arm in the client registry, missing: " + character;
		}
		assert registry.contains("JujutsuCharacter[] characters = JujutsuCharacter.values()")
				: "The sweep must be derived from the enum, not written out beside the switch";
	}

	/**
	 * The reason the two definitions are separate interfaces rather than one. Checks the whole shared
	 * source set, not just the definition files, since anything they reach is loaded with them.
	 */
	private static void assertTheServerHalfStaysServerOnly() throws Exception {
		// Split so this file's own assertion text cannot match the pattern it is searching for.
		String minecraftClient = "net.minecraft." + "client";
		String modClient = "jujutsu.mod." + "client";
		try (Stream<Path> tree = Files.walk(MAIN)) {
			for (Path source : tree.filter(path -> path.toString().endsWith(".java")).toList()) {
				String body = Files.readString(source);
				assert !body.contains(minecraftClient) && !body.contains(modClient)
						: source + " is in the shared source set and must not reference a client type";
			}
		}
	}

	/** The whole point of the seam: shared client files ask the definition instead of naming a vessel. */
	private static void assertNoSharedClientFileNamesAVessel() throws Exception {
		String[] shared = {
				"jujutsu/mod/client/rich/theme/ClickGuiTheme.java",
				"jujutsu/mod/client/render/CharacterGeoRenderers.java",
				"jujutsu/mod/client/rich/modules/jujutsu/JujutsuModules.java",
				"jujutsu/mod/client/rich/screens/clickgui/impl/character/CharacterRosterPanel.java",
				"jujutsu/mod/client/JujutsuModClient.java",
		};
		for (String file : shared) {
			String body = Files.readString(CLIENT.resolve(file));
			for (String vessel : new String[] {"NOBARA", "TODO", "Nobara", "Todo"}) {
				assert !body.contains(vessel)
						: file + " must not name " + vessel + "; it should ask the vessel's client definition";
			}
		}
		String init = Files.readString(CLIENT.resolve("jujutsu/mod/client/JujutsuModClient.java"));
		assert init.contains("JujutsuCharacterClients.registerAll()")
				: "Client init must install vessel hooks through the registry";
		int director = init.indexOf("VfxDirector.initialize()");
		int vessels = init.indexOf("JujutsuCharacterClients.registerAll()");
		assert director >= 0 && vessels > director
				: "Vessel hooks register recipes into the director, so the director must exist first";
	}

	/**
	 * A card with no abilities draws an empty strip, which is right for NONE and a bug for anyone else.
	 * Read off the source because building an entry needs client classes.
	 */
	private static void assertEveryVesselDeclaresItsOwnCard() throws Exception {
		String[][] vessels = {
				{"jujutsu/mod/client/character/nobara/NobaraClientDefinition.java", "5"},
				{"jujutsu/mod/client/character/todo/TodoClientDefinition.java", "3"},
		};
		for (String[] vessel : vessels) {
			String body = Files.readString(CLIENT.resolve(vessel[0]));
			int abilities = body.split("new CharacterRosterEntry.Ability\\(", -1).length - 1;
			assert abilities == Integer.parseInt(vessel[1])
					: vessel[0] + " must list " + vessel[1] + " abilities, its router answers that many, found " + abilities;
			assert body.contains("createRenderer") && body.contains("accent()")
					: vessel[0] + " must declare how it is drawn and what colour it paints the menu";
		}
	}
}
