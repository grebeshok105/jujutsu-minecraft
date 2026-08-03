package jujutsu.mod.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.entity.EntityDimensions;
import org.junit.jupiter.api.Test;

class CharacterPresentationScaleTest {
	private static final Path ROOT = Path.of("").toAbsolutePath();

	@Test
	void playerSkinModelsUseTheCharacterSpecificArmWidth() {
		assertEquals("wide", JujutsuCharacter.NONE.modelId());
		assertEquals("slim", JujutsuCharacter.NOBARA.modelId());
		assertEquals("wide", JujutsuCharacter.TODO.modelId());
		assertEquals("wide", JujutsuCharacter.MEGUMI.modelId());
	}

	@Test
	void todoScaleChangesBoundingBoxAndEyeHeightWithoutChangingOtherStats() {
		EntityDimensions base = EntityDimensions.scalable(0.6f, 1.8f);
		EntityDimensions scaled = base.scale(JujutsuCharacter.TODO.bodyScale());

		assertEquals(1.15f, JujutsuCharacter.TODO.bodyScale(), 1.0e-6f);
		assertEquals(0.69f, scaled.width(), 1.0e-6f);
		assertEquals(2.07f, scaled.height(), 1.0e-6f);
		assertEquals(base.eyeHeight() * 1.15f, scaled.eyeHeight(), 1.0e-6f);
	}

	@Test
	void dimensionRefreshFollowsBothAuthoritativeAndMirroredSelection() throws Exception {
		String serverSelection = Files.readString(ROOT.resolve(
				"src/main/java/jujutsu/mod/character/CharacterSelectionManager.java"));
		String clientSelection = Files.readString(ROOT.resolve(
				"src/client/java/jujutsu/mod/client/character/ClientCharacterSelectionManager.java"));
		String mixin = Files.readString(ROOT.resolve(
				"src/main/java/jujutsu/mod/mixin/CharacterPlayerDimensionsMixin.java"));

		assertTrue(serverSelection.contains("player.refreshDimensions()"),
				"Server selection must refresh the authoritative player dimensions");
		assertTrue(clientSelection.contains("refreshDimensions()"),
				"Client selection mirror must refresh rendered player dimensions");
		assertTrue(mixin.contains("CharacterSelectionView.of(player).bodyScale()"),
				"The common dimensions hook must use the selected vessel and its body scale");
		assertTrue(!mixin.toLowerCase().contains("reach") && !mixin.toLowerCase().contains("attack_damage"),
				"Physical Todo scale must not modify reach or damage");
	}
}
