package jujutsu.mod.character.nobara.projectjjk;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.phys.Vec3;

public final class HairpinChainTest {
	private static final UUID A = new UUID(0L, 1L);
	private static final UUID B = new UUID(0L, 2L);
	private static final UUID C = new UUID(0L, 3L);

	private HairpinChainTest() {}

	public static void main(String[] args) {
		nearestNeighborUsesSnapshotAndUuidTieBreak();
		directedUsesExactCadence();
		temporaryEntryDoesNotBlockOrConsumeLaterEntries();
		finaleMovesToLastSuccessfulResolvableEntry();
		temporaryEntryRotatesAndRetriesAfterOtherEntries();
		directedOrderKeepsSelectedSeedFirst();
		depthAndFinaleHaveDedicatedPresentation();
	}

	private static void depthAndFinaleHaveDedicatedPresentation() {
		try {
			String hammer = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java"));
			String recipes = Files.readString(Path.of("src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java"));
			assert hammer.contains("NobaraVfxIds.NAIL_DEEPEN") : "Depth II/III transitions need a dedicated cue";
			assert !hammer.contains("findEmbeddedNail(") && !hammer.contains("UUID nailId") : "Removed nail-head state must not remain";
			assert recipes.contains("nailDeepen(VfxCue cue)") : "Depth transitions need a recipe";
			assert recipes.contains("isHairpinFinale") : "Finale presentation must be decoded explicitly";
			assert recipes.contains("hairpinExplosionDepth") : "Level III must have a heavy branch";
			assert recipes.contains("PROJECTJJK_LONG_WHOOSH") : "Finale needs a distinct sound tail";
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}

	private static void nearestNeighborUsesSnapshotAndUuidTieBreak() {
		List<HairpinChainOrder.Candidate> candidates = List.of(
				new HairpinChainOrder.Candidate(C, new Vec3(4, 0, 0)),
				new HairpinChainOrder.Candidate(B, new Vec3(2, 0, 0)),
				new HairpinChainOrder.Candidate(A, new Vec3(-2, 0, 0)));
		List<UUID> order = HairpinChainOrder.nearestNeighbor(Vec3.ZERO, candidates).stream().map(HairpinChainOrder.Candidate::nailId).toList();
		assert order.equals(List.of(A, B, C)) : order;
		assert candidates.getFirst().position().equals(new Vec3(4, 0, 0)) : "Ordering must not mutate candidate snapshots";
	}

	private static void directedUsesExactCadence() {
		HairpinChain chain = HairpinChain.start(List.of(A, B), 10L, 2);
		assert chain.poll(9L, id -> HairpinChain.Resolution.RESOLVED).kind() == HairpinChain.StepKind.WAIT;
		assert chain.poll(10L, id -> HairpinChain.Resolution.RESOLVED).nailId().equals(A);
		assert chain.poll(11L, id -> HairpinChain.Resolution.RESOLVED).kind() == HairpinChain.StepKind.WAIT;
		assert chain.poll(12L, id -> HairpinChain.Resolution.RESOLVED).nailId().equals(B);
	}

	private static void temporaryEntryDoesNotBlockOrConsumeLaterEntries() {
		HairpinChain chain = HairpinChain.start(List.of(A, B, C), 0L, 3);
		Map<UUID, HairpinChain.Resolution> state = Map.of(
				A, HairpinChain.Resolution.RESOLVED,
				B, HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE,
				C, HairpinChain.Resolution.RESOLVED);
		HairpinChain.Step first = chain.poll(0L, state::get);
		HairpinChain.Step second = chain.poll(3L, state::get);
		assert first.nailId().equals(A) && !first.finale();
		assert second.nailId().equals(C) && !second.finale();
		assert chain.poll(6L, state::get).kind() == HairpinChain.StepKind.WAIT;
		assert chain.skippedTemporary().equals(List.of(B)) : "Temporary nail must remain identifiable and unconsumed";
	}

	private static void finaleMovesToLastSuccessfulResolvableEntry() {
		HairpinChain chain = HairpinChain.start(List.of(A, B, C), 0L, 2);
		Map<UUID, HairpinChain.Resolution> state = Map.of(
				A, HairpinChain.Resolution.RESOLVED,
				B, HairpinChain.Resolution.CONFIRMED_REMOVED,
				C, HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE);
		HairpinChain.Step step = chain.poll(0L, state::get);
		assert step.nailId().equals(A) && !step.finale();
		assert chain.poll(2L, state::get).kind() == HairpinChain.StepKind.WAIT : "temporary anchors keep the chain alive";
	}

	private static void temporaryEntryRotatesAndRetriesAfterOtherEntries() {
		HairpinChain chain = HairpinChain.start(List.of(A, B), 0L, 3);
		Map<UUID, HairpinChain.Resolution> unavailable = Map.of(A, HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE, B, HairpinChain.Resolution.RESOLVED);
		assert chain.poll(0L, unavailable::get).nailId().equals(B);
		Map<UUID, HairpinChain.Resolution> restored = Map.of(A, HairpinChain.Resolution.RESOLVED, B, HairpinChain.Resolution.CONFIRMED_REMOVED);
		assert chain.poll(3L, restored::get).nailId().equals(A) : "rotated temporary nail must retry";
		HairpinChain.Step complete = chain.poll(6L, restored::get);
		assert complete.kind() == HairpinChain.StepKind.COMPLETE && complete.nailId().equals(A) : "completion identifies the last actual success for finale presentation";
	}

	private static void directedOrderKeepsSelectedSeedFirst() {
		List<HairpinChainOrder.Candidate> ordered = HairpinChainOrder.directed(A, new Vec3(9, 0, 0), List.of(
				new HairpinChainOrder.Candidate(B, Vec3.ZERO), new HairpinChainOrder.Candidate(A, new Vec3(9, 0, 0)),
				new HairpinChainOrder.Candidate(C, new Vec3(8, 0, 0))));
		assert ordered.stream().map(HairpinChainOrder.Candidate::nailId).toList().equals(List.of(A, C, B));
	}
}
