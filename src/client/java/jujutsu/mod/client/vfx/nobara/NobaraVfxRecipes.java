package jujutsu.mod.client.vfx.nobara;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.VfxContext;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.NobaraVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

public final class NobaraVfxRecipes {
	private static final DustParticleOptions PROJECTJJK_CYAN = new DustParticleOptions(0x2CE8F5, 1.35f);
	private static final DustParticleOptions PROJECTJJK_CYAN_SMALL = new DustParticleOptions(0x2CE8F5, 0.68f);

	private NobaraVfxRecipes() {}

	public static void register() {
		VfxDirector.register(NobaraVfxIds.HAMMER, NobaraVfxRecipes::hammer);
		VfxDirector.register(NobaraVfxIds.IMPACT, NobaraVfxRecipes::impact);
		VfxDirector.register(NobaraVfxIds.IMPACT_SOUND, NobaraVfxRecipes::impactSound);
		VfxDirector.register(NobaraVfxIds.RESONANCE_CHANNEL, NobaraVfxRecipes::resonanceChannel);
		VfxDirector.register(NobaraVfxIds.RESONANCE_STRIKE, NobaraVfxRecipes::resonanceStrike);
		VfxDirector.register(NobaraVfxIds.LINK_BIND, NobaraVfxRecipes::linkBind);
		VfxDirector.register(NobaraVfxIds.DETONATE, NobaraVfxRecipes::detonate);
		VfxDirector.register(NobaraVfxIds.ENLARGE, NobaraVfxRecipes::enlarge);
		VfxDirector.register(NobaraVfxIds.EXPLOSION, NobaraVfxRecipes::explosion);
		VfxDirector.register(NobaraVfxIds.FIRST_PERSON_SNAP, NobaraVfxRecipes::firstPersonSnap);
	}

	private static VfxInstance hammer(VfxCue cue) {
		return VfxInstance.of(10, (context, initialAgeTicks) -> {
			float proximity = context.proximity(cue, 48.0);
			if (proximity <= 0.01f) {
				return;
			}
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0x1A2B3C4DL);
				context.playNoFalloff(SoundEvents.ANVIL_USE, 1.0f * proximity, 0.94f, origin, random);
				context.playNoFalloff(SoundEvents.NETHERITE_BLOCK_HIT, 0.55f * proximity, 0.72f, origin, random);
				context.playNoFalloff(JujutsuSounds.HAIRPIN_HAMMER_SNAP, 0.75f * proximity, 1.0f, origin, random);
			}
			context.camera().triggerSwing(intensity(cue), proximity, initialAgeTicks);
			context.hud().triggerSwing(proximity, initialAgeTicks);
		});
	}

	private static VfxInstance impact(VfxCue cue) {
		return VfxInstance.of(20, (context, initialAgeTicks) -> {
			float proximity = context.proximity(cue, 56.0);
			if (proximity <= 0.01f) {
				return;
			}
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				playImpactSound(context, origin, proximity, random(cue, 0x17711L));
			}
			context.camera().triggerImpact(intensity(cue), proximity, initialAgeTicks);
			context.hud().triggerImpact(proximity, initialAgeTicks);
		});
	}

	private static VfxInstance impactSound(VfxCue cue) {
		return VfxInstance.of(2, (context, initialAgeTicks) -> {
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				playImpactSound(context, context.resolveOrigin(cue), 1.0f, random(cue, 0x41E7L));
			}
		});
	}

	private static VfxInstance resonanceChannel(VfxCue cue) {
		return VfxInstance.of(12, (context, initialAgeTicks) -> {
			float proximity = context.proximity(cue, 48.0);
			if (proximity > 0.01f) {
				context.camera().triggerImpact(intensity(cue), proximity, initialAgeTicks);
				context.hud().triggerImpact(proximity * 0.55f, initialAgeTicks);
			}
		});
	}

	private static VfxInstance resonanceStrike(VfxCue cue) {
		return VfxInstance.of(24, (context, initialAgeTicks) -> {
			int marks = intensity(cue);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0x5A17E0L);
				spawnResonanceBurst(context, origin, marks, random);
			}
			float proximity = context.proximity(cue, 64.0);
			if (proximity > 0.01f) {
				context.camera().triggerImpact(marks, proximity, initialAgeTicks);
				context.hud().triggerImpact(proximity, initialAgeTicks);
			}
		});
	}

	private static VfxInstance linkBind(VfxCue cue) {
		return VfxInstance.of(14, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0xB1ADL);
			context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, 20, 0.9, 0.0, -0.05, random);
			context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 12, 0.5, 0.08, random);
			context.playNoFalloff(JujutsuSounds.PROJECTJJK_CHIME, 0.7f, 1.15f, origin, random);
		});
	}

	private static VfxInstance detonate(VfxCue cue) {
		return VfxInstance.of(14, (context, initialAgeTicks) -> {
			int marks = intensity(cue);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0xDE70A7EL);
				context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 8 + marks * 2, 0.28, 0.08, random);
				context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, 14 + marks * 2, 0.78, -0.18, 0.04, random);
			}
			float proximity = context.proximity(cue, 40.0);
			if (proximity > 0.01f) {
				context.camera().triggerSwing(marks, proximity * 0.7f, initialAgeTicks);
				context.hud().triggerSwing(proximity * 0.65f, initialAgeTicks);
			}
		});
	}

	private static VfxInstance enlarge(VfxCue cue) {
		return VfxInstance.of(28, (context, initialAgeTicks) -> {
			int marks = intensity(cue);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.ENLARGE, 28);
			float proximity = context.proximity(cue, 64.0);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0xE11A6EL);
				context.burst(ParticleTypes.FLASH, origin.add(0.0, 0.18, 0.0), 2, 0.18, 0.0, random);
				context.burst(PROJECTJJK_CYAN, origin.add(0.0, 0.2, 0.0), 34 + marks * 7, 1.15, 0.18, random);
				context.burst(ParticleTypes.DAMAGE_INDICATOR, origin, 12, 0.22, 0.04, random);
				context.burst(ParticleTypes.SOUL_FIRE_FLAME, origin, 18 + marks * 4, 0.36, 0.08, random);
				context.burst(JujutsuParticles.HAIRPIN_SNAP_CRACK, origin, 12 + marks, 0.3, 0.08, random);
				context.burst(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, origin, 18, 0.44, 0.3, random);
				context.burst(JujutsuParticles.HAIRPIN_BURST_RESIDUE, origin, 24, 0.48, 0.18, random);
				context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, 12, 0.48, 0.0, -0.12, random);
				if (proximity > 0.01f) {
					context.playNoFalloff(JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT, 1.35f * proximity, 1.82f, origin, random);
					context.playNoFalloff(JujutsuSounds.PROJECTJJK_GOO_FOLEY, 0.36f * proximity, 1.45f, origin, random);
				}
			}
			if (proximity > 0.01f) {
				context.camera().triggerImpact(marks + 2, Math.min(1.0f, proximity * 1.15f), initialAgeTicks);
				context.hud().triggerImpact(Math.min(1.0f, proximity * 1.1f), initialAgeTicks);
			}
		});
	}

	private static VfxInstance explosion(VfxCue cue) {
		return VfxInstance.of(18, (context, initialAgeTicks) -> {
			int marks = intensity(cue);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.EXPLOSION, 18);
			float proximity = context.proximity(cue, 64.0);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0xE0B00FL);
				context.burst(ParticleTypes.FLASH, origin.add(0.0, 0.18, 0.0), 1, 0.0, 0.0, random);
				context.burst(PROJECTJJK_CYAN, origin, 24 + marks * 5, 0.85, 0.2, random);
				context.burst(ParticleTypes.SOUL_FIRE_FLAME, origin, 10 + marks * 3, 0.38, 0.09, random);
				context.burst(JujutsuParticles.HAIRPIN_SPARK, origin, 22 + marks * 5, 0.52, 0.28, random);
				context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 6 + marks, 0.28, 0.08, random);
				context.ring(JujutsuParticles.HAIRPIN_IGNITION_TICK, origin, 20 + marks * 3, 1.1 + Math.min(4, marks) * 0.12, 0.08, 0.04, random);
				if (proximity > 0.01f) {
					context.playNoFalloff(JujutsuSounds.PROJECTJJK_EXPLODE, 0.42f * proximity, 1.96f, origin, random);
					context.playNoFalloff(JujutsuSounds.PROJECTJJK_IMPLODE, 0.24f * proximity, 1.22f, origin, random);
				}
			}
			if (proximity > 0.01f) {
				context.camera().triggerImpact(marks, Math.min(1.0f, proximity * 0.82f), initialAgeTicks);
				context.hud().triggerImpact(Math.min(1.0f, proximity * 0.74f), initialAgeTicks);
			}
		});
	}

	private static VfxInstance firstPersonSnap(VfxCue cue) {
		return VfxInstance.of(15, (context, initialAgeTicks) -> context.firstPerson().triggerSnap(initialAgeTicks));
	}

	private static void spawnResonanceBurst(VfxContext context, Vec3 origin, int marks, RandomSource random) {
		int intensity = 26 + marks * 14;
		context.burst(ParticleTypes.SOUL_FIRE_FLAME, origin, intensity, 0.5, 0.22, random);
		context.burst(JujutsuParticles.HAIRPIN_MARK_STAIN, origin, 6 + marks * 2, 0.5, 0.02, random);
		context.burst(JujutsuParticles.HAIRPIN_SPARK, origin, intensity, 0.6, 0.24, random);
		context.burst(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, origin, 8 + marks * 4, 0.6, 0.34, random);
		context.burst(ParticleTypes.CRIT, origin, intensity, 0.6, 0.2, random);
		context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, 18 + marks * 6, 1.2 + marks * 0.35, -0.6, 0.06, random);
		context.burst(ParticleTypes.FLASH, origin, 1, 0.0, 0.0, random);
	}

	private static void playImpactSound(VfxContext context, Vec3 origin, float volumeScale, RandomSource random) {
		context.playNoFalloff(JujutsuSounds.PROJECTJJK_WHOOSH_HIT, 0.9f * volumeScale, 0.72f, origin, random);
		context.playNoFalloff(JujutsuSounds.PROJECTJJK_EXPLODE, 0.52f * volumeScale, 0.78f, origin, random);
	}

	private static int intensity(VfxCue cue) {
		return Math.max(1, cue.intensity());
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
