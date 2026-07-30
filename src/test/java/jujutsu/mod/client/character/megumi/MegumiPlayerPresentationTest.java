package jujutsu.mod.client.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MegumiPlayerPresentationTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/jujutsumod");
	private static final Path MODEL = ASSETS.resolve("geckolib/models/megumi/megumi_fushiguro.geo.json");
	private static final Path ANIMATIONS = ASSETS.resolve("geckolib/animations/megumi/megumi_fushiguro.animation.json");
	private static final Path MODEL_TEXTURE = ASSETS.resolve("textures/entity/character/megumi_fushiguro.png");
	private static final Path PLAYER_SKIN = ASSETS.resolve("textures/entity/character/megumi.png");
	private static final Path ANIMATABLE_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoAnimatable.java");
	private static final Path RENDERER_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoRenderer.java");
	private static final Path DIVINE_DOG_RENDERER_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiDivineDogRenderer.java");
	private static final Path DIVINE_DOG_RENDER_STATE_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiDivineDogRenderState.java");
	private static final Path MODEL_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoModel.java");
	private static final Path CLIENT_DEFINITION_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java");
	private static final Path SUMMON_RUNTIME_SOURCE = Path.of(
			"src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java");
	private static final Path VFX_RECIPES_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/vfx/megumi/MegumiVfxRecipes.java");

	@Test
	void runtimeAssetsExposeTheApprovedRigAndClips() throws Exception {
		assertTrue(Files.isRegularFile(MODEL), "Megumi's exported GeckoLib model is missing");
		assertTrue(Files.isRegularFile(ANIMATIONS), "Megumi's exported GeckoLib animations are missing");

		JsonObject geometry = JsonParser.parseString(Files.readString(MODEL)).getAsJsonObject();
		JsonArray bones = geometry.getAsJsonArray("minecraft:geometry")
				.get(0).getAsJsonObject().getAsJsonArray("bones");
		Set<String> boneNames = new HashSet<>();
		bones.forEach(bone -> boneNames.add(bone.getAsJsonObject().get("name").getAsString()));
		assertTrue(boneNames.containsAll(Set.of(
				"head", "rightArm", "leftArm", "right_elbow", "left_elbow", "right_hand", "left_hand")),
				"Megumi's model must expose every bone used by the shared player renderer");

		JsonObject clips = JsonParser.parseString(Files.readString(ANIMATIONS)).getAsJsonObject()
				.getAsJsonObject("animations");
		assertEquals(Set.of(
				"animation.megumi_fushiguro.idle",
				"animation.megumi_fushiguro.walk",
				"animation.megumi_fushiguro.run",
				"animation.megumi_fushiguro.combat_idle",
				"animation.megumi_fushiguro.punch_1",
				"animation.megumi_fushiguro.punch_2",
				"animation.megumi_fushiguro.kick",
				"animation.megumi_fushiguro.summon_divine_dogs"), clips.keySet());
	}

	@Test
	void modelAtlasAndPlayerSkinKeepTheirDistinctLayouts() throws Exception {
		assertDimensions(MODEL_TEXTURE, 128, 128);
		assertDimensions(PLAYER_SKIN, 64, 64);
	}

	@Test
	void ordinarySwingsCycleThroughTheThreeApprovedClipsPerPlayer() throws Exception {
		String animatable = Files.readString(ANIMATABLE_SOURCE);
		int punchOne = animatable.indexOf("animation.megumi_fushiguro.punch_1");
		int punchTwo = animatable.indexOf("animation.megumi_fushiguro.punch_2");
		int kick = animatable.indexOf("animation.megumi_fushiguro.kick");
		assertTrue(punchOne >= 0 && punchOne < punchTwo && punchTwo < kick,
				"Megumi's ordinary melee route must stay punch_1 -> punch_2 -> kick");

		String renderer = Files.readString(RENDERER_SOURCE);
		assertTrue(renderer.contains("WeakHashMap<AbstractClientPlayer, SwingState>"),
				"Melee sequence state must be isolated per rendered player and released with that player");
		assertTrue(renderer.contains("player.swingTime < state.lastSwingTime"),
				"A restarted swing must advance even when vanilla keeps its swinging flag set");
	}

	@Test
	void clientDefinitionOwnsTheBodyAndConfirmedSummonTriggersItsClip() throws Exception {
		String definition = Files.readString(CLIENT_DEFINITION_SOURCE);
		assertTrue(definition.contains("new MegumiPlayerGeoRenderer<>(context)"),
				"Megumi's client definition must opt into his replaced-player renderer");
		assertTrue(definition.contains("textures/entity/character/megumi.png"),
				"The vanilla-layout skin must own first-person hands and the roster portrait");

		String runtime = Files.readString(SUMMON_RUNTIME_SOURCE);
		assertTrue(runtime.contains("MegumiVfxIds.DOGS_SUMMON_BODY, player.position(), player.getId(), Vec3.ZERO"),
				"A confirmed summon cue must identify the caster whose GeckoLib clip should play");
		String recipes = Files.readString(VFX_RECIPES_SOURCE);
		assertTrue(recipes.contains("MegumiAnimationHooks.triggerDivineDogs(cue)"),
				"The existing summon recipe must trigger the model animation without a new receiver");
	}

	@Test
	void clientDefinitionOwnsADedicatedVanillaDogRendererSeam() throws Exception {
		String definition = Files.readString(CLIENT_DEFINITION_SOURCE);
		assertTrue(definition.contains("MegumiDivineDogRenderer::new"),
				"Only Megumi's client definition may register his dedicated Divine Dog renderer");

		String renderer = Files.readString(DIVINE_DOG_RENDERER_SOURCE);
		assertTrue(renderer.contains("extends WolfRenderer"),
				"The dedicated Divine Dog renderer must retain vanilla wolf rendering until its later presentation pass");
	}

	@Test
	void divineDogRendererConsumesSynchronizedPhaseThroughVerticalTranslationOnly() throws Exception {
		String state = Files.readString(DIVINE_DOG_RENDER_STATE_SOURCE);
		assertTrue(state.contains("extends WolfRenderState"));
		assertTrue(state.contains("MegumiDogPresentationPolicy.Phase phase"));
		assertTrue(state.contains("float progress"));
		assertTrue(state.contains("float verticalOffset"));

		String renderer = Files.readString(DIVINE_DOG_RENDERER_SOURCE);
		assertTrue(renderer.contains("dog.presentationPhase()"));
		assertTrue(renderer.contains("dog.presentationTicks()"));
		assertTrue(renderer.contains("MegumiDogPresentationPolicy.progress("));
		assertTrue(renderer.contains("MegumiDogPresentationPolicy.verticalOffset("));
		assertTrue(renderer.contains("matrices.translate(0.0f, dogState.verticalOffset, 0.0f)"));
		assertTrue(renderer.contains("super.render(state, matrices, consumers, packedLight)"),
				"The custom seam must preserve vanilla wolf models, layers, variants and textures");
	}

	@Test
	void modelUsesHorizontalOnlyScaleAndKeepsItsHeadFacingForward() throws Exception {
		String renderer = Files.readString(RENDERER_SOURCE);
		assertTrue(renderer.contains("withScale(1.25f, 1.0f)"),
				"Megumi must gain width without changing his rendered height");

		String headLookWeight = methodBody(Files.readString(MODEL_SOURCE), "protected float headLookWeight");
		assertTrue(headLookWeight.contains("return 0.0f;"),
				"Megumi must opt out of the shared procedural head-look rotation");
	}

	private static String methodBody(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, () -> "Missing method: " + signature);
		int end = source.indexOf("\n\t}", start);
		assertTrue(end >= 0, () -> "Unterminated method: " + signature);
		return source.substring(start, end);
	}

	private static void assertDimensions(Path path, int width, int height) throws Exception {
		assertTrue(Files.isRegularFile(path), () -> "Missing texture: " + path);
		BufferedImage image = ImageIO.read(path.toFile());
		assertEquals(width, image.getWidth(), path + " width");
		assertEquals(height, image.getHeight(), path + " height");
	}
}
