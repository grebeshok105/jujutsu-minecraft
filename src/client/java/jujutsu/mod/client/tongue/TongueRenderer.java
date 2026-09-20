package jujutsu.mod.client.tongue;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.world.VfxWorldGeometry;

/** World pass for the persistent cubic tongue and manifested partial Toad head. */
public final class TongueRenderer {
	private static final double SAG_PER_BLOCK = 0.035;
	private static final double MAX_SAG = 0.24;
	private static boolean registered;

	private TongueRenderer() {}

	/** Registers one AFTER_ENTITIES pass; B2's client init owns the call. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WorldRenderEvents.AFTER_ENTITIES.register(TongueRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		ClientLevel level = context.world();
		MultiBufferSource consumers = context.consumers();
		if (level == null || consumers == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cameraPosition = camera.getPosition();
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		// Two sequential passes, one buffer each: entityTranslucent rides the shared buffer,
		// so the second getBuffer() would end the first mid-frame ("Not building!"). Emit
		// all tongue geometry, then fetch the head consumer and emit all heads.
		VertexConsumer tongue = consumers.getBuffer(RenderType.entityTranslucent(TongueModel.TEXTURE));
		for (Player owner : level.players()) {
			TongueClientState.Phase phase = TongueClientState.phaseFor(owner.getUUID());
			if (phase == null) {
				continue;
			}
			float alpha = TongueClientState.alpha(owner.getUUID(), partialTick);
			if (alpha <= 0.0f) {
				continue;
			}
			Vec3 tip = TongueClientState.tipPosition(owner, partialTick);
			if (tip == null) {
				continue;
			}
			Vec3 mouth = TongueClientState.mouthWorldPos(owner, partialTick).subtract(cameraPosition);
			renderTongue(tongue, mouth, tip.subtract(cameraPosition), alpha);
		}
		VertexConsumer head = consumers.getBuffer(RenderType.entityTranslucent(ToadHeadRenderer.TEXTURE));
		for (Player owner : level.players()) {
			TongueClientState.Phase phase = TongueClientState.phaseFor(owner.getUUID());
			if (phase == null) {
				continue;
			}
			float alpha = TongueClientState.alpha(owner.getUUID(), partialTick);
			if (alpha <= 0.0f) {
				continue;
			}
			ToadHeadRenderer.render(head, owner, cameraPosition, partialTick, alpha);
		}
	}

	private static void renderTongue(VertexConsumer consumer, Vec3 mouth, Vec3 tip, float alpha) {
		Vec3 span = tip.subtract(mouth);
		double length = span.length();
		if (length < 1.0E-4) {
			return;
		}
		double sag = Math.min(MAX_SAG, length * SAG_PER_BLOCK);
		for (int index = 0; index < TongueModel.SEGMENT_COUNT; index++) {
			double t0 = index / (double) TongueModel.SEGMENT_COUNT;
			double t1 = (index + 1.0) / TongueModel.SEGMENT_COUNT;
			Vec3 start = curvePoint(mouth, tip, t0, sag);
			Vec3 end = curvePoint(mouth, tip, t1, sag);
			Vec3 direction = end.subtract(start);
			if (direction.lengthSqr() < 1.0E-6) {
				continue;
			}
			Vec3 forward = direction.normalize();
			Vec3[] basis = VfxWorldGeometry.directionalBasis(forward);
			float taper = 1.0f - index * 0.085f;
			float halfWidth = 0.0625f * taper;
			float halfHeight = 0.0625f * taper;
			float u0 = index * 8.0f / 64.0f;
			float u1 = (index * 8.0f + 8.0f) / 64.0f;
			int segmentAlpha = Math.round(255.0f * alpha);
			drawCuboid(consumer, start.add(end).scale(0.5), forward, basis[0], basis[1],
				(float) (direction.length() * 0.5), halfWidth, halfHeight,
				u0, 0.0f, u1, 8.0f / 64.0f, segmentAlpha);
		}
	}

	private static Vec3 curvePoint(Vec3 start, Vec3 end, double progress, double sag) {
		return start.lerp(end, progress).add(0.0, -Math.sin(Math.PI * progress) * sag, 0.0);
	}

	/** Shared textured cuboid primitive used by the tongue and head passes. */
	static void drawCuboid(VertexConsumer consumer, Vec3 center, Vec3 forward, Vec3 right, Vec3 up,
			float halfLength, float halfWidth, float halfHeight,
			float u0, float v0, float u1, float v1, int alpha) {
		Vec3 f = forward.scale(halfLength);
		Vec3 r = right.scale(halfWidth);
		Vec3 h = up.scale(halfHeight);
		quad(consumer, center.add(f), r, h, forward, u0, v0, u1, v1, alpha, false);
		quad(consumer, center.subtract(f), r, h, forward.scale(-1.0), u0, v0, u1, v1, alpha, true);
		quad(consumer, center.add(h), r, f, up, u0, v0, u1, v1, alpha, false);
		quad(consumer, center.subtract(h), r, f, up.scale(-1.0), u0, v0, u1, v1, alpha, true);
		quad(consumer, center.add(r), f, h, right, u0, v0, u1, v1, alpha, false);
		quad(consumer, center.subtract(r), f, h, right.scale(-1.0), u0, v0, u1, v1, alpha, true);
	}

	private static void quad(VertexConsumer consumer, Vec3 center, Vec3 axisA, Vec3 axisB, Vec3 normal,
			float u0, float v0, float u1, float v1, int alpha, boolean reverse) {
		Vec3 a0 = center.subtract(axisA).subtract(axisB);
		Vec3 a1 = center.add(axisA).subtract(axisB);
		Vec3 a2 = center.add(axisA).add(axisB);
		Vec3 a3 = center.subtract(axisA).add(axisB);
		if (reverse) {
			vertex(consumer, a3, normal, u0, v0, alpha);
			vertex(consumer, a2, normal, u1, v0, alpha);
			vertex(consumer, a1, normal, u1, v1, alpha);
			vertex(consumer, a0, normal, u0, v1, alpha);
		} else {
			vertex(consumer, a0, normal, u0, v0, alpha);
			vertex(consumer, a1, normal, u1, v0, alpha);
			vertex(consumer, a2, normal, u1, v1, alpha);
			vertex(consumer, a3, normal, u0, v1, alpha);
		}
	}

	private static void vertex(VertexConsumer consumer, Vec3 point, Vec3 normal, float u, float v, int alpha) {
		consumer.addVertex((float) point.x, (float) point.y, (float) point.z)
				.setUv(u, v)
				.setColor(255, 255, 255, alpha)
				.setLight(LightTexture.FULL_BRIGHT)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setNormal((float) normal.x, (float) normal.y, (float) normal.z);
	}
}
