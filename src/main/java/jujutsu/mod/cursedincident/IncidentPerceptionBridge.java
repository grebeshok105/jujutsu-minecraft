package jujutsu.mod.cursedincident;

import java.util.Objects;
import java.util.function.Predicate;

import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.cursedincident.runtime.PerceptionOverrideRuntime;

/** One replaceable perception predicate shared by server gates and the client mirror. */
public final class IncidentPerceptionBridge {
	private static final Predicate<Player> SERVER_DEFAULT = player ->
			player instanceof ServerPlayer serverPlayer && PerceptionOverrideRuntime.isOverridden(serverPlayer);
	private static volatile Predicate<Player> provider = SERVER_DEFAULT;

	private IncidentPerceptionBridge() {
	}

	public static boolean perceivesOverride(Player player) {
		return player != null && provider.test(player);
	}

	/** Installs the side-local provider; the client replaces the server default during bootstrap. */
	public static void install(Predicate<Player> predicate) {
		provider = Objects.requireNonNull(predicate, "perception predicate");
	}

	public static void reset() {
		provider = SERVER_DEFAULT;
	}

	public static Predicate<Player> currentProviderForTest() {
		return provider;
	}
}
