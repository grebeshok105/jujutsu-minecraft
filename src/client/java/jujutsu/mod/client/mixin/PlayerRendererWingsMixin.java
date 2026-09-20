package jujutsu.mod.client.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.render.PlayerRenderLayerRegistry;

/** Adds every vessel-registered layer to vanilla's default and slim PlayerRenderer instances. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererWingsMixin {
	@Shadow
	protected abstract boolean addLayer(RenderLayer<PlayerRenderState, PlayerModel> layer);

	@Inject(method = "<init>", at = @At("TAIL"))
	private void jujutsumod$addWingsLayer(EntityRendererProvider.Context context, boolean slim,
			CallbackInfo ci) {
		for (RenderLayer<PlayerRenderState, PlayerModel> layer : PlayerRenderLayerRegistry.createLayers(
				(RenderLayerParent<PlayerRenderState, PlayerModel>) (Object) this)) {
			addLayer(layer);
		}
	}
}
