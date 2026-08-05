package jujutsu.mod.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CharacterSkinAnimationBridgeTest {
	@Test
	void nestedTransformsComposeInVanillaZyxOrder() {
		CharacterSkinAnimationAdapter.Transform parent = CharacterSkinAnimationAdapter.Transform.fromGeo(
				1.5f, -2.0f, 0.5f, 0.3f, -0.4f, 0.5f);
		CharacterSkinAnimationAdapter.Transform child = CharacterSkinAnimationAdapter.Transform.fromGeo(
				2.0f, 3.0f, 4.0f, -0.2f, 0.6f, -0.7f);

		CharacterSkinAnimationAdapter.Transform actual = parent.plus(child);
		Quaternionf parentRotation = new Quaternionf().rotationZYX(0.5f, 0.4f, -0.3f);
		Quaternionf childRotation = new Quaternionf().rotationZYX(-0.7f, -0.6f, 0.2f);
		Quaternionf expectedRotation = new Quaternionf(parentRotation).mul(childRotation);
		Vector3f expectedPosition = parentRotation.transform(new Vector3f(-2.0f, 3.0f, 4.0f))
				.add(-1.5f, -2.0f, 0.5f);

		assertEquals(expectedPosition.x, actual.x(), 1.0e-5f);
		assertEquals(expectedPosition.y, actual.y(), 1.0e-5f);
		assertEquals(expectedPosition.z, actual.z(), 1.0e-5f);
		assertQuaternionEquivalent(expectedRotation, actual.rotation());
		Vector3f euler = actual.vanillaEuler();
		Quaternionf reconstructed = new Quaternionf().rotationZYX(euler.z, euler.y, euler.x);
		assertQuaternionEquivalent(actual.rotation(), reconstructed);
	}

	@Test
	void capturedPlayerPartsRestoreEveryMutableFieldAndCloseIsIdempotent() {
		PlayerModel model = newPlayerModel();
		List<ModelPart> parts = List.of(
				model.root(), model.body, model.head, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg);
		List<PartSnapshot> expected = parts.stream().map(PartSnapshot::capture).toList();

		CharacterSkinAnimationState snapshot = CharacterSkinAnimationState.capture(model);
		for (int index = 0; index < parts.size(); index++) {
			mutate(parts.get(index), index + 1.0f);
		}

		snapshot.close();
		for (PartSnapshot part : expected) {
			part.assertRestored();
		}

		model.body.x = 123.0f;
		snapshot.close();
		assertEquals(123.0f, model.body.x);
	}

	private static PlayerModel newPlayerModel() {
		return new PlayerModel(
				LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
	}

	private static void mutate(ModelPart part, float value) {
		part.x = value;
		part.y = value + 1.0f;
		part.z = value + 2.0f;
		part.xRot = value + 3.0f;
		part.yRot = value + 4.0f;
		part.zRot = value + 5.0f;
		part.xScale = value + 6.0f;
		part.yScale = value + 7.0f;
		part.zScale = value + 8.0f;
		part.visible = false;
		part.skipDraw = true;
	}

	private static void assertQuaternionEquivalent(Quaternionf expected, Quaternionf actual) {
		assertTrue(Math.abs(expected.dot(actual)) > 1.0f - 1.0e-5f,
				() -> "Expected equivalent rotations, got " + expected + " and " + actual);
	}

	private record PartSnapshot(ModelPart part, float x, float y, float z, float xRot, float yRot, float zRot,
			float xScale, float yScale, float zScale, boolean visible, boolean skipDraw) {
		private static PartSnapshot capture(ModelPart part) {
			return new PartSnapshot(part, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
					part.xScale, part.yScale, part.zScale, part.visible, part.skipDraw);
		}

		private void assertRestored() {
			assertEquals(x, part.x);
			assertEquals(y, part.y);
			assertEquals(z, part.z);
			assertEquals(xRot, part.xRot);
			assertEquals(yRot, part.yRot);
			assertEquals(zRot, part.zRot);
			assertEquals(xScale, part.xScale);
			assertEquals(yScale, part.yScale);
			assertEquals(zScale, part.zScale);
			assertEquals(visible, part.visible);
			assertEquals(skipDraw, part.skipDraw);
		}
	}
}
