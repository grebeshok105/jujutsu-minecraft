package jujutsu.mod.combat;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * One server-side implementation of "this body is being held": the Toad's grab and a cursed
 * spirit's runner pin their victim the same way, so they share the code and the
 * {@link JujutsuEffects#GRIPPED} marker the client reads.
 *
 * <p>Why a pin instead of a velocity: the client owns its own movement, so a server-side vector on
 * a player is a fiction. A vanilla teleport packet, however, is obeyed — and the marker stops the
 * client from rubber-banding against it. The authoritative half stays on the server: every held
 * tick re-applies the anchor.
 */
public final class HoldSupport {
	private HoldSupport() {}

	/**
	 * Pins {@code victim} to {@code anchor} for the next {@code markerTicks} and refreshes the
	 * marker. Safe to call every tick: the marker is a short re-applied buff, not a timer.
	 */
	public static void applyHold(LivingEntity victim, Vec3 anchor, int markerTicks) {
		victim.setDeltaMovement(Vec3.ZERO);
		victim.teleportTo(anchor.x, anchor.y, anchor.z);
		// The teleport packet moves the position; hurtMarked carries the motion reset.
		victim.hurtMarked = true;
		victim.addEffect(new MobEffectInstance(JujutsuEffects.GRIPPED,
				Math.max(1, markerTicks), 0, false, false, false));
	}

	/** Drops the marker: whoever was holding the body has let go (recall, death, break). */
	public static void release(LivingEntity victim) {
		victim.removeEffect(JujutsuEffects.GRIPPED);
	}

	/** Whether the body is currently marked as held — the client-side suppression reads the same. */
	public static boolean isHeld(LivingEntity victim) {
		return victim.hasEffect(JujutsuEffects.GRIPPED);
	}
}
