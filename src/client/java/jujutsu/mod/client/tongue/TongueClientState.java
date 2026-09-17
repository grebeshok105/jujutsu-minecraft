package jujutsu.mod.client.tongue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.TonguePullPolicy;
import jujutsu.mod.network.MegumiTongueStatePayload;

/**
 * The client's whole picture of Toad's partial tongue (issue #108): whether it is anchored, where,
 * and how long it has been held. Fed by {@link MegumiTongueStatePayload} — the server is
 * authoritative about the state, while the <em>physics</em> of the pull is client-authoritative
 * (HoldSupport doctrine: a server-side velocity on a player is a fiction, the owning client's own
 * integration is what actually moves the body).
 *
 * <p>The hold counter starts when the activation payload lands, not when the server attached: the
 * cap ramp is a feel parameter, and the round trip is under a tick. {@link #nextVelocity} is the
 * single law entry point the travel mixin calls — released ticks return the velocity instance
 * untouched, so nothing is written and nothing is allocated.
 */
public final class TongueClientState {
	private static boolean active;
	private static Vec3 anchor = Vec3.ZERO;
	private static long attachedGameTime;

	private TongueClientState() {}

	/** Applies a server state update: attach to the anchor, or drop it on break/release. */
	public static void apply(MegumiTongueStatePayload payload) {
		active = payload.active();
		anchor = active ? new Vec3(payload.anchorX(), payload.anchorY(), payload.anchorZ()) : Vec3.ZERO;
		ClientLevel level = Minecraft.getInstance().level;
		attachedGameTime = level == null ? 0L : level.getGameTime();
	}

	/** Drops the state — the client half of the disconnect-is-a-clean-slate rule. */
	public static void clear() {
		active = false;
		anchor = Vec3.ZERO;
	}

	public static boolean isActive() {
		return active;
	}

	/** The attached anchor, or {@link Vec3#ZERO} while no tongue is out. */
	public static Vec3 anchor() {
		return anchor;
	}

	/** Ticks the tongue has been held on this client, floored at zero across a clock reset. */
	public static int holdTicks() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return 0;
		}
		long held = level.getGameTime() - attachedGameTime;
		return (int) Math.min(Math.max(held, 0L), Integer.MAX_VALUE);
	}

	/**
	 * The velocity the local player should carry out of this tick's travel: {@link
	 * TonguePullPolicy#pull} while anchored, {@link TonguePullPolicy#release} (identity) otherwise.
	 */
	public static Vec3 nextVelocity(Vec3 velocity, Vec3 playerPos, Input input) {
		if (!active) {
			return TonguePullPolicy.release(velocity);
		}
		return TonguePullPolicy.pull(velocity, playerPos, anchor, holdTicks(), input);
	}
}
