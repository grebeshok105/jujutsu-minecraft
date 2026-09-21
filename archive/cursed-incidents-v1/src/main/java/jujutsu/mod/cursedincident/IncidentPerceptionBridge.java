package jujutsu.mod.cursedincident;

import java.util.Objects;
import java.util.function.Predicate;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import jujutsu.mod.cursedincident.runtime.PerceptionOverrideRuntime;

/** Server-only perception seam; client mirrors stay in the client source set. */
public final class IncidentPerceptionBridge {
	private static final Predicate<ServerPlayer> SERVER_DEFAULT = PerceptionOverrideRuntime::isOverridden;
	private static volatile Predicate<ServerPlayer> serverProvider = SERVER_DEFAULT;

	private IncidentPerceptionBridge() {
	}

	/**
	 * Evaluates the authoritative server override. A client-side {@link Player} can never
	 * reach this provider, which keeps an integrated server independent of its local mirror.
	 */
	public static boolean perceivesOverride(Player player) {
		return player instanceof ServerPlayer serverPlayer && serverProvider.test(serverPlayer);
	}

	/** Installs a server-side provider for the production seam or a focused test. */
	public static void installServer(Predicate<ServerPlayer> predicate) {
		serverProvider = Objects.requireNonNull(predicate, "server perception predicate");
	}

	public static void reset() {
		serverProvider = SERVER_DEFAULT;
	}

	public static Predicate<ServerPlayer> currentServerProviderForTest() {
		return serverProvider;
	}
}
