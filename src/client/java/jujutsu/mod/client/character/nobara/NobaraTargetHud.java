package jujutsu.mod.client.character.nobara;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.client.ui.WorldToScreen;
import jujutsu.mod.client.ui.msdf.MsdfFonts;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import jujutsu.mod.client.vfx.VfxDirector;

/**
 * Screen-space target HUD for Nobara's embedded nails, replacing the former world-space
 * billboard that rode the nail renderer. One VfxDirector HUD contribution draws, for every
 * living target carrying locally-owned embedded nails: a name pill above the head and a
 * vertical glass card stack to the target's right — health (ring + percent + ratio), grade
 * (star glyph + {@link NobaraTargetLayout#gradeDisplay}), and nails (icon + count).
 *
 * <p>Data comes from the existing {@link NobaraEspState} snapshot keyed by target entity id;
 * the leader-nail concept is gone. World-to-screen projection lives in the shared
 * {@link WorldToScreen} helper. All animation state is per-target and pruned every frame,
 * so a vanished nail or target leaves nothing behind.
 */
public final class NobaraTargetHud {
	private static final SdfRenderer SDF = new SdfRenderer();

	// Palette (fake glass; no framebuffer blur in v1).
	private static final int GLASS_TOP = 0xE6141820;
	private static final int GLASS_BOTTOM = 0xC60E1118;
	private static final int BORDER = 0x59FFFFFF;
	private static final int GLOW = 0x38E48A36;
	private static final int TEXT_MAIN = 0xFFF2F5FA;
	private static final int TEXT_SUB = 0xFF9AA7B5;
	private static final int HEALTH_RING = 0xFFFF5A6E;
	private static final int NAIL_TOP = 0xFFC8D2DC;
	private static final int NAIL_BOTTOM = 0xFF8FA0AE;
	private static final int STAR_GOLD = 0xFFFFC94D;
	private static final int BADGE_BG = 0xCC1B2733;

	private static final float RADIUS = 8f;
	private static final float BORDER_WIDTH = 1f;
	private static final float GLOW_RADIUS = 6f;
	private static final float HIGHLIGHT = 0.35f;
	private static final float BADGE_HEIGHT = 12f;
	private static final double CHEST_FRACTION = 0.62;
	private static final double HEAD_OFFSET_BLOCKS = 0.35;
	private static final double BLOCKS_TO_PX = 16.0;
	private static final float STACK_GAP_PX = 10f;
	private static final float SCREEN_MARGIN = 4f;

	/** Per-target animation memory; pruned against the live snapshot each frame. */
	private static final Map<Integer, TargetUiState> STATES = new HashMap<>();

	private static final class TargetUiState {
		long firstSeenGameTime;
		long lastSeenGameTime;
		float shownHp;
		float shownMaxHp;
		float lastRawHp;
		long hpChangedAtGameTime = Long.MIN_VALUE;
		long countChangedAtGameTime = Long.MIN_VALUE;
		int lastCount;
	}

	private NobaraTargetHud() {}

	/** Registers this HUD into the single VfxDirector HUD element. Call from client hooks. */
	public static void register() {
		VfxDirector.registerHudContribution(JujutsuMod.id("nobara_target_hud"), NobaraTargetHud::render);
	}

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
		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		float partialTick = tickCounter.getGameTimeDeltaPartialTick(false);
		long gameTime = client.level.getGameTime();

		// One placement pass per frame: track() advances animation memory exactly once,
		// and shapes/texts draw from the same resolved placements so they can never disagree.
		List<Placement> placements = new ArrayList<>();
		for (Map.Entry<Integer, NobaraEspState.TargetEsp> entry : snapshot.entrySet()) {
			if (!(client.level.getEntity(entry.getKey()) instanceof LivingEntity living) || !living.isAlive()) {
				continue;
			}
			Placement place = place(client, cam, guiWidth, guiHeight, partialTick, gameTime, entry.getValue(), living);
			if (place != null) {
				placements.add(place);
			}
		}

		SDF.begin();
		for (Placement place : placements) {
			drawShapes(place);
		}
		SDF.flush();

		for (Placement place : placements) {
			drawTexts(place);
		}
		MsdfFonts.endFrame();

		prune(snapshot);
	}

	private static void drawShapes(Placement place) {
		float alpha = place.alpha;
		addGlassCard(place.health.x(), place.health.y(), place.health.w(), place.health.h(), alpha, place.pulse);
		addGlassCard(place.grade.x(), place.grade.y(), place.grade.w(), place.grade.h(), alpha, 0f);
		addGlassCard(place.nails.x(), place.nails.y(), place.nails.w(), place.nails.h(), alpha, 0f);

		// Health ring inside the health card.
		float ringSize = place.health.h() * 0.42f;
		SDF.add(SdfShape.builder()
				.rect(place.health.x() + 7f * place.scale, place.health.y() + (place.health.h() - ringSize) / 2f,
						ringSize, ringSize)
				.radius(ringSize / 2f)
				.border(2f, blendAlpha(HEALTH_RING, alpha))
				.fill(0, 0)
				.build());

		// Nail icon strip inside the nails card.
		float pop = place.pop;
		float iconW = 4f * place.scale;
		float iconH = 14f * place.scale * pop;
		SDF.add(SdfShape.builder()
				.rect(place.nails.x() + 10f * place.scale,
						place.nails.y() + (place.nails.h() - iconH) / 2f, iconW, iconH)
				.radius(2f * place.scale)
				.border(0f, 0)
				.fill(blendAlpha(NAIL_TOP, alpha), blendAlpha(NAIL_BOTTOM, alpha))
				.build());

		// Name pill above the head; its text is staged for the text pass.
		Badge badge = place.badge;
		if (badge == null) {
			return;
		}
		SDF.add(SdfShape.builder()
				.rect(badge.x(), badge.y(), badge.w(), BADGE_HEIGHT)
				.radius(BADGE_HEIGHT / 2f)
				.border(BORDER_WIDTH, blendAlpha(BORDER, alpha))
				.glow(4f, blendAlpha(GLOW, alpha * 0.6f))
				.highlight(0.25f)
				.fill(blendAlpha(BADGE_BG, alpha), blendAlpha(BADGE_BG, alpha))
				.build());
	}

	private static void drawTexts(Placement place) {
		float alpha = place.alpha;
		float scale = place.scale;

		NobaraTargetLayout.Card health = place.health;
		float ringSize = health.h() * 0.42f;
		float pctX = health.x() + 7f * scale + ringSize + 8f * scale;
		MsdfFonts.draw(MsdfFonts.Face.BOLD, NobaraTargetLayout.hpPercentText(place.ui.shownHp, place.ui.shownMaxHp),
				pctX, health.y() + health.h() * 0.22f, 9f * scale, blendAlpha(TEXT_MAIN, alpha));
		MsdfFonts.draw(MsdfFonts.Face.UI, NobaraTargetLayout.hpRatioText(place.ui.shownHp, place.ui.shownMaxHp),
				pctX, health.y() + health.h() * 0.58f, 4.5f * scale, blendAlpha(TEXT_SUB, alpha));

		NobaraTargetLayout.Card grade = place.grade;
		MsdfFonts.drawIcon("D", grade.x() + 8f * scale, grade.y() + grade.h() / 2f - 5.5f * scale,
				11f * scale, blendAlpha(STAR_GOLD, alpha));
		MsdfFonts.draw(MsdfFonts.Face.BOLD, NobaraTargetLayout.gradeDisplay(place.rankKey),
				grade.x() + grade.w() * 0.55f, grade.y() + grade.h() / 2f - 4.5f * scale,
				9f * scale, blendAlpha(TEXT_MAIN, alpha));

		NobaraTargetLayout.Card nails = place.nails;
		MsdfFonts.draw(MsdfFonts.Face.BOLD, "×" + place.nailCount(),
				nails.x() + 18f * scale, nails.y() + nails.h() / 2f - 4f * scale * place.pop,
				8f * scale * place.pop, blendAlpha(TEXT_MAIN, alpha));

		Badge badge = place.badge;
		if (badge != null) {
			MsdfFonts.drawCentered(MsdfFonts.Face.UI, badge.text(), badge.x() + badge.w() / 2f,
					badge.y() + BADGE_HEIGHT / 2f - 2.2f, 4.5f, blendAlpha(TEXT_MAIN, alpha));
		}
	}

	private record Placement(NobaraTargetLayout.Card health, NobaraTargetLayout.Card grade,
			NobaraTargetLayout.Card nails, TargetUiState ui, float alpha, float pop,
			float pulse, float scale, String rankKey, int nailCount, Badge badge) {}

	/** Name pill geometry + text, resolved once in the placement pass. */
	private record Badge(String text, float x, float y, float w) {}

	/** Resolves the target on screen and advances its animation memory; null when off-screen. */
	private static Placement place(Minecraft client, Camera cam, int guiWidth, int guiHeight,
			float partialTick, long gameTime, NobaraEspState.TargetEsp esp, LivingEntity living) {
		TargetUiState ui = track(gameTime, esp, living);
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
		float pop = ui.countChangedAtGameTime == Long.MIN_VALUE ? 1f
				: NobaraTargetAnim.popScale(gameTime - ui.countChangedAtGameTime, partialTick);
		float pulse = ui.hpChangedAtGameTime == Long.MIN_VALUE ? 0f
				: NobaraTargetAnim.pulseAlpha(gameTime - ui.hpChangedAtGameTime, partialTick);

		float stackW = NobaraTargetLayout.CARD_W * scale;
		float stackH = (NobaraTargetLayout.HEALTH_H + NobaraTargetLayout.SMALL_H * 2
				+ NobaraTargetLayout.GAP * 2) * scale;
		float bbWidthPx = clampPx((float) (living.getBbWidth() * scale * BLOCKS_TO_PX), 12f, 40f);
		float attachX = (float) screen.x() + bbWidthPx / 2f + STACK_GAP_PX + slide;
		float attachY = (float) screen.y() - stackH / 2f;
		double[] clamped = WorldToScreen.clampToScreen(attachX, attachY, stackW, stackH,
				guiWidth, guiHeight, SCREEN_MARGIN);
		attachX = (float) clamped[0];
		attachY = (float) clamped[1];

		return new Placement(
				NobaraTargetLayout.healthCard(attachX, attachY, scale),
				NobaraTargetLayout.gradeCard(attachX, attachY, scale),
				NobaraTargetLayout.nailsCard(attachX, attachY, scale),
				ui, alpha, pop, pulse, scale,
				rankKeyFor(living), esp.nailCount(),
				resolveBadge(living, cam, guiWidth, guiHeight, partialTick));
	}

	/** Projects the name pill above the head; null when the head is off-screen. */
	private static Badge resolveBadge(LivingEntity living, Camera cam, int guiWidth, int guiHeight,
			float partialTick) {
		Vec3 head = living.getPosition(partialTick).add(0.0, living.getBbHeight() + HEAD_OFFSET_BLOCKS, 0.0);
		WorldToScreen.Projection headScreen = WorldToScreen.project(
				head.x - cam.x(), head.y - cam.y(), head.z - cam.z(),
				cam.pitchDeg(), cam.yawDeg(), cam.fovDeg(), guiWidth, guiHeight);
		if (!headScreen.visible()) {
			return null;
		}
		String name = living.getDisplayName().getString();
		float w = MsdfFonts.width(MsdfFonts.Face.UI, name, 4.5f) + 10f;
		double[] pos = WorldToScreen.clampToScreen(
				headScreen.x() - w / 2f, headScreen.y() - BADGE_HEIGHT - SCREEN_MARGIN,
				w, BADGE_HEIGHT, guiWidth, guiHeight, SCREEN_MARGIN);
		return new Badge(name, (float) pos[0], (float) pos[1], w);
	}

	private static TargetUiState track(long gameTime, NobaraEspState.TargetEsp esp, LivingEntity living) {
		TargetUiState ui = STATES.computeIfAbsent(esp.targetId(), id -> {
			TargetUiState fresh = new TargetUiState();
			fresh.firstSeenGameTime = gameTime;
			fresh.shownHp = living.getHealth();
			fresh.shownMaxHp = living.getMaxHealth();
			fresh.lastRawHp = living.getHealth();
			fresh.lastCount = esp.nailCount();
			return fresh;
		});
		ui.lastSeenGameTime = gameTime;
		float hp = living.getHealth();
		if (hp != ui.lastRawHp) {
			ui.hpChangedAtGameTime = gameTime;
			ui.lastRawHp = hp;
		}
		if (esp.nailCount() != ui.lastCount) {
			ui.countChangedAtGameTime = gameTime;
			ui.lastCount = esp.nailCount();
		}
		// Displayed values chase the live ones every frame.
		ui.shownHp = NobaraTargetAnim.approachValue(ui.shownHp, hp, 1.0f);
		ui.shownMaxHp = NobaraTargetAnim.approachValue(ui.shownMaxHp, living.getMaxHealth(), 1.0f);
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

	private static void addGlassCard(float x, float y, float w, float h, float alpha, float pulse) {
		SDF.add(SdfShape.builder()
				.rect(x, y, w, h)
				.radius(RADIUS)
				.border(BORDER_WIDTH + pulse, blendAlpha(BORDER, alpha))
				.glow(GLOW_RADIUS, blendAlpha(GLOW, alpha))
				.highlight(HIGHLIGHT)
				.fill(blendAlpha(GLASS_TOP, alpha), blendAlpha(GLASS_BOTTOM, alpha))
				.build());
	}

	private static float clampPx(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	/** Multiplies only the alpha channel of an ARGB color, keeping hue. */
	private static int blendAlpha(int argb, float alpha) {
		int a = (int) (((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0xFFFFFF);
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
