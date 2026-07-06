package jujutsu.mod.client.fx;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import jujutsu.mod.network.HairpinFxPayload;

public final class HairpinPlaybackManager {
	private static final List<HairpinPlayback> ACTIVE_PLAYBACKS = new ArrayList<>();

	private HairpinPlaybackManager() {}

	public static void registerClientTick() {
		ClientTickEvents.END_CLIENT_TICK.register(HairpinPlaybackManager::tick);
	}

	public static void start(HairpinFxPayload payload) {
		ACTIVE_PLAYBACKS.add(new HairpinPlayback(payload, System.currentTimeMillis()));
	}

	public static List<HairpinPlayback> activePlaybacks() {
		return List.copyOf(ACTIVE_PLAYBACKS);
	}

	private static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (client.level == null || client.player == null) {
			ACTIVE_PLAYBACKS.clear();
			return;
		}

		for (HairpinPlayback playback : ACTIVE_PLAYBACKS) {
			playback.tick(client, now);
		}
		ACTIVE_PLAYBACKS.removeIf(playback -> playback.isDone(now));
	}
}
