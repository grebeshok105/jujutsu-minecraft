package jujutsu.mod.client.vfx.megumi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.world.VfxWorldGeometry;
import jujutsu.mod.vfx.VfxAnchorResolver;
import jujutsu.mod.vfx.VfxCue;

/** World pass for unstable Nue discharge ribbons and the directional elephant jet audit visual. */
public final class NueArcRenderer {
	private static final int ELECTRIC_DARK_R = 16;
	private static final int ELECTRIC_DARK_G = 56;
	private static final int ELECTRIC_DARK_B = 92;
	private static final int ELECTRIC_R = 92;
	private static final int ELECTRIC_G = 194;
	private static final int ELECTRIC_B = 255;
	private static final int ELECTRIC_CORE_R = 224;
	private static final int ELECTRIC_CORE_G = 250;
	private static final int ELECTRIC_CORE_B = 255;
	private static final int WATER_DARK_R = 24;
	private static final int WATER_DARK_G = 92;
	private static final int WATER_DARK_B = 148;
	private static final int WATER_R = 92;
	private static final int WATER_G = 194;
	private static final int WATER_B = 240;
	private static final int WATER_CORE_R = 224;
	private static final int WATER_CORE_G = 250;
	private static final int WATER_CORE_B = 255;
	private static final int IMPACT_DURATION_TICKS = 6;
	private static final int ELEPHANT_JET_TTL_TICKS = 8;
	private static final double ELEPHANT_JET_LENGTH = 12.0;

	private static final NueArcState SHOCKS = NueArcState.shared();
	private static final List<VfxCue> ELEPHANT_JETS = new ArrayList<>();
	private static boolean registered;
	private static ClientLevel activeLevel;
	private static final long SEED_STEP = 0x9E3779B97F4A7C15L;
	private NueArcRenderer() {}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WorldRenderEvents.AFTER_ENTITIES.register(NueArcRenderer::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Called by the recipe on the cue's opening beat; the ribbon itself stays frame-dynamic. */
	public static void registerElephantJet(VfxCue cue) {
		if (cue == null || cue.anchorEntityId() == VfxCue.NO_ANCHOR) {
			return;
		}
		ELEPHANT_JETS.add(cue);
		if (ELEPHANT_JETS.size() > 24) {
			ELEPHANT_JETS.remove(0);
		}
	}

	static void clear() {
		SHOCKS.clear();
		ELEPHANT_JETS.clear();
		activeLevel = null;
	}

	private static void render(WorldRenderContext context) {
		ClientLevel level = context.world();
		MultiBufferSource consumers = context.consumers();
		if (level == null || consumers == null) {
			return;
		}
		if (activeLevel != level) {
			clear();
			activeLevel = level;
		}
		Camera camera = context.camera();
		Vec3 cameraPosition = camera.getPosition();
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());
		long gameTime = level.getGameTime();

		for (NueArcState.Arc arc : SHOCKS.activeAt(gameTime)) {
			Entity nue = level.getEntity(arc.nueEntityId());
			if (nue == null || nue.isRemoved()) {
				continue;
			}
			Vec3 start = nue.position().add(0.0, nue.getBbHeight() * 0.55, 0.0).subtract(cameraPosition);
			Vec3 target = arc.targetPoint().subtract(cameraPosition);
			renderShock(consumer, start, target, arc, gameTime, partialTick);
		}

		for (Iterator<VfxCue> iterator = ELEPHANT_JETS.iterator(); iterator.hasNext();) {
			VfxCue cue = iterator.next();
			float age = Math.max(0.0f, gameTime - cue.startGameTime() + partialTick);
			if (age >= ELEPHANT_JET_TTL_TICKS) {
				iterator.remove();
				continue;
			}
			Entity elephant = level.getEntity(cue.anchorEntityId());
			if (elephant == null || elephant.isRemoved()) {
				continue;
			}
			Vec3 trunk = VfxAnchorResolver.resolve(cue, id -> {
				Entity anchor = level.getEntity(id);
				return anchor == null ? null : anchor.position();
			}).subtract(cameraPosition);
			Vec3 direction = cue.direction().lengthSqr() < 1.0E-8
					? elephant.getLookAngle().normalize() : cue.direction();
			renderJet(consumer, trunk, trunk.add(direction.scale(ELEPHANT_JET_LENGTH)), cue, age);
		}
	}

	private static void renderShock(VertexConsumer consumer, Vec3 start, Vec3 target, NueArcState.Arc arc,
			long gameTime, float partialTick) {
		float age = arc.ageAt(gameTime, partialTick);
		float fade = age < IMPACT_DURATION_TICKS
				? 1.0f - age / (NueArcState.TTL_TICKS + 1.0f)
				: Math.max(0.0f, (NueArcState.TTL_TICKS - age) / 2.0f);
		int alpha = Math.max(0, Math.min(255, Math.round(230.0f * fade)));
		if (alpha <= 0) {
			return;
		}
		Vec3 delta = target.subtract(start);
		long jitterSeed = arc.seed() ^ ((long) Math.floor(age / 2.0f) * SEED_STEP);
		int strands = 2 + (int) (random(jitterSeed) * 2.0);
		for (int strand = 0; strand < strands; strand++) {
			drawJagged(consumer, start, target, jitterSeed ^ (strand * 0xD1B54A32D192ED03L),
				6 + (int) (random(jitterSeed ^ strand) * 2.0), 0.035f, alpha,
				ELECTRIC_DARK_R, ELECTRIC_DARK_G, ELECTRIC_DARK_B,
				ELECTRIC_R, ELECTRIC_G, ELECTRIC_B,
				ELECTRIC_CORE_R, ELECTRIC_CORE_G, ELECTRIC_CORE_B);
		}

		float impactAge = Math.min(IMPACT_DURATION_TICKS, Math.max(0.0f, age));
		float impactProgress = impactAge / IMPACT_DURATION_TICKS;
		int impactAlpha = Math.round(alpha * (1.0f - impactProgress));
		if (impactAlpha <= 0) {
			return;
		}
		Vec3[] basis = VfxWorldGeometry.directionalBasis(delta);
		float shellRadius = 0.16f + impactProgress * 0.88f;
		VfxWorldGeometry.renderDirectionalRing(consumer, target, basis[0], basis[1], shellRadius, 0.85f,
				impactAlpha, impactProgress * 3.0f, ELECTRIC_DARK_R, ELECTRIC_DARK_G, ELECTRIC_DARK_B,
				ELECTRIC_R, ELECTRIC_G, ELECTRIC_B);
		VfxWorldGeometry.renderDirectionalRing(consumer, target, basis[1], delta.lengthSqr() < 1.0E-8 ? VfxWorldGeometry.UP : delta.normalize(),
				shellRadius * 0.75f, 0.72f, Math.round(impactAlpha * 0.72f), -impactProgress * 2.0f,
				ELECTRIC_DARK_R, ELECTRIC_DARK_G, ELECTRIC_DARK_B, ELECTRIC_CORE_R, ELECTRIC_CORE_G, ELECTRIC_CORE_B);
		for (int mini = 0; mini < 4; mini++) {
			long miniSeed = jitterSeed ^ (mini * 0xA24BAED4963EE407L);
			double angle = random(miniSeed) * Math.PI * 2.0;
			Vec3 radial = basis[0].scale(Math.cos(angle)).add(basis[1].scale(Math.sin(angle))).normalize();
			Vec3 miniStart = target.add(radial.scale(0.10 + impactProgress * 0.08));
			Vec3 miniEnd = target.add(radial.scale(0.22 + impactProgress * 0.72))
					.add(delta.lengthSqr() < 1.0E-8 ? Vec3.ZERO : delta.normalize().scale((random(miniSeed ^ 1L) - 0.5) * 0.25));
			drawJagged(consumer, miniStart, miniEnd, miniSeed, 3, 0.022f,
				Math.round(impactAlpha * 0.75f), ELECTRIC_DARK_R, ELECTRIC_DARK_G, ELECTRIC_DARK_B,
				ELECTRIC_R, ELECTRIC_G, ELECTRIC_B, ELECTRIC_CORE_R, ELECTRIC_CORE_G, ELECTRIC_CORE_B);
		}
	}

	private static void renderJet(VertexConsumer consumer, Vec3 start, Vec3 end, VfxCue cue, float age) {
		float fade = Math.min(1.0f, Math.max(0.0f, age / 0.8f))
				* Math.min(1.0f, Math.max(0.0f, (ELEPHANT_JET_TTL_TICKS - age) / 1.5f));
		int alpha = Math.round(205.0f * fade);
		if (alpha <= 0) {
			return;
		}
		long seed = cue.seed() ^ ((long) Math.floor(age / 2.0f) * SEED_STEP);
		for (int strand = 0; strand < 2; strand++) {
			drawJagged(consumer, start, end, seed ^ (strand * 0x632BE59BD9B4E019L), 7,
				0.075f - strand * 0.02f, alpha,
				WATER_DARK_R, WATER_DARK_G, WATER_DARK_B,
				WATER_R, WATER_G, WATER_B,
				WATER_CORE_R, WATER_CORE_G, WATER_CORE_B);
		}
	}

	private static void drawJagged(VertexConsumer consumer, Vec3 start, Vec3 end, long seed, int segments,
			float width, int alpha, int darkR, int darkG, int darkB, int edgeR, int edgeG, int edgeB,
			int coreR, int coreG, int coreB) {
		if (alpha <= 0) {
			return;
		}
		Vec3 delta = end.subtract(start);
		Vec3[] basis = VfxWorldGeometry.directionalBasis(delta);
		Vec3 point = start;
		for (int segment = 0; segment < segments; segment++) {
			float t = (segment + 1.0f) / segments;
			Vec3 next = end;
			if (segment + 1 < segments) {
				long segmentSeed = seed ^ (segment * 0xC2B2AE3D27D4EB4FL);
				next = start.lerp(end, t)
						.add(basis[0].scale((random(segmentSeed) - 0.5) * 0.42))
						.add(basis[1].scale((random(segmentSeed ^ 0x51EDL) - 0.5) * 0.32));
			}
			Vec3 side = VfxWorldGeometry.sideVector(next.subtract(point), point.add(next).scale(0.5), width);
			VfxWorldGeometry.addRibbon(consumer, point, next, side.scale(2.8), darkR, darkG, darkB,
					Math.round(alpha * 0.42f));
			VfxWorldGeometry.addRibbon(consumer, point, next, side, edgeR, edgeG, edgeB, alpha);
			VfxWorldGeometry.addRibbon(consumer, point.lerp(next, 0.18), next, side.scale(0.35),
					coreR, coreG, coreB, Math.round(alpha * 0.62f));
			point = next;
		}
	}

	private static double random(long seed) {
		long x = seed ^ (seed >>> 33);
		x *= 0xFF51AFD7ED558CCDL;
		x ^= (x >>> 33);
		x *= 0xC4CEB9FE1A85EC53L;
		x ^= (x >>> 33);
		return (x & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;
	}
}
