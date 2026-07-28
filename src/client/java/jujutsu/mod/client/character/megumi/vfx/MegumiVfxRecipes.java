package jujutsu.mod.client.character.megumi.vfx;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.megumi.vfx.MegumiVfxIds;
import jujutsu.mod.client.character.megumi.MegumiAnimationHooks;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** Divine Dog effects composed entirely from the existing VFX Core channels. */
public final class MegumiVfxRecipes {
	private static final DustParticleOptions SHADOW_TEAL = new DustParticleOptions(0x2F8F83, 1.0f);
	private static final DustParticleOptions SHADOW_DARK = new DustParticleOptions(0x102E2B, 0.75f);

	private MegumiVfxRecipes() {}

	public static void register() {
		VfxDirector.register(MegumiVfxIds.DOGS_SUMMON, MegumiVfxRecipes::summon);
		VfxDirector.register(MegumiVfxIds.DOGS_RECALL, MegumiVfxRecipes::recall);
		VfxDirector.register(MegumiVfxIds.DOGS_SIC, MegumiVfxRecipes::sic);
	}

	private static VfxInstance summon(VfxCue cue) {
		return VfxInstance.of(16, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Entity anchor = context.client().level == null ? null : context.client().level.getEntity(cue.anchorEntityId());
			if (anchor instanceof AbstractClientPlayer) {
				MegumiAnimationHooks.triggerDivineDogs(cue);
				if (anchor == context.client().player) {
					context.firstPerson().triggerSign(0.0f);
				}
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0xD0655A11L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, 16);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.10, 0.0), 14, 0.42, 0.13, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.04, 0.0), 10, 0.58, 0.0, 0.05, random);
		});
	}

	private static VfxInstance recall(VfxCue cue) {
		return VfxInstance.of(12, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0xD0652ECA11L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_CLOSE, 12);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.08, 0.0), 14, 0.68, 0.0, -0.08, random);
		});
	}

	private static VfxInstance sic(VfxCue cue) {
		return VfxInstance.of(8, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 target = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x51C7A26E7L);
			context.ring(SHADOW_TEAL, target, 14, 0.55, 0.0, -0.05, random);
			context.burst(SHADOW_DARK, target, 7, 0.18, 0.06, random);
		});
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
