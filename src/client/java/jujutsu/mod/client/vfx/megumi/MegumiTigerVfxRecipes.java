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

/** Tiger Funeral's shadow summon, committed-strike accents, and finisher burst. */
public final class MegumiTigerVfxRecipes {
	private static final DustParticleOptions TIGER_DUST = new DustParticleOptions(0x39252B, 0.9f);
	private static final int SUMMON_DURATION_TICKS = 16;
	private static final int STRIKE_DURATION_TICKS = 7;
	private static final int FINISHER_DURATION_TICKS = 12;
	private static final int FINISHER_RECOVERY_DUST_TICK = 8;

	private MegumiTigerVfxRecipes() {}

	public static void register() {
		VfxDirector.register(MegumiVfxIds.TIGER_SUMMON, MegumiTigerVfxRecipes::summon);
		VfxDirector.register(MegumiVfxIds.TIGER_STRIKE, MegumiTigerVfxRecipes::strike);
		VfxDirector.register(MegumiVfxIds.TIGER_FINISHER, MegumiTigerVfxRecipes::finisher);
	}

	private static VfxInstance summon(VfxCue cue) {
		return VfxInstance.of(SUMMON_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			ClientLevel level = context.client().level;
			Entity anchor = level == null ? null : level.getEntity(cue.anchorEntityId());
			if (anchor instanceof AbstractClientPlayer) {
				MegumiAnimationHooks.triggerTiger(cue);
				if (anchor == context.client().player) {
					context.firstPerson().triggerSign(0.0f);
				}
			}
			Vec3 origin = cue.origin();
			RandomSource random = random(cue, 0x544947455253554dL);
			context.world().triggerImpact(cue,
					VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, SUMMON_DURATION_TICKS);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE,
					origin.add(0.0, 0.05, 0.0), 18, 1.35, 0.0, 0.09, random);
			context.burst(TIGER_DUST, origin.add(0.0, 0.25, 0.0), 12, 0.45, 0.18, random);
		});
	}

	private static VfxInstance strike(VfxCue cue) {
		return VfxInstance.of(STRIKE_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x5449474552535452L);
			context.burst(ParticleTypes.CRIT, origin, 8, 0.26, 0.12, random);
			context.burst(TIGER_DUST, origin, 5, 0.18, 0.08, random);
		});
	}

	private static VfxInstance finisher(VfxCue cue) {
		return VfxInstance.of(FINISHER_DURATION_TICKS, (context, initialAgeTicks) -> {
			if (initialAgeTicks == FINISHER_RECOVERY_DUST_TICK) {
				RandomSource random = random(cue, 0x5449474552445553L);
				context.burst(TIGER_DUST, cue.origin().add(0.0, 0.04, 0.0),
						7, 0.32, 0.03, random);
				return;
			}
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = random(cue, 0x544947455246494eL);
			context.world().triggerImpact(cue,
					VfxWorldChannel.ImpactStyle.MEGUMI_SHADOW_OPEN, FINISHER_DURATION_TICKS);
			context.burst(ParticleTypes.CRIT, origin, 18, 0.5, 0.2, random);
			context.burst(ParticleTypes.POOF, origin, 12, 0.42, 0.14, random);
			context.ring(JujutsuParticles.MEGUMI_SHADOW_MOTE,
					origin.add(0.0, 0.04, 0.0), 22, 1.9, 0.0, 0.12, random);
		});
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}
}
