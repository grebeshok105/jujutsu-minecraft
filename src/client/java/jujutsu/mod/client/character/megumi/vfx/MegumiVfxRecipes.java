package jujutsu.mod.client.character.megumi.vfx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.megumi.vfx.MegumiVfxIds;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** Divine Dog effects composed entirely from the existing VFX Core particle channel. */
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
		return VfxInstance.of(10, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0xD0655A11L);
			context.burst(SHADOW_DARK, origin.add(0.0, 0.15, 0.0), 20, 0.65, 0.20, random);
			context.ring(SHADOW_TEAL, origin.add(0.0, 0.08, 0.0), 18, 1.0, 0.0, -0.08, random);
		});
	}

	private static VfxInstance recall(VfxCue cue) {
		return VfxInstance.of(12, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0xD0652ECA11L);
			context.burst(ParticleTypes.FALLING_OBSIDIAN_TEAR, origin.add(0.0, 1.25, 0.0),
					22, 0.7, 0.025, random);
			context.ring(SHADOW_DARK, origin.add(0.0, 0.12, 0.0), 16, 1.0, 0.0, -0.10, random);
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
