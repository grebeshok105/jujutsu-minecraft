package jujutsu.mod.cursedincident;

import java.util.Locale;

/**
 * What anchors an incident (issue #110): OBJECT = a physical cursed object (~80% of
 * natural rolls), FREE = source-less activity (~20%).
 */
public enum SourceKind {
	OBJECT,
	FREE;

	public static SourceKind byName(String name) {
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
