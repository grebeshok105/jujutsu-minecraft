package jujutsu.mod.client.vfx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jujutsu.mod.client.character.megumi.vfx.MegumiVfxRecipes;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.vfx.todo.TodoVfxRecipes;
import jujutsu.mod.vfx.VfxCue;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class VfxDurationContractTest {
	@BeforeEach
	void clearBefore() {
		VfxDirector.resetRecipesForTest();
	}

	@AfterEach
	void clearAfter() {
		VfxDirector.resetRecipesForTest();
	}

	@Test
	void equalLifetimeUsesOneNamedValueForRecipeAndRetainedWorldState() {
		NobaraVfxRecipes.register();
		TodoVfxRecipes.register();
		MegumiVfxRecipes.register();
		VfxCue cue = new VfxCue(jujutsu.mod.vfx.NobaraVfxIds.HAMMER, Vec3.ZERO, VfxCue.NO_ANCHOR,
				Vec3.ZERO, 1, 0L, 1L, Vec3.ZERO);

		assertEquals(NobaraVfxRecipes.HAMMER_DURATION_TICKS,
				VfxDirector.recipeForTest(jujutsu.mod.vfx.NobaraVfxIds.HAMMER).create(cue).durationTicks());
		assertEquals(NobaraVfxRecipes.HAMMER_DURATION_TICKS, 10);
		String source = readNobaraRecipes();
		assertTrue(source.contains("VfxInstance.of(HAMMER_DURATION_TICKS"));
		assertTrue(source.lines().map(String::strip).anyMatch(line -> line.equals(
				"context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.HAMMER_SEND, HAMMER_DURATION_TICKS);")),
				"hammer recipe and retained impact must share the named duration");
		assertEquals(MegumiVfxRecipes.SUMMON_DURATION_TICKS, 16);
		assertEquals(MegumiVfxRecipes.RECALL_DURATION_TICKS, 12);
		assertEquals(TodoVfxRecipes.SWAP_ENDPOINT_DURATION_TICKS, 8);
	}

	private static String readNobaraRecipes() {
		try {
			return Files.readString(Path.of("src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java"));
		} catch (IOException exception) {
			throw new AssertionError(exception);
		}
	}

	@Test
	void blackFlashKeepsIntentionalLongRecipeAndShortRetainedImpact() {
		assertEquals(48, NobaraVfxRecipes.BLACK_FLASH_RECIPE_DURATION_TICKS);
		assertEquals(28, NobaraVfxRecipes.BLACK_FLASH_WORLD_IMPACT_DURATION_TICKS);
	}
}
