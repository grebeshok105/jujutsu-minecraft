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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Deals source-aware damage: the hit arrives through the target's normal
 * {@code hurtServer} path carrying the attacker's damage source, so interaction
 * gates (e.g. "a non-sorcerer cannot hurt a cursed spirit") and the combat log
 * see a real attacker — unlike a generic-source {@code /damage} command.
 *
 * <p>{@code amount} is optional: when omitted the attacker's
 * {@code attack_damage} attribute is used (falling back to 1.0 for entities
 * without the attribute). The attacker must be a {@link LivingEntity} —
 * player attackers produce a {@code playerAttack} source, anything else a
 * {@code mobAttack} source.
 */
@McpTool(
		name = "jujutsu_entity_attack",
		description = "Deals damage to an entity with the attacker's real damage source (mob/player attack)")
public final class JujutsuEntityAttackTool extends BaseTool {

	private static final JsonNode SCHEMA = Schemas.object()
			.required("attacker_uuid", Schemas.string("Attacking entity UUID (must be living)"))
			.required("target_uuid", Schemas.string("Target entity UUID (must be living)"))
			.optional("amount", Schemas.number("Damage amount; default = attacker's attack_damage attribute, else 1.0"))
			.build();

	public JujutsuEntityAttackTool() {
		super("jujutsu_entity_attack");
	}

	@Override
	public JsonNode inputSchema() {
		return SCHEMA;
	}

	@Override
	public ToolResult execute(JsonNode arguments, ToolContext context) {
		ArgumentReader r = reader(arguments);
		UUID attackerUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("attacker_uuid"));
		UUID targetUuid = JujutsuMcpdevPlayers.parseUuid(r.requireString("target_uuid"));
		boolean amountGiven = r.has("amount");
		float amount = amountGiven ? (float) r.requireDouble("amount") : -1f;
		if (amountGiven && amount < 0f) {
			throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "amount must be >= 0, got " + amount);
		}
		MinecraftServer server = JujutsuMcpdevPlayers.requireServer();
		return onMainThread(
				context,
				ignored -> {
					Entity attackerEntity = JujutsuMcpdevEntities.require(server, attackerUuid);
					if (!(attackerEntity instanceof LivingEntity attacker)) {
						throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
								"Attacker is not living: " + attackerUuid);
					}
					Entity targetEntity = JujutsuMcpdevEntities.require(server, targetUuid);
					if (!(targetEntity instanceof LivingEntity target)) {
						throw new McpException(ErrorCodes.TOOL_HANDLER_ERROR,
								"Target is not living: " + targetUuid);
					}
					ServerLevel level = (ServerLevel) target.level();
					DamageSource source = attacker instanceof ServerPlayer player
							? level.damageSources().playerAttack(player)
							: level.damageSources().mobAttack(attacker);
					float dealt = amountGiven ? amount : defaultDamage(attacker);
					boolean hurt = target.hurtServer(level, source, dealt);
					ObjectNode node = context.mapper().createObjectNode();
					node.put("hurt", hurt);
					node.put("amount", dealt);
					node.put("target_uuid", targetUuid.toString());
					node.put("target_health", target.getHealth());
					node.put("target_alive", target.isAlive());
					return ToolResult.ofToon(node);
				});
	}

	private static float defaultDamage(LivingEntity attacker) {
		var attribute = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
		return attribute == null ? 1.0f : (float) attribute.getValue();
	}
}
