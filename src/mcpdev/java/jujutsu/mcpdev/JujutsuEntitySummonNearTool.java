package jujutsu.mcpdev;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.ArgumentReader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Summons an entity next to a player (combat-test setup). With no offset the
 * entity spawns 3 blocks ahead of the player's look direction at eye height;
 * {@code dx}/{@code dy}/{@code dz} add an explicit offset to that anchor.
 * {@code nbt} is optional SNBT merged into the summon (e.g. a spirit ability
 * pool or a health buff).
 *
 * <p>The spawn itself delegates to the vanilla {@code /summon} command — same
 * mechanism as upstream {@code entity_summon} — and the new entity's UUID is
 * recovered by diffing the entity uuids in a small box around the spawn point
 * (the summon success flag does not surface it).
 */
@McpTool(
		name = "jujutsu_entity_summon_near",
		description = "Summons an entity near a player with optional offset and SNBT data")
public final class JujutsuEntitySummonNearTool extends BaseTool {

	/** Default forward distance from the player's eyes, in blocks. */
	private static final double FORWARD_BLOCKS = 3.0;

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID — the summon anchor"))
			.required("entity_type", Schemas.string("Entity type id, e.g. minecraft:zombie or jujutsumod:cursed_spirit"))
			.optional("dx", Schemas.number("Extra X offset added to the spawn anchor"))
			.optional("dy", Schemas.number("Extra Y offset added to the spawn anchor"))
			.optional("dz", Schemas.number("Extra Z offset added to the spawn anchor"))
			.optional("nbt", Schemas.string("SNBT merged into the summoned entity"))
			.build();

	public JujutsuEntitySummonNearTool() {
		super("jujutsu_entity_summon_near");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		String entityType = r.requireString("entity_type");
		double dx = r.optDouble("dx", 0.0);
		double dy = r.optDouble("dy", 0.0);
		double dz = r.optDouble("dz", 0.0);
		String nbt = r.optString("nbt", null);
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					ServerLevel level = player.level();
					Vec3 spawn = player.getEyePosition()
							.add(player.getLookAngle().normalize().scale(FORWARD_BLOCKS))
							.add(dx, dy, dz);
					String dimension = level.dimension().location().toString();

					BlockPos blockPos = BlockPos.containing(spawn);
					AABB box = new AABB(blockPos).inflate(3.0);
					Set<UUID> before = new HashSet<>();
					for (Entity e : level.getEntities((Entity) null, box, ent -> true)) {
						before.add(e.getUUID());
					}

					String command = String.format(
							Locale.ROOT,
							"execute in %s run summon %s %f %f %f%s",
							dimension, entityType, spawn.x(), spawn.y(), spawn.z(),
							nbt == null || nbt.isBlank() ? "" : " " + nbt);
					var source = server.createCommandSourceStack().withSuppressedOutput();
					int result;
					try {
						result = server.getCommands().getDispatcher().execute(command, source);
					} catch (Exception e) {
						throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
								"Summon command failed: " + command + " (" + e.getMessage() + ")", e);
					}
					if (result == 0) {
						throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
								"Summon command failed: " + command);
					}

					UUID newest = null;
					int newestTick = Integer.MAX_VALUE;
					for (Entity e : level.getEntities((Entity) null, box, ent -> true)) {
						if (before.contains(e.getUUID())) {
							continue;
						}
						if (e.tickCount < newestTick) {
							newestTick = e.tickCount;
							newest = e.getUUID();
						}
					}
					ObjectNode node = context.mapper().createObjectNode();
					node.put("x", spawn.x());
					node.put("y", spawn.y());
					node.put("z", spawn.z());
					node.put("dimension", dimension);
					if (newest == null) {
						node.putNull("uuid");
						node.put("resolved", false);
					} else {
						node.put("uuid", newest.toString());
						node.put("resolved", true);
					}
					return ToolResult.ofToon(node);
				});
	}
}
