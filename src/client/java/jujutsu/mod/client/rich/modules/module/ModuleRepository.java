package jujutsu.mod.client.rich.modules.module;

import java.util.ArrayList;
import java.util.List;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;
import jujutsu.mod.client.rich.modules.module.setting.implement.BooleanSetting;
import jujutsu.mod.client.rich.modules.module.setting.implement.BindSetting;
import jujutsu.mod.client.rich.modules.jujutsu.JujutsuModules;
import org.lwjgl.glfw.GLFW;

public class ModuleRepository {
	private final List<ModuleStructure> moduleStructures = new ArrayList<>();
	private final List<ModuleStructure> hiddenModules = new ArrayList<>();

	public void setup() {
		// Real jujutsu content exposed as Rich modules (Character/Combat/…).
		JujutsuModules.registerAll(this);
	}

	public ModuleBuilder builder() {
		return new ModuleBuilder(this);
	}

	public void registerModule(ModuleStructure module, boolean hidden) {
		if (hidden) {
			hiddenModules.add(module);
		} else {
			moduleStructures.add(module);
		}
	}

	public List<ModuleStructure> modules() {
		return moduleStructures;
	}

	public List<ModuleStructure> hiddenModules() {
		return hiddenModules;
	}
}
