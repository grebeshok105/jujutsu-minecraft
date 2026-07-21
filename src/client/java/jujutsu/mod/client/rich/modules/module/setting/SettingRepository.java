package jujutsu.mod.client.rich.modules.module.setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingRepository implements Setupable {
	private final List<Setting> settings = new ArrayList<>();

	@Override
	public final void settings(Setting... setting) {
		settings.addAll(Arrays.asList(setting));
	}

	public Setting get(String name) {
		return settings.stream()
				.filter(setting -> setting.getName().equalsIgnoreCase(name))
				.findFirst()
				.orElse(null);
	}

	public List<Setting> settings() {
		return settings;
	}
}
