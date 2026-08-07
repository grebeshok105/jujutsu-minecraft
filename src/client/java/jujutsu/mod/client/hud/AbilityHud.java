package jujutsu.mod.client.hud;

import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.glfw.GLFW;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientAbilityCooldowns;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.character.HudSlot;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.client.rich.screens.clickgui.impl.DragHandler;
import jujutsu.mod.client.rich.theme.ClickGuiTheme;
import jujutsu.mod.client.ui.msdf.MsdfFonts;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;

/**
 * In-world ability HUD: a horizontal strip of slots for the selected vessel.
 *
 * <p>Registered via {@code VfxDirector.registerHudContribution} from JujutsuModClient. Renders
 * through the SDF/MSDF pipelines directly (context-independent — works in the HUD pass, not just
 * from a Screen), with one SDF flush and one MSDF endFrame per frame. The strip is hidden while no
 * vessel is selected, and its position is session-only: a fixed bottom-center anchor plus the
 * {@link DragHandler} offset (drag polling lives in this class's tick hook).
 */
public final class AbilityHud {
	private static final float CELL = 20.0f;
	private static final float GAP = 4.0f;
	private static final float RADIUS = 5.0f;
	private static final float BORDER_WIDTH = 0.6f;
	private static final float ICON_SIZE = 14.0f;
	private static final float LABEL_SIZE = 4.0f;
	/** How far the strip's bottom edge sits above the screen's bottom edge, in GUI pixels. */
	private static final float BOTTOM_MARGIN = 40.0f;
	private static final float TEXTURE_SIZE = 96.0f;

	private static final SdfRenderer SDF = new SdfRenderer();
	private static final DragHandler DRAG = new DragHandler();

	/** Scratch buffers for GLFW cursor polling (avoids per-tick allocation). */
	private static final double[] CURSOR_X = new double[1];
	private static final double[] CURSOR_Y = new double[1];

	/** Last rendered strip bounds in GUI-scaled pixels (top-left origin), for drag hit tests. */
	static int STRIP_X;
	static int STRIP_Y;
	static int STRIP_WIDTH;
	static int STRIP_HEIGHT;

	/** The undragged bottom-center anchor, in GUI-scaled pixels. Stable across frames for clamping. */
	static int BASE_X;
	static int BASE_Y;

	static {
		// No screen exists to deliver mouse events — the HUD polls GLFW directly each tick.
		ClientTickEvents.END_CLIENT_TICK.register(AbilityHud::tickDrag);
	}

	private AbilityHud() {}

	/** HUD contribution callback; hidden unless the local player has a vessel selected. */
	public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			clearStripBounds();
			return;
		}
		JujutsuCharacter character = ClientCharacterSelectionManager.characterOrNone(client.player.getUUID());
		if (character == JujutsuCharacter.NONE) {
			clearStripBounds();
			return;
		}
		List<HudSlot> slots = JujutsuCharacterClients.definition(character).hudSlots();
		if (slots.isEmpty()) {
			clearStripBounds();
			return;
		}

		int accent = ClickGuiTheme.accentFor(character);
		int fill = ClickGuiTheme.raised(40);
		int border = ClickGuiTheme.outline(200);
		int overlayColor = withAlpha(accent, 140);
		int labelColor = withAlpha(accent, 200);

		float totalWidth = slots.size() * (CELL + GAP);
		float x = (graphics.guiWidth() - totalWidth) / 2.0f + DRAG.getOffsetX();
		float y = graphics.guiHeight() - BOTTOM_MARGIN - CELL + DRAG.getOffsetY();

		SDF.begin();
		for (int index = 0; index < slots.size(); index++) {
			HudSlot slot = slots.get(index);
			float sx = x + index * (CELL + GAP);
			SDF.add(SdfShape.builder()
					.rect(sx, y, CELL, CELL)
					.radius(RADIUS)
					.border(BORDER_WIDTH, border)
					.fill(fill)
					.build());
			int remaining = ClientAbilityCooldowns.remainingTicks(character, slot.ability());
			int total = JujutsuCharacterClients.definition(character).maxCooldownTicks(slot.ability());
			if (remaining > 0 && total > 0) {
				float fraction = Math.min(1.0f, remaining / (float) total);
				float overlayHeight = CELL * fraction;
				SDF.add(SdfShape.builder()
						.rect(sx, y + CELL - overlayHeight, CELL, overlayHeight)
						.radius(RADIUS)
						.fill(overlayColor)
						.build());
			}
		}
		SDF.flush();

		for (int index = 0; index < slots.size(); index++) {
			HudSlot slot = slots.get(index);
			float sx = x + index * (CELL + GAP);
			graphics.blit(RenderPipelines.GUI_TEXTURED, slot.icon(),
					Math.round(sx + (CELL - ICON_SIZE) / 2.0f), Math.round(y + 1.0f),
					0.0f, 0.0f, Math.round(ICON_SIZE), Math.round(ICON_SIZE),
					Math.round(TEXTURE_SIZE), Math.round(TEXTURE_SIZE),
					Math.round(TEXTURE_SIZE), Math.round(TEXTURE_SIZE));
			MsdfFonts.drawCentered(MsdfFonts.Face.BOLD, slot.keyLabel(),
					sx + CELL / 2.0f, y + ICON_SIZE + 1.0f, LABEL_SIZE, labelColor);
		}
		MsdfFonts.endFrame();

		STRIP_X = Math.round(x);
		STRIP_Y = Math.round(y);
		STRIP_WIDTH = Math.round(totalWidth);
		STRIP_HEIGHT = Math.round(CELL);
		BASE_X = Math.round((graphics.guiWidth() - totalWidth) / 2.0f);
		BASE_Y = Math.round(graphics.guiHeight() - BOTTOM_MARGIN - CELL);
	}

	/**
	 * Polls GLFW mouse state each tick and drives the drag handler — the HUD is in-world, so no
	 * screen ever delivers mouse events. The strip is grabbed only when the press lands inside the
	 * last rendered bounds, and its offset is clamped to keep it fully on-screen.
	 */
	static void tickDrag(Minecraft client) {
		if (client.player == null || STRIP_WIDTH <= 0 || STRIP_HEIGHT <= 0) {
			DRAG.endDrag();
			DRAG.update();
			return;
		}

		long window = client.getWindow().getWindow();
		boolean pressed = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		GLFW.glfwGetCursorPos(window, CURSOR_X, CURSOR_Y);
		// GLFW reports physical pixels; the strip bounds and DragHandler work in GUI-scaled pixels.
		double guiScale = client.getWindow().getGuiScale();
		double mouseX = CURSOR_X[0] / guiScale;
		double mouseY = CURSOR_Y[0] / guiScale;

		if (pressed) {
			if (!DRAG.isDragging()) {
				DRAG.startDrag(mouseX, mouseY, STRIP_X, STRIP_Y, STRIP_WIDTH, STRIP_HEIGHT);
			}
			if (DRAG.isDragging()) {
				DRAG.drag(mouseX, mouseY);
				clampToScreen(client);
			}
		} else {
			DRAG.endDrag();
		}
		// Eases the offset back toward its target while idle; a live drag writes it directly.
		DRAG.update();
	}

	/** Confines the offset so the strip stays fully on-screen: bounds are relative to the base anchor. */
	private static void clampToScreen(Minecraft client) {
		float maxOffsetX = client.getWindow().getGuiScaledWidth() - STRIP_WIDTH - BASE_X;
		float maxOffsetY = client.getWindow().getGuiScaledHeight() - STRIP_HEIGHT - BASE_Y;
		DRAG.clampTo(-BASE_X, maxOffsetX, -BASE_Y, maxOffsetY);
	}

	private static int withAlpha(int argb, int alpha) {
		return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
	}

	private static void clearStripBounds() {
		STRIP_X = 0;
		STRIP_Y = 0;
		STRIP_WIDTH = 0;
		STRIP_HEIGHT = 0;
		BASE_X = 0;
		BASE_Y = 0;
	}
}
