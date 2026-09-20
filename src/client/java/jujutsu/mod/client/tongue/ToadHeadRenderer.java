package jujutsu.mod.client.tongue;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.vfx.world.VfxWorldGeometry;

/** Renders the head and mouth bones of the imported Toad rig as a partial manifestation. */
public final class ToadHeadRenderer {
	static final ResourceLocation TEXTURE =
			JujutsuMod.id("textures/entity/megumi_toad_head.png");
	private static final float HEAD_SCALE = 0.6f;

	private ToadHeadRenderer() {}

	static void render(VertexConsumer consumer, Player owner, Vec3 cameraPosition,
			float partialTick, float alpha) {
		Vec3 ownerPosition = owner.getPosition(partialTick);
		Vec3 up = VfxWorldGeometry.UP;
		float yaw = Mth.rotLerp(partialTick, owner.yRotO, owner.getYRot()) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		if (forward.lengthSqr() < 1.0E-6) {
			forward = VfxWorldGeometry.NORTH;
		} else {
			forward = forward.normalize();
		}
		Vec3[] basis = VfxWorldGeometry.directionalBasis(forward);
		Vec3 right = basis[0];
		Vec3 headCenter = ownerPosition.add(0.0, owner.getBbHeight() + 0.38, 0.0).subtract(cameraPosition);
		int headAlpha = Math.round(255.0f * Math.max(0.0f, Math.min(1.0f, alpha)));
		float headWidth = 0.34f * HEAD_SCALE;
		float headHeight = 0.20f * HEAD_SCALE;
		float headDepth = 0.30f * HEAD_SCALE;
		TongueRenderer.drawCuboid(consumer, headCenter, forward, right, up,
				headDepth, headWidth, headHeight,
				26.0f / 64.0f, 12.0f / 64.0f, 45.0f / 64.0f, 23.0f / 64.0f, headAlpha);

		// The source rig's mouth bone projects toward the facing direction and hangs just below the head.
		Vec3 mouthCenter = headCenter.add(forward.scale(0.22f * HEAD_SCALE)).subtract(up.scale(0.15f * HEAD_SCALE));
		TongueRenderer.drawCuboid(consumer, mouthCenter, forward, right, up,
				0.20f * HEAD_SCALE, 0.34f * HEAD_SCALE, 0.13f * HEAD_SCALE,
				0.0f, 20.0f / 64.0f, 20.0f / 64.0f, 32.0f / 64.0f, headAlpha);
	}

	/** Kept explicit for resource tests and diagnostics without exposing renderer internals. */
	static int packedLight() {
		return LightTexture.FULL_BRIGHT;
	}
}
