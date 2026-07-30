package jujutsu.mod.client.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MegumiShadowPresentationTest {
	private static final Path SUMMON_RUNTIME = Path.of(
			"src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java");
	private static final Path RECIPES = Path.of(
			"src/client/java/jujutsu/mod/client/vfx/megumi/MegumiVfxRecipes.java");
	private static final Path PARTICLES = Path.of("src/main/java/jujutsu/mod/registry/JujutsuParticles.java");
	private static final Path CLIENT_DEFINITION = Path.of(
			"src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java");
	private static final Path MOTE = Path.of(
			"src/client/java/jujutsu/mod/client/character/megumi/particle/MegumiShadowMoteParticle.java");
	private static final Path MOTE_JSON = Path.of(
			"src/main/resources/assets/jujutsumod/particles/megumi_shadow_mote.json");
	private static final Path REUSED_SPRITE = Path.of(
			"src/main/resources/assets/jujutsumod/textures/particle/hairpin_spark.png");

	@Test
	void ownerCueTriggersTheBodyOnceWhileDogCuesOwnGroundPresentation() throws Exception {
		String runtime = Files.readString(SUMMON_RUNTIME);
		assertTrue(runtime.contains(
				"MegumiVfxIds.DOGS_SUMMON_BODY, player.position(), player.getId(), Vec3.ZERO"),
				"The original owner-anchored summon cue must keep driving Megumi's body clip");
		assertEquals(2, occurrences(runtime,
				"broadcastDogCue(level, player, MegumiVfxIds.DOGS_SUMMON"),
				"Each Divine Dog needs its own authoritative spawn-origin cue");

		String recipes = Files.readString(RECIPES);
		assertTrue(recipes.contains("VfxDirector.register(MegumiVfxIds.DOGS_SUMMON_BODY, MegumiVfxRecipes::summonBody)"),
				"The owner body cue must have a distinct identity from the dog pool cue");
		String summon = recipes.substring(recipes.indexOf("private static VfxInstance summon("),
				recipes.indexOf("private static VfxInstance summonBody"));
		assertTrue(!summon.contains("anchorEntityId"),
				"A dog pool must never depend on client-side anchor resolution");
		assertTrue(recipes.contains("MegumiAnimationHooks.triggerDivineDogs(cue)"));
		assertTrue(recipes.contains("anchor == context.client().player"));
		assertTrue(recipes.contains("context.firstPerson().triggerSign(0.0f)"),
				"Only the locally anchored confirmed summon may start first-person SIGN");
		assertTrue(recipes.contains("VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN"));
		assertTrue(recipes.contains("cue.origin()"),
				"A dog pool must stay at the exact authoritative spawn origin");
	}

	@Test
	void recallUsesOneStillLivingDogCuePerDogAndNeverAnOwnerPool() throws Exception {
		String runtime = Files.readString(SUMMON_RUNTIME);
		String teardown = runtime.substring(
				runtime.indexOf("public static void teardown"),
				runtime.indexOf("private static void broadcastCue"));
		assertTrue(teardown.contains("broadcastDogCue(level, owner, MegumiVfxIds.DOGS_RECALL, dog)"),
				"Manual recall must emit while the actual dog body still exists");
		assertTrue(teardown.indexOf("MegumiVfxIds.DOGS_RECALL") < teardown.indexOf("dog.beginRecall()"),
				"The cue origin must be captured before the dog begins sinking away");
		assertTrue(!teardown.contains("MegumiVfxIds.DOGS_RECALL, owner.position()"),
				"Recall must not synthesize a pool at the owner");
		assertTrue(Files.readString(RECIPES).contains("VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_CLOSE"));
	}

	@Test
	void shadowMoteIsRegisteredFromMegumisClientDefinitionAndReusesAProjectSprite() throws Exception {
		String particles = Files.readString(PARTICLES);
		assertTrue(particles.contains("MEGUMI_SHADOW_MOTE"));
		assertTrue(particles.contains("JujutsuMod.id(\"megumi_shadow_mote\")"));

		String definition = Files.readString(CLIENT_DEFINITION);
		assertTrue(definition.contains("ParticleFactoryRegistry.getInstance().register(JujutsuParticles.MEGUMI_SHADOW_MOTE"),
				"Megumi owns his client particle provider registration");
		assertTrue(Files.isRegularFile(MOTE));
		String mote = Files.readString(MOTE);
		assertTrue(mote.contains("return super.getLightColor(partialTick);"),
				"Shadow motes must use world lighting instead of full-bright rendering");
		assertTrue(mote.contains("rCol = accent ? 0.045f : 0.015f"),
				"Accent motes must remain neutral near-black");
		assertFalse(mote.contains("0xF000F0"), "Shadow motes must not force full-bright lighting");
		assertFalse(mote.contains("0.92f"), "Shadow motes must not retain the saturated teal accent");
		assertTrue(Files.isRegularFile(MOTE_JSON));
		assertTrue(Files.readString(MOTE_JSON).contains("jujutsumod:hairpin_spark"));
		assertTrue(Files.isRegularFile(REUSED_SPRITE));
	}

	private static int occurrences(String text, String needle) {
		return (text.length() - text.replace(needle, "").length()) / needle.length();
	}
}
