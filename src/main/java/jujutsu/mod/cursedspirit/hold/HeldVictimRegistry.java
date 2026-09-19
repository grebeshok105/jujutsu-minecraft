package jujutsu.mod.cursedspirit.hold;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative holder/victim pairs used by the shared hold primitive and push gate.
 *
 * <p>The map is keyed by victim UUID so a victim can have at most one holder. The registry is
 * deliberately separate from the {@code GRIPPED} effect: the effect is the client-facing marker,
 * while this pair table is the narrow physics gate and lets release clean up the exact relationship.
 */
public final class HeldVictimRegistry {
	private static final Map<UUID, UUID> HOLDERS = new ConcurrentHashMap<>();

	private HeldVictimRegistry() {
	}

	/** Records a pair when the victim has no different holder already registered. */
	public static boolean hold(LivingEntity holder, LivingEntity victim) {
		if (holder == null || victim == null || holder == victim) {
			return false;
		}
		UUID previous = HOLDERS.putIfAbsent(victim.getUUID(), holder.getUUID());
		return previous == null || previous.equals(holder.getUUID());
	}

	/** Drops the pair for {@code victim}; idempotent for all release paths. */
	public static void release(UUID victimUuid) {
		if (victimUuid != null) {
			HOLDERS.remove(victimUuid);
		}
	}

	public static void release(LivingEntity victim) {
		if (victim != null) {
			release(victim.getUUID());
		}
	}

	/** True only for the held side of a pair, never for the holder alone. */
	public static boolean isHeld(Entity entity) {
		return entity != null && HOLDERS.containsKey(entity.getUUID());
	}

	public static boolean isHeldBy(LivingEntity holder, LivingEntity victim) {
		return holder != null && victim != null
				&& holder.getUUID().equals(HOLDERS.get(victim.getUUID()));
	}

	public static UUID holderUuid(UUID victimUuid) {
		return victimUuid == null ? null : HOLDERS.get(victimUuid);
	}

	/** Clears JVM state when a server stops; no pair may leak into the next world. */
	public static void clear() {
		HOLDERS.clear();
	}
}
