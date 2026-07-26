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
	private static final CharacterDefinition NONE = new NoneDefinition();
	private static final CharacterDefinition NOBARA = new NobaraDefinition();
	private static final CharacterDefinition TODO = new TodoDefinition();

	private JujutsuCharacters() {}

	public static CharacterDefinition definition(JujutsuCharacter character) {
		return switch (character) {
			case NONE -> NONE;
			case NOBARA -> NOBARA;
			case TODO -> TODO;
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
