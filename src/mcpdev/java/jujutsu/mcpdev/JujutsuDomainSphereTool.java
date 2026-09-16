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
 * Fires the debug domain-sphere VFX once, ahead of the target player (issue #43 dev lane).
 *
 * <p>This is the dev-lane twin of {@code /jujutsu domain_sphere}: it emits the same
 * {@code jujutsumod:domain_sphere} cue through the production transport
 * ({@link JujutsuNetworking#sendVfxCue}), four blocks down the player's look vector, so an
 * OMP-driven run can trigger and re-trigger the effect without typing a command in the
 * game window. Nothing here reaches gameplay — the recipe is registered by
 * {@code DomainSphereDebug} on the client, and the id constant lives in
 * {@code jujutsu.mod.vfx} (main) precisely so this dev source set can name it.
 *
 * <p>{@code routed} is {@code false} when the connection cannot carry the payload (headless
 * GameTest player, no client attached) — a result, not an exception, mirroring
 * {@link JujutsuAbilityInvokeTool}'s refusal shape. {@code radius} is clamped to the same
 * 5..64 range the schema advertises and the client command enforces, so a client that
 * ignores {@code minimum}/{@code maximum} still cannot ask for an absurd sphere.
 */
@McpTool(
		name = "jujutsu_domain_sphere",
		description = "Triggers the debug domain-sphere effect ahead of the target player")
public final class JujutsuDomainSphereTool extends BaseTool {

	private static final int MIN_RADIUS = 5;
	private static final int MAX_RADIUS = 64;
	private static final int DEFAULT_RADIUS = 30;

	/** The cue lands this far along the look vector, measured from the eye position. */
	private static final double AHEAD_BLOCKS = 4.0;

	private static final JsonNode SCHEMA = Schemas.object()
			.required("player_uuid", Schemas.string("Player UUID"))
			.optional("radius", Schemas.integerBetween(
					"Sphere radius in blocks; default " + DEFAULT_RADIUS, MIN_RADIUS, MAX_RADIUS))
			.build();

	public JujutsuDomainSphereTool() {
		super("jujutsu_domain_sphere");
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
		int radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r.optInt("radius", DEFAULT_RADIUS)));
		return onMainThread(
				context,
				ignored -> {
					ServerPlayer player = JujutsuMcpdevPlayers.requireOnline(server, playerUuid);
					Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(AHEAD_BLOCKS));
					VfxCue cue = VfxCues.worldFixed(DebugVfxIds.DOMAIN_SPHERE, origin, radius,
							player.level().getGameTime(), player.getRandom().nextLong());
					ObjectNode node = context.mapper().createObjectNode();
					node.put("routed", JujutsuNetworking.sendVfxCue(player, cue));
					return ToolResult.ofToon(node);
				});
	}
}
