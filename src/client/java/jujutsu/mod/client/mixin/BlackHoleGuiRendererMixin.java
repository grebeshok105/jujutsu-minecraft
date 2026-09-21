package jujutsu.mod.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.blackhole.BlackHoleState;

/**
 * Warps the HUD geometrically while a black hole is active, without touching the GUI pipeline:
 * the framebuffer is copied before and after {@code GuiRenderer.render}, the pre-HUD image is
 * restored, and the post-HUD image is composited back through the warp shader with an alpha mask
 * derived from the pre/post difference — so only pixels the HUD actually drew get pulled.
 */
@Mixin(GuiRenderer.class)
public abstract class BlackHoleGuiRendererMixin {
	@Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"))
	private void jujutsumod$blackHoleHudPre(GpuBufferSlice fogBuffer, CallbackInfo ci) {
		if (VfxDirector.blackHole().hudWarpActive() > 0.0f) {
			VfxDirector.blackHole().renderer().captureHudPre(Minecraft.getInstance());
		}
	}

	@Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("TAIL"))
	private void jujutsumod$blackHoleHudComposite(GpuBufferSlice fogBuffer, CallbackInfo ci) {
		if (VfxDirector.blackHole().hudWarpActive() <= 0.0f) {
			return;
		}
		var context = VfxDirector.lastWorldContext();
		BlackHoleState hole = VfxDirector.blackHole().active();
		Minecraft client = Minecraft.getInstance();
		if (context == null || hole == null || client.level == null) {
			return;
		}
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		float age = hole.ageTicks(client.level.getGameTime(), partialTick);
		VfxDirector.blackHole().renderer().compositeHud(
				client, hole, age,
				context.projectionMatrix(), context.positionMatrix(),
				context.camera().getPosition());
	}
}
