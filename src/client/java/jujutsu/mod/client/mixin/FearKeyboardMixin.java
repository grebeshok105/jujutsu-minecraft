package jujutsu.mod.client.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.curse.fear.FearClientState;
import jujutsu.mod.client.curse.fear.FearInputMap;

/**
 * Fear keyboard inversion (Block 3, Step 7, D8): one canonical point for the keyboard.
 *
 * <p>Probe-verified on 1.21.8 (javap over the named merged jar): {@code keyPress} ends in
 * {@code KeyMapping.set}/{@code click} when no screen is open and in
 * {@code Screen.keyPressed/keyReleased} when one is — so remapping the raw key argument
 * here inverts movement bindings, hotbar/actions <em>and</em> GUI key handling through
 * the same path, with no second inversion in {@code Screen}. Text entry is untouched: it
 * travels the separate {@code charTyped} path, which this mixin never sees.
 *
 * <p>Decision record: physical-code remap (W↔S by position, not by binding), so rebound
 * keys are out of scope by design. The per-tick transition flush below is load-bearing:
 * KeyMapping state only changes on key events, so a release under fear would clear the
 * wrong mapping (stuck keys) and held keys would never re-enter the remap.
 */
@Mixin(KeyboardHandler.class)
public abstract class FearKeyboardMixin {
	// Slots: 0 = this, 1-2 = window (long), 3 = key. argsOnly keeps the search on arguments.
	@ModifyVariable(method = "keyPress(JIIII)V", at = @At("HEAD"), argsOnly = true, index = 3)
	private int jujutsumod$remapFearKey(int key) {
		return FearClientState.hasFear() ? FearInputMap.remapKey(key) : key;
	}

	@Inject(method = "tick()V", at = @At("TAIL"))
	private void jujutsumod$flushFearTransition(CallbackInfo ci) {
		FearClientState.pollTransition();
	}
}
