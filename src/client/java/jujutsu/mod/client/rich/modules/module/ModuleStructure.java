package jujutsu.mod.client.rich.modules.module;

import lombok.Getter;
import lombok.Setter;
import org.lwjgl.glfw.GLFW;
import jujutsu.mod.client.rich.IMinecraft;
import jujutsu.mod.client.rich.modules.module.category.ModuleCategory;
import jujutsu.mod.client.rich.modules.module.setting.SettingRepository;
import jujutsu.mod.client.rich.util.animations.Animation;
import jujutsu.mod.client.rich.util.animations.Decelerate;
import jujutsu.mod.client.rich.util.animations.Direction;

@Getter
@Setter
public class ModuleStructure extends SettingRepository implements IMinecraft {
	private final String name;
	private final String description;
	private final ModuleCategory category;
	private final Animation animation = new Decelerate().setMs(175).setValue(1);

	private int key = GLFW.GLFW_KEY_UNKNOWN;
	private int type = 1;
	public boolean state;
	public boolean favorite;

	public ModuleStructure(String name, ModuleCategory category) {
		this(name, "", category);
	}

	public ModuleStructure(String name, String description, ModuleCategory category) {
		this.name = name;
		this.description = description == null ? "" : description;
		this.category = category;
	}

	public void switchState() {
		setState(!state);
	}

	public void setState(boolean state) {
		animation.setDirection(state ? Direction.FORWARDS : Direction.BACKWARDS);
		this.state = state;
	}

	public void switchFavorite() {
		favorite = !favorite;
	}

	public void setFavorite(boolean favorite) {
		this.favorite = favorite;
	}

	public void activate() {}

	public void deactivate() {}

	public boolean isState() {
		return state;
	}

	public boolean isFavorite() {
		return favorite;
	}

	public int getKey() {
		return key;
	}

	public void setKey(int key) {
		this.key = key;
	}
}
