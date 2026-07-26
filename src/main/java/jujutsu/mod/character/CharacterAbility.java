package jujutsu.mod.character;

/** Shared slots for character-owned active techniques. */
public enum CharacterAbility {
	PRIMARY(0);

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
