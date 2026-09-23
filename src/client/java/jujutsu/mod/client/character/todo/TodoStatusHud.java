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
 * Todo's three compact HUD chips, contributed to the director's single combat HUD callback: the live
 * stone (remaining seconds + the swap hint), the live pair selection (target name + remaining TTL +
 * the cycle hint), and cue-fed Rhythm stages. All render only while the local player is Todo and
 * fail open when their authoritative state has lapsed.
 */
public final class TodoStatusHud {
	private static final int BACKGROUND = 0xB8121818;
	private static final int TEXT = 0xFFE5F1EF;
	private static final int HINT = 0x99E5F1EF;
	private static final int ACCENT = 0xFFA56CFF;
	private static final int PEAK_ACCENT = 0xFFE6C45A;
	private static final int REVISED_ACCENT = 0xFFE1B6FF;
	/** Chip size and spacing match MegumiCooldownHud's bar; the pair chip stacks below the stone. */
	private static final int CHIP_HEIGHT = 36;
	private static final int CHIP_GAP = 6;

	/** The stone is re-resolved by id every frame; no entity or level reference is ever pinned. */
	private static int cachedStoneId = -1;
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
		int entityId = TodoPairSelectionClientState.newestLiveId(gameTime);
		if (entityId == -1) {
			return;
		}
		long remaining = TodoPairSelectionClientState.remainingTicks(entityId, gameTime);
		if (remaining <= 0 || !(level.getEntity(entityId) instanceof LivingEntity living) || !living.isAlive()) {
			return;
		}
		// The marked body's name rides in the hint line; a selection is a name to remember.
		Component hint = Component.translatable("hud.jujutsumod.todo.pair.hint", living.getDisplayName());
		drawChip(graphics, client.font, chipY(graphics) + CHIP_HEIGHT + CHIP_GAP, ACCENT,
				Component.translatable("hud.jujutsumod.todo.pair"),
				hint, seconds(remaining));
	}
	/**
	 * Third Todo chip: five staged pips (Beat 0..3 plus Peak) and, while Revised is active, a
	 * compact countdown bar. The state is fed exclusively by rhythm VFX cues.
	 */
	public static void renderRhythm(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (client.player == null || level == null || !isTodo(client)) {
			return;
		}
		int beat = TodoRhythmClientState.beat();
		int revisedTicks = TodoRhythmClientState.revisedTicks();
		int y = chipY(graphics) + 2 * (CHIP_HEIGHT + CHIP_GAP);
		Font font = client.font;
		Component rhythm = Component.translatable("hud.jujutsumod.todo.rhythm");
		Component revised = Component.translatable("hud.jujutsumod.todo.revised");
		Component label = revisedTicks > 0 ? revised : rhythm;
		int width = Math.max(170, font.width(label) + 54);
		int x = 12;
		int accent = revisedTicks > 0 ? REVISED_ACCENT : ACCENT;
		graphics.fill(x, y, x + width, y + CHIP_HEIGHT, BACKGROUND);
		graphics.fill(x, y, x + 3, y + CHIP_HEIGHT, accent);
		graphics.drawString(font, label, x + 8, y + 5, TEXT, false);
		int peakColor = PEAK_ACCENT;
		if (beat == 4) {
			double pulse = 0.65 + 0.35 * (0.5 + 0.5
					* Math.sin((level.getGameTime() + tickCounter.getGameTimeDeltaPartialTick(false)) * 0.22));
			int peakAlpha = Math.round(255.0 * pulse);
			peakColor = (peakAlpha << 24) | (PEAK_ACCENT & 0x00FFFFFF);
			Component peak = Component.translatable("hud.jujutsumod.todo.rhythm.peak");
			graphics.drawString(font, peak, x + width - font.width(peak) - 7, y + 5, peakColor, false);
		}
		int pipY = y + 18;
		for (int pip = 0; pip < 5; pip++) {
			int pipX = x + 8 + pip * 18;
			boolean filled = pip <= beat;
			int color = filled ? (pip == 4 ? peakColor : accent) : 0x553F4650;
			graphics.fill(pipX, pipY, pipX + 12, pipY + 7, color);
		}
		if (revisedTicks > 0) {
			int barX = x + 8;
			int barY = y + 29;
			int barWidth = width - 16;
			graphics.fill(barX, barY, barX + barWidth, barY + 3, 0x553F4650);
			int filledWidth = Math.round(barWidth * (revisedTicks / 120.0f));
			graphics.fill(barX, barY, barX + filledWidth, barY + 3, REVISED_ACCENT);
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
	 * The local player's live stone, or null. The id-based cache is re-resolved through
	 * {@code level.getEntity(int)} every frame, so a disconnect or dimension change can never pin a
	 * dead level through a static field; the full scan runs at most once per client tick.
	 */
	private static TodoStoneEntity liveStone(Minecraft client, ClientLevel level) {
		TodoStoneEntity cached = resolveOwnStone(client, level, cachedStoneId);
		if (cached != null) {
			return cached;
		}
		cachedStoneId = -1;
		if (level.getGameTime() == lastStoneScanTick) {
			return null;
		}
		lastStoneScanTick = level.getGameTime();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof TodoStoneEntity stone
					&& stone.clientOwnerUuid().filter(client.player.getUUID()::equals).isPresent()) {
				cachedStoneId = stone.getId();
				return stone;
			}
		}
		return null;
	}

	private static TodoStoneEntity resolveOwnStone(Minecraft client, ClientLevel level, int entityId) {
		if (entityId == -1) {
			return null;
		}
		return level.getEntity(entityId) instanceof TodoStoneEntity stone
				&& !stone.isRemoved()
				&& stone.clientOwnerUuid().filter(client.player.getUUID()::equals).isPresent()
				? stone
				: null;
	}
}
