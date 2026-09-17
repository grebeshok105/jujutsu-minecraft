package jujutsu.mod.client.character.megumi.selector;

import java.util.List;
import java.util.Set;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiSlotState;
import jujutsu.mod.client.character.QuickSelectorScreen;
import jujutsu.mod.client.input.JujutsuKeybinds;
import jujutsu.mod.network.ShikigamiSelectPayload;

/**
 * The quick-selector strip: a bottom-centred row of shikigami, each showing its live 3D body, its name
 * and its state, opened by holding the selector key and closed by releasing it.
 *
 * <p>Deliberately a screen rather than a HUD overlay, which is what buys the interaction the design
 * spec asks for: opening a screen releases the mouse (so the cursor appears centred and camera-look
 * suspends), and {@code Minecraft} gates the keybind pass on {@code screen == null}, so left click
 * cannot leak into an attack while the strip is up. It does not pause: {@link #isPauseScreen()} is
 * false, so the world keeps running at full speed.
 *
 * <p>Three contract decisions worth knowing before reading the code:
 *
 * <ul>
 *   <li><b>One close gesture.</b> {@link #shouldCloseOnEsc()} is false and a click never closes the
 *       strip, because the spec allows exactly one: releasing the selector key. {@link #keyReleased}
 *       closes and returns false so vanilla still clears the mapping — returning true would leave the
 *       key stuck down and swallow the next press.</li>
 *   <li><b>Optimistic selection.</b> A click applies locally at once and then sends the C2S payload;
 *       the next server push is authoritative. The strip stays open through any number of clicks, so
 *       the last accepted one is the one that survives closing.</li>
 *   <li><b>Availability is asked, never guessed.</b> Every state — selectable or not — comes from
 *       {@link ClientMegumiShikigamiState}, and the verdict for a click comes from
 *       {@link ShikigamiSelectorState}.</li>
 * </ul>
 */
public final class MegumiShikigamiSelectorScreen extends Screen implements QuickSelectorScreen {
	/** Panel padding around the slot row. */
	private static final int PANEL_PAD = 6;
	/** The header band holding the title and the close hint. */
	private static final int HEADER_H = 15;
	/** Extra pixels each slot rises during the entrance, on top of the strip's own slide. */
	private static final int SLOT_RISE_PX = 10;
	/** The whole strip's entrance slide. */
	private static final int STRIP_RISE_PX = 12;
	/** Safety net: an exit clock that never runs out still releases the player. */
	private static final int FORCE_CLOSE_TICKS = 40;

	private final List<MegumiShikigami> entries = List.of(MegumiShikigami.values());
	private final SelectorFeedback feedback = new MegumiSelectorFeedback();
	private final SelectorMotion motion = new SelectorMotion();
	private List<ShikigamiSelectorLayout.Slot> slots = List.of();
	private MegumiShikigami hovered;
	private boolean entered;
	private boolean closing;
	private boolean selectorKeyWasDown;
	private int closingTicks;

	public MegumiShikigamiSelectorScreen() {
		super(Component.translatable("selector.jujutsumod.megumi.title"));
	}

	/** Runs on open and on every resize, so the strip re-centres instead of holding a stale layout. */
	@Override
	protected void init() {
		slots = ShikigamiSelectorLayout.layout(this.width, this.height, entries);
		if (!entered) {
			entered = true;
			motion.open();
			feedback.open();
			// A key-driven open means the gesture is still armed and waiting for its release —
			// latch the watchdog now. Without this, a release landing before the first tick that
			// samples the key down would leave the strip open forever.
			selectorKeyWasDown = JujutsuKeybinds.selectorGesture.isOpen();
		}
		super.init();
	}

	/** The world keeps running: this is a combat control, not a menu. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The spec defines one close gesture — releasing the selector key — so Esc must not add a second. */
	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	/** No blur, no dirt: the strip floats over the live world. */
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void tick() {
		motion.tick(1.0f);
		if (closing) {
			closingTicks++;
			if (motion.stripProgress() <= 0.0f || closingTicks > FORCE_CLOSE_TICKS) {
				onClose();
			}
			return;
		}
		watchSelectorKey();
		super.tick();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		if (slots.isEmpty()) {
			return;
		}
		updateHover(mouseX, mouseY);
		float strip = motion.stripProgress();
		if (strip <= 0.0f) {
			return;
		}
		graphics.pose().pushMatrix();
		graphics.pose().translate(0.0f, (1.0f - strip) * STRIP_RISE_PX);
		drawPanel(graphics, strip);
		drawSlots(graphics);
		graphics.pose().popMatrix();
	}

	@Override
	public void mouseMoved(double mouseX, double mouseY) {
		updateHover(mouseX, mouseY);
		super.mouseMoved(mouseX, mouseY);
	}

	/**
	 * Left click is the strip's while it is open. Every button is swallowed — a click that hits no slot
	 * changes nothing but still must not reach the world — and the strip never closes on a click.
	 */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT || closing) {
			return true;
		}
		ShikigamiSelectorLayout.Slot hit = ShikigamiSelectorLayout.slotAt(slots, mouseX, mouseY);
		if (hit == null) {
			return true;
		}
		int index = slots.indexOf(hit);
		switch (ShikigamiSelectorState.resolveClick(ClientMegumiShikigamiState.stateOf(hit.type()))) {
			case SELECT -> {
				ClientMegumiShikigamiState.markSelected(hit.type());
				if (ClientPlayNetworking.canSend(ShikigamiSelectPayload.TYPE)) {
					ClientPlayNetworking.send(new ShikigamiSelectPayload(hit.type().id()));
				}
				feedback.select();
				motion.pulse(index);
			}
			case REJECT -> {
				feedback.reject();
				motion.shake(index);
			}
			case NONE -> {
			}
		}
		return true;
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		KeyMapping mapping = JujutsuKeybinds.quickSelectorKey;
		if (mapping != null && mapping.matchesMouse(button)) {
			// The selector key may be bound to a mouse button; that release is this screen's close too.
			requestClose();
		}
		return true;
	}

	/**
	 * Releasing the selector key closes the strip. Returning false is part of the contract: vanilla then
	 * runs {@code KeyMapping.set(key, false)}, and a mapping left down would swallow the next press.
	 */
	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		KeyMapping mapping = JujutsuKeybinds.quickSelectorKey;
		if (mapping != null && mapping.matches(keyCode, scanCode)) {
			requestClose();
			return false;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	/**
	 * Closes when the selector key is no longer held. The release event normally does this; the watchdog
	 * covers the release that never arrives — the window losing focus mid-hold. A strip opened without
	 * the key (the dev toggle) never saw the key down, so the watchdog keeps out of its way.
	 */
	private void watchSelectorKey() {
		if (selectorKeyDown()) {
			selectorKeyWasDown = true;
		} else if (selectorKeyWasDown) {
			requestClose();
		}
	}

	private static boolean selectorKeyDown() {
		KeyMapping mapping = JujutsuKeybinds.quickSelectorKey;
		if (mapping == null) {
			return false;
		}
		if (mapping.isDown()) {
			return true;
		}
		// Same physical fallback the input layer uses for this key, for the case where the event is
		// lost — and only for the default/unbound key, since a rebound key's events always reach it.
		if (mapping.isUnbound() || mapping.isDefault()) {
			return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), InputConstants.KEY_G);
		}
		return false;
	}

	private void requestClose() {
		if (closing) {
			return;
		}
		closing = true;
		closingTicks = 0;
		hovered = null;
		motion.close();
	}

	/** Hover feedback fires on change only — once per entry entered, never per frame. */
	private void updateHover(double mouseX, double mouseY) {
		MegumiShikigami now;
		if (closing) {
			now = null;
		} else {
			ShikigamiSelectorLayout.Slot hit = ShikigamiSelectorLayout.slotAt(slots, mouseX, mouseY);
			now = hit == null ? null : hit.type();
		}
		if (now != hovered) {
			hovered = now;
			if (now != null) {
				feedback.hover();
			}
		}
	}

	private void drawSlots(GuiGraphics graphics) {
		float strip = motion.stripProgress();
		MegumiShikigami selected = ClientMegumiShikigamiState.selected();
		for (int index = 0; index < slots.size(); index++) {
			float stagger = motion.slotStagger(index);
			if (stagger <= 0.0f) {
				continue;
			}
			ShikigamiSelectorLayout.Slot slot = slots.get(index);
			MegumiShikigami type = slot.type();
			MegumiShikigamiSlotState state = ClientMegumiShikigamiState.stateOf(type);
			boolean slotHovered = !closing && type == hovered;
			Set<String> flags = ShikigamiSelectorState.flags(type == selected, slotHovered, state);
			graphics.pose().pushMatrix();
			float offsetX = motion.shakeOffset(index);
			float slotRise = (1.0f - stagger) * SLOT_RISE_PX;
			// 2D elements already carry the strip rise via the outer translate in render();
			// the PiP box ignores the pose stack, so Motion gets the full offset.
			graphics.pose().translate(offsetX, slotRise);
			ShikigamiSlotView.render(graphics, this.font, slot, state, flags,
					new ShikigamiSlotView.Motion(stagger, motion.hoverScale(index, slotHovered), motion.pulseT(index),
							offsetX, slotRise + (1.0f - strip) * STRIP_RISE_PX));
			graphics.pose().popMatrix();
		}
	}

	private void drawPanel(GuiGraphics graphics, float alpha) {
		Font font = this.font;
		ShikigamiSelectorLayout.Slot first = slots.get(0);
		ShikigamiSelectorLayout.Slot last = slots.get(slots.size() - 1);
		int x = first.x() - PANEL_PAD;
		int y = first.y() - PANEL_PAD - HEADER_H;
		int w = last.x() + last.w() - first.x() + 2 * PANEL_PAD;
		int h = last.y() + last.h() - first.y() + 2 * PANEL_PAD + HEADER_H;

		for (int layer = 4; layer >= 1; layer--) {
			graphics.fill(x - layer, y + layer + 2, x + w + layer, y + h + layer + 2,
					SelectorTheme.withAlpha(0xFF000000, alpha * 0.05f * layer));
		}
		int top = SelectorTheme.withAlpha(SelectorTheme.PANEL_FILL, alpha);
		int bottom = SelectorTheme.withAlpha(SelectorTheme.lerp(SelectorTheme.PANEL_FILL, 0xFF000000, 0.5f), alpha);
		graphics.fillGradient(x + 2, y, x + w - 2, y + h, top, bottom);
		graphics.fillGradient(x, y + 2, x + w, y + h - 2, top, bottom);
		// Caramel gloss along the top edge, then the dark-red rules that close the panel.
		graphics.fillGradient(x + 2, y + 1, x + w - 2, y + 7,
				SelectorTheme.withAlpha(0x3AFFD9B0, alpha), SelectorTheme.withAlpha(0x00FFD9B0, alpha));
		int ruleY = y + HEADER_H - 3;
		graphics.fill(x + 5, ruleY, x + w - 5, ruleY + 1, SelectorTheme.withAlpha(SelectorTheme.ACCENT_DEEP, alpha * 0.9f));
		graphics.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, SelectorTheme.withAlpha(SelectorTheme.ACCENT_DEEP, alpha));
		graphics.fill(x + 2, y + h - 1, x + w - 2, y + h, SelectorTheme.withAlpha(SelectorTheme.ACCENT_GLOW, alpha * 0.85f));

		graphics.drawString(font, this.title, x + 7, y + 4,
				SelectorTheme.withAlpha(SelectorTheme.TEXT_MUTED, alpha), false);
		Component hint = closeHint();
		if (hint != null) {
			graphics.drawString(font, hint, x + w - 7 - font.width(hint), y + 4,
					SelectorTheme.withAlpha(SelectorTheme.TEXT_MUTED, alpha * 0.75f), false);
		}
	}

	/** The header's right-hand line: which key the player has to let go of to close the strip. */
	private static Component closeHint() {
		KeyMapping mapping = JujutsuKeybinds.quickSelectorKey;
		if (mapping == null) {
			return null;
		}
		return Component.translatable("selector.jujutsumod.megumi.close_hint", mapping.getTranslatedKeyMessage());
	}
}
