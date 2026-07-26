package jujutsu.mod.character;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.nobara.NobaraDefinition;
import jujutsu.mod.character.todo.TodoDefinition;

/**
 * Binds every {@link JujutsuCharacter} constant to its server-side definition.
 *
 * <p>This switch is the compile-time guarantee, and it is the only one on the server side. It has no
 * {@code default} arm on purpose: adding an enum constant stops the build here until someone says what
 * that vessel does, rather than letting it inherit whatever a catch-all happened to return. A registry
 * map plus a test would fail the <i>build</i>; only an exhaustive switch fails <i>compilation</i>.
 *
 * <p>Definitions are stateless and shared, so they are created once. Anything per-player lives in the
 * player's attachment or in the runtime that owns it, never in a field here.
 */
public final class JujutsuCharacters {
	// Suffixed rather than named after the constants they answer for. With bare names the arms read
	// "case NONE -> NONE", and a transposition — "case NOBARA -> TODO" — reads exactly like a correct
	// line. The suffix makes the wrong one look wrong; the registry test catches it either way.
	private static final CharacterDefinition NONE_DEFINITION = new NoneDefinition();
	private static final CharacterDefinition NOBARA_DEFINITION = new NobaraDefinition();
	private static final CharacterDefinition TODO_DEFINITION = new TodoDefinition();

	private JujutsuCharacters() {}

	public static CharacterDefinition definition(JujutsuCharacter character) {
		return switch (character) {
			case NONE -> NONE_DEFINITION;
			case NOBARA -> NOBARA_DEFINITION;
			case TODO -> TODO_DEFINITION;
		};
	}

	/** The definition of whatever the player currently is, never null. */
	public static CharacterDefinition of(ServerPlayer player) {
		return definition(CharacterSelectionManager.selected(player));
	}

	/**
	 * Every registered definition, for shared code that has to sweep all of them — clearing attribute
	 * modifiers, for one. Derived from the enum rather than listed by hand, so it cannot fall behind the
	 * switch above the way a second hand-maintained list would.
	 */
	public static CharacterDefinition[] all() {
		JujutsuCharacter[] characters = JujutsuCharacter.values();
		CharacterDefinition[] definitions = new CharacterDefinition[characters.length];
		for (int index = 0; index < characters.length; index++) {
			definitions[index] = definition(characters[index]);
		}
		return definitions;
	}
}
