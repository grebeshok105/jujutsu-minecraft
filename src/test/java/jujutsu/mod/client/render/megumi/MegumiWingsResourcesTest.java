package jujutsu.mod.client.render.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

final class MegumiWingsResourcesTest {
	private static final Path GEO =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/models/megumi_nue_wings.geo.json");
	private static final Path ANIMATIONS =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/animations/megumi_nue_wings.animation.json");
	private static final Path TEXTURE =
			Path.of("src/main/resources/assets/jujutsumod/textures/entity/megumi_nue_wings.png");
	private static final Set<String> CLIPS = Set.of(
			"animation.megumi_nue_wings.materialize",
			"animation.megumi_nue_wings.folded_idle",
			"animation.megumi_nue_wings.unfold",
			"animation.megumi_nue_wings.fly",
			"animation.megumi_nue_wings.fold",
			"animation.megumi_nue_wings.dissolve");

	@Test
	void wingGeometryAndTextureExistWithVolumetricCubes() throws Exception {
		assertTrue(Files.isRegularFile(GEO));
		assertTrue(Files.isRegularFile(TEXTURE));
		JsonObject geometry = json(GEO).getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		assertEquals("geometry.megumi_nue_wings",
				geometry.getAsJsonObject("description").get("identifier").getAsString());
		JsonArray bones = geometry.getAsJsonArray("bones");
		Set<String> names = new java.util.HashSet<>();
		java.util.Map<String, String> parents = new java.util.HashMap<>();
		for (var element : bones) {
			JsonObject bone = element.getAsJsonObject();
			String name = bone.get("name").getAsString();
			names.add(name);
			if (bone.has("parent")) {
				parents.put(name, bone.get("parent").getAsString());
			}
			if (!bone.has("cubes")) {
				continue;
			}
			for (var cubeElement : bone.getAsJsonArray("cubes")) {
				JsonArray size = cubeElement.getAsJsonObject().getAsJsonArray("size");
				assertTrue(size.get(0).getAsDouble() > 0.0 && size.get(1).getAsDouble() > 0.0);
				assertTrue(size.get(2).getAsDouble() >= 1.0,
						"wing planes must have at least one pixel of depth");
			}
		}
		assertEquals(Set.of("wings_root", "right_wing", "right_wing_tip", "left_wing", "left_wing_tip"), names);
		assertEquals("wings_root", parents.get("right_wing"));
		assertEquals("right_wing", parents.get("right_wing_tip"));
		assertEquals("wings_root", parents.get("left_wing"));
		assertEquals("left_wing", parents.get("left_wing_tip"));
	}

	@Test
	void animationSetContainsEveryLifecycleClip() throws Exception {
		assertTrue(Files.isRegularFile(ANIMATIONS));
		JsonObject animations = json(ANIMATIONS).getAsJsonObject("animations");
		assertEquals(CLIPS, animations.keySet());
		for (String clip : CLIPS) {
			assertTrue(animations.getAsJsonObject(clip).get("animation_length").getAsDouble() > 0.0);
		}
		assertTrue(animations.getAsJsonObject("animation.megumi_nue_wings.folded_idle").get("loop").getAsBoolean());
		assertTrue(animations.getAsJsonObject("animation.megumi_nue_wings.fly").get("loop").getAsBoolean());
	}

	@Test
	void wingUvRegionsCarryNonTransparentPixels() throws Exception {
		BufferedImage texture = ImageIO.read(TEXTURE.toFile());
		assertEquals(256, texture.getWidth());
		assertEquals(256, texture.getHeight());
		assertTrue(hasOpaquePixel(texture, 36, 4, 114, 31), "tip wing UV island is empty");
		assertTrue(hasOpaquePixel(texture, 27, 33, 109, 103), "root wing UV island is empty");
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static boolean hasOpaquePixel(BufferedImage image, int minX, int minY, int maxX, int maxY) {
		for (int y = minY; y < maxY; y++) {
			for (int x = minX; x < maxX; x++) {
				if ((image.getRGB(x, y) >>> 24) != 0) {
					return true;
				}
			}
		}
		return false;
	}
}
