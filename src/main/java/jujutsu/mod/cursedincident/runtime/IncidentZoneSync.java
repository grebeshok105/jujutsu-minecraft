package jujutsu.mod.cursedincident.runtime;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.cursedincident.CursedIncidentVfxIds;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.SecondaryNode;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.IncidentZoneStatePayload;
import jujutsu.mod.network.JujutsuNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Broadcasts {@link IncidentZoneStatePayload} snapshots for one incident work center.
 * Audience is perception-filtered ({@link CursePerception#perceives}) inside the same
 * delivery radius the incident cues use — a non-mage client never learns a zone exists.
 */
public final class IncidentZoneSync {

	private IncidentZoneSync() {
	}

	/** Sends the current state of the parent work center (nodeId = zero UUID). */
	public static int sendZoneState(ServerLevel level, IncidentRecord record) {
		return send(level, record, IncidentZoneStatePayload.PARENT_NODE,
				record == null ? null : record.center,
				record == null ? 0.0 : record.radius, true);
	}

	/** Sends the current state of one secondary node at its own center/radius. */
	public static int sendZoneState(ServerLevel level, IncidentRecord record, SecondaryNode node) {
		if (node == null) {
			return 0;
		}
		return send(level, record, node.nodeId(), node.center(), node.radius(), !node.scarred());
	}

	/** Deactivates exactly one work-center key on every perceiving client. */
	public static int sendInactive(ServerLevel level, IncidentRecord record, UUID nodeId, BlockPos center) {
		return send(level, record, nodeId == null ? IncidentZoneStatePayload.PARENT_NODE : nodeId,
				center, 0.0, false);
	}

	private static int send(ServerLevel level, IncidentRecord record, UUID nodeId, BlockPos center,
			double radius, boolean active) {
		if (level == null || record == null || record.id == null || center == null) {
			return 0;
		}
		IncidentStage stage = record.stage == null ? IncidentStage.INITIAL : record.stage;
		String atmosphere = record.params == null ? "" : record.params.atmosphereId();
		IncidentZoneStatePayload payload = new IncidentZoneStatePayload(
				level.dimension().location().toString(),
				record.id,
				nodeId,
				center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
				radius,
				stage.ordinal(),
				atmosphere,
				record.sealed ? record.sealTier : 0,
				record.sealed ? record.sealIntegrity : 0,
				active);
		int sent = 0;
		// Inactive teardown reaches every perceiving player in the level, not just the
		// delivery radius: a client that cached the zone then walked away must still hear
		// the stand-down, or the stale entry lives until the client-side TTL.
		for (ServerPlayer player : active
				? recipients(level, center)
				: level.players().stream().filter(CursePerception::perceives).toList()) {
			if (ServerPlayNetworking.canSend(player, IncidentZoneStatePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}
		return sent;
	}

	/**
	 * The audience a zone-state broadcast reaches: perceiving players inside the delivery
	 * radius. Exposed so GameTests can assert the non-mage filter directly — the {@code sent}
	 * counter stays 0 for placed victims (no negotiated channels) and can never serve as an
	 * oracle.
	 */
	public static java.util.List<ServerPlayer> recipients(ServerLevel level, BlockPos center) {
		return JujutsuNetworking.recipients(level, Vec3.atCenterOf(center),
				CursedIncidentVfxIds.VFX_DELIVERY_RADIUS, CursePerception::perceives);
	}
}
