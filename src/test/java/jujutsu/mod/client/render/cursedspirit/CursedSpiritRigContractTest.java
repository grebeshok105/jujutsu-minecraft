package jujutsu.mod.client.render.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Pins the frozen port contract: every variant ships exactly its frozen clips, each
 * clip animates exactly its frozen bone set with the frozen looping flag, every clip
 * bone exists on the baked model, and every texture file exists.
 */
class CursedSpiritRigContractTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/jujutsumod");

	@Test
	void clipKeysMatchFrozen() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			assertEquals(CursedSpiritRigFixtures.BONES.get(variant).keySet(), clips.keySet(),
					"clip keys for " + variant);
		}
	}

	@Test
	void clipBoneSetsAndCountsMatchFrozen() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			for (Map.Entry<String, Set<String>> frozen
					: CursedSpiritRigFixtures.BONES.get(variant).entrySet()) {
				Set<String> actual = new HashSet<>(
						clips.get(frozen.getKey()).boneAnimations().keySet());
				assertEquals(frozen.getValue(), actual,
						"bone set for " + variant + "/" + frozen.getKey());
				assertEquals(frozen.getValue().size(), actual.size(),
						"bone count for " + variant + "/" + frozen.getKey());
			}
		}
	}

	@Test
	void loopingFlagsMatchFrozen() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			for (Map.Entry<String, AnimationDefinition> clip : clips.entrySet()) {
				boolean expectedLooping =
						!CursedSpiritRigFixtures.NON_LOOPING.contains(variant.name() + "/" + clip.getKey());
				assertEquals(expectedLooping, clip.getValue().looping(),
						"looping for " + variant + "/" + clip.getKey());
			}
		}
	}

	@Test
	void everyClipBoneExistsOnBakedModel() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			ModelPart root = CursedSpiritRigFixtures.LAYERS.get(variant).get().bakeRoot();
			java.util.function.Function<String, ModelPart> lookup = root.createPartLookup();
			Map<String, AnimationDefinition> clips =
					CursedSpiritRigFixtures.clipsOf(CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			for (Map.Entry<String, AnimationDefinition> clip : clips.entrySet()) {
				for (String bone : clip.getValue().boneAnimations().keySet()) {
					assertTrue(lookup.apply(bone) != null,
							"clip bone " + bone + " of " + variant + "/" + clip.getKey()
									+ " missing from baked model");
				}
			}
		}
	}

	@Test
	void everyTextureExists() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Path texture = ASSETS.resolve(variant.texture());
			assertTrue(Files.isRegularFile(texture), "missing texture " + texture);
		}
	}
}
