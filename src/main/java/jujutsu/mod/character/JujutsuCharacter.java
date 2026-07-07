package jujutsu.mod.character;

import java.util.Locale;

public enum JujutsuCharacter {
	NONE("none", "wide"),
	NOBARA("nobara", "wide");

	private final String id;
	private final String modelId;

	JujutsuCharacter(String id, String modelId) {
		this.id = id;
		this.modelId = modelId;
	}

	public String id() {
		return id;
	}

	public String modelId() {
		return modelId;
	}

	public static JujutsuCharacter byId(String id) {
		String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
		for (JujutsuCharacter character : values()) {
			if (character.id.equals(normalized)) {
				return character;
			}
		}
		return NONE;
	}
}
