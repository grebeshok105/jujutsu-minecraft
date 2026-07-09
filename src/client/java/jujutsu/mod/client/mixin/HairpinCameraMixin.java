package jujutsu.mod.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.vfx.VfxDirector;

@Mixin(Camera.class)
public abstract class HairpinCameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract float getXRot();

	@Shadow
	public abstract float getYRot();

	@Inject(method = "setup", at = @At("TAIL"))
	private void jujutsumod$applyHairpinImpulse(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
		float yaw = VfxDirector.yawOffset();
		float pitch = VfxDirector.pitchOffset();
		if (Math.abs(yaw) > 0.001f || Math.abs(pitch) > 0.001f) {
			setRotation(getYRot() + yaw, getXRot() + pitch);
		}
	}
}
