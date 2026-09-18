package jujutsu.mod.cursedincident;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The world-effect half of the incident engine (issue #110). The logical core
 * ({@link IncidentControl}) never touches blocks/entities directly — it calls this
 * sink, so the same control flow serves production, dev commands and the agent bridge.
 *
 * <p>Block 3 supplies the real implementation ({@code InfectionSink}); the default is
 * a no-op so the core compiles and unit-tests without a world.
 */
public interface IncidentWorldSink {

	IncidentWorldSink NOOP = new IncidentWorldSink() {
	};

	/** A stage transition happened — plan/apply the stage's world delta. */
	default void applyStageDelta(ServerLevel level, IncidentRecord rec, IncidentStage from, IncidentStage to) {
	}

	/** Per-tick world work for one active work centre, bounded by {@code tickBudget}. */
	default void tickZone(ServerLevel level, IncidentRecord rec, BlockPos center, UUID nodeId, int tickBudget) {
	}

	/** The source moved: stop dependent work at the old centre (scar stays). */
	default void onRelocated(ServerLevel level, IncidentRecord rec, BlockPos oldCenter) {
	}

	/** The source object was sealed — dependent escalation halts. */
	default void onSealed(ServerLevel level, IncidentRecord rec) {
	}

	/** The seal was removed — dependent escalation resumes. */
	default void onUnsealed(ServerLevel level, IncidentRecord rec) {
	}

	/** The seal broke (integrity zero or catastrophic failure) — escalation resumes. */
	default void onSealBroken(ServerLevel level, IncidentRecord rec) {
	}

	/** A physical seal crossed a degradation band; T4 supplies the cue implementation. */
	default void onSealDegraded(ServerLevel level, IncidentRecord record, java.util.UUID objectId, int bandIndex) {
	}

	/** Remove runtime entities and source bindings while retaining the incident scar. */
	default void onCleanup(ServerLevel level, IncidentRecord record) {
	}
}
