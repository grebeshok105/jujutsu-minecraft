package jujutsu.mod.combat;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.network.BlackFlashFocusPayload;

public final class BlackFlashFocus {
	private static final String TAG = "jujutsumod.black_flash_focus";
	private BlackFlashFocus() {}
	public static boolean hasFocus(ServerPlayer player) { return player.getTags().contains(TAG); }
	public static void grant(ServerPlayer player) { player.addTag(TAG); sync(player); }
	/** Removes the focus tag from the player. Exists for the dev control surface + gametests. */
	public static void clear(ServerPlayer player) {
		player.removeTag(TAG);
	}

	public static void sync(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, BlackFlashFocusPayload.TYPE)) ServerPlayNetworking.send(player, new BlackFlashFocusPayload(hasFocus(player)));
	}
}
