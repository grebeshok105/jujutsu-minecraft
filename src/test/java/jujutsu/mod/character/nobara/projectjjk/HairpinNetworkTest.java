package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/** Assertion-main coverage for the pure Hairpin network and seed ordering contract. */
public final class HairpinNetworkTest {
	private static final NailAnchorRegistry.NailOrigin ORIGIN = NailAnchorRegistry.NailOrigin.LAUNCHED;
	private static final UUID SEED_ID = new UUID(0L, 1L);
	private static final UUID LOW_ID = new UUID(0L, 2L);
	private static final UUID HIGH_ID = new UUID(0L, 3L);
	private static final UUID BRANCH_ID = new UUID(0L, 4L);
	private static final UUID ISLAND_ID = new UUID(0L, 5L);
	private static final UUID TARGET_A = new UUID(0L, 10L);
	private static final UUID TARGET_B = new UUID(0L, 11L);

	private HairpinNetworkTest() {}

	public static void main(String[] args) {
		determinismAcrossFiftyShuffles();
		seedAlwaysComesFirst();
		uuidTieBreakWins();
		radiusExcludesUnreachableCandidates();
		branchingExpansionUsesAnyFrontierNode();
		multiNailTargetRemainsInSnapshot();
	}

	private static void determinismAcrossFiftyShuffles() {
		List<HairpinNetwork.Node> source = fixture();
		List<UUID> expected = ids(HairpinNetwork.build(source, source.get(0), 10.0));
		Random random = new Random(0x5EEDL);
		for (int iteration = 0; iteration < 50; iteration++) {
			List<HairpinNetwork.Node> shuffled = new ArrayList<>(source);
			Collections.shuffle(shuffled, random);
			assert ids(HairpinNetwork.build(shuffled, source.get(0), 10.0)).equals(expected)
					: "network order changed at iteration " + iteration;
		}
	}

	private static void seedAlwaysComesFirst() {
		List<HairpinNetwork.Node> source = fixture();
		List<HairpinNetwork.Node> chain = HairpinNetwork.build(source, source.get(2), 10.0);
		assert chain.getFirst().nailId().equals(source.get(2).nailId());
	}

	private static void uuidTieBreakWins() {
		HairpinNetwork.Node seed = node(SEED_ID, 0.0, 0.0, 0.0, null);
		HairpinNetwork.Node low = node(LOW_ID, 2.0, 0.0, 0.0, null);
		HairpinNetwork.Node high = node(HIGH_ID, -2.0, 0.0, 0.0, null);
		List<HairpinNetwork.Node> chain = HairpinNetwork.build(List.of(high, low), seed, 2.0);
		assert chain.get(1).nailId().equals(LOW_ID) : chain;
	}

	private static void radiusExcludesUnreachableCandidates() {
		List<HairpinNetwork.Node> chain = HairpinNetwork.build(fixture(), fixture().get(0), 10.0);
		assert !ids(chain).contains(ISLAND_ID) : "unreachable island was included";
	}

	private static void branchingExpansionUsesAnyFrontierNode() {
		HairpinNetwork.Node seed = node(SEED_ID, 0.0, 0.0, 0.0, null);
		HairpinNetwork.Node first = node(LOW_ID, 9.0, 0.0, 0.0, null);
		HairpinNetwork.Node second = node(HIGH_ID, 18.0, 0.0, 0.0, null);
		HairpinNetwork.Node branch = node(BRANCH_ID, 1.0, 0.0, 0.0, null);
		List<HairpinNetwork.Node> chain = HairpinNetwork.build(List.of(second, branch, first), seed, 10.0);
		assert ids(chain).equals(List.of(SEED_ID, BRANCH_ID, LOW_ID, HIGH_ID)) : chain;
	}

	private static void multiNailTargetRemainsInSnapshot() {
		List<HairpinNetwork.Node> source = List.of(
				node(SEED_ID, 0.0, 0.0, 0.0, TARGET_A),
				node(LOW_ID, 2.0, 0.0, 0.0, TARGET_A),
				node(HIGH_ID, 4.0, 0.0, 0.0, TARGET_B));
		List<HairpinNetwork.Node> chain = HairpinNetwork.build(source, source.getFirst(), 10.0);
		assert chain.stream().filter(node -> TARGET_A.equals(node.targetId())).count() == 2;
	}

	private static List<HairpinNetwork.Node> fixture() {
		return List.of(
				node(SEED_ID, 0.0, 0.0, 0.0, TARGET_A),
				node(LOW_ID, 3.0, 0.0, 0.0, TARGET_A),
				node(HIGH_ID, 6.0, 0.0, 0.0, TARGET_B),
				node(BRANCH_ID, 3.0, 3.0, 0.0, TARGET_B),
				node(ISLAND_ID, 100.0, 0.0, 0.0, null));
	}

	private static HairpinNetwork.Node node(UUID id, double x, double y, double z, UUID target) {
		return new HairpinNetwork.Node(id, new Vec3(x, y, z), target, 1, ORIGIN);
	}

	private static List<UUID> ids(List<HairpinNetwork.Node> nodes) {
		return nodes.stream().map(HairpinNetwork.Node::nailId).toList();
	}
}
