package jujutsu.mod.client.fx;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.network.HairpinNailFlightPayload;
import jujutsu.mod.network.PreparedNailsPayload;

public final class NobaraNailFlightManager {
	private static final int FLIGHT_TICKS = 3;
	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final List<Prepared> PREPARED = new ArrayList<>();

	private NobaraNailFlightManager() {}

	public static void registerClientTick() {
		ClientTickEvents.END_CLIENT_TICK.register(NobaraNailFlightManager::tick);
	}

	public static void startFlight(HairpinNailFlightPayload payload) {
		Flight flight = new Flight(payload);
		PREPARED.removeIf(prepared -> prepared.payload().playerEntityId() == payload.ownerEntityId());
		FLIGHTS.add(flight);
	}

	public static void showPrepared(PreparedNailsPayload payload) {
		PREPARED.add(new Prepared(payload));
	}

	public static List<Flight> activeFlights() {
		return List.copyOf(FLIGHTS);
	}

	public static List<Prepared> activePrepared() {
		return List.copyOf(PREPARED);
	}

	private static void tick(Minecraft client) {
		if (client.level == null || client.player == null) {
			FLIGHTS.clear();
			PREPARED.clear();
			return;
		}
		long gameTime = client.level.getGameTime();
		FLIGHTS.removeIf(flight -> gameTime - flight.payload().startGameTime() > FLIGHT_TICKS + 8L);
	}

	public record Flight(HairpinNailFlightPayload payload) {
		public Vec3 target() {
			return new Vec3(payload.targetX(), payload.targetY(), payload.targetZ());
		}

		public Vec3 target(ClientLevel level) {
			if (payload.targetEntityId() < 0) {
				return target();
			}
			Entity entity = level.getEntity(payload.targetEntityId());
			return entity == null ? target() : entity.getBoundingBox().getCenter();
		}

		public List<Vec3> nails() {
			List<Vec3> nails = List.of(
					new Vec3(payload.nail0X(), payload.nail0Y(), payload.nail0Z()),
					new Vec3(payload.nail1X(), payload.nail1Y(), payload.nail1Z()),
					new Vec3(payload.nail2X(), payload.nail2Y(), payload.nail2Z()),
					new Vec3(payload.nail3X(), payload.nail3Y(), payload.nail3Z())
			);
			return nails.subList(0, Math.max(0, Math.min(payload.nailCount(), nails.size())));
		}

		public float progress(long gameTime, float partialTick) {
			float elapsed = Math.max(0.0f, gameTime - payload.startGameTime() + partialTick);
			float raw = Math.min(1.0f, elapsed / FLIGHT_TICKS);
			return raw * raw * (3.0f - 2.0f * raw);
		}
	}

	public record Prepared(PreparedNailsPayload payload) {
		public List<Vec3> nails() {
			return payload.nails();
		}

		public Vec3 direction() {
			return payload.direction();
		}

		public float fade(long gameTime, float partialTick) {
			return 1.0f;
		}
	}
}
