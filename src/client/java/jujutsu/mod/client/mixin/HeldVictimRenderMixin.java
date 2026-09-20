package jujutsu.mod.client.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.render.cursedspirit.CarriedVictimSmoothing;

/** Applies render-only three-tick interpolation to victims carrying the GRIPPED marker. */
@Mixin(LivingEntityRenderer.class)
public abstract class HeldVictimRenderMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("RETURN"))
	private void jujutsumod$smoothHeldVictim(LivingEntity entity, LivingEntityRenderState state,
			float partialTick, CallbackInfo ci) {
		// Only held victims (or a release still easing back) get an override — writing the
		// raw position for every living entity would kill vanilla partial-tick interpolation.
		if (!CarriedVictimSmoothing.hasVisualOverride(entity)) {
			return;
		}
		Vec3 visual = CarriedVictimSmoothing.position(entity, partialTick);
		state.x = visual.x;
		state.y = visual.y;
		state.z = visual.z;
	}
}
