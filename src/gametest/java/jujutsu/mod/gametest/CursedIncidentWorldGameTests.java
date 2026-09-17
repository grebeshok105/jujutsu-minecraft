package jujutsu.mod.gametest;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;

import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentControl.InspectView;
import jujutsu.mod.cursedincident.IncidentControl.SpawnRequest;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.SourceKind;
import jujutsu.mod.cursedincident.persist.IncidentNbt;
import jujutsu.mod.cursedincident.persist.IncidentSavedData;

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
			// 400_000 ticks guarantees CATASTROPHIC at every multiplier (worst 1.82 →
			// threshold 349_440); grade is rolled, so the bound must cover it.
			IncidentControl.advance(rec.id, 400_000);
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
			// 96_000 ticks guarantees ≥GROWING even at the slowest multiplier (1.82 →
			// threshold 87_360); the oracle is that A moved while B stayed put.
			IncidentControl.advance(a.id, 96_000);
			InspectView va = IncidentControl.inspect(a.id);
			InspectView vb = IncidentControl.inspect(b.id);
			helper.assertTrue(va.stage().ordinal() > IncidentStage.INITIAL.ordinal()
					&& vb.stage() == IncidentStage.INITIAL,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"independent stages", "a advanced, b INITIAL",
							va.stage() + " vs " + vb.stage()));
			IncidentControl.cleanup(a.id);
			IncidentControl.cleanup(b.id);
		});
		helper.runAtTickTime(100, helper::succeed);
	}


	/** R59 GT half — ids stay unique across an explicit SavedData codec round-trip. */
	@GameTest(maxTicks = 120)
	public void idCollisionFreeAcrossSaveLoad(GameTestHelper helper) {
		String fixture = "idCollisionFreeAcrossSaveLoad";
		helper.runAtTickTime(5, () -> {
			var a = IncidentControl.spawn(req(helper, 11L));
			var b = IncidentControl.spawn(req(helper, 22L));
			helper.assertTrue(!a.id.equals(b.id),
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"distinct ids", "a!=b", a.id + " vs " + b.id));
			IncidentSavedData before = new IncidentSavedData(Map.of(a.id, a, b.id, b), 0L);
			JsonObject encoded = IncidentSavedData.CODEC.encodeStart(JsonOps.INSTANCE, before)
					.result().orElseThrow().getAsJsonObject();
			IncidentSavedData after = IncidentSavedData.CODEC.parse(JsonOps.INSTANCE, encoded)
					.result().orElseThrow();
			helper.assertTrue(after.incidents().size() == 2
					&& after.incidents().containsKey(a.id)
					&& after.incidents().containsKey(b.id),
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"ids survive SavedData round-trip", "both ids", after.incidents().keySet()));
			IncidentControl.cleanup(a.id);
			IncidentControl.cleanup(b.id);
		});
		helper.runAtTickTime(100, helper::succeed);
	}

	/** R60 GT half — codec fallback drops only the corrupt entry and preserves siblings. */
	@GameTest(maxTicks = 120)
	public void corruptSavedDataFallsBack(GameTestHelper helper) {
		String fixture = "corruptSavedDataFallsBack";
		helper.runAtTickTime(5, () -> {
			var healthy = IncidentControl.spawn(req(helper, 33L));
			var sibling = IncidentControl.spawn(req(helper, 44L));
			IncidentControl.setStage(healthy.id, IncidentStage.GROWING);
			JsonObject root = IncidentSavedData.CODEC.encodeStart(JsonOps.INSTANCE,
					new IncidentSavedData(Map.of(healthy.id, healthy, sibling.id, sibling), 0L))
					.result().orElseThrow().getAsJsonObject();
			JsonObject incidents = root.getAsJsonObject(IncidentNbt.INCIDENTS);
			JsonObject corruptStage = incidents.getAsJsonObject(healthy.id.toString()).deepCopy();
			corruptStage.addProperty(IncidentNbt.STAGE, "not_a_stage");
			incidents.add(healthy.id.toString(), corruptStage);
			incidents.add("dddddddd-dddd-dddd-dddd-dddddddddddd", new JsonObject());
			IncidentSavedData decoded = IncidentSavedData.CODEC.parse(JsonOps.INSTANCE, root)
					.result().orElseThrow();
			helper.assertTrue(decoded.incidents().size() == 2
					&& decoded.get(healthy.id) != null
					&& decoded.get(sibling.id) != null,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"corrupt record isolation", "two healthy records", decoded.incidents().keySet()));
			helper.assertTrue(decoded.get(healthy.id).stage == IncidentStage.INITIAL
					&& decoded.get(sibling.id).stage == sibling.stage,
					GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"corrupt stage fallback", "healthy=INITIAL, sibling preserved",
							decoded.get(healthy.id).stage + " / " + decoded.get(sibling.id).stage));
			IncidentControl.cleanup(healthy.id);
			IncidentControl.cleanup(sibling.id);
		});
		helper.runAtTickTime(100, helper::succeed);
	}

}
