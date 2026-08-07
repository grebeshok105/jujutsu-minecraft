package jujutsu.mod.client.character;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.character.CharacterAbility;

/**
 * One cell of the in-world ability HUD.
 *
 * <p>The roster card and the HUD show the same abilities; this record is the same information bound
 * to a {@link CharacterAbility} so the HUD can look up a cooldown per slot. The card stays the
 * source of the glyph and the name — each vessel builds its HUD list out of its own card's strip —
 * while the ability slot and the key label are what the HUD itself needs.
 *
 * @param icon the small emoji-style glyph, shared with the roster card
 * @param nameKey the translated ability name, shared with the roster card
 * @param ability the technique slot this cell answers for
 * @param keyLabel the keys that reach it, written the way a player types them
 */
public record HudSlot(ResourceLocation icon, String nameKey, CharacterAbility ability, String keyLabel) {}
