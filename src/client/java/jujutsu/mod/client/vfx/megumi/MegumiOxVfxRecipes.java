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

/** Grounded shadow-and-dust effects for Piercing Ox's committed charge. */
public final class MegumiOxVfxRecipes {
	private static final int SUMMON_TICKS = 16;
	private static final int WINDUP_TICKS = 8;
	private static final int CHARGE_TICKS = 4;
	private static final int IMPACT_TICKS = 10;
	private static final int ABORT_TICKS = 8;
	private static final DustParticleOptions OX_DUST = new DustParticleOptions(0x4A2B1B, 1.0f);

	private MegumiOxVfxRecipes() {}

	public static void register() {
		VfxDirector.register(MegumiVfxIds.OX_SUMMON, MegumiOxVfxRecipes::summon);
		VfxDirector.register(MegumiVfxIds.OX_WINDUP, MegumiOxVfxRecipes::windup);
		VfxDirector.register(MegumiVfxIds.OX_CHARGE, MegumiOxVfxRecipes::charge);
		VfxDirector.register(MegumiVfxIds.OX_IMPACT, MegumiOxVfxRecipes::impact);
		VfxDirector.register(MegumiVfxIds.OX_ABORT, MegumiOxVfxRecipes::abort);
	}

	private static VfxInstance summon(VfxCue cue) {
		return VfxInstance.of(SUMMON_TICKS, (context, age) -> {
			if (!VfxTimeline.isOpeningBeat(age)) {
				return;
			}
			ClientLevel level = context.client().level;
			Entity anchor = level == null ? null : level.getEntity(cue.anchorEntityId());
			if (anchor instanceof AbstractClientPlayer) {
				MegumiAnimationHooks.triggerOx(cue);
				if (anchor == context.client().player) {
					context.firstPerson().triggerSign(0.0f);
				}
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x4F585301L);
			context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_TICKS);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.05, 0.0),
					18, 0.6, 0.12, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.04, 0.0),
					14, 1.25, 0.0, 0.05, random);
		});
	}

	private static VfxInstance windup(VfxCue cue) {
		return VfxInstance.of(WINDUP_TICKS, (context, age) -> {
			if (!VfxTimeline.isOpeningBeat(age)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x4F585701L);
			context.burst(OX_DUST, origin.add(0.0, 0.08, 0.0), 8, 0.35, 0.03, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, 0.04, 0.0),
					10, 0.8, 0.0, 0.04, random);
		});
	}

	/** A repeated low trail; the server encodes normalized accumulated travel in cue intensity (1..4). */
	private static VfxInstance charge(VfxCue cue) {
		return VfxInstance.of(CHARGE_TICKS, (context, age) -> {
			if (!VfxTimeline.isOpeningBeat(age)) {
				return;
			}
			int intensity = Math.max(1, Math.min(4, cue.intensity()));
			Vec3 origin = context.resolveOrigin(cue).subtract(cue.direction().scale(0.55))
					.add(0.0, 0.06, 0.0);
			RandomSource random = random(cue, 0x4F584301L);
			context.burst(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin,
					4 + intensity * 3, 0.16 + intensity * 0.07, 0.015, random);
			context.burst(OX_DUST, origin, 2 + intensity, 0.12 + intensity * 0.04, 0.02, random);
		});
	}

	private static VfxInstance impact(VfxCue cue) {
		return VfxInstance.of(IMPACT_TICKS, (context, age) -> {
			if (!VfxTimeline.isOpeningBeat(age)) {
				return;
			}
			int intensity = Math.max(1, Math.min(4, cue.intensity()));
			Vec3 origin = context.resolveOrigin(cue).add(0.0, 0.25, 0.0);
			RandomSource random = random(cue, 0x4F584901L);
			context.burst(ParticleTypes.POOF, origin, 8 + intensity * 4, 0.38 + intensity * 0.08,
					0.09, random);
			context.burst(OX_DUST, origin, 6 + intensity * 3, 0.3 + intensity * 0.07, 0.035, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin.add(0.0, -0.20, 0.0),
					10 + intensity * 2, 0.5 + intensity * 0.12, 0.0, 0.06, random);
		});
	}

	private static VfxInstance abort(VfxCue cue) {
		return VfxInstance.of(ABORT_TICKS, (context, age) -> {
			if (!VfxTimeline.isOpeningBeat(age)) {
				return;
			}
			int intensity = Math.max(1, Math.min(4, cue.intensity()));
			Vec3 origin = context.resolveOrigin(cue).add(0.0, 0.05, 0.0);
			RandomSource random = random(cue, 0x4F584101L);
			context.burst(OX_DUST, origin, 8 + intensity * 3, 0.35 + intensity * 0.08, 0.02, random);
			context.burst(ParticleTypes.POOF, origin, 4 + intensity * 2, 0.25 + intensity * 0.05,
					0.035, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE, origin, 8 + intensity * 2,
					0.45 + intensity * 0.12, 0.0, 0.04, random);
		});
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
