package jujutsu.mod.client.rich;

import jujutsu.mod.client.rich.manager.Manager;

/** Minimal host for the ported Rich clickgui (no full Rich client). */
public final class Initialization {
	private static Initialization instance;
	private Manager manager;

	private Initialization() {}

	public static Initialization getInstance() {
		if (instance == null) {
			instance = new Initialization();
			instance.manager = new Manager();
			instance.manager.init();
		}
		return instance;
	}

	public Manager getManager() {
		return manager;
	}
}
