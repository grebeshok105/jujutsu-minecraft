package jujutsu.mcpdev;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * In-memory ring buffer of server-side combat events, consumed by
 * {@link JujutsuCombatLogTool} (the observability gap from the live test run:
 * nothing downstream could prove who hit whom, with what source, or who died).
 *
 * <p>Events are recorded from Fabric's {@link ServerLivingEntityEvents}:
 * {@code AFTER_DAMAGE} for every damage instance that actually landed (so the
 * amount is post-mitigation, not the attacker's intent) and {@code AFTER_DEATH}
 * for deaths. Both callbacks run on the server main thread; the buffer is
 * synchronized only because {@link #snapshot} is read from the MCP HTTP thread.
 *
 * <p>The buffer is cleared on server stop via {@link #clear()} (wired in
 * {@link JujutsuMcpdevBridge}) — a fresh world must never see stale events from
 * the previous session.
 */
final class JujutsuCombatLog {

	/** Ring capacity — covers a long scripted fight without unbounded growth. */
	static final int CAPACITY = 512;

	/** One recorded combat event. All fields are captured at event time. */
	record Entry(
			long tick,
			String kind,
			String targetType,
			UUID targetUuid,
			String attackerType,
			UUID attackerUuid,
			float amount,
			boolean blocked) {}

	private static final Object LOCK = new Object();
	private static final ArrayDeque<Entry> BUFFER = new ArrayDeque<>(CAPACITY);

	private JujutsuCombatLog() {}

	/** Registers the Fabric event hooks; called once from the mod initializer. */
	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(
				(entity, source, baseDamageTaken, damageTaken, blocked) ->
						record("damage", entity, source, damageTaken, blocked));
		ServerLivingEntityEvents.AFTER_DEATH.register(
				(entity, source) -> record("death", entity, source, 0f, false));
	}

	/** Drops every recorded event — called on SERVER_STOPPED. */
	static void clear() {
		synchronized (LOCK) {
			BUFFER.clear();
		}
	}

	private static void record(String kind, LivingEntity target, DamageSource source, float amount, boolean blocked) {
		MinecraftServer server = JujutsuMcpdevBridge.server();
		long tick = server == null ? -1L : server.getTickCount();
		Entity attacker = resolveAttacker(source);
		Entry entry = new Entry(
				tick,
				kind,
				entityTypeId(target),
				target.getUUID(),
				attacker == null ? null : entityTypeId(attacker),
				attacker == null ? null : attacker.getUUID(),
				amount,
				blocked);
		synchronized (LOCK) {
			if (BUFFER.size() >= CAPACITY) {
				BUFFER.pollFirst();
			}
			BUFFER.addLast(entry);
		}
	}

	/**
	 * Resolves the credited attacker: {@code source.getEntity()} for direct hits;
	 * for projectiles the projectile's owner is the real attacker (a spirit's
	 * spat glob must blame the spirit, not the glob entity).
	 */
	private static Entity resolveAttacker(DamageSource source) {
		if (source == null) {
			return null;
		}
		Entity attacker = source.getEntity();
		if (attacker instanceof Projectile projectile && projectile.getOwner() != null) {
			return projectile.getOwner();
		}
		return attacker;
	}

	private static String entityTypeId(Entity entity) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
	}

	/**
	 * A point-in-time copy of the buffer for the HTTP-side drain/filter.
	 * Returned oldest-first.
	 */
	static List<Entry> snapshot() {
		synchronized (LOCK) {
			return new ArrayList<>(BUFFER);
		}
	}
}
