package jujutsu.mod.character.nobara.projectjjk;

import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Assertion-main coverage for the level-free slice of {@link NailAnchorRegistry}: the entry record,
 * origin enum, null-guards and the global clear. Live-level behaviour (tracking, eviction, deeply
 * anchored, per-level isolation) is exercised by {@code NobaraAnchorGameTests}.
 */
public final class NailAnchorRegistryTest {
	private NailAnchorRegistryTest() {}

	public static void main(String[] args) {
		entryRecordCarriesEveryField();
		originEnumCoversAllThreeSources();
		nullGuardsReturnEmptyAndZero();
		clearAllIsIdempotent();
		System.out.println("NailAnchorRegistryTest passed");
	}

	private static void entryRecordCarriesEveryField() {
		UUID nailId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		UUID targetId = UUID.randomUUID();
		NailAnchor anchor = NailAnchor.entity(targetId, 7, new Vec3(0.0, 1.0, 0.0), new Vec3(0.0, 0.0, 1.0));
		NailAnchorRegistry.Entry entry = new NailAnchorRegistry.Entry(
				nailId, ownerId, anchor, 3, NailAnchorRegistry.NailOrigin.TRAP_IMPACT, targetId);
		assert entry.nailId().equals(nailId);
		assert entry.ownerId().equals(ownerId);
		assert entry.anchor() == anchor;
		assert entry.depth() == 3;
		assert entry.origin() == NailAnchorRegistry.NailOrigin.TRAP_IMPACT;
		assert entry.targetId().equals(targetId);
	}

	private static void originEnumCoversAllThreeSources() {
		List<NailAnchorRegistry.NailOrigin> origins = List.of(NailAnchorRegistry.NailOrigin.values());
		assert origins.size() == 3 : "a new nail origin must be indexed, not silently added";
		assert origins.contains(NailAnchorRegistry.NailOrigin.LAUNCHED);
		assert origins.contains(NailAnchorRegistry.NailOrigin.TRAP_CORNER);
		assert origins.contains(NailAnchorRegistry.NailOrigin.TRAP_IMPACT);
	}

	private static void nullGuardsReturnEmptyAndZero() {
		assert NailAnchorRegistry.ownedAnchors(null, UUID.randomUUID()).isEmpty();
		assert NailAnchorRegistry.anchorsOnTarget(null, UUID.randomUUID(), UUID.randomUUID()).isEmpty();
		assert NailAnchorRegistry.anchorsOnTarget(null, UUID.randomUUID(), null).isEmpty();
		assert !NailAnchorRegistry.isDeeplyAnchored(null, UUID.randomUUID(), UUID.randomUUID());
		assert NailAnchorRegistry.discardOwned((net.minecraft.server.level.ServerLevel) null, UUID.randomUUID()) == 0;
		assert NailAnchorRegistry.discardOwned((net.minecraft.server.MinecraftServer) null) == 0;
		// untrack/updateDepth on nulls must not throw.
		NailAnchorRegistry.untrack(null, UUID.randomUUID());
		NailAnchorRegistry.updateDepth(null, UUID.randomUUID(), 3);
	}

	private static void clearAllIsIdempotent() {
		NailAnchorRegistry.clearAll();
		NailAnchorRegistry.clearAll();
	}
}
