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
 * plus the head swing ride the action layer as forced one-shot holds (their JSON
 * {@code loop:true} never governs {@code RawAnimation}). Both clips animate a fraction of the
 * body — trunk alone, head alone — so either one on the base controller would freeze the legs
 * for the whole jet or swing.
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
	void theJetAndTheSwingRideTheActionControllerAsForcedOneShotHolds() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		assertTrue(source.contains("thenPlay(\"animation.megumi_max_elephant.shoot\")"),
				"The jet must be a one-shot: a looping shoot would never finish and never release the trunk");
		assertFalse(source.contains("thenLoop(\"animation.megumi_max_elephant.shoot\")"),
				"The JSON loop flag must not leak into playback: the hold re-arms the one-shot while the jet is live");
		assertTrue(source.contains("thenPlay(\"animation.megumi_max_elephant.attack\")"),
				"The melee swing keeps its own one-shot for when no jet is running");
		assertTrue(source.contains("\"megumi_elephant_action\""),
				"The jet and the swing need their own action controller, or either clip would freeze the legs while it plays");
		String baseArm = methodBody(source, "baseAnimation");
		assertFalse(baseArm.contains("SHOOT") || baseArm.contains("ATTACK"),
				"The base controller must never reference the action clips: it owns locomotion only");
		String actionArm = methodBody(source, "actionAnimation");
		assertTrue(actionArm.contains("SHOOT") && actionArm.contains("ATTACK"),
				"The action layer must play both the jet and the swing");
		assertTrue(source.indexOf("this::baseAnimation") < source.indexOf("this::actionAnimation"),
				"The base controller registers first so the action layer wins the bones they share");
	}

	@Test
	void theActionClipsTouchNoLegTheWalkCycleUses() throws Exception {
		JsonObject animations = json(ANIMATION_JSON).getAsJsonObject("animations");
		Set<String> legs = Set.of(
				"left_front_leg", "right_front_leg", "left_back_leg", "right_back_leg");
		for (String clip : Set.of(
				"animation.megumi_max_elephant.shoot", "animation.megumi_max_elephant.attack")) {
			Set<String> bones = new TreeSet<>();
			animations.getAsJsonObject(clip).getAsJsonObject("bones").keySet().forEach(bones::add);
			Set<String> frozen = new TreeSet<>(bones);
			frozen.retainAll(legs);
			assertTrue(frozen.isEmpty(),
					clip + " overlays the walk cycle, which is only safe while it leaves the legs alone: " + bones);
		}
		Set<String> walk = new TreeSet<>();
		animations.getAsJsonObject("animation.megumi_max_elephant.walk")
				.getAsJsonObject("bones").keySet().forEach(walk::add);
		assertTrue(walk.containsAll(legs),
				"The walk cycle underneath must actually drive the legs: " + walk);
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
}
