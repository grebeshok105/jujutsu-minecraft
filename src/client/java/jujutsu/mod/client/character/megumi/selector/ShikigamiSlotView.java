package jujutsu.mod.client.character.megumi.selector;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;
import jujutsu.mod.character.megumi.MegumiElephantEntity;
import jujutsu.mod.character.megumi.MegumiDeerEntity;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiOxEntity;
import jujutsu.mod.character.megumi.MegumiRabbitEntity;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;
import jujutsu.mod.character.megumi.MegumiSerpentEntity;
import jujutsu.mod.character.megumi.MegumiShikigamiSlotState;
import jujutsu.mod.character.megumi.MegumiToadEntity;
import jujutsu.mod.character.megumi.MegumiTigerEntity;
import jujutsu.mod.client.render.megumi.MegumiDivineDogRenderState;
import jujutsu.mod.client.render.megumi.MegumiShikigamiRenderState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * One slot of the strip: the tactile plate, the shikigami's live 3D body, its name, and the state
 * markers that tell the player why a slot will or will not answer a click.
 *
 * <p>The body is a real entity instance, built once per type against the current client level and
 * cached — never added to the world, never ticked, so it costs nothing beyond the render itself. Its
 * presentation is pinned to the settled pose before submission: a client-side body is synced into
 * {@code MATERIALIZING} at construction and its phase only ever advances on the server, so an
 * unpinned preview would render sunk a block below its box (the materialize offset) and play the rise
 * clip. Pinning the render state's phase/progress/offset shows the same body the player sees standing
 * in the world.
 *
 * <p>Everything here is drawing only — which states are selectable and what a click means lives in
 * {@link ShikigamiSelectorState}, and where the slots are lives in {@link ShikigamiSelectorLayout}.
 */
public final class ShikigamiSlotView {
	/** Fraction of the model well a body fills; the rest is breathing room around the silhouette. */
	private static final float MODEL_FILL = 0.92f;
	/**
	 * The cooldown wipe's full-height span in ticks. The ledger's deadlines carry no per-type maximum,
	 * so the bar reads as "roughly how long" — twenty seconds fills the well; anything longer caps out.
	 */
	private static final int COOLDOWN_BAR_SPAN_TICKS = 400;
	/** The dark copy drawn one pixel under every label, so no text ever sits bare on a plate. */
	private static final int TEXT_SHADOW = 0xAA000000;
	/** A slight downward camera pitch reads as "standing on a shelf" rather than a flat mugshot. */
	private static final float MODEL_PITCH = -0.22f;
	/** A quarter turn off dead-centre; identical for every body so the strip reads as one row. */
	private static final float MODEL_YAW = 205.0f;
	/** Per-type preview multiplier, for bodies whose hitbox and authored silhouette disagree. */
	private static final Map<MegumiShikigami, Float> MODEL_TRIM = new EnumMap<>(Map.of(
			MegumiShikigami.DOGS, 0.95f,
			MegumiShikigami.NUE, 1.0f,
			MegumiShikigami.TOAD, 1.0f,
			MegumiShikigami.RABBITS, 0.9f,
			MegumiShikigami.ELEPHANT, 0.95f,
			MegumiShikigami.SERPENT, 0.8f,
			MegumiShikigami.DEER, 0.9f,
			MegumiShikigami.OX, 0.9f,
			MegumiShikigami.TIGER, 0.95f));
	private static final Map<MegumiShikigami, LivingEntity> MODELS = new EnumMap<>(MegumiShikigami.class);

	/** Marker grids, drawn as pixels so the strip needs no texture of its own. '#' is painted. */
	private static final String[] MARKER_SUMMONED = {".#.", "###", ".#."};
	private static final String[] MARKER_COOLDOWN = {".###.", "#...#", "#.#.#", "#...#", ".###."};
	private static final String[] MARKER_LOCKED = {".###.", ".#.#.", "#####", "##.##", "#####"};
	private static final String[] MARKER_DESTROYED = {"#...#", ".#.#.", "..#..", ".#.#.", "#...#"};
	private static final String[] MARKER_TEMPORARY = {"#.#.#", ".....", "#.#.#", ".....", "#.#.#"};

	private ShikigamiSlotView() {
	}

	/** The per-slot presentation values the screen reads off its motion channel. */
	record Motion(float alpha, float hoverScale, float pulseT, float offsetX, float offsetY) {
	}

	/** Drops the cached preview bodies. Called when the client leaves the level they were built on. */
	public static void clearModels() {
		MODELS.clear();
	}

	static void render(GuiGraphics graphics, Font font, ShikigamiSelectorLayout.Slot slot,
			MegumiShikigamiSlotState state, Set<String> flags, Motion motion) {
		boolean active = flags.contains(ShikigamiSelectorState.ACTIVE);
		boolean hovered = flags.contains(ShikigamiSelectorState.HOVERED);
		boolean selectable = flags.contains(ShikigamiSelectorState.AVAILABLE);
		float alpha = motion.alpha();
		int x = slot.x();
		int y = slot.y();
		int w = slot.w();
		int h = slot.h();

		int plate = SelectorTheme.SLOT_FILL;
		if (active) {
			plate = SelectorTheme.lerp(plate, SelectorTheme.ACCENT_DEEP, 0.45f);
		}
		if (hovered) {
			plate = SelectorTheme.lerp(plate, SelectorTheme.ACCENT_GLOW, 0.22f);
		}
		plate(graphics, x, y, w, h, SelectorTheme.withAlpha(plate, alpha));

		int boxX = x + Math.max(2, w / 12);
		int boxY = y + Math.max(2, Math.round(h * 0.06f));
		int boxW = w - 2 * Math.max(2, w / 12);
		int boxH = Math.round(h * 0.60f);
		well(graphics, boxX, boxY, boxW, boxH, SelectorTheme.withAlpha(SelectorTheme.PANEL_FILL, alpha * 0.85f));
		// The PiP element ignores the pose stack (its pose is IDENTITY), so the slot's animated
		// rise/shake offset is folded into the body's box by hand — otherwise the plate slides
		// under a body that is already at rest.
		renderModel(graphics, slot.type(), boxX + Math.round(motion.offsetX()), boxY + Math.round(motion.offsetY()),
				boxW, boxH, motion.hoverScale(), alpha);

		if (!selectable) {
			graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1,
					SelectorTheme.withAlpha(0xFF0E0A08, alpha * 0.42f));
		}
		if (flags.contains(ShikigamiSelectorState.COOLDOWN)) {
			cooldownWipe(graphics, font, slot.type(), boxX, boxY, boxW, boxH, alpha);
		}

		if (active) {
			frame(graphics, x, y, w, h, SelectorTheme.withAlpha(SelectorTheme.ACCENT_DEEP, alpha));
			frame(graphics, x + 1, y + 1, w - 2, h - 2, SelectorTheme.withAlpha(SelectorTheme.ACCENT_GLOW, alpha * 0.55f));
			graphics.fill(x + 3, y + 1, x + w - 3, y + 2, SelectorTheme.withAlpha(SelectorTheme.ACCENT_GLOW, alpha));
		} else if (hovered) {
			frame(graphics, x, y, w, h, SelectorTheme.withAlpha(SelectorTheme.ACCENT_HOVER, alpha * 0.9f));
		} else {
			graphics.fill(x + 2, y, x + w - 2, y + 1, SelectorTheme.withAlpha(0x33FFFFFF, alpha));
			graphics.fill(x + 2, y + h - 1, x + w - 2, y + h, SelectorTheme.withAlpha(0x33000000, alpha));
		}
		if (motion.pulseT() > 0.0f) {
			int glow = SelectorTheme.withAlpha(SelectorTheme.ACCENT_HOVER, motion.pulseT() * 0.9f);
			frame(graphics, x - 1, y - 1, w + 2, h + 2, glow);
			graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2,
					SelectorTheme.withAlpha(SelectorTheme.ACCENT_HOVER, motion.pulseT() * 0.16f));
		}

		marker(graphics, flags, state, x + w - 3, y + 3, w, alpha);
		name(graphics, font, slot.type(), active, selectable, x, y, w, h, alpha);
		stateLabel(graphics, font, state, x, y, w, h, alpha);
	}

	/** The 3D body, framed in its well. Nothing is drawn when there is no level to build it against. */
	private static void renderModel(GuiGraphics graphics, MegumiShikigami type, int x, int y, int w, int h,
			float hoverScale, float alpha) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || w <= 4 || h <= 4) {
			return;
		}
		LivingEntity body = model(type, level);
		float trim = MODEL_TRIM.getOrDefault(type, 1.0f);
		float height = Math.max(0.4f, body.getBbHeight());
		float width = Math.max(0.4f, body.getBbWidth());
		float scale = Math.min(h * MODEL_FILL / height, w * MODEL_FILL / width) * trim * hoverScale;

		Vector3f translation = new Vector3f(0.0f, body.getBbHeight() / 2.0f, 0.0f);
		Quaternionf pitch = new Quaternionf().rotateX(MODEL_PITCH);
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(pitch);
		body.setYRot(MODEL_YAW);
		body.yRotO = MODEL_YAW;
		body.setYBodyRot(MODEL_YAW);
		body.yBodyRotO = MODEL_YAW;
		body.setYHeadRot(MODEL_YAW);

		graphics.enableScissor(x, y, x + w, y + h);
		graphics.submitEntityRenderState(stateOf(body), scale, translation, rotation, pitch,
				x, y, x + w, y + h);
		graphics.disableScissor();
	}

	/**
	 * Builds the render state the way the vanilla GUI preview does, then pins the settled pose into
	 * it. See the class comment: without this, a preview body renders mid-materialization.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static EntityRenderState stateOf(LivingEntity body) {
		Minecraft client = Minecraft.getInstance();
		EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(body);
		EntityRenderState state = renderer.createRenderState(body, 1.0f);
		state.hitboxesRenderState = null;
		if (state instanceof MegumiShikigamiRenderState shikigami) {
			shikigami.phase = MegumiShikigamiPresentationPolicy.Phase.ACTIVE;
			shikigami.progress = 1.0f;
			shikigami.verticalOffset = 0.0f;
		} else if (state instanceof MegumiDivineDogRenderState dog) {
			dog.phase = MegumiDogPresentationPolicy.Phase.ACTIVE;
			dog.progress = 1.0f;
			dog.verticalOffset = 0.0f;
			// The slot stands for the summoned pair; the black Dire Wolf is the iconic silhouette.
			dog.blackVariant = true;
		}
		// PACKED_LIGHT is normally injected by GeckoLib's EntityRenderDispatcher mixin, which only
		// wraps the entity-taking render overload. The PiP path submits a prebuilt state, so the
		// ticket never arrives and GeoRenderer.defaultRender throws IllegalArgumentException on it.
		if (state instanceof GeoRenderState geo && !geo.hasGeckolibData(DataTickets.PACKED_LIGHT)) {
			geo.addGeckolibData(DataTickets.PACKED_LIGHT, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
		}
		return state;
	}

	private static LivingEntity model(MegumiShikigami type, ClientLevel level) {
		LivingEntity cached = MODELS.get(type);
		if (cached != null && cached.level() == level) {
			return cached;
		}
		LivingEntity fresh = switch (type) {
			case DOGS -> new MegumiDivineDogEntity(JujutsuEntities.MEGUMI_DIVINE_DOG, level);
			case NUE -> new MegumiNueEntity(JujutsuEntities.MEGUMI_NUE, level);
			case TOAD -> new MegumiToadEntity(JujutsuEntities.MEGUMI_TOAD, level);
			case RABBITS -> new MegumiRabbitEntity(JujutsuEntities.MEGUMI_RABBIT, level);
			case ELEPHANT -> new MegumiElephantEntity(JujutsuEntities.MEGUMI_MAX_ELEPHANT, level);
			case SERPENT -> new MegumiSerpentEntity(JujutsuEntities.MEGUMI_SERPENT, level);
			case DEER -> new MegumiDeerEntity(JujutsuEntities.MEGUMI_DEER, level);
			case OX -> new MegumiOxEntity(JujutsuEntities.MEGUMI_OX, level);
			case TIGER -> new MegumiTigerEntity(JujutsuEntities.MEGUMI_TIGER, level);
		};
		MODELS.put(type, fresh);
		return fresh;
	}

	/** The model well: a recessed pocket with a floor shadow, so bodies sit in the plate, not on it. */
	private static void well(GuiGraphics graphics, int x, int y, int w, int h, int fill) {
		plate(graphics, x, y, w, h, fill);
		graphics.fillGradient(x + 1, y + h - Math.max(3, h / 5), x + w - 1, y + h - 1,
				SelectorTheme.withAlpha(0x00000000, 1.0f), SelectorTheme.withAlpha(0x59000000, 1.0f));
		int cx = x + w / 2;
		int cy = y + h - 2;
		for (int row = 0; row < 3; row++) {
			int half = Math.max(1, w / 2 - 3 - row * 3);
			graphics.fill(cx - half, cy - row, cx + half, cy - row + 1,
					SelectorTheme.withAlpha(0x66000000, 0.55f - row * 0.15f));
		}
	}

	/** The remaining cooldown, as a wipe climbing the well plus its whole seconds in the middle. */
	private static void cooldownWipe(GuiGraphics graphics, Font font, MegumiShikigami type,
			int x, int y, int w, int h, float alpha) {
		int remaining = ClientMegumiShikigamiState.cooldownRemainingTicks(type);
		if (remaining <= 0) {
			return;
		}
		float fraction = Math.min(1.0f, remaining / (float) COOLDOWN_BAR_SPAN_TICKS);
		int bar = Math.max(1, Math.round(fraction * h));
		graphics.fill(x, y + h - bar, x + w, y + h, SelectorTheme.withAlpha(SelectorTheme.STATE_COOLDOWN, alpha * 0.30f));
		graphics.fill(x, y + h - bar, x + w, y + h - bar + 1,
				SelectorTheme.withAlpha(SelectorTheme.STATE_COOLDOWN, alpha * 0.65f));
		int seconds = Math.max(1, (int) Math.ceil(remaining / 20.0));
		if (w >= 24) {
			text(graphics, font, Component.literal(Integer.toString(seconds)),
					x + w / 2, y + h / 2 - 4, SelectorTheme.withAlpha(SelectorTheme.STATE_COOLDOWN, alpha), 0.9f);
		}
	}

	private static void marker(GuiGraphics graphics, Set<String> flags, MegumiShikigamiSlotState state,
			int right, int top, int w, float alpha) {
		String[] grid = markerGrid(flags);
		if (grid == null) {
			return;
		}
		int color = SelectorTheme.withAlpha(glyphColor(state), alpha);
		int width = grid[0].length();
		int x = right - width;
		int y = top;
		for (int row = 0; row < grid.length; row++) {
			String line = grid[row];
			for (int col = 0; col < line.length(); col++) {
				if (line.charAt(col) != '#') {
					continue;
				}
				graphics.fill(x + col, y + row, x + col + 1, y + row + 1, color);
			}
		}
	}

	private static String[] markerGrid(Set<String> flags) {
		if (flags.contains(ShikigamiSelectorState.SUMMONED)) {
			return MARKER_SUMMONED;
		}
		if (flags.contains(ShikigamiSelectorState.COOLDOWN)) {
			return MARKER_COOLDOWN;
		}
		if (flags.contains(ShikigamiSelectorState.LOCKED)) {
			return MARKER_LOCKED;
		}
		if (flags.contains(ShikigamiSelectorState.DESTROYED)) {
			return MARKER_DESTROYED;
		}
		if (flags.contains(ShikigamiSelectorState.TEMPORARY)) {
			return MARKER_TEMPORARY;
		}
		return null;
	}

	/** Marker and label hue. The two darkest markers are lifted toward muted text so they stay legible. */
	private static int glyphColor(MegumiShikigamiSlotState state) {
		return switch (state) {
			case READY, SUMMONED -> SelectorTheme.STATE_SUMMONED;
			case COOLDOWN, TEMPORARY -> SelectorTheme.STATE_COOLDOWN;
			case LOCKED -> SelectorTheme.lerp(SelectorTheme.STATE_LOCKED, SelectorTheme.TEXT_MUTED, 0.35f);
			case DESTROYED -> SelectorTheme.lerp(SelectorTheme.STATE_DESTROYED, SelectorTheme.TEXT_MUTED, 0.55f);
		};
	}

	private static void name(GuiGraphics graphics, Font font, MegumiShikigami type, boolean active,
			boolean selectable, int x, int y, int w, int h, float alpha) {
		Component name = Component.translatable("jujutsumod.megumi.shikigami." + type.id());
		if (active) {
			name = name.copy().withStyle(ChatFormatting.BOLD);
		}
		int color = SelectorTheme.withAlpha(selectable ? SelectorTheme.TEXT_PRIMARY : SelectorTheme.TEXT_MUTED, alpha);
		float scale = fit(font, name, w - 4);
		text(graphics, font, name, x + w / 2, y + Math.round(h * 0.66f), color, scale);
	}

	private static void stateLabel(GuiGraphics graphics, Font font, MegumiShikigamiSlotState state,
			int x, int y, int w, int h, float alpha) {
		String key = ShikigamiSelectorState.stateLabelKey(state);
		if (key == null) {
			return;
		}
		Component label = Component.translatable(key);
		int color = SelectorTheme.withAlpha(glyphColor(state), alpha * 0.95f);
		float scale = Math.min(0.72f, fit(font, label, w - 3));
		text(graphics, font, label, x + w / 2, y + Math.round(h * 0.66f) + 10, color, scale);
	}

	/** Largest text scale at or below 1 that keeps {@code text} inside {@code available} pixels. */
	private static float fit(Font font, Component text, int available) {
		int width = font.width(text);
		if (width <= 0 || width <= available) {
			return 1.0f;
		}
		return Math.max(0.5f, available / (float) width);
	}

	/** Centred text with a one-pixel darkened copy under it — legible on any plate without a shadow. */
	private static void text(GuiGraphics graphics, Font font, Component text, int centerX, int y, int color,
			float scale) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, y);
		graphics.pose().scale(scale);
		int half = font.width(text) / 2;
		graphics.drawString(font, text, -half + 1, 1, TEXT_SHADOW, false);
		graphics.drawString(font, text, -half, 0, color, false);
		graphics.pose().popMatrix();
	}

	/** A plate with its corners cut by two pixels — the strip's one recurring shape. */
	private static void plate(GuiGraphics graphics, int x, int y, int w, int h, int fill) {
		graphics.fill(x + 2, y, x + w - 2, y + h, fill);
		graphics.fill(x, y + 2, x + w, y + h - 2, fill);
	}

	private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
		graphics.fill(x, y, x + w, y + 1, color);
		graphics.fill(x, y + h - 1, x + w, y + h, color);
		graphics.fill(x, y + 1, x + 1, y + h - 1, color);
		graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
	}
}
