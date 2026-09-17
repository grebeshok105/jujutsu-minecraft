package jujutsu.mod.cursedincident;

import java.util.Locale;

/**
 * What anchors an incident: a physical object (~80% of natural rolls) or free
 * activity (~20%).
 */
public enum SourceKind {
	OBJECT,
	FREE;

	public static boolean isKnown(String name) {
		return byName(name) != null;
	}

	public static SourceKind byName(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static SourceKind byNameOrDefault(String name) {
		SourceKind kind = byName(name);
		return kind == null ? FREE : kind;
	}

	public String wireName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
