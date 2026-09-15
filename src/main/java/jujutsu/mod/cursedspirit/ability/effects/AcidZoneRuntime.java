package jujutsu.mod.cursedspirit.ability.effects;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.combat.JujutsuDamageSources;
import jujutsu.mod.cursedspirit.perception.CursePerception;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCues;

/**
 * Server-authoritative acid zones (Block 3, Step 5) on the
 * {@code MegumiShadowTrapRuntime} pattern: a static runtime map, not entities. One cylinder
 * per landed glob, pulsing every {@link #PULSE_PERIOD_TICKS} ticks, capped per owner
 * (oldest of that owner evicted), damage stacking across overlapping zones. The creator is
 * immune; other spirits take the pulse; non-perceiving <em>players</em> take nothing —
 * and that gate is per {@code perceives}, not "all non-players", so a second spirit in
 * the pool still burns (C1 invariant).
 */
public final class AcidZoneRuntime {
	/** Pulse period, shared with the cue re-emit. */
	public static final int PULSE_PERIOD_TICKS = 10;
	/** Cap of live zones per owner; the oldest of that owner goes on overflow. */
	public static final int MAX_ZONES_PER_OWNER = 3;
	/** Vertical reach above/below the impact point. */
	private static final double VERTICAL_REACH = 2.0;

	/** One live pool. The centre never moves; only the clock does. */
	record AcidZone(UUID id, UUID ownerUuid, ResourceKey<Level> dimension, Vec3 center,
			double radius, long expiresAtGameTime, float pulseDamage) {
	}

	private static final Map<UUID, AcidZone> ZONES = new ConcurrentHashMap<>();

	private AcidZoneRuntime() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AcidZoneRuntime::tick);
		// The static pool outlives a world inside one JVM: an integrated-server stop
		// without this clear would leak phantom zones into the next world.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ZONES.clear());
	}

	/**
	 * Lands a zone. Emits the announce cue; the pulse re-emits while it lives.
	 *
	 * @return the zone id, for tests.
	 */
	public static UUID place(ServerLevel level, UUID ownerUuid, Vec3 center, double radius,
			int lifetimeTicks, float pulseDamage) {
		evictOverflow(ownerUuid);
		UUID id = UUID.randomUUID();
		ZONES.put(id, new AcidZone(id, ownerUuid, level.dimension(), center, radius,
				level.getGameTime() + Math.max(1, lifetimeTicks), pulseDamage));
		JujutsuNetworking.broadcastVfxCue(level, center, CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(CursedSpiritVfxIds.ACID_ZONE, center, 1, level.getGameTime(),
						center.hashCode()),
				CursePerception::perceives);
		return id;
	}

	private static void evictOverflow(UUID ownerUuid) {
		if (ownerUuid == null) {
			return;
		}
		long owned = ZONES.values().stream().filter(zone -> ownerUuid.equals(zone.ownerUuid())).count();
		if (owned < MAX_ZONES_PER_OWNER) {
			return;
		}
		ZONES.values().stream()
				.filter(zone -> ownerUuid.equals(zone.ownerUuid()))
				.min(java.util.Comparator.comparingLong(AcidZone::expiresAtGameTime))
				.ifPresent(oldest -> ZONES.remove(oldest.id()));
	}

	private static void tick(MinecraftServer server) {
		for (Map.Entry<UUID, AcidZone> entry : Map.copyOf(ZONES).entrySet()) {
			AcidZone zone = entry.getValue();
			ServerLevel level = server.getLevel(zone.dimension());
			long gameTime = level == null ? 0L : level.getGameTime();
			net.minecraft.core.BlockPos anchor = net.minecraft.core.BlockPos.containing(zone.center());
			if (level == null || gameTime >= zone.expiresAtGameTime()
					|| !level.getChunkSource().hasChunk(anchor.getX() >> 4, anchor.getZ() >> 4)) {
				ZONES.remove(entry.getKey());
				continue;
			}
			if ((gameTime % PULSE_PERIOD_TICKS) != 0) {
				continue;
			}
			pulse(level, zone, gameTime);
		}
	}

	private static void pulse(ServerLevel level, AcidZone zone, long gameTime) {
		AABB search = new AABB(zone.center(), zone.center()).inflate(
				zone.radius(), VERTICAL_REACH, zone.radius());
		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, search,
				LivingEntity::isAlive)) {
			if (zone.ownerUuid() != null && zone.ownerUuid().equals(body.getUUID())) {
				continue;
			}
			if (body instanceof Player && !CursePerception.perceives(body)) {
				continue;
			}
			if (!insideCylinder(zone, body.position())) {
				continue;
			}
			// A zone pulse is an area tick, not a hit: overlapping pools must stack (R53), and
			// the vanilla 20-tick hurt cooldown would swallow the sibling zone's same-tick pulse.
			// Same precedent as the elephant's presence pulse and the Black Flash damage type.
			body.invulnerableTime = 0;
			body.hurtServer(level,
					JujutsuDamageSources.cursedAcid(level, ownerEntity(level, zone)), zone.pulseDamage());
		}
		JujutsuNetworking.broadcastVfxCue(level, zone.center(),
				CursedSpiritVfxIds.VFX_DELIVERY_RADIUS,
				VfxCues.worldFixed(CursedSpiritVfxIds.ACID_ZONE, zone.center(), 1, gameTime,
						zone.center().hashCode()),
				CursePerception::perceives);
	}

	private static net.minecraft.world.entity.Entity ownerEntity(ServerLevel level, AcidZone zone) {
		if (zone.ownerUuid() == null) {
			return null;
		}
		net.minecraft.world.entity.Entity owner = level.getEntity(zone.ownerUuid());
		return owner instanceof LivingEntity ? owner : null;
	}

	static boolean insideCylinder(AcidZone zone, Vec3 feet) {
		double dx = feet.x - zone.center().x;
		double dz = feet.z - zone.center().z;
		double dy = feet.y - zone.center().y;
		return dx * dx + dz * dz <= zone.radius() * zone.radius()
				&& dy >= -1.0
				&& dy <= VERTICAL_REACH;
	}

	/** Live zone count, for GameTests. */
	public static int zoneCount() {
		return ZONES.size();
	}

	/** Live zones of one owner, for GameTests. */
	public static int zoneCountOf(UUID ownerUuid) {
		int count = 0;
		for (AcidZone zone : ZONES.values()) {
			if (ownerUuid.equals(zone.ownerUuid())) {
				count++;
			}
		}
		return count;
	}

	/** Removes only one owner's zones so concurrent GameTests cannot erase each other. */
	public static void removeZonesOfForTest(UUID ownerUuid) {
		if (ownerUuid != null) {
			ZONES.entrySet().removeIf(entry -> ownerUuid.equals(entry.getValue().ownerUuid()));
		}
	}

	/** Clears every zone. GameTests only. */
	public static void clearForTest() {
		ZONES.clear();
	}
}
