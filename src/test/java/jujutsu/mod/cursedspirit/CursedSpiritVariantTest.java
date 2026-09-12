package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Variant table completeness (Step 9): the frozen roster, per-variant sounds/weights/scales, the
 * weighted roll (seeded distribution + boundary core), and the NBT load decision
 * (unknown/cross-tier ids re-roll, valid ids survive).
 */
final class CursedSpiritVariantTest {
	// The roll/resolve tests touch CursedSpiritEntity statics, whose class init defines
	// synched entity data — that needs a bootstrapped game. Without this, the class passes
	// only when another test (e.g. CursedSpiritRegistryTest) bootstraps first in the same fork.
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void frozenRosterMatchesTiers() {
		assertEquals(List.of(CursedSpiritVariant.PROWLER, CursedSpiritVariant.FLOATING_CURSE,
				CursedSpiritVariant.GULBER), CursedSpiritVariant.variantsOf(CursedSpiritTier.LESSER));
		assertEquals(List.of(CursedSpiritVariant.KELVIN, CursedSpiritVariant.BUTCHER,
				CursedSpiritVariant.GUZZLER, CursedSpiritVariant.BLUD),
				CursedSpiritVariant.variantsOf(CursedSpiritTier.COMMON));
		assertEquals(List.of(CursedSpiritVariant.WALKING_BED, CursedSpiritVariant.WISTIVER),
				CursedSpiritVariant.variantsOf(CursedSpiritTier.GREATER));
		assertEquals(9, CursedSpiritVariant.values().length);
	}

	@Test
	void everyVariantHasTextureAndRequiredSounds() {
		Map<CursedSpiritVariant, Integer> weights = new EnumMap<>(CursedSpiritVariant.class);
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			assertTrue(variant.id().equals(variant.id().toLowerCase(java.util.Locale.ROOT)),
					variant + " id lowercase");
			assertEquals("textures/entity/cursed/cursed_" + variant.id() + ".png", variant.texture());
			assertNotNull(variant.ambientSound(), variant + " ambient");
			assertNotNull(variant.hurtSound(), variant + " hurt");
			assertNotNull(variant.deathSound(), variant + " death");
			assertTrue(variant.renderScale() > 0, variant + " scale");
			weights.put(variant, variant.weight());
		}
		// Frozen weights.
		assertEquals(3, weights.get(CursedSpiritVariant.PROWLER));
		assertEquals(2, weights.get(CursedSpiritVariant.FLOATING_CURSE));
		assertEquals(2, weights.get(CursedSpiritVariant.GULBER));
		assertEquals(3, weights.get(CursedSpiritVariant.KELVIN));
		assertEquals(2, weights.get(CursedSpiritVariant.BUTCHER));
		assertEquals(2, weights.get(CursedSpiritVariant.GUZZLER));
		assertEquals(1, weights.get(CursedSpiritVariant.BLUD));
		assertEquals(1, weights.get(CursedSpiritVariant.WALKING_BED));
		assertEquals(1, weights.get(CursedSpiritVariant.WISTIVER));
	}

	@Test
	void onlyGulberAndGuzzlerLackAScream() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			if (variant == CursedSpiritVariant.GULBER || variant == CursedSpiritVariant.GUZZLER) {
				assertNull(variant.screamSound(), variant + " must have no scream");
			} else {
				assertNotNull(variant.screamSound(), variant + " scream");
			}
		}
	}

	@Test
	void renderOffsetsOnlyFloaterHovers() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			if (variant == CursedSpiritVariant.FLOATING_CURSE) {
				assertEquals(0.35f, variant.renderOffsetY());
			} else {
				assertEquals(0.0f, variant.renderOffsetY());
			}
		}
	}

	@Test
	void weightedSelectionCoreBoundaries() {
		// LESSER roster weights 3/2/2, total 7: roll 0-2 -> PROWLER, 3-4 -> FLOATING_CURSE, 5-6 -> GULBER.
		List<CursedSpiritVariant> roster = CursedSpiritVariant.variantsOf(CursedSpiritTier.LESSER);
		assertEquals(0, CursedSpiritEntity.selectVariantIndex(roster, 7, 0));
		assertEquals(0, CursedSpiritEntity.selectVariantIndex(roster, 7, 2));
		assertEquals(1, CursedSpiritEntity.selectVariantIndex(roster, 7, 3));
		assertEquals(1, CursedSpiritEntity.selectVariantIndex(roster, 7, 4));
		assertEquals(2, CursedSpiritEntity.selectVariantIndex(roster, 7, 5));
		assertEquals(2, CursedSpiritEntity.selectVariantIndex(roster, 7, 6));
	}

	@Test
	void seededRollStaysInTierAndCoversEveryVariant() {
		RandomSource random = RandomSource.create(1234L);
		for (CursedSpiritTier tier : CursedSpiritTier.values()) {
			Map<CursedSpiritVariant, Integer> counts = new EnumMap<>(CursedSpiritVariant.class);
			for (int i = 0; i < 20000; i++) {
				CursedSpiritVariant rolled = CursedSpiritEntity.rollVariant(tier, random);
				assertEquals(tier, rolled.tier(), "roll stays in tier " + tier);
				counts.merge(rolled, 1, Integer::sum);
			}
			// Every variant of the tier appears well above noise with this seed.
			for (CursedSpiritVariant variant : CursedSpiritVariant.variantsOf(tier)) {
				assertTrue(counts.getOrDefault(variant, 0) > 500,
						variant + " appears in 20000 seeded rolls, counts=" + counts);
			}
		}
	}

	@Test
	void resolveLoadedVariantKeepsValidAndRerollsUnknownOrCrossTier() {
		RandomSource random = RandomSource.create(99L);
		assertEquals(CursedSpiritVariant.KELVIN,
				CursedSpiritEntity.resolveLoadedVariant(CursedSpiritTier.COMMON, "kelvin", random));
		// Unknown id re-rolls inside the tier.
		for (int i = 0; i < 20; i++) {
			assertEquals(CursedSpiritTier.COMMON,
					CursedSpiritEntity.resolveLoadedVariant(CursedSpiritTier.COMMON, "nope", random).tier());
		}
		// Cross-tier id (a GREATER id on a COMMON body) re-rolls inside the body tier.
		for (int i = 0; i < 20; i++) {
			assertEquals(CursedSpiritTier.COMMON,
					CursedSpiritEntity.resolveLoadedVariant(CursedSpiritTier.COMMON, "wistiver", random).tier());
		}
		// The NBT key is the frozen contract.
		assertEquals("Variant", CursedSpiritEntity.VARIANT_TAG);
	}
}
