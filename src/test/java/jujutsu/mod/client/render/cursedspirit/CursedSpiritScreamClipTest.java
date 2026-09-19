package jujutsu.mod.client.render.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Regression contract for the shared hurt/scream shake fix. Scream clips keep authored rotation
 * recoil, but no root/body positional channel may exceed the fixed 0.75-block bound. The sanitized
 * clips remove those channels entirely; the explicit bound keeps future authored additions honest.
 */
class CursedSpiritScreamClipTest {
	private static final Set<String> ROOT_OR_BODY_BONES = Set.of(
			"body", "body2", "bod", "spine", "root", "flater", "flatter", "bone", "bone2", "bone4");

	@Test
	void screamRootAndBodyPositionsStayWithinBound() {
		float bound = CursedSpiritClips.SCREAM_POSITION_BOUND_BLOCKS;
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, AnimationDefinition> clips = CursedSpiritRigFixtures.clipsOf(
					CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			AnimationDefinition scream = clips.get("SCREAMER");
			if (scream == null) {
				continue;
			}
			for (Map.Entry<String, java.util.List<AnimationChannel>> entry
					: scream.boneAnimations().entrySet()) {
				if (!ROOT_OR_BODY_BONES.contains(entry.getKey())) {
					continue;
				}
				for (AnimationChannel channel : entry.getValue()) {
					if (channel.target() != AnimationChannel.Targets.POSITION) {
						continue;
					}
					for (var keyframe : channel.keyframes()) {
						float max = Math.max(Math.abs(keyframe.target().x()),
								Math.max(Math.abs(keyframe.target().y()), Math.abs(keyframe.target().z())));
						assertTrue(max <= bound,
							"scream position exceeds " + bound + " blocks on " + variant + "/"
									+ entry.getKey());
					}
				}
			}
		}
	}

	@Test
	void gulberAndGuzzlerRemainScreamlessControls() {
		for (CursedSpiritVariant variant : Set.of(CursedSpiritVariant.GULBER,
				CursedSpiritVariant.GUZZLER)) {
			Map<String, AnimationDefinition> clips = CursedSpiritRigFixtures.clipsOf(
					CursedSpiritRigFixtures.ANIMATIONS.get(variant));
			assertFalse(clips.containsKey("SCREAMER"), "control variant gained a scream clip: " + variant);
		}
	}
}
