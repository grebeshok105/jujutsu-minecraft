package jujutsu.mod.client.vfx.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.VfxPalette;
import jujutsu.mod.vfx.TodoSwapArrivalPayload;
import jujutsu.mod.vfx.VfxCue;

public final class SwapWorldEffects {
	private static final int TODO_VIOLET_R = VfxPalette.TODO_VIOLET_R;
	private static final int TODO_VIOLET_G = VfxPalette.TODO_VIOLET_G;
	private static final int TODO_VIOLET_B = VfxPalette.TODO_VIOLET_B;
	private static final int TODO_EDGE_R = VfxPalette.TODO_EDGE_R;
	private static final int TODO_EDGE_G = VfxPalette.TODO_EDGE_G;
	private static final int TODO_EDGE_B = VfxPalette.TODO_EDGE_B;
	private static final int TODO_DEEP_R = VfxPalette.TODO_DEEP_R;
	private static final int TODO_DEEP_G = VfxPalette.TODO_DEEP_G;
	private static final int TODO_DEEP_B = VfxPalette.TODO_DEEP_B;
	private static final float SILHOUETTE_PEAK_ALPHA = 168.0f;
	/** How far the residue slides after the body, in blocks, over its whole life. */
	private static final double SILHOUETTE_DRIFT = 0.30;
	/** Blocks per tick below which an arrival gets no streak, because it did not really keep moving. */
	private static final double ARRIVAL_STREAK_MIN_SPEED = 0.06;

	private SwapWorldEffects() {}

	public static void renderBoogieWoogie(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade, VfxCue cue) {
		Vec3 target = center.add(cue.anchorOffset());
		if (target.distanceToSqr(center) < 1.0E-4) {
			return;
		}
		int alpha = Math.min(220, Math.round(210.0f * fade));
		Vec3 midpoint = center.add(target).scale(0.5);
		Vec3 side = VfxWorldGeometry.sideVector(target.subtract(center), midpoint, 0.028f + Math.min(3, intensity) * 0.006f);
		VfxWorldGeometry.addRibbon(consumer, center, target, side.scale(3.4), TODO_DEEP_R, TODO_DEEP_G, TODO_DEEP_B, Math.round(alpha * 0.42f));
		VfxWorldGeometry.addRibbon(consumer, center, target, side.scale(1.35), TODO_VIOLET_R, TODO_VIOLET_G, TODO_VIOLET_B, alpha);
		VfxWorldGeometry.addRibbon(consumer, center.add(target.subtract(center).scale(0.12)), target, side.scale(0.42), TODO_EDGE_R, TODO_EDGE_G, TODO_EDGE_B, Math.round(alpha * 0.62f));
		float pulse = 0.22f + (1.0f - progress) * 0.18f;
		addTodoPulse(consumer, center, pulse, alpha);
		addTodoPulse(consumer, target, pulse, alpha);
	}

	/**
	 * The residue of a body that was somewhere else a moment ago.
	 *
	 * <p>Outline, not fill. {@link RenderType#lightning()} blends additively, so a filled body at any
	 * readable alpha blows out toward white and reads as a bright box rather than a person — which is why
	 * the only solid piece here is a torso wash at a sixth of the outline's alpha.
	 *
	 * <p>Held for four ticks and no longer: the residue stands exactly where the <em>other</em> body is now
	 * arriving, and a longer overlap stops reading as "you see who left" and starts reading as a bug.
	 */
	public static void renderSwapAfterimage(VertexConsumer consumer, Vec3 center, float progress, VfxCue cue) {
		int alpha = Math.round(SILHOUETTE_PEAK_ALPHA * silhouetteAlpha(progress));
		if (alpha <= 0) {
			return;
		}
		float width = silhouetteWidth(cue.anchorOffset().x);
		float height = silhouetteHeight(cue.anchorOffset().y) * (1.0f - progress * 0.06f);
		// The residue slides after the body that left. This is the departure half of keeping the motion
		// readable: without it a swap reads as two bodies blinking, with no sense of which way either went.
		Vec3 feet = center.add(cue.direction().scale(progress * SILHOUETTE_DRIFT));
		Vec3 view = feet.lengthSqr() < 1.0E-4 ? VfxWorldGeometry.NORTH : feet.normalize();
		Vec3 right = VfxWorldGeometry.UP.cross(view);
		right = right.lengthSqr() < 1.0E-5 ? VfxWorldGeometry.EAST : right.normalize();

		float viewYaw = (float) Math.toDegrees(Math.atan2(-feet.x, feet.z));
		float halfWidth = width * 0.5f * facingScale((float) cue.anchorOffset().z, viewYaw);
		float thickness = Math.max(0.028f, width * 0.085f);

		Vec3 hipJoint = feet.add(VfxWorldGeometry.UP.scale(height * 0.48));
		Vec3 waist = feet.add(VfxWorldGeometry.UP.scale(height * 0.26));
		Vec3 neck = feet.add(VfxWorldGeometry.UP.scale(height * 0.78));
		Vec3 headCentre = feet.add(VfxWorldGeometry.UP.scale(height * 0.905));
		float headRadius = height * 0.075f;

		addSilhouetteBone(consumer, waist, neck, thickness, alpha, false);
		// A diamond rather than a ring: four ribbons read as a head at a glance and cost a fifth as much.
		Vec3 headTop = headCentre.add(VfxWorldGeometry.UP.scale(headRadius));
		Vec3 headBottom = headCentre.subtract(VfxWorldGeometry.UP.scale(headRadius));
		Vec3 headRight = headCentre.add(right.scale(headRadius));
		Vec3 headLeft = headCentre.subtract(right.scale(headRadius));
		addSilhouetteBone(consumer, headTop, headRight, thickness * 0.8f, alpha, true);
		addSilhouetteBone(consumer, headRight, headBottom, thickness * 0.8f, alpha, true);
		addSilhouetteBone(consumer, headBottom, headLeft, thickness * 0.8f, alpha, true);
		addSilhouetteBone(consumer, headLeft, headTop, thickness * 0.8f, alpha, true);

		Vec3 shoulderRight = neck.add(right.scale(halfWidth));
		Vec3 shoulderLeft = neck.subtract(right.scale(halfWidth));
		addSilhouetteBone(consumer, shoulderLeft, shoulderRight, thickness, alpha, true);
		addSilhouetteBone(consumer, shoulderRight, feet.add(right.scale(halfWidth * 1.05)).add(VfxWorldGeometry.UP.scale(height * 0.42)),
				thickness * 0.8f, alpha, false);
		addSilhouetteBone(consumer, shoulderLeft, feet.subtract(right.scale(halfWidth * 1.05)).add(VfxWorldGeometry.UP.scale(height * 0.42)),
				thickness * 0.8f, alpha, false);

		Vec3 hipRight = hipJoint.add(right.scale(halfWidth * 0.62));
		Vec3 hipLeft = hipJoint.subtract(right.scale(halfWidth * 0.62));
		addSilhouetteBone(consumer, hipLeft, hipRight, thickness * 0.9f, alpha, false);
		addSilhouetteBone(consumer, hipJoint.add(right.scale(halfWidth * 0.55)), feet.add(right.scale(halfWidth * 0.45)),
				thickness * 0.9f, alpha, false);
		addSilhouetteBone(consumer, hipJoint.subtract(right.scale(halfWidth * 0.55)), feet.subtract(right.scale(halfWidth * 0.45)),
				thickness * 0.9f, alpha, false);

		// The only filled piece, and deliberately barely there: enough mass that the outline is a body.
		VfxWorldGeometry.addRibbon(consumer, waist, neck, right.scale(halfWidth * 0.92),
				TODO_DEEP_R, TODO_DEEP_G, TODO_DEEP_B, Math.round(alpha * 0.16f));
	}

	/**
	 * The landing. Gathers inward where the departure threw outward, so the two ends of a swap never read
	 * as the same event happening twice.
	 */
	public static void renderSwapArrival(VertexConsumer consumer, Vec3 center, float progress, float fade, VfxCue cue) {
		int alpha = Math.min(200, Math.round(200.0f * fade));
		if (alpha <= 0) {
			return;
		}
		TodoSwapArrivalPayload payload = TodoSwapArrivalPayload.from(cue);
		double speed = payload.speed();
		float width = silhouetteWidth(payload.bodyWidth());
		float height = silhouetteHeight(payload.bodyHeight());
		Vec3 chest = center.add(VfxWorldGeometry.UP.scale(height * 0.45));

		VfxWorldGeometry.renderDirectionalRing(consumer, chest, VfxWorldGeometry.EAST, VfxWorldGeometry.NORTH, width * (1.45f - progress * 1.0f), 1.0f,
				Math.round(alpha * 0.9f), progress * 1.6f,
				TODO_DEEP_R, TODO_DEEP_G, TODO_DEEP_B, TODO_EDGE_R, TODO_EDGE_G, TODO_EDGE_B);
		addSilhouetteBone(consumer, center, center.add(VfxWorldGeometry.UP.scale(height * (0.25f + fade * 0.75f))),
				Math.max(0.03f, width * 0.09f), Math.round(alpha * 0.7f), true);

		// A streak only when the body genuinely kept moving. One that always draws is a lie, and it would
		// tell that lie far more often than the truth -- most swaps end with someone standing still.
		if (speed < ARRIVAL_STREAK_MIN_SPEED) {
			return;
		}
		Vec3 direction = payload.direction();
		double length = Math.min(1.6, speed * 2.4) * (1.0 - progress * 0.6);
		Vec3[] basis = VfxWorldGeometry.directionalBasis(direction);
		for (int lane = -1; lane <= 1; lane++) {
			Vec3 head = chest.add(basis[0].scale(lane * width * 0.30));
			addSilhouetteBone(consumer, head.subtract(direction.scale(length)), head, 0.026f,
					Math.round(alpha * (lane == 0 ? 0.85f : 0.5f)), lane == 0);
		}
	}

	private static void addSilhouetteBone(VertexConsumer consumer, Vec3 start, Vec3 end, float width, int alpha, boolean highlight) {
		if (alpha <= 0) {
			return;
		}
		Vec3 side = VfxWorldGeometry.sideVector(end.subtract(start), start.add(end).scale(0.5), width);
		VfxWorldGeometry.addRibbon(consumer, start, end, side.scale(2.6f), TODO_DEEP_R, TODO_DEEP_G, TODO_DEEP_B, Math.round(alpha * 0.40f));
		VfxWorldGeometry.addRibbon(consumer, start, end, side, TODO_VIOLET_R, TODO_VIOLET_G, TODO_VIOLET_B, alpha);
		if (highlight) {
			VfxWorldGeometry.addRibbon(consumer, start.lerp(end, 0.15), end, side.scale(0.45f),
					TODO_EDGE_R, TODO_EDGE_G, TODO_EDGE_B, Math.round(alpha * 0.55f));
		}
	}

	/** A cue with no dimensions must still draw a person, and a giant must not draw a scarecrow. */
	static float silhouetteWidth(double raw) {
		return raw <= 0.05 ? 0.6f : (float) Math.min(2.0, raw);
	}

	static float silhouetteHeight(double raw) {
		return raw <= 0.05 ? 1.8f : (float) Math.min(4.0, raw);
	}

	/** Full for the first quarter so the eye catches it at all, then convex: a residue, not a fade-out. */
	static float silhouetteAlpha(float progress) {
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		if (clamped <= 0.25f) {
			return 1.0f;
		}
		float fall = (clamped - 0.25f) / 0.75f;
		return (float) Math.pow(1.0 - fall, 1.8);
	}

	/**
	 * The body's own yaw widens or narrows the shoulders instead of turning the figure, which keeps it
	 * billboarded: a truly yaw-aligned silhouette collapses to a vertical line when seen edge-on, and it
	 * would spend its whole 200 ms unreadable.
	 */
	static float facingScale(float bodyYawDegrees, float viewYawDegrees) {
		double delta = Math.toRadians(bodyYawDegrees - viewYawDegrees);
		return (float) (0.45 + 0.55 * Math.abs(Math.cos(delta)));
	}

	private static void addTodoPulse(VertexConsumer consumer, Vec3 center, float radius, int alpha) {
		Vec3 horizontal = VfxWorldGeometry.EAST.scale(radius);
		Vec3 vertical = VfxWorldGeometry.UP.scale(radius);
		VfxWorldGeometry.addRibbon(consumer, center.subtract(horizontal), center.add(horizontal), vertical.scale(0.08), TODO_VIOLET_R, TODO_VIOLET_G, TODO_VIOLET_B, alpha);
		VfxWorldGeometry.addRibbon(consumer, center.subtract(vertical), center.add(vertical), horizontal.scale(0.08), TODO_EDGE_R, TODO_EDGE_G, TODO_EDGE_B, Math.round(alpha * 0.8f));
	}
}
