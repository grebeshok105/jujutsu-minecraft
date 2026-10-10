package jujutsu.mcpdev;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.ArgumentReader;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.DebugVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxCues;

/**
 * Dev-lane twin of {@code /jujutsu_debug black_hole}: emits the same
 * {@code jujutsumod:black_hole} cue through the production transport so an OMP-driven run can
 * trigger the effect without typing in the game window. {@code seconds} is the stable-phase
 * duration; the client clamps it to the same range the command enforces.
 */
@McpTool(
		name = "jujutsu_black_hole",
		description = "Triggers the black hole visual experiment ahead of the target player")
public final class JujutsuBlackHoleTool extends BaseTool {
	private static final int MIN_SECONDS = 1;
	private static final int MAX_SECONDS = 60;
	private static final int DEFAULT_SECONDS = 7;
	/** Spawn distance ahead of the eye, matching BlackHoleProfile.SPAWN_DISTANCE on the client. */
	private static final double AHEAD_BLOCKS = 30.0;

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.optional("seconds", Schemas.integerBetween(
					"Stable-phase seconds; default " + DEFAULT_SECONDS, MIN_SECONDS, MAX_SECONDS))
			.build();

	public JujutsuBlackHoleTool() {
		super("jujutsu_black_hole");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		int seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, r.optInt("seconds", DEFAULT_SECONDS)));
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(AHEAD_BLOCKS));
					VfxCue cue = VfxCues.worldFixed(DebugVfxIds.BLACK_HOLE, origin, seconds * 20,
							player.level().getGameTime(), player.getRandom().nextLong());
					ObjectNode node = context.mapper().createObjectNode();
					node.put("routed", JujutsuNetworking.sendVfxCue(player, cue));
					return ToolResult.ofToon(node);
				});
	}
}
