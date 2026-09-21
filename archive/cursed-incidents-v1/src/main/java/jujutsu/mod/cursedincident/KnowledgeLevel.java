package jujutsu.mod.cursedincident;

import java.util.Locale;

/**
 * The data-only object-identification ladder. Behavioural learning is deliberately
 * outside issue #110; the level is persisted and consumed by presentation code.
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
		return other != null && ordinal() >= other.ordinal();
	}

	public static boolean isKnown(String name) {
		return byName(name) != null;
	}

	public static KnowledgeLevel byName(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static KnowledgeLevel byNameOrDefault(String name) {
		KnowledgeLevel level = byName(name);
		return level == null ? UNKNOWN : level;
	}

	public String wireName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
