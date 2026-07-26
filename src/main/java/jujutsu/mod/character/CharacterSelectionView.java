package jujutsu.mod.character;

import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Reads a player's vessel from either side.
 *
 * <p>The server owns the selection in a data attachment; the client keeps its own mirror fed by
 * {@code CharacterSelectionSyncPayload}. Shared code that runs on both sides — an item's {@code use},
 * which vanilla calls on the client and the server — cannot reach the client mirror directly, because
 * this source set must never touch a client class.
 *
 * <p>So the client hands its answer in at init. The same shape {@code ProjectJjkStrawDollItem} already
 * uses for its renderer factory. Without it a vessel check inside an item would pass on the server and
 * be skipped on the client, and the client would predict an action the server then refuses — a
 * consumed item and a played sound that both have to be taken back.
 */
public final class CharacterSelectionView {
	private static Function<UUID, JujutsuCharacter> clientLookup = playerId -> JujutsuCharacter.NONE;

	private CharacterSelectionView() {}

	/** Called once from client init. */
	public static void setClientLookup(Function<UUID, JujutsuCharacter> lookup) {
		clientLookup = lookup == null ? playerId -> JujutsuCharacter.NONE : lookup;
	}

	/**
	 * The player's vessel, authoritative on the server and mirrored on the client.
	 *
	 * <p>Never use this to decide the outcome of an action — the mirror can lag a selection change by a
	 * round trip. It is for deciding whether to <b>offer</b> an action, so both sides agree on what the
	 * player may attempt. The server still decides what actually happens.
	 */
	public static JujutsuCharacter of(Player player) {
		if (player instanceof ServerPlayer server) {
			return CharacterSelectionManager.selected(server);
		}
		return clientLookup.apply(player.getUUID());
	}
}
