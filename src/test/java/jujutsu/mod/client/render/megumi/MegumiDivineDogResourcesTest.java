package jujutsu.mod.client.render.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 * Cross-artifact contract for the Dire Wolf Divine Dog visual: every animation id named in
 * client code must exist in the shipped animation set, and every bone the model hides must
 * exist in the shipped geometry. A clip rename or a geometry re-export that drops a tack
 * bone would otherwise fail silently (frozen pose, visible mount tack) with no game client
 * in the test run to notice.
 */
final class MegumiDivineDogResourcesTest {
	private static final Path CLIENT = Path.of("src/client/java/jujutsu/mod/client/render/megumi");
	private static final Path ANIMATABLE_SOURCE = CLIENT.resolve("MegumiDogGeoAnimatable.java");
	private static final Path MODEL_SOURCE = CLIENT.resolve("MegumiDivineDogModel.java");
	private static final Path ANIMATION_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/animations/megumi_divine_dog.animation.json");
	private static final Path GEO_JSON =
			Path.of("src/main/resources/assets/jujutsumod/geckolib/models/megumi_divine_dog.geo.json");
	private static final Path TEXTURES = Path.of("src/main/resources/assets/jujutsumod/textures/entity");

	private static final Set<String> CONTRACT_CLIPS = Set.of(
			"animation.megumi_divine_dog.idle",
			"animation.megumi_divine_dog.walk",
			"animation.megumi_divine_dog.sprint",
			"animation.megumi_divine_dog.attack",
			"animation.megumi_divine_dog.standup",
			"animation.megumi_divine_dog.sitdown");
	private static final Set<String> CONTRACT_HIDDEN_BONES = Set.of("saddle", "bridle", "chests");

	@Test
	void animatableClipIdsExistInTheShippedAnimationSet() throws Exception {
		String source = Files.readString(ANIMATABLE_SOURCE);
		Set<String> referenced = Pattern.compile("animation\\.megumi_divine_dog\\.[A-Za-z0-9_]+")
				.matcher(source)
				.results()
				.map(MatchResult::group)
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(CONTRACT_CLIPS, referenced,
				"The animatable must reference exactly the contracted Dire Wolf clips");

		JsonObject animations = json(ANIMATION_JSON).getAsJsonObject("animations");
		for (String clip : referenced) {
			assertTrue(animations.has(clip),
					"Client code plays " + clip + " but the shipped animation JSON has no such clip");
		}
	}

	@Test
	void hiddenTackBonesExistAndTheTorsoStaysVisible() throws Exception {
		String source = Files.readString(MODEL_SOURCE);
		Set<String> hidden = Pattern.compile("hide\\(\"([A-Za-z0-9_]+)\"\\)")
				.matcher(source)
				.results()
				.map(match -> match.group(1))
				.collect(Collectors.toCollection(TreeSet::new));
		assertEquals(CONTRACT_HIDDEN_BONES, hidden,
				"The model must hide exactly the imported mount tack, never the torso");

		Set<String> bones = collectBones(json(GEO_JSON));
		for (String bone : hidden) {
			assertTrue(bones.contains(bone),
					"The model hides bone " + bone + " but the shipped geometry has no such bone");
		}
		assertTrue(bones.contains("chest"), "The Dire Wolf torso bone must exist and stay visible");
	}

	@Test
	void modelAnimationAndVariantTexturesResolveToShippedFiles() throws Exception {
		assertTrue(Files.isRegularFile(GEO_JSON), "Missing shipped Dire Wolf geometry");
		assertTrue(Files.isRegularFile(ANIMATION_JSON), "Missing shipped Dire Wolf animation set");
		assertTrue(Files.isRegularFile(TEXTURES.resolve("megumi_divine_dog_white.png")),
				"Missing white-variant Dire Wolf texture");
		assertTrue(Files.isRegularFile(TEXTURES.resolve("megumi_divine_dog_black.png")),
				"Missing black-variant Dire Wolf texture");
	}

	@Test
	void variantSelectsItsShippedTextureSheet() {
		assertEquals("jujutsumod", MegumiDivineDogTextures.WHITE.getNamespace());
		assertEquals("textures/entity/megumi_divine_dog_white.png", MegumiDivineDogTextures.forVariant(false).getPath(),
				"Every variant except black stays on the default light sheet");
		assertEquals("textures/entity/megumi_divine_dog_black.png", MegumiDivineDogTextures.forVariant(true).getPath(),
				"The black wolf variant must serve the dark Dire Wolf sheet");
	}

	private static JsonObject json(Path path) throws Exception {
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static Set<String> collectBones(JsonObject geo) {
		Set<String> bones = new TreeSet<>();
		for (JsonElement geometry : geo.getAsJsonArray("minecraft:geometry")) {
			collectBones(geometry.getAsJsonObject().getAsJsonArray("bones"), bones);
		}
		return bones;
	}

	private static void collectBones(JsonArray boneArray, Set<String> bones) {
		for (JsonElement element : boneArray) {
			JsonObject bone = element.getAsJsonObject();
			bones.add(bone.get("name").getAsString());
			JsonElement children = bone.get("children");
			if (children != null && children.isJsonArray()) {
				JsonArray nested = new JsonArray();
				for (JsonElement child : children.getAsJsonArray()) {
					if (child.isJsonObject()) {
						nested.add(child);
					}
				}
				collectBones(nested, bones);
			}
		}
	}
}
