package jujutsu.mod.client.vfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

public final class VfxDirector {
	private static final int MAX_ACTIVE_INSTANCES = 64;
	private static final Map<ResourceLocation, VfxRecipe> RECIPES = new HashMap<>();
	private static final List<ActiveInstance> ACTIVE_INSTANCES = new ArrayList<>();
	private static final Set<ResourceLocation> UNKNOWN_EFFECT_IDS = new HashSet<>();
	private static final VfxWorldChannel WORLD = new VfxWorldChannel();
	private static final VfxHudChannel HUD = new VfxHudChannel();
	private static final VfxCameraChannel CAMERA = new VfxCameraChannel();
	private static final VfxFirstPersonChannel FIRST_PERSON = new VfxFirstPersonChannel();
	private static final VfxParticleChannel PARTICLES = new VfxParticleChannel();
	private static final VfxSoundChannel SOUND = new VfxSoundChannel();
	private static final VfxPostProcessChannel POST_PROCESS = new VfxPostProcessChannel();
	private static final VfxTimeChannel TIME = new VfxTimeChannel();
	private static ClientLevel activeLevel;
	private static boolean initialized;

	private VfxDirector() {}

	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		WorldRenderEvents.AFTER_ENTITIES.register(VfxDirector::renderWorld);
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JujutsuMod.id("vfx_overlay"), VfxDirector::renderHud);
		ClientTickEvents.END_CLIENT_TICK.register(VfxDirector::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	public static void register(ResourceLocation effectId, VfxRecipe recipe) {
		VfxRecipe previous = RECIPES.putIfAbsent(effectId, recipe);
		if (previous != null) {
			throw new IllegalStateException("Duplicate VFX recipe: " + effectId);
		}
	}

	public static void receive(VfxCue cue) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) {
			return;
		}
		bindLevel(client);
		VfxRecipe recipe = RECIPES.get(cue.effectId());
		if (recipe == null) {
			if (UNKNOWN_EFFECT_IDS.add(cue.effectId())) {
				JujutsuMod.LOGGER.warn("Ignoring unknown VFX cue {}", cue.effectId());
			}
			return;
		}

		VfxInstance instance = recipe.create(cue);
		if (VfxTimeline.isExpired(cue, client.level.getGameTime(), instance.durationTicks())) {
			return;
		}
		if (ACTIVE_INSTANCES.size() >= MAX_ACTIVE_INSTANCES) {
			ACTIVE_INSTANCES.remove(0);
		}
		float initialAgeTicks = VfxTimeline.ageTicks(cue, client.level.getGameTime(), 0.0f);
		instance.start(context(client), initialAgeTicks);
		ACTIVE_INSTANCES.add(new ActiveInstance(cue, instance));
	}

	public static float yawOffset() {
		return CAMERA.yawOffset();
	}

	public static float pitchOffset() {
		return CAMERA.pitchOffset();
	}

	public static float fovOffset() {
		return CAMERA.fovOffset();
	}

	public static float timeScale() {
		return TIME.timeScale();
	}

	public static VfxFirstPersonChannel.Pose firstPersonPose() {
		return FIRST_PERSON.currentPose();
	}

	private static void renderWorld(WorldRenderContext context) {
		WORLD.render(context);
		POST_PROCESS.render(Minecraft.getInstance());
	}

	private static void renderHud(GuiGraphics graphics, DeltaTracker tickCounter) {
		HUD.render(graphics, tickCounter);
	}

	private static void tick(Minecraft client) {
		if (client.level == null) {
			reset();
			return;
		}
		bindLevel(client);
		Iterator<ActiveInstance> iterator = ACTIVE_INSTANCES.iterator();
		while (iterator.hasNext()) {
			ActiveInstance active = iterator.next();
			if (VfxTimeline.isExpired(active.cue(), client.level.getGameTime(), active.instance().durationTicks())) {
				iterator.remove();
			}
		}
	}

	private static VfxContext context(Minecraft client) {
		return new VfxContext(client, VfxQuality.from(client.options.particles().get()), WORLD, HUD, CAMERA, FIRST_PERSON, PARTICLES, SOUND, POST_PROCESS, TIME);
	}

	private static void bindLevel(Minecraft client) {
		if (activeLevel != client.level) {
			clear();
			activeLevel = client.level;
		}
	}

	private static void reset() {
		clear();
		POST_PROCESS.resetSession();
		activeLevel = null;
	}

	private static void clear() {
		ACTIVE_INSTANCES.clear();
		WORLD.clear();
		HUD.clear();
		CAMERA.clear();
		FIRST_PERSON.clear();
		PARTICLES.clear();
		SOUND.clear();
		POST_PROCESS.clear();
		TIME.clear();
	}

	private record ActiveInstance(VfxCue cue, VfxInstance instance) {}
}
