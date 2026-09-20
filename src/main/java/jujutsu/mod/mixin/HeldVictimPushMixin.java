package jujutsu.mod.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;

/**
 * Stops vanilla pair physics from fighting the authoritative hold pin.
 *
 * <p>This is intentionally separate from {@link CursedSpiritPushMixin}: that mixin gates curse
 * perception pairs, while this one suppresses only pushes involving a registered victim. Held
 * victims retain ordinary block collision and are never switched to noclip.
 */
@Mixin(Entity.class)
public abstract class HeldVictimPushMixin {
	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$suppressHeldVictimPush(Entity other, CallbackInfo ci) {
		Entity self = (Entity) (Object) this;
		if (HeldVictimRegistry.isHeld(self) || HeldVictimRegistry.isHeld(other)) {
			ci.cancel();
		}
	}
}
