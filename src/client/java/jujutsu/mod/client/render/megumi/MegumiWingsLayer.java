package jujutsu.mod.client.render.megumi;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.Entity;
import jujutsu.mod.client.character.megumi.MegumiWingsState;

/** Attaches the real 3D Nue wing rig to both vanilla player skins. */
public final class MegumiWingsLayer extends RenderLayer<PlayerRenderState, PlayerModel> {
	private static final MegumiWingsGeoRenderer RENDERER = new MegumiWingsGeoRenderer();

	public MegumiWingsLayer(RenderLayerParent<PlayerRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
			PlayerRenderState renderState, float limbAngle, float limbDistance) {
		if (renderState.isInvisible || renderState.isSpectator) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		Entity entity = client.level.getEntity(renderState.id);
		if (!(entity instanceof AbstractClientPlayer player)) {
			return;
		}
		MegumiWingsState.Phase phase = MegumiWingsState.phaseFor(player.getUUID());
		if (phase == null) {
			return;
		}

		poseStack.pushPose();
		// The player model has already received setupAnim, so this copies crouch, swim, and
		// fall-flying body rotation before placing the rig on the back-facing (+Z) side.
		getParentModel().body.translateAndRotate(poseStack);
		// The wing rig is authored Y-up (Blockbench space) but this layer runs inside the vanilla
		// player flip (scale(-1,-1,1)): rendered as-is the rig hangs upside-down below the body
		// pivot, which is why the wings used to float at the feet. A 180° roll about Z restores
		// Y-up for the rig without flipping face winding, then the root is seated so the wing
		// pivots land on the upper back (rig pivot y=31 ≈ 1.07 blocks above the root).
		poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0f));
		poseStack.translate(0.0f, -1.2f, 0.125f);
		RENDERER.render(player, poseStack, bufferSource, packedLight,
				client.getDeltaTracker().getGameTimeDeltaPartialTick(false), phase);
		poseStack.popPose();
	}
}
