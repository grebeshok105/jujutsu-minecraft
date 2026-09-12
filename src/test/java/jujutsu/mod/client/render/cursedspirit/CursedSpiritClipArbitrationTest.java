package jujutsu.mod.client.render.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * A frame applies exactly one clip layer: with an attack running, every bone the attack clip names
 * must end the frame on the attack's pose — never overwritten by the eternal idle loop. The ported
 * models used to apply idle last, which is what made bodies jitter and made a swing read as empty
 * (issue #77).
 */
class CursedSpiritClipArbitrationTest {
	private static final float AGE = 5.0f;

	@Test
	void attackPoseSurvivesTheIdleLoop() {
		for (CursedSpiritVariant variant : CursedSpiritVariant.values()) {
			Map<String, Set<String>> frozen = CursedSpiritRigFixtures.BONES.get(variant);
			Set<String> shared = new HashSet<>(frozen.get("IDLE"));
			shared.retainAll(frozen.get("ATTACK"));
			shared.remove("head"); // the head carries the look rotation from the render state
			assertTrue(!shared.isEmpty(), variant + " shares no bone between idle and attack");
			String bone = shared.iterator().next();
			float attackOnly = poseOf(variant, bone, true, false);
			float idleOnly = poseOf(variant, bone, false, true);
			float both = poseOf(variant, bone, true, true);
			assertNotEquals(attackOnly, idleOnly,
					"fixture bone must differ between the clips: " + variant + "/" + bone);
			assertEquals(attackOnly, both, 1.0E-6f,
					"attack must win over the idle loop for " + variant + "/" + bone);
		}
	}

	/** Runs setupAnim on a fresh rig with the given clips started and returns the bone's X rotation. */
	private static float poseOf(CursedSpiritVariant variant, String bone, boolean attack, boolean idle) {
		ModelPart root = CursedSpiritRigFixtures.LAYERS.get(variant).get().bakeRoot();
		EntityModel<CursedSpiritRenderState> model = CursedSpiritRigFixtures.MODELS.get(variant).apply(root);
		CursedSpiritRenderState state = new CursedSpiritRenderState();
		state.ageInTicks = AGE;
		if (attack) {
			state.attack.start(0);
		}
		if (idle) {
			state.idle.start(0);
		}
		model.setupAnim(state);
		return root.createPartLookup().apply(bone).xRot;
	}
}
