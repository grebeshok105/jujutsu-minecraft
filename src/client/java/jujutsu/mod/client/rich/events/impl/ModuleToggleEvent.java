package jujutsu.mod.client.rich.events.impl;

import jujutsu.mod.client.rich.modules.module.ModuleStructure;

public final class ModuleToggleEvent {
	private final ModuleStructure module;
	private final boolean state;

	public ModuleToggleEvent(ModuleStructure module, boolean state) {
		this.module = module;
		this.state = state;
	}

	public ModuleStructure getModule() {
		return module;
	}

	public boolean isState() {
		return state;
	}
}
