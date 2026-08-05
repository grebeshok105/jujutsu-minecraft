package jujutsu.mod.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CharacterSkinAnimationPackTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/jujutsumod");
	private static final Set<String> CANONICAL_BONES = Set.of(
			"root", "body", "head", "leftArm", "left_elbow", "left_hand", "rightArm", "right_elbow",
			"right_hand", "leftLeg", "left_knee", "rightLeg", "right_knee");

	@Test
	void everySkinPackHasACompleteInvisibleRigAndUsableClips() throws Exception {
		List<Pack> packs = List.of(
				new Pack("nobara", "projectjjk/npc", Set.of(
						"animation.player_model.idle", "animation.player_model.idle2", "animation.player_model.walk",
						"animation.player_model.walk2", "animation.player_model.run", "animation.player_model.one_two",
						"animation.player_model.attack1", "animation.player_model.attack2", "animation.player_model.attack3",
						"animation.player_model.snap", "animation.player_model.spell1", "animation.player_model.spell2",
						"animation.player_model.spell3", "animation.player_model.spell4", "animation.player_model.spell5",
						"animation.player_model.swipe1", "animation.player_model.hammer_horizontal",
						"animation.player_model.hammer_overhead", "animation.player_model.hammer_nail_launch",
						"animation.player_model.hammer_embedded_drive", "animation.player_model.hammer_doll_strike",
						"animation.player_model.self_resonance", "animation.player_model.black_flash"),
						Set.of("animation.player_model.idle", "animation.player_model.idle2", "animation.player_model.walk",
								"animation.player_model.walk2", "animation.player_model.run")),
				new Pack("todo", "todo/todo_aoi", Set.of(
						"animation.todo_aoi.idle", "animation.todo_aoi.idle2", "animation.todo_aoi.walk",
						"animation.todo_aoi.walk2", "animation.todo_aoi.run", "animation.todo_aoi.attack",
						"ability.boogie_woogie"),
						Set.of("animation.todo_aoi.idle", "animation.todo_aoi.idle2", "animation.todo_aoi.walk",
								"animation.todo_aoi.walk2", "animation.todo_aoi.run")),
				new Pack("megumi", "megumi/megumi_fushiguro", Set.of(
						"animation.megumi_fushiguro.idle", "animation.megumi_fushiguro.walk",
						"animation.megumi_fushiguro.run", "animation.megumi_fushiguro.combat_idle",
						"animation.megumi_fushiguro.punch_1", "animation.megumi_fushiguro.punch_2",
						"animation.megumi_fushiguro.kick", "animation.megumi_fushiguro.summon_divine_dogs",
						"animation.megumi_fushiguro.shadow_dive", "animation.megumi_fushiguro.shadow_emerge"),
						Set.of("animation.megumi_fushiguro.idle", "animation.megumi_fushiguro.walk",
								"animation.megumi_fushiguro.run", "animation.megumi_fushiguro.combat_idle")));

		for (Pack pack : packs) {
			JsonObject rig = json(ASSETS.resolve("geckolib/models/character_skin/" + pack.id() + ".geo.json"));
			JsonArray bones = rig.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
			Set<String> names = bones.asList().stream()
					.map(JsonElement::getAsJsonObject)
					.map(bone -> bone.get("name").getAsString())
					.collect(java.util.stream.Collectors.toSet());
			assertEquals(CANONICAL_BONES, names, pack.id() + " canonical bones");
			for (JsonElement element : bones) {
				JsonObject bone = element.getAsJsonObject();
				assertEquals(3, bone.getAsJsonArray("pivot").size(), pack.id() + " pivot");
				assertEquals(3, bone.getAsJsonArray("rotation").size(), pack.id() + " rotation");
				assertFalse(bone.has("cubes"), pack.id() + " rig must stay invisible");
				assertFalse(bone.has("uv"), pack.id() + " rig must not define texture geometry");
			}

			JsonObject clips = json(ASSETS.resolve("geckolib/animations/" + pack.animationPath() + ".animation.json"))
					.getAsJsonObject("animations");
			assertEquals(pack.clips(), clips.keySet(), pack.id() + " clip set");
			for (String clipName : pack.clips()) {
				JsonObject clip = clips.getAsJsonObject(clipName);
				assertTrue(clip.get("animation_length").getAsDouble() > 0.0, pack.id() + " clip length: " + clipName);
				assertTrue(clip.has("bones") && !clip.getAsJsonObject("bones").isEmpty(),
						pack.id() + " clip must animate a bone: " + clipName);
			}
			for (String clipName : pack.loopingClips()) {
				assertTrue(clips.getAsJsonObject(clipName).get("loop").getAsBoolean(),
						pack.id() + " locomotion clip must loop: " + clipName);
			}
		}
	}

	@Test
	void controllersPreserveLocomotionAndActionTriggerNames() throws Exception {
		String nobara = read("src/client/java/jujutsu/mod/client/render/nobara/NobaraPlayerGeoAnimatable.java");
		assertTrue(nobara.contains("IDLE_2 = loop") && nobara.contains("WALK_2 = loop")
				&& nobara.contains("movement.running()") && nobara.contains("MELEE_VARIANT"));
		for (String trigger : List.of(
				"one_two", "attack1", "attack2", "attack3", "snap", "spell1", "spell2", "spell3", "spell4",
				"spell5", "swipe1", "hammer_horizontal", "hammer_overhead", "hammer_nail_launch",
				"hammer_embedded_drive", "hammer_doll_strike", "self_resonance", "black_flash")) {
			assertTrue(nobara.contains("triggerableAnim(\"" + trigger + "\""), "Nobara trigger: " + trigger);
		}

		String todo = read("src/client/java/jujutsu/mod/client/render/todo/TodoPlayerGeoAnimatable.java");
		assertTrue(todo.contains("RUN = loop") && todo.contains("movement.running()"));
		assertTrue(todo.contains("triggerableAnim(\"attack\", ATTACK)"));
		assertTrue(todo.contains("triggerableAnim(BOOGIE_WOOGIE_ANIM, BOOGIE_WOOGIE)"));

		String megumi = read("src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoAnimatable.java");
		assertTrue(megumi.contains("COMBAT_IDLE_ANIMATION = loop") && megumi.contains("MELEE_VARIANT_COUNT = 3")
				&& megumi.contains("triggerableAnim(SUMMON_ANIM, SUMMON)"));
	}

	@Test
	void abilityCuesReachTheCasterAnimationHooks() throws Exception {
		String nobaraRecipes = read("src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java");
		assertTrue(nobaraRecipes.contains("NobaraVfxIds.CASTER_ACTION")
				&& nobaraRecipes.contains("CASTER_HAIRPIN_DIRECTED")
				&& nobaraRecipes.contains("CASTER_MEGA_NAIL")
				&& nobaraRecipes.contains("CASTER_NAIL_TRAP")
				&& nobaraRecipes.contains("CASTER_HAMMER_EMBEDDED"));
		assertTrue(read("src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java")
				.contains("NobaraVfxIds.CASTER_ACTION"));
		assertTrue(read("src/main/java/jujutsu/mod/character/nobara/projectjjk/NailTrapRuntime.java")
				.contains("NobaraVfxIds.CASTER_ACTION"));
		assertTrue(read("src/main/java/jujutsu/mod/character/nobara/projectjjk/NobaraHammerCombatRuntime.java")
				.contains("NobaraVfxIds.CASTER_ACTION"));

		String todoHooks = read("src/client/java/jujutsu/mod/client/vfx/todo/TodoAnimationHooks.java");
		assertTrue(todoHooks.contains("TodoPlayerGeoAnimatable.INSTANCE.triggerAction")
				&& todoHooks.contains("BOOGIE_WOOGIE_ANIM"));
		String megumiRecipes = read("src/client/java/jujutsu/mod/client/vfx/megumi/MegumiVfxRecipes.java");
		assertTrue(megumiRecipes.contains("MegumiAnimationHooks.triggerDivineDogs(cue)"));
	}

	private static JsonObject json(Path path) throws Exception {
		assertTrue(Files.isRegularFile(path), "Missing animation resource: " + path);
		return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
	}

	private static String read(String relativePath) throws Exception {
		Path path = Path.of(relativePath);
		assertTrue(Files.isRegularFile(path), "Missing source contract: " + path);
		return Files.readString(path);
	}

	private record Pack(String id, String animationPath, Set<String> clips, Set<String> loopingClips) {}
}
