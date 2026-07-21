package jujutsu.mod.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.gui.NeonDashboardScreen;

@Mixin(Gui.class)
public abstract class NeonDashboardCrosshairMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void jujutsumod$hideCrosshairWhileDashboardOpen(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof NeonDashboardScreen) {
            ci.cancel();
        }
    }
}
