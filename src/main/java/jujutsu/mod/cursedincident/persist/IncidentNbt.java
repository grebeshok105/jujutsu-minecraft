package jujutsu.mod.cursedincident.persist;

import java.util.UUID;

import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.KnowledgeLevel;
import jujutsu.mod.cursedincident.SourceKind;

/** Frozen schema keys and small validation helpers for incident persistence. */
public final class IncidentNbt {
	public static final String ID = "id";
	public static final String SEED = "seed";
	public static final String CREATED = "created";
	public static final String LAST_UPDATE = "last_update";
	public static final String BONUS_AGE = "bonus_age";
	public static final String DIM = "dim";
	public static final String CENTER = "center";
	public static final String RADIUS = "radius";
	public static final String STAGE = "stage";
	public static final String SCARRED = "scarred";
	public static final String SOURCE_KIND = "source_kind";
	public static final String OBJECT_ID = "object_id";
	public static final String OBJECT_TYPE = "object_type";
	public static final String OBJECT_GRADE = "object_grade";
	public static final String SOURCE_POS = "source_pos";
	public static final String SOURCE_CONTAINER = "source_container";
	public static final String TEMPLATE = "template";
	public static final String PARAMS = "params";
	public static final String SECONDARIES = "secondaries";
	public static final String TRANSITIONS = "transitions";
	public static final String SCARS = "scars";
	public static final String SEAL = "seal";
	public static final String KNOWLEDGE = "knowledge";
	public static final String DWELL = "dwell";
	public static final String COUNTERS = "counters";
	public static final String INCIDENTS = "incidents";
	public static final String PRESSURE = "pressure";

	// Nested parameter keys.
	public static final String ZONE_SHAPE = "zone_shape";
	public static final String BASE_RADIUS = "base_radius";
	public static final String CURSE_WEIGHTS = "curse_weights";
	public static final String ATMOSPHERE = "atmosphere";
	public static final String LOCAL_GOALS = "local_goals";
	public static final String ESCALATION_SPEED = "escalation_speed";
	public static final String IGNORE_SHELTER = "ignore_shelter";
	public static final String SECONDARY_AT_CRITICAL = "secondary_at_critical";
	public static final String DWELL_TICKS_REQUIRED = "dwell_ticks_required";

	// Nested seal/dwell/counter keys.
	public static final String SEALED = "sealed";
	public static final String INTEGRITY = "integrity";
	public static final String TIER = "tier";
	public static final String FAILURES = "failures";
	public static final String TICKS = "ticks";
	public static final String ANCHOR = "anchor";
	public static final String BLOCKS_CHANGED = "blocks_changed";
	public static final String CURSES_SPAWNED = "curses_spawned";
	public static final String ANIMALS_CULLED = "animals_culled";
	public static final String CHUNK_EDITS_DEFERRED = "chunk_edits_deferred";
	public static final String FROM = "from";
	public static final String TO = "to";
	public static final String GAME_TIME = "game_time";

	// Compatibility aliases matching the schema names in the plan prose.
	public static final String Id = ID;
	public static final String Seed = SEED;
	public static final String Created = CREATED;
	public static final String LastUpdate = LAST_UPDATE;
	public static final String BonusAge = BONUS_AGE;
	public static final String Dim = DIM;
	public static final String Center = CENTER;
	public static final String Radius = RADIUS;
	public static final String Stage = STAGE;
	public static final String Scarred = SCARRED;
	public static final String SourceKind = SOURCE_KIND;
	public static final String ObjectId = OBJECT_ID;
	public static final String ObjectType = OBJECT_TYPE;
	public static final String SourcePos = SOURCE_POS;
	public static final String SourceContainer = SOURCE_CONTAINER;
	public static final String Template = TEMPLATE;
	public static final String Params = PARAMS;
	public static final String Secondaries = SECONDARIES;
	public static final String Transitions = TRANSITIONS;
	public static final String Scars = SCARS;
	public static final String Seal = SEAL;
	public static final String Knowledge = KNOWLEDGE;
	public static final String Dwell = DWELL;
	public static final String Counters = COUNTERS;

	private IncidentNbt() {
	}

	public static boolean validId(UUID id) {
		return id != null;
	}

	public static boolean validRadius(double radius) {
		return Double.isFinite(radius) && radius >= 0.0;
	}

	public static boolean validStage(String value) {
		return IncidentStage.isKnown(value);
	}

	public static boolean validSourceKind(String value) {
		return jujutsu.mod.cursedincident.SourceKind.isKnown(value);
	}

	public static boolean validKnowledge(String value) {
		return KnowledgeLevel.isKnown(value);
	}
	public static IncidentStage safeStage(String value) {
		return IncidentStage.byNameOrDefault(value);
	}

	public static SourceKind safeSourceKind(String value) {
		return jujutsu.mod.cursedincident.SourceKind.byNameOrDefault(value);
	}

	public static KnowledgeLevel safeKnowledge(String value) {
		return KnowledgeLevel.byNameOrDefault(value);
	}
}
