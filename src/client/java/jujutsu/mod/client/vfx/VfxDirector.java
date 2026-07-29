package jujutsu.mod.client.vfx;

import java.util.HashMap;
import java.util.HashSet;
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
	private static final Map<ResourceLocation, VfxRecipe> RECIPES = new HashMap<>();
	private static final Set<ResourceLocation> UNKNOWN_EFFECT_IDS = new HashSet<>();
	private static final VfxWorldChannel WORLD = new VfxWorldChannel();
	private static final VfxHudChannel HUD = new VfxHudChannel();
	private static final VfxCameraChannel CAMERA = new VfxCameraChannel();
	private static final VfxFirstPersonChannel FIRST_PERSON = new VfxFirstPersonChannel();
	private static final VfxParticleChannel PARTICLES = new VfxParticleChannel();
	private static final VfxSoundChannel SOUND = new VfxSoundChannel();
	private static final VfxPostProcessChannel POST_PROCESS = new VfxPostProcessChannel();
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

	public static void registerHudContribution(ResourceLocation id, VfxHudChannel.Contribution contribution) {
		HUD.registerContribution(id, contribution);
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

		startResolvedCue(cue, recipe, client.level.getGameTime(), context(client));
	}

	static boolean startResolvedCue(VfxCue cue, VfxRecipe recipe, long gameTime, VfxContext context) {
		VfxInstance instance = recipe.create(cue);
		if (VfxTimeline.isExpired(cue, gameTime, instance.durationTicks())) {
			return false;
		}
		float initialAgeTicks = VfxTimeline.ageTicks(cue, gameTime, 0.0f);
		instance.start(context, initialAgeTicks);
		return true;
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

	public static VfxFirstPersonChannel.Pose firstPersonPose() {
		return FIRST_PERSON.currentPose();
	}

	public static VfxFirstPersonChannel.Style firstPersonStyle() {
		return FIRST_PERSON.style();
	}

	/** Sample once per frame; pass the result to every arm so they share one instant. */
	public static float firstPersonProgress() {
		return FIRST_PERSON.progress();
	}

	public static void expireFirstPerson() {
		FIRST_PERSON.expireIfFinished();
	}

	/** Drops an in-flight first-person animation, e.g. on a vessel switch. */
	public static void cancelFirstPerson() {
		FIRST_PERSON.cancel();
	}

	public static VfxFirstPersonChannel.Pose firstPersonDualArmPose(
			VfxFirstPersonChannel.Style style, net.minecraft.world.entity.HumanoidArm arm, float progress) {
		return FIRST_PERSON.dualArmPose(style, arm, progress);
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
		// After bindLevel, so a level change has already restored the duck through clear() before this
		// looks at a deadline that no longer belongs to anything.
		SOUND.tick(client);
	}

	private static VfxContext context(Minecraft client) {
		return new VfxContext(client, VfxQuality.from(client.options.particles().get()), WORLD, HUD, CAMERA, FIRST_PERSON, PARTICLES, SOUND, POST_PROCESS);
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
		WORLD.clear();
		HUD.clear();
		CAMERA.clear();
		FIRST_PERSON.clear();
		PARTICLES.clear();
		SOUND.clear();
		POST_PROCESS.clear();
	}
}
