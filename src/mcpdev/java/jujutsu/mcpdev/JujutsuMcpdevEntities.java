package jujutsu.mcpdev;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;

/**
 * Shared cross-dimension entity resolution for the dev-control tools. Same
 * fail-closed contract as {@link JujutsuMcpdevPlayers}: call on the server main
 * thread inside {@code onMainThread}; unknown uuids throw
 * {@code TOOL_HANDLER_ERROR} (the upstream "Entity not found" idiom) while
 * soft-check tools (combat log filters, wait conditions) use {@link #find}
 * instead.
 */
final class JujutsuMcpdevEntities {

	private JujutsuMcpdevEntities() {}

	/**
	 * Looks up an entity by uuid across all loaded levels; returns {@code null}
	 * when it is not loaded in any dimension (removed, unloaded, or never spawned).
	 * Players resolve through the player list first so a dead-but-online player
	 * still resolves (its entity stays registered until respawn).
	 */
	static Entity find(MinecraftServer server, UUID uuid) {
		ServerPlayer player = server.getPlayerList().getPlayer(uuid);
		if (player != null) {
			return player;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity entity = level.getEntity(uuid);
			if (entity != null) {
				return entity;
			}
		}
		return null;
	}

	/**
	 * {@link #find} plus the hard failure: unknown uuid is {@code TOOL_HANDLER_ERROR}.
	 */
	static Entity require(MinecraftServer server, UUID uuid) {
		Entity entity = find(server, uuid);
		if (entity == null) {
			throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR, "Entity not found: " + uuid);
		}
		return entity;
	}
}
