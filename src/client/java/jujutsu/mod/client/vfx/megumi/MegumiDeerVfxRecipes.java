package jujutsu.mod.client.vfx.megumi;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.character.megumi.MegumiAnimationHooks;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.vfx.MegumiVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** Pale, restrained positive-energy recipes for Round Deer's summon, healing pulse and cleanse. */
public final class MegumiDeerVfxRecipes {
	private static final int SUMMON_DURATION_TICKS = 16;
	private static final int PULSE_DURATION_TICKS = 10;
	private static final int CLEANSE_DURATION_TICKS = 8;
	private static final DustParticleOptions POSITIVE_LIGHT = new DustParticleOptions(0xF1F2DF, 0.78f);
	private static final DustParticleOptions POSITIVE_MINT = new DustParticleOptions(0xC5E9D9, 0.62f);

	private MegumiDeerVfxRecipes() {}

	public static void register() {
		VfxDirector.register(MegumiVfxIds.DEER_SUMMON, MegumiDeerVfxRecipes::summon);
		VfxDirector.register(MegumiVfxIds.DEER_PULSE, MegumiDeerVfxRecipes::pulse);
		VfxDirector.register(MegumiVfxIds.DEER_CLEANSE, MegumiDeerVfxRecipes::cleanse);
	}

	private static VfxInstance summon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x44454501L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN,
					SUMMON_DURATION_TICKS);
			context.ring(POSITIVE_LIGHT, origin.add(0.0, 0.08, 0.0), 12, 0.9, 0.0, 0.08, random);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.08, 0.0),
					6, 0.24, 0.08, random);

			ClientLevel level = context.client().level;
			Entity anchor = level == null ? null : level.getEntity(cue.anchorEntityId());
			if (anchor instanceof AbstractClientPlayer) {
				MegumiAnimationHooks.triggerDeer(cue);
				if (anchor == context.client().player) {
					context.firstPerson().triggerSign(0.0f);
				}
			}
		});
	}

	private static VfxInstance pulse(VfxCue cue) {
		return VfxInstance.of(PULSE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue).add(0.0, 0.45, 0.0);
			RandomSource random = random(cue, 0x44454502L);
			context.ring(POSITIVE_LIGHT, origin, 12, 0.72, 0.0, 0.04, random);
			context.burst(ParticleTypes.END_ROD, origin, 4, 0.16, 0.04, random);
		});
	}

	private static VfxInstance cleanse(VfxCue cue) {
		return VfxInstance.of(CLEANSE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue).add(0.0, 0.5, 0.0);
			RandomSource random = random(cue, 0x44454503L);
			context.ring(POSITIVE_MINT, origin, 9, 0.48, 0.03, 0.02, random);
			context.burst(ParticleTypes.END_ROD, origin, 5, 0.14, 0.03, random);
		});
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
