package jujutsu.mod.client.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Attaches held items to a vessel's hand bones.
 * Vessels differ only in bone naming, so subclasses supply the two bone names and nothing else.
 */
public abstract class CharacterHeldItemLayer<A extends GeoAnimatable, R extends PlayerRenderState & GeoRenderState>
		extends BlockAndItemGeoLayer<A, AbstractClientPlayer, R> {
	// One shared pair of tickets: a render state belongs to exactly one player, drawn by exactly
	// one vessel renderer, so per-character ticket ids would never disambiguate anything.
	private static final DataTicket<ItemStack> RIGHT_HAND_ITEM = DataTicket.create("character_right_hand_item", ItemStack.class);
	private static final DataTicket<ItemStack> LEFT_HAND_ITEM = DataTicket.create("character_left_hand_item", ItemStack.class);

	private final String rightHandBone;
	private final String leftHandBone;

	protected CharacterHeldItemLayer(GeoRenderer<A, AbstractClientPlayer, R> renderer,
			String rightHandBone, String leftHandBone) {
		super(renderer);
		this.rightHandBone = rightHandBone;
		this.leftHandBone = leftHandBone;
	}

	@Override
	protected List<RenderData<R>> getRelevantBones(R renderState, BakedGeoModel model) {
		return List.of(
				new RenderData<>(rightHandBone, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
						(bone, state) -> Either.left(state.getOrDefaultGeckolibData(RIGHT_HAND_ITEM, ItemStack.EMPTY))),
				new RenderData<>(leftHandBone, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
						(bone, state) -> Either.left(state.getOrDefaultGeckolibData(LEFT_HAND_ITEM, ItemStack.EMPTY)))
		);
	}

	@Override
	public void addRenderData(A animatable, AbstractClientPlayer player, R renderState) {
		boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
		renderState.addGeckolibData(RIGHT_HAND_ITEM, rightHanded ? player.getMainHandItem() : player.getOffhandItem());
		renderState.addGeckolibData(LEFT_HAND_ITEM, rightHanded ? player.getOffhandItem() : player.getMainHandItem());
	}

	@Override
	protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, ItemDisplayContext displayContext,
			R renderState, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
			poseStack.mulPose(Axis.XN.rotationDegrees(90.0f));
			poseStack.translate(0.0f, 0.125f, -0.0625f);
			if (stack.getItem() instanceof ShieldItem) {
				poseStack.translate(0.0, 0.125, -0.25);
			}
		} else if (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
			poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
			poseStack.translate(0.0f, 0.125f, -0.0625f);
			if (stack.getItem() instanceof ShieldItem) {
				poseStack.translate(0.0, 0.125, 0.25);
				poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
			}
		}

		super.renderStackForBone(poseStack, bone, stack, displayContext, renderState, bufferSource, packedLight, packedOverlay);
	}
}
