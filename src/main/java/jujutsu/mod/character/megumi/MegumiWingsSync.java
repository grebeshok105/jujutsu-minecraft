package jujutsu.mod.character.megumi;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.network.MegumiWingsStatePayload;

/** Broadcasts the server's current visual phase to the owner and nearby tracking players. */
public final class MegumiWingsSync {
	private static final Map<UUID, PhaseStart> PHASE_STARTS = new ConcurrentHashMap<>();

	private MegumiWingsSync() {}

	/** Sends an active phase, retaining its original game-time anchor across heartbeats. */
	public static void send(ServerPlayer owner, int phase) {
		if (owner == null || !MegumiWingsStatePayload.isKnownPhase(phase)) {
			return;
		}
		long now = owner.level().getGameTime();
		UUID ownerUuid = owner.getUUID();
		PhaseStart phaseStart = PHASE_STARTS.compute(ownerUuid, (ignored, previous) ->
				previous == null || previous.phase() != phase
						? new PhaseStart(phase, now)
						: previous);
		broadcast(owner, new MegumiWingsStatePayload(ownerUuid, true, phase, phaseStart.gameTime()));
	}

	/** Sends the folding teardown packet and forgets the owner's heartbeat anchor. */
	public static void sendInactive(ServerPlayer owner) {
		if (owner == null) {
			return;
		}
		long now = owner.level().getGameTime();
		PHASE_STARTS.remove(owner.getUUID());
		broadcast(owner, new MegumiWingsStatePayload(
				owner.getUUID(), false, MegumiWingsStatePayload.FOLDING, now));
	}

	private static void broadcast(ServerPlayer owner, MegumiWingsStatePayload payload) {
		Set<ServerPlayer> recipients = new LinkedHashSet<>(PlayerLookup.tracking(owner));
		recipients.add(owner);
		for (ServerPlayer recipient : recipients) {
			if (recipient.connection != null && ServerPlayNetworking.canSend(recipient, payload.type())) {
				ServerPlayNetworking.send(recipient, payload);
			}
		}
	}

	private record PhaseStart(int phase, long gameTime) {}
}
