package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import jujutsu.mod.registry.JujutsuEntities;
/**
 * Spawn-gate contract (Block 4, Step 4): the pure cap core boundaries, the profile's spawn
 * ordering/group sanity, and that the three registered biome rows read their numbers from the
 * profile — no literals beside the structure itself (three rows, all tiers covered).
 */
final class CursedSpiritSpawnRulesTest {
	// EntityType class-load touches vanilla registries, so the two spawnRows tests need a
	// bootstrapped game (read-only here: no JujutsuEntities.register() call — the registry test
	// owns registration and a second register() of the same ids would collide in one JVM).
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void belowCapBoundary() {
		int cap = CursedSpiritProfile.MAX_SPIRITS_NEARBY;
		assertTrue(CursedSpiritSpawnRules.belowCap(0, cap), "empty area passes");

		assertTrue(CursedSpiritSpawnRules.belowCap(cap - 1, cap), "cap-1 passes");
		assertFalse(CursedSpiritSpawnRules.belowCap(cap, cap), "cap refuses");
		assertFalse(CursedSpiritSpawnRules.belowCap(cap + 1, cap), "cap+1 refuses");
	}

	@Test
	void spawnWeightsOrderLesserAboveCommonAboveGreater() {
		int lesser = CursedSpiritProfile.of(CursedSpiritTier.LESSER).spawnWeight();
		int common = CursedSpiritProfile.of(CursedSpiritTier.COMMON).spawnWeight();
		int greater = CursedSpiritProfile.of(CursedSpiritTier.GREATER).spawnWeight();
		assertTrue(lesser > common, "LESSER weight " + lesser + " must exceed COMMON " + common);
		assertTrue(common > greater, "COMMON weight " + common + " must exceed GREATER " + greater);
	}

	@Test
	void spawnGroupsAreSane() {
		for (CursedSpiritTier tier : CursedSpiritTier.values()) {
			CursedSpiritTierStats stats = CursedSpiritProfile.of(tier);
			assertTrue(stats.spawnMinGroup() >= 1, tier + " minGroup >= 1");
			assertTrue(stats.spawnMinGroup() <= stats.spawnMaxGroup(), tier + " minGroup <= maxGroup");
		}
	}

	@Test
	void spawnRowsReadNumbersFromProfile() {
		List<CursedSpiritSpawnIntegration.SpawnRow> rows = CursedSpiritSpawnIntegration.spawnRows();
		assertEquals(CursedSpiritTier.values().length, rows.size(), "one row per tier");
		Set<CursedSpiritTier> seen = EnumSet.noneOf(CursedSpiritTier.class);
		for (CursedSpiritSpawnIntegration.SpawnRow row : rows) {
			assertTrue(seen.add(row.tier()), "tier covered once: " + row.tier());
			CursedSpiritTierStats stats = CursedSpiritProfile.of(row.tier());
			assertEquals(stats.spawnWeight(), row.weight(), row.tier() + " weight from profile");
			assertEquals(stats.spawnMinGroup(), row.minGroupSize(), row.tier() + " minGroup from profile");
			assertEquals(stats.spawnMaxGroup(), row.maxGroupSize(), row.tier() + " maxGroup from profile");
		}
	}

	@Test
	void spawnRowsTargetTheTierTypes() {
		List<CursedSpiritSpawnIntegration.SpawnRow> rows = CursedSpiritSpawnIntegration.spawnRows();
		for (CursedSpiritSpawnIntegration.SpawnRow row : rows) {
			switch (row.tier()) {
				case LESSER -> assertEquals(JujutsuEntities.LESSER_CURSED_SPIRIT, row.type(), "lesser type");
				case COMMON -> assertEquals(JujutsuEntities.CURSED_SPIRIT, row.type(), "common type");
				case GREATER -> assertEquals(JujutsuEntities.GREATER_CURSED_SPIRIT, row.type(), "greater type");
			}
		}
	}
}
