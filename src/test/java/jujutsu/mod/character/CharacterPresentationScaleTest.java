package jujutsu.mod.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
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
	void todoSkinUsesClassicFourPixelArmUvs() throws IOException {
		Path skin = ROOT.resolve("src/main/resources/assets/jujutsumod/textures/entity/character/todo.png");
		BufferedImage image = ImageIO.read(skin.toFile());
		assertNotNull(image, "Could not read Todo player skin");
		assertEquals(64, image.getWidth());
		assertEquals(64, image.getHeight());

		assertOpaqueRect(image, 44, 16, 8, 4, "right arm top and bottom faces");
		assertOpaqueRect(image, 40, 20, 16, 12, "right arm side faces");
		assertOpaqueRect(image, 36, 48, 8, 4, "left arm top and bottom faces");
		assertOpaqueRect(image, 32, 52, 16, 12, "left arm side faces");
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
	private static void assertOpaqueRect(BufferedImage image, int x, int y, int width, int height,
			String message) {
		for (int py = y; py < y + height; py++) {
			for (int px = x; px < x + width; px++) {
				assertTrue((image.getRGB(px, py) >>> 24) != 0,
						message + " must cover pixel (" + px + ", " + py + ")");
			}
		}
	}

}
