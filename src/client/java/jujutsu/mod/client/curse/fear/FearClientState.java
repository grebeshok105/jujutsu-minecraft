package jujutsu.mod.client.curse.fear;

import net.minecraft.client.Minecraft;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Client-side fear gate (Block 3, Step 7). The server assigns the state via the
 * {@code CURSED_FEAR} marker; the client only executes the inversion while it is present.
 * One predicate for both mixins, so fear can never half-apply to one input type.
 */
public final class FearClientState {
	public static boolean hasFear() {
		var player = Minecraft.getInstance().player;
		return player != null && player.hasEffect(JujutsuEffects.CURSED_FEAR);
	}

	private static boolean lastFear;

	/**
	 * Flushes held-key state on fear gain/loss. KeyMapping state only changes on key
	 * events, so without this a release under fear clears the wrong mapping (stuck keys)
	 * and already-held keys never re-enter the remap. Called once per client tick.
	 */
	public static void pollTransition() {
		boolean now = hasFear();
		if (now != lastFear) {
			lastFear = now;
			net.minecraft.client.KeyMapping.releaseAll();
		}
	}
}
