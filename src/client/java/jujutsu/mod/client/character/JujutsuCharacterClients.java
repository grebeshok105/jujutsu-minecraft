package jujutsu.mod.client.character;

import java.util.Arrays;
import java.util.Comparator;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.megumi.MegumiClientDefinition;
import jujutsu.mod.client.character.nobara.NobaraClientDefinition;
import jujutsu.mod.client.character.todo.TodoClientDefinition;

/**
 * Binds every {@link JujutsuCharacter} constant to its client-side definition.
 *
 * <p>The client half of the compile-time guarantee. Together with {@code JujutsuCharacters} on the
 * server these are the only two exhaustive switches a new vessel must satisfy, and until it satisfies
 * both, nothing compiles — which is the point: a vessel that renders but cannot cast, or casts but has
 * no card, is a worse failure than a build error.
 */
public final class JujutsuCharacterClients {
	private static final CharacterClientDefinition NONE_DEFINITION = new NoneClientDefinition();
	private static final CharacterClientDefinition NOBARA_DEFINITION = new NobaraClientDefinition();
	private static final CharacterClientDefinition TODO_DEFINITION = new TodoClientDefinition();
	private static final CharacterClientDefinition MEGUMI_DEFINITION = new MegumiClientDefinition();

	private JujutsuCharacterClients() {}

	public static CharacterClientDefinition definition(JujutsuCharacter character) {
		return switch (character) {
			case NONE -> NONE_DEFINITION;
			case NOBARA -> NOBARA_DEFINITION;
			case TODO -> TODO_DEFINITION;
			case MEGUMI -> MEGUMI_DEFINITION;
		};
	}

	/**
	 * Every definition, in the enum's order, derived from it rather than listed again — the roster draws
	 * its cards straight from this, so a hand-kept second list would silently drop a vessel from the menu.
	 */
	public static CharacterClientDefinition[] all() {
		JujutsuCharacter[] characters = JujutsuCharacter.values();
		CharacterClientDefinition[] definitions = new CharacterClientDefinition[characters.length];
		for (int index = 0; index < characters.length; index++) {
			definitions[index] = definition(characters[index]);
		}
		return definitions;
	}

	/**
	 * Every definition in menu order, vessels first and "no vessel" last.
	 *
	 * <p>Separate from {@link #all()} because enum order is not menu order: the enum starts with NONE so
	 * it reads as the absent value, while the menu has always shown it after the vessels.
	 */
	public static CharacterClientDefinition[] inRosterOrder() {
		CharacterClientDefinition[] ordered = all();
		Arrays.sort(ordered, Comparator.comparingInt(CharacterClientDefinition::rosterOrder));
		return ordered;
	}

	/** Installs every vessel's client-only listeners. Called once from client init. */
	public static void registerAll() {
		for (CharacterClientDefinition definition : all()) {
			definition.registerClientHooks();
		}
	}
}
