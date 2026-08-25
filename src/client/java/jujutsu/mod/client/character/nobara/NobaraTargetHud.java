package jujutsu.mod.client.character.nobara;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.client.ui.WorldToScreen;

/**
 * Screen-space target HUD for Nobara's embedded nails. One VfxDirector HUD contribution draws,
 * for every living target carrying locally-owned embedded nails, a thin light bracket line just
 * right of the target's silhouette with rows to its right: name, a hairline divider, a compact
 * segmented HP bar, integer {@code current/max}, a 16x16 Minecraft-style nail icon with the local
 * player's embedded-nail count for that target, and the rank token from {@link NobaraEspRanks}.
 *
 * <p>Data comes from the existing {@link NobaraEspState} snapshot keyed by target entity id — one
 * entry per target, so one target never draws more than one HUD regardless of nail count.
 * World-to-screen projection lives in the shared {@link WorldToScreen} helper; anchors derive from
 * the target entity itself (its bounding box), never from a nail position. All animation state is
 * per-target and pruned every frame, so a vanished nail or target leaves nothing behind.
 *
 * <p>Rendering is deliberately vanilla GuiGraphics (fill / font / blit): no SDF glass, no MSDF
 * glyphs — the interface is meant to dissolve into Minecraft rather than compete with it.
 */
public final class NobaraTargetHud {
	private static final ResourceLocation NAIL_ICON = JujutsuMod.id("textures/gui/hud/nail_icon.png");

	// Palette — off-whites, muted red for HP, calm dark gray for empty segments, warm gold for rank.
	private static final int LINE = 0xFFF0EDE4;
	private static final int LINE_DIM = 0x55F0EDE4;
	private static final int HP_FILLED = 0xFFB23B35;
	private static final int HP_EMPTY = 0xFF56514A;
	private static final int RANK_GOLD = 0xFFD6B05C;

	/** Anchor height on the target body: upper-half feel, like the reference. */
	private static final double CHEST_FRACTION = 0.60;
	private static final double BLOCKS_TO_PX = 16.0;
	/** Visual gap between the target silhouette and the bracket line. */
	private static final float SILHOUETTE_GAP_PX = 3f;
	private static final float EDGE_MARGIN = 4f;

	/** Per-target animation memory; pruned against the live snapshot each frame. */
	private static final Map<Integer, TargetUiState> STATES = new HashMap<>();

	private static final class TargetUiState {
		long firstSeenGameTime;
		float shownHp;
		float shownMaxHp;
		float lastRawHp;
		/** Last frame's gameTime + partialTick, for FPS-independent HP chasing. */
		double lastFrameTime = Double.NaN;
	}

	private NobaraTargetHud() {}

	/** HUD contribution callback; draws only while the snapshot has targets (non-Nobara is empty). */
	public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			STATES.clear();
			return;
		}
		Map<Integer, NobaraEspState.TargetEsp> snapshot = NobaraEspState.snapshot();
		if (snapshot.isEmpty()) {
			STATES.clear();
			return;
		}

		Camera cam = Camera.of(client);
		Font font = client.font;
		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		float partialTick = tickCounter.getGameTimeDeltaPartialTick(false);
		long gameTime = client.level.getGameTime();

		// One placement pass per frame: track() advances animation memory exactly once, and shapes
		// and texts draw from the same resolved placements so they can never disagree.
		List<Placement> placements = new ArrayList<>();
		for (Map.Entry<Integer, NobaraEspState.TargetEsp> entry : snapshot.entrySet()) {
			if (!(client.level.getEntity(entry.getKey()) instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			Placement place = place(client, cam, font, guiWidth, guiHeight, partialTick, gameTime, entry.getValue(), living);
			if (place != null) {
				placements.add(place);
			}
		}

		for (Placement place : placements) {
			drawBlock(graphics, font, place);
		}

		prune(snapshot);
	}

	private static void drawBlock(GuiGraphics graphics, Font font, Placement p) {
		float alpha = p.alpha;
		float scale = p.scale;
		NobaraTargetLayout.Block b = p.block;

		// Bracket line right of the silhouette: vertical hairline + short top/bottom caps.
		int bx = Math.round(b.x());
		int by = Math.round(b.y());
		int cap = Math.max(1, Math.round(NobaraTargetLayout.BRACKET_CAP * scale));
		graphics.fill(bx, by, bx + 1, by + Math.round(b.h()), withAlpha(LINE, alpha));
		graphics.fill(bx, by, bx + cap, by + 1, withAlpha(LINE, alpha));
		graphics.fill(bx, by + Math.round(b.h()) - 1, bx + cap, by + Math.round(b.h()), withAlpha(LINE, alpha));

		// Hairline divider under the name, same width as the HP bar.
		int dividerW = Math.max(1, Math.round(b.hpW()));
		graphics.fill(Math.round(b.contentX()), Math.round(b.dividerY()),
				Math.round(b.contentX()) + dividerW, Math.round(b.dividerY()) + 1,
				withAlpha(LINE_DIM, alpha));

		// Compact segmented HP bar: filled muted red, empty calm dark gray, 1px gaps.
		int filled = NobaraTargetLayout.filledSegments(p.ui.shownHp, p.ui.shownMaxHp);
		int segW = Math.max(1, Math.round(NobaraTargetLayout.HP_SEGMENT_W * scale));
		int segGap = Math.max(1, Math.round(NobaraTargetLayout.HP_SEGMENT_GAP * scale));
		int segH = Math.max(1, Math.round(NobaraTargetLayout.HP_H * scale));
		int segX = Math.round(b.contentX());
		int segY = Math.round(b.hpY());
		for (int i = 0; i < NobaraTargetLayout.HP_SEGMENTS; i++) {
			int color = i < filled ? HP_FILLED : HP_EMPTY;
			graphics.fill(segX, segY, segX + segW, segY + segH, withAlpha(color, alpha));
			segX += segW + segGap;
		}

		// Nail icon + count row.
		int iconPx = Math.max(1, Math.round(NobaraTargetLayout.NAIL_H * scale));
		graphics.blit(RenderPipelines.GUI_TEXTURED, NAIL_ICON,
				Math.round(b.contentX()), Math.round(b.nailY()),
				0.0f, 0.0f, iconPx, iconPx, 16, 16, 16, 16);

		// Rank cell: 1px outline square + gold token right of it.
		int cell = Math.max(1, Math.round(NobaraTargetLayout.RANK_CELL * scale));
		drawHollowRect(graphics, Math.round(b.contentX()), Math.round(b.rankY()), cell, withAlpha(LINE, alpha));

		// Texts — vanilla font with shadow, the same path the other HUD chips use.
		drawStringShadowed(graphics, font, p.name, Math.round(b.contentX()), Math.round(b.nameY()),
				withAlpha(LINE, alpha));
		drawStringShadowed(graphics, font, p.ratio, Math.round(b.contentX()), Math.round(b.ratioY()),
				withAlpha(LINE, alpha));

		float countX = b.contentX() + iconPx + NobaraTargetLayout.COUNT_GAP * scale;
		float countY = b.nailY() + (NobaraTargetLayout.NAIL_H * scale - 9f) / 2f;
		drawStringShadowed(graphics, font, p.count, Math.round(countX), Math.round(countY),
				withAlpha(LINE, alpha));

		float tokenX = b.contentX() + (NobaraTargetLayout.RANK_CELL + NobaraTargetLayout.ROW_GAP) * scale;
		float tokenY = b.rankY() + (NobaraTargetLayout.RANK_CELL * scale - 9f) / 2f;
		drawStringShadowed(graphics, font, p.rankToken, Math.round(tokenX), Math.round(tokenY),
				withAlpha(RANK_GOLD, alpha));
	}

	private record Placement(String name, String ratio, String count, String rankToken,
			NobaraTargetLayout.Block block, TargetUiState ui, float alpha, float scale) {}

	/** Resolves the target on screen and advances its animation memory; null when off-screen. */
	private static Placement place(Minecraft client, Camera cam, Font font, int guiWidth, int guiHeight,
			float partialTick, long gameTime, NobaraEspState.TargetEsp esp, LivingEntity living) {
		TargetUiState ui = track(gameTime, partialTick, esp, living);
		Vec3 chest = living.getPosition(partialTick).add(0.0, living.getBbHeight() * CHEST_FRACTION, 0.0);
		WorldToScreen.Projection screen = WorldToScreen.project(
				chest.x - cam.x(), chest.y - cam.y(), chest.z - cam.z(),
				cam.pitchDeg(), cam.yawDeg(), cam.fovDeg(), guiWidth, guiHeight);
		if (!screen.visible()) {
			return null;
		}
		double depthBlocks = Math.max(1.0, Math.sqrt(living.distanceToSqr(cam.x(), cam.y(), cam.z())));
		float scale = NobaraTargetLayout.attachScale(depthBlocks);
		float alpha = NobaraTargetAnim.appearAlpha(gameTime - ui.firstSeenGameTime, partialTick);
		float slide = NobaraTargetAnim.slideOffsetPx(gameTime - ui.firstSeenGameTime, partialTick);

		String name = living.getDisplayName().getString();
		String ratio = NobaraTargetLayout.hpRatioText(ui.shownHp, ui.shownMaxHp);
		String count = Integer.toString(esp.nailCount());
		String rankToken = NobaraTargetLayout.gradeDisplay(rankKeyFor(living));

		float nameW = font.width(name);
		float countW = font.width(count);
		float rankW = font.width(rankToken);
		NobaraTargetLayout.Block probe = NobaraTargetLayout.block(0f, 0f, scale, nameW, countW, rankW);

		float bbWidthPx = clampPx((float) (living.getBbWidth() * scale * BLOCKS_TO_PX), 12f, 40f);
		float attachX = (float) screen.x() + bbWidthPx / 2f + SILHOUETTE_GAP_PX + slide;
		float attachY = (float) screen.y() - probe.h() / 2f;
		double[] clamped = WorldToScreen.clampToScreen(attachX, attachY, probe.w(), probe.h(),
				guiWidth, guiHeight, EDGE_MARGIN);
		attachX = (float) clamped[0];
		attachY = (float) clamped[1];

		NobaraTargetLayout.Block block = NobaraTargetLayout.block(attachX, attachY, scale, nameW, countW, rankW);
		return new Placement(name, ratio, count, rankToken, block, ui, alpha, scale);
	}

	private static TargetUiState track(long gameTime, float partialTick, NobaraEspState.TargetEsp esp, LivingEntity living) {
		TargetUiState ui = STATES.computeIfAbsent(esp.targetId(), id -> {
			TargetUiState fresh = new TargetUiState();
			fresh.firstSeenGameTime = gameTime;
			fresh.shownHp = living.getHealth();
			fresh.shownMaxHp = living.getMaxHealth();
			fresh.lastRawHp = living.getHealth();
			return fresh;
		});
		float hp = living.getHealth();
		if (hp != ui.lastRawHp) {
			ui.lastRawHp = hp;
		}
		// Displayed values chase the live ones every frame, at a rate independent of FPS:
		// UiEase.approach is tick-exponential, so pass the real elapsed ticks between frames.
		double frameTime = gameTime + partialTick;
		double deltaTicks = Double.isNaN(ui.lastFrameTime)
				? 1.0
				: Math.max(0.0, Math.min(4.0, frameTime - ui.lastFrameTime));
		ui.lastFrameTime = frameTime;
		ui.shownHp = NobaraTargetAnim.approachValue(ui.shownHp, hp, (float) deltaTicks);
		ui.shownMaxHp = NobaraTargetAnim.approachValue(ui.shownMaxHp, living.getMaxHealth(), (float) deltaTicks);
		return ui;
	}

	private static void prune(Map<Integer, NobaraEspState.TargetEsp> snapshot) {
		Iterator<Map.Entry<Integer, TargetUiState>> it = STATES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, TargetUiState> entry = it.next();
			if (!snapshot.containsKey(entry.getKey())) {
				it.remove();
			}
		}
	}

	private static String rankKeyFor(LivingEntity living) {
		if (living instanceof Player targetPlayer) {
			JujutsuCharacter vessel = ClientCharacterSelectionManager.characterOrNone(targetPlayer.getUUID());
			String vesselGradeKey = vessel != JujutsuCharacter.NONE
					? JujutsuCharacterClients.definition(vessel).rosterEntry().subtitleKey()
					: null;
			return NobaraEspRanks.rankKey(true, vesselGradeKey, living.getMaxHealth());
		}
		return NobaraEspRanks.rankKey(false, null, living.getMaxHealth());
	}

	private static float clampPx(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	/** Multiplies only the alpha channel of an ARGB color, keeping hue. */
	private static int withAlpha(int argb, float alpha) {
		int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	private static void drawHollowRect(GuiGraphics graphics, int x, int y, int size, int color) {
		graphics.fill(x, y, x + size, y + 1, color);
		graphics.fill(x, y + size - 1, x + size, y + size, color);
		graphics.fill(x, y, x + 1, y + size, color);
		graphics.fill(x + size - 1, y, x + size, y + size, color);
	}

	/** Plain shadowed vanilla-font string; the exact rendering path the other HUD chips use. */
	private static void drawStringShadowed(GuiGraphics graphics, Font font, String text,
			int x, int y, int color) {
		if (text.isEmpty()) {
			return;
		}
		graphics.drawString(font, text, x, y, color, true);
	}

	/** Snapshot of the camera values the projection needs, taken once per frame. */
	private record Camera(double x, double y, double z, float pitchDeg, float yawDeg, float fovDeg) {
		static Camera of(Minecraft client) {
			Vec3 pos = client.gameRenderer.getMainCamera().getPosition();
			return new Camera(pos.x, pos.y, pos.z,
					client.player.getXRot(), client.player.getYRot(),
					client.options.fov().get().intValue());
		}
	}
}