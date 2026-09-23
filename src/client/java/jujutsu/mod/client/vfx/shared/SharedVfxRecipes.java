package jujutsu.mod.client.vfx.shared;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.render.nobara.NobaraPlayerGeoAnimatable;
import jujutsu.mod.client.vfx.VfxContext;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.SharedVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** VFX recipes for mechanics shared by more than one vessel. */
public final class SharedVfxRecipes {
	public static final double WIDE_PRESENTATION_RADIUS = 64.0;
	public static final int BLACK_FLASH_RECIPE_DURATION_TICKS = 48;
	public static final int BLACK_FLASH_WORLD_IMPACT_DURATION_TICKS = 28;

	private SharedVfxRecipes() {}

	public static void register() {
		VfxDirector.register(SharedVfxIds.BLACK_FLASH, SharedVfxRecipes::blackFlash);
	}

	/**
	 * Black Flash's world/audio presentation is shared. The only vessel-specific part is the optional
	 * body animation: the Nobara animatable is triggered when this cue has an anchored Nobara player;
	 * Todo deliberately has no Black Flash body clip and still receives the complete world presentation.
	 */
	private static VfxInstance blackFlash(VfxCue cue) {
		return VfxInstance.of(BLACK_FLASH_RECIPE_DURATION_TICKS, (context, initialAgeTicks) -> {
			Vec3 origin = context.resolveOrigin(cue);
			float proximity = context.proximity(cue, WIDE_PRESENTATION_RADIUS);
			RandomSource random = random(cue, 0xB1ACF1A5L);
			Vec3 dir = cue.direction();

			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				// The retained world flash ends earlier so the long recipe can keep camera and HUD tails alive.
				context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.BLACK_FLASH, BLACK_FLASH_WORLD_IMPACT_DURATION_TICKS);
				context.burst(ParticleTypes.FLASH, origin, 4, 0.1, 0.0, random);
				context.burst(ParticleTypes.CRIT, origin, 8, 0.4, 0.6, random);
				context.burst(JujutsuParticles.BF_IMPACT, origin, 7, 0.35, 0.12, random);
				context.burst(JujutsuParticles.BF_LIGHTNING, origin, 10, 1.1, 0.18, random);
				context.burst(JujutsuParticles.BF_SPARK, origin, 16, 0.5, 1.4, random);
				if (dir.lengthSqr() > 1e-6) {
					Vec3 sparkOrigin = origin.add(dir.scale(0.4));
					context.burst(JujutsuParticles.BF_SPARK, sparkOrigin, 8, 0.3, 1.8, random);
					context.burst(JujutsuParticles.BF_LIGHTNING, origin.add(dir.scale(0.8)), 4, 0.6, 0.3, random);
				}
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT, 1.5f * proximity, 0.62f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT_2, 1.2f * proximity, 0.78f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_SNAP, 1.0f * proximity, 1.4f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, 0.9f * proximity, 0.48f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_WHOOSH_VORTEX, 0.7f * proximity, 0.35f, origin, random);
				triggerAnchoredAction(context, cue, "black_flash");
			}

			if (proximity > 0.01f) {
				context.camera().triggerBlackFlash(8, proximity, initialAgeTicks);
				context.hud().triggerImpact(proximity, initialAgeTicks);
				context.hud().triggerFlash(250, Math.round(220 * proximity), initialAgeTicks);
				context.hud().triggerNausea(0.8f, initialAgeTicks);
				context.postProcess().triggerBlur(Math.round(300.0f * proximity), initialAgeTicks);
				if (cue.anchorEntityId() != VfxCue.NO_ANCHOR
						&& context.client().player != null
						&& context.client().player.getId() == cue.anchorEntityId()) {
					context.firstPerson().triggerSnap(initialAgeTicks);
				}
			}
		});
	}

	private static void triggerAnchoredAction(VfxContext context, VfxCue cue, String animation) {
		if (context.client().level == null || cue.anchorEntityId() == VfxCue.NO_ANCHOR) {
			return;
		}
		Entity entity = context.client().level.getEntity(cue.anchorEntityId());
		if (entity == null) {
			return;
		}
		// Keep the body-animation dispatch explicitly narrowed to the Nobara animatable. Todo has no BF
		// body clip; its shared cue intentionally stops at world, sound, camera, and post effects.
		Object animatable = NobaraPlayerGeoAnimatable.INSTANCE;
		if (animatable instanceof NobaraPlayerGeoAnimatable nobara) {
			nobara.triggerAction(entity, animation);
		}
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
