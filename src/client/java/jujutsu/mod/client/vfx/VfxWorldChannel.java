package jujutsu.mod.client.vfx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.world.BlackFlashWorldEffects;
import jujutsu.mod.client.vfx.world.HairpinWorldEffects;
import jujutsu.mod.client.vfx.world.ShadowWorldEffects;
import jujutsu.mod.client.vfx.world.SwapWorldEffects;
import jujutsu.mod.vfx.VfxAnchorResolver;
import jujutsu.mod.vfx.VfxCue;

public final class VfxWorldChannel {
	private static final int MAX_IMPACT_FLASHES = 48;
	private final List<ImpactFlash> impactFlashes = new ArrayList<>();

	public void triggerImpact(VfxCue cue, ImpactStyle style, int durationTicks) {
		impactFlashes.add(new ImpactFlash(cue, style, Math.max(1, durationTicks)));
		if (impactFlashes.size() > MAX_IMPACT_FLASHES) {
			impactFlashes.remove(0);
		}
	}

	void render(WorldRenderContext context) {
		MultiBufferSource consumers = context.consumers();
		if (consumers == null) {
			return;
		}
		Camera camera = context.camera();
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		VertexConsumer lightningConsumer = consumers.getBuffer(RenderType.lightning());
		renderImpactFlashes(lightningConsumer, camera.getPosition(), context, partialTick, false);
		VertexConsumer shadowPoolConsumer = consumers.getBuffer(RenderType.debugQuads());
		renderImpactFlashes(shadowPoolConsumer, camera.getPosition(), context, partialTick, true);
	}

	void clear() {
		impactFlashes.clear();
	}

	int activeEffectCount() {
		return impactFlashes.size();
	}

	private void renderImpactFlashes(
			VertexConsumer consumer, Vec3 cameraPosition, WorldRenderContext context,
			float partialTick, boolean shadowPass) {
		for (Iterator<ImpactFlash> iterator = impactFlashes.iterator(); iterator.hasNext();) {
			ImpactFlash flash = iterator.next();
			float age = context.world().getGameTime() - flash.cue().startGameTime() + partialTick;
			if (age >= flash.durationTicks()) {
				iterator.remove();
				continue;
			}
			float progress = Math.max(0.0f, Math.min(1.0f, age / flash.durationTicks()));
			float fade = 1.0f - progress;
			if (isShadowPoolStyle(flash.style()) != shadowPass) {
				continue;
			}
			Vec3 origin = flash.style().isWorldFixed()
					? flash.cue().origin()
					: VfxAnchorResolver.resolve(flash.cue(), entityId -> {
						Entity anchor = context.world().getEntity(entityId);
						return anchor == null ? null : anchor.position();
					});
			Vec3 center = origin.subtract(cameraPosition);
			int intensity = Math.max(1, flash.cue().intensity());
			switch (flash.style()) {
				case HAMMER_SEND -> HairpinWorldEffects.renderHammerSend(consumer, center, intensity, progress, fade);
				case ENLARGE -> HairpinWorldEffects.renderEnlargeImpact(consumer, center, intensity, progress, fade);
				case EXPLOSION -> HairpinWorldEffects.renderExplosionImpact(consumer, center, intensity, progress, fade);
				case RITUAL_BIND -> HairpinWorldEffects.renderRitualBind(consumer, center, intensity, progress, fade);
				case DOLL_STRIKE -> HairpinWorldEffects.renderDollStrike(consumer, center, intensity, progress, fade);
				case RESONANCE_RELEASE -> HairpinWorldEffects.renderResonanceRelease(consumer, center, intensity, progress, fade);
				case BLACK_FLASH -> BlackFlashWorldEffects.renderBlackFlash(consumer, center, intensity, progress, fade, flash.cue());
				case BOOGIE_WOOGIE -> SwapWorldEffects.renderBoogieWoogie(consumer, center, intensity, progress, fade, flash.cue());
				case SWAP_AFTERIMAGE -> SwapWorldEffects.renderSwapAfterimage(consumer, center, progress, flash.cue());
				case SWAP_ARRIVAL -> SwapWorldEffects.renderSwapArrival(consumer, center, progress, fade, flash.cue());
				case MEGUMI_SHADOW_OPEN -> ShadowWorldEffects.renderMegumiShadowPool(consumer, center, progress, true);
				case MEGUMI_SHADOW_CLOSE -> ShadowWorldEffects.renderMegumiShadowPool(consumer, center, progress, false);
			}
		}
	}

	private static boolean isShadowPoolStyle(ImpactStyle style) {
		return style == ImpactStyle.MEGUMI_SHADOW_OPEN || style == ImpactStyle.MEGUMI_SHADOW_CLOSE;
	}

	/**
	 * Whether a flash stays at the immutable cue origin is a property of the style, not a list kept
	 * somewhere else: a new style cannot be declared without answering the question, because the
	 * constructor demands it. Getting it wrong on an afterimage would make the residue follow the body
	 * that left, which is the one thing an afterimage must never do.
	 */
	public enum ImpactStyle {
		HAMMER_SEND(false),
		ENLARGE(false),
		EXPLOSION(false),
		RITUAL_BIND(false),
		DOLL_STRIKE(true),
		RESONANCE_RELEASE(true),
		BLACK_FLASH(true),
		BOOGIE_WOOGIE(true),
		SWAP_AFTERIMAGE(true),
		SWAP_ARRIVAL(true),
		MEGUMI_SHADOW_OPEN(true),
		MEGUMI_SHADOW_CLOSE(true);

		private final boolean worldFixed;

		ImpactStyle(boolean worldFixed) {
			this.worldFixed = worldFixed;
		}

		public boolean isWorldFixed() {
			return worldFixed;
		}
	}

	private record ImpactFlash(VfxCue cue, ImpactStyle style, int durationTicks) {}
}
