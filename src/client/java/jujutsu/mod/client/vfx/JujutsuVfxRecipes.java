package jujutsu.mod.client.vfx;

import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.vfx.todo.TodoVfxRecipes;

/**
 * Aggregate registration for every character VFX recipe pack.
 * Required once a second playable character exists (AGENTS.md / How-to-add-next-character).
 */
public final class JujutsuVfxRecipes {
	private JujutsuVfxRecipes() {}

	public static void registerAll() {
		NobaraVfxRecipes.register();
		TodoVfxRecipes.register();
	}
}
