package jujutsu.mod.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import jujutsu.mod.client.curse.fear.FearClientState;
import jujutsu.mod.client.curse.fear.FearInputMap;

/**
 * Fear mouse inversion (Block 3, Step 7, D8): one canonical point for the mouse.
 *
 * <p>Probe-verified on 1.21.8 (javap over the named merged jar): {@code onPress} routes
 * to {@code Screen.mouseClicked/mouseReleased} and {@code onScroll} to
 * {@code Screen.mouseScrolled} when a screen is open, so argument-level inversion covers
 * GUI buttons and wheel automatically. Look inverts in {@code turnPlayer} — the
 * screen-open path ({@code mouseMoved/mouseDragged}) is deliberately untouched, because
 * mirroring the cursor would break hit-testing instead of reading as fear. Look inverts
 * at the {@code LocalPlayer.turn(DD)V} call inside {@code turnPlayer}: javap-verified on
 * the mapped 1.21.8 jar, the {@code (D)V} argument is the frame time-delta consumed only
 * by {@code SmoothDouble.getNewDeltaValue(DD)D} (smooth camera), while the actual yaw/pitch
 * deltas are the two doubles passed to {@code player.turn(...)}. Button codes
 * are GLFW (0 = left, 1 = right, 2 = middle): only 0↔1 swap.
 */
@Mixin(MouseHandler.class)
public abstract class FearMouseMixin {
	// onPress(JIII)V — slots: 0 = this, 1-2 = window, 3 = button, 4 = action, 5 = mods.
	@ModifyVariable(method = "onPress(JIII)V", at = @At("HEAD"), argsOnly = true, index = 3)
	private int jujutsumod$swapFearButton(int button) {
		if (!FearClientState.hasFear()) {
			return button;
		}
		return switch (button) {
			case 0 -> 1;
			case 1 -> 0;
			default -> button;
		};
	}

	// onScroll(JDD)V — slots: 0 = this, 1-2 = window, 3-4 = xOffset, 5-6 = yOffset.
	@ModifyVariable(method = "onScroll(JDD)V", at = @At("HEAD"), argsOnly = true, index = 5)
	private double jujutsumod$invertFearWheel(double yOffset) {
		return FearClientState.hasFear() ? -yOffset : yOffset;
	}

	// turnPlayer(D)V — the (D)V arg is the frame time-delta used ONLY for
	// smooth-camera smoothing (javap-verified on the mapped 1.21.8 jar: it feeds
	// SmoothDouble.getNewDeltaValue(DD)D, never the player). The real look deltas are
	// locals 3/5, computed from accumulatedDX/DY in all three branches (smooth /
	// first-person-scoping / normal) and consumed by the LocalPlayer.turn(DD)V call.
	// Modifying that one invoke's args covers every branch — no fabric event exists
	// for look deltas, hence the mixin.
	@ModifyArgs(method = "turnPlayer(D)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void jujutsumod$invertFearLook(Args args) {
		boolean fear = FearClientState.hasFear();
		if (!fear) {
			return;
		}
		args.set(0, FearInputMap.lookDelta((double) args.get(0), true));
		args.set(1, FearInputMap.lookDelta((double) args.get(1), true));
	}
}
