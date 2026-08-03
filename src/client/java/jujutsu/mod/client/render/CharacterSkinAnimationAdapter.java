package jujutsu.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;

/** Evaluates an invisible GeckoLib rig and maps its humanoid pose to a vanilla PlayerModel. */
public class CharacterSkinAnimationAdapter<A extends GeoAnimatable>
		implements CharacterSkinAnimation, GeoRenderer<A, AbstractClientPlayer, GeoRenderState> {
	private static final String ROOT = "root";
	private static final String BODY = "body";
	private static final String HEAD = "head";
	private static final String LEFT_ARM = "leftArm";
	private static final String RIGHT_ARM = "rightArm";
	private static final String LEFT_LEG = "leftLeg";
	private static final String RIGHT_LEG = "rightLeg";
	private static final float VANILLA_X_FROM_GEO = -1.0f;
	private static final float VANILLA_Y_FROM_GEO = -1.0f;
	private static final float VANILLA_Z_FROM_GEO = 1.0f;

	private final A animatable;
	private final GeoModel<A> model;

	public CharacterSkinAnimationAdapter(A animatable, GeoModel<A> model) {
		this.animatable = animatable;
		this.model = model;
	}

	@Override
	public GeoModel<A> getGeoModel() {
		return model;
	}

	@Override
	public long getInstanceId(A animatable, AbstractClientPlayer player) {
		return player.getUUID().getLeastSignificantBits();
	}

	/** The rig exists for animation evaluation only and must never emit a GeckoLib draw pass. */
	@Override
	public net.minecraft.client.renderer.RenderType getRenderType(GeoRenderState renderState, ResourceLocation texture) {
		return null;
	}

	@Override
	public void fireCompileRenderLayersEvent() {}

	@Override
	public void fireCompileRenderStateEvent(A animatable, AbstractClientPlayer player, GeoRenderState renderState) {}

	@Override
	public boolean firePreRenderEvent(GeoRenderState renderState, PoseStack matrices, BakedGeoModel model,
			MultiBufferSource consumers) {
		return false;
	}

	@Override
	public void firePostRenderEvent(GeoRenderState renderState, PoseStack matrices, BakedGeoModel model,
			MultiBufferSource consumers) {}

	@Override
	public CharacterSkinAnimationState apply(AbstractClientPlayer player, PlayerRenderState renderState,
			PlayerModel playerModel, float partialTick, int packedLight) {
		CharacterSkinAnimationState snapshot = CharacterSkinAnimationState.capture(playerModel);
		try {
			GeoRenderState geoState = (GeoRenderState) (Object) renderState;
			geoState.addGeckolibData(DataTickets.HUMANOID_MODEL, playerModel);
			geoState.addGeckolibData(DataTickets.PACKED_LIGHT, packedLight);

			// prepareForRenderPass needs the baked rig selected before fillRenderState invokes it.
			model.getBakedModel(model.getModelResource(geoState));
			fillRenderState(animatable, player, geoState, partialTick);
			addSkinAnimationData(animatable, player, geoState);
			model.handleAnimations(createAnimationState(geoState));
			applyPose(playerModel);
			return snapshot;
		} catch (RuntimeException exception) {
			snapshot.close();
			throw exception;
		}
	}

	/** Vessel-specific render state belongs in the vessel adapter, not in shared dispatch. */
	protected void addSkinAnimationData(A animatable, AbstractClientPlayer player, GeoRenderState renderState) {}

	private void applyPose(PlayerModel playerModel) {
		applyPart(playerModel.root(), local(ROOT));
		applyPart(playerModel.body, accumulated(BODY));
		applyPart(playerModel.head, accumulated(HEAD));
		applyPart(playerModel.leftArm, accumulated(LEFT_ARM, "left_elbow", "left_hand"));
		applyPart(playerModel.rightArm, accumulated(RIGHT_ARM, "right_elbow", "right_hand"));
		applyPart(playerModel.leftLeg, accumulated(LEFT_LEG, "left_knee"));
		applyPart(playerModel.rightLeg, accumulated(RIGHT_LEG, "right_knee"));
	}

	private void applyPart(net.minecraft.client.model.geom.ModelPart part, Transform transform) {
		part.x += -transform.x;
		part.y += transform.y;
		part.z += transform.z;
		part.xRot = transform.xRot * VANILLA_X_FROM_GEO;
		part.yRot = transform.yRot * VANILLA_Y_FROM_GEO;
		part.zRot = transform.zRot * VANILLA_Z_FROM_GEO;
	}

	private Transform accumulated(String boneName, String... foldedChildren) {
		Transform transform = model.getBone(boneName).map(this::ancestorsWithoutRoot).orElse(Transform.ZERO);
		for (String childName : foldedChildren) {
			transform = transform.plus(model.getBone(childName).map(CharacterSkinAnimationAdapter::local).orElse(Transform.ZERO));
		}
		return transform;
	}

	private Transform ancestorsWithoutRoot(GeoBone bone) {
		Transform transform = Transform.ZERO;
		for (GeoBone current = bone; current != null && !ROOT.equals(current.getName()); current = current.getParent()) {
			transform = transform.plus(local(current));
		}
		return transform;
	}

	private Transform local(String boneName) {
		return model.getBone(boneName).map(CharacterSkinAnimationAdapter::local).orElse(Transform.ZERO);
	}

	private static Transform local(GeoBone bone) {
		return new Transform(bone.getPosX(), bone.getPosY(), bone.getPosZ(),
				bone.getRotX(), bone.getRotY(), bone.getRotZ());
	}

	private record Transform(float x, float y, float z, float xRot, float yRot, float zRot) {
		private static final Transform ZERO = new Transform(0, 0, 0, 0, 0, 0);

		private Transform plus(Transform other) {
			return new Transform(x + other.x, y + other.y, z + other.z,
					xRot + other.xRot, yRot + other.yRot, zRot + other.zRot);
		}
	}
}
