package jujutsu.mod.client.render.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Structural animation guarantees: clip keys are unique per variant, every clip has
 * a positive length, and no channel targets a part absent from the baked model.
 */
class CursedSpiritAnimationContractTest {
	@Test
	void clipKeysUniquePerVariant() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			Set<String> unique = new HashSet<>(clips.keySet());
			assertEquals(clips.size(), unique.size(), "duplicate clip keys for " + variant);
			assertEquals(CursedSpiritRigFixtures.BONES.get(variant).size(), clips.size(),
					"clip count for " + variant);
		}
	}

	@Test
	void clipLengthsPositive() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			for (Map.Entry<String, AnimationDefinition> clip : clips.entrySet()) {
				assertTrue(clip.getValue().lengthInSeconds() > 0.0f,
						"non-positive length for " + variant + "/" + clip.getKey());
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
