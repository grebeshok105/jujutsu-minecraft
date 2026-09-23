package jujutsu.mod.cursedincident;

import java.util.Locale;

/**
 * Incident stage ladder (issue #110): INITIAL -> GROWING -> INFESTED -> CRITICAL ->
 * CATASTROPHIC. The ordinal is the severity axis and stage transitions never move
 * backwards.
 */
public enum IncidentStage {
	INITIAL,
	GROWING,
	INFESTED,
	CRITICAL,
	CATASTROPHIC;

	/** Returns the next stage, or this value when already terminal. */
	public IncidentStage next() {
		int next = ordinal() + 1;
		return next < values().length ? values()[next] : this;
	}

	public boolean atLeast(IncidentStage other) {
		return other != null && ordinal() >= other.ordinal();
	}

	public boolean isTerminal() {
		return this == CATASTROPHIC;
	}

	public static boolean isKnown(String name) {
		return byName(name) != null;
	}

	/**
	 * Parses a case-insensitive wire name. Unknown names intentionally return null so
	 * persistence can choose its documented safe default (INITIAL).
	 */
	public static IncidentStage byName(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	public static IncidentStage byNameOrDefault(String name) {
		IncidentStage stage = byName(name);
		return stage == null ? INITIAL : stage;
	}

	public String wireName() {
		return name().toLowerCase(Locale.ROOT);
	}
}
