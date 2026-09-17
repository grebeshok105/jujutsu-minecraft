package jujutsu.mod.character.megumi;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-owner state for partial manifestation (issue #108): at most one of Nue's wings or Toad's
 * tongue is materialized at a time, keyed by owner UUID like every other Megumi runtime.
 *
 * <p>This is the contract skeleton other blocks compile against; the state machine itself lands
 * with the partial-core block. Until then every query reports "no partial active" and every
 * command refuses, which is the correct behaviour while the feature is unimplemented.
 */
public final class MegumiPartialRuntime {
	private MegumiPartialRuntime() {}

	/** Read-only snapshot of one owner's active partial, for the dev control surface. */
	public record PartialView(String kind, long startedGameTime) {}

	/** True while any partial manifestation is active for this owner. */
	public static boolean isAnyActive(UUID ownerId) {
		return false;
	}

	/** True while the partial of exactly this shikigami type is active (spec §19 exclusion). */
	public static boolean isActiveForType(UUID ownerId, MegumiShikigami type) {
		return false;
	}

	/** Live snapshot of the owner's partial state, if one exists. */
	public static Optional<PartialView> partialView(UUID ownerId) {
		return Optional.empty();
	}

	/** Press edge of the shared partial key: toggles wings on Nue, starts the tongue on Toad. */
	public static boolean tryPartial(ServerPlayer player, boolean notify) {
		return false;
	}

	/** Release edge of the shared partial key: detaches the tongue. Never carries a cooldown. */
	public static boolean tryPartialRelease(ServerPlayer player) {
		return false;
	}

	/** Ends any active partial for this owner. Registered against the same triggers as the packs. */
	public static void teardown(MinecraftServer server, UUID ownerId) {}

	public static void register() {}
}
