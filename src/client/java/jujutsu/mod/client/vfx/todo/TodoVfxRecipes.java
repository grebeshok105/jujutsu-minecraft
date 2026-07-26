package jujutsu.mod.client.vfx.todo;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.VfxContext;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxInstance;
import jujutsu.mod.client.vfx.VfxPalette;
import jujutsu.mod.client.vfx.VfxWorldChannel;
import jujutsu.mod.vfx.TodoVfxIds;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

/** Todo's VFX Core recipes. No effect owns separate callbacks or packet receivers. */
public final class TodoVfxRecipes {
	private static final DustParticleOptions TODO_VIOLET = new DustParticleOptions(
			packRgb(VfxPalette.TODO_VIOLET_R, VfxPalette.TODO_VIOLET_G, VfxPalette.TODO_VIOLET_B), 1.05f);
	private static final DustParticleOptions TODO_EDGE = new DustParticleOptions(
			packRgb(VfxPalette.TODO_EDGE_R, VfxPalette.TODO_EDGE_G, VfxPalette.TODO_EDGE_B), 0.72f);

	private TodoVfxRecipes() {}

	public static void register() {
		VfxDirector.register(TodoVfxIds.BOOGIE_WOOGIE, TodoVfxRecipes::boogieWoogie);
		VfxDirector.register(TodoVfxIds.SWAP_ENDPOINT, TodoVfxRecipes::swapEndpoint);
	}

	private static VfxInstance boogieWoogie(VfxCue cue) {
		// ~15 ticks covers Nobara-style first-person snap phases + third-person GeckoLib clap.
		return VfxInstance.of(15, (context, initialAgeTicks) -> {
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				float proximity = context.proximity(cue, 56.0);
				context.camera().triggerLaunch(1, proximity * 0.45f, initialAgeTicks);
				context.hud().triggerFlash(80, Math.round(62.0f * proximity), initialAgeTicks);
				// Third-person GeckoLib clap (both arms) via replaced-entity animatable.
				TodoAnimationHooks.triggerBoogieWoogie(cue);
				if (isLocalAnchor(context, cue)) {
					// FP clap always starts at progress 0 (ignore late-cue age) so every cast matches.
					context.firstPerson().triggerClap(0.0f);
				}
			}
		});
	}

	private static VfxInstance swapEndpoint(VfxCue cue) {
		return VfxInstance.of(8, (context, initialAgeTicks) -> {
			if (VfxTimeline.isOpeningBeat(initialAgeTicks)) {
				Vec3 endpoint = context.resolveOrigin(cue);
				emitFlash(context, endpoint, random(cue, 0xB001E13L));
				// Only the leading endpoint carries the pair delta, so the ribbon spans the two bodies once.
				context.world().triggerImpact(cue, VfxWorldChannel.ImpactStyle.BOOGIE_WOOGIE, 8);
			}
		});
	}

	private static void emitFlash(VfxContext context, Vec3 origin, RandomSource random) {
		context.burst(TODO_VIOLET, origin, 12, 0.28, 0.16, random);
		context.burst(TODO_EDGE, origin, 8, 0.18, 0.10, random);
		context.ring(TODO_EDGE, origin, 10, 0.34, 0.06, 0.045, random);
	}

	private static boolean isLocalAnchor(VfxContext context, VfxCue cue) {
		return context.client().player != null
				&& cue.anchorEntityId() != VfxCue.NO_ANCHOR
				&& cue.anchorEntityId() == context.client().player.getId();
	}

	private static RandomSource random(VfxCue cue, long salt) {
		return RandomSource.create(cue.seed() ^ salt);
	}

	private static int packRgb(int red, int green, int blue) {
		return ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
	}
}
