package jujutsu.mod.client.render.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Cross-artifact contract for the Piercing Ox visual: every animation id named in client code
 * must exist in the shipped Blockbench set, the geometry keeps its identifier, and the model
 * serves the shipped sheet.
 */
final class MegumiOxResourcesTest {
	private static final Path CLIENT = Path.of("src/client/java/jujutsu/mod/client/render/megumi");
	private static final Path ANIMATABLE_SOURCE = CLIENT.resolve("MegumiOxGeoAnimatable.java");
	private static final Path MODEL_SOURCE = CLIENT.resolve("MegumiOxModel.java");
	private static final Path ANIMATION_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/animations/megumi_ox.animation.json");
	private static final Path GEO_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/models/megumi_ox.geo.json");
	private static final Path TEXTURE =
			Path.of("src/main/resources/assets/jujutsumod/textures/entity/megumi_ox.png");

	private static final Set<String> CONTRACT_CLIPS = Set.of(
			"animation.megumi_ox.charge",
			"animation.megumi_ox.idle",
			"animation.megumi_ox.impact",
			"animation.megumi_ox.recover",
			"animation.megumi_ox.run",
			"animation.megumi_ox.walk",
			"animation.megumi_ox.windup"
	);

	@Test
	void animatableClipIdsExistInTheShippedAnimationSet() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		Set<String> referenced = Pattern.compile("animation\\.megumi_ox\\.[A-Za-z0-9_]+")
				.matcher(source)
				.results()
				.map(MatchResult::group)
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(CONTRACT_CLIPS, referenced,
				"The animatable must reference exactly the contracted Piercing Ox clips");

		JsonObject animations = json(ANIMATION_JSON).getAsJsonObject("animations");
		for (String clip : referenced) {
			assertTrue(animations.has(clip),
					"Client code plays " + clip + " but the shipped animation JSON has no such clip");
		}
	}

	@Test
	void geometryKeepsItsIdentifier() throws Exception {
		JsonObject description = json(GEO_JSON).getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject().getAsJsonObject("description");
		assertEquals("geometry.megumi_ox", description.get("identifier").getAsString());
	}

	@Test
	void modelAndTextureResolveToShippedFiles() throws Exception {
		assertTrue(Files.isRegularFile(GEO_JSON), "Missing shipped Piercing Ox geometry");
		assertTrue(Files.isRegularFile(ANIMATION_JSON), "Missing shipped Piercing Ox animation set");
		assertTrue(Files.isRegularFile(TEXTURE), "Missing shipped Piercing Ox texture");
		String model = Files.readString(MODEL_SOURCE);
		assertTrue(model.contains("textures/entity/megumi_ox.png"),
				"The model must serve the shipped Piercing Ox sheet");
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}
}
