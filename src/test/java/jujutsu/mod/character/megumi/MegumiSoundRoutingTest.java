package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MegumiSoundRoutingTest {
	private static final Path ENTITY = Path.of("src/main/java/jujutsu/mod/character/megumi/MegumiDivineDogEntity.java");
	private static final Path RUNTIME = Path.of("src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java");
	private static final Path RECIPES = Path.of("src/client/java/jujutsu/mod/client/vfx/megumi/MegumiVfxRecipes.java");

	@Test
	void divineDogBeatsUseServerSpatialSoundAtTheirAuthoritativeSources() throws Exception {
		String entity = Files.readString(ENTITY);
		String runtime = Files.readString(RUNTIME);

		assertTrue(entity.contains("JujutsuSounds.PROJECTJJK_GOO_FOLEY"));
		assertTrue(entity.contains("JujutsuSounds.PROJECTJJK_WHOOSH_HIT"));
		assertTrue(entity.contains("JujutsuSounds.PROJECTJJK_IMPLODE"));
		assertTrue(runtime.contains("JujutsuSounds.PROJECTJJK_SNAP"));
		assertTrue(entity.contains("growlSound().value()"));
		assertTrue(entity.contains("serverLevel.playSound(null, getX(), getY(), getZ()"),
				"Dog sounds must be server broadcasts from the dog position");
		assertTrue(runtime.contains("white.playShadowOpenSound()"));
		assertTrue(runtime.contains("black.playShadowOpenSound()"));
		assertTrue(runtime.contains("player.level().playSound(null, player.getX(), player.getY(), player.getZ()"),
				"Sic command accent must be spatially broadcast from the owner");
	}

	@Test
	void megumiRecipesNeverDuplicateServerSoundLocally() throws Exception {
		String recipes = Files.readString(RECIPES);
		assertFalse(recipes.contains("playNoFalloff"));
		assertFalse(recipes.contains("JujutsuSounds"));
	}
}
