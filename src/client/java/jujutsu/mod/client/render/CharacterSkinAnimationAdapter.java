package jujutsu.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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
	/** Waist height below the vanilla body part's neck pivot, in model units (body cube spans y 0..12). */
	private static final float BODY_WAIST_PIVOT_Y = 12.0f;

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

	/**
	 * Shared instance ID for triggering a player-vessel animation, matching the ID the skin bridge uses
	 * during rendering. All trigger calls MUST use this ID; the default GeoReplacedEntity.triggerAnim
	 * uses entity.getId() which creates a different manager than the bridge evaluates.
	 *
	 * @see #getInstanceId(GeoAnimatable, AbstractClientPlayer)
	 */
	public static long playerTriggerInstanceId(Entity player) {
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
		// Vanilla poses (crouch, swim, fly, sleep, ride, spin attack) MUST be rendered by the native
		// HumanoidModel.setupAnim — GeckoLib clips are authored for standing pose and produce broken
		// visuals when applied over a transformed hitbox. Return null to skip the bridge entirely;
		// the mixin handles the null path cleanly (no snapshot leak, vanilla skin renders natively).
		if (renderState.isCrouching || renderState.isVisuallySwimming || renderState.isFallFlying
				|| renderState.bedOrientation != null || renderState.isPassenger || renderState.isAutoSpinAttack) {
			return null;
		}

		CharacterSkinAnimationState snapshot = CharacterSkinAnimationState.capture(playerModel);
		try {
			// GeckoLib normally augments this state through its client mixin. Keep the bridge optional
			// when that integration is absent so vanilla rendering remains the fallback.
			if (!(renderState instanceof GeoRenderState geoState)) {
				snapshot.close();
				return null;
			}
			geoState.addGeckolibData(DataTickets.HUMANOID_MODEL, playerModel);
			geoState.addGeckolibData(DataTickets.PACKED_LIGHT, packedLight);
			addPlayerAnimationData(geoState, player.getDeltaMovement(), player.isSprinting());
			addSkinAnimationData(animatable, player, geoState);

			// prepareForRenderPass needs the baked rig selected before fillRenderState invokes it.
			model.getBakedModel(model.getModelResource(geoState));
			fillRenderState(animatable, player, geoState, partialTick);
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

	static void addPlayerAnimationData(GeoRenderState renderState, Vec3 velocity, boolean sprinting) {
		renderState.addGeckolibData(DataTickets.VELOCITY, velocity);
		renderState.addGeckolibData(DataTickets.SPRINTING, sprinting);
	}

	private void applyPose(PlayerModel playerModel) {
		applyPart(playerModel.root(), local(ROOT));
		Transform body = accumulated(BODY);
		Transform head = local(HEAD);
		applyPart(playerModel.body, body);
		// The head is a sibling of the body in the vanilla model: it follows the leaned torso's neck
		// point translationally, while its rotation stays look-driven (never body-accumulated).
		applyPart(playerModel.head,
				new Transform(body.x() + head.x(), body.y() + head.y(), body.z() + head.z(), head.rotation()));
		applyPart(playerModel.leftArm, accumulated(LEFT_ARM, "left_elbow", "left_hand"));
		applyPart(playerModel.rightArm, accumulated(RIGHT_ARM, "right_elbow", "right_hand"));
		applyPart(playerModel.leftLeg, accumulated(LEFT_LEG, "left_knee"));
		applyPart(playerModel.rightLeg, accumulated(RIGHT_LEG, "right_knee"));
	}

	private void applyPart(net.minecraft.client.model.geom.ModelPart part, Transform transform) {
		part.x += transform.x;
		part.y += transform.y;
		part.z += transform.z;
		Vector3f rotation = transform.vanillaEuler();
		part.xRot = rotation.x;
		part.yRot = rotation.y;
		part.zRot = rotation.z;
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
			Transform localTransform = local(current);
			if (BODY.equals(current.getName())) {
				localTransform = waistCompensated(localTransform);
			}
			transform = localTransform.plus(transform);
		}
		return transform;
	}

	/**
	 * The vanilla body part pivots at the neck while body-lean keyframes are authored around the waist.
	 * Conjugating the rotation by the waist offset keeps the hip line attached to the legs, so a
	 * forward run lean tips the shoulders instead of swinging the waist away from the hips.
	 */
	private static Transform waistCompensated(Transform transform) {
		Vector3f waist = new Vector3f(0.0f, BODY_WAIST_PIVOT_Y, 0.0f);
		Vector3f rotated = transform.rotation().transform(new Vector3f(waist));
		return new Transform(transform.x() + waist.x - rotated.x,
				transform.y() + waist.y - rotated.y,
				transform.z() + waist.z - rotated.z,
				transform.rotation());
	}

	private Transform local(String boneName) {
		return model.getBone(boneName).map(CharacterSkinAnimationAdapter::local).orElse(Transform.ZERO);
	}

	private static Transform local(GeoBone bone) {
		return Transform.fromGeo(bone.getPosX(), bone.getPosY(), bone.getPosZ(),
				bone.getRotX(), bone.getRotY(), bone.getRotZ());
	}

	static record Transform(float x, float y, float z, Quaternionf rotation) {
		private static final Transform ZERO = new Transform(0, 0, 0, new Quaternionf());

		static Transform fromGeo(float x, float y, float z, float xRot, float yRot, float zRot) {
			return new Transform(-x, y, z,
					new Quaternionf().rotationZYX(
							zRot * VANILLA_Z_FROM_GEO,
							yRot * VANILLA_Y_FROM_GEO,
							xRot * VANILLA_X_FROM_GEO));
		}

		Transform plus(Transform other) {
			Vector3f otherPosition = rotation.transform(new Vector3f(other.x, other.y, other.z))
					.add(x, y, z);
			return new Transform(otherPosition.x, otherPosition.y, otherPosition.z,
					new Quaternionf(rotation).mul(other.rotation));
		}

		Vector3f vanillaEuler() {
			return rotation.getEulerAnglesZYX(new Vector3f());
		}
	}
}
