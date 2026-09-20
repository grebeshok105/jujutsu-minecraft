package jujutsu.mod.client.vfx.cursedspirit;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.VfxContext;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.vfx.CursedSpiritVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** Curse-ability VFX recipes (Block 3, Step 9): one compact world burst per ability cue. */
public final class CursedSpiritVfxRecipes {
	private static final DustParticleOptions CURSE_GREEN = new DustParticleOptions(
			packRgb(120, 220, 110), 1.0f);
	private static final DustParticleOptions CURSE_DARK = new DustParticleOptions(
			packRgb(46, 90, 44), 0.7f);
	private static final DustParticleOptions FEAR_PURPLE = new DustParticleOptions(
			packRgb(122, 62, 168), 0.9f);
	private static final DustParticleOptions RAGE_RED = new DustParticleOptions(
			packRgb(200, 60, 60), 1.0f);

	private CursedSpiritVfxRecipes() {
	}

	public static void register() {
		VfxDirector.register(CursedSpiritVfxIds.DASH, CursedSpiritVfxRecipes::burst);
		VfxDirector.register(CursedSpiritVfxIds.SLAM, CursedSpiritVfxRecipes::slam);
		VfxDirector.register(CursedSpiritVfxIds.ACID_SPIT, CursedSpiritVfxRecipes::spit);
		VfxDirector.register(CursedSpiritVfxIds.ACID_ZONE, CursedSpiritVfxRecipes::zone);
		VfxDirector.register(CursedSpiritVfxIds.RUNNER, CursedSpiritVfxRecipes::grabFlash);
		VfxDirector.register(CursedSpiritVfxIds.FEAR, CursedSpiritVfxRecipes::fear);
		VfxDirector.register(CursedSpiritVfxIds.REGEN, CursedSpiritVfxRecipes::burst);
		VfxDirector.register(CursedSpiritVfxIds.ARMOR, CursedSpiritVfxRecipes::burst);
		VfxDirector.register(CursedSpiritVfxIds.BERSERK, CursedSpiritVfxRecipes::rage);
	}

	private static VfxInstance burst(VfxCue cue) {
		return VfxInstance.of(8, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			emit(context, cue, CURSE_GREEN, CURSE_DARK);
		});
	}
	private static VfxInstance grabFlash(VfxCue cue) {
		// Runner cues are emitted at the yaw-relative hand/contact point, never at spirit origin.
		return VfxInstance.of(6, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			emit(context, cue, CURSE_GREEN, CURSE_DARK);
		});
	}

	private static VfxInstance slam(VfxCue cue) {
		return VfxInstance.of(12, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			emit(context, cue, CURSE_DARK, CURSE_GREEN);
		});
	}

	private static VfxInstance spit(VfxCue cue) {
		return VfxInstance.of(8, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			emit(context, cue, CURSE_GREEN, CURSE_GREEN);
		});
	}

	private static VfxInstance zone(VfxCue cue) {
		return VfxInstance.of(10, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			emit(context, cue, CURSE_GREEN, CURSE_DARK);
		});
	}

	private static VfxInstance fear(VfxCue cue) {
		return VfxInstance.of(10, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			emit(context, cue, FEAR_PURPLE, CURSE_DARK);
		});
	}

	private static VfxInstance rage(VfxCue cue) {
		return VfxInstance.of(12, (context, initialAgeTicks) -> {
			if (!VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				return;
			}
			emit(context, cue, RAGE_RED, CURSE_DARK);
		});
	}

	private static void emit(VfxContext context, VfxCue cue, DustParticleOptions primary,
			DustParticleOptions edge) {
		Vec3 origin = cue.origin();
		RandomSource random = RandomSource.create(cue.seed() ^ 0xC05ECA5EL);
		context.burst(primary, origin.add(0.0, 0.5, 0.0), 8, 0.25, 0.10, random);
		context.ring(edge, origin.add(0.0, 0.06, 0.0), 8, 0.4, 0.0, 0.03, random);
	}

	private static int packRgb(int red, int green, int blue) {
		return ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
	}
}
