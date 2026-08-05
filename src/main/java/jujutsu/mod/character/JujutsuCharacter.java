package jujutsu.mod.character;

import java.util.Locale;

public enum JujutsuCharacter {
	NONE("none", "wide", 1.0f),
	NOBARA("nobara", "slim", 1.0f),
	TODO("todo", "wide", 1.15f),
	MEGUMI("megumi", "wide", 1.0f);

	private final String id;
	private final String modelId;
	private final float bodyScale;

	JujutsuCharacter(String id, String modelId, float bodyScale) {
		this.id = id;
		this.modelId = modelId;
		this.bodyScale = bodyScale;
	}

	public String id() {
		return id;
	}

	public String modelId() {
		return modelId;
	}

	/** Physical player body scale used by dimensions and third-person rendering. */
	public float bodyScale() {
		return bodyScale;
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
