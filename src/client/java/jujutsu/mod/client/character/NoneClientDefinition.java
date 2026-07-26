package jujutsu.mod.client.character;

import java.util.List;
import jujutsu.mod.character.JujutsuCharacter;

/**
 * No vessel, as an object rather than a null.
 *
 * <p>It is a real card in the menu, so it needs a name, a colour and an empty input strip like any
 * other. No renderer means the vanilla player model, which is exactly what "no vessel" should look like.
 */
final class NoneClientDefinition implements CharacterClientDefinition {
	private static final int ACCENT = 0xFF7A8796;

	@Override
	public JujutsuCharacter id() {
		return JujutsuCharacter.NONE;
	}

	@Override
	public CharacterRosterEntry rosterEntry() {
		return new CharacterRosterEntry(
				"screen.jujutsumod.character_select.default.name",
				"screen.jujutsumod.character_select.default.role",
				"screen.jujutsumod.character_select.default",
				JujutsuCharacterIcons.BUST, false,
				List.of());
	}

	/** Last in the menu, where it has always been: the vessels come first. */
	@Override
	public int rosterOrder() {
		return Integer.MAX_VALUE;
	}

	@Override
	public int accent() {
		return ACCENT;
	}

	@Override
	public String moduleName() {
		return "None";
	}

	@Override
	public String moduleDescription() {
		return "No cursed technique";
	}

	@Override
	public boolean moduleStartsEnabled() {
		return true;
	}
}
