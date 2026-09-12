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
 * Cross-artifact contract for the imported Nue visual: every animation id named in client code
 * must exist in the shipped animation set with a positive length, the impact swing must stay a
 * whole-body one-shot on the base controller, and the feet clips must stay looped overlays on
 * their own layer — legs and talons alone, so the wings keep beating through fast travel and
 * the dive. Either feet clip back on the base controller would freeze the wings for the whole
 * dive.
 */
final class MegumiNueResourcesTest {
	private static final Path CLIENT = Path.of("src/client/java/jujutsu/mod/client/render/megumi");
	private static final Path ANIMATABLE_SOURCE = CLIENT.resolve("MegumiNueGeoAnimatable.java");
	private static final Path MODEL_SOURCE = CLIENT.resolve("MegumiNueModel.java");
	private static final Path ANIMATION_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/animations/megumi_nue.animation.json");
	private static final Path GEO_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/models/megumi_nue.geo.json");
	private static final Path TEXTURE =
			Path.of("src/main/resources/assets/jujutsumod/textures/entity/megumi_nue.png");

	private static final Set<String> CONTRACT_CLIPS = Set.of(
			"animation.megumi_nue.idle",
			"animation.megumi_nue.fly",
			"animation.megumi_nue.flight_feet",
			"animation.megumi_nue.grab_feet",
			"animation.megumi_nue.attack");

	@Test
	void animatableClipIdsExistInTheShippedAnimationSet() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		Set<String> referenced = Pattern.compile("animation\\.megumi_nue\\.[A-Za-z0-9_]+")
				.matcher(source)
				.results()
				.map(MatchResult::group)
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(CONTRACT_CLIPS, referenced,
				"The animatable must reference exactly the contracted Nue clips");

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
	void geometryKeepsItsUpstreamIdentifier() throws Exception {
		JsonObject description = json(GEO_JSON).getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject().getAsJsonObject("description");
		assertEquals("geometry.megumi_nue", description.get("identifier").getAsString());
		assertEquals(2, json(ANIMATION_JSON).get("geckolib_format_version").getAsInt());
	}

	@Test
	void modelAndTextureResolveToShippedFiles() throws Exception {
		assertTrue(Files.isRegularFile(GEO_JSON), "Missing shipped Nue geometry");
		assertTrue(Files.isRegularFile(ANIMATION_JSON), "Missing shipped Nue animation set");
		assertTrue(Files.isRegularFile(TEXTURE), "Missing shipped Nue texture");
		String model = Files.readString(MODEL_SOURCE);
		assertTrue(model.contains("textures/entity/megumi_nue.png"),
				"The model must serve the shipped Nue sheet");
	}

	@Test
	void theFeetRideTheFeetLayerWhileTheImpactStaysABaseOneShot() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		assertTrue(source.contains("\"megumi_nue_feet\""),
				"The feet need their own layer, or their clips would freeze the wings while they play");
		String baseArm = methodBody(source, "baseAnimation");
		assertFalse(baseArm.contains("FLIGHT_FEET") || baseArm.contains("GRAB_FEET"),
				"The base controller must never reference the feet clips: it owns whole-body clips only");
		String feetArm = methodBody(source, "feetAnimation");
		assertTrue(feetArm.contains("FLIGHT_FEET") && feetArm.contains("GRAB_FEET"),
				"The feet layer must play both feet clips");
		assertTrue(source.indexOf("this::baseAnimation") < source.indexOf("this::feetAnimation"),
				"The base controller registers first so the feet layer wins the legs they share");
		assertTrue(source.contains("thenPlay(\"animation.megumi_nue.attack\")"),
				"The impact swing must be a one-shot: a looping impact would never finish and never release the dive pose");
		assertFalse(source.contains("thenLoop(\"animation.megumi_nue.attack\")"),
				"The impact swing must not be looped: a looping impact never finishes");
	}

	@Test
	void theFeetClipsTouchNoWingTheFlightCycleBeats() throws Exception {
		JsonObject animations = json(ANIMATION_JSON).getAsJsonObject("animations");
		Set<String> wings = Set.of("right_wing", "right_wing_tip", "left_wing", "left_wing_tip");
		for (String clip : Set.of("animation.megumi_nue.flight_feet", "animation.megumi_nue.grab_feet")) {
			Set<String> bones = boneNames(animations, clip);
			Set<String> overlap = new TreeSet<>(bones);
			overlap.retainAll(wings);
			assertTrue(overlap.isEmpty(),
					clip + " overlays the flight cycle, which is only safe while it leaves the wings alone: " + bones);
		}
		Set<String> impact = boneNames(animations, "animation.megumi_nue.attack");
		Set<String> flight = boneNames(animations, "animation.megumi_nue.fly");
		assertEquals(flight, impact,
				"The impact stays on the base controller on purpose: it animates the same whole body as the flight cycle");
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static String methodBody(String source, String method) {
		int start = source.indexOf(" " + method + "(");
		int open = source.indexOf('{', start);
		int depth = 0;
		for (int i = open; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(open, i + 1);
				}
			}
		}
		throw new IllegalArgumentException("No body found for " + method);
	}

	private static Set<String> boneNames(JsonObject animations, String clip) {
		Set<String> bones = new TreeSet<>();
		animations.getAsJsonObject(clip).getAsJsonObject("bones").keySet().forEach(bones::add);
		return bones;
	}
}
