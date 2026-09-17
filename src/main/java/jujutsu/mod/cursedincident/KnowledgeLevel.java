package jujutsu.mod.cursedincident;

import java.util.Locale;

/**
 * Per-player-facing identification ladder for a cursed object (issue #110 spec §13).
 * Ships as data only: the learning/research subsystem is a future consumer; #110 stores
 * the level and gates display text on it, nothing else.
 */
public enum KnowledgeLevel {
	UNKNOWN,
	ROUGH_DANGER,
	EFFECT_TYPE,
	PROPERTIES,
	ORIGIN,
	NAME,
	SEALING_METHODS,
	RESTRICTIONS;

	public boolean atLeast(KnowledgeLevel other) {
		return ordinal() >= other.ordinal();
	}

	public static KnowledgeLevel byName(String name) {
		if (name == null) {
			return null;
		}
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public String wireName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
