package jujutsu.mod.client.rich.modules.module.setting;

import java.util.function.Supplier;

public class Setting {
	private final String name;
	private String description;
	private Supplier<Boolean> visible;

	public Setting(String name) {
		this.name = name;
	}

	public Setting(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setVisible(Supplier<Boolean> visible) {
		this.visible = visible;
	}

	public boolean isVisible() {
		return visible == null || visible.get();
	}
}
