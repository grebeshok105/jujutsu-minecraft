package jujutsu.mod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.render.nobara.NobaraPlayerGeoRenderer;
import jujutsu.mod.client.render.todo.TodoPlayerGeoRenderer;

/**
 * Chooses which GeckoLib replaced-player renderer draws a player, for every vessel.
 * Not Nobara-specific: this is the single dispatch point for the whole roster.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class CharacterRenderDispatchMixin {
	@Unique
	private NobaraPlayerGeoRenderer<?> jujutsumod$nobaraRenderer;
	@Unique
	private TodoPlayerGeoRenderer<?> jujutsumod$todoRenderer;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void jujutsumod$createCharacterGeoRenderers(EntityRendererProvider.Context context, EntityModel<?> model, float shadowRadius, CallbackInfo ci) {
		if ((Object) this instanceof PlayerRenderer) {
			jujutsumod$nobaraRenderer = new NobaraPlayerGeoRenderer<>(context);
			jujutsumod$todoRenderer = new TodoPlayerGeoRenderer<>(context);
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$renderCharacterGeo(LivingEntityRenderState state, PoseStack matrices, MultiBufferSource consumers, int packedLight, CallbackInfo ci) {
		if (!(state instanceof PlayerRenderState playerState) || playerState.isSpectator) {
			return;
		}
		ClientCharacterSelectionManager.Selection selection = ClientCharacterSelectionManager.selectionByEntityId(playerState.id);
		if (selection == null) {
			return;
		}
		ClientCharacterSelectionManager.RenderContext renderContext = ClientCharacterSelectionManager.renderContextByEntityId(playerState.id);
		AbstractClientPlayer player = renderContext == null ? null : renderContext.player();
		if (player == null) {
			return;
		}
		if (selection.character() == JujutsuCharacter.NOBARA && jujutsumod$nobaraRenderer != null
				&& jujutsumod$nobaraRenderer.renderNobara(player, playerState, renderContext.partialTick(), matrices, consumers, packedLight)) {
			ci.cancel();
			return;
		}
		if (selection.character() == JujutsuCharacter.TODO && jujutsumod$todoRenderer != null
				&& jujutsumod$todoRenderer.renderTodo(player, playerState, renderContext.partialTick(), matrices, consumers, packedLight)) {
			ci.cancel();
		}
	}
}
