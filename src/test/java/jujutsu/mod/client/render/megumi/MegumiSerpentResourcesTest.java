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
 * Cross-artifact contract for the Great Serpent visual: every animation id named in client code
 * must exist in the shipped Blockbench set, the geometry keeps its identifier, and the model
 * serves the shipped sheet.
 */
final class MegumiSerpentResourcesTest {
	private static final Path CLIENT = Path.of("src/client/java/jujutsu/mod/client/render/megumi");
	private static final Path ANIMATABLE_SOURCE = CLIENT.resolve("MegumiSerpentGeoAnimatable.java");
	private static final Path MODEL_SOURCE = CLIENT.resolve("MegumiSerpentModel.java");
	private static final Path ANIMATION_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/animations/megumi_serpent.animation.json");
	private static final Path GEO_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/models/megumi_serpent.geo.json");
	private static final Path TEXTURE =
			Path.of("src/main/resources/assets/jujutsumod/textures/entity/megumi_serpent.png");

	private static final Set<String> CONTRACT_CLIPS = Set.of(
			"animation.megumi_serpent.bind",
			"animation.megumi_serpent.emerge",
			"animation.megumi_serpent.idle",
			"animation.megumi_serpent.release",
			"animation.megumi_serpent.slither",
			"animation.megumi_serpent.submerge",
			"animation.megumi_serpent.submerged_idle"
	);

	@Test
	void animatableClipIdsExistInTheShippedAnimationSet() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		Set<String> referenced = Pattern.compile("animation\\.megumi_serpent\\.[A-Za-z0-9_]+")
				.matcher(source)
				.results()
				.map(MatchResult::group)
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(CONTRACT_CLIPS, referenced,
				"The animatable must reference exactly the contracted Great Serpent clips");

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
		assertEquals("geometry.megumi_serpent", description.get("identifier").getAsString());
	}

	@Test
	void modelAndTextureResolveToShippedFiles() throws Exception {
		assertTrue(Files.isRegularFile(GEO_JSON), "Missing shipped Great Serpent geometry");
		assertTrue(Files.isRegularFile(ANIMATION_JSON), "Missing shipped Great Serpent animation set");
		assertTrue(Files.isRegularFile(TEXTURE), "Missing shipped Great Serpent texture");
		String model = Files.readString(MODEL_SOURCE);
		assertTrue(model.contains("textures/entity/megumi_serpent.png"),
				"The model must serve the shipped Great Serpent sheet");
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}
}
