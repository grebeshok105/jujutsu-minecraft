package jujutsu.mod.client.cursedincident;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.VfxContext;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.cursedincident.CursedIncidentVfxIds;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.VfxCue;

/** Client-only recipes for the five incident atmosphere cues. */
public final class IncidentAtmosphere {
	private static final DustParticleOptions CURSE_DUST = new DustParticleOptions(packRgb(27, 10, 40), 1.2f);
	private static final DustParticleOptions SEAL_SPARK = new DustParticleOptions(packRgb(220, 160, 55), 0.8f);
	private static final DustParticleOptions BIRTH_DUST = new DustParticleOptions(packRgb(80, 20, 120), 1.5f);

	private IncidentAtmosphere() {
	}

	public static void register() {
		VfxDirector.register(CursedIncidentVfxIds.ZONE_AMBIENT, IncidentAtmosphere::zoneAmbient);
		VfxDirector.register(CursedIncidentVfxIds.STAGE_PULSE, IncidentAtmosphere::stagePulse);
		VfxDirector.register(CursedIncidentVfxIds.SEAL_DEGRADE, IncidentAtmosphere::sealDegrade);
		VfxDirector.register(CursedIncidentVfxIds.SEAL_BREAK, IncidentAtmosphere::sealBreak);
		VfxDirector.register(CursedIncidentVfxIds.SECONDARY_BIRTH, IncidentAtmosphere::secondaryBirth);
	}

	private static VfxInstance zoneAmbient(VfxCue cue) {
		return VfxInstance.of(48, (context, age) -> {
			Vec3 origin = context.resolveOrigin(cue);
			float proximity = context.proximity(cue, CursedIncidentVfxIds.VFX_DELIVERY_RADIUS);
			RandomSource random = RandomSource.create(cue.seed());
			context.burst(CURSE_DUST, origin, 18 + cue.intensity() * 4, 1.5 + proximity, 0.08, random);
			context.playNoFalloff(JujutsuSounds.INCIDENT_DRONE, 0.15f + proximity * 0.45f,
					0.75f + random.nextFloat() * 0.1f, origin, random);
		});
	}

	private static VfxInstance stagePulse(VfxCue cue) {
		return VfxInstance.of(20, (context, age) -> {
			context.world().triggerImpact(cue, jujutsu.mod.client.vfx.VfxWorldChannel.ImpactStyle.EXPLOSION, 20);
			context.burst(ParticleTypes.SMOKE, context.resolveOrigin(cue), 24, 1.0, 0.12,
					RandomSource.create(cue.seed()));
		});
	}

	private static VfxInstance sealDegrade(VfxCue cue) {
		return VfxInstance.of(24, (context, age) -> context.ring(SEAL_SPARK, context.resolveOrigin(cue), 16,
				0.8, 0.5, 0.04, RandomSource.create(cue.seed())));
	}

	private static VfxInstance sealBreak(VfxCue cue) {
		return VfxInstance.of(28, (context, age) -> {
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = RandomSource.create(cue.seed());
			context.burst(ParticleTypes.SOUL_FIRE_FLAME, origin, 30, 1.3, 0.15, random);
			context.playNoFalloff(JujutsuSounds.SEAL_BREAK, 0.8f, 0.85f, origin, random);
		});
	}

	private static VfxInstance secondaryBirth(VfxCue cue) {
		return VfxInstance.of(32, (context, age) -> {
			Vec3 origin = context.resolveOrigin(cue);
			RandomSource random = RandomSource.create(cue.seed());
			context.ring(BIRTH_DUST, origin, 24, 2.0, 0.2, 0.12, random);
			context.burst(ParticleTypes.PORTAL, origin, 20, 1.8, 0.2, random);
			context.playNoFalloff(JujutsuSounds.SECONDARY_BIRTH, 0.75f, 0.9f, origin, random);
		});
	}

	private static int packRgb(int red, int green, int blue) {
		return ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
	}
}
