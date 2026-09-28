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
	private static final Path MODEL = ASSETS.resolve("geckolib/models/character_skin/megumi.geo.json");
	private static final Path ANIMATIONS = ASSETS.resolve("geckolib/animations/megumi/megumi_fushiguro.animation.json");
	private static final Path PLAYER_SKIN = ASSETS.resolve("textures/entity/character/megumi.png");
	private static final Path ANIMATABLE_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoAnimatable.java");
	private static final Path RENDERER_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiSkinAnimationAdapter.java");
	private static final Path DIVINE_DOG_RENDERER_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiDivineDogRenderer.java");
	private static final Path DIVINE_DOG_RENDER_STATE_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiDivineDogRenderState.java");
	private static final Path MODEL_SOURCE = Path.of(
			"src/client/java/jujutsu/mod/client/render/megumi/MegumiSkinAnimationModel.java");
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
				"root", "body", "head", "rightArm", "leftArm", "rightLeg", "leftLeg",
				"right_elbow", "left_elbow", "right_hand", "left_hand")),
				"Megumi's invisible rig must expose every bone used by the skin animation bridge");
		assertTrue(!Files.readString(MODEL).contains("\"cubes\"")
				&& !Files.readString(MODEL).contains("\"uv\""),
				"Megumi's skin animation rig must not carry visible Blockbench geometry");

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
				"animation.megumi_fushiguro.summon_divine_dogs",
				"animation.megumi_fushiguro.summon_serpent",
				"animation.megumi_fushiguro.summon_deer",
				"animation.megumi_fushiguro.summon_ox",
				"animation.megumi_fushiguro.summon_tiger",
				"animation.megumi_fushiguro.shadow_dive",
				"animation.megumi_fushiguro.shadow_emerge"), clips.keySet());
	}

	@Test
	void playerSkinUsesTheStandardLayout() throws Exception {
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

		String adapter = Files.readString(RENDERER_SOURCE);
		assertTrue(adapter.contains("WeakHashMap<AbstractClientPlayer, SwingState>"),
				"Melee sequence state must be isolated per rendered player and released with that player");
		assertTrue(adapter.contains("player.swingTime < state.lastSwingTime"),
				"A restarted swing must advance even when vanilla keeps its swinging flag set");
	}

	@Test
	void clientDefinitionOwnsTheBodyAndConfirmedSummonTriggersItsClip() throws Exception {
		String definition = Files.readString(CLIENT_DEFINITION_SOURCE);
		assertTrue(definition.contains("new MegumiSkinAnimationAdapter()")
				&& definition.contains("skinAnimation()"),
				"Megumi's client definition must opt into the skin animation bridge");
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
	void clientDefinitionOwnsADedicatedDogRendererSeam() throws Exception {
		String definition = Files.readString(CLIENT_DEFINITION_SOURCE);
		assertTrue(definition.contains("MegumiDivineDogRenderer::new"),
				"Only Megumi's client definition may register his dedicated Divine Dog renderer");

		String renderer = Files.readString(DIVINE_DOG_RENDERER_SOURCE);
		assertTrue(renderer.contains(
				"GeoReplacedEntityRenderer<MegumiDogGeoAnimatable, MegumiDivineDogEntity, MegumiDivineDogRenderState>"),
				"The dedicated Divine Dog renderer is the GeckoLib Dire Wolf path, not vanilla wolf rendering");
	}

	@Test
	void divineDogRendererConsumesSynchronizedPhaseThroughVerticalTranslationOnly() throws Exception {
		String state = Files.readString(DIVINE_DOG_RENDER_STATE_SOURCE);
		assertTrue(state.contains("MegumiDogPresentationPolicy.Phase phase"));
		assertTrue(state.contains("float progress"));
		assertTrue(state.contains("float verticalOffset"));

		String renderer = Files.readString(DIVINE_DOG_RENDERER_SOURCE);
		assertTrue(renderer.contains("entity.presentationPhase()"));
		assertTrue(renderer.contains("entity.presentationTicks()"));
		assertTrue(renderer.contains("MegumiDogPresentationPolicy.progress("));
		assertTrue(renderer.contains("MegumiDogPresentationPolicy.verticalOffset("));
		assertTrue(renderer.contains("poseStack.translate(0.0f, renderState.verticalOffset, 0.0f)"),
				"Presentation must reach the frame as a vertical pose translation");
		assertTrue(!renderer.contains(".setPos("),
				"Presentation must never move the authoritative entity position");
	}

	@Test
	void divineDogRendererFeedsTheSyncedVariantAndSwingIntoTheRenderState() throws Exception {
		String body = methodBody(Files.readString(DIVINE_DOG_RENDERER_SOURCE), "public void extractRenderState(");
		assertTrue(body.contains("DataComponents.WOLF_VARIANT"),
				"The texture choice must read the synchronized wolf variant, not a client guess");
		assertTrue(body.contains("WolfVariants.BLACK"), "Only the black wolf variant may leave the default sheet");
		assertTrue(body.contains("entity.getAttackAnim(partialTick)"),
				"The attack clip must be driven by the entity's own swing progress");

		String model = Files.readString(Path.of(
				"src/client/java/jujutsu/mod/client/render/megumi/MegumiDivineDogModel.java"));
		assertTrue(model.contains("MegumiDivineDogTextures.forVariant("),
				"The model must route the synced variant through the shipped Dire Wolf texture set");
	}

	@Test
	void modelUsesHorizontalOnlyScaleAndKeepsItsHeadFacingForward() throws Exception {
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
