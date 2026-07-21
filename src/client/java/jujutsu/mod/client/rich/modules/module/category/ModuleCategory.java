package jujutsu.mod.client.rich.modules.module.category;

public enum ModuleCategory {
	COMBAT("Combat"),
	MOVEMENT("Movement"),
	RENDER("Render"),
	PLAYER("Player"),
	MISC("Misc"),
	AUTOBUY("AutoBuy");

	private final String readableName;

	ModuleCategory(String readableName) {
		this.readableName = readableName;
	}

	public String getReadableName() {
		return readableName;
	}
}
