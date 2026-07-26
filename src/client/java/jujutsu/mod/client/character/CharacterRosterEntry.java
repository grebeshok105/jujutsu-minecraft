package jujutsu.mod.client.character;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * One vessel's card in the selection menu.
 *
 * <p>The three text lines are named for what they are rather than reused for whatever each vessel felt
 * like putting there. The previous shape had fields called name/technique/grade into which Nobara passed
 * a full name, a role and a grade while Todo passed a name, a technique and a role — so the record's own
 * field names were wrong for one of them no matter which vessel you read it beside.
 *
 * @param nameKey the vessel's display name
 * @param roleKey what it does in a fight
 * @param subtitleKey the smaller third line: a grade, a technique, anything short
 * @param portrait skin texture or icon
 * @param portraitIsSkin true to draw it as a player skin head, false to draw it as a flat icon
 * @param abilities the input strip, in input order
 */
public record CharacterRosterEntry(
		String nameKey,
		String roleKey,
		String subtitleKey,
		ResourceLocation portrait,
		boolean portraitIsSkin,
		List<Ability> abilities
) {
	public CharacterRosterEntry {
		abilities = List.copyOf(abilities);
	}

	/**
	 * One entry in a card's input strip.
	 *
	 * @param icon the small emoji-style glyph
	 * @param nameKey what the ability is called
	 * @param inputLabel the keys that reach it, written the way a player types them
	 */
	public record Ability(ResourceLocation icon, String nameKey, String inputLabel) {}
}
