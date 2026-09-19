package jujutsu.mod.client.tongue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.TonguePullPolicy;
import jujutsu.mod.network.MegumiTongueStatePayload;

/**
 * Client-side presentation and pull state for every owner's manifested Toad tongue.
 *
 * <p>The server sends a phase transition and an immutable anchor. The client interpolates the
 * physical tip through SHOOTING and RETRACTING while the local player still uses the anchored point
 * for the existing mobility law. Entries are keyed by owner UUID so remote players render correctly.
 */
public final class TongueClientState {
	public enum Phase {
		SHOOTING(MegumiTongueStatePayload.SHOOTING, 4),
		ANCHORED(MegumiTongueStatePayload.ANCHORED, 1),
		RETRACTING(MegumiTongueStatePayload.RETRACTING, 5);

		private final int wireValue;
		private final int durationTicks;

		Phase(int wireValue, int durationTicks) {
			this.wireValue = wireValue;
			this.durationTicks = durationTicks;
		}

		public int wireValue() {
			return wireValue;
		}

		public int durationTicks() {
			return durationTicks;
		}

		public static Phase fromWire(int wireValue) {
			for (Phase phase : values()) {
				if (phase.wireValue == wireValue) {
					return phase;
				}
			}
			return null;
		}
	}

	private static final long STALE_AFTER_TICKS = 80L;
	private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
	private static long lastKnownGameTime;

	private TongueClientState() {}

	/** Applies a server phase update, including hard inactive teardown. */
	public static void apply(MegumiTongueStatePayload payload) {
		if (payload == null || payload.ownerUuid() == null) {
			return;
		}
		Phase phase = Phase.fromWire(payload.phase());
		if (phase == null) {
			return;
		}
		long now = gameTime();
		synchronized (ENTRIES) {
			if (!payload.active()) {
				ENTRIES.remove(payload.ownerUuid());
				return;
			}
			Entry previous = ENTRIES.get(payload.ownerUuid());
			if (previous != null && phase.ordinal() < previous.phase().ordinal()) {
				return;
			}
			long localPhaseStart = previous != null && previous.phase() == phase
					? previous.localPhaseStartGameTime()
					: phase == Phase.SHOOTING && payload.shotGameTime() > 0L
							? payload.shotGameTime() : now;
			ENTRIES.put(payload.ownerUuid(), new Entry(
					phase,
					new Vec3(payload.anchorX(), payload.anchorY(), payload.anchorZ()),
					payload.shotGameTime(),
					localPhaseStart,
					now));
		}
	}

	/** Advances interpolation and removes stale/finished remote entries. */
	public static void tick() {
		long now = gameTime();
		synchronized (ENTRIES) {
			cleanup(now);
		}
	}

	/** Returns the current phase for an owner, or {@code null} after teardown. */
	public static Phase phaseFor(UUID ownerUuid) {
		if (ownerUuid == null) {
			return null;
		}
		long now = gameTime();
		synchronized (ENTRIES) {
			cleanup(now);
			Entry entry = ENTRIES.get(ownerUuid);
			return entry == null ? null : entry.phase();
		}
	}

	/** True while the owner's head/tongue presentation still has a live phase. */
	public static boolean active(UUID ownerUuid) {
		return phaseFor(ownerUuid) != null;
	}

	/** Compatibility query for the local-player physics and old callers. */
	public static boolean isActive() {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && active(client.player.getUUID());
	}

	/** Returns the latest anchor for an owner, or {@link Vec3#ZERO} when absent. */
	public static Vec3 anchor(UUID ownerUuid) {
		Entry entry = entry(ownerUuid);
		return entry == null ? Vec3.ZERO : entry.anchor();
	}

	/** Compatibility query for the local player's anchor. */
	public static Vec3 anchor() {
		Minecraft client = Minecraft.getInstance();
		return client.player == null ? Vec3.ZERO : anchor(client.player.getUUID());
	}

	/** Normalized current phase progress, with shooting/retraction partial-tick interpolation. */
	public static float phaseProgress(UUID ownerUuid, float partialTick) {
		Entry entry = entry(ownerUuid);
		return entry == null ? 0.0f : progress(entry, partialTick);
	}

	/** Returns the world-space animated tongue tip for an owner. */
	public static Vec3 tipPosition(Player owner, float partialTick) {
		if (owner == null) {
			return null;
		}
		Entry entry = entry(owner.getUUID());
		if (entry == null) {
			return null;
		}
		return tipPosition(mouthWorldPos(owner, partialTick), entry.anchor(), entry.phase(), progress(entry, partialTick));
	}

	/** Pure tip interpolation used by the renderer and phase tests. */
	public static Vec3 tipPosition(Vec3 mouth, Vec3 anchor, Phase phase, float progress) {
		if (mouth == null || anchor == null || phase == null) {
			return mouth;
		}
		float eased = Mth.clamp(progress, 0.0f, 1.0f);
		if (phase == Phase.ANCHORED) {
			return anchor;
		}
		if (phase == Phase.RETRACTING) {
			return anchor.lerp(mouth, eased);
		}
		return mouth.lerp(anchor, eased);
	}

	/** The manifested mouth point above the owner's head, yaw-facing toward the tongue. */
	public static Vec3 mouthWorldPos(Player owner) {
		return mouthWorldPos(owner, 1.0f);
	}

	/** Partial-tick variant used by the world renderer to avoid a one-frame root snap. */
	public static Vec3 mouthWorldPos(Player owner, float partialTick) {
		if (owner == null) {
			return Vec3.ZERO;
		}
		Vec3 position = owner.getPosition(partialTick);
		float yaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot()) * Mth.DEG_TO_RAD;
		Vec3 horizontal = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		if (horizontal.lengthSqr() < 1.0E-6) {
			horizontal = new Vec3(0.0, 0.0, 1.0);
		} else {
			horizontal = horizontal.normalize();
		}
		return position.add(0.0, owner.getBbHeight() + 0.29, 0.0).add(horizontal.scale(0.132));
	}

	/** Retracting and shooting heads fade with their phase; anchored heads stay fully visible. */
	public static float alpha(UUID ownerUuid, float partialTick) {
		Entry entry = entry(ownerUuid);
		if (entry == null) {
			return 0.0f;
		}
		return switch (entry.phase()) {
			case SHOOTING -> progress(entry, partialTick);
			case ANCHORED -> 1.0f;
			case RETRACTING -> 1.0f - progress(entry, partialTick);
		};
	}

	/** Ticks the local pull ramp from the original shot timestamp. */
	public static int holdTicks() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return 0;
		}
		Entry entry = entry(client.player.getUUID());
		if (entry == null || entry.phase() != Phase.ANCHORED) {
			return 0;
		}
		ClientLevel level = client.level;
		if (level == null) {
			return 0;
		}
		long held = level.getGameTime() - entry.shotGameTime();
		return (int) Math.min(Math.max(held, 0L), Integer.MAX_VALUE);
	}

	/**
	 * The velocity the local player should carry out of this tick's travel: pull while anchored,
	 * release (identity) during shooting, retracting, and hard teardown.
	 */
	public static Vec3 nextVelocity(Vec3 velocity, Vec3 playerPos, Input input) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return TonguePullPolicy.release(velocity);
		}
		Entry entry = entry(client.player.getUUID());
		if (entry == null || entry.phase() != Phase.ANCHORED) {
			return TonguePullPolicy.release(velocity);
		}
		return TonguePullPolicy.pull(velocity, playerPos, entry.anchor(), holdTicks(), input);
	}

	/** Drops every remote entry on disconnect or level change. */
	public static void clear() {
		synchronized (ENTRIES) {
			ENTRIES.clear();
		}
	}

	private static Entry entry(UUID ownerUuid) {
		if (ownerUuid == null) {
			return null;
		}
		long now = gameTime();
		synchronized (ENTRIES) {
			cleanup(now);
			return ENTRIES.get(ownerUuid);
		}
	}

	private static float progress(Entry entry, float partialTick) {
		if (entry.phase() == Phase.ANCHORED) {
			return 1.0f;
		}
		long elapsed = gameTime() - entry.localPhaseStartGameTime();
		return Mth.clamp((elapsed + partialTick) / (float) entry.phase().durationTicks(), 0.0f, 1.0f);
	}

	private static void cleanup(long now) {
		ENTRIES.entrySet().removeIf(entry -> {
			Entry state = entry.getValue();
			boolean stalledShot = state.phase() == Phase.SHOOTING
					&& now - state.lastSeenGameTime() > STALE_AFTER_TICKS;
			boolean finishedRetract = state.phase() == Phase.RETRACTING
					&& now - state.localPhaseStartGameTime() >= Phase.RETRACTING.durationTicks();
			return stalledShot || finishedRetract;
		});
	}

	private static long gameTime() {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			lastKnownGameTime = client.level.getGameTime();
		}
		return lastKnownGameTime;
	}

	private record Entry(
			Phase phase,
			Vec3 anchor,
			long shotGameTime,
			long localPhaseStartGameTime,
			long lastSeenGameTime) {}
}
