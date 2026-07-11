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
import jujutsu.mod.client.render.nobara.NobaraPlayerGeoAnimatable;

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
		VfxDirector.register(NobaraVfxIds.REMNANT_DROP, NobaraVfxRecipes::remnantDrop);
		VfxDirector.register(NobaraVfxIds.RITUAL_BIND, NobaraVfxRecipes::ritualBind);
		VfxDirector.register(NobaraVfxIds.DOLL_STRIKE, NobaraVfxRecipes::dollStrike);
		VfxDirector.register(NobaraVfxIds.RESONANCE_RELEASE, NobaraVfxRecipes::resonanceRelease);
		VfxDirector.register(NobaraVfxIds.HAMMER_HORIZONTAL, cue -> hammerAction(cue, "hammer_horizontal", false));
		VfxDirector.register(NobaraVfxIds.HAMMER_OVERHEAD, cue -> hammerAction(cue, "hammer_overhead", true));
		VfxDirector.register(NobaraVfxIds.HAMMER_NAIL_LAUNCH, cue -> hammerAction(cue, "hammer_nail_launch", true));
		VfxDirector.register(NobaraVfxIds.EMBEDDED_NAIL_DRIVE, cue -> hammerAction(cue, "hammer_embedded_drive", true));
		VfxDirector.register(NobaraVfxIds.BLACK_FLASH, NobaraVfxRecipes::blackFlash);
		VfxDirector.register(NobaraVfxIds.SELF_RESONANCE, cue -> hammerAction(cue, "self_resonance", true));
		VfxDirector.register(NobaraVfxIds.NAIL_DEEPEN, NobaraVfxRecipes::nailDeepen);
	}

	private static VfxInstance nailDeepen(VfxCue cue) {
		return VfxInstance.of(16, (context, initialAgeTicks) -> {
			Vec3 origin = context.resolveOrigin(cue);
			int depth = Math.max(2, Math.min(3, intensity(cue)));
			float proximity = context.proximity(cue, 48.0);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				RandomSource random = random(cue, 0xDEE9EEL);
				context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, depth == 3 ? 26 : 18, depth == 3 ? 0.78 : 0.54, -0.24, -0.1, random);
				context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, depth == 3 ? 28 : 16, 0.42, 0.04, random);
				context.burst(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, origin, depth == 3 ? 18 : 9, 0.32, 0.24, random);
				context.burst(new DustParticleOptions(0x2A0008, depth == 3 ? 1.5f : 1.0f), origin, depth == 3 ? 22 : 10, 0.38, 0.12, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_IMPLODE, 0.46f * proximity, depth == 3 ? 0.7f : 1.05f, origin, random);
			}
			if (proximity > 0.01f) context.camera().triggerHeavyImpact(depth, proximity * (depth == 3 ? 0.72f : 0.38f), initialAgeTicks);
		});
	}

	private static VfxInstance hammerAction(VfxCue cue, String animation, boolean heavy) {
		return VfxInstance.of(heavy ? 18 : 10, (context, initialAgeTicks) -> {
			if (VfxTimeline.isOpeningBeat(initialAgeTicks) && context.client().level != null && cue.anchorEntityId() != VfxCue.NO_ANCHOR) {
				var entity = context.client().level.getEntity(cue.anchorEntityId());
				if (entity != null) NobaraPlayerGeoAnimatable.INSTANCE.triggerAction(entity, animation);
			}
			context.firstPerson().triggerSnap(initialAgeTicks);
			float proximity = context.proximity(cue, 40.0);
			if (proximity > 0.01f) context.camera().triggerHeavyImpact(heavy ? 2 : 1, proximity * (heavy ? 0.65f : 0.3f), initialAgeTicks);
		});
	}

	private static VfxInstance blackFlash(VfxCue cue) {
		return VfxInstance.of(22, (context, initialAgeTicks) -> {
			Vec3 origin = context.resolveOrigin(cue);
			float proximity = context.proximity(cue, 64.0);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				RandomSource random = random(cue, 0xB1ACF1A5L);
				context.burst(ParticleTypes.FLASH, origin, 2, 0.05, 0.0, random);
				context.burst(new DustParticleOptions(0x170006, 1.8f), origin, 34, 0.72, 0.24, random);
				context.burst(new DustParticleOptions(0x8F0018, 1.2f), origin, 28, 0.55, 0.31, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT, 1.2f * proximity, 0.72f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT_2, 1.0f * proximity, 0.86f, origin, random);
				if (context.client().level != null && cue.anchorEntityId() != VfxCue.NO_ANCHOR) {
					var entity = context.client().level.getEntity(cue.anchorEntityId());
					if (entity != null) NobaraPlayerGeoAnimatable.INSTANCE.triggerAction(entity, "black_flash");
				}
			}
			if (proximity > 0.01f) {
				context.camera().triggerHeavyImpact(5, proximity, initialAgeTicks);
				context.hud().triggerImpact(proximity, initialAgeTicks);
				context.postProcess().triggerBlur(Math.round(320.0f * proximity), initialAgeTicks);
			}
		});
	}

	private static VfxInstance hammer(VfxCue cue) {
		return VfxInstance.of(10, (context, initialAgeTicks) -> {
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.HAMMER_SEND, 10);
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
			context.camera().triggerLaunch(intensity(cue), proximity, initialAgeTicks);
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
			context.camera().triggerHeavyImpact(intensity(cue), proximity, initialAgeTicks);
			context.hud().triggerImpact(proximity, initialAgeTicks);
			context.postProcess().triggerBlur(Math.round(135.0f * proximity), initialAgeTicks);
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
		return ritualBind(cue);
	}

	private static VfxInstance resonanceStrike(VfxCue cue) {
		return resonanceRelease(cue);
	}

	private static VfxInstance linkBind(VfxCue cue) {
		return remnantDrop(cue);
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
				context.camera().triggerLaunch(marks, proximity * 0.7f, initialAgeTicks);
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
				context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 18 + marks * 4, 0.36, 0.08, random);
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
				context.camera().triggerHeavyImpact(marks + 2, Math.min(1.0f, proximity * 1.15f), initialAgeTicks);
				context.hud().triggerImpact(Math.min(1.0f, proximity * 1.1f), initialAgeTicks);
				context.postProcess().triggerBlur(Math.round(260.0f * proximity), initialAgeTicks);
			}
		});
	}

	private static VfxInstance explosion(VfxCue cue) {
		return VfxInstance.of(18, (context, initialAgeTicks) -> {
			int depth = NobaraVfxIds.hairpinExplosionDepth(intensity(cue));
			boolean finale = NobaraVfxIds.isHairpinFinale(intensity(cue));
			int marks = depth;
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.EXPLOSION, 18);
			float proximity = context.proximity(cue, 64.0);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0xE0B00FL);
				context.burst(ParticleTypes.FLASH, origin.add(0.0, 0.18, 0.0), 1, 0.0, 0.0, random);
				context.burst(PROJECTJJK_CYAN, origin, 24 + marks * 5, 0.85, 0.2, random);
				context.burst(JujutsuParticles.HAIRPIN_BURST_RESIDUE, origin, 10 + marks * 3, 0.38, 0.09, random);
				context.burst(JujutsuParticles.HAIRPIN_SPARK, origin, 22 + marks * 5, 0.52, 0.28, random);
				context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 6 + marks, 0.28, 0.08, random);
				context.ring(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 20 + marks * 3, 1.1 + Math.min(4, marks) * 0.12, 0.08, 0.04, random);
				if (depth == 3) {
					context.burst(new DustParticleOptions(0x1A0006, 1.8f), origin, 34, 0.62, 0.22, random);
					context.burst(JujutsuParticles.HAIRPIN_SNAP_CRACK, origin, 24, 0.5, 0.22, random);
					context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, 34, 1.55, 0.2, -0.16, random);
				}
				if (finale) {
					context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, 42, 2.05, 0.42, 0.14, random);
					context.ring(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 34, 2.65, -0.12, 0.08, random);
					context.burst(ParticleTypes.FLASH, origin, 2, 0.12, 0.0, random);
				}
				if (proximity > 0.01f) {
					context.playNoFalloff(JujutsuSounds.PROJECTJJK_EXPLODE, 0.42f * proximity, 1.96f, origin, random);
					context.playNoFalloff(JujutsuSounds.PROJECTJJK_IMPLODE, 0.24f * proximity, 1.22f, origin, random);
					if (depth == 3) context.playNoFalloff(JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, 0.68f * proximity, 0.76f, origin, random);
					if (finale) context.playNoFalloff(JujutsuSounds.PROJECTJJK_LONG_WHOOSH, 0.86f * proximity, 0.58f, origin, random);
				}
			}
			if (proximity > 0.01f) {
				context.camera().triggerExplosion(depth + (finale ? 3 : 0), Math.min(1.0f, proximity * (finale ? 1.0f : 0.82f)), initialAgeTicks);
				context.hud().triggerImpact(Math.min(1.0f, proximity * 0.74f), initialAgeTicks);
				context.postProcess().triggerBlur(Math.round(210.0f * proximity), initialAgeTicks);
			}
		});
	}

	private static VfxInstance remnantDrop(VfxCue cue) {
		return VfxInstance.of(16, (context, initialAgeTicks) -> {
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.RITUAL_BIND, 16);
			float proximity = context.proximity(cue, 64.0);
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks) || proximity <= 0.01f) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0xB1ADL);
			context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, 16, 0.72, 0.0, -0.04, random);
			context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 10, 0.34, 0.06, random);
			context.playNoFalloff(JujutsuSounds.PROJECTJJK_CHIME, 0.62f * proximity, 1.22f, origin, random);
		});
	}

	private static VfxInstance ritualBind(VfxCue cue) {
		return VfxInstance.of(18, (context, initialAgeTicks) -> {
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.RITUAL_BIND, 18);
			float proximity = context.proximity(cue, 48.0);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0x7117A1L);
				context.ring(JujutsuParticles.HAIRPIN_WARN_EDGE, origin, 24, 1.05, -0.2, -0.08, random);
				context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, 18, 0.46, 0.075, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_MAGIC, 0.82f * proximity, 0.76f, origin, random);
				if (context.client().level != null && cue.anchorEntityId() != VfxCue.NO_ANCHOR) {
					var entity = context.client().level.getEntity(cue.anchorEntityId());
					if (entity != null) NobaraPlayerGeoAnimatable.INSTANCE.triggerAction(entity, "hammer_doll_strike");
				}
			}
			if (proximity > 0.01f) {
				context.camera().triggerRitual(intensity(cue), proximity, initialAgeTicks);
				context.hud().triggerSwing(proximity * 0.56f, initialAgeTicks);
				context.postProcess().triggerBlur(Math.round(185.0f * proximity), initialAgeTicks);
			}
		});
	}

	private static VfxInstance dollStrike(VfxCue cue) {
		return VfxInstance.of(36, (context, initialAgeTicks) -> {
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.DOLL_STRIKE, 36);
			float proximity = context.proximity(cue, 44.0);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0xD01157L);
				context.burst(JujutsuParticles.HAIRPIN_SNAP_CRACK, origin, 42, 0.52, 0.28, random);
				context.burst(JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, origin, 28, 0.64, 0.45, random);
				context.burst(PROJECTJJK_CYAN, origin, 56, 0.68, 0.34, random);
				context.burst(ParticleTypes.EXPLOSION_EMITTER, origin, 3, 0.18, 0.02, random);
				context.burst(ParticleTypes.FLASH, origin, 3, 0.12, 0.0, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_IMPLODE, 1.15f * proximity, 0.52f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, 1.5f * proximity, 0.58f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT, 1.1f * proximity, 0.68f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_LONG_WHOOSH, 0.9f * proximity, 0.55f, origin, random);
			}
			if (proximity > 0.01f) {
				context.camera().triggerResonanceImpact(intensity(cue) + 7, Math.min(1.0f, proximity * 1.15f), initialAgeTicks);
				context.camera().triggerExplosion(intensity(cue) + 5, proximity, initialAgeTicks);
				context.hud().triggerImpact(proximity, initialAgeTicks);
				context.hud().triggerFlash(420, Math.round(210.0f * proximity), initialAgeTicks);
				context.postProcess().triggerBlur(Math.round(520.0f * proximity), initialAgeTicks);
				context.firstPerson().triggerSnap(initialAgeTicks);
			}
			if (isLocalAnchor(context, cue)) {
				context.hud().triggerNausea(0.85f, initialAgeTicks);
				context.postProcess().triggerBlur(700, initialAgeTicks);
			}
		});
	}

	private static VfxInstance resonanceRelease(VfxCue cue) {
		return VfxInstance.of(38, (context, initialAgeTicks) -> {
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.RESONANCE_RELEASE, 38);
			int marks = intensity(cue);
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 origin = context.resolveOrigin(cue);
				RandomSource random = random(cue, 0x5A17E0L);
				spawnResonanceBurst(context, origin, marks, random);
				context.burst(ParticleTypes.EXPLOSION_EMITTER, origin, 4, 0.3, 0.04, random);
				context.burst(ParticleTypes.FLASH, origin, 3, 0.18, 0.0, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_IMPLODE, 1.1f, 0.48f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_DEEP_EXPLOSION, 1.5f, 0.66f, origin, random);
				context.playNoFalloff(JujutsuSounds.PROJECTJJK_BLACK_FLASH_IMPACT_2, 1.15f, 0.72f, origin, random);
			}
			float proximity = context.proximity(cue, 64.0);
			if (proximity > 0.01f) {
				context.camera().triggerResonanceImpact(marks + 2, proximity, initialAgeTicks);
				context.camera().triggerExplosion(marks + 5, proximity, initialAgeTicks);
				context.hud().triggerImpact(proximity, initialAgeTicks);
				context.hud().triggerFlash(520, Math.round(225.0f * proximity), initialAgeTicks);
				context.postProcess().triggerBlur(Math.round(360.0f * proximity), initialAgeTicks);
			}
			if (isLocalAnchor(context, cue)) {
				context.hud().triggerNausea(1.0f, initialAgeTicks);
				context.postProcess().triggerBlur(520, initialAgeTicks);
			}
		});
	}

	private static VfxInstance firstPersonSnap(VfxCue cue) {
		return VfxInstance.of(15, (context, initialAgeTicks) -> context.firstPerson().triggerSnap(initialAgeTicks));
	}

	private static void spawnResonanceBurst(VfxContext context, Vec3 origin, int marks, RandomSource random) {
		int intensity = 26 + marks * 14;
		context.burst(JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, origin, intensity, 0.5, 0.22, random);
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

	private static boolean isLocalAnchor(VfxContext context, VfxCue cue) {
		return context.client().player != null
				&& cue.anchorEntityId() != VfxCue.NO_ANCHOR
				&& cue.anchorEntityId() == context.client().player.getId();
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
