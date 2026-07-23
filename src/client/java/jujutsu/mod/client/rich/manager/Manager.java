package jujutsu.mod.client.rich.manager;

import jujutsu.mod.client.rich.events.api.EventManager;
import jujutsu.mod.client.rich.modules.module.ModuleProvider;
import jujutsu.mod.client.rich.modules.module.ModuleRepository;
import jujutsu.mod.client.rich.screens.clickgui.ClickGui;
import jujutsu.mod.client.rich.util.render.font.FontInitializer;
import jujutsu.mod.client.rich.util.render.font.FontRenderer;
import jujutsu.mod.client.rich.util.render.font.Fonts;
import jujutsu.mod.client.rich.util.render.shader.RenderCore;
import jujutsu.mod.client.rich.util.render.shader.Scissor;

/** Slim manager: render core + modules + clickgui only. */
public final class Manager {
	private EventManager eventManager;
	private RenderCore renderCore;
	private Scissor scissor;
	private ModuleRepository moduleRepository;
	private ModuleProvider moduleProvider;
	private ClickGui clickgui;

	public void init() {
		FontInitializer.register();
		eventManager = new EventManager();
		renderCore = new RenderCore();
		// load MSDF font registry into renderer
		renderCore.getFontRenderer().loadAllFonts(Fonts.getRegistry());
		scissor = new Scissor();
		moduleRepository = new ModuleRepository();
		moduleRepository.setup();
		moduleProvider = new ModuleProvider(moduleRepository.modules());
		clickgui = new ClickGui();
	}

	public EventManager getEventManager() {
		return eventManager;
	}

	public RenderCore getRenderCore() {
		return renderCore;
	}

	public Scissor getScissor() {
		return scissor;
	}

	public ModuleRepository getModuleRepository() {
		return moduleRepository;
	}

	public ModuleProvider getModuleProvider() {
		return moduleProvider;
	}

	public ClickGui getClickgui() {
		return clickgui;
	}
}
