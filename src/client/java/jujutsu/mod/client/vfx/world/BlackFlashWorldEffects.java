package jujutsu.mod.client.vfx.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.VfxPalette;
import jujutsu.mod.vfx.VfxCue;

public final class BlackFlashWorldEffects {
	private static final int BF_CORE_R = VfxPalette.BLACK_FLASH_CORE_R;
	private static final int BF_CORE_G = VfxPalette.BLACK_FLASH_CORE_G;
	private static final int BF_CORE_B = VfxPalette.BLACK_FLASH_CORE_B;
	private static final int BF_RED_R = VfxPalette.BLACK_FLASH_RED_R;
	private static final int BF_RED_G = VfxPalette.BLACK_FLASH_RED_G;
	private static final int BF_RED_B = VfxPalette.BLACK_FLASH_RED_B;
	private static final int BF_CRIMSON_R = VfxPalette.BLACK_FLASH_CRIMSON_R;
	private static final int BF_CRIMSON_G = VfxPalette.BLACK_FLASH_CRIMSON_G;
	private static final int BF_CRIMSON_B = VfxPalette.BLACK_FLASH_CRIMSON_B;
	private static final int BF_VOID_R = VfxPalette.BLACK_FLASH_VOID_R;
	private static final int BF_VOID_G = VfxPalette.BLACK_FLASH_VOID_G;
	private static final int BF_VOID_B = VfxPalette.BLACK_FLASH_VOID_B;
	private static final int BF_BLOOD_R = VfxPalette.BLACK_FLASH_BLOOD_R;
	private static final int BF_BLOOD_G = VfxPalette.BLACK_FLASH_BLOOD_G;
	private static final int BF_BLOOD_B = VfxPalette.BLACK_FLASH_BLOOD_B;
	private static final int BF_SPARK_R = VfxPalette.BLACK_FLASH_SPARK_R;
	private static final int BF_SPARK_G = VfxPalette.BLACK_FLASH_SPARK_G;
	private static final int BF_SPARK_B = VfxPalette.BLACK_FLASH_SPARK_B;

	private BlackFlashWorldEffects() {}

	public static void renderBlackFlash(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade, VfxCue cue) {
		int alpha = Math.min(250, Math.round(245.0f * fade));
		float scale = 1.0f + Math.min(4, intensity) * 0.14f;
		Vec3 forward = cue.direction().lengthSqr() < 1e-6 ? VfxWorldGeometry.NORTH : cue.direction();
		Vec3[] basis = VfxWorldGeometry.directionalBasis(forward);
		Vec3 right = basis[0];
		Vec3 up = basis[1];
		long seed = cue.seed();
		int variant = (int) (Math.abs(seed) % 3);

		if (progress < 0.35f) {
			float compression = progress / 0.35f;
			float radius = (1.8f - compression * 1.4f) * scale;
			VfxWorldGeometry.renderDirectionalRing(consumer, center, right, up, radius, 0.72f, Math.round(alpha * 0.9f * (1.0f - compression * 0.4f)),
					-compression * 3.0f, BF_VOID_R, BF_VOID_G, BF_VOID_B, BF_BLOOD_R, BF_BLOOD_G, BF_BLOOD_B);
		}

		if (progress > 0.12f && progress < 0.6f) {
			float bladeProgress = (progress - 0.12f) / 0.48f;
			int bladeAlpha = Math.round(alpha * (1.0f - bladeProgress * 0.55f));
			for (int index = 0; index < 5; index++) {
				double spreadAngle = (index - 2.0) * 0.36;
				Vec3 bladeDir = forward.scale(Math.cos(spreadAngle)).add(right.scale(Math.sin(spreadAngle))).normalize();
				float length = (2.2f + bladeProgress * 1.8f) * scale;
				Vec3 start = center.add(bladeDir.scale(0.1f));
				Vec3 end = center.add(bladeDir.scale(length));
				addBlackFlashBlade(consumer, start, end, 0.16f + (index == 2 ? 0.06f : 0.0f), bladeAlpha);
			}
		}

		if (progress > 0.25f && progress < 0.92f) {
			float lightningProgress = (progress - 0.25f) / 0.67f;
			int lightningAlpha = (int) Math.round(alpha * Math.pow(1.0f - lightningProgress, 1.4));
			switch (variant) {
				case 0 -> renderForkedLightning(consumer, center, forward, right, up, scale, seed, lightningAlpha);
				case 1 -> renderSpiralLightning(consumer, center, forward, right, up, scale, seed, lightningAlpha, progress);
				default -> renderCascadeLightning(consumer, center, forward, right, up, scale, seed, lightningAlpha);
			}
			int microAlpha = Math.round(lightningAlpha * 0.6f);
			for (int i = 0; i < 4; i++) {
				long microSeed = seed ^ (i * 0xDEADBEEFL);
				double angle = pseudoRandom(microSeed) * Math.PI * 2.0;
				Vec3 dir = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle))).normalize();
				float len = (0.6f + (float) pseudoRandom(microSeed ^ 0x99L) * 0.8f) * scale;
				Vec3 start = center.add(dir.scale(0.2f));
				Vec3 end = start.add(dir.scale(len));
				Vec3 side = VfxWorldGeometry.sideVector(end.subtract(start), start.add(end).scale(0.5), 0.018f);
				VfxWorldGeometry.addRibbon(consumer, start, end, side, BF_SPARK_R, BF_SPARK_G, BF_SPARK_B, microAlpha);
			}
		}

		if (progress > 0.38f) {
			float ringProgress = (progress - 0.38f) / 0.62f;
			int ringAlpha = (int) Math.round(alpha * Math.pow(1.0f - ringProgress, 2.0));
			float radius = (0.5f + ringProgress * 3.8f) * scale;
			VfxWorldGeometry.renderDirectionalRing(consumer, center, right, up, radius, 0.82f, ringAlpha,
					ringProgress * 2.4f, BF_VOID_R, BF_VOID_G, BF_VOID_B, BF_BLOOD_R, BF_BLOOD_G, BF_BLOOD_B);
			if (ringProgress < 0.5f) {
				float innerRadius = (0.3f + ringProgress * 1.2f) * scale;
				VfxWorldGeometry.renderDirectionalRing(consumer, center, right, up, innerRadius, 0.6f, Math.round(ringAlpha * 0.5f),
						-ringProgress * 4.0f, BF_BLOOD_R, BF_BLOOD_G, BF_BLOOD_B, BF_CRIMSON_R, BF_CRIMSON_G, BF_CRIMSON_B);
			}
		}
	}

	private static void renderForkedLightning(VertexConsumer consumer, Vec3 center, Vec3 forward, Vec3 right, Vec3 up,
			float scale, long seed, int alpha) {
		for (int bolt = 0; bolt < 8; bolt++) {
			long boltSeed = seed ^ (bolt * 0x9E3779B97F4A7C15L);
			double baseAngle = (bolt < 6)
					? (pseudoRandom(boltSeed) - 0.5) * 1.0
					: pseudoRandom(boltSeed) * Math.PI * 2.0;
			int segments = 5 + (int) (pseudoRandom(boltSeed ^ 0x1234L) * 3.0);
			float totalLength = (1.8f + (float) pseudoRandom(boltSeed ^ 0x5678L) * 1.8f) * scale;
			Vec3 boltDir = (bolt < 6)
					? forward.scale(0.75).add(right.scale(Math.cos(baseAngle) * 0.45)).add(up.scale(Math.sin(baseAngle) * 0.45)).normalize()
					: right.scale(Math.cos(baseAngle)).add(up.scale(Math.sin(baseAngle))).normalize();
			Vec3 point = center;
			for (int seg = 0; seg < segments; seg++) {
				float segLength = totalLength / segments;
				double lateralOffset = (pseudoRandom(boltSeed ^ (seg * 0xABCDL)) - 0.5) * 0.7;
				Vec3 nextPoint = point.add(boltDir.scale(segLength)).add(right.scale(lateralOffset));
				int segR = (seg & 1) == 0 ? BF_BLOOD_R : BF_CORE_R;
				int segG = (seg & 1) == 0 ? BF_BLOOD_G : BF_CORE_G;
				int segB = (seg & 1) == 0 ? BF_BLOOD_B : BF_CORE_B;
				Vec3 side = VfxWorldGeometry.sideVector(nextPoint.subtract(point), point.add(nextPoint).scale(0.5), 0.032f);
				VfxWorldGeometry.addRibbon(consumer, point, nextPoint, side.scale(2.6f), BF_VOID_R, BF_VOID_G, BF_VOID_B, Math.round(alpha * 0.45f));
				VfxWorldGeometry.addRibbon(consumer, point, nextPoint, side, segR, segG, segB, alpha);
				if (seg == segments / 2 && pseudoRandom(boltSeed ^ 0xF0E1L) > 0.4) {
					Vec3 forkDir = boltDir.add(right.scale((pseudoRandom(boltSeed ^ 0xF0E2L) - 0.5) * 1.2)).normalize();
					Vec3 forkEnd = nextPoint.add(forkDir.scale(segLength * 1.5f));
					Vec3 forkSide = VfxWorldGeometry.sideVector(forkEnd.subtract(nextPoint), nextPoint.add(forkEnd).scale(0.5), 0.02f);
					VfxWorldGeometry.addRibbon(consumer, nextPoint, forkEnd, forkSide, BF_CRIMSON_R, BF_CRIMSON_G, BF_CRIMSON_B, Math.round(alpha * 0.7f));
				}
				point = nextPoint;
			}
		}
	}

	private static void renderSpiralLightning(VertexConsumer consumer, Vec3 center, Vec3 forward, Vec3 right, Vec3 up,
			float scale, long seed, int alpha, float progress) {
		for (int bolt = 0; bolt < 6; bolt++) {
			long boltSeed = seed ^ (bolt * 0x6C62272E07BB0142L);
			int segments = 6 + (int) (pseudoRandom(boltSeed ^ 0x2222L) * 2.0);
			float totalLength = (2.0f + (float) pseudoRandom(boltSeed ^ 0x3333L) * 1.4f) * scale;
			double startAngle = pseudoRandom(boltSeed) * Math.PI * 2.0 + progress * 4.0;
			Vec3 point = center;
			for (int seg = 0; seg < segments; seg++) {
				float t = (float) seg / segments;
				double angle = startAngle + t * Math.PI * 1.5;
				float segLength = totalLength / segments;
				Vec3 spiralDir = forward.scale(0.6)
						.add(right.scale(Math.cos(angle) * 0.5))
						.add(up.scale(Math.sin(angle) * 0.5)).normalize();
				Vec3 nextPoint = point.add(spiralDir.scale(segLength));
				int segR = (seg % 3 == 0) ? BF_CORE_R : BF_CRIMSON_R;
				int segG = (seg % 3 == 0) ? BF_CORE_G : BF_CRIMSON_G;
				int segB = (seg % 3 == 0) ? BF_CORE_B : BF_CRIMSON_B;
				Vec3 side = VfxWorldGeometry.sideVector(nextPoint.subtract(point), point.add(nextPoint).scale(0.5), 0.028f);
				VfxWorldGeometry.addRibbon(consumer, point, nextPoint, side.scale(2.4f), BF_VOID_R, BF_VOID_G, BF_VOID_B, Math.round(alpha * 0.4f));
				VfxWorldGeometry.addRibbon(consumer, point, nextPoint, side, segR, segG, segB, alpha);
				point = nextPoint;
			}
		}
	}

	private static void renderCascadeLightning(VertexConsumer consumer, Vec3 center, Vec3 forward, Vec3 right, Vec3 up,
			float scale, long seed, int alpha) {
		for (int bolt = 0; bolt < 9; bolt++) {
			long boltSeed = seed ^ (bolt * 0x2545F4914F6CDD1DL);
			double baseAngle = pseudoRandom(boltSeed) * Math.PI * 2.0;
			int segments = 3 + (int) (pseudoRandom(boltSeed ^ 0x4444L) * 2.0);
			float totalLength = (1.2f + (float) pseudoRandom(boltSeed ^ 0x5555L) * 2.2f) * scale;
			float downwardBias = -0.3f - (float) pseudoRandom(boltSeed ^ 0x6666L) * 0.4f;
			Vec3 boltDir = right.scale(Math.cos(baseAngle) * 0.6)
					.add(up.scale(downwardBias))
					.add(forward.scale(0.3)).normalize();
			Vec3 point = center.add(up.scale(0.3f * scale));
			for (int seg = 0; seg < segments; seg++) {
				float segLength = totalLength / segments;
				double lateralOffset = (pseudoRandom(boltSeed ^ (seg * 0x7777L)) - 0.5) * 0.5;
				Vec3 nextPoint = point.add(boltDir.scale(segLength)).add(right.scale(lateralOffset));
				int segR = (seg & 1) == 0 ? BF_BLOOD_R : BF_SPARK_R;
				int segG = (seg & 1) == 0 ? BF_BLOOD_G : BF_SPARK_G;
				int segB = (seg & 1) == 0 ? BF_BLOOD_B : BF_SPARK_B;
				Vec3 side = VfxWorldGeometry.sideVector(nextPoint.subtract(point), point.add(nextPoint).scale(0.5), 0.03f);
				VfxWorldGeometry.addRibbon(consumer, point, nextPoint, side.scale(2.2f), BF_VOID_R, BF_VOID_G, BF_VOID_B, Math.round(alpha * 0.5f));
				VfxWorldGeometry.addRibbon(consumer, point, nextPoint, side, segR, segG, segB, alpha);
				point = nextPoint;
			}
		}
	}

	private static void addBlackFlashBlade(VertexConsumer consumer, Vec3 start, Vec3 end, float width, int alpha) {
		if (alpha <= 0) {
			return;
		}
		Vec3 side = VfxWorldGeometry.sideVector(end.subtract(start), start.add(end).scale(0.5), width);
		VfxWorldGeometry.addRibbon(consumer, start, end, side.scale(3.5f), BF_VOID_R, BF_VOID_G, BF_VOID_B, Math.round(alpha * 0.45f));
		VfxWorldGeometry.addRibbon(consumer, start, end, side.scale(1.4f), BF_CRIMSON_R, BF_CRIMSON_G, BF_CRIMSON_B, alpha);
		VfxWorldGeometry.addRibbon(consumer, start.lerp(end, 0.2), end, side.scale(0.45f), BF_CORE_R, BF_CORE_G, BF_CORE_B, Math.round(alpha * 0.5f));
	}

	private static double pseudoRandom(long seed) {
		long x = seed ^ (seed >>> 33);
		x *= 0xFF51AFD7ED558CCDL;
		x ^= (x >>> 33);
		x *= 0xC4CEB9FE1A85EC53L;
		x ^= (x >>> 33);
		return (x & 0x7FFFFFFFFFFFFFFFL) / (double) 0x7FFFFFFFFFFFFFFFL;
	}
}
