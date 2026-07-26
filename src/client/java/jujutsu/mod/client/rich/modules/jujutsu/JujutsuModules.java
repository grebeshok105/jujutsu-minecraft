package jujutsu.mod.client.rich.modules.jujutsu;

import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.client.rich.modules.module.ModuleRepository;
import jujutsu.mod.client.rich.modules.module.ModuleStructure;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;

/**
 * Characters tab content (Combat category renamed to Characters in the sidebar).
 * Visual roster is drawn by {@code CharacterRosterPanel}; modules keep the repo non-empty.
 *
 * <p>Built from the client registry rather than written out, so a vessel cannot be in the menu and
 * missing from this tab — which is what a second hand-kept list of three eventually produces.
 */
public final class JujutsuModules {
	private JujutsuModules() {}

	public static void registerAll(ModuleRepository repo) {
		for (CharacterClientDefinition definition : JujutsuCharacterClients.all()) {
			ModuleStructure module = new ModuleStructure(
					definition.moduleName(), definition.moduleDescription(), ModuleCategory.COMBAT);
			module.setState(definition.moduleStartsEnabled());
			repo.builder().add(module);
		}
	}
}
