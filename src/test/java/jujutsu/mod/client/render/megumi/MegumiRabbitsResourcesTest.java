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
 * Cross-artifact contract for the Rabbit Escape visual: every animation id named in client code
 * must exist in the shipped animation set with a positive length, and the swing must stay a
 * one-shot on its own controller. A clip rename or a re-export that loops the swing would
 * otherwise fail silently (frozen hop, endless flail) with no game client in the test run to
 * notice.
 */
final class MegumiRabbitsResourcesTest {
	private static final Path CLIENT = Path.of("src/client/java/jujutsu/mod/client/render/megumi");
	private static final Path ANIMATABLE_SOURCE = CLIENT.resolve("MegumiRabbitsGeoAnimatable.java");
	private static final Path MODEL_SOURCE = CLIENT.resolve("MegumiRabbitModel.java");
	private static final Path ANIMATION_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/animations/megumi_rabbit.animation.json");
	private static final Path GEO_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/models/megumi_rabbit.geo.json");
	private static final Path TEXTURE =
			Path.of("src/main/resources/assets/jujutsumod/textures/entity/megumi_rabbit.png");

	private static final Set<String> CONTRACT_CLIPS = Set.of(
			"animation.megumi_rabbit.walk",
			"animation.megumi_rabbit.run",
			"animation.megumi_rabbit.attack");

	@Test
	void animatableClipIdsExistInTheShippedAnimationSet() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		Set<String> referenced = Pattern.compile("animation\\.megumi_rabbit\\.[A-Za-z0-9_]+")
				.matcher(source)
				.results()
				.map(MatchResult::group)
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(CONTRACT_CLIPS, referenced,
				"The animatable must reference exactly the contracted Rabbit Escape clips");

		JsonObject animations = json(ANIMATION_JSON).getAsJsonObject("animations");
		for (String clip : referenced) {
			assertTrue(animations.has(clip),
					"Client code plays " + clip + " but the shipped animation JSON has no such clip");
			assertTrue(animations.getAsJsonObject(clip).has("animation_length"),
					"Clip " + clip + " ships without animation_length");
			assertTrue(animations.getAsJsonObject(clip).get("animation_length").getAsDouble() > 0.0,
					"Clip " + clip + " must carry a positive animation_length");
		}
	}

	@Test
	void geoIdentifierAndFormatVersionMatchTheLoaderContract() throws Exception {
		JsonObject description = json(GEO_JSON)
				.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
				.getAsJsonObject("description");
		assertEquals("geometry.megumi_rabbit", description.get("identifier").getAsString());
		assertEquals(2, json(ANIMATION_JSON).get("geckolib_format_version").getAsInt());
	}

	@Test
	void modelAndTextureResolveToShippedFiles() throws Exception {
		assertTrue(Files.isRegularFile(GEO_JSON), "Missing shipped Rabbit Escape geometry");
		assertTrue(Files.isRegularFile(ANIMATION_JSON), "Missing shipped Rabbit Escape animation set");
		assertTrue(Files.isRegularFile(TEXTURE), "Missing shipped Rabbit Escape texture");
		assertTrue(Files.readString(MODEL_SOURCE).contains("textures/entity/megumi_rabbit.png"),
				"The model must serve the shipped Rabbit Escape sheet");
	}

	@Test
	void theSwingRidesItsOwnControllerAsAOneShotClip() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		assertTrue(source.contains("thenPlay(\"animation.megumi_rabbit.attack\")"),
				"The swing clip must be a one-shot: a looping swing would never finish");
		assertTrue(source.contains("megumi_rabbits_action"),
				"The swing needs its own controller, or its clip would freeze the hop cycle while it plays");
		assertFalse(source.contains("thenLoop(\"animation.megumi_rabbit.attack\")"),
				"The swing must not be looped: a looping swing never finishes");
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}
}
