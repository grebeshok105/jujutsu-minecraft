package jujutsu.mod.character.megumi;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.network.ShikigamiStatePayload;

/**
 * Builds and pushes the selector's authoritative snapshot to its owner.
 *
 * <p>One place assembles the payload, because the client's markers are only as honest as the facts
 * behind them: the active selection, which type has a live pack, and each type's deadline. Every call
 * site that can change one of those pushes — summon, recall, death, expiry, cycle, a direct click, a
 * join and a vessel change — so the strip never shows a slot state the server has already moved past.
 *
 * <p>The headless guard is not defensive style: Fabric's {@code canSend} asserts on the connection
 * rather than reporting false, and a GameTest player has none, so a snapshot must decide it has
 * nowhere to send before it asks. Same shape as {@code JujutsuNetworking.sendAbilityCooldown}.
 */
public final class MegumiShikigamiSync {
	private MegumiShikigamiSync() {}

	/** Sends the owner their roster snapshot. A player with no connection receives nothing. */
	public static void push(ServerPlayer player) {
		if (player == null || player.connection == null) {
			return;
		}
		if (!ServerPlayNetworking.canSend(player, ShikigamiStatePayload.TYPE)) {
			return;
		}
		UUID ownerId = player.getUUID();
		long now = player.level().getGameTime();
		Set<MegumiShikigami> activeTypes = EnumSet.noneOf(MegumiShikigami.class);
		for (MegumiShikigamiPack pack : MegumiShikigamiRuntime.packs(ownerId)) {
			activeTypes.add(pack.type());
		}
		if (MegumiSummonRuntime.pack(ownerId) != null) {
			activeTypes.add(MegumiShikigami.DOGS);
		}
		MegumiShikigami[] roster = MegumiShikigami.values();
		byte[] stateCodes = new byte[roster.length];
		long[] cooldownUntil = new long[roster.length];
		for (int i = 0; i < roster.length; i++) {
			MegumiShikigami type = roster[i];
			long remaining = MegumiSummonCooldowns.remainingTicks(ownerId, type, now);
			boolean summoned = activeTypes.contains(type);
			cooldownUntil[i] = remaining > 0 ? now + remaining : 0L;
			stateCodes[i] = (byte) MegumiShikigamiSlotState.derive(summoned, (int) Math.min(remaining, Integer.MAX_VALUE)).ordinal();
		}
		ServerPlayNetworking.send(player, new ShikigamiStatePayload(
				MegumiShikigamiSelection.selected(ownerId).id(), stateCodes, cooldownUntil, now));
	}
}
