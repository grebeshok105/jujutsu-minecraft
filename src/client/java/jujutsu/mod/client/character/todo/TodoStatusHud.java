package jujutsu.mod.client.character.todo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.todo.TodoStoneEntity;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;

/**
 * Todo's two compact HUD chips, contributed to the director's single combat HUD callback: the
 * live stone (remaining seconds + the swap hint) and the live pair selection (target name +
 * remaining TTL + the cycle hint). Both render only while the local player is Todo, and both fail
 * open — a stone that is gone or a selection whose cache entry lapsed simply stop drawing.
 */
public final class TodoStatusHud {
	private static final int BACKGROUND = 0xB8121818;
	private static final int TEXT = 0xFFE5F1EF;
	private static final int HINT = 0x99E5F1EF;
	private static final int ACCENT = 0xFFA56CFF;
	/** Chip size and spacing match MegumiCooldownHud's bar; the pair chip stacks below the stone. */
	private static final int CHIP_HEIGHT = 36;
	private static final int CHIP_GAP = 6;

	private static TodoStoneEntity cachedStone;
	private static ClientLevel cachedLevel;
	private static long lastStoneScanTick = Long.MIN_VALUE;

	private TodoStatusHud() {}

	public static void renderStone(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (client.player == null || level == null || !isTodo(client)) {
			return;
		}
		TodoStoneEntity stone = liveStone(client, level);
		if (stone == null || stone.remainingTicks() <= 0) {
			return;
		}
		drawChip(graphics, client.font, chipY(graphics), ACCENT,
				Component.translatable("hud.jujutsumod.todo.stone"),
				Component.translatable("hud.jujutsumod.todo.stone.hint"),
				seconds(stone.remainingTicks()));
	}

	public static void renderPair(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (client.player == null || level == null || !isTodo(client)) {
			return;
		}
		long gameTime = level.getGameTime();
		for (int entityId : TodoPairSelectionClientState.entityIds()) {
			long remaining = TodoPairSelectionClientState.remainingTicks(entityId, gameTime);
			if (remaining <= 0) {
				continue;
			}
			Entity entity = level.getEntity(entityId);
			if (!(entity instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			// The marked body's name rides in the hint line; a selection is a name to remember.
			Component hint = Component.translatable("hud.jujutsumod.todo.pair.hint", living.getDisplayName());
			drawChip(graphics, client.font, chipY(graphics) + CHIP_HEIGHT + CHIP_GAP, ACCENT,
					Component.translatable("hud.jujutsumod.todo.pair"),
					hint, seconds(remaining));
			return;
		}
	}

	private static void drawChip(GuiGraphics graphics, Font font, int y, int accent,
			Component label, Component hint, String value) {
		int width = Math.max(120, font.width(label) + font.width(value) + 28);
		int x = 12;
		graphics.fill(x, y, x + width, y + CHIP_HEIGHT, BACKGROUND);
		graphics.fill(x, y, x + 3, y + CHIP_HEIGHT, accent);
		graphics.drawString(font, label, x + 8, y + 5, TEXT, false);
		graphics.drawString(font, hint, x + 8, y + 18, HINT, false);
		graphics.drawString(font, value, x + width - font.width(value) - 7, y + 5, accent, false);
	}

	private static int chipY(GuiGraphics graphics) {
		return Math.max(12, graphics.guiHeight() / 2 - 13);
	}

	private static String seconds(long ticks) {
		return ((ticks + 19) / 20) + "s";
	}

	private static boolean isTodo(Minecraft client) {
		return ClientCharacterSelectionManager.characterOrNone(client.player.getUUID()) == JujutsuCharacter.TODO;
	}

	/**
	 * The local player's live stone, or null. Scanned at most once per client tick; between ticks
	 * the cached reference is reused while the entity is still in the level.
	 */
	private static TodoStoneEntity liveStone(Minecraft client, ClientLevel level) {
		if (cachedLevel == level && cachedStone != null && !cachedStone.isRemoved()) {
			return cachedStone;
		}
		cachedLevel = level;
		cachedStone = null;
		if (level.getGameTime() == lastStoneScanTick) {
			return null;
		}
		lastStoneScanTick = level.getGameTime();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof TodoStoneEntity stone
					&& stone.clientOwnerUuid().filter(client.player.getUUID()::equals).isPresent()) {
				cachedStone = stone;
				break;
			}
		}
		return cachedStone;
	}
}
