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
		directedAndMassUseExactCadence();
		temporaryEntryDoesNotBlockOrConsumeLaterEntries();
		finaleMovesToLastSuccessfulResolvableEntry();
		runtimeUsesDistinctDirectedAndMassChains();
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

	private static void runtimeUsesDistinctDirectedAndMassChains() {
		try {
			String runtime = Files.readString(Path.of("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java"));
			assert runtime.contains("startDirectedHairpin(ServerPlayer caster)") : "R needs a directed server entrypoint";
			assert runtime.contains("startMassHairpin(ServerPlayer caster)") : "B needs a mass server entrypoint";
			assert runtime.contains("HAIRPIN_DIRECTED_CHAIN_RADIUS") : "R selection must use its ten-block radius";
			assert runtime.contains("HAIRPIN_DIRECTED_DAMAGE_PER_NAIL") : "R damage must be independent from B";
			assert runtime.contains("HAIRPIN_BLOCK_EXPLOSION_POWER") && runtime.contains("shouldDamageEntity") : "R block explosion must destroy terrain without vanilla entity damage";
			assert !runtime.contains("consumeAnchorMarks(level, anchors, gameTime);") : "Marks cannot be consumed before individual successful steps";
			assert !runtime.contains("class PendingExplosion") : "Legacy chain state must be replaced by HairpinChainScheduler";
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

	private static void directedAndMassUseExactCadence() {
		HairpinChain directed = HairpinChain.start(HairpinChain.Mode.DIRECTED, List.of(A, B), 10L, 2);
		assert directed.poll(9L, id -> HairpinChain.Resolution.RESOLVED).kind() == HairpinChain.StepKind.WAIT;
		assert directed.poll(10L, id -> HairpinChain.Resolution.RESOLVED).nailId().equals(A);
		assert directed.poll(11L, id -> HairpinChain.Resolution.RESOLVED).kind() == HairpinChain.StepKind.WAIT;
		assert directed.poll(12L, id -> HairpinChain.Resolution.RESOLVED).nailId().equals(B);

		HairpinChain mass = HairpinChain.start(HairpinChain.Mode.MASS, List.of(A, B), 20L, 3);
		assert mass.poll(20L, id -> HairpinChain.Resolution.RESOLVED).nailId().equals(A);
		assert mass.poll(22L, id -> HairpinChain.Resolution.RESOLVED).kind() == HairpinChain.StepKind.WAIT;
		assert mass.poll(23L, id -> HairpinChain.Resolution.RESOLVED).nailId().equals(B);
	}

	private static void temporaryEntryDoesNotBlockOrConsumeLaterEntries() {
		HairpinChain chain = HairpinChain.start(HairpinChain.Mode.MASS, List.of(A, B, C), 0L, 3);
		Map<UUID, HairpinChain.Resolution> state = Map.of(
				A, HairpinChain.Resolution.RESOLVED,
				B, HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE,
				C, HairpinChain.Resolution.RESOLVED);
		HairpinChain.Step first = chain.poll(0L, state::get);
		HairpinChain.Step second = chain.poll(3L, state::get);
		assert first.nailId().equals(A) && !first.finale();
		assert second.nailId().equals(C) && second.finale();
		assert chain.poll(6L, state::get).kind() == HairpinChain.StepKind.COMPLETE;
		assert chain.skippedTemporary().equals(List.of(B)) : "Temporary nail must remain identifiable and unconsumed";
	}

	private static void finaleMovesToLastSuccessfulResolvableEntry() {
		HairpinChain chain = HairpinChain.start(HairpinChain.Mode.DIRECTED, List.of(A, B, C), 0L, 2);
		Map<UUID, HairpinChain.Resolution> state = Map.of(
				A, HairpinChain.Resolution.RESOLVED,
				B, HairpinChain.Resolution.CONFIRMED_REMOVED,
				C, HairpinChain.Resolution.TEMPORARILY_UNAVAILABLE);
		HairpinChain.Step step = chain.poll(0L, state::get);
		assert step.nailId().equals(A) && step.finale() : "Last successful resolvable entry owns finale";
	}
}
