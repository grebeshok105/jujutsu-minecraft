package jujutsu.mod.character;

/**
 * Shared slots for character-owned active techniques.
 *
 * <p>Network ids are part of the wire format and are never renumbered; new slots append.
 */
public enum CharacterAbility {
	PRIMARY(0),
	SECONDARY(1),
	TERTIARY(2);

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
