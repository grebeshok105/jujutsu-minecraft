package jujutsu.mod.client.rich.modules.module;

import java.util.List;
import java.util.stream.Collectors;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;

public class ModuleProvider {
	private final List<ModuleStructure> modules;

	public ModuleProvider(List<ModuleStructure> modules) {
		this.modules = modules;
	}

	public List<ModuleStructure> modules() {
		return modules;
	}

	public List<ModuleStructure> getModules(ModuleCategory category) {
		return modules.stream().filter(m -> m.getCategory() == category).collect(Collectors.toList());
	}
}
