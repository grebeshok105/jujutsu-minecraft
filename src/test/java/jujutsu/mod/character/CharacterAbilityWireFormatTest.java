package jujutsu.mod.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The one owner of the slot wire format.
 *
 * <p>This contract used to live inside {@code TodoFakeClapTest}, where nobody adding a slot would
 * think to look, and where its message still claimed {@code ATTACK_CONTEXT} was the last one long
 * after a sixth slot had been appended. It belongs on its own.
 *
 * <p>{@code CharacterAbility} ids travel in {@code CharacterAbilityPayload} and
 * {@code AbilityCooldownPayload}. They are never written to disk — the persisted value is the
 * character id — so the rule is not "these numbers are sacred forever" but the narrower one AGENTS.md
 * states: <b>append, never renumber</b>. A renumbering is invisible at compile time and shows up only
 * as a client and a server disagreeing about which button was pressed.
 */
class CharacterAbilityWireFormatTest {
	/**
	 * Every id that has shipped. Adding a slot must add a line here, on purpose — that is the point.
	 * Changing an existing line means an old client would cast the wrong ability.
	 */
	private static final Map<CharacterAbility, Integer> SHIPPED_IDS = new EnumMap<>(Map.of(
			CharacterAbility.PRIMARY, 0,
			CharacterAbility.PRIMARY_SNEAK, 1,
			CharacterAbility.SECONDARY, 2,
			CharacterAbility.SECONDARY_SNEAK, 3,
			CharacterAbility.ATTACK_CONTEXT, 4,
			CharacterAbility.USE_CONTEXT, 5));

	@Test
	void everySlotIsAccountedForAndKeepsTheIdItShippedWith() {
		// Derived from the enum, so a seventh slot fails here rather than shipping unpinned — which is
		// exactly what happened to the sixth.
		assertEquals(CharacterAbility.values().length, SHIPPED_IDS.size(),
				"a slot was added without recording the network id it ships with");
		for (CharacterAbility slot : CharacterAbility.values()) {
			Integer shipped = SHIPPED_IDS.get(slot);
			assertEquals(shipped, slot.networkId(),
					"renumbering " + slot + " changes what an existing client's key press means");
		}
	}

	@Test
	void idsAppendInDeclarationOrder() {
		// "Append, never renumber" as a structure rather than a list: inserting a slot in the middle
		// shifts every id after it, and this fails before anyone has to notice by eye.
		for (CharacterAbility slot : CharacterAbility.values()) {
			assertEquals(slot.ordinal(), slot.networkId(),
					slot + " must take the next free id; inserting a slot mid-enum renumbers the rest");
		}
	}

	@Test
	void everySlotRoundTripsThroughItsId() {
		for (CharacterAbility slot : CharacterAbility.values()) {
			assertEquals(slot, CharacterAbility.byNetworkId(slot.networkId()),
					"slot must survive the wire: " + slot);
		}
	}

	@Test
	void anUnknownIdResolvesToNothingRatherThanADefault() {
		// A newer client may send a slot this server does not have. Falling back to an arm would fire
		// the wrong ability; refusing is the only safe answer.
		assertNull(CharacterAbility.byNetworkId(CharacterAbility.values().length),
				"the first unused id must not resolve");
		assertNull(CharacterAbility.byNetworkId(99));
		assertNull(CharacterAbility.byNetworkId(-1), "a negative id must not resolve either");
	}

	@Test
	void idsAreUniqueAndNonNegative() {
		boolean[] seen = new boolean[CharacterAbility.values().length];
		for (CharacterAbility slot : CharacterAbility.values()) {
			int id = slot.networkId();
			assertTrue(id >= 0 && id < seen.length, slot + " has an id outside the contiguous range: " + id);
			assertTrue(!seen[id], "two slots share network id " + id);
			seen[id] = true;
		}
	}
}
