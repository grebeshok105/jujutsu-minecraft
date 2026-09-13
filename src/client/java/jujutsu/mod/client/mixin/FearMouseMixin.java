package jujutsu.mod.client.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import jujutsu.mod.client.curse.fear.FearClientState;

/**
 * Fear mouse inversion (Block 3, Step 7, D8): one canonical point for the mouse.
 *
 * <p>Probe-verified on 1.21.8 (javap over the named merged jar): {@code onPress} routes
 * to {@code Screen.mouseClicked/mouseReleased} and {@code onScroll} to
 * {@code Screen.mouseScrolled} when a screen is open, so argument-level inversion covers
 * GUI buttons and wheel automatically. Look inverts in {@code turnPlayer} — the
 * screen-open path ({@code mouseMoved/mouseDragged}) is deliberately untouched, because
 * mirroring the cursor would break hit-testing instead of reading as fear. Button codes
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

	// turnPlayer(D)V — slot 1 is the accumulated look delta.
	@ModifyVariable(method = "turnPlayer(D)V", at = @At("HEAD"), argsOnly = true, index = 1)
	private double jujutsumod$invertFearLook(double delta) {
		return FearClientState.hasFear() ? -delta : delta;
	}
}
