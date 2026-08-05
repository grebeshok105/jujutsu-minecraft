package jujutsu.mod.character;

/**
 * Shared active-technique slots, named after the <b>input position</b> that reaches them rather than
 * after what any one vessel does with them.
 *
 * <p>That naming is the whole point. It lets the input layer be a translator — a key plus whether the
 * player is sneaking maps to a slot, with no knowledge of who is selected — and leaves "what does my
 * {@code PRIMARY_SNEAK} do" to each vessel's own router. Adding a vessel then touches no shared input
 * code at all.
 *
 * <p>New slots append rather than renumber. These ids travel only in {@code CharacterAbilityPayload}
 * and {@code AbilityCooldownPayload} and are kept in memory, never written to disk — the persisted
 * value is the character id — which is why the slots could be renamed and renumbered at all.
 */
public enum CharacterAbility {
	/** The technique key. */
	PRIMARY(0),
	/** The technique key while sneaking. */
	PRIMARY_SNEAK(1),
	/** The second technique key. */
	SECONDARY(2),
	/** The second technique key while sneaking. */
	SECONDARY_SNEAK(3),
	/** Left click while holding a technique weapon. */
	ATTACK_CONTEXT(4),
	/**
	 * Two right clicks in quick succession.
	 *
	 * <p>The only slot whose input the game already owns: the first click of the pair is handled by
	 * vanilla before the mod ever sees it, and that is accepted rather than worked around. The input
	 * layer sends this slot only once a pair completes, so an ordinary right click costs no packet.
	 */
	USE_CONTEXT(5),
	/**
	 * The second technique key while sneaking, held past the tap window.
	 *
	 * <p>The input layer buffers a sneaking press of the second technique key and resolves the gesture
	 * on release (a tap, {@link #SECONDARY_SNEAK}) or at the hold threshold (this slot). A vessel with
	 * no held technique simply answers {@code false} here, exactly like any other unclaimed slot.
	 */
	SECONDARY_SNEAK_HOLD(6),
	/**
	 * The release that ends a held second-technique gesture.
	 *
	 * <p>Deliberately never carries a cooldown: an end-of-gesture request has to reach the vessel's
	 * router even while the slot that started the gesture is cooling down, or the player could not end
	 * what he is still inside. A release with no matching server state is refused by the router.
	 */
	SECONDARY_SNEAK_RELEASE(7);

	private final int networkId;

	CharacterAbility(int networkId) {
		this.networkId = networkId;
	}

	public int networkId() {
		return networkId;
	}

	public static CharacterAbility byNetworkId(int id) {
		for (CharacterAbility ability : values()) {
			if (ability.networkId == id) {
				return ability;
			}
		}
		return null;
	}
}
