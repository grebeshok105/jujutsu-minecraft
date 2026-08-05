package jujutsu.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEmbedding;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.client.character.nobara.NobaraEspRanks;
import jujutsu.mod.client.character.nobara.NobaraEspState;
import jujutsu.mod.client.vfx.VfxPalette;
import jujutsu.mod.registry.JujutsuItems;

public final class ProjectJjkNailRenderer extends EntityRenderer<ProjectJjkNailEntity, ProjectJjkNailRenderer.State> {
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
	private static final Vector3f MODEL_UP = new Vector3f(0.0f, 1.0f, 0.0f);
	private static final ItemStack NAIL_STACK = new ItemStack(JujutsuItems.HAIRPIN_NAIL);
	private static final int CURSED_BLUE_R = VfxPalette.CURSED_BLUE_R;
	private static final int CURSED_BLUE_G = VfxPalette.CURSED_BLUE_G;
	private static final int CURSED_BLUE_B = VfxPalette.CURSED_BLUE_B;
	private static final int CURSED_BLUE_EDGE_R = VfxPalette.CURSED_BLUE_EDGE_R;
	private static final int CURSED_BLUE_EDGE_G = VfxPalette.CURSED_BLUE_EDGE_G;
	private static final int CURSED_BLUE_EDGE_B = VfxPalette.CURSED_BLUE_EDGE_B;
	private static final int CURSED_BLUE_DARK_R = VfxPalette.CURSED_BLUE_DARK_R;
	private static final int CURSED_BLUE_DARK_G = VfxPalette.CURSED_BLUE_DARK_G;
	private static final int CURSED_BLUE_DARK_B = VfxPalette.CURSED_BLUE_DARK_B;
	private static final int CURSED_BLUE_WHITE_R = VfxPalette.CURSED_BLUE_WHITE_R;
	private static final int CURSED_BLUE_WHITE_G = VfxPalette.CURSED_BLUE_WHITE_G;
	private static final int CURSED_BLUE_WHITE_B = VfxPalette.CURSED_BLUE_WHITE_B;
	private static final int NOBARA_ACCENT = 0xE48A36;
	private final ItemRenderer itemRenderer;

	public ProjectJjkNailRenderer(EntityRendererProvider.Context context) {
		super(context);
		itemRenderer = Minecraft.getInstance().getItemRenderer();
		shadowRadius = 0.0f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ProjectJjkNailEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.direction = safeDirection(entity.forwardDirection());
		state.launched = entity.isFlying();
		state.embedded = entity.isEmbedded();
		state.seed = entity.getId();
		state.age = entity.tickCount + partialTick;
		state.embeddedAnchorOffset = Vec3.ZERO;
		state.hasEmbeddedAnchor = false;
		state.ownedByLocal = false;
		state.isEspLeader = false;
		state.trapNail = entity.isTrapNail();
		state.isMega = entity.isMegaNail();
		state.megaRenderScale = entity.megaRenderScale();
		state.espTarget = null;
		if (state.embedded) {
			Entity host = entity.embeddedTargetEntityId() < 0 ? null : entity.level().getEntity(entity.embeddedTargetEntityId());
			if (host instanceof LivingEntity living && living.isAlive()) {
				Vec3 hostPosition = living.getPosition(partialTick);
				float bodyYaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
				Vec3 anchor = hostPosition.add(ProjectJjkNailEmbedding.worldOffset(entity.embeddedLocalOffset(), bodyYaw));
				state.embeddedAnchorOffset = anchor.subtract(state.x, state.y, state.z);
				state.hasEmbeddedAnchor = true;
				state.direction = safeDirection(ProjectJjkNailEmbedding.worldForward(entity.embeddedLocalForward(), bodyYaw));

				// ESP snapshot check for Nobara
				int targetId = entity.embeddedTargetEntityId();
				Map<Integer, NobaraEspState.TargetEsp> snapshot = NobaraEspState.snapshot();
				NobaraEspState.TargetEsp esp = snapshot.get(targetId);
				var localPlayer = Minecraft.getInstance().player;
				boolean ownNail = localPlayer != null
						&& entity.clientOwnerUuid().map(localPlayer.getUUID()::equals).orElse(false);
				if (esp != null && ownNail) {
					state.ownedByLocal = true;
					state.isEspLeader = esp.leaderNailEntityId() == entity.getId();
					if (state.isEspLeader) {
						Vec3 targetTop = hostPosition.add(0.0, living.getBbHeight() + 0.45, 0.0);
						Vec3 billboardOffset = targetTop.subtract(state.x, state.y, state.z);

						String rankKey;
						if (living instanceof Player targetPlayer) {
							UUID targetUuid = targetPlayer.getUUID();
							JujutsuCharacter targetVessel = ClientCharacterSelectionManager.characterOrNone(targetUuid);
							String vesselGradeKey = targetVessel != JujutsuCharacter.NONE
									? JujutsuCharacterClients.definition(targetVessel).rosterEntry().subtitleKey()
									: null;
							rankKey = NobaraEspRanks.rankKey(true, vesselGradeKey, living.getMaxHealth());
						} else {
							rankKey = NobaraEspRanks.rankKey(false, null, living.getMaxHealth());
						}

						state.espTarget = new State.EspTargetData(
								living.getDisplayName(),
								living.getHealth(),
								living.getMaxHealth(),
								rankKey,
								esp.nailCount(),
								esp.nailDepths(),
								billboardOffset
						);
					}
				}
			}
		}
	}

	@Override
	public void render(State state, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		Vec3 direction = safeDirection(state.direction);
		matrices.pushPose();
		if (state.embedded && state.hasEmbeddedAnchor) {
			matrices.translate(state.embeddedAnchorOffset.x, state.embeddedAnchorOffset.y, state.embeddedAnchorOffset.z);
		}
		if (state.isMega) {
			// Mega nail — giant material nail with enhanced aura.
			float megaScale = state.megaRenderScale;
			// Light scale pulse.
			megaScale *= 1.0f + 0.03f * (float) Math.sin(state.age * 0.12f + state.seed * 0.37f);
			renderCompressedEnergyAura(consumers.getBuffer(RenderType.lightning()), matrices, Vec3.ZERO, direction,
					state.age + state.seed * 0.37f, 1.0f, 2.2f, 0.24f, 6, state.launched);
			matrices.mulPose(new Quaternionf().rotationTo(MODEL_UP, toVector3f(direction)));
			matrices.pushPose();
			matrices.mulPose(new Quaternionf().rotateY((float) ((state.seed & 3) * Math.PI * 0.5)));
			matrices.scale(megaScale, megaScale, megaScale);
			itemRenderer.renderStatic(NAIL_STACK, ItemDisplayContext.FIXED, packedLight,
					OverlayTexture.NO_OVERLAY, matrices, consumers, Minecraft.getInstance().level, state.seed);
			matrices.popPose();
			matrices.popPose();
			super.render(state, matrices, consumers, packedLight);
			return;
		}
		if (!state.embedded) {
			float alpha = state.launched ? 0.96f : 0.68f;
			float length = state.launched ? 1.28f : 0.76f;
			float width = state.launched ? 0.115f : 0.078f;
			int bands = state.launched ? 3 : 2;
			renderCompressedEnergyAura(consumers.getBuffer(RenderType.lightning()), matrices, Vec3.ZERO, direction,
					state.age + state.seed * 0.37f, alpha, length, width, bands, state.launched);
		} else if (state.ownedByLocal) {
			renderEmbeddedMarkPulse(consumers.getBuffer(RenderType.lightning()), matrices, Vec3.ZERO, direction,
					state.age + state.seed * 0.37f, NOBARA_ACCENT);
		} else {
			renderEmbeddedMarkPulse(consumers.getBuffer(RenderType.lightning()), matrices, Vec3.ZERO, direction,
					state.age + state.seed * 0.37f);
		}
		// Trap nail persistent visual — vertical pillar + ground ring
		if (state.trapNail) {
			renderTrapNailPillar(consumers.getBuffer(RenderType.lightning()), matrices,
					state.age + state.seed * 0.37f);
		}
		matrices.mulPose(new Quaternionf().rotationTo(MODEL_UP, toVector3f(direction)));
		if (state.embedded) {
			matrices.translate(0.0f, -0.18f, 0.0f);
		}
		matrices.pushPose();
		float scale = state.embedded ? 0.58f : state.launched ? 0.7f : 0.62f;
		matrices.mulPose(new Quaternionf().rotateY((float) ((state.seed & 3) * Math.PI * 0.5)));
		matrices.scale(scale, scale, scale);
		itemRenderer.renderStatic(
				NAIL_STACK,
				ItemDisplayContext.FIXED,
				packedLight,
				OverlayTexture.NO_OVERLAY,
				matrices,
				consumers,
				Minecraft.getInstance().level,
				state.seed
		);
		matrices.popPose();
		matrices.popPose();
		if (state.isEspLeader && state.espTarget != null) {
			renderEspBillboard(state.espTarget, matrices, consumers, packedLight);
		}
		super.render(state, matrices, consumers, packedLight);
	}

	private static void renderEmbeddedMarkPulse(VertexConsumer consumer, PoseStack matrices, Vec3 center, Vec3 direction, float age) {
		Vec3 line = safeDirection(direction);
		Vec3 side = axisSide(line, 1.0f).normalize();
		Vec3 cross = line.cross(side).normalize();
		float pulse = 0.5f + 0.5f * (float) Math.sin(age * 0.18f);
		float radius = 0.095f + pulse * 0.018f;
		int alpha = Math.round(34.0f + pulse * 24.0f);
		renderPressureBand(consumer, matrices, center.subtract(line.scale(0.08)), side, cross, radius, alpha);
	}

	private static void renderEmbeddedMarkPulse(VertexConsumer consumer, PoseStack matrices, Vec3 center, Vec3 direction, float age, int accentRgb) {
		Vec3 line = safeDirection(direction);
		Vec3 side = axisSide(line, 1.0f).normalize();
		Vec3 cross = line.cross(side).normalize();
		float pulse = 0.5f + 0.5f * (float) Math.sin(age * 0.18f);
		float radius = 0.095f + pulse * 0.018f;
		int alpha = Math.round(34.0f + pulse * 24.0f);
		int r = (accentRgb >> 16) & 0xFF;
		int g = (accentRgb >> 8) & 0xFF;
		int b = accentRgb & 0xFF;
		renderPressureBand(consumer, matrices, center.subtract(line.scale(0.08)), side, cross, radius, alpha, r, g, b);
	}

	private static void renderCompressedEnergyAura(VertexConsumer consumer, PoseStack matrices, Vec3 center, Vec3 direction,
			float age, float alpha, float length, float width, int bands, boolean launched) {
		if (alpha <= 0.01f) {
			return;
		}
		Vec3 line = safeDirection(direction);
		Vec3 tail = center.subtract(line.scale(launched ? length * 0.72f : length * 0.46f));
		Vec3 head = center.add(line.scale(launched ? length * 0.28f : length * 0.54f));
		Vec3 side = axisSide(line, width);
		Vec3 cross = line.cross(side);
		if (cross.lengthSqr() < 1.0E-5) {
			cross = axisSide(line.cross(EAST), width * 0.8f);
		} else {
			cross = cross.normalize().scale(width * 0.78f);
		}

		float pulse = 0.9f + 0.1f * (float) Math.sin(age * 0.38f);
		addRibbon(consumer, matrices, tail, head, side.scale(1.65 * pulse), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(88.0f * alpha));
		addRibbon(consumer, matrices, tail, head, cross.scale(1.38 * pulse), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(64.0f * alpha));
		addRibbon(consumer, matrices, tail.add(line.scale(length * 0.08f)), head, side.scale(0.68), CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, Math.round(178.0f * alpha));
		addRibbon(consumer, matrices, center.subtract(line.scale(length * 0.06f)), head.add(line.scale(length * 0.1f)), cross.scale(0.26), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(118.0f * alpha));

		Vec3 tipStart = head.subtract(line.scale(0.18f));
		Vec3 tipEnd = head.add(line.scale(launched ? 0.17f : 0.08f));
		addRibbon(consumer, matrices, tipStart, tipEnd, side.scale(0.46), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(205.0f * alpha));
		for (int index = 0; index < bands; index++) {
			double offset = (index + 0.5) / bands;
			double wave = Math.sin(age * 0.31f + index * 1.7) * 0.5 + 0.5;
			Vec3 ringCenter = tail.lerp(head, offset);
			float ringRadius = width * (0.82f + (float) wave * 0.16f);
			renderPressureBand(consumer, matrices, ringCenter, side.normalize(), cross.normalize(), ringRadius, Math.round(78.0f * alpha));
		}
		int slivers = launched ? 3 : 2;
		for (int index = 0; index < slivers; index++) {
			double angle = age * 0.18 + index * Math.PI * 2.0 / slivers;
			Vec3 orbit = side.normalize().scale(Math.cos(angle) * width * 1.45)
					.add(cross.normalize().scale(Math.sin(angle) * width * 1.45));
			Vec3 sliverCenter = tail.lerp(head, 0.28 + index * 0.22).add(orbit);
			Vec3 sliverStart = sliverCenter.subtract(line.scale(launched ? 0.16f : 0.08f));
			Vec3 sliverEnd = sliverCenter.add(line.scale(launched ? 0.13f : 0.07f));
			addRibbon(consumer, matrices, sliverStart, sliverEnd, side.normalize().scale(0.012f),
					CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(132.0f * alpha));
		}
	}

	private static void renderPressureBand(VertexConsumer consumer, PoseStack matrices, Vec3 center, Vec3 side, Vec3 cross, float radius, int alpha) {
		if (alpha <= 0) {
			return;
		}
		int segments = 10;
		for (int segment = 0; segment < segments; segment++) {
			double a0 = segment * Math.PI * 2.0 / segments;
			double a1 = (segment + 0.65) * Math.PI * 2.0 / segments;
			Vec3 start = center.add(side.scale(Math.cos(a0) * radius)).add(cross.scale(Math.sin(a0) * radius));
			Vec3 end = center.add(side.scale(Math.cos(a1) * radius)).add(cross.scale(Math.sin(a1) * radius));
			Vec3 thickness = sideVector(end.subtract(start), 0.012f);
			addRibbon(consumer, matrices, start, end, thickness.scale(2.0), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, alpha / 2);
			addRibbon(consumer, matrices, start, end, thickness, CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, alpha);
		}
	}

	private static void renderPressureBand(VertexConsumer consumer, PoseStack matrices, Vec3 center, Vec3 side, Vec3 cross, float radius, int alpha, int r, int g, int b) {
		if (alpha <= 0) {
			return;
		}
		int segments = 10;
		for (int segment = 0; segment < segments; segment++) {
			double a0 = segment * Math.PI * 2.0 / segments;
			double a1 = (segment + 0.65) * Math.PI * 2.0 / segments;
			Vec3 start = center.add(side.scale(Math.cos(a0) * radius)).add(cross.scale(Math.sin(a0) * radius));
			Vec3 end = center.add(side.scale(Math.cos(a1) * radius)).add(cross.scale(Math.sin(a1) * radius));
			Vec3 thickness = sideVector(end.subtract(start), 0.012f);
			addRibbon(consumer, matrices, start, end, thickness.scale(2.0), r / 2, g / 2, b / 2, alpha / 2);
			addRibbon(consumer, matrices, start, end, thickness, r, g, b, alpha);
		}
	}

	private static void renderTrapNailPillar(VertexConsumer consumer, PoseStack matrices, float age) {
		// Ground ring — slow breathing pulse, cursed blue palette
		float breathe = 0.6f + 0.4f * (float) Math.sin(age * 0.035f);
		float ringRadius = 0.42f * breathe;
		Vec3 side = EAST;
		Vec3 cross = UP.cross(side).normalize();

		// Outer ground ring
		renderPressureBand(consumer, matrices, Vec3.ZERO, side, cross, ringRadius, Math.round(48.0f * breathe));
		// Inner ground ring (brighter)
		renderPressureBand(consumer, matrices, Vec3.ZERO, side, cross, ringRadius * 0.55f, Math.round(68.0f * breathe));

		// Vertical pillar — stacked rings ascending from ground
		float beamHeight = 1.8f;
		float pillarPulse = 0.7f + 0.3f * (float) Math.sin(age * 0.055f);
		int layers = 8;
		for (int i = 0; i <= layers; i++) {
			float t = (float) i / layers;
			float y = beamHeight * t;
			float layerAlpha = (1.0f - t * 0.7f) * 80.0f * pillarPulse;
			float layerRadius = 0.05f + 0.04f * (1.0f - t * 0.5f);
			renderPressureBand(consumer, matrices, new Vec3(0.0, y, 0.0), side, cross, layerRadius, Math.round(layerAlpha));
		}

		// Bright tip at top of pillar
		float tipAlpha = 100.0f * pillarPulse;
		renderPressureBand(consumer, matrices, new Vec3(0.0, beamHeight, 0.0), side, cross, 0.07f, Math.round(tipAlpha));

		// Vertical glow ribbon
		float ribbonAlpha = 28.0f * pillarPulse;
		Vec3 top = new Vec3(0.0, beamHeight * 0.9, 0.0);
		addRibbon(consumer, matrices, Vec3.ZERO, top, side.scale(0.025),
				CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, Math.round(ribbonAlpha));
		addRibbon(consumer, matrices, Vec3.ZERO, top, cross.scale(0.025),
				CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(ribbonAlpha * 0.6f));
	}

	private static void renderEspBillboard(State.EspTargetData esp, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		matrices.pushPose();
		matrices.translate(esp.billboardOffset().x, esp.billboardOffset().y, esp.billboardOffset().z);
		matrices.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
		matrices.scale(-0.025f, -0.025f, 0.025f);

		Font font = Minecraft.getInstance().font;
		float lineH = font.lineHeight + 2;

		String hpText = String.format(Locale.ROOT, " %.1f/%.1f", esp.hp(), esp.maxHp());
		String line1Str = esp.name().getString() + " \u2665" + hpText;
		Component line1 = Component.literal(line1Str);
		Component line2 = Component.translatable(esp.rankKey());

		StringBuilder pips = new StringBuilder();
		for (int d : esp.nailDepths()) {
			if (!pips.isEmpty()) pips.append(' ');
			pips.append("\u2022".repeat(d));
		}
		String line3Str = "\u00D7" + esp.nailCount() + " " + pips;
		Component line3 = Component.literal(line3Str);

		// Background travels with each line through drawInBatch, exactly like vanilla nameplates:
		// a hand-built background quad batches after the glyphs and paints over them.
		int background = 0xB8121818;
		PoseStack.Pose pose = matrices.last();
		org.joml.Matrix4f m = pose.pose();
		font.drawInBatch(line1, -font.width(line1) / 2.0f, 0.0f, 0xFFE5F1EF, false, m, consumers,
				Font.DisplayMode.NORMAL, background, packedLight);
		font.drawInBatch(line2, -font.width(line2) / 2.0f, -lineH, 0xFFB8C4C2, false, m, consumers,
				Font.DisplayMode.NORMAL, background, packedLight);
		font.drawInBatch(line3, -font.width(line3) / 2.0f, -lineH * 2, 0xFFE48A36, false, m, consumers,
				Font.DisplayMode.NORMAL, background, packedLight);

		matrices.popPose();
	}

	private static Vec3 axisSide(Vec3 direction, float width) {
		Vec3 line = safeDirection(direction);
		Vec3 side = line.cross(UP);
		if (side.lengthSqr() < 1.0E-5) {
			side = line.cross(EAST);
		}
		return side.normalize().scale(width);
	}

	private static Vec3 sideVector(Vec3 direction, float width) {
		return axisSide(direction, width);
	}

	private static void addRibbon(VertexConsumer consumer, PoseStack matrices, Vec3 start, Vec3 end, Vec3 side, int red, int green, int blue, int alpha) {
		PoseStack.Pose pose = matrices.last();
		consumer.addVertex(pose, (float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private static Vector3f toVector3f(Vec3 vector) {
		return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
	}

	public static final class State extends EntityRenderState {
		private Vec3 direction = new Vec3(0.0, 0.0, 1.0);
		private boolean launched;
		private boolean embedded;
		private int seed;
		private float age;
		private boolean hasEmbeddedAnchor;
		private Vec3 embeddedAnchorOffset = Vec3.ZERO;
		private boolean ownedByLocal;
		private boolean isEspLeader;
		private boolean trapNail;
		private boolean isMega;
		private float megaRenderScale;
		private EspTargetData espTarget;

		public record EspTargetData(
				Component name,
				float hp,
				float maxHp,
				String rankKey,
				int nailCount,
				List<Integer> nailDepths,
				Vec3 billboardOffset
		) {}
	}
}
