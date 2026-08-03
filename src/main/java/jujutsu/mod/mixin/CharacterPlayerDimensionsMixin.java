package jujutsu.mod.mixin;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jujutsu.mod.character.CharacterSelectionView;

/** Applies the selected vessel's physical body scale to vanilla player dimensions. */
@Mixin(LivingEntity.class)
public abstract class CharacterPlayerDimensionsMixin {
	@Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
	private void jujutsumod$applyCharacterBodyScale(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		LivingEntity entity = (LivingEntity) (Object) this;
		if (entity instanceof Player player) {
			float bodyScale = CharacterSelectionView.of(player).bodyScale();
			if (bodyScale != 1.0f) {
				cir.setReturnValue(cir.getReturnValue().scale(bodyScale));
			}
		}
	}
}
