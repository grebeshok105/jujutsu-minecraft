package jujutsu.mod.cursedincident;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The full logical state of one incident (issue #110 spec §15). Persisted inside
 * {@code IncidentSavedData}; lives independently of chunk loading — the world half is
 * applied lazily through {@link IncidentWorldSink}.
 *
 * <p>All time fields use {@code level.getGameTime()} — never {@code tickCount} (the
 * per-entity clock resets on rejoin; documented trap in MegumiHostilityPolicy).
 *
 * <p>Seal state authority: the object component ({@code CursedObjectState}) is the
 * authoritative store when the source is an object; this record mirrors it for queries.
 * Every seal op writes the component first, then mirrors here (plan review F9).
 */
public final class IncidentRecord {

	/** One executed stage transition, kept for inspect/debuggability (spec §15). */
	public record Transition(IncidentStage from, IncidentStage to, long gameTime) {
	}

	/** Work counters — the perf oracle (spec §21): asserted bounded per tick. */
	public static final class WorkCounters {
		public long blocksChanged;
		public long cursesSpawned;
		public long animalsCulled;
		public long chunkEditsDeferred;

		public WorkCounters copy() {
			WorkCounters c = new WorkCounters();
			c.blocksChanged = blocksChanged;
			c.cursesSpawned = cursesSpawned;
			c.animalsCulled = animalsCulled;
			c.chunkEditsDeferred = chunkEditsDeferred;
			return c;
		}
	}

	public UUID id;
	public long seed;
	public long createdGameTime;
	public long lastUpdateGameTime;
	/** Logical age added by dev ops (advance age) on top of wall game-time. */
	public long bonusAgeTicks;
	public ResourceKey<Level> dimension;
	public BlockPos center;
	public double radius;
	public IncidentStage stage = IncidentStage.INITIAL;
	/** Terminal flag set by cleanup(): the record persists as a SCAR — stage ladder untouched. */
	public boolean scarred;
	public SourceKind sourceKind = SourceKind.FREE;
	/** Instance id of the cursed object when sourceKind == OBJECT. */
	public UUID objectInstanceId;
	public String objectTypeId;
	public Integer objectGrade;
	/** Last known world position of the source object (dwell or drop point). */
	public BlockPos sourcePos;
	/** Last known container position holding the source object, if any. */
	public BlockPos sourceContainer;
	public String templateId = "";
	public IncidentParams params;
	public final List<SecondaryNode> secondaries = new ArrayList<>();
	public final List<Transition> transitions = new ArrayList<>();
	/** Previous centres whose physical damage stays forever (spec §8.3). */
	public final List<BlockPos> scars = new ArrayList<>();
	public boolean sealed;
	public int sealIntegrity;
	public int sealTier;
	/** Count of refused/failed seal attempts and seal breaks (inspect field seal.failures). */
	public int sealFailures;
	public KnowledgeLevel knowledge = KnowledgeLevel.UNKNOWN;
	/** Dwell accumulation for the mobile-source rule (spec §8.2). */
	public long dwellTicks;
	public BlockPos dwellAnchor;
	public final WorkCounters counters = new WorkCounters();

	/** Logical age in ticks: world time elapsed since creation plus dev-added age. */
	public long ageTicks(long nowGameTime) {
		return Math.max(0L, nowGameTime - createdGameTime) + bonusAgeTicks;
	}
}
