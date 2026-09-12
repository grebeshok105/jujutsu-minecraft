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
 * Cross-artifact contract for the imported Toad visual: every animation id named in client code
 * must exist in the shipped animation set, and the tongue must be a one-shot — a looping tongue
 * would never finish and never release the jaw.
 */
final class MegumiToadResourcesTest {
	private static final Path CLIENT = Path.of("src/client/java/jujutsu/mod/client/render/megumi");
	private static final Path ANIMATABLE_SOURCE = CLIENT.resolve("MegumiToadGeoAnimatable.java");
	private static final Path MODEL_SOURCE = CLIENT.resolve("MegumiToadModel.java");
	private static final Path ANIMATION_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/animations/megumi_toad.animation.json");
	private static final Path GEO_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/models/megumi_toad.geo.json");
	private static final Path TEXTURE =
			Path.of("src/main/resources/assets/jujutsumod/textures/entity/megumi_toad.png");

	private static final Set<String> CONTRACT_CLIPS = Set.of(
			"animation.megumi_toad.walk",
			"animation.megumi_toad.attack",
			"animation.megumi_toad.tongue");

	@Test
	void animatableClipIdsExistInTheShippedAnimationSet() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		Set<String> referenced = Pattern.compile("animation\\.megumi_toad\\.[A-Za-z0-9_]+")
				.matcher(source)
				.results()
				.map(MatchResult::group)
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(CONTRACT_CLIPS, referenced,
				"The animatable must reference exactly the contracted Toad clips (walk/attack/tongue; howl stays unused)");

		JsonObject animations = JsonParser.parseString(Files.readString(ANIMATION_JSON))
				.getAsJsonObject().getAsJsonObject("animations");
		for (String clip : referenced) {
			assertTrue(animations.has(clip),
					"Client code plays " + clip + " but the shipped animation JSON has no such clip");
		}
		assertTrue(animations.has("animation.megumi_toad.howl"),
				"The unused howl clip must survive the import untouched");
	}

	@Test
	void geometryIdentifierAndTextureResolveToShippedFiles() throws Exception {
		assertTrue(Files.isRegularFile(GEO_JSON), "Missing shipped Toad geometry");
		assertTrue(Files.isRegularFile(ANIMATION_JSON), "Missing shipped Toad animation set");
		assertTrue(Files.isRegularFile(TEXTURE), "Missing shipped Toad texture");
		JsonObject description = JsonParser.parseString(Files.readString(GEO_JSON))
				.getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0)
				.getAsJsonObject().getAsJsonObject("description");
		assertEquals("geometry.megumi_toad", description.get("identifier").getAsString());
		String modelSource = Files.readString(MODEL_SOURCE);
		assertTrue(modelSource.contains("textures/entity/megumi_toad.png"),
				"The model must serve the shipped Toad sheet");
	}

	@Test
	void theTongueRidesTheBaseControllerAsAOneShotClip() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		assertTrue(source.contains("thenPlay(\"animation.megumi_toad.tongue\")"),
				"The tongue clip must be a one-shot: a looping tongue would never finish and never release the jaw");
		assertFalse(source.contains("thenLoop(\"animation.megumi_toad.tongue\")"),
				"The tongue must not be looped: a looping tongue clip never finishes and never releases the jaw");
	}

	@Test
	void theImportedAnimationSetCarriesTheGeckolibFormatVersion() throws Exception {
		JsonObject root = JsonParser.parseString(Files.readString(ANIMATION_JSON)).getAsJsonObject();
		assertTrue(root.has("geckolib_format_version"), "The Toad set must stamp geckolib_format_version");
		assertEquals(2, root.get("geckolib_format_version").getAsInt());
	}
}
