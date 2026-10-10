package jujutsu.mcpdev;

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

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import jujutsu.mod.character.megumi.MegumiShikigami;

/**
 * Directly selects a Megumi shikigami slot for live QA — the selector strip and the cycle key
 * exist for humans, but the acceptance script needs to land on one roster entry by id.
 *
 * <p>The id must equal a canonical {@link MegumiShikigami#id} (case-insensitive): the enum's
 * unknown-id fallback must not swallow typos, so anything else is {@code TOOL_INPUT_INVALID}.
 * The mutation runs on the server main thread through the same selection path the strip uses,
 * so cooldown/lock refusals behave identically — the tool reports the refusal instead of
 * bypassing it.
 */
@McpTool(
		name = "jujutsu_shikigami_select",
		description = "Selects one Megumi shikigami slot by id (dogs, nue, toad, rabbits, elephant, serpent, deer, ox, tiger).")
public final class JujutsuShikigamiSelectTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.required("shikigami_id", Schemas.string("Canonical shikigami id (case-insensitive)"))
			.build();

	public JujutsuShikigamiSelectTool() {
		super("jujutsu_shikigami_select");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID playerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("player_uuid"));
		String shikigamiId = r.requireString("shikigami_id");
		MegumiShikigami target = resolveShikigami(shikigamiId);
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					// Same entry point as the selector packet — a non-Megumi vessel answers false.
					boolean accepted = jujutsu.mod.character.JujutsuCharacters.of(player)
							.selectShikigami(player, target.id());
					ObjectNode node = context.mapper().createObjectNode();
					node.put("player", player.getUUID().toString());
					node.put("shikigami", target.id());
					node.put("accepted", accepted);
					return ToolResult.ofToon(node);
				});
	}

	/** Canonical-id check that refuses typos instead of falling back to the first slot. */
	private static MegumiShikigami resolveShikigami(String id) {
		for (MegumiShikigami type : MegumiShikigami.values()) {
			if (type.id().equalsIgnoreCase(id)) {
				return type;
			}
		}
		throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Unknown shikigami id: " + id);
	}
}
