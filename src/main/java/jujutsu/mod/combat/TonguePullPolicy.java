package jujutsu.mod.combat;

import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * The pull law of Toad's partial tongue (issue #108): what one tick of hold-to-anchor does to the
 * holder's velocity. Pure — the world feeds facts in, nothing here reads or writes it.
 *
 * <p><b>Why this is not client code.</b> The physics runs in a mixin on {@code Player.travel}, and
 * that mixin sits in the shared client package, which by the vessel boundary rule may not name a
 * vessel's own types. The law therefore lives here, shared, and the vessel keeps the parts that are
 * genuinely its own (the anchor resolution and the state machine). Same reasoning as
 * {@link HoldSupport}, the other shared helper that touches a player's motion.
 *
 * <p><b>The law.</b> Each tick adds {@code 0.08} blocks/tick² along the line to the anchor, then
 * clamps the <em>total</em> speed to a cap that ramps from {@code 0.5} to {@code 1.2} blocks/tick
 * over 40 held ticks (D9: the cap is what keeps the client's position packets under the server's
 * "moved too quickly" threshold). Gravity is never cancelled: the policy only ever adds along its
 * own axis and never flips a fall upward — a climb is the player's business, not the tongue's.
 *
 * <p><b>Steering.</b> WASD deflects the axis by at most {@value #STEER_MAX_DEGREES} degrees in the
 * plane perpendicular to the line: A/D swing the holder around it and W/S aim the pull up or down,
 * which is the pair the arcs, the pump and the dive-to-glide of R36 fall out of. Two geometries have
 * nothing to bend: a line that runs exactly vertical offers no lift or drop, and over a body that is
 * neither moving nor under a line with a horizontal direction there is no sideways reference either
 * — then the pull simply stays on axis. Vanilla applies the friction the policy discards, which is
 * deliberate: the tongue drives, not the ground.
 */
public final class TonguePullPolicy {
	/** Blocks per tick² the tongue adds along its axis while held. */
	public static final double ACCELERATION_PER_TICK = 0.14;
	/** Speed cap at the instant of attachment, blocks/tick. */
	public static final double BASE_SPEED_CAP = 0.9;
	/** Cap growth per held tick, blocks/tick² — 40 held ticks reach {@link #MAX_SPEED_CAP} exactly. */
	public static final double SPEED_CAP_GAIN_PER_TICK = 0.0175;
	/** Hard ceiling of the cap ramp, blocks/tick. */
	public static final double MAX_SPEED_CAP = 1.6;
	/** Largest deflection a held key may put between the tongue's axis and the applied pull. */
	public static final double STEER_MAX_DEGREES = 20.0;
	/** {@code tan(STEER_MAX_DEGREES)}: the tangent length that deflects the axis by exactly that angle. */
	private static final double STEER_TANGENT = Math.tan(Math.toRadians(STEER_MAX_DEGREES));
	/** Both a zero input vector and a heading too short to define a frame compare below this. */
	private static final double MIN_DIRECTION_SQR = 1.0E-6;
	/** Closer than this the line has no direction to add along; the cap alone still applies. */
	private static final double MIN_PULL_DISTANCE_SQR = 0.0025;

	private TonguePullPolicy() {}

	/**
	 * One tick of the pull. {@code velocity} is the velocity vanilla just computed for this tick
	 * (gravity and any flight input already in it), {@code playerPos} the body position the anchor
	 * was attached from, and {@code holdTicks} the ticks the key has been held.
	 */
	public static Vec3 pull(Vec3 velocity, Vec3 playerPos, Vec3 anchor, int holdTicks, Input input) {
		double cap = speedCap(holdTicks);
		Vec3 toAnchor = anchor.subtract(playerPos);
		if (toAnchor.lengthSqr() < MIN_PULL_DISTANCE_SQR) {
			// Reached the anchor: nothing left to add along, but the cap still bounds the tick.
			return clampSpeed(velocity, cap);
		}
		Vec3 axis = toAnchor.normalize();
		Vec3 steered = steer(axis, horizontal(velocity), input);
		return clampSpeed(velocity.add(steered.scale(ACCELERATION_PER_TICK)), cap);
	}

	/**
	 * The law while no tongue is attached: the velocity is kept verbatim — and, deliberately, the
	 * <em>same instance</em>, which is what lets the physics caller skip a pointless
	 * {@code setDeltaMovement} on every released tick.
	 */
	public static Vec3 release(Vec3 velocity) {
		return velocity;
	}

	/** The speed cap {@code holdTicks} ticks into a hold — the ramp of D9, clamped at both ends. */
	public static double speedCap(int holdTicks) {
		int held = Math.max(0, holdTicks);
		return Math.min(BASE_SPEED_CAP + held * SPEED_CAP_GAIN_PER_TICK, MAX_SPEED_CAP);
	}

	/**
	 * Deflects the pull axis toward the held keys, in the plane perpendicular to the line: A/D swing
	 * the holder around the line's vertical axis, W/S aim the pull up or down. The deflection is an
	 * angle, never a direction flip — at most {@value #STEER_MAX_DEGREES} degrees whatever is held, so
	 * a full keyboard cannot out-pull the law. {@code heading} is only needed for the one geometry
	 * that has no sideways reference of its own: a line that runs exactly vertical.
	 */
	static Vec3 steer(Vec3 axis, Vec3 heading, Input input) {
		double liftKey = (input.forward() ? 1.0 : 0.0) - (input.backward() ? 1.0 : 0.0);
		double swingKey = (input.right() ? 1.0 : 0.0) - (input.left() ? 1.0 : 0.0);
		if (liftKey == 0.0 && swingKey == 0.0) {
			return axis;
		}
		Vec3 push = new Vec3(0.0, liftKey, 0.0).add(lateral(axis, heading).scale(swingKey));
		if (push.lengthSqr() < MIN_DIRECTION_SQR) {
			// A line straight up or down over a body with no motion: neither pair has anywhere to bend.
			return axis;
		}
		Vec3 tangent = push.subtract(axis.scale(push.dot(axis)));
		if (tangent.lengthSqr() < MIN_DIRECTION_SQR) {
			// The keys point along the line itself: a deflection cannot, so this is a reel, not a steer.
			return axis;
		}
		// tangent is perpendicular to axis by construction, so the tangent length is the tangent of the
		// deflection angle — exactly STEER_MAX_DEGREES, whatever the key combination.
		return axis.add(tangent.normalize().scale(STEER_TANGENT)).normalize();
	}

	/**
	 * The horizontal direction perpendicular to the line, to the holder's right: the line's own
	 * horizontal heading turned 90° clockwise as seen from above (facing +Z puts -X on the right).
	 * A vertical line has no such heading, and there every horizontal direction is equally
	 * perpendicular to it — so the body's own heading stands in, and if it has none the pair is dead.
	 */
	private static Vec3 lateral(Vec3 axis, Vec3 heading) {
		Vec3 flat = horizontal(axis);
		Vec3 reference = flat.lengthSqr() < MIN_DIRECTION_SQR ? heading : flat;
		if (reference.lengthSqr() < MIN_DIRECTION_SQR) {
			return Vec3.ZERO;
		}
		Vec3 forward = reference.normalize();
		return new Vec3(-forward.z, 0.0, forward.x);
	}

	/** The horizontal part of a vector; zero when the vector is vertical. */
	private static Vec3 horizontal(Vec3 vector) {
		return new Vec3(vector.x, 0.0, vector.z);
	}

	/** Scales {@code velocity} down to {@code cap} when it exceeds it; the instance is kept otherwise. */
	private static Vec3 clampSpeed(Vec3 velocity, double cap) {
		double speedSqr = velocity.lengthSqr();
		if (speedSqr <= cap * cap) {
			return velocity;
		}
		return velocity.scale(cap / Math.sqrt(speedSqr));
	}
}
