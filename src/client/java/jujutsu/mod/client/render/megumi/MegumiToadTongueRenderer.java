package jujutsu.mod.client.render.megumi;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.megumi.MegumiToadEntity;
import jujutsu.mod.client.tongue.TongueModel;
import jujutsu.mod.client.tongue.TongueRenderer;

/**
 * The summoned Toad's grab tongue: while the body holds a victim, the same cubic ribbon the
 * partial tongue uses runs from the toad's mouth to the victim's chest. Lives in the vessel
 * package — the shared tongue pass may not name a vessel type.
 */
public final class MegumiToadTongueRenderer {
	private static boolean registered;

	private MegumiToadTongueRenderer() {}

	/** Registers one AFTER_ENTITIES pass; {@code MegumiPartialClientInit} owns the call. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WorldRenderEvents.AFTER_ENTITIES.register(MegumiToadTongueRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		ClientLevel level = context.world();
		MultiBufferSource consumers = context.consumers();
		if (level == null || consumers == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cameraPosition = camera.getPosition();
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		VertexConsumer tongue = consumers.getBuffer(RenderType.entityTranslucent(TongueModel.TEXTURE));
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof MegumiToadEntity toad)) {
				continue;
			}
			Entity victim = level.getEntity(toad.grabbedEntityId());
			if (!(victim instanceof LivingEntity held)) {
				continue;
			}
			Vec3 toadPos = toad.getPosition(partialTick);
			float yaw = Mth.rotLerp(partialTick, toad.yRotO, toad.getYRot()) * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
			Vec3 mouth = toadPos.add(0.0, toad.getBbHeight() * 0.62, 0.0)
					.add(forward.scale(toad.getBbWidth() * 0.55)).subtract(cameraPosition);
			Vec3 tip = held.getPosition(partialTick)
					.add(0.0, held.getBbHeight() * 0.5, 0.0).subtract(cameraPosition);
			TongueRenderer.renderTongue(tongue, mouth, tip, 1.0f);
		}
	}
}
