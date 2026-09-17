package jujutsu.mod.cursedincident;

import java.util.Locale;

/**
 * Incident stage ladder (issue #110): INITIAL -> GROWING -> INFESTED -> CRITICAL ->
 * CATASTROPHIC. Forward-only; the ordinal IS the severity axis.
 */
public enum IncidentStage {
	INITIAL,
	GROWING,
	INFESTED,
	CRITICAL,
	CATASTROPHIC;

	public IncidentStage next() {
		int next = ordinal() + 1;
		return next < values().length ? values()[next] : this;
	}

	public boolean atLeast(IncidentStage other) {
		return ordinal() >= other.ordinal();
	}

	public static boolean isKnown(String name) {
		return byName(name) != null;
	}

	public static IncidentStage byName(String name) {
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
