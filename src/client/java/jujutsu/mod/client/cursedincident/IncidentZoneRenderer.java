package jujutsu.mod.client.cursedincident;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.world.VfxWorldGeometry;
import jujutsu.mod.registry.JujutsuParticles;

/**
 * Persistent per-zone renderer for cursed incidents. Driven entirely by
 * {@link IncidentZoneState} (server snapshots); deterministic geometry is seeded by the
 * zone key so every client sees the same veins/cracks.
 *
 * <p>Stage ladder drives intensity: INITIAL is a faint wrongness (sparse motes + thin
 * edge ring), later stages add veins, pulse glow and ground cracks.
 */
public final class IncidentZoneRenderer {
	private static final int MAX_ZONES = 8;
	private static final int MAX_VEINS_PER_ZONE = 48;
	private static final int MAX_CRACKS_PER_ZONE = 24;
	private static final double LOD_DISTANCE = 96.0;
	private static final int RING_SEGMENTS = 40;

	/** Deterministic vein/crack geometry cache, keyed by zone key + stage. */
	private static final Map<String, ZoneGeometry> GEOMETRY = new HashMap<>();
	private static int moteBudget;

	private IncidentZoneRenderer() {
	}

	public static void register() {
		// AFTER_ENTITIES, not AFTER_TRANSLUCENT: inside the framegraph pass the shared
		// BufferSource is already ended, so getBuffer() hands back a dead builder and the
		// first addVertex crashes "Not building!". AFTER_ENTITIES is the proven hook —
		// NueArcRenderer and VfxWorldChannel draw through context.consumers() there.
		WorldRenderEvents.AFTER_ENTITIES.register(IncidentZoneRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		ClientLevel level = context.world();
		if (level == null) {
			return;
		}
		MultiBufferSource consumers = context.consumers();
		if (consumers == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3 camPos = camera.getPosition();
		String dimension = level.dimension().location().toString();
		// One consumer for everything: debugQuads rides the shared buffer, which any later
		// getBuffer() call from another AFTER_TRANSLUCENT listener ends mid-frame — the
		// first addVertex then crashes "Not building!". lightning is a fixed buffer and
		// stays open for the whole pass.
		VertexConsumer glow = consumers.getBuffer(RenderType.lightning());
		VertexConsumer quads = glow;
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long gameTime = level.getGameTime();

		int rendered = 0;
		for (IncidentZoneState.Zone zone : IncidentZoneState.zones()) {
			if (rendered >= MAX_ZONES) {
				break;
			}
			if (!zone.key.dimension().equals(dimension) || zone.center == null) {
				continue;
			}
			double dist = zone.center.distanceTo(camPos);
			if (dist > LOD_DISTANCE + zone.radius) {
				continue;
			}
			rendered++;
			renderZone(quads, glow, zone, camPos, gameTime, partialTick, dist);
			spawnMotes(level, zone, gameTime, dist);
		}
	}

	private static void renderZone(VertexConsumer quads, VertexConsumer glow,
			IncidentZoneState.Zone zone, Vec3 camPos, long gameTime, float partialTick, double dist) {
		int stage = Math.max(0, Math.min(4, zone.stage));
		ZoneGeometry geo = geometryFor(zone);
		Vec3 c = zone.center.subtract(camPos);
		double radius = Math.max(2.0, zone.radius);

		// Edge ring: the zone boundary reads at every stage; alpha scales with severity.
		int ringAlpha = 28 + stage * 22;
		renderGroundRing(quads, c, radius, 0.10f + stage * 0.02f, 46, 10, 66, ringAlpha);

		// Cursed veins: dark violet ribbons crawling outward from the center.
		int veinAlpha = Math.min(200, 40 + stage * 40);
		for (Vec3[] vein : geo.veins) {
			for (int i = 0; i + 1 < vein.length; i++) {
				Vec3 a = vein[i].subtract(camPos);
				Vec3 b = vein[i + 1].subtract(camPos);
				Vec3 side = VfxWorldGeometry.sideVector(b.subtract(a), a.add(b).scale(0.5), 0.05f + stage * 0.015f);
				VfxWorldGeometry.addRibbon(quads, a, b, side, 30, 8, 44, veinAlpha);
			}
		}

		// CRITICAL+: slow pulse glow ring expanding from the center.
		if (stage >= 3) {
			float phase = ((gameTime % 60) + partialTick) / 60.0f;
			double pulseRadius = radius * (0.25 + 0.75 * phase);
			int pulseAlpha = (int) (90 * (1.0f - phase));
			renderGroundRing(glow, c, pulseRadius, 0.16f, 120, 40, 170, pulseAlpha);
		}

		// CATASTROPHIC: jagged ground cracks radiating from the center.
		if (stage >= 4) {
			for (Vec3[] crack : geo.cracks) {
				for (int i = 0; i + 1 < crack.length; i++) {
					Vec3 a = crack[i].subtract(camPos);
					Vec3 b = crack[i + 1].subtract(camPos);
					Vec3 side = VfxWorldGeometry.sideVector(b.subtract(a), a.add(b).scale(0.5), 0.09f);
					VfxWorldGeometry.addRibbon(quads, a, b, side, 8, 2, 12, 230);
				}
			}
		}

		// Source-object aura ring at the zone heart.
		renderGroundRing(glow, c, 1.1, 0.22f, 90, 30, 130, 120 + stage * 20);

		// Seal: talisman barrier ring at the center, degrading per band.
		if (zone.sealTier > 0) {
			renderSeal(glow, c, zone, gameTime, partialTick);
		}
	}

	private static void renderSeal(VertexConsumer glow, Vec3 center, IncidentZoneState.Zone zone,
			long gameTime, float partialTick) {
		int band = zone.sealBand();
		// Band 0 = pristine, 4 = failing; color drifts gold → angry red as it degrades.
		float integrity = switch (band) {
			case 0 -> 1.0f;
			case 1 -> 0.75f;
			case 2 -> 0.5f;
			case 3 -> 0.25f;
			default -> 0.08f;
		};
		int r = (int) (200 + 55 * (1.0f - integrity));
		int g = (int) (170 * integrity);
		int b = (int) (60 + 60 * integrity);
		// Failing seals flicker.
		float flicker = band >= 3 ? 0.6f + 0.4f * Mth.sin((gameTime + partialTick) * 0.9f) : 1.0f;
		int alpha = (int) (150 * integrity * flicker) + 30;
		double sealRadius = 1.6;
		renderGroundRing(glow, center, sealRadius, 0.30f, r, g, b, alpha);
		// Vertical talisman posts around the ring — the readable "sealed" silhouette.
		int posts = 6;
		for (int i = 0; i < posts; i++) {
			double angle = (Math.PI * 2 * i) / posts;
			Vec3 base = center.add(Math.cos(angle) * sealRadius, 0.0, Math.sin(angle) * sealRadius);
			Vec3 top = base.add(0.0, 0.9 + 0.15 * integrity, 0.0);
			Vec3 side = VfxWorldGeometry.sideVector(top.subtract(base), base, 0.05f);
			VfxWorldGeometry.addRibbon(glow, base, top, side, r, g, b, alpha);
		}
	}

	private static void renderGroundRing(VertexConsumer consumer, Vec3 center, double radius,
			float width, int r, int g, int b, int alpha) {
		if (alpha <= 0) {
			return;
		}
		Vec3 prev = null;
		for (int i = 0; i <= RING_SEGMENTS; i++) {
			double angle = (Math.PI * 2 * i) / RING_SEGMENTS;
			Vec3 p = center.add(Math.cos(angle) * radius, 0.06, Math.sin(angle) * radius);
			if (prev != null) {
				Vec3 side = VfxWorldGeometry.sideVector(p.subtract(prev), prev, width);
				VfxWorldGeometry.addRibbon(consumer, prev, p, side, r, g, b, alpha);
			}
			prev = p;
		}
	}

	/** Client-side mote spawning on a per-zone cadence; bounded by a global frame budget. */
	private static void spawnMotes(ClientLevel level, IncidentZoneState.Zone zone, long gameTime, double dist) {
		if (moteBudget <= 0) {
			return;
		}
		int stage = Math.max(0, Math.min(4, zone.stage));
		// INITIAL: a mote every ~6 ticks; CATASTROPHIC: every tick.
		int period = Math.max(1, 6 - stage);
		if (gameTime % period != 0) {
			return;
		}
		double radius = Math.max(2.0, zone.radius);
		RandomSource random = RandomSource.create(zone.key.incidentId().hashCode() ^ gameTime);
		int count = dist > LOD_DISTANCE * 0.6 ? 1 : 1 + stage;
		for (int i = 0; i < count && moteBudget > 0; i++, moteBudget--) {
			double angle = random.nextDouble() * Math.PI * 2;
			double rr = Math.sqrt(random.nextDouble()) * radius;
			double x = zone.center.x + Math.cos(angle) * rr;
			double z = zone.center.z + Math.sin(angle) * rr;
			double y = zone.center.y + random.nextDouble() * 1.5;
			level.addParticle(JujutsuParticles.CURSED_MOTE, x, y, z, 0.0, 0.02, 0.0);
		}
	}

	private static ZoneGeometry geometryFor(IncidentZoneState.Zone zone) {
		String cacheKey = zone.key.incidentId() + ":" + zone.key.nodeId() + ":" + zone.stage;
		return GEOMETRY.computeIfAbsent(cacheKey, k -> generate(zone));
	}

	private static ZoneGeometry generate(IncidentZoneState.Zone zone) {
		int stage = Math.max(0, Math.min(4, zone.stage));
		RandomSource random = RandomSource.create(
				zone.key.incidentId().hashCode() ^ zone.key.nodeId().getLeastSignificantBits());
		double radius = Math.max(2.0, zone.radius);
		ZoneGeometry geo = new ZoneGeometry();
		int veinCount = Math.min(MAX_VEINS_PER_ZONE, 6 + stage * 10);
		for (int i = 0; i < veinCount; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double len = radius * (0.3 + random.nextDouble() * 0.7);
			int segments = 3 + random.nextInt(3);
			Vec3[] path = new Vec3[segments + 1];
			Vec3 p = zone.center;
			double heading = angle;
			for (int s = 0; s <= segments; s++) {
				path[s] = p;
				heading += (random.nextDouble() - 0.5) * 0.9;
				double step = len / segments;
				p = p.add(Math.cos(heading) * step, 0.0, Math.sin(heading) * step);
			}
			geo.veins.add(path);
		}
		if (stage >= 4) {
			int crackCount = Math.min(MAX_CRACKS_PER_ZONE, 8 + stage * 4);
			for (int i = 0; i < crackCount; i++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double len = radius * (0.4 + random.nextDouble() * 0.6);
				int segments = 4 + random.nextInt(3);
				Vec3[] path = new Vec3[segments + 1];
				Vec3 p = zone.center;
				double heading = angle;
				for (int s = 0; s <= segments; s++) {
					path[s] = p;
					heading += (random.nextDouble() - 0.5) * 1.4;
					double step = len / segments;
					p = p.add(Math.cos(heading) * step, 0.0, Math.sin(heading) * step);
				}
				geo.cracks.add(path);
			}
		}
		return geo;
	}

	/** Per-frame mote budget reset — called from the client tick in CursedIncidentClient. */
	public static void resetMoteBudget() {
		moteBudget = 24;
	}

	public static void clearCache() {
		GEOMETRY.clear();
	}

	private static final class ZoneGeometry {
		final List<Vec3[]> veins = new ArrayList<>();
		final List<Vec3[]> cracks = new ArrayList<>();
	}
}
