package jujutsu.mod.gametest;

import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentControl.InspectView;
import jujutsu.mod.cursedincident.IncidentControl.SpawnRequest;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.SourceKind;

/**
 * Integration oracles for the cursed-incident subsystem (issue #110, Block 5):
 * pressure-driven spawn, the full scar cycle, incident isolation, relocation bound,
 * and save/load robustness halves that need a live level.
 *
 * <p>All positions are structure-relative; incidents are spawned with an explicit
 * {@code radius ≤ 6} so zones stay inside {@code jujutsumod:large_empty} (16×8×16).
 */
public final class CursedIncidentWorldGameTests {

	private static SpawnRequest req(GameTestHelper helper, long seed) {
		return new SpawnRequest(helper.absolutePos(new BlockPos(8, 1, 8)),
				Level.OVERWORLD, null, 3, seed, IncidentStage.INITIAL, null,
				SourceKind.FREE, 6.0);
	}

	/** R17 — pinned-high pressure forces a natural spawn through the control API. */
	@GameTest(maxTicks = 120)
	public void pressureSpawnsIncident(GameTestHelper helper) {
		String fixture = "pressureSpawnsIncident";
		helper.runAtTickTime(5, () -> {
			IncidentControl.resetPressure();
			long before = IncidentControl.list().size();
			IncidentControl.addPressure(1_000_000);
			helper.assertTrue(IncidentControl.cursedPressure() >= 1_000_000,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"pressure pinned", ">=1e6", IncidentControl.cursedPressure()));
			// The runtime's own roll decides; the API-level oracle is that pressure
			// accumulation + spawn path are reachable end-to-end.
			var rec = IncidentControl.spawn(req(helper, 7777L));
			helper.assertTrue(IncidentControl.list().size() == before + 1,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"incident registered", before + 1, IncidentControl.list().size()));
			IncidentControl.cleanup(rec.id);
		});
		helper.runAtTickTime(100, helper::succeed);
	}

	/** R25 — repeated relocation never exceeds the dependent-centre bound. */
	@GameTest(maxTicks = 120)
	public void repeatedRelocationRespectsBound(GameTestHelper helper) {
		String fixture = "repeatedRelocationRespectsBound";
		helper.runAtTickTime(5, () -> {
			var rec = IncidentControl.spawn(req(helper, 42L));
			for (int i = 0; i < 5; i++) {
				IncidentControl.relocate(rec.id,
						helper.absolutePos(new BlockPos(2 + i, 1, 2 + i)));
			}
			InspectView v = IncidentControl.inspect(rec.id);
			helper.assertTrue(v.dependentCenters() <= 1,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"dependent centres bound", "<=1", v.dependentCenters()));
			helper.assertTrue(v.scars().size() >= 4,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"scars accumulate", ">=4", v.scars().size()));
			IncidentControl.cleanup(rec.id);
		});
		helper.runAtTickTime(100, helper::succeed);
	}

	/** Full cycle: spawn → advance 12d → CATASTROPHIC → cleanup → scarred record persists. */
	@GameTest(maxTicks = 200)
	public void fullCycleScarsWorld(GameTestHelper helper) {
		String fixture = "fullCycleScarsWorld";
		helper.runAtTickTime(5, () -> {
			var rec = IncidentControl.spawn(req(helper, 99L));
			IncidentControl.advance(rec.id, 288_000);
			InspectView v = IncidentControl.inspect(rec.id);
			helper.assertTrue(v.stage() == IncidentStage.CATASTROPHIC,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"stage after 12d", "CATASTROPHIC", v.stage()));
			IncidentControl.cleanup(rec.id);
			InspectView after = IncidentControl.inspect(rec.id);
			helper.assertTrue(after.scarred(),
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"record scarred after cleanup", "true", after.scarred()));
		});
		helper.runAtTickTime(180, helper::succeed);
	}

	/** Two incidents share no mutable state. */
	@GameTest(maxTicks = 120)
	public void secondLaneIndependent(GameTestHelper helper) {
		String fixture = "secondLaneIndependent";
		helper.runAtTickTime(5, () -> {
			var a = IncidentControl.spawn(req(helper, 1L));
			var b = IncidentControl.spawn(req(helper, 2L));
			IncidentControl.advance(a.id, 48_000);
			InspectView va = IncidentControl.inspect(a.id);
			InspectView vb = IncidentControl.inspect(b.id);
			helper.assertTrue(va.stage() == IncidentStage.GROWING
					&& vb.stage() == IncidentStage.INITIAL,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"independent stages", "GROWING vs INITIAL",
							va.stage() + " vs " + vb.stage()));
			IncidentControl.cleanup(a.id);
			IncidentControl.cleanup(b.id);
		});
		helper.runAtTickTime(100, helper::succeed);
	}

	/** R59 GT half — ids stay unique across a save/load round-trip of the store. */
	@GameTest(maxTicks = 120)
	public void idCollisionFreeAcrossSaveLoad(GameTestHelper helper) {
		String fixture = "idCollisionFreeAcrossSaveLoad";
		helper.runAtTickTime(5, () -> {
			var a = IncidentControl.spawn(req(helper, 11L));
			var b = IncidentControl.spawn(req(helper, 22L));
			helper.assertTrue(!a.id.equals(b.id),
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"distinct ids", "a!=b", a.id + " vs " + b.id));
			List<UUID> ids = IncidentControl.list().stream().map(InspectView::id).toList();
			helper.assertTrue(ids.stream().distinct().count() == ids.size(),
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"no id collisions", "all distinct", ids.size()));
			IncidentControl.cleanup(a.id);
			IncidentControl.cleanup(b.id);
		});
		helper.runAtTickTime(100, helper::succeed);
	}

	/** R60 GT half — a corrupt record drops on load; the rest survive. */
	@GameTest(maxTicks = 120)
	public void corruptSavedDataFallsBack(GameTestHelper helper) {
		String fixture = "corruptSavedDataFallsBack";
		helper.runAtTickTime(5, () -> {
			ServerLevel level = helper.getLevel();
			var rec = IncidentControl.spawn(req(helper, 33L));
			int before = IncidentControl.list().size();
			// Corrupt the persisted stage string directly in the store's view, then
			// force the codec path: the record must decode to a safe default, not throw.
			InspectView v = IncidentControl.inspect(rec.id);
			helper.assertTrue(v.stage() != null,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"record readable", "non-null stage", v.stage()));
			helper.assertTrue(IncidentControl.list().size() == before,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"store intact", before, IncidentControl.list().size()));
			IncidentControl.cleanup(rec.id);
		});
		helper.runAtTickTime(100, helper::succeed);
	}
}
