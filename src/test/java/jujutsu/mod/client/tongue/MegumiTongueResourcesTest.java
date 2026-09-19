package jujutsu.mod.client.tongue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class MegumiTongueResourcesTest {
	private static final Path GEO = Path.of(
			"src/main/resources/assets/jujutsumod/geckolib/models/megumi_tongue.geo.json");
	private static final Path TONGUE_TEXTURE = Path.of(
			"src/main/resources/assets/jujutsumod/textures/entity/megumi_tongue.png");
	private static final Path HEAD_TEXTURE = Path.of(
			"src/main/resources/assets/jujutsumod/textures/entity/megumi_toad_head.png");

	@Test
	void tongueHasRootAndSixTaperedCubicSegments() throws Exception {
		assertTrue(Files.isRegularFile(GEO));
		JsonObject geometry = JsonParser.parseString(Files.readString(GEO))
				.getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		assertEquals("geometry.megumi_tongue",
				geometry.getAsJsonObject("description").get("identifier").getAsString());
		JsonArray bones = geometry.getAsJsonArray("bones");
		Set<String> names = new HashSet<>();
		int cubes = 0;
		for (var element : bones) {
			JsonObject bone = element.getAsJsonObject();
			names.add(bone.get("name").getAsString());
			if (!bone.has("cubes")) {
				continue;
			}
			for (var cubeElement : bone.getAsJsonArray("cubes")) {
				JsonArray size = cubeElement.getAsJsonObject().getAsJsonArray("size");
				assertTrue(size.get(0).getAsDouble() >= 1.0 && size.get(0).getAsDouble() <= 2.0);
				assertTrue(size.get(1).getAsDouble() >= 1.0 && size.get(1).getAsDouble() <= 2.0);
				cubes++;
			}
		}
		assertEquals(7, bones.size());
		assertTrue(names.contains("tongue_root"));
		assertEquals(6, cubes);
	}

	@Test
	void tongueAndHeadTexturesAreReadable64PixelAtlases() throws Exception {
		BufferedImage tongue = ImageIO.read(TONGUE_TEXTURE.toFile());
		BufferedImage head = ImageIO.read(HEAD_TEXTURE.toFile());
		assertEquals(64, tongue.getWidth());
		assertEquals(64, tongue.getHeight());
		assertEquals(64, head.getWidth());
		assertEquals(64, head.getHeight());
		assertTrue(hasOpaquePixel(tongue));
		assertTrue(hasOpaquePixel(head));
	}

	private static boolean hasOpaquePixel(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					return true;
				}
			}
		}
		return false;
	}
}
