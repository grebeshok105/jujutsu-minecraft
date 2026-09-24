package jujutsu.mod.client.vfx.megumi;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.megumi.MegumiSerpentEntity;
import jujutsu.mod.client.render.megumi.MegumiSerpentGeoAnimatable;
import jujutsu.mod.client.character.megumi.MegumiAnimationHooks;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** Great Serpent's shadow-pool, ambush, bind, and release effects. */
public final class MegumiSerpentVfxRecipes {
	private static final int SUMMON_DURATION_TICKS = 12;
	private static final int AMBUSH_DURATION_TICKS = 10;
	private static final int BIND_DURATION_TICKS = 8;
	private static final int RELEASE_DURATION_TICKS = 8;
	private static final DustParticleOptions SHADOW_DARK = new DustParticleOptions(0x102E2B, 0.8f);

	private MegumiSerpentVfxRecipes() {}

	public static void register() {
		VfxDirector.register(MegumiVfxIds.SERPENT_SUMMON, MegumiSerpentVfxRecipes::summon);
		VfxDirector.register(MegumiVfxIds.SERPENT_AMBUSH, MegumiSerpentVfxRecipes::ambush);
		VfxDirector.register(MegumiVfxIds.SERPENT_BIND, MegumiSerpentVfxRecipes::bind);
		VfxDirector.register(MegumiVfxIds.SERPENT_RELEASE, MegumiSerpentVfxRecipes::release);
	}

	private static VfxInstance summon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Entity anchor = context.client().level == null ? null
					: context.client().level.getEntity(cue.anchorEntityId());
			if (anchor instanceof AbstractClientPlayer) {
				MegumiAnimationHooks.triggerSerpent(cue);
				if (anchor == context.client().player) {
					context.firstPerson().triggerSign(0.0f);
				}
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x5345525001L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN,
					SUMMON_DURATION_TICKS);
			context.ring(SHADOW_DARK, origin.add(0.0, 0.05, 0.0), 16, 0.72, 0.0, 0.04, random);
			context.burst(ParticleTypes.POOF, origin.add(0.0, 0.12, 0.0), 10, 0.45, 0.12, random);
		});
	}

	private static VfxInstance ambush(VfxCue cue) {
		return VfxInstance.of(AMBUSH_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			triggerBodyAction(context.client().level == null ? null
					: context.client().level.getEntity(cue.anchorEntityId()), "ambush");
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x5345525002L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN,
					AMBUSH_DURATION_TICKS);
			context.ring(SHADOW_DARK, origin.add(0.0, 0.04, 0.0), 12, 0.54, 0.0, -0.05, random);
			context.burst(ParticleTypes.POOF, origin.add(0.0, 0.10, 0.0), 8, 0.28, 0.08, random);
		});
	}

	private static VfxInstance bind(VfxCue cue) {
		return VfxInstance.of(BIND_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			triggerBodyAction(context.client().level == null ? null
					: context.client().level.getEntity(cue.anchorEntityId()), "bind");
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x5345525003L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_TRAP_CLOSE,
					BIND_DURATION_TICKS);
			context.ring(SHADOW_DARK, origin.add(0.0, 0.35, 0.0), 10, 0.42, 0.0, 0.03, random);
			context.burst(ParticleTypes.CRIT, origin.add(0.0, 0.70, 0.0), 6, 0.18, 0.08, random);
		});
	}

	private static VfxInstance release(VfxCue cue) {
		return VfxInstance.of(RELEASE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			triggerBodyAction(context.client().level == null ? null
					: context.client().level.getEntity(cue.anchorEntityId()), "release");
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x5345525004L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_CLOSE,
					RELEASE_DURATION_TICKS);
			context.ring(SHADOW_DARK, origin.add(0.0, 0.06, 0.0), 12, 0.48, 0.0, -0.04, random);
			context.burst(ParticleTypes.POOF, origin.add(0.0, 0.18, 0.0), 8, 0.25, 0.10, random);
		});
	}

	private static void triggerBodyAction(Entity anchor, String action) {
		if (anchor instanceof MegumiSerpentEntity) {
			MegumiSerpentGeoAnimatable.INSTANCE.triggerAction(anchor, action);
		}
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
