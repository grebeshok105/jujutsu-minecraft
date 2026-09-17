package jujutsu.mod.cursedincident;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The single internal API of the incident subsystem (issue #110 spec §16.1). Gameplay,
 * {@code /jujutsu incident} commands and the MCP dev bridge all enter through here —
 * there is no second implementation of the mechanics.
 *
 * <p>All state lives in {@code IncidentSavedData} on the overworld, reached through the
 * bound store supplier — production binds {@code () -> IncidentSavedData.get(overworld)}
 * on SERVER_STARTED; plain JUnit binds {@code IncidentSavedData::new} (review F3). All
 * world effects go through the bound {@link IncidentWorldSink}. Server-side only.
 */
public final class IncidentControl {

	/** Everything needed to mint an incident; nulls mean "roll it". */
	public record SpawnRequest(BlockPos center, ResourceKey<Level> dimension, String templateId,
			Integer grade, Long seed, IncidentStage startStage, String objectTypeId, SourceKind sourceKind,
			Double radius) {
	}

	/** Machine-readable seal outcome (R38): refusal carries the required tier + reason. */
	public record SealAttempt(boolean ok, int requiredTier, String reason) {
	}

	/** The machine-readable snapshot returned by inspect/list (spec §17, scout-4 field list). */
	public record InspectView(UUID id, long seed, String templateId, IncidentStage stage, boolean scarred,
			long ageTicks, long createdGameTime, long lastUpdateGameTime, BlockPos center, double radius,
			String zoneShape, SourceKind sourceKind, UUID objectInstanceId, String objectTypeId,
			Integer objectGrade, BlockPos sourcePos, BlockPos sourceContainer,
			Map<String, Integer> curseSet, String atmosphereId, List<String> localGoals,
			boolean ignoreShelter, boolean sealed, int sealIntegrity, int sealTier, int sealFailures,
			String knowledge, List<SecondaryNode> secondaries, int dependentCenters, List<BlockPos> scars,
			Map<String, Long> workCounters, List<String> transitionLog) {
	}

	public static final class IncidentNotFoundException extends RuntimeException {
		public IncidentNotFoundException(UUID id) {
			super("unknown incident " + id);
		}
	}

	private IncidentControl() {
	}

	// ---- wiring (bound once from CursedIncidents.registerServerHooks via *Wiring) ----

	/** Binds the store. Production: overworld SavedData; tests: a fresh instance. */
	public static void bindStore(Supplier<?> store) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void bindWorldSink(IncidentWorldSink sink) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void bindDwellProvider(DwellProvider provider) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void bindObjectSpawner(ObjectSpawner spawner) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	// ---- lifecycle ----

	public static IncidentRecord spawn(SpawnRequest req) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static UUID spawnObject(ServerLevel level, BlockPos pos, String typeId, int grade, long seed) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static InspectView inspect(UUID incidentId) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static List<InspectView> list() {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static IncidentStage setStage(UUID id, IncidentStage stage) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	/** Adds logical age then runs the SAME transition path as offline catch-up (spec §19). */
	public static long advance(UUID id, long ticks) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	/** The one transition engine: live tick, dev advance and offline catch-up all call this. */
	public static void advanceTo(IncidentRecord rec, long targetAgeTicks) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void escalate(UUID id, double multiplier) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static SealAttempt seal(UUID id, int sealTier) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static boolean unseal(UUID id) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static int damageSeal(UUID id, int amount) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void relocate(UUID id, BlockPos newCenter) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static SecondaryNode forceSecondary(UUID id, BlockPos pos) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void identify(UUID id, KnowledgeLevel level) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void cleanup(UUID id) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static long reseed(UUID id, long newSeed) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void catchUp(ServerLevel overworld) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	// ---- cursed pressure (spec §4.2; storage owned by B1's IncidentSavedData) ----

	public static long cursedPressure() {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static long addPressure(long delta) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void resetPressure() {
		throw new UnsupportedOperationException("block-1 pending");
	}

	public static void clearRuntimeState() {
		throw new UnsupportedOperationException("block-1 pending");
	}
}
