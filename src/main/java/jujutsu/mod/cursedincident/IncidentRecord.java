package jujutsu.mod.cursedincident;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jujutsu.mod.cursedincident.policy.SpawnRollPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Full durable logical state for one incident. */
public final class IncidentRecord {
	/** One executed stage transition, retained for inspect/debuggability. */
	public record Transition(IncidentStage from, IncidentStage to, long gameTime) {
		public Transition {
			from = from == null ? IncidentStage.INITIAL : from;
			to = to == null ? from : to;
			gameTime = Math.max(0L, gameTime);
		}
	}

	/** Work counters used by the world budget oracle. */
	public static final class WorkCounters {
		public long blocksChanged;
		public long cursesSpawned;
		public long animalsCulled;
		public long chunkEditsDeferred;

		public WorkCounters() {
		}

		public WorkCounters(long blocksChanged, long cursesSpawned, long animalsCulled, long chunkEditsDeferred) {
			this.blocksChanged = Math.max(0L, blocksChanged);
			this.cursesSpawned = Math.max(0L, cursesSpawned);
			this.animalsCulled = Math.max(0L, animalsCulled);
			this.chunkEditsDeferred = Math.max(0L, chunkEditsDeferred);
		}

		public WorkCounters copy() {
			return new WorkCounters(blocksChanged, cursesSpawned, animalsCulled, chunkEditsDeferred);
		}
	}

	public UUID id() {
		return id;
	}
	public UUID id = UUID.randomUUID();
	public long seed;
	public long createdGameTime;
	public long lastUpdateGameTime;
	/** Logical age added by dev operations on top of world game time. */
	public long bonusAgeTicks;
	public ResourceKey<Level> dimension = Level.OVERWORLD;
	public BlockPos center = BlockPos.ZERO;
	public double radius;
	public IncidentStage stage = IncidentStage.INITIAL;
	/** Terminal cleanup flag; this is not an additional stage. */
	public boolean scarred;
	public SourceKind sourceKind = SourceKind.FREE;
	public UUID objectInstanceId;
	public String objectTypeId;
	public Integer objectGrade;
	public BlockPos sourcePos;
	public BlockPos sourceContainer;
	public String templateId = "blight";
	public IncidentParams params = new IncidentParams(
			"sphere", 0.0, java.util.Map.of(), "", java.util.List.of(), 1.0, false, false,
			SpawnRollPolicy.DEFAULT_DWELL_TICKS);
	public final List<SecondaryNode> secondaries = new ArrayList<>();
	public final List<Transition> transitions = new ArrayList<>();
	/** Previous centres whose physical damage remains permanently. */
	public final List<BlockPos> scars = new ArrayList<>();
	public boolean sealed;
	public int sealIntegrity;
	public int sealTier;
	public int sealFailures;
	public KnowledgeLevel knowledge = KnowledgeLevel.UNKNOWN;
	public long dwellTicks;
	public BlockPos dwellAnchor;
	public final WorkCounters counters = new WorkCounters();

	/** Logical age in ticks: world time elapsed since creation plus dev-added age. */
	public long ageTicks(long nowGameTime) {
		long elapsed;
		try {
			elapsed = Math.subtractExact(nowGameTime, createdGameTime);
		} catch (ArithmeticException overflow) {
			elapsed = nowGameTime >= createdGameTime ? Long.MAX_VALUE : Long.MIN_VALUE;
		}
		if (elapsed < 0L) {
			elapsed = 0L;
		}
		if (Long.MAX_VALUE - elapsed < Math.max(0L, bonusAgeTicks)) {
			return Long.MAX_VALUE;
		}
		return elapsed + Math.max(0L, bonusAgeTicks);
	}

	public int dependentCenterCount() {
		int count = 0;
		for (SecondaryNode node : secondaries) {
			if (!node.selfSustaining()) {
				count++;
			}
		}
		return count;
	}
}
