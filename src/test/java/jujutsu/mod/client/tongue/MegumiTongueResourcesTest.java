package jujutsu.mod.client.tongue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
	void tongueHasRootAndTaperedCubicSegments() throws Exception {
		assertTrue(Files.isRegularFile(GEO));
		JsonObject geometry = JsonParser.parseString(Files.readString(GEO))
				.getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		assertEquals("geometry.megumi_tongue",
				geometry.getAsJsonObject("description").get("identifier").getAsString());
		JsonArray bones = geometry.getAsJsonArray("bones");
		Set<String> names = new HashSet<>();
		List<Double> segmentWidths = new ArrayList<>();
		for (var element : bones) {
			JsonObject bone = element.getAsJsonObject();
			String name = bone.get("name").getAsString();
			names.add(name);
			if (!name.startsWith("tongue_segment_")) {
				continue;
			}
			assertTrue(bone.has("cubes") && !bone.getAsJsonArray("cubes").isEmpty(),
					"each tongue segment has geometry: " + name);
			JsonArray size = bone.getAsJsonArray("cubes").get(0).getAsJsonObject()
					.getAsJsonArray("size");
			assertTrue(size.get(0).getAsDouble() >= 1.0 && size.get(0).getAsDouble() <= 2.0);
			assertTrue(size.get(1).getAsDouble() >= 1.0 && size.get(1).getAsDouble() <= 2.0);
			segmentWidths.add(size.get(0).getAsDouble());
		}
		assertTrue(segmentWidths.size() >= 5 && segmentWidths.size() <= 7,
				"tongue has 5-7 tapering segments: " + segmentWidths.size());
		assertTrue(names.contains("tongue_root"));
		for (int index = 1; index < segmentWidths.size(); index++) {
			assertTrue(segmentWidths.get(index) < segmentWidths.get(index - 1),
					"tongue segment " + index + " is narrower than its predecessor");
		}
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
