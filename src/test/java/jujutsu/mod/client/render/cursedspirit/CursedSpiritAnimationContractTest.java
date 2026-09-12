package jujutsu.mod.client.render.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Structural animation guarantees: every variant ships exactly its frozen clip
 * count, every clip has exactly its authored length, and no channel targets a
 * part absent from the baked model.
 */
class CursedSpiritAnimationContractTest {
	/**
	 * Frozen per-variant clip counts, written as literals independent of
	 * {@link CursedSpiritRigFixtures#BONES} so a fixtures typo cannot pass here.
	 */
	private static final Map<CursedSpiritVariant, Integer> EXPECTED_CLIP_COUNTS = Map.of(
			CursedSpiritVariant.PROWLER, 4,
			CursedSpiritVariant.FLOATING_CURSE, 3,
			CursedSpiritVariant.GULBER, 3,
			CursedSpiritVariant.KELVIN, 4,
			CursedSpiritVariant.BUTCHER, 4,
			CursedSpiritVariant.GUZZLER, 3,
			CursedSpiritVariant.BLUD, 4,
			CursedSpiritVariant.WALKING_BED, 4,
			CursedSpiritVariant.WISTIVER, 4);

	/**
	 * Authored per-clip lengths in seconds, read off the pack's decompiled
	 * {@code AnimationDefinition} statements (dropped clips excluded).
	 */
	private static final Map<CursedSpiritVariant, Map<String, Float>> EXPECTED_LENGTHS = Map.ofEntries(
			Map.entry(CursedSpiritVariant.PROWLER,
					Map.of("IDLE", 2.12f, "WALK", 0.92f, "ATTACK", 1.36f, "SCREAMER", 0.56f)),
			Map.entry(CursedSpiritVariant.FLOATING_CURSE,
					Map.of("IDLE", 2.12f, "ATTACK", 0.72f, "SCREAMER", 0.56f)),
			Map.entry(CursedSpiritVariant.GULBER,
					Map.of("IDLE", 5.04f, "WALK", 1.12f, "ATTACK", 0.6f)),
			Map.entry(CursedSpiritVariant.KELVIN,
					Map.of("IDLE", 5.04f, "WALK", 1.08f, "ATTACK", 1.2f, "SCREAMER", 0.56f)),
			Map.entry(CursedSpiritVariant.BUTCHER,
					Map.of("IDLE", 2.08f, "WALK", 1.08f, "ATTACK", 1.36f, "SCREAMER", 0.56f)),
			Map.entry(CursedSpiritVariant.GUZZLER,
					Map.of("IDLE", 5.04f, "WALK", 1.32f, "ATTACK", 0.56f)),
			Map.entry(CursedSpiritVariant.BLUD,
					Map.of("IDLE", 2.08f, "WALK", 1.16f, "ATTACK", 0.76f, "SCREAMER", 0.56f)),
			Map.entry(CursedSpiritVariant.WALKING_BED,
					Map.of("IDLE", 2.96f, "WALK", 1.08f, "ATTACK", 1.3f, "SCREAMER", 0.56f)),
			Map.entry(CursedSpiritVariant.WISTIVER,
					Map.of("IDLE", 2.08f, "WALK", 1.24f, "ATTACK", 0.76f, "SCREAMER", 0.56f)));

	@Test
	void clipCountsMatchFrozen() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			assertEquals(EXPECTED_CLIP_COUNTS.get(variant).intValue(), clips.size(),
					"clip count for " + variant);
		}
	}

	@Test
	void clipLengthsMatchAuthored() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			Map<String, Float> expected = EXPECTED_LENGTHS.get(variant);
			assertEquals(expected.keySet(), clips.keySet(), "clip keys for " + variant);
			for (Map.Entry<String, Float> frozen : expected.entrySet()) {
				assertEquals(frozen.getValue(),
						Float.valueOf(clips.get(frozen.getKey()).lengthInSeconds()),
						"length for " + variant + "/" + frozen.getKey());
			}
		}
	}

	@Test
	void noChannelTargetsMissingPart() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			ModelPart root = CursedSpiritRigFixtures.LAYERS.get(variant).get().bakeRoot();
			java.util.function.Function<String, ModelPart> lookup = root.createPartLookup();
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			for (Map.Entry<String, AnimationDefinition> clip : clips.entrySet()) {
				for (String bone : clip.getValue().boneAnimations().keySet()) {
					assertTrue(lookup.apply(bone) != null,
							"channel of " + variant + "/" + clip.getKey() + " targets missing part "
									+ bone);
				}
			}
		}
	}
}
