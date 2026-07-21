package jujutsu.mod.client.rich.modules.jujutsu;

import jujutsu.mod.client.rich.modules.module.ModuleRepository;
import jujutsu.mod.client.rich.modules.module.ModuleStructure;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;
import jujutsu.mod.client.rich.modules.module.setting.implement.BooleanSetting;
import jujutsu.mod.client.rich.modules.module.setting.implement.BindSetting;
import org.lwjgl.glfw.GLFW;

/** Jujutsu vessels/kit registered into the ported Rich module list. */
public final class JujutsuModules {
	private JujutsuModules() {}

	public static void registerAll(ModuleRepository repo) {
		ModuleStructure nobara = new ModuleStructure(
				"Nobara", "Straw Doll Technique — Grade 3 vessel", ModuleCategory.COMBAT);
		nobara.setKey(GLFW.GLFW_KEY_N);
		nobara.setState(true);
		nobara.settings(
				new BooleanSetting("Piercing Nail", "Directed nail shot").setValue(true),
				new BindSetting("Piercing Key", "Keybind").setKey(GLFW.GLFW_KEY_R),
				new BooleanSetting("Hairpin Enlarge", "Enlarge marked hairpin").setValue(true),
				new BindSetting("Enlarge Key", "Keybind").setKey(GLFW.GLFW_KEY_B),
				new BooleanSetting("Hairpin Boom", "Mass detonation").setValue(true),
				new BooleanSetting("Resonance", "Hammer / ritual").setValue(true));

		ModuleStructure none = new ModuleStructure("None", "No cursed technique", ModuleCategory.COMBAT);
		none.settings(new BooleanSetting("Vanilla play", "Clear vessel").setValue(true));

		ModuleStructure piercing = new ModuleStructure("Piercing Nail", "Combat nail", ModuleCategory.COMBAT);
		piercing.setKey(GLFW.GLFW_KEY_R);
		ModuleStructure enlarge = new ModuleStructure("Hairpin Enlarge", "Combat hairpin", ModuleCategory.COMBAT);
		enlarge.setKey(GLFW.GLFW_KEY_B);
		ModuleStructure boom = new ModuleStructure("Hairpin Boom", "Mass hairpin", ModuleCategory.COMBAT);
		ModuleStructure resonance = new ModuleStructure("Resonance", "Ritual / hammer", ModuleCategory.COMBAT);

		ModuleStructure msdf = new ModuleStructure("MSDF Type", "Sharp UI text", ModuleCategory.RENDER);
		ModuleStructure sdf = new ModuleStructure("SDF Panels", "Rounded surfaces", ModuleCategory.RENDER);
		ModuleStructure menuKey = new ModuleStructure("Open Menu", "Key N", ModuleCategory.MISC);
		menuKey.setKey(GLFW.GLFW_KEY_N);
		ModuleStructure neon = new ModuleStructure("Neon Dashboard", "Key V", ModuleCategory.MISC);
		neon.setKey(GLFW.GLFW_KEY_V);

		// Movement / Player placeholders so every sidebar category has modules
		ModuleStructure move = new ModuleStructure("Movement Info", "No movement mods", ModuleCategory.MOVEMENT);
		ModuleStructure player = new ModuleStructure("Player Info", "No player mods", ModuleCategory.PLAYER);

		repo.builder()
				.add(nobara)
				.add(none)
				.add(piercing)
				.add(enlarge)
				.add(boom)
				.add(resonance)
				.add(msdf)
				.add(sdf)
				.add(move)
				.add(player)
				.add(menuKey)
				.add(neon);
	}
}
