package jujutsu.mod.client.render.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Cross-artifact contract for the Max Elephant visual: every animation id named in client code
 * must exist in the shipped set, the geometry keeps its upstream identifier, and the trunk jet
 * rides the base controller as a forced one-shot hold (its JSON {@code loop:true} never governs
 * {@code RawAnimation}).
 */
final class MegumiElephantResourcesTest {
	private static final Path CLIENT = Path.of("src/client/java/jujutsu/mod/client/render/megumi");
	private static final Path ANIMATABLE_SOURCE = CLIENT.resolve("MegumiElephantGeoAnimatable.java");
	private static final Path MODEL_SOURCE = CLIENT.resolve("MegumiElephantModel.java");
	private static final Path ANIMATION_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/animations/megumi_max_elephant.animation.json");
	private static final Path GEO_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/models/megumi_max_elephant.geo.json");
	private static final Path TEXTURE =
			Path.of("src/main/resources/assets/jujutsumod/textures/entity/megumi_max_elephant.png");

	private static final Set<String> CONTRACT_CLIPS = Set.of(
			"animation.megumi_max_elephant.shoot",
			"animation.megumi_max_elephant.walk",
			"animation.megumi_max_elephant.run",
			"animation.megumi_max_elephant.idle",
			"animation.megumi_max_elephant.attack");

	@Test
	void animatableClipIdsExistInTheShippedAnimationSet() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		Set<String> referenced = Pattern.compile("animation\\.megumi_max_elephant\\.[A-Za-z0-9_]+")
				.matcher(source)
				.results()
				.map(MatchResult::group)
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(CONTRACT_CLIPS, referenced,
				"The animatable must reference exactly the contracted Max Elephant clips");

		JsonObject animations = json(ANIMATION_JSON).getAsJsonObject("animations");
		for (String clip : referenced) {
			assertTrue(animations.has(clip),
					"Client code plays " + clip + " but the shipped animation JSON has no such clip");
		}
	}

	@Test
	void geometryKeepsItsUpstreamIdentifier() throws Exception {
		JsonObject description = json(GEO_JSON).getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject().getAsJsonObject("description");
		assertEquals("geometry.megumi_max_elephant", description.get("identifier").getAsString());
	}

	@Test
	void modelAndTextureResolveToShippedFiles() throws Exception {
		assertTrue(Files.isRegularFile(GEO_JSON), "Missing shipped Max Elephant geometry");
		assertTrue(Files.isRegularFile(ANIMATION_JSON), "Missing shipped Max Elephant animation set");
		assertTrue(Files.isRegularFile(TEXTURE), "Missing shipped Max Elephant texture");
		String model = Files.readString(MODEL_SOURCE);
		assertTrue(model.contains("textures/entity/megumi_max_elephant.png"),
				"The model must serve the shipped Max Elephant sheet");
	}

	@Test
	void theJetRidesTheBaseControllerAsAForcedOneShotHold() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		assertTrue(source.contains("thenPlay(\"animation.megumi_max_elephant.shoot\")"),
				"The jet must be a one-shot: a looping shoot would never finish and never release the body");
		assertFalse(source.contains("thenLoop(\"animation.megumi_max_elephant.shoot\")"),
				"The JSON loop flag must not leak into playback: the hold re-arms the one-shot while the jet is live");
		assertTrue(source.contains("thenPlay(\"animation.megumi_max_elephant.attack\")"),
				"The melee swing keeps its own one-shot for when no jet is running");
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}
}
