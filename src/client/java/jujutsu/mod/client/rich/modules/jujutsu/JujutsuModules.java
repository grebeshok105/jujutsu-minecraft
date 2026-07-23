package jujutsu.mod.client.rich.modules.jujutsu;

import jujutsu.mod.client.rich.modules.module.ModuleRepository;
import jujutsu.mod.client.rich.modules.module.ModuleStructure;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;

/**
 * Characters tab content (Combat category renamed to Characters in the sidebar).
 * Visual roster is drawn by {@code CharacterRosterPanel}; modules keep the repo non-empty.
 */
public final class JujutsuModules {
	private JujutsuModules() {}

	public static void registerAll(ModuleRepository repo) {
		ModuleStructure nobara = new ModuleStructure(
				"Nobara", "Straw Doll Technique — Grade 3 vessel", ModuleCategory.COMBAT);
		nobara.setState(false);

		ModuleStructure none = new ModuleStructure(
				"None", "No cursed technique", ModuleCategory.COMBAT);
		none.setState(true);

		repo.builder().add(nobara).add(none);
	}
}
